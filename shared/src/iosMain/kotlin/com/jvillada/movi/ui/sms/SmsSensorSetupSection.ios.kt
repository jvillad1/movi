package com.jvillada.movi.ui.sms

import androidx.compose.runtime.Composable

/** iOS no puede leer SMS: la captura vive en el teléfono Android y aquí no se pinta nada. */
@Composable
actual fun SmsSensorSetupSection(onSynced: () -> Unit, conAccionPrincipal: Boolean) {
}

@Composable
actual fun rememberSmsCaptureReady(): Boolean = false

/** Vuelta 109: aquí no hay SMS que leer. */
@Composable
actual fun rememberEsteAparatoLeeSms(): Boolean = false
