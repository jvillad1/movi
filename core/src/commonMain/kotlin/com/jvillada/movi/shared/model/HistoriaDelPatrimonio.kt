package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # El patrimonio en el tiempo (Ola 4 · Ver la plata)
 *
 * Lo que contesta `GET /api/patrimonio/historia`: el patrimonio neto al cierre de cada mes de los
 * últimos doce (o desde el primer movimiento del dueño) y el de hoy, para la curva de Patrimonio.
 *
 * Cada punto sale de [patrimonioDe] —la única regla del patrimonio— sobre las cuentas con el saldo
 * que tenían a esa hora, sin anulados y con la convención de cada tipo de cuenta ([signedDelta]).
 * Lo que la historia **no sabe**, y no finge saber:
 *
 * - **Los bienes** no tienen historia de avalúos: cuentan con su valor de hoy en todos los puntos.
 * - **Los dólares** se pasan a pesos con la TRM de hoy, no con la de cada mes ([conLaTrmDeHoy]).
 * - **Lo apartado** no entra: no cambia el patrimonio neto (ver [Patrimonio.apartado]).
 * - **Un mes antes del primer movimiento** no aparece: no hay datos, y dibujarlo como cero sería
 *   inventar una caída.
 *
 * Todos los campos con valor por defecto: un cliente viejo no revienta con uno nuevo.
 */

/**
 * Cuántos cierres de mes trae la historia, además del punto de hoy. Lo usa el server para armarla y
 * la app para saber si la curva arranca donde arrancó el dueño (menos puntos) o donde la corta la
 * ventana (vuelta de diseño 23).
 */
const val MESES_DE_HISTORIA: Int = 12

@Serializable
data class HistoriaDelPatrimonio(
    /** Del más viejo al más nuevo; el último es hoy. Vacía si el dueño no tiene movimientos. */
    val puntos: List<PuntoDelPatrimonio> = emptyList(),
    /** Hay plata en otra moneda: los puntos la cuentan con la TRM de hoy. */
    val conLaTrmDeHoy: Boolean = false,
    /**
     * Vuelta 60: **el primer movimiento real del dueño** (ISO, `"2025-08-14"`): el más viejo que no es
     * una apertura ni un ajuste de saldo. Es desde cuándo «usas Movi» —el mismo arranque de «Tus
     * períodos», que tiene movimientos desde ahí—, no el primer punto de la curva. `null` con un
     * server viejo o sin movimientos.
     */
    val primerMovimiento: String? = null,
    /** Vuelta 60: hay bienes (la casa, el carro): cuentan con su valor de hoy en todos los puntos. */
    val conBienes: Boolean = false,
)

/**
 * **Lo que cambió el patrimonio entre dos puntos, y cuánto de eso son ajustes** (vuelta 60).
 *
 * @property cambio el neto del último menos el del primero.
 * @property porAjustes lo que de ese cambio vino de ajustes de saldo y aperturas hechos entre los dos
 *   puntos ([PuntoDelPatrimonio.ajustes]): correcciones de lo que Movi sabía, no plata ganada ni
 *   perdida.
 */
data class CambioDelPatrimonio(val cambio: Long, val porAjustes: Long) {
    /** El cambio sin los ajustes: lo que de verdad se movió. Resta pura, sin cifras nuevas. */
    val sinAjustes: Long get() = cambio - porAjustes
}

/** El [CambioDelPatrimonio] del primero al último de [puntos], o `null` con menos de dos. */
fun cambioDelPatrimonio(puntos: List<PuntoDelPatrimonio>): CambioDelPatrimonio? {
    if (puntos.size < 2) return null
    val primero = puntos.first()
    val ultimo = puntos.last()
    return CambioDelPatrimonio(cambio = ultimo.neto - primero.neto, porAjustes = ultimo.ajustes - primero.ajustes)
}

/**
 * Un punto de la curva.
 *
 * @property mes el mes del punto, `"2026-03"`.
 * @property fecha el último día del mes, ISO; en el punto de hoy, hoy.
 * @property neto el patrimonio neto a esa fecha ([Patrimonio.neto]).
 * @property esHoy el último punto: lo que hay ahora, no un cierre.
 * @property ajustes vuelta 60: **cuánto de [neto] viene de ajustes de saldo y aperturas** («Ajuste de
 *   saldo», «Saldo inicial»/«Deuda inicial»): el neto con todo menos el neto sin esos movimientos, a
 *   esa fecha. No son ganancia ni pérdida: son lo que Movi no sabía y el dueño le contó después
 *   (una deuda que ya existía, un saldo que se corrigió). La diferencia entre dos puntos dice cuánto
 *   del cambio fue eso ([cambioDelPatrimonio]). Cero con un server viejo.
 */
@Serializable
data class PuntoDelPatrimonio(
    val mes: String,
    val fecha: String,
    val neto: Long,
    val esHoy: Boolean = false,
    val ajustes: Long = 0L,
)
