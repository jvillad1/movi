package com.jvillada.movi.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.jvillada.movi.observabilidad.Uso

@Composable
actual fun BackHandlerEffect(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled) {
        // Vuelta 104: el atrás del sistema (`toque:atras`, `como=SISTEMA`), frente a la flecha ← de la
        // barra de arriba (`como=FLECHA`). Solo cuando la app lo atiende: con todo cerrado, lo atiende Android.
        Uso.atrasDelSistema()
        onBack()
    }
}
