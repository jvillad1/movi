package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * Ver el `expect`. Escucha `focus` en la ventana y `visibilitychange` en el documento (solo cuando vuelve
 * a `visible`). Los dos suelen llegar juntos al volver a la pestaña: quien lo usa decide cada cuánto
 * recargar (`debeRecargarAlVolverElFoco`).
 */
@Composable
actual fun AlVolverElFocoALaVentana(alVolver: () -> Unit) {
    val vigente by rememberUpdatedState(alVolver)
    DisposableEffect(Unit) {
        val alEnfocar: (Event) -> Unit = { vigente() }
        val alCambiarLaVisibilidad: (Event) -> Unit = {
            if (!laPestanaEstaOculta()) vigente()
        }
        window.addEventListener("focus", alEnfocar)
        document.addEventListener("visibilitychange", alCambiarLaVisibilidad)
        onDispose {
            window.removeEventListener("focus", alEnfocar)
            document.removeEventListener("visibilitychange", alCambiarLaVisibilidad)
        }
    }
}

/** `document.hidden`, que el `Document` de Kotlin/Wasm no trae. */
private fun laPestanaEstaOculta(): Boolean = js("document.hidden === true")
