package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * En iOS el estilo de la barra de estado lo declara el `UIViewController` que hospeda a Compose
 * (`preferredStatusBarStyle`), y ese vive en el proyecto de Xcode, no acá. Con Movi clara (vuelta
 * 29) el estilo que corresponde es el de íconos oscuros, que es el que iOS elige solo sobre un fondo
 * claro con el estilo por defecto.
 */
@Composable
actual fun AjustarBarrasDelSistema() = Unit
