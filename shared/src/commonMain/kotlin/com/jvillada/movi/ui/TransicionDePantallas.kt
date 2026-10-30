package com.jvillada.movi.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.jvillada.movi.theme.Movimiento

/**
 * Cómo se va de una pantalla a otra. Lo decide [direccionDe] mirando la pila antes y después.
 */
enum class DireccionDeTransicion {
    /** Se apiló una pantalla: entra desde la derecha con un fundido. */
    APILAR,

    /** Se sacó la de arriba: el movimiento inverso, la de abajo vuelve desde la izquierda. */
    VOLVER,

    /**
     * Cambio de pestaña raíz (o la pila se reemplazó entera): un fundido corto, sin deslizar. Desde la
     * vuelta 79 es **fade-through**: sale lo viejo y después entra lo nuevo, nunca los dos a la vez.
     */
    FUNDIDO,

    /** La pantalla de arriba es la misma: no hay transición. */
    NINGUNA,
}

/**
 * **Hacia dónde se mueve la navegación**, comparando la pila [antes] con la de [despues].
 *
 * - Misma pantalla arriba → [DireccionDeTransicion.NINGUNA].
 * - [despues] es [antes] con algo más encima → [DireccionDeTransicion.APILAR]; salvo que lo de encima
 *   sea la raíz de otra pestaña (tocar la barra estando en Hoy): eso es un fundido.
 * - [despues] es [antes] con algo menos → [DireccionDeTransicion.VOLVER]; salvo que se llegue a otra
 *   pestaña desde su raíz (el «atrás» de Movimientos a Hoy) o saltando varios pisos (tocar Hoy
 *   estando en «Por revisar», abierto desde Movimientos): eso también es un fundido. Entre pestañas
 *   no hay «adelante» ni «atrás», y deslizar inventaría una dirección que no existe.
 * - Cualquier otro cambio (cambiar de pestaña, entrar, cerrar sesión, el destino de reserva de la
 *   flecha ‹) → [DireccionDeTransicion.FUNDIDO].
 *
 * Pura y sin Compose, para poder probarla (`DireccionDeTransicionTest`).
 */
fun direccionDe(antes: List<Screen>, despues: List<Screen>): DireccionDeTransicion {
    val de = antes.lastOrNull()
    val a = despues.lastOrNull()
    if (de == null || a == null) return DireccionDeTransicion.FUNDIDO
    if (de == a) return DireccionDeTransicion.NINGUNA
    val cambiaDePestana = destinoResaltado(de) != destinoResaltado(a)
    val seApilo = despues.size > antes.size && despues.subList(0, antes.size) == antes
    val seSaco = despues.size < antes.size && antes.subList(0, despues.size) == despues
    return when {
        seApilo -> if (cambiaDePestana && NavStack.esRaizDePestana(a)) DireccionDeTransicion.FUNDIDO else DireccionDeTransicion.APILAR
        seSaco -> if (cambiaDePestana && (NavStack.esRaizDePestana(de) || antes.size - despues.size > 1)) {
            DireccionDeTransicion.FUNDIDO
        } else {
            DireccionDeTransicion.VOLVER
        }
        else -> DireccionDeTransicion.FUNDIDO
    }
}

/**
 * La animación de cada [DireccionDeTransicion], con los tiempos de [Movimiento]. La pantalla que
 * entra no recorre todo el ancho: arranca a un cuarto y llega fundiéndose, que se lee como
 * «adelante» sin que el ojo tenga que seguir una pantalla entera cruzando.
 *
 * Con [reducido] no se anima nada: la pantalla nueva aparece en el acto.
 */
fun transicionEntrePantallas(
    direccion: DireccionDeTransicion,
    reducido: Boolean,
): ContentTransform {
    // Todas las pantallas llenan el hueco: no hay tamaño que animar (`sizeTransform = null`).
    if (reducido || direccion == DireccionDeTransicion.NINGUNA) {
        return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    }
    return when (direccion) {
        DireccionDeTransicion.APILAR -> ContentTransform(
            targetContentEnter = slideInHorizontally(Movimiento.alEntrar(false)) { it / 4 } +
                fadeIn(Movimiento.alEntrar(false, Movimiento.MEDIO_MS)),
            initialContentExit = slideOutHorizontally(Movimiento.alSalir(false)) { -it / 10 } +
                fadeOut(Movimiento.alSalir(false, Movimiento.CORTO_MS)),
            sizeTransform = null,
        )
        // Al volver, la que se va queda ENCIMA y se desliza hacia la derecha descubriendo la de
        // abajo: el inverso exacto de apilar.
        DireccionDeTransicion.VOLVER -> ContentTransform(
            targetContentEnter = slideInHorizontally(Movimiento.alEntrar(false)) { -it / 10 } +
                fadeIn(Movimiento.alEntrar(false, Movimiento.MEDIO_MS)),
            initialContentExit = slideOutHorizontally(Movimiento.alSalir(false)) { it / 4 } +
                fadeOut(Movimiento.alSalir(false, Movimiento.CORTO_MS)),
            targetContentZIndex = -1f,
            sizeTransform = null,
        )
        // Vuelta 79 · **fade-through** entre pestañas: primero se va lo viejo ([Movimiento.SALIDA_DEL_FUNDIDO_MS])
        // y recién después entra lo nuevo, fundiéndose desde 0,98. Antes los dos fundidos corrían a la vez y
        // a los 100 ms se veían el héroe de Inicio y Pagos encimados (`r7-pestana-pagos-100.png`).
        else -> ContentTransform(
            targetContentEnter = fadeIn(Movimiento.alEntrarDelFundido(false)) +
                scaleIn(Movimiento.alEntrarDelFundido(false), initialScale = Movimiento.ESCALA_AL_ENTRAR_LA_PESTANA),
            initialContentExit = fadeOut(Movimiento.alSalirDelFundido(false)),
            sizeTransform = null,
        )
    }
}

/**
 * **La opacidad de lo que sale y de lo que entra en el fade-through**, a [ms] de empezado (vuelta 79): la
 * misma cuenta que hacen los specs de [Movimiento] ([Movimiento.alSalirDelFundido] y
 * [Movimiento.alEntrarDelFundido]), muestreada. `DobleExposicionTest` recorre cada cuadro y exige que
 * nunca las dos pasen de 0 a la vez. Pura.
 */
fun opacidadesDelFundido(ms: Long): Pair<Float, Float> {
    val sale = Movimiento.alSalirDelFundido<Float>(false).vectorize(Float.VectorConverter)
        .getValueFromNanos(ms * 1_000_000L, AnimationVector1D(1f), AnimationVector1D(0f), AnimationVector1D(0f)).value
    val entra = Movimiento.alEntrarDelFundido<Float>(false).vectorize(Float.VectorConverter)
        .getValueFromNanos(ms * 1_000_000L, AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f)).value
    return sale.coerceIn(0f, 1f) to entra.coerceIn(0f, 1f)
}

/** Cuánto dura el fade-through entero, en ms: la salida y, después, la entrada. */
val DURACION_DEL_FUNDIDO_MS: Int = Movimiento.SALIDA_DEL_FUNDIDO_MS + Movimiento.MEDIO_MS
