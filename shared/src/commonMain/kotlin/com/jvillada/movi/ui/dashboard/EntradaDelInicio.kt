package com.jvillada.movi.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.theme.LocalMovimientoReducido

/**
 * Cuánto dura la entrada del Inicio: la cifra grande contando desde cero y las barras creciendo.
 *
 * 600 ms: lo bastante para que se note que la cifra «llega» y lo bastante corto para que nadie la
 * espere. Es el techo que puso la especificación de la ola («≤600 ms, una sola vez»).
 */
const val DURACION_DE_LA_ENTRADA_MS: Int = 600

/**
 * **La entrada del Inicio, una sola vez.** Devuelve un progreso de 0 a 1 que el bloque [clave]
 * usa para contar su cifra o para hacer crecer sus barras.
 *
 * La especificación pidió «efecto wow, con mesura», y la mesura es la mitad difícil:
 *
 * - **Una vez por proceso y por bloque**, no por recomposición. Se anota en
 *   [DashboardDataCache.entradasHechas]: al volver al Inicio desde Movimientos, las cifras ya están
 *   donde tienen que estar. Una cifra que vuelve a contar desde cero cada vez que uno entra deja de
 *   ser un efecto y pasa a ser una espera.
 * - **Por bloque** y no para toda la pantalla, porque cada bloque llega cuando llega su respuesta:
 *   las categorías pueden aparecer un segundo después del hero, y con una sola marca ya gastada
 *   aparecerían con las barras quietas.
 * - **Solo cuando el bloque tiene datos** ([listo]): el hero se compone con «—» antes de que las
 *   cuentas contesten, y contar hasta un guion no tiene sentido.
 *
 * Al cerrar sesión la marca se borra (ver [DashboardDataCache.clear]): el próximo que entra ve su
 * Inicio llegar, como la primera vez.
 */
@Composable
fun rememberProgresoDeEntrada(clave: String, listo: Boolean = true): Float {
    val progreso = remember(clave) {
        Animatable(if (clave in DashboardDataCache.entradasHechas) 1f else 0f)
    }
    LaunchedEffect(clave, listo) {
        if (!listo || progreso.value >= 1f) return@LaunchedEffect
        progreso.animateTo(1f, tween(DURACION_DE_LA_ENTRADA_MS, easing = FastOutSlowInEasing))
        DashboardDataCache.entradasHechas += clave
    }
    return progreso.value
}

/**
 * La cifra que se muestra mientras cuenta: [valor] por el [progreso], redondeada a pesos, y
 * **exactamente** [valor] al terminar — sin el redondeo de coma flotante, que con $1.528 millones
 * dejaría la cifra final corrida en unos pesos.
 */
fun cifraContando(valor: Long, progreso: Float): Long =
    if (progreso >= 1f) valor else (valor * progreso.toDouble()).toLong()

/**
 * **La cifra del héroe nunca arranca en $0** (vuelta 39). Hasta acá entraba contando desde cero
 * ([rememberProgresoDeEntrada]), y en la web, al recargar con la instantánea a la vista, el héroe decía
 * «Te quedan $0 · para 4 días · $696.137 por día» varios segundos: la cuenta se quedaba esperando
 * cuadros que la página, ocupada con las diez lecturas, no le daba. Un «$0» de 40 px es la afirmación
 * más fuerte de la pantalla, y era falsa.
 *
 * Ahora la primera cifra se muestra **tal cual llega** (la de la instantánea o la de la carga), y solo
 * un cambio después —la carga nueva que reemplaza a la de ayer— se anima, de la cifra vieja a la nueva:
 * en el medio solo hay valores entre dos cifras reales. Con el sistema pidiendo menos movimiento
 * ([LocalMovimientoReducido]) el cambio es inmediato.
 */
@Composable
fun rememberCifraQueCambia(valor: Long): Long {
    val reducido = LocalMovimientoReducido.current
    var desde by remember { mutableStateOf(valor) }
    var hasta by remember { mutableStateOf(valor) }
    val progreso = remember { Animatable(1f) }
    LaunchedEffect(valor) {
        if (valor == hasta) return@LaunchedEffect
        desde = cifraEntre(desde, hasta, progreso.value)
        hasta = valor
        if (reducido) {
            progreso.snapTo(1f)
        } else {
            progreso.snapTo(0f)
            progreso.animateTo(1f, tween(DURACION_DE_LA_ENTRADA_MS, easing = FastOutSlowInEasing))
        }
    }
    // Antes de que el efecto corra (el primer cuadro después del cambio) se sigue viendo la de antes.
    return cifraEntre(desde, hasta, progreso.value)
}

/** La cifra a [progreso] del camino de [desde] a [hasta]; exactamente [hasta] al terminar. Pura. */
fun cifraEntre(desde: Long, hasta: Long, progreso: Float): Long =
    if (progreso >= 1f) hasta else desde + ((hasta - desde) * progreso.toDouble()).toLong()
