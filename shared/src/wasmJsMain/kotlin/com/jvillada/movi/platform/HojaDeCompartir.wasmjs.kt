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

@Composable
internal actual fun hojaDeCompartirDeLaPlataforma(): HojaDeCompartir = HojaDeLaWeb

private object HojaDeLaWeb : HojaDeCompartir {
    override val abreLaHojaDelSistema: Boolean = false
    override fun compartir(texto: String) { jsCopiar(texto) }
    override fun copiar(texto: String) { jsCopiar(texto) }
}
