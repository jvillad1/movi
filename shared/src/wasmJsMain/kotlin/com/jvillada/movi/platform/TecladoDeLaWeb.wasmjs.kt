package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget

/**
 * Ver el `expect`. Escucha `resize` y `scroll` del `visualViewport` (los dos cambian al abrir y cerrar
 * el teclado, y al desplazar la página por debajo) y relee el hueco, como `AlVolverElFocoALaVentana`
 * escucha la ventana. Un navegador sin `visualViewport` da cero: ahí no hay teclado en pantalla.
 */
@Composable
actual fun altoDelTecladoDeLaWeb(): Dp {
    var alto by remember { mutableStateOf(huecoDelTecladoEnPixelesCss()) }
    DisposableEffect(Unit) {
        val visual = elVisualViewport()
        val alCambiar: (Event) -> Unit = { alto = huecoDelTecladoEnPixelesCss() }
        visual?.addEventListener("resize", alCambiar)
        visual?.addEventListener("scroll", alCambiar)
        onDispose {
            visual?.removeEventListener("resize", alCambiar)
            visual?.removeEventListener("scroll", alCambiar)
        }
    }
    // En la web un dp de Compose es un píxel CSS (la densidad sale de `devicePixelRatio`).
    return alto.toFloat().dp
}

/** `window.visualViewport`, que el `Window` de Kotlin/Wasm no trae; `null` si el navegador no lo tiene. */
private fun elVisualViewport(): EventTarget? = js("window.visualViewport || null")

/** `innerHeight − visualViewport.height − offsetTop`, con el zoom en 1; cero si no hay `visualViewport`. */
private fun huecoDelTecladoEnPixelesCss(): Double = js(
    """(function () {
        var v = window.visualViewport;
        if (!v || Math.abs(v.scale - 1) > 0.01) return 0;
        var hueco = window.innerHeight - v.height - v.offsetTop;
        return hueco > 1 ? hueco : 0;
    })()""",
)
