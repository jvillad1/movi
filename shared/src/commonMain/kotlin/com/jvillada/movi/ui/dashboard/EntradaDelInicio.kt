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
import com.jvillada.movi.theme.Movimiento

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
 * # Vuelta 75 · **el héroe cambia entero, nunca a medias**
 *
 * **La cifra del héroe nunca arranca en $0** (vuelta 39): la primera se muestra tal cual llega (la de la
 * instantánea o la de la carga). Hasta la 74, un cambio después —la carga nueva que reemplaza a la de
 * ayer— animaba **solo la cifra grande**, de la vieja a la nueva en 600 ms, mientras «Tu plata» y «para N
 * días · $X por día» cambiaban en el acto. En la web, con la página ocupada en las diez lecturas, la cuenta
 * se quedaba sin cuadros y durante un segundo convivían la cifra vieja y la línea nueva: «$1.417.943» junto
 * a «$441.916 por día», que es de «$1.325.750» (`r5-volver-300.png`).
 *
 * Ahora el héroe muestra **un solo estado a la vez** ([EstadoDelHeroe]: la cifra, su línea y «Tu plata»,
 * de la MISMA lectura): al llegar uno nuevo, el viejo se desvanece entero y el nuevo aparece entero
 * ([rememberEstadoQueCambia]). En ningún cuadro se ve una pieza del viejo con otra del nuevo. Con el
 * sistema pidiendo menos movimiento ([LocalMovimientoReducido]) el cambio es inmediato, y el primer dato
 * (después del esqueleto o del «—») se muestra sin esperar.
 */
data class EstadoDelHeroe(
    val teQuedan: TeQuedanDeHoy?,
    val tuPlata: Long?,
    /** «¿Me alcanza?» (revisión de la 75): el renglón cambia con la cifra, no antes. */
    val renglon: RenglonMeAlcanza? = null,
)

/**
 * **¿El paso de [antes] a [despues] es una cifra que cambia?** Solo entre dos cifras del mismo tipo (las
 * dos «Te quedan» o las dos «Tu plata»): el primer dato después del esqueleto o del «—» llega sin esperar,
 * y pasar de «Tu plata» a «Te quedan» no es una cifra que cambia. Que aparezca el renglón «¿Me alcanza?»
 * donde no había, sin que cambie nada más, tampoco: entra sin hacer parpadear la cifra. Pura.
 */
fun esUnCambioDelHeroe(antes: EstadoDelHeroe, despues: EstadoDelHeroe): Boolean {
    if (antes.renglon == null && antes == despues.copy(renglon = null)) return false
    return (antes.teQuedan != null && despues.teQuedan != null) ||
        (antes.teQuedan == null && despues.teQuedan == null && antes.tuPlata != null && despues.tuPlata != null)
}

/** Lo que se pinta en un cuadro: el estado que se ve y con qué opacidad (0 a 1). */
data class EstadoQueSeVe<T>(val mostrado: T, val opacidad: Float)

/** Cuánto dura el cambio del héroe: la mitad se va el viejo, la otra mitad llega el nuevo. */
const val DURACION_DEL_CAMBIO_DEL_HEROE_MS: Int = Movimiento.LARGO_MS

/**
 * **Un estado que cambia entero.** Mientras dura el cambio, la primera mitad se ve [valor] viejo
 * desvaneciéndose y la segunda el nuevo apareciendo: nunca los dos, nunca una mezcla. Antes de que el
 * efecto corra (el primer cuadro después del cambio) se sigue viendo el viejo, entero.
 *
 * @param seAnima si el paso de uno a otro se anima; si no (el primer dato después del esqueleto), es
 *   inmediato.
 * @param congelado revisión de la 75: **mientras la carga está en curso** lo visible no cambia por un
 *   valor que se animaría —las cuentas llegan antes que el resumen, y el estado intermedio junta piezas
 *   de dos cargas—; al terminar la carga, cambia entero al último. Lo que no se anima (el primer dato)
 *   entra igual.
 */
@Composable
fun <T> rememberEstadoQueCambia(
    valor: T,
    congelado: Boolean = false,
    seAnima: (antes: T, despues: T) -> Boolean,
): EstadoQueSeVe<T> {
    val reducido = LocalMovimientoReducido.current
    var desde by remember { mutableStateOf(valor) }
    var hasta by remember { mutableStateOf(valor) }
    val progreso = remember { Animatable(1f) }
    LaunchedEffect(valor, congelado) {
        if (valor == hasta && progreso.value >= 1f) return@LaunchedEffect
        val visible = estadoEnElCambio(desde, hasta, progreso.value)
        // Con la carga en vuelo, lo que ya se ve se queda: el cambio llega entero cuando termina.
        if (congelado && visible != valor && seAnima(visible, valor)) return@LaunchedEffect
        if (reducido || visible == valor || !seAnima(visible, valor)) {
            desde = valor
            hasta = valor
            progreso.snapTo(1f)
            return@LaunchedEffect
        }
        desde = visible
        hasta = valor
        progreso.snapTo(0f)
        progreso.animateTo(1f, tween(DURACION_DEL_CAMBIO_DEL_HEROE_MS, easing = FastOutSlowInEasing))
    }
    val p = progreso.value
    return EstadoQueSeVe(estadoEnElCambio(desde, hasta, p), opacidadEnElCambio(p))
}

/** El estado que se ve a [progreso] del cambio: el viejo hasta la mitad, el nuevo desde ahí. Pura. */
fun <T> estadoEnElCambio(desde: T, hasta: T, progreso: Float): T = if (progreso < 0.5f) desde else hasta

/** La opacidad a [progreso] del cambio: 1 → 0 hasta la mitad, 0 → 1 después. Pura. */
fun opacidadEnElCambio(progreso: Float): Float =
    if (progreso >= 1f) 1f else kotlin.math.abs(1f - 2f * progreso.coerceIn(0f, 1f))

/** La cifra a [progreso] del camino de [desde] a [hasta]; exactamente [hasta] al terminar. Pura. */
fun cifraEntre(desde: Long, hasta: Long, progreso: Float): Long =
    if (progreso >= 1f) hasta else desde + ((hasta - desde) * progreso.toDouble()).toLong()
