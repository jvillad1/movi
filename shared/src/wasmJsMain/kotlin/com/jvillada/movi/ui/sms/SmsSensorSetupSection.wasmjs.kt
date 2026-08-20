package com.jvillada.movi.ui.sms

import androidx.compose.runtime.Composable

/** La web no puede leer SMS: la captura vive en el teléfono Android y aquí no se pinta nada. */
@Composable
actual fun SmsSensorSetupSection(onSynced: () -> Unit) {
}
