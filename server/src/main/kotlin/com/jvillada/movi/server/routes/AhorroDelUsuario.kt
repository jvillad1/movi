package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Users
import com.jvillada.movi.shared.model.AhorroDelPeriodo
import com.jvillada.movi.shared.model.montoDelAhorro
import com.jvillada.movi.shared.model.tipoDeAhorroDe
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll

/** El ahorro de cada período que eligió [uid] (ver `AhorroDelPeriodo.kt` en :core); sin elegir, nada. */
internal fun Transaction.ahorroDelPeriodoDe(uid: String): AhorroDelPeriodo {
    val fila = Users.select(Users.ahorroDelPeriodoTipo, Users.ahorroDelPeriodoValor)
        .where { Users.id eq uid }
        .firstOrNull() ?: return AhorroDelPeriodo()
    return AhorroDelPeriodo(tipoDeAhorroDe(fila[Users.ahorroDelPeriodoTipo]), fila[Users.ahorroDelPeriodoValor] ?: 0L)
}

/**
 * **Los pesos del ahorro del período de [uid]**, con [entro] = lo que entró en el período con la regla
 * de «Entró» (`monthCashFlow` / `flujoDeCaja`). La usan el resumen del Inicio y las preguntas
 * sugeridas de Movi AI: las dos pasan su «Entró» —la misma suma— y restan la misma cifra.
 *
 * No la usa el cierre del período, a propósito, igual que con los apartados: el cierre describe lo que
 * fue, y el ajuste es de hoy (no hay historia de cuánto apartaba en un período que ya cerró).
 */
internal fun Transaction.montoDelAhorroDe(uid: String, entro: Long): Long =
    montoDelAhorro(ahorroDelPeriodoDe(uid), entro)
