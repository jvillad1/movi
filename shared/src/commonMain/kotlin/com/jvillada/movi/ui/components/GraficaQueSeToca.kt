package com.jvillada.movi.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * # Una gráfica que se toca (Ola 4 · Ver la plata)
 *
 * Las gráficas de Movi están dibujadas a mano con `Canvas`. Esto les agrega lo que una gráfica de
 * plata necesita para leerse de verdad, sin librerías:
 *
 * - **Deslizar el dedo** (o pasar el mouse por encima, en la web) elige el punto que queda debajo:
 *   [alElegir] recibe su índice, y la pantalla dice su valor en el renglón de la leyenda. Al salir el
 *   mouse, `null`. Al soltar el dedo, la elección se queda: lo último que se miró sigue escrito.
 * - **Un toque** (apretar y soltar sin deslizar) llama a [alTocar] con el índice tocado, si hay.
 *
 * Los [cuantos] puntos se reparten parejo a lo ancho, como los dibujan las gráficas: el índice de
 * una `x` es la franja en la que cae. Si el dedo se mueve más que el umbral del sistema (un
 * desplazamiento de la lista, o un deslizamiento sobre la gráfica) ya no es un toque.
 *
 * **El mouse quieto no elige** (vuelta 45). «En qué se va» abría con el 25 oct elegido —«25 oct · $0 ·
 * período pasado $0»— sin que nadie tocara nada: el clic que la abrió dejó el puntero donde después
 * quedó la gráfica, y Compose avisa con un movimiento sintético cuando lo de abajo de un puntero quieto
 * cambia. Ahora, sin botón apretado, solo elige un movimiento que de verdad mueve el puntero
 * ([elMouseSeMovio]); la gráfica abre sin elegir y la leyenda dice el resumen.
 */
fun Modifier.graficaQueSeToca(
    cuantos: Int,
    /** Lo que dibuja la gráfica: si cambia (otro período, otros datos), los toques se leen contra lo nuevo. */
    datos: Any?,
    alElegir: (Int?) -> Unit,
    alTocar: ((Int) -> Unit)? = null,
    /**
     * `true` para una línea: los puntos van de borde a borde (el primero en x = 0, el último en el
     * ancho) y se elige el más cercano. `false` para barras: cada una ocupa su franja.
     */
    enPuntos: Boolean = false,
): Modifier = if (cuantos <= 0) this else pointerInput(cuantos, datos, alTocar != null, enPuntos) {
    val umbral = viewConfiguration.touchSlop
    fun indice(x: Float): Int {
        val fraccion = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        val i = if (enPuntos) kotlin.math.round(fraccion * (cuantos - 1)).toInt() else (fraccion * cuantos).toInt()
        return i.coerceIn(0, cuantos - 1)
    }
    awaitPointerEventScope {
        var desde: Offset? = null
        var deslizo = false
        // Dónde se vio el puntero sin botón apretado por última vez: lo que distingue al mouse que se
        // mueve del movimiento sintético de un puntero quieto (ver el KDoc).
        var ultimoSinApretar: Offset? = null
        while (true) {
            val evento = awaitPointerEvent()
            val cambio = evento.changes.firstOrNull() ?: continue
            when (evento.type) {
                PointerEventType.Press -> {
                    desde = cambio.position
                    deslizo = false
                    alElegir(indice(cambio.position.x))
                }
                PointerEventType.Move -> if (desde != null) {
                    if ((cambio.position - desde).getDistance() > umbral) deslizo = true
                    alElegir(indice(cambio.position.x))
                } else {
                    val antes = ultimoSinApretar
                    ultimoSinApretar = cambio.position
                    if (elMouseSeMovio(antes, cambio.position)) alElegir(indice(cambio.position.x))
                }
                PointerEventType.Enter -> if (desde == null) ultimoSinApretar = cambio.position
                PointerEventType.Release -> {
                    if (desde != null && !deslizo && !cambio.isConsumed) alTocar?.invoke(indice(cambio.position.x))
                    desde = null
                    ultimoSinApretar = cambio.position
                }
                PointerEventType.Exit -> if (desde == null) {
                    ultimoSinApretar = null
                    alElegir(null)
                }
                else -> Unit
            }
        }
    }
}

/**
 * **¿El puntero se movió de verdad?** Sin botón apretado, un movimiento cuenta solo si viene de un lugar
 * conocido y distinto: el primero que llega (sin [antes]) y el que repite la posición son los
 * sintéticos que Compose manda cuando cambia lo que hay debajo de un puntero quieto. Pura.
 */
internal fun elMouseSeMovio(antes: Offset?, ahora: Offset): Boolean = antes != null && antes != ahora
