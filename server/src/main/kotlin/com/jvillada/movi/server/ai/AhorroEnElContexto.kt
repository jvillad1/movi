package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.routes.ahorroDelPeriodoDe
import com.jvillada.movi.server.routes.montoDelAhorroDe
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.PreguntaSugerida
import java.time.LocalDate

/**
 * **[this] con el ahorro del período de [uid]**, o [this] tal cual si no aparta nada (y entonces no se
 * lee nada más: el contexto de quien no eligió un ahorro no cambia ni cuesta una consulta de más).
 *
 * Las dos cifras salen de donde las saca Hoy, nunca de una cuenta propia:
 * - el ahorro en pesos, de `montoDelAhorroDe` sobre lo que «Entró» ([ContextoDelPeriodo.ingresos],
 *   la misma suma que el resumen del Inicio);
 * - «Te quedan», de la tarjeta Disponible que arma «¿Cómo voy?» ([datosDeLasSugeridas] →
 *   `disponibleAlDia`, la MISMA `disponibleDelPeriodo` que pinta Hoy), que ya resta el ahorro.
 */
internal suspend fun ContextoDelPeriodo.conElAhorro(uid: String, hoy: LocalDate = AppClock.today()): ContextoDelPeriodo {
    val ajuste = dbQuery { ahorroDelPeriodoDe(uid) }
    if (!ajuste.apartaAlgo) return this
    val disponible = datosDeLasSugeridas(uid, PreguntaSugerida.COMO_VOY, hoy).disponible
    val monto = disponible?.ahorroDelPeriodo ?: dbQuery { montoDelAhorroDe(uid, ingresos) }
    return copy(
        ahorro = AhorroParaContexto(
            ajuste = ajuste,
            monto = monto,
            teQuedan = disponible?.periodo?.teQuedan,
            diasQueQuedan = disponible?.diasQueQuedan ?: diasQueQuedan,
        ),
    )
}
