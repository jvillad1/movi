package com.jvillada.movi.ui.extractos

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * En Android el selector **no filtra**, ni siquiera para extractos. Es deliberado, y la primera
 * versión de este archivo se equivocó en las dos direcciones.
 *
 * Esa versión pasó a `ActivityResultContracts.OpenDocument` con una lista de mimes, diciendo en
 * un comentario que era «el mismo catálogo que la web y iOS». Era falso, y la revisión lo midió
 * en un emulador API 35 con archivos reales:
 *
 * - **La web filtra por EXTENSIÓN** (`.pdf,.csv,.xls,.xlsx`) y **iOS por UTI derivada de la
 *   extensión**. En los dos, un `Extracto.pdf` se puede elegir sin importar qué mime le ponga
 *   quien lo guardó.
 * - **Android es la única que filtra por el mime que REPORTA EL PROVEEDOR**, y ese mime no lo
 *   controla el dueño. Medido: un extracto en `.txt` (`text/plain`) y un PDF real que el
 *   proveedor reporta como `application/octet-stream` —lo que hace DownloadProvider cuando el
 *   portal del banco fuerza la descarga— **quedaban grises, sin manera de elegirlos**. El server
 *   sí sabe leer los dos.
 *
 * O sea: el filtro bloqueaba archivos válidos, sin mensaje y sin «mostrar todos». Antes el
 * archivo se podía elegir y, si no servía, el error llegaba del server con una explicación.
 *
 * Y hay un segundo motivo para volver a `GetContent`: `OpenDocument` lanza `ACTION_OPEN_DOCUMENT`,
 * que solo muestra proveedores de SAF, mientras `GetContent` lanza `ACTION_GET_CONTENT`, que
 * muestra **cualquier app** que sepa entregar contenido. Cambiarlo sacaba del selector a las apps
 * que solo declaran `GET_CONTENT` (típicamente fotos que ya viven solo en la nube). Eso afectaba
 * a Extractos, que ya le funcionaba al dueño en su teléfono, para arreglar algo que no estaba
 * roto.
 *
 * El filtro sigue existiendo donde SÍ se puede hacer bien: web e iOS, por extensión.
 */
@Composable
actual fun rememberFilePicker(
    aceptar: TiposDeArchivo,
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit,
): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
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
    // `aceptar` no se usa acá y eso es la decisión, no un olvido: ver el KDoc de arriba.
    return { launcher.launch(if (aceptar == TiposDeArchivo.IMAGENES) "image/*" else "*/*") }
}
