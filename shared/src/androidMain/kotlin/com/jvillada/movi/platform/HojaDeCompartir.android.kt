package com.jvillada.movi.platform

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun hojaDeCompartirDeLaPlataforma(): HojaDeCompartir {
    val contexto = LocalContext.current
    return remember(contexto) { HojaDeAndroid(contexto) }
}

private class HojaDeAndroid(private val contexto: Context) : HojaDeCompartir {
    override val abreLaHojaDelSistema: Boolean = true

    override fun compartir(texto: String) {
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        val selector = Intent.createChooser(envio, "Compartir resumen").apply {
            // Desde algo que no es una Activity (el contexto de Compose puede venir envuelto),
            // Android exige una tarea nueva para arrancar otra pantalla.
            if (contexto !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        contexto.startActivity(selector)
    }

    override fun copiar(texto: String) {
        val portapapeles = contexto.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("Enlace de Movi", texto).apply {
            // **El enlace es una llave.** Desde Android 13 el sistema muestra una vista previa de lo
            // que se copia; marcándolo como sensible la tapa, igual que hace con una contraseña. En
            // versiones anteriores el extra simplemente se ignora (la constante es un String que el
            // compilador copia, así que no hace falta preguntar por la versión).
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        portapapeles.setPrimaryClip(clip)
    }
}
