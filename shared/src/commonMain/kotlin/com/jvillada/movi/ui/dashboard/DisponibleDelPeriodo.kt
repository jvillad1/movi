package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.ui.components.formatMoneyCompact
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/*
 * # «Disponible»: la cuenta vive en `:core`
 *
 * La regla —qué es el disponible, sus tres ventanas y sus metas— se mudó entera a `:core`
 * (`shared/model/DisponibleDelPeriodo.kt`) para que el server cuente «cuánto quedó del Disponible» en
 * el resumen de cierre con la MISMA función que pinta la tarjeta. Acá quedan los mismos nombres de
 * siempre —`typealias` y funciones que delegan—, así ninguna pantalla ni prueba cambió de import, y
 * las frases de la tarjeta, que son de la pantalla.
 */

typealias PlataDelDisponible = com.jvillada.movi.shared.model.PlataDelDisponible
typealias NivelDelGasto = com.jvillada.movi.shared.model.NivelDelGasto
typealias VentanaDelDisponible = com.jvillada.movi.shared.model.VentanaDelDisponible
typealias DisponibleDelPeriodo = com.jvillada.movi.shared.model.DisponibleDelPeriodo

/** Ver [com.jvillada.movi.shared.model.plataDelDisponibleDe]. */
fun plataDelDisponibleDe(summary: DashboardSummary): PlataDelDisponible? =
    com.jvillada.movi.shared.model.plataDelDisponibleDe(summary)

/** Ver [com.jvillada.movi.shared.model.fijosDelPeriodo]. */
fun fijosDelPeriodo(checklist: List<PagoDelPeriodo>): Long = com.jvillada.movi.shared.model.fijosDelPeriodo(checklist)

/** Ver [com.jvillada.movi.shared.model.ingresosPorRecibirDelPeriodo]. */
fun ingresosPorRecibirDelPeriodo(checklist: List<PagoDelPeriodo>): Long =
    com.jvillada.movi.shared.model.ingresosPorRecibirDelPeriodo(checklist)

/** Ver [com.jvillada.movi.shared.model.disponibleDelPeriodo]. */
fun disponibleDelPeriodo(
    ingresosRecibidos: Long,
    checklist: List<PagoDelPeriodo>,
    gastoVariablePorDia: Map<String, Long>,
    inicio: LocalDate,
    finExclusivo: LocalDate,
    hoy: LocalDate,
    plata: PlataDelDisponible? = null,
): DisponibleDelPeriodo? = com.jvillada.movi.shared.model.disponibleDelPeriodo(
    ingresosRecibidos, checklist, gastoVariablePorDia, inicio, finExclusivo, hoy, plata,
)

// ── Lo que dice cada fila ────────────────────────────────────────────────────
//
// Puro y aparte de la pantalla para que las pruebas lean las frases exactas. Todo en tuteo.

// «De dónde sale el disponible» ya no son dos líneas de texto: es la cascada de
// `CascadaDelDisponible.kt` (vuelta de diseño 28), renglón por renglón y con el total.

/** La frase cuando no hay margen: los fijos se llevan todo, o más de todo. */
internal fun sinMargen(d: DisponibleDelPeriodo): String {
    // En una variable: la clase vive en `:core` desde que se mudó, y Kotlin no hace smart cast de una
    // propiedad de otro módulo.
    val plata = d.plata
    if (plata == null) {
        return if (d.disponible < 0L) "Los fijos del período superan tus ingresos por ${formatMoneyCompact(-d.disponible)}"
        else "Los fijos del período se llevan todos tus ingresos"
    }
    // Con otros pagos de deuda, los fijos solos pueden no ser los que se llevan todo: se nombran los
    // dos. Y lo apartado, cuando sin él sí quedaría margen: es lo que se lleva lo último. Igual el
    // ahorro del período: es una elección, y se dice que es él el que deja sin margen.
    val otros = listOfNotNull(
        "los otros pagos de deuda".takeIf { plata.otrosPagosDeDeuda > 0L },
        "lo que apartaste".takeIf { plata.apartado > 0L && d.disponible + plata.apartado > 0L },
        "tu ahorro del período".takeIf { d.ahorroDelPeriodo > 0L && d.disponible + d.ahorroDelPeriodo > 0L },
    )
    val quienes = if (otros.isEmpty()) "Los fijos del período"
    else "Los fijos" + otros.dropLast(1).joinToString("") { ", $it" } + " y " + otros.last()
    return if (d.disponible < 0L) "$quienes superan lo que tenías y lo que entró por ${formatMoneyCompact(-d.disponible)}"
    else "$quienes se llevan todo lo que tenías y lo que entró"
}

/**
 * El rótulo corto de la columna de la semana: «Esta semana», o con sus días cuando la semana se
 * recorta en un borde del período («Esta semana · 4 días»). Sin la aclaración, una meta de la semana
 * más chica que la de siempre parecería un error.
 */
internal fun rotuloDeLaSemana(d: DisponibleDelPeriodo): String =
    if (d.semanaCorta) "Semana · ${d.diasDeLaSemana} ${if (d.diasDeLaSemana == 1) "día" else "días"}"
    else "Semana"

// La frase de cómo viene la tarjeta ya no se arma fila por fila: cada fila decía la suya y con el
// período pasado convivían «Te pasaste por $563.456» y «Vas bien» a un renglón de distancia. Ahora
// hay UNA frase por tarjeta y manda la peor ventana — ver `fraseDelDisponible` en
// `InicioDeUnVistazo.kt`.

// ── Vuelta 108 · sin margen para el día a día ────────────────────────────────

/**
 * **«Cuánto puedes gastar» cuando ya no hay margen** (vuelta 108). Antes la tarjeta decía «Semana $0 · ya te
 * pasaste» y «Meta de hoy $0»: dos ceros que no se distinguían de un «vas bien» (el 2-nov era lunes y no
 * había gastado nada) y no proponían nada. Ahora dice lo que sirve para decidir: que no hay margen hasta que
 * te paguen, lo que llevas gastado en el día a día contra lo que era libre, lo que llevas en la semana y hoy
 * (sin meta), y los tres pagos que más pesan en lo que falta.
 *
 * @property titulo «No hay margen para el día a día hasta el 25 nov».
 * @property diaADia «$1.950.929 · lo libre era $1.362.010».
 * @property pesan los pagos que más pesan: «Cuota Vehículo 8761 · 17 nov» y su monto.
 * @property verTodos «Ver los 9 pagos que faltan», o `null` sin pagos.
 */
data class SinMargenParaElDiaADia(
    val titulo: String,
    val diaADia: String,
    val semana: String,
    val hoy: String,
    val pesan: List<PagoQuePesa>,
    val verTodos: String?,
)

/** Vuelta 134 · un pago de «Lo que más pesa»: su nombre con la fecha, su monto y si es una estimación («unos»). */
data class PagoQuePesa(val nombre: String, val monto: Long, val estimada: Boolean = false)

/** El rótulo de la fila del período sin margen. */
const val DIA_A_DIA_DEL_PERIODO: String = "Día a día del período"

/** El rótulo de los pagos que más pesan. */
const val LO_QUE_MAS_PESA: String = "Lo que más pesa en lo que falta"

/** Cuántos pagos se nombran sin margen. */
const val PAGOS_QUE_MAS_PESAN: Int = 3

/**
 * [SinMargenParaElDiaADia] del disponible y de la lista del período. Solo tiene sentido con
 * [DisponibleDelPeriodo.yaTePasaste]; puro.
 *
 * @param pesos cómo se escriben las cifras (enteras, como el resto de la tarjeta).
 */
fun sinMargenParaElDiaADia(
    d: DisponibleDelPeriodo,
    checklist: List<PagoDelPeriodo>,
    hoy: LocalDate,
    conElPeriodoElegido: Boolean,
    pesos: (Long) -> String = { com.jvillada.movi.ui.components.formatMoney(it, "COP") },
): SinMargenParaElDiaADia {
    val dias = d.diasQueQuedan.coerceAtLeast(1)
    // El día que te pagan (el arranque del siguiente) si eligió su período; si no, el último día del mes.
    val hasta = hoy.plus(if (conElPeriodoElegido) dias else dias - 1, kotlinx.datetime.DateTimeUnit.DAY)
    val faltan = com.jvillada.movi.shared.model.pagosQueFaltanDelPeriodo(checklist)
    return SinMargenParaElDiaADia(
        titulo = "No hay margen para el día a día hasta el ${com.jvillada.movi.shared.model.fechaCortaDeLaPregunta(hasta)}",
        diaADia = pesos(d.periodo.gastado) +
            if (d.disponible > 0L) " · lo libre era ${pesos(d.disponible)}" else " · no había nada libre",
        semana = "llevas ${pesos(d.semana.gastado)}",
        hoy = "llevas ${pesos(d.hoy.gastado)}",
        pesan = faltan.take(PAGOS_QUE_MAS_PESAN).map { pago ->
            val cuando = runCatching { LocalDate.parse(pago.vence) }.getOrNull()
                ?.let { " · ${com.jvillada.movi.shared.model.fechaCortaDeLaPregunta(it)}" }.orEmpty()
            PagoQuePesa("${pago.nombre}$cuando", pago.monto, com.jvillada.movi.shared.model.laCifraDelPagoEsEstimada(pago))
        },
        verTodos = when (faltan.size) {
            0 -> null
            1 -> "Ver el pago que falta"
            else -> "Ver los ${faltan.size} pagos que faltan"
        },
    )
}
