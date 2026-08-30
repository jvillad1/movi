package com.jvillada.movi.ui.extractos

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Los tipos que el importador sabe leer. Mismo catálogo que la web y iOS — si divergen, el mismo
 * archivo se puede elegir en un teléfono y no en otro.
 */
private val MIMES_DE_EXTRACTO = arrayOf(
    "application/pdf",
    "text/csv",
    "text/comma-separated-values",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "image/*",
)

@Composable
actual fun rememberFilePicker(
    aceptar: TiposDeArchivo,
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit,
): () -> Unit {
    val ctx = LocalContext.current
    // `OpenDocument` y no `GetContent`: el primero acepta una LISTA de tipos, el segundo uno
    // solo. Con `GetContent` la única forma de ofrecer PDF + CSV + hojas + imágenes era `*/*`,
    // que es lo que hacía antes — o sea que Extractos ofrecía archivos que el importador no sabe
    // leer, y el error aparecía recién después de subirlos.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // El nombre REAL del archivo, no el último segmento del `content://`.
        //
        // `uri.lastPathSegment` sobre un URI del selector de Android devuelve cosas como
        // `msf:1000000042` o `primary:Download/Extracto.pdf`: un id interno, no un nombre. Daba
        // igual mientras solo alimentaba la detección de banco, pero desde que existe la pantalla
        // de Documentos ese texto es **el nombre visible de la fila**, el `filename` con el que se
        // baja el archivo, y la entrada de `tipoSugeridoPara` — que con un `msf:1000000042`
        // clasifica todo como «Otro».
        //
        // Si el proveedor no trae `DISPLAY_NAME` se cae al comportamiento anterior en vez de
        // abortar: un nombre feo es mejor que no poder subir el archivo.
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
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@rememberLauncherForActivityResult
        onResult(name, bytes, mime)
    }
    return {
        launcher.launch(
            when (aceptar) {
                TiposDeArchivo.EXTRACTOS -> MIMES_DE_EXTRACTO
                TiposDeArchivo.TODOS -> arrayOf("*/*")
            },
        )
    }
}
