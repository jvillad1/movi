package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.cargosYaCobradosEnElMes
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DesgloseDeCuota
import com.jvillada.movi.shared.model.desglosarCuotaRegistrada
import com.jvillada.movi.shared.model.signedDelta
import com.jvillada.movi.shared.model.validarInteresReal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

/** El desglose de un pago a una deuda, o por qué el interés que escribió el dueño no cabe. */
internal sealed interface DesgloseDelPago {
    data class Bien(val desglose: DesgloseDeCuota) : DesgloseDelPago
    data class Mal(val motivo: String) : DesgloseDelPago
}

/**
 * **Cuánto de este pago baja de verdad la deuda**, dentro de la transacción del llamador. Es la cuenta
 * que hacían igual, cada uno con su copia, el pago de cuota (`POST /api/payments/installment`) y la
 * Ola Y (`PUT /api/events/{id}/vincular-deuda`); ahora la usan ellos dos y la confirmación de un aviso
 * de dos patas ([escribirLasPatasDelAviso]). Ningún número sale de una fórmula nueva: el saldo de la
 * deuda, lo que ya cobró en el mes ([cargosYaCobradosEnElMes]), la validación del interés real
 * ([validarInteresReal]) y el reparto ([desglosarCuotaRegistrada]) son los de siempre.
 *
 * - **Sin las patas de este mismo pago** ([transferId]): un reintento calcula lo mismo que el primero.
 * - **Por moneda**, como `computeBalances`: la deuda de esta moneda, no una suma de varias.
 * - [cuota] es lo que baja la deuda **en su moneda** (la conversión, si hacía falta, ya pasó).
 */
internal fun Transaction.desgloseDelPagoDeDeuda(
    uid: String,
    deuda: Account,
    transferId: String,
    timestamp: Long,
    cuota: Long,
    interesReal: Long?,
): DesgloseDelPago {
    val eventos = loadNonVoidedEventsIn(uid, deuda.id)
        .filter { it.transferId != transferId && it.currency == deuda.currency }
    val saldoAntesDelPago = eventos.sumOf { signedDelta(deuda.type, it.type, it.amount) }
    val terms = if (deuda.type == AccountType.LOAN) {
        Credits.selectAll()
            .where { (Credits.userId eq uid) and (Credits.accountId eq deuda.id) }
            .firstOrNull()?.toCreditTerms()
    } else {
        null
    }
    // Lo que ya cubrieron los otros pagos de esta deuda en la misma cuota (ver `cargosYaCobradosEnElMes`).
    val delMes = eventos.filter { it.noAmortiza != null }
    // La plata que salió de la cuenta en cada pago de antes: la otra pata de su par.
    val pares = delMes.mapNotNull { it.transferId }.toSet()
    val pagadoPorPar = if (pares.isEmpty()) emptyMap() else {
        Events.selectAll()
            .where { (Events.userId eq uid) and (Events.transferId inList pares) and (Events.accountId neq deuda.id) }
            .associate { it[Events.transferId]!! to it[Events.amount] }
    }
    val yaCobradoEnElMes = cargosYaCobradosEnElMes(delMes, epochMillisToAppDate(timestamp), terms?.dayOfMonth) { fila ->
        fila.transferId?.let { pagadoPorPar[it] }
    }
    validarInteresReal(
        interesReal, cuota, deuda.type,
        terms?.insuranceMonthly, terms?.otrosCargosMensuales, yaCobradoEnElMes,
    )?.let { return DesgloseDelPago.Mal(it) }
    return DesgloseDelPago.Bien(
        desglosarCuotaRegistrada(
            cuota = cuota,
            tipoDeLaDeuda = deuda.type,
            saldoDeLaDeuda = saldoAntesDelPago,
            rateEa = terms?.rateEa,
            seguroMensual = terms?.insuranceMonthly,
            otrosCargosMensuales = terms?.otrosCargosMensuales,
            sinIntereses = terms?.sinIntereses ?: false,
            interesReal = interesReal,
            yaCobradoEnElMes = yaCobradoEnElMes,
        ),
    )
}
