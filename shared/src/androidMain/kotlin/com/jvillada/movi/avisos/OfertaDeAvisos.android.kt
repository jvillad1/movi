package com.jvillada.movi.avisos

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Actual Android: hay oferta solo en Android 13+, sin el permiso concedido y con la tarjeta todavía
 * sin contestar. Los dos botones la cierran para siempre ([PreferenciasDeAvisos.cerrarOferta]); el
 * diálogo del sistema sale únicamente al tocar «Activar avisos».
 */
@Composable
internal actual fun rememberOfertaDeAvisos(): OfertaDeAvisos? {
    val context = LocalContext.current
    // El launcher se registra siempre, haya oferta o no: las reglas de Compose no dejan llamarlo
    // dentro de un `if`, y registrarlo no muestra nada.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val hayQueOfrecer = remember(context) {
        avisosPidenPermiso() && !tienePermisoDeAvisos(context) && !PreferenciasDeAvisos.ofertaCerrada(context)
    }
    if (!hayQueOfrecer) return null
    return remember(context, launcher) {
        object : OfertaDeAvisos {
            override fun pedir() {
                PreferenciasDeAvisos.cerrarOferta(context)
                runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }

            override fun descartar() {
                PreferenciasDeAvisos.cerrarOferta(context)
            }
        }
    }
}
