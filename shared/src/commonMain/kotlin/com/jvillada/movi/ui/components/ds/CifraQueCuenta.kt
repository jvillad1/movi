package com.jvillada.movi.ui.components.ds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movimiento
import kotlinx.datetime.Clock

/*
 * # La cifra que cuenta cuando la cambió el dueño (vuelta 79)
 *
 * La revisión visual 7: al deshacer un gasto, la cifra del héroe de Inicio **saltaba** sin animación. Desde
 * la vuelta 75 un cambio de cifras que llega de una carga se desvanece entero y aparece entero (nunca la
 * cifra vieja con la línea nueva); eso está bien para lo que cambió por fuera, pero cuando el cambio lo
 * hizo el dueño —anotar, anular, deshacer— la cifra tiene que **contarse** hasta la nueva: es la respuesta
 * a lo que acaba de tocar. Al deshacer, cuenta hacia atrás, de vuelta a la de antes.
 *
 * [CambioDelDueno] lo marca el aviso de abajo ([AvisoDeDeshacer.mostrar] y lo que se deshace o se hace
 * desde él); el héroe lo toma una vez ([rememberCifraQueCuenta]) y cuenta en [Movimiento.LARGO_MS] con la
 * curva estándar. Con el sistema pidiendo menos movimiento, el cambio es inmediato.
 */

/**
 * **El último cambio de la plata lo hizo el dueño**, hace menos de [VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS]. Lo
 * marca el aviso «Anotado · Deshacer» y lo toma la cifra del héroe una sola vez. Lo limpia
 * `SessionManager.clear`.
 */
object CambioDelDueno {
    private var marcadoEn: Long? = null

    /** Ahora mismo el dueño cambió la plata (anotó, anuló o deshizo). */
    fun marcar(ahora: Long = Clock.System.now().toEpochMilliseconds()) {
        marcadoEn = ahora
    }

    /** ¿Hay un cambio del dueño sin tomar, de hace menos de [VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS]? */
    fun pendiente(ahora: Long = Clock.System.now().toEpochMilliseconds()): Boolean =
        marcadoEn?.let { ahora - it in 0..VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS } == true

    /** Lo toma (una sola cifra cuenta por cambio): `true` si estaba [pendiente]. */
    fun tomar(ahora: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        val habia = pendiente(ahora)
        marcadoEn = null
        return habia
    }

    fun limpiar() {
        marcadoEn = null
    }
}

/**
 * Cuánto vale la marca de [CambioDelDueno]: lo que tarda, holgado, en volver la carga del Inicio después de
 * anotar (o en volver al Inicio desde la pantalla donde se anotó).
 */
const val VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS: Long = 15_000L

/** Cuánto dura la cuenta de la cifra: [Movimiento.LARGO_MS] (~280 ms). */
const val DURACION_DE_LA_CIFRA_QUE_CUENTA_MS: Int = Movimiento.LARGO_MS

/** La cifra a [progreso] del camino de [desde] a [hasta]; exactamente [hasta] al terminar. Pura. */
fun cifraEnLaCuenta(desde: Long, hasta: Long, progreso: Float): Long =
    if (progreso >= 1f) hasta else desde + ((hasta - desde) * progreso.toDouble()).toLong()

/**
 * **La cifra que se muestra**: [valor], salvo mientras cuenta. Cuando [valor] cambia de una cifra a otra y
 * el cambio lo hizo el dueño ([CambioDelDueno.tomar]), cuenta de la vieja a la nueva en
 * [DURACION_DE_LA_CIFRA_QUE_CUENTA_MS]; si no, o con menos movimiento, cambia en el acto. `null` (todavía
 * no hay cifra) pasa tal cual.
 */
@Composable
fun rememberCifraQueCuenta(valor: Long?): Long? {
    val reducido = LocalMovimientoReducido.current
    var desde by remember { mutableStateOf(valor) }
    var hasta by remember { mutableStateOf(valor) }
    val progreso = remember { Animatable(1f) }
    LaunchedEffect(valor) {
        val antes = if (progreso.value >= 1f) hasta else hasta?.let { h -> desde?.let { cifraEnLaCuenta(it, h, progreso.value) } }
        if (valor == null || antes == null || antes == valor || reducido || !CambioDelDueno.tomar()) {
            desde = valor
            hasta = valor
            progreso.snapTo(1f)
            return@LaunchedEffect
        }
        desde = antes
        hasta = valor
        progreso.snapTo(0f)
        progreso.animateTo(1f, tween(DURACION_DE_LA_CIFRA_QUE_CUENTA_MS, easing = Movimiento.estandar))
    }
    val d = desde
    val h = hasta
    if (valor != h) {
        // Un cambio que no hizo el dueño (o con menos movimiento) se ve en el mismo cuadro: la cifra nunca
        // se queda un cuadro atrás de su línea (`ElHeroeNoMezclaTest`). Uno del dueño arranca de lo de antes.
        if (reducido || valor == null || h == null || !CambioDelDueno.pendiente()) return valor
        return if (progreso.value >= 1f) h else d?.let { cifraEnLaCuenta(it, h, progreso.value) }
    }
    return if (d == null || h == null) h else cifraEnLaCuenta(d, h, progreso.value)
}
