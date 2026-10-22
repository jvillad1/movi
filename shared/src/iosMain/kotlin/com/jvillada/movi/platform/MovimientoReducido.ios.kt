package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** Ver el `expect`. «Reducir movimiento» de Accesibilidad. */
@Composable
actual fun movimientoReducidoDelSistema(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
