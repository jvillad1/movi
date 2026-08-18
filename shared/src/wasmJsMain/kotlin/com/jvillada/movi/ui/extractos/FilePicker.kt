package com.jvillada.movi.ui.extractos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

private const val PICKER_INPUT_ID = "movi-file-picker-input"

// El input HTML nativo por fuera del canvas es la única forma de abrir el diálogo del
// sistema desde wasm — Compose no tiene acceso al filesystem del navegador. Se crea una
// sola vez y se reusa (mismo patrón que el overlay de login en index.html).
private fun ensurePickerInput(): HTMLInputElement {
    val existing = document.getElementById(PICKER_INPUT_ID) as? HTMLInputElement
    if (existing != null) return existing
    val input = document.createElement("input") as HTMLInputElement
    input.id = PICKER_INPUT_ID
    input.type = "file"
    input.accept = ".pdf,.csv,.xls,.xlsx,image/*"
    input.style.display = "none"
    document.body?.appendChild(input)
    return input
}

@Composable
actual fun rememberFilePicker(
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit {
    return remember(onResult) {
        {
            val input = ensurePickerInput()
            // Limpia la selección previa: sin esto, volver a elegir el mismo archivo dos
            // veces seguidas no dispara el evento "change" la segunda vez.
            input.value = ""
            input.onchange = {
                val file: File? = input.files?.get(0)
                if (file != null) {
                    val reader = FileReader()
                    reader.onload = {
                        val buffer = reader.result as ArrayBuffer
                        val int8 = Int8Array(buffer)
                        val bytes = ByteArray(int8.length) { i -> int8[i] }
                        val mime = file.type.ifBlank { "application/octet-stream" }
                        onResult(file.name, bytes, mime)
                    }
                    reader.readAsArrayBuffer(file)
                }
            }
            // Tiene que ejecutarse en el mismo tick del click del usuario que devolvió esta
            // lambda — Compose en wasm lo entrega así, y los navegadores bloquean el diálogo
            // de archivos si se dispara de forma diferida (async, setTimeout, etc.).
            input.click()
        }
    }
}
