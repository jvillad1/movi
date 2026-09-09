package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandlerEffect(enabled: Boolean, onBack: () -> Unit) {
    // iOS handles back navigation via native swipe gesture
}
