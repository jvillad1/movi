package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import platform.UIKit.UIPasteboard

/** Lo mínimo en iOS: copiar. Ver el KDoc de [HojaDeCompartir]. */
@Composable
internal actual fun hojaDeCompartirDeLaPlataforma(): HojaDeCompartir = HojaDeIos

private object HojaDeIos : HojaDeCompartir {
    override val abreLaHojaDelSistema: Boolean = false
    override fun compartir(texto: String) = copiar(texto)
    override fun copiar(texto: String) {
        UIPasteboard.generalPasteboard.string = texto
    }
}
