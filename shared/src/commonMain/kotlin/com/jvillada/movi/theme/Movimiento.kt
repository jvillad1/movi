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

    /**
     * **Lo único que se repite: el cursor.** Medio ciclo del parpadeo del cursor de una cifra que se
     * escribe con el teclado físico (vuelta 34): encendido este tiempo, apagado otro tanto, como el
     * de los campos de texto de los sistemas. No es una transición —nada entra ni sale—, por eso no
     * cuenta para el «nada dura más que [LARGO_MS]». Con el sistema pidiendo menos movimiento el
     * cursor queda fijo.
     */
    const val PARPADEO_DEL_CURSOR_MS = 530

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

    // ── Vuelta 79 · que tocar se sienta bien ─────────────────────────────────

    /**
     * **Lo que tarda un toque en hundirse** (y en volver): ~100 ms con la curva [estandar]. Es lo único
     * más corto que [CORTO_MS]: un toque que tarda en responder se siente lento.
     */
    const val PRESIONAR_MS = 100

    /** **Cuánto se achica lo que se presiona**: una acción, una píldora, una fila, una tarjeta. */
    const val ESCALA_AL_PRESIONAR = 0.96f

    /** El `Boton` se achica un poco menos (es más ancho: 0,96 se notaba de más en uno de pantalla entera). */
    const val ESCALA_DEL_BOTON_AL_PRESIONAR = 0.97f

    /**
     * Una [Fila] de lista va de borde a borde: al 0,96 se despegaba de los lados de su tarjeta. Se hunde
     * apenas y la acompaña el velo.
     */
    const val ESCALA_DE_LA_FILA_AL_PRESIONAR = 0.985f

    /** El hundirse y el volver de un toque: [PRESIONAR_MS] con [estandar], o nada con menos movimiento. */
    fun <T> alPresionar(reducido: Boolean): FiniteAnimationSpec<T> =
        if (reducido) snap() else tween(PRESIONAR_MS, easing = estandar)

    /**
     * **El fundido entre pestañas es «fade-through»** (vuelta 79): primero se va lo viejo en este tiempo,
     * y recién después entra lo nuevo. Antes salían y entraban a la vez, y a los 100 ms se veían el héroe
     * de Inicio y la lista de Pagos encimados.
     */
    const val SALIDA_DEL_FUNDIDO_MS = 90

    /** Lo nuevo del fade-through entra desde este tamaño (casi el suyo): se asienta, no aparece. */
    const val ESCALA_AL_ENTRAR_LA_PESTANA = 0.98f

    /** Lo que sale en el fade-through: [SALIDA_DEL_FUNDIDO_MS] con [salir]. */
    fun <T> alSalirDelFundido(reducido: Boolean): FiniteAnimationSpec<T> =
        if (reducido) snap() else tween(SALIDA_DEL_FUNDIDO_MS, easing = salir)

    /** Lo que entra en el fade-through: [MEDIO_MS] con [entrar], **después** de que salió lo viejo. */
    fun <T> alEntrarDelFundido(reducido: Boolean): FiniteAnimationSpec<T> =
        if (reducido) snap() else tween(MEDIO_MS, delayMillis = SALIDA_DEL_FUNDIDO_MS, easing = entrar)
}

/**
 * ¿El sistema pidió reducir el movimiento? Lo decide `App()` una vez (ver
 * `movimientoReducidoDelSistema`) y lo lee todo lo que se mueve. `false` fuera de la app: las
 * pruebas que montan una pantalla suelta ven el movimiento de siempre.
 */
val LocalMovimientoReducido = staticCompositionLocalOf { false }
