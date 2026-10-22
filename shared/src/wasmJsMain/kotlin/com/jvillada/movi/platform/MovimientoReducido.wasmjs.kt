package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

/** Ver el `expect`. Un navegador sin `matchMedia` (o que lanza) cuenta como «sin preferencia». */
@Composable
actual fun movimientoReducidoDelSistema(): Boolean = remember {
    runCatching { window.matchMedia("(prefers-reduced-motion: reduce)").matches }.getOrDefault(false)
}
