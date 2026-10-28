package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.ui.components.formatMoneyCompact
import kotlinx.datetime.LocalDate

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
