package com.jvillada.movi.platform

import kotlinx.browser.window

/** Ver el `expect`. Un navegador sin `matchMedia` (o que lanza) cuenta como «no se sabe»: se toca. */
internal actual fun elPunteroPrincipalEsFino(): Boolean =
    runCatching { window.matchMedia("(pointer: fine)").matches }.getOrDefault(false)
