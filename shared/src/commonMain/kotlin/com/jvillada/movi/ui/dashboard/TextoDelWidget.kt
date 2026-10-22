package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

/**
 * # Lo que dice el widget «Te quedan hoy» de Android
 *
 * Tres renglones: el rótulo, la cifra y de cuándo es. Sale **solo de la instantánea del Inicio** que
 * el teléfono ya guarda ([InstantaneaDelInicio]): un widget no pide red, y si pidiera tendría que
 * cargar la sesión en un proceso que la app no abrió.
 *
 * **La cifra es la del héroe de Hoy, no otra.** Si el héroe abre con «Te quedan $X» —la columna
 * «Período» de Plan, ver [teQuedanDelInicio]—, el widget dice eso mismo («Te pasaste por» si va en
 * rojo). Si no hay disponible que afirmar (sin ingresos ni plata, o la instantánea es de un período
 * que ya terminó: [conElPeriodoDe] tira sus cifras), el héroe vuelve a «Tu plata», y el widget
 * también. Dos lugares que contestaran distinto a «¿cuánto me queda?» serían dos lugares en los que
 * no se puede creer.
 *
 * Sin sesión o sin instantánea: «Abre Movi». Nunca un cero: un «$0» en la pantalla de inicio del
 * teléfono es la afirmación más fuerte que puede hacer, y sería falsa.
 *
 * Puro y en `commonMain` para probarlo sin teléfono (`TextoDelWidgetTest`); el widget de verdad
 * (`WidgetTeQuedan`, en `:androidApp`) solo lo pinta.
 */
data class TextoDelWidget(
    val rotulo: String,
    /** La cifra ya escrita («$4.963.400»), o `null` cuando no hay nada que afirmar. */
    val cifra: String?,
    /** «a las 9:40», «ayer a las 21:05», «el 3 de octubre a las 9:40», o qué hacer. */
    val pie: String,
    /** Con el mismo criterio que el héroe: «Te pasaste por», o «Tu plata» en negativo. */
    val enRojo: Boolean = false,
)

/** El rótulo cuando no hay nada que mostrar: lo único útil es abrir la app. */
const val WIDGET_ABRE_MOVI: String = "Abre Movi"

/** El pie de «Abre Movi». */
const val WIDGET_PARA_VER_CUANTO_TE_QUEDA: String = "para ver cuánto te queda"

/**
 * [TextoDelWidget] desde la instantánea guardada.
 *
 * @param instantanea lo que devolvió `InstantaneaDelInicio.datos(userId)`, o `null`.
 * @param selladaEn cuándo se guardó (epoch ms), o `null` si la guardó una versión que no lo anotaba.
 * @param haySesion sin sesión no se muestra nada aunque quede algo guardado.
 * @param ahora el reloj, para el período vigente y para el «ayer».
 * @param saldosOcultos «Ocultar los saldos» ([SaldosOcultosStore]): la cifra sale como
 *   [PLATA_OCULTA], igual que en el héroe. El rótulo y el pie no cambian; el rojo de «Te pasaste
 *   por» tampoco, porque el rótulo ya lo dice.
 */
fun textoDelWidget(
    instantanea: DashboardData?,
    selladaEn: Long?,
    haySesion: Boolean,
    ahora: Long,
    saldosOcultos: Boolean = SaldosOcultosStore.ocultos,
): TextoDelWidget {
    fun cifra(monto: Long) = if (saldosOcultos) PLATA_OCULTA else formatCOPSinOcultar(monto)
    val abreMovi = TextoDelWidget(WIDGET_ABRE_MOVI, null, WIDGET_PARA_VER_CUANTO_TE_QUEDA)
    if (!haySesion || instantanea == null) return abreMovi
    val hoy = epochMillisToAppDate(ahora)
    val data = instantanea.conElPeriodoDe(ahora)
    val pie = selladaEn?.let { deCuandoEs(it, ahora) } ?: "Toca para actualizar"
    teQuedanDelInicio(data, hoy)?.let { t ->
        return TextoDelWidget(t.rotulo, cifra(t.monto), pie, enRojo = t.enRojo)
    }
    val cuentas = data.accounts ?: return abreMovi
    // Con lo apartado, como el hero de Hoy: si no, el widget diría el colegio como plata libre.
    val tuPlata = heroBalance(cuentas, data.apartados.orEmpty()).tuPlata
    return TextoDelWidget(HERO_BALANCE_TITLE, cifra(tuPlata), pie, enRojo = tuPlata < 0)
}

/**
 * «a las 9:40» si es de hoy, «ayer a las 21:05», o «el 3 de octubre a las 9:40». La hora en 24 horas
 * y en la de Bogotá ([AppTimeZone]), la misma de toda la app: un widget que dijera «9:40» sin a.m. ni
 * p.m. dejaría la duda justo en la hora que importa.
 */
internal fun deCuandoEs(selladaEn: Long, ahora: Long): String {
    val momento = Instant.fromEpochMilliseconds(selladaEn).toLocalDateTime(AppTimeZone.zone)
    val hora = "${momento.hour}:${momento.minute.toString().padStart(2, '0')}"
    val hoy = epochMillisToAppDate(ahora)
    return when (val dia = etiquetaDeFecha(momento.date, hoy)) {
        "Hoy" -> "a las $hora"
        "Ayer" -> "ayer a las $hora"
        else -> "el $dia a las $hora"
    }
}
