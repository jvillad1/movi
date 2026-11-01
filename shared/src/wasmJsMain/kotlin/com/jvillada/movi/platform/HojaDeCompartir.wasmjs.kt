package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * Copiar en el navegador. `navigator.clipboard` pide que la página tenga el foco y, en algunos
 * navegadores, un gesto del usuario «reciente»; el clic de Compose llega por el canvas y a veces no
 * cuenta como tal. Si la escritura falla —o la API no existe—, se cae a `window.prompt` con el
 * enlace ya seleccionado: feo, pero el dueño nunca se queda sin poder copiarlo.
 *
 * El `try` envuelve todo por la misma razón que en `PushOptIn.wasmjs.kt`: una excepción de JS que
 * cruza a Kotlin en mitad de un clic se lleva la pantalla puesta.
 */
private fun jsCopiar(texto: String): Boolean =
    js("(function(t){try{var p=function(){window.prompt('Copia este enlace:', t);};if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(t).catch(p);}else{p();}return true;}catch(e){return false;}})(texto)")

/** ¿El navegador tiene `navigator.share` (la hoja de compartir del sistema, Web Share)? */
private fun jsPuedeCompartir(): Boolean =
    js("(function(){try{return !!(navigator.share);}catch(e){return false;}})()")

/**
 * Abre la hoja de compartir del navegador con [texto]. Si la persona la cierra (`AbortError`) no pasa
 * nada; si el navegador la niega (por ejemplo, porque el clic del lienzo no contó como gesto), copia,
 * con el `window.prompt` de respaldo de [jsCopiar].
 */
private fun jsCompartirMensaje(texto: String): Boolean =
    js("(function(t){try{var p=function(){window.prompt('Copia este mensaje:', t);};var c=function(){if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(t).catch(p);}else{p();}};navigator.share({text:t}).catch(function(e){if(!e||e.name!=='AbortError'){c();}});return true;}catch(e){return false;}})(texto)")

/**
 * Descarga [contenido] como el archivo [nombre]: un `Blob` y un `<a download>` que se pulsa solo. Con el
 * `try` por la misma razón que arriba.
 */
private fun jsDescargar(nombre: String, contenido: String, tipo: String): Boolean =
    js("(function(n,c,t){try{var b=new Blob([c],{type:t});var u=URL.createObjectURL(b);var a=document.createElement('a');a.href=u;a.download=n;a.style.display='none';document.body.appendChild(a);a.click();document.body.removeChild(a);setTimeout(function(){URL.revokeObjectURL(u);},10000);return true;}catch(e){return false;}})(nombre,contenido,tipo)")

@Composable
internal actual fun hojaDeCompartirDeLaPlataforma(): HojaDeCompartir = HojaDeLaWeb

private object HojaDeLaWeb : HojaDeCompartir {
    override val abreLaHojaDelSistema: Boolean = false
    override fun compartir(texto: String) { jsCopiar(texto) }
    override fun copiar(texto: String) { jsCopiar(texto) }

    override val puedeDescargar: Boolean = true
    override fun descargar(nombre: String, contenido: String, tipo: String): Boolean = jsDescargar(nombre, contenido, tipo)

    /**
     * Vuelta de diseño 26: con `navigator.share` (el teléfono, Safari, Chrome en Windows o macOS), la
     * hoja del navegador; si no, se copia y la pantalla dice «Mensaje copiado» con el enlace de
     * WhatsApp.
     */
    override fun mandarMensaje(texto: String): ComoSalioElMensaje =
        if (jsPuedeCompartir() && jsCompartirMensaje(texto)) {
            ComoSalioElMensaje.POR_LA_HOJA
        } else {
            jsCopiar(texto)
            ComoSalioElMensaje.COPIADO
        }
}
