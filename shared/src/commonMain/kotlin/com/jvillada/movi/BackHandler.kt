package com.jvillada.movi

import androidx.compose.runtime.Composable

@Composable
expect fun BackHandlerEffect(enabled: Boolean = true, onBack: () -> Unit)
