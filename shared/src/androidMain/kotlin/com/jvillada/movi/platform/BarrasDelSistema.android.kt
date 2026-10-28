package com.jvillada.movi.platform

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * En Android lo decide `WindowInsetsController`: `isAppearanceLightStatusBars = true` significa
 * «la barra tiene fondo claro», o sea **íconos oscuros**, que es lo que pide el único tema de Movi.
 *
 * `SideEffect` y no `LaunchedEffect`: es una orden a una API imperativa de la ventana, no trabajo
 * suspendido.
 *
 * `view.isInEditMode` cubre la vista previa del IDE, donde no hay `Activity` detrás y el cast
 * tiraría. El `runCatching` cubre lo demás: esta llamada no puede tumbar la app por un adorno.
 */
@Composable
actual fun AjustarBarrasDelSistema() {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        runCatching {
            val ventana = (view.context as Activity).window
            WindowCompat.getInsetsController(ventana, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
}
