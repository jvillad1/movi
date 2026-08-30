package com.jvillada.movi.ui.extractos

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // El nombre REAL del archivo, no el último segmento del `content://`.
        //
        // `uri.lastPathSegment` sobre un URI del selector de archivos de Android devuelve cosas
        // como `msf:1000000042` o `primary:Download/Extracto.pdf`: un id interno, no un nombre.
        // Daba igual mientras solo alimentaba la detección de banco por nombre, pero desde que
        // existe la pantalla de Documentos ese texto es **el nombre visible de la fila**, el
        // `filename` con el que se baja el archivo, y la entrada de `tipoSugeridoPara` — que con
        // un `msf:1000000042` clasifica todo como «Otro».
        //
        // `DISPLAY_NAME` del `contentResolver` es el nombre que el usuario ve en su explorador.
        // Si el proveedor no lo trae (pasa con algunos), se cae al comportamiento anterior en vez
        // de abortar: un nombre feo es mejor que no poder subir el archivo.
        val name = ctx.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && cursor.moveToFirst()) cursor.getString(i) else null
            }
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: "documento"
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        onResult(name, bytes, mime)
    }
    return { launcher.launch("*/*") }
}
