package com.jvillada.movi.platform

/**
 * **Cinturón, además de los tirantes de `push.js`.**
 *
 * Estas cuatro funciones cruzan la frontera JS→Kotlin de forma SÍNCRONA, en mitad de una
 * composición. Una excepción del lado de JS no llega como un valor raro: entra a Kotlin como
 * `JsException` y se lleva puesta la pantalla que la estaba llamando — y, medido en el navegador
 * con el almacenamiento del sitio bloqueado, también **la navegación entera**: «Recurrentes» y
 * Perfil no abrían, y desde ahí ningún otro toque hacía nada.
 *
 * `push.js` ya no puede tirar (envuelve su `localStorage` en un `try`), pero es un archivo
 * estático que un navegador puede tener cacheado de una versión anterior, y `window.moviPush` lo
 * puede pisar cualquiera. El `try` de acá abajo hace que **ninguna versión de ese archivo**, ni
 * ninguna API del navegador que falte, pueda apagar una pantalla: sin push disponible se contesta
 * lo mismo que en un navegador que no lo soporta.
 */
private fun jsSupported(): Boolean =
    js("(function(){try{return !!(window.moviPush && window.moviPush.supported())}catch(e){return false}})()")

private fun jsStatus(): String =
    js("(function(){try{return window.moviPush ? window.moviPush.status() : 'unsupported'}catch(e){return 'unsupported'}})()")

private fun jsEnable(): Boolean =
    js("(function(){try{if(window.moviPush) window.moviPush.enable()}catch(e){}return true})()")

private fun jsDisable(): Boolean =
    js("(function(){try{if(window.moviPush) window.moviPush.disable()}catch(e){}return true})()")

actual object PushOptIn {
    actual val supported: Boolean get() = jsSupported()
    actual fun status(): String = jsStatus()
    actual fun enable() { jsEnable() }
    actual fun disable() { jsDisable() }
}
