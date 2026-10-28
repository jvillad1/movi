package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * La hoja de compartir del sistema (vuelta de diseño 26): `UIActivityViewController` presentado sobre
 * el controlador de vista de arriba. Si no hay ninguno a la vista, copia. Ver el KDoc de
 * [HojaDeCompartir].
 */
@Composable
internal actual fun hojaDeCompartirDeLaPlataforma(): HojaDeCompartir = HojaDeIos

private object HojaDeIos : HojaDeCompartir {
    override val abreLaHojaDelSistema: Boolean = true

    override fun compartir(texto: String) {
        val arriba = controladorDeArriba()
        if (arriba == null) {
            copiar(texto)
            return
        }
        val hoja = UIActivityViewController(activityItems = listOf(texto), applicationActivities = null)
        // En un iPad la hoja es un globo y necesita de dónde salir; sin esto, la app se cierra.
        hoja.popoverPresentationController?.sourceView = arriba.view
        arriba.presentViewController(hoja, animated = true, completion = null)
    }

    override fun copiar(texto: String) {
        UIPasteboard.generalPasteboard.string = texto
    }

    override fun mandarMensaje(texto: String): ComoSalioElMensaje =
        if (controladorDeArriba() != null) {
            compartir(texto)
            ComoSalioElMensaje.POR_LA_HOJA
        } else {
            copiar(texto)
            ComoSalioElMensaje.COPIADO
        }
}

/** El controlador de vista que se está viendo: la raíz de la ventana y, encima, lo que presentó. */
@Suppress("DEPRECATION")
private fun controladorDeArriba(): UIViewController? {
    var actual = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (actual?.presentedViewController != null) actual = actual.presentedViewController
    return actual
}
