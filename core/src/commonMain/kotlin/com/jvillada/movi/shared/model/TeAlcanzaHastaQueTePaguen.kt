package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * # Vuelta 108 · «¿Te alcanza hasta que te paguen?»: una pregunta, una base
 *
 * El diagnóstico del 2-nov-2026: el Inicio decía «Te pasaste por $588.919 · del disponible del período ·
 * faltan 23 días para el corte», debajo «No te alcanza · el 24 nov quedarías en −$7M», y la sección
 * «Período $1.950.929 de $1.362.010». Tres cifras contestaban «¿cómo voy?» con tres bases distintas
 * (el disponible sin el apartado, la caja con el apartado y un supuesto inflado, el gasto contra el
 * disponible), y ninguna decía cuál mandaba. El dueño aprobó la propuesta A: **una sola pregunta y una
 * sola base**.
 *
 * - **La cifra** es [DisponibleDelPeriodo.loQueQueda]: lo libre del período menos lo gastado en el día a
 *   día. Por la identidad del disponible es lo mismo que *Tu plata libre + lo que te entra − los pagos que
 *   faltan − las compras con tarjeta sin pagar − tu ahorro*. Negativa → «Te faltan $X»; si no, «Te quedan
 *   $X». Sin margen ya no se cuenta «desde $0»: lo que faltó para los fijos también falta.
 * - **La línea que la explica**: con faltante, «Faltan $10,3M en pagos y tienes $9,7M libres, contando lo
 *   que te entra» (las dos cifras restan exactamente la de arriba: «libres» es la cifra + los pagos que
 *   faltan); con sobra, «Para 23 días · $53.900 por día».
 * - **La proyección, como detalle y con su supuesto a la vista**: la MISMA cifra menos los días que
 *   faltan (sin hoy) por el gasto de un día normal ([GastoDelDiaADia.porDia]). «Con tu gasto de un día
 *   normal, el 24 nov te faltarían unos $3,7M · Calculado con $143.000 por día». Nunca una base distinta:
 *   la cifra cuenta las compras con tarjeta (las sin pagar ya restan), así que el supuesto también
 *   ([GastoDelDiaADia.delHeroe], no el de la caja, que es solo efectivo). Lo fija
 *   `TeAlcanzaHastaQueTePaguenTest` contra una simulación día por día.
 *
 * **Una sola alarma en rojo**: la cifra. La proyección va en alerta solo si empeora el veredicto (hoy
 * alcanza y con el gasto de un día normal no).
 *
 * Sirve para cualquier período (mensual, quincenal, el que sea): todo sale de [DisponibleDelPeriodo] —sus
 * días que quedan, su cierre— y de la lista de ese período. Puro: lo llaman el héroe del Inicio, la
 * tarjeta «Cuánto puedes gastar» de Pagos, el widget, el resumen de la mañana y «¿Cómo voy?» de Movi AI,
 * para que todos digan lo mismo.
 */

/** El rótulo de la cifra cuando no alcanza. */
const val ROTULO_TE_FALTAN: String = "Te faltan"

/** El rótulo de la cifra cuando alcanza (o queda justo en cero). */
const val ROTULO_TE_QUEDAN_HASTA_QUE_TE_PAGUEN: String = "Te quedan"

/** El nombre de lo que antes se llamaba «Disponible del período» (vuelta 108). */
const val LIBRE_PARA_GASTAR: String = "Libre para gastar"

/** Lo que dice el renglón de la proyección, con su supuesto. */
data class ProyeccionHastaQueTePaguen(
    /** «Con tu gasto de un día normal, el 24 nov te faltarían unos $3,7M». */
    val texto: String,
    /** «Calculado con $91.000 por día» (o «con tus $91.000 por día», si lo eligió el dueño). */
    val supuesto: String,
    /** Lo que quedaría al final, con signo: `cifra − días × porDia`. */
    val alFinal: Long,
    /** El último día del período, ISO: el día antes de que te paguen. */
    val ultimoDia: String,
    /** Los días que se restan: los que faltan, sin hoy. */
    val dias: Int,
    val porDia: Long,
    /** El gasto de un día normal lo escribió el dueño en Pagos (no es el estimado de Movi). */
    val elegido: Boolean,
    /** Va en alerta solo si empeora el veredicto: hoy alcanza y con el gasto de un día normal no. */
    val enAlerta: Boolean,
)

/**
 * La respuesta entera a «¿te alcanza hasta que te paguen?».
 *
 * @property pregunta «Hasta que te paguen el 25 nov» (o «Hasta el 30 nov» con el mes de calendario).
 * @property cifra con signo: [DisponibleDelPeriodo.loQueQueda].
 * @property explicacion la línea de debajo de la cifra.
 * @property pagosQueFaltan los fijos del período que siguen pendientes (en pesos, sin tarjetas por saldo).
 * @property libres lo libre contando lo que te entra: `cifra + pagosQueFaltan`.
 * @property nivel cómo se pinta la explicación: [NivelDelGasto.PASADO] con faltante, [NivelDelGasto.CERCA] si
 *   va por encima del ritmo o no queda nada, lo de siempre si no.
 */
data class TeAlcanzaHastaQueTePaguen(
    val pregunta: String,
    val rotulo: String,
    val cifra: Long,
    val explicacion: String,
    val nivel: NivelDelGasto,
    val pagosQueFaltan: Long,
    val libres: Long,
    val diasQueQuedan: Int,
    val proyeccion: ProyeccionHastaQueTePaguen?,
) {
    val monto: Long get() = if (cifra < 0L) -cifra else cifra
    val enRojo: Boolean get() = cifra < 0L
}

/**
 * Los pagos fijos del período que faltan: la parte pendiente de [fijosDelPeriodo] (mismo filtro: sin
 * ingresos, sin tarjetas por saldo, sin abonos, solo pesos), del más grande al más chico.
 */
fun pagosQueFaltanDelPeriodo(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> =
    checklist
        .filter { !it.esIngreso && !it.pagado && !it.montoEsSaldo && !it.esAbono && !it.saleDeFueraDeTuPlata && it.moneda == "COP" && it.monto > 0L }
        .sortedWith(compareByDescending<PagoDelPeriodo> { it.monto }.thenBy { it.vence })

/**
 * [TeAlcanzaHastaQueTePaguen] del disponible del período.
 *
 * @param d el disponible, de [disponibleDelPeriodo] (la misma cuenta del Inicio y de Pagos).
 * @param checklist la lista del período ([checklistDelPeriodo]): de ahí salen los pagos que faltan.
 * @param gasto el supuesto del gasto de un día normal; `null` o sin [GastoDelDiaADia.porDia], sin proyección.
 * @param hoy la fecha de hoy en la zona de la app.
 * @param conElPeriodoElegido el dueño eligió su período (`!PeriodSettings.esMesDeCalendario`): su arranque es
 *   el día que le pagan, y la pregunta lo dice así.
 * @param pesos cómo se escribe una cifra en las frases: compacta («$2,6M»). La app le pasa la suya, que
 *   respeta «Ocultar los saldos»; el server, [pesosCortos].
 */
fun teAlcanzaHastaQueTePaguen(
    d: DisponibleDelPeriodo,
    checklist: List<PagoDelPeriodo>,
    gasto: GastoDelDiaADia?,
    hoy: LocalDate,
    conElPeriodoElegido: Boolean,
    pesos: (Long) -> String = ::pesosCortos,
): TeAlcanzaHastaQueTePaguen {
    val cifra = d.loQueQueda
    val diasQueQuedan = d.diasQueQuedan.coerceAtLeast(1)
    val ultimoDia = hoy.plus(diasQueQuedan - 1, DateTimeUnit.DAY)
    val cuandoTePagan = hoy.plus(diasQueQuedan, DateTimeUnit.DAY)
    val pregunta = preguntaHastaQueTePaguen(cuandoTePagan, conElPeriodoElegido)
    val pagosQueFaltan = pagosQueFaltanDelPeriodo(checklist).sumOf { it.monto }
    val libres = cifra + pagosQueFaltan
    val periodo = d.periodo
    val (explicacion, nivel) = when {
        cifra < 0L && pagosQueFaltan > 0L && libres > 0L ->
            "Faltan ${pesos(pagosQueFaltan)} en pagos y tienes ${pesos(libres)} libres, contando lo que te entra" to NivelDelGasto.PASADO
        cifra < 0L && pagosQueFaltan > 0L ->
            "Faltan ${pesos(pagosQueFaltan)} en pagos y no tienes plata libre para ellos" to NivelDelGasto.PASADO
        cifra < 0L && d.disponible > 0L ->
            "Gastaste ${pesos(periodo.gastado)} en el día a día y lo libre era ${pesos(d.disponible)}" to NivelDelGasto.PASADO
        cifra < 0L ->
            "Tus pagos fijos del período se llevaron más de lo que tenías" +
                (if (periodo.gastado > 0L) " · gastaste ${pesos(periodo.gastado)} en el día a día" else "") to NivelDelGasto.PASADO
        cifra == 0L -> "No te queda nada libre para el día a día" to NivelDelGasto.CERCA
        diasQueQuedan <= 1 -> "Para hoy" to periodo.nivel
        periodo.contraElRitmo > 0L ->
            "Para $diasQueQuedan días · vas ${pesos(periodo.contraElRitmo)} por encima de lo previsto a hoy" to NivelDelGasto.CERCA
        else -> "Para $diasQueQuedan días · ${pesos(cifra / diasQueQuedan)} por día" to periodo.nivel
    }
    return TeAlcanzaHastaQueTePaguen(
        pregunta = pregunta,
        rotulo = if (cifra < 0L) ROTULO_TE_FALTAN else ROTULO_TE_QUEDAN_HASTA_QUE_TE_PAGUEN,
        cifra = cifra,
        explicacion = explicacion,
        nivel = nivel,
        pagosQueFaltan = pagosQueFaltan,
        libres = libres,
        diasQueQuedan = diasQueQuedan,
        proyeccion = proyeccionHastaQueTePaguen(cifra, diasQueQuedan - 1, ultimoDia, gasto, pesos),
    )
}

/**
 * [teAlcanzaHastaQueTePaguen] con el día en ISO (`"2026-11-02"`): el server la llama así, porque no ve los
 * tipos de `kotlinx-datetime` (como [filasDeLaCaja]). `null` si la fecha no se entiende.
 */
fun teAlcanzaHastaQueTePaguen(
    d: DisponibleDelPeriodo,
    checklist: List<PagoDelPeriodo>,
    gasto: GastoDelDiaADia?,
    hoy: String,
    conElPeriodoElegido: Boolean,
): TeAlcanzaHastaQueTePaguen? {
    val dia = runCatching { LocalDate.parse(hoy) }.getOrNull() ?: return null
    return teAlcanzaHastaQueTePaguen(d, checklist, gasto, dia, conElPeriodoElegido)
}

/**
 * **La proyección**: la cifra de hoy menos [dias] días de gasto normal, o `null` sin supuesto o sin días
 * por delante (el último día del período no hay nada que proyectar).
 */
internal fun proyeccionHastaQueTePaguen(
    cifra: Long,
    dias: Int,
    ultimoDia: LocalDate,
    gasto: GastoDelDiaADia?,
    pesos: (Long) -> String,
): ProyeccionHastaQueTePaguen? {
    // Vuelta 108 (revisión, P1): el supuesto del HÉROE, con las compras con tarjeta. La cifra ya restó las
    // compras con tarjeta sin pagar; si la proyección no restara las que vienen, mezclaría dos bases.
    val porDia = gasto?.delHeroe?.coerceAtLeast(0L) ?: return null
    if (dias <= 0) return null
    val alFinal = cifra - dias * porDia
    val elDia = "el ${fechaCortaDeLaPregunta(ultimoDia)}"
    val elegido = gasto.elegido != null
    val texto = when {
        cifra < 0L -> "Con tu gasto de un día normal, $elDia te faltarían unos ${pesos(-alFinal)}"
        alFinal < 0L -> "Hoy te alcanza; con tu gasto de un día normal, $elDia te faltarían unos ${pesos(-alFinal)}"
        else -> "Con tu gasto de un día normal te alcanza: $elDia te quedarían unos ${pesos(alFinal)}"
    }
    val supuesto = if (elegido) "Calculado con tus ${pesos(porDia)} por día" else "Calculado con ${pesos(redondeadoAMil(porDia))} por día"
    return ProyeccionHastaQueTePaguen(
        texto = texto,
        supuesto = supuesto,
        alFinal = alFinal,
        ultimoDia = ultimoDia.toString(),
        dias = dias,
        porDia = porDia,
        elegido = elegido,
        enAlerta = cifra >= 0L && alFinal < 0L,
    )
}

/**
 * **La pregunta del héroe**: «Hasta que te paguen el 25 nov» si el dueño eligió su período (su arranque es el
 * día que le pagan), o «Hasta el 30 nov» (el último día) con el mes de calendario.
 *
 * @param cuandoTePagan el arranque del período siguiente.
 */
fun preguntaHastaQueTePaguen(cuandoTePagan: LocalDate, conElPeriodoElegido: Boolean): String =
    if (conElPeriodoElegido) "Hasta que te paguen el ${fechaCortaDeLaPregunta(cuandoTePagan)}"
    else "Hasta el ${fechaCortaDeLaPregunta(cuandoTePagan.minus(1, DateTimeUnit.DAY))}"

/**
 * El estimado se dice redondeado al mil («$91.000», no «$90.688»): es un supuesto, y seis cifras exactas
 * lo harían pasar por una medida. Por debajo de $10.000 va tal cual.
 */
fun redondeadoAMil(monto: Long): Long = if (monto < 10_000L) monto else (monto + 500L) / 1_000L * 1_000L

private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** «25 nov». */
fun fechaCortaDeLaPregunta(fecha: LocalDate): String = "${fecha.dayOfMonth} ${MESES_CORTOS[fecha.monthNumber - 1]}"

/**
 * **Plata en poco espacio**, en `:core`: «$588.919» por debajo del millón, «$2,6M» desde ahí. La misma
 * regla que la app usa en sus frases (`formatMoneyCompactSinOcultar`, lo vigila
 * `LasFrasesDelHeroeHablanComoLaAppTest`), para que el server diga las cifras como la app.
 */
fun pesosCortos(monto: Long): String {
    val abs = if (monto < 0L) -monto else monto
    val signo = if (monto < 0L) "−" else ""
    if (abs < 1_000_000L) return signo + "$" + abs.toString().reversed().chunked(3).joinToString(".").reversed()
    val decimas = (abs + 50_000L) / 100_000L
    val entero = (decimas / 10).toString().reversed().chunked(3).joinToString(".").reversed()
    val decimal = decimas % 10
    return if (decimal == 0L) "$signo$${entero}M" else "$signo$$entero,${decimal}M"
}
