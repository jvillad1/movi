package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * En iOS el estilo de la barra de estado lo declara el `UIViewController` que hospeda a Compose
 * (`preferredStatusBarStyle`), y ese vive en el proyecto de Xcode, no acá. Cuando el tema claro
 * llegue a iOS hay que tocarlo allá; dejarlo escrito acá sería mentir sobre quién manda.
 */
@Composable
actual fun AjustarBarrasDelSistema(oscuro: Boolean) = Unit
