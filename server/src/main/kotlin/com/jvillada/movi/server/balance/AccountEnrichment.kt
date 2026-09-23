package com.jvillada.movi.server.balance

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.normalizarCondicion
import org.jetbrains.exposed.sql.ResultRow

/** Fila de `accounts` → wire [Account] (balance crudo de la fila; ver [enrichWith] para el derivado). */
fun ResultRow.toAccount() = Account(
    id       = this[Accounts.id],
    name     = this[Accounts.name],
    type     = AccountType.valueOf(this[Accounts.type]),
    balance  = this[Accounts.balance],
    currency = this[Accounts.currency],
    // Mismo helper que usan la escritura (`POST`/`PUT /{id}/conditioned-to`) y el espejo local:
    // una fila con espacios en blanco no puede salir de «Tu plata» solo en un lado.
    condicionadaA = normalizarCondicion(this[Accounts.conditionedTo]),
    // La edad de la versión guardada. Viaja en toda respuesta para que el espejo local del
    // cliente la conserve: ver `Account.lastEditedAt`.
    lastEditedAt = this[Accounts.lastEditedAt],
    // `asset_kind` NULL = no es un bien. Las otras tres columnas solo significan algo con ella.
    bien = this[Accounts.assetKind]?.let { clase ->
        Bien(
            clase = clase,
            valor = this[Accounts.assetValue] ?: 0L,
            valorAl = this[Accounts.assetValuedOn],
            deudaId = this[Accounts.assetDebtId],
        )
    },
)

/**
 * Reemplaza el balance almacenado por los derivados de eventos (por moneda + estimado COP) y
 * agrega **cuándo se cuadró por última vez** y **desde cuándo existe** la cuenta.
 *
 * Los dos sellos salen de los mismos eventos que ya están en la mano —cero consultas nuevas— y por
 * eso viajan en toda respuesta que pase por acá: la pantalla «Cuadre de saldos» y el aviso del
 * Inicio leen la lista de cuentas que ya pedían, sin una llamada más. Ver
 * [Account.lastAdjustmentAt] y [Account.firstEventAt] para qué significa cada uno.
 *
 * [events] son los eventos **no anulados** de la cuenta (ver `loadNonVoidedEvents`): un ajuste que
 * se anuló no cuadró nada, así que no puede seguir contando como la última vez que se miró.
 */
fun enrichWith(base: Account, events: List<FinancialEvent>, rate: Double): Account =
    conSaldos(base, computeBalances(base.type, events), rate).copy(
        lastAdjustmentAt   = events.filter { it.category == ADJUSTMENT_CATEGORY }.maxOfOrNull { it.timestamp },
        firstEventAt       = events.minOfOrNull { it.timestamp },
    )

/**
 * Pone en [base] los saldos derivados ([balances], por moneda) y su estimado en pesos. Es la mitad
 * de [enrichWith] que no necesita los eventos sueltos: la usa también [cuentasConSaldo], que suma
 * en SQL.
 *
 * **Un bien sale SIEMPRE en cero**, tenga los movimientos que tenga. Es la garantía del contrato de
 * `Account.bien` en :core: un APK viejo no conoce ese campo y suma el `balance` a «Tu plata», así
 * que la única forma de que la casa no le aparezca como $1.412 millones disponibles es que el
 * valor NO viaje en `balance`. Y tiene que ser el server quien lo fuerce: un movimiento anotado
 * contra la casa desde ese mismo APK viejo (que la ofrece como cualquier inversión) no puede
 * convertirla en plata por la puerta de atrás.
 */
fun conSaldos(base: Account, balances: Map<String, Long>, rate: Double): Account =
    if (base.bien != null) {
        base.copy(balance = 0L, balancesByCurrency = emptyMap(), estimatedTotalCop = null)
    } else {
        base.copy(
            balance            = balances["COP"] ?: 0L,
            balancesByCurrency = balances,
            estimatedTotalCop  = estimatedTotalCop(balances, rate),
        )
    }
