package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandlerEffect(enabled: Boolean, onBack: () -> Unit) {
    // Web: no hardware back button
}
