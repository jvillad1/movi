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
)

/**
 * Un punto de la curva.
 *
 * @property mes el mes del punto, `"2026-03"`.
 * @property fecha el último día del mes, ISO; en el punto de hoy, hoy.
 * @property neto el patrimonio neto a esa fecha ([Patrimonio.neto]).
 * @property esHoy el último punto: lo que hay ahora, no un cierre.
 */
@Serializable
data class PuntoDelPatrimonio(
    val mes: String,
    val fecha: String,
    val neto: Long,
    val esHoy: Boolean = false,
)
