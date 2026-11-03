package com.jvillada.movi

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import com.jvillada.movi.fallos.instalarLaCapturaDeFallos
import com.jvillada.movi.fallos.subirLosFallosGuardados
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

fun MainViewController() = ComposeUIViewController(
    configure = {
        enforceStrictPlistSanityCheck = false
        // Vuelta 117 · **el teclado lo acomoda la raíz, no iOS.** Por defecto Compose en iOS corre la
        // vista entera hacia arriba hasta que el campo con el foco quede encima del teclado
        // (`FocusableAboveKeyboard`), y además la raíz de `App.kt` descuenta el teclado con
        // `imePadding`: los dos a la vez movían la pantalla de más o de menos según el cuadro, y a
        // veces el campo de Movi AI quedaba debajo del teclado. Con `DoNothing` manda solo el inset,
        // como en Android.
        onFocusBehavior = OnFocusBehavior.DoNothing
    }
) { App() }.also {
    // Vuelta 100: el gancho de los fallos de Kotlin y la subida de lo que quedó del arranque anterior.
    instalarLaCapturaDeFallos()
    MainScope().launch { runCatching { subirLosFallosGuardados() } }
}
