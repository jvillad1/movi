package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/** Ver el `expect`: en el teléfono lo dice el ciclo de vida, no el foco de una ventana. */
@Composable
actual fun AlVolverElFocoALaVentana(alVolver: () -> Unit) = Unit
