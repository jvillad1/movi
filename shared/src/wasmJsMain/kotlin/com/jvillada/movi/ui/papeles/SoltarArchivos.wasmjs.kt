package com.jvillada.movi.ui.papeles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.DragEvent
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * Escucha `dragover` y `drop` sobre el documento entero —el lienzo de Compose no los recibe— y lee
 * lo soltado con el mismo `FileReader` del selector de archivos. `dragover` tiene que cancelarse:
 * sin eso el navegador abre el archivo en la pestaña y se va de Movi.
 */
@Composable
actual fun EscucharArchivosSoltados() {
    DisposableEffect(Unit) {
        val alArrastrar: (Event) -> Unit = { evento -> evento.preventDefault() }
        val alSoltar: (Event) -> Unit = { evento ->
            evento.preventDefault()
            val archivos = evento.unsafeCast<DragEvent>().dataTransfer?.files
            if (archivos != null) {
                for (i in 0 until archivos.length) {
                    archivos[i]?.let { leerYEncolar(it) }
                }
            }
        }
        document.addEventListener("dragover", alArrastrar)
        document.addEventListener("drop", alSoltar)
        onDispose {
            document.removeEventListener("dragover", alArrastrar)
            document.removeEventListener("drop", alSoltar)
        }
    }
}

private fun leerYEncolar(archivo: File) {
    val lector = FileReader()
    lector.onload = {
        val buffer = lector.result?.unsafeCast<ArrayBuffer>()
        if (buffer != null) {
            val int8 = Int8Array(buffer)
            val bytes = ByteArray(int8.length) { i -> int8[i] }
            val mime = archivo.type.ifBlank { "application/octet-stream" }
            PapelesCompartidos.recibir(listOf(ArchivoCompartido(archivo.name, bytes, mime)))
        }
    }
    lector.readAsArrayBuffer(archivo)
}
