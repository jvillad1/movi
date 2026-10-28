package com.jvillada.movi.shared.model

import kotlin.math.abs

/*
 * # «¿Se repite todos los meses?» solo cuando ya se repitió (vuelta 44)
 *
 * Después de guardar un gasto, Movi ofrecía «¿"Mercado" se repite todos los meses? Sí, anótalo»
 * casi siempre: después de repartir el mercado, después de $15.000 en efectivo en un restaurante.
 * Era ruido. Ahora se ofrece solo si Movi **ya vio** ese mismo gasto —el mismo comercio o título
 * ([claveDeComercio]), con un monto parecido— en dos períodos anteriores distintos. Las guardas de
 * siempre (traspasos, categorías reservadas, una regla o una suscripción con ese nombre, cuántas
 * veces se ofreció en la sesión) siguen en la app; esto es la evidencia, y las tres veces en que no
 * se ofrece nunca.
 */

/** Cuánto puede variar el monto entre una vez y otra para que sea «el mismo gasto»: ±15 % (elegido). */
const val MARGEN_DEL_MONTO_QUE_SE_REPITE: Int = 15

/** En cuántos períodos anteriores distintos tiene que haberse visto: dos (elegido). */
const val PERIODOS_PARA_PROPONER_EL_RECURRENTE: Int = 2

/** Un gasto en efectivo por debajo de esto no se propone como recurrente: $50.000 (elegido). */
const val EFECTIVO_QUE_NO_SE_PROPONE: Long = 50_000L

/**
 * **Cómo se guardó el movimiento**, lo que el movimiento solo no dice.
 *
 * @property seReparte se guardó para repartirlo con alguien (Repartir este gasto).
 * @property esEfectivo salió de una cuenta de efectivo.
 * @property saldoUnaOcurrencia es el pago de algo que Movi ya espera (una factura del período, la
 *   cuota de un crédito): ya hay una regla detrás.
 */
data class ComoSeGuardo(
    val seReparte: Boolean = false,
    val esEfectivo: Boolean = false,
    val saldoUnaOcurrencia: Boolean = false,
)

/** ¿[otro] es el mismo gasto que [evento]? Mismo tipo, moneda y comercio, y un monto parecido. */
private fun esElMismoGasto(evento: FinancialEvent, clave: String, otro: FinancialEvent): Boolean {
    if (otro.id == evento.id || otro.transferId != null) return false
    if (otro.type != evento.type || otro.currency != evento.currency) return false
    if (claveDeComercio(otro) != clave) return false
    // En enteros: 15 % de un monto en pesos no tiene por qué caer exacto en un `Double`.
    return abs(otro.amount - evento.amount) * 100 <= evento.amount * MARGEN_DEL_MONTO_QUE_SE_REPITE
}

/**
 * **¿Se le propone a [evento] volverse recurrente?**
 *
 * Nunca después de repartir, de un gasto en efectivo de menos de [EFECTIVO_QUE_NO_SE_PROPONE] ni de
 * un pago que saldó una ocurrencia ([como]); nunca si ya hay una regla o una suscripción para eso
 * ([yaHayReglaOSuscripcion]). Y solo si en [historia] (los movimientos vivos, sin anulados) el mismo
 * gasto aparece en al menos [PERIODOS_PARA_PROPONER_EL_RECURRENTE] períodos **anteriores** al del
 * movimiento, contados con el período del dueño ([ajustes]).
 */
fun seRepiteParaProponer(
    evento: FinancialEvent,
    historia: List<FinancialEvent>,
    ajustes: PeriodSettings,
    como: ComoSeGuardo = ComoSeGuardo(),
    yaHayReglaOSuscripcion: Boolean = false,
): Boolean {
    if (como.seReparte || evento.espacioId != null) return false
    if (como.esEfectivo && evento.amount < EFECTIVO_QUE_NO_SE_PROPONE) return false
    if (como.saldoUnaOcurrencia || yaHayReglaOSuscripcion) return false
    if (evento.transferId != null || evento.amount <= 0L) return false
    val clave = claveDeComercio(evento) ?: return false
    val suPeriodo = periodoDe(evento.timestamp, ajustes)
    val anteriores = historia
        .filter { esElMismoGasto(evento, clave, it) }
        .map { periodoDe(it.timestamp, ajustes) }
        .filter { it.prefijo < suPeriodo.prefijo }
        .toSet()
    return anteriores.size >= PERIODOS_PARA_PROPONER_EL_RECURRENTE
}
