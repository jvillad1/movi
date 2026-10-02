package com.jvillada.movi.ui.papeles

import androidx.compose.runtime.Composable

/**
 * **Soltar un archivo sobre Movi, en la web** (Ola 2): un comprobante o un extracto arrastrado desde
 * el escritorio entra a la misma cola que «Subir comprobante o extracto» ([PapelesCompartidos]) y
 * `App()` abre la hoja que lo lee — después de la puerta, como todo lo compartido.
 *
 * En Android e iOS no hace nada: ahí la puerta es la hoja de compartir del sistema.
 */
@Composable
expect fun EscucharArchivosSoltados()
