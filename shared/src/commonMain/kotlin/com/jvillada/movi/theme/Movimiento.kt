package com.jvillada.movi.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * # El movimiento de Movi, en un solo lugar
 *
 * Ola 1 · «Que Movi se sienta viva» (revisión del 22-oct-2026 contra Revolut y Monzo): hasta acá
 * Movi no se movía —las pantallas cambiaban de golpe y las hojas aparecían sin entrar—, y lo único
 * animado era el conteo de la cifra de Hoy y el pulso del esqueleto.
 *
 * **El principio es el de Monzo: movimiento con mesura.** Se mueve la navegación y unos pocos
 * momentos clave; nada decorativo. Las duraciones son cortas (180–280 ms) y las curvas, las
 * estándar de Material: lo que **entra** desacelera con énfasis (llega rápido y se asienta), lo que
 * **sale** acelera (se va sin hacerse esperar).
 *
 * Toda animación de la app sale de acá —duraciones, curvas y specs—, para que «más rápido» o «más
 * suave» sea un cambio de una línea y no una búsqueda por treinta archivos.
 *
 * ### Reducir movimiento
 *
 * Si el sistema pide menos movimiento ([LocalMovimientoReducido], ver
 * `com.jvillada.movi.platform.movimientoReducidoDelSistema`), los specs de acá devuelven [snap]:
 * el cambio pasa en el acto, sin deslizar ni fundir.
 */
object Movimiento {
    /** Lo más corto: un fundido entre pestañas, lo que sale. */
    const val CORTO_MS = 180

    /** Lo de en medio: el velo de una hoja, la salida de una pantalla apilada. */
    const val MEDIO_MS = 220

    /** Lo más largo: una pantalla o una hoja que entra. Nada de Movi dura más que esto. */
    const val LARGO_MS = 280

    /** Material «emphasized decelerate»: lo que entra llega rápido y se asienta. */
    val entrar: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material «emphasized accelerate»: lo que sale se va sin hacerse esperar. */
    val salir: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Material «standard»: lo que se mueve sin entrar ni salir (volver a su lugar). */
    val estandar: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Lo que entra: [LARGO_MS] con [entrar], o nada si el sistema pide menos movimiento. */
    fun <T> alEntrar(reducido: Boolean, duracion: Int = LARGO_MS): FiniteAnimationSpec<T> =
        if (reducido) snap() else tween(duracion, easing = entrar)

    /** Lo que sale: [MEDIO_MS] con [salir], o nada si el sistema pide menos movimiento. */
    fun <T> alSalir(reducido: Boolean, duracion: Int = MEDIO_MS): FiniteAnimationSpec<T> =
        if (reducido) snap() else tween(duracion, easing = salir)

    /** Lo que vuelve a su lugar (una hoja que se soltó antes del umbral). */
    fun <T> alVolver(reducido: Boolean, duracion: Int = MEDIO_MS): FiniteAnimationSpec<T> =
        if (reducido) snap() else tween(duracion, easing = estandar)
}

/**
 * ¿El sistema pidió reducir el movimiento? Lo decide `App()` una vez (ver
 * `movimientoReducidoDelSistema`) y lo lee todo lo que se mueve. `false` fuera de la app: las
 * pruebas que montan una pantalla suelta ven el movimiento de siempre.
 */
val LocalMovimientoReducido = staticCompositionLocalOf { false }
