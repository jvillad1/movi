package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # «Tus períodos»: cómo le fue al dueño en cada ciclo
 *
 * Lo que el server contesta en `GET /api/periodos` (una fila por período, del en curso al más
 * viejo) y en `GET /api/periodos/{id}` (el detalle de uno). Las cifras salen de las MISMAS reglas
 * que el resto de la app —el flujo de caja de «Gastos» y de las barras de Presupuestos, el
 * emparejamiento del checklist, el saldo de «Tu plata»—, calculadas sobre la ventana de ese período
 * con el corte y los inicios propios del dueño.
 *
 * Todos los campos tienen valor por defecto: un cliente que no conozca uno nuevo lo ignora, y uno
 * que reciba una respuesta vieja no revienta.
 */
@Serializable
data class ResumenDePeriodo(
    /** El prefijo del período, `"2026-09"`: el mes que le da nombre (ver [PeriodoFinanciero.prefijo]). */
    val id: String,
    /** «Septiembre 2026» — ver [tituloDelPeriodo]. */
    val nombre: String,
    /** El primer día del período, ISO. */
    val desde: String,
    /** El último día **incluido** del período, ISO (no el arranque del siguiente). */
    val hasta: String,
    /** `true` si este período arrancó otro día que el de su corte (ver [PeriodSettings.iniciosPropios]). */
    val inicioPropio: Boolean = false,
    /** `true` solo en el período que contiene hoy. */
    val enCurso: Boolean = false,
    /** Lo que entró, con la regla de «Ingresos» (flujo de caja, en pesos, sin anulados ni «Por confirmar»). */
    val entradas: Long = 0,
    /** Lo que salió, con la regla de «Gastos» (la misma suma que las barras de Presupuestos). */
    val salidas: Long = 0,
    /** Cuántos movimientos suman en [entradas] y [salidas]. */
    val movimientos: Int = 0,
)

/** El detalle de un período. Ver [ResumenDePeriodo] para el porqué de las reglas. */
@Serializable
data class DetalleDePeriodo(
    val resumen: ResumenDePeriodo,
    /** El gasto del período por categoría, de mayor a menor. */
    val porCategoria: List<GastoDeCategoria> = emptyList(),
    /** Cada recurrente con el vencimiento que cae en este período y cómo quedó. */
    val pagosFijos: List<PagoFijoDelPeriodo> = emptyList(),
    /** Los presupuestos de hoy, con lo que se gastó en esta ventana. */
    val presupuestos: List<PresupuestoDelPeriodo> = emptyList(),
    /** Los cinco gastos de flujo más grandes del período (vivos, confirmados, en pesos). */
    val masGrandes: List<FinancialEvent> = emptyList(),
    /**
     * Lo que había en «Tu plata» a las 00:00 del primer día. `null` cuando no se puede decir sin
     * mentir: antes de que Movi conociera el saldo inicial de todas las cuentas de Tu plata, o con
     * movimientos en otra moneda en una de ellas, que una suma en pesos dejaría afuera en silencio.
     */
    val tuPlataAlEmpezar: Long? = null,
    /** Lo mismo al cerrar el período; en el período en curso, lo que hay hoy (hasta el final del día). */
    val tuPlataAlCerrar: Long? = null,
)

@Serializable
data class GastoDeCategoria(val category: String, val monto: Long)

/**
 * Un recurrente en un período.
 *
 * [estado] es un `String` y no un enum a propósito: un valor nuevo mañana no puede romper la
 * deserialización de un cliente instalado. Hoy vale [PAGO_FIJO_LISTO], [PAGO_FIJO_PENDIENTE] o
 * [PAGO_FIJO_CON_DUDAS].
 */
@Serializable
data class PagoFijoDelPeriodo(
    val ruleId: String,
    val nombre: String,
    /** Lo que dice la regla. */
    val monto: Long,
    val esIngreso: Boolean,
    /** El vencimiento de este período, ISO. */
    val vencimiento: String,
    val estado: String,
    /** El movimiento que lo prueba, si hay uno (un «ya lo pagué» sin movimiento no tiene). */
    val eventId: String? = null,
    /** Lo que de verdad se movió, en pesos, si hay movimiento y es en pesos. */
    val montoReal: Long? = null,
)

/** Sellado a mano o emparejado por Movi con un único movimiento concluyente. */
const val PAGO_FIJO_LISTO: String = "LISTO"

/** Sin sello ni movimiento que lo pruebe (o todavía no llegó). */
const val PAGO_FIJO_PENDIENTE: String = "PENDIENTE"

/** Hay dos o más movimientos que podrían serlo y Movi no elige: se lo pregunta al dueño. */
const val PAGO_FIJO_CON_DUDAS: String = "CON_DUDAS"

@Serializable
data class PresupuestoDelPeriodo(val category: String, val limite: Long, val gastado: Long)
