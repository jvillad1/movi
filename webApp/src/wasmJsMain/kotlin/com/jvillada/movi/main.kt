package com.jvillada.movi

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.jvillada.movi.ui.components.ConDobleClicDelSistema

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow("Movi") {
        // El doble y el triple clic, con el tiempo del sistema (500 ms) en vez del de Compose
        // (300). Va acá y no adentro de `App()` porque el número correcto depende de si el gesto
        // se hace con un mouse o con un dedo: en Android y iOS manda el de su plataforma. Ver
        // [ViewConfigurationConDobleClicDelSistema] para la medición.
        ConDobleClicDelSistema {
            App()
        }
    }
}
