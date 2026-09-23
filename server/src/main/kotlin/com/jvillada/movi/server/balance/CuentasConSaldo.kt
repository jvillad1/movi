package com.jvillada.movi.server.balance

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.signedDelta
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum

/**
 * **Las cuentas del usuario con su saldo derivado, sumado en SQL** — lo que `GET /api/accounts`
 * contesta, sin traerse la historia entera de movimientos para lograrlo.
 *
 * Existe para que el server pueda llamar a `patrimonioDe` (la regla única del patrimonio, en
 * :core) en los lugares que no tienen la lista de cuentas a mano: el resumen del Inicio
 * (`/api/dashboard/summary`) y el contexto de Movi AI. `GET /api/accounts` carga todos los
 * eventos con `loadNonVoidedEvents` porque además necesita las fechas del último ajuste y del
 * primer movimiento; acá solo hacen falta los saldos, y un `GROUP BY cuenta, tipo, moneda`
 * devuelve unas pocas filas por cuenta en vez de toda la historia. El resumen del Inicio ya
 * cuidaba ese costo (ver `sumasAntesDe` en `DashboardRoutes.kt`), y no se le podía agregar un
 * recorrido completo solo para una cifra.
 *
 * El saldo sale con [signedDelta] y [conSaldos] —las MISMAS funciones que usa [enrichWith]—, así
 * que una cuenta dice acá exactamente lo que dice en la lista, incluido el cero forzado de un
 * bien. Sin anulados; los «Por confirmar» sí, porque el saldo los incluye (igual que la lista).
 */
fun Transaction.cuentasConSaldo(uid: String, voidedIds: Set<String>, usdToCop: Double): List<Account> {
    val total = Events.amount.sum()
    val sumasPorCuenta = Events.select(Events.accountId, Events.type, Events.currency, total)
        .where {
            val base = Events.userId eq uid
            if (voidedIds.isEmpty()) base else base and (Events.id notInList voidedIds.toList())
        }
        .groupBy(Events.accountId, Events.type, Events.currency)
        .mapNotNull { row ->
            val tipo = runCatching { TransactionType.valueOf(row[Events.type]) }.getOrNull() ?: return@mapNotNull null
            SumaPorMoneda(row[Events.accountId], tipo, row[Events.currency], row[total] ?: 0L)
        }
        .groupBy { it.accountId }

    return Accounts.selectAll()
        .where { Accounts.userId eq uid }
        // El mismo orden que la lista (ver el comentario largo en `GET /api/accounts`): quien lea
        // esto en orden —el contexto de Movi AI— ve las cuentas como las ve el dueño.
        .orderBy(Accounts.name.lowerCase() to SortOrder.ASC, Accounts.id to SortOrder.ASC)
        .mapNotNull { row ->
            // Una fila con un tipo que esta versión no conoce no tumba el resumen entero.
            val cuenta = runCatching { row.toAccount() }.getOrNull() ?: return@mapNotNull null
            val saldos = sumasPorCuenta[cuenta.id].orEmpty()
                .groupBy { it.moneda }
                .mapValues { (_, sumas) -> sumas.sumOf { signedDelta(cuenta.type, it.tipo, it.monto) } }
            conSaldos(cuenta, saldos, usdToCop)
        }
}

/**
 * ¿Tiene [uid] algún movimiento en otra moneda que no sea pesos? Si no, la tasa del dólar no se usa
 * para nada en [cuentasConSaldo] y quien la llama puede ahorrarse pedirla. Una fila, con `LIMIT 1`.
 */
fun Transaction.hayMovimientosEnOtraMoneda(uid: String): Boolean =
    Events.select(Events.id)
        .where { (Events.userId eq uid) and (Events.currency neq "COP") }
        .limit(1)
        .any()

private data class SumaPorMoneda(val accountId: String, val tipo: TransactionType, val moneda: String, val monto: Long)
