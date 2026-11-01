package com.jvillada.movi

import androidx.compose.ui.window.ComposeUIViewController
import com.jvillada.movi.fallos.instalarLaCapturaDeFallos
import com.jvillada.movi.fallos.subirLosFallosGuardados
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

fun MainViewController() = ComposeUIViewController(
    configure = { enforceStrictPlistSanityCheck = false }
) { App() }.also {
    // Vuelta 100: el gancho de los fallos de Kotlin y la subida de lo que quedó del arranque anterior.
    instalarLaCapturaDeFallos()
    MainScope().launch { runCatching { subirLosFallosGuardados() } }
}
