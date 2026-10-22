package com.jvillada.movi.platform

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Ver el `expect`. Una escala de 0 es «Quitar animaciones»; sin poder leerla, el movimiento sigue. */
@Composable
actual fun movimientoReducidoDelSistema(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        runCatching {
            Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
