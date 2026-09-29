package com.jvillada.movi.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/** Ver el `expect`. `ComposeTarget` es el `<canvas>` de `webApp/.../index.html`. */
internal actual fun devolverElTecladoAlLienzo() {
    val lienzo = (document.getElementById("ComposeTarget") ?: document.querySelector("canvas")) as? HTMLElement
    // Solo si el foco quedó huérfano: no se le quita a nada que lo tenga de verdad.
    val activo = document.activeElement
    if (activo == null || activo == document.body) lienzo?.focus()
}
