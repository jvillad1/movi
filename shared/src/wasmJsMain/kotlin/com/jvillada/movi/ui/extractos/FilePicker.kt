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
    input.style.display = "none"
    document.body?.appendChild(input)
    // El `accept` NO se fija acá: se reasigna en cada apertura (ver `rememberFilePicker`). Fijarlo
    // al crear el input haría que la primera pantalla que abra el selector le imponga su filtro a
    // la otra para siempre — el input se crea una vez y se reusa entre pantallas.
    return input
}

/**
 * El `accept` se reasigna en CADA apertura, no solo al crear el input.
 *
 * El input se crea una vez y se reusa entre pantallas: si el `accept` se fijara al crearlo, la
 * primera pantalla que abriera el selector le impondría su filtro a la otra para siempre. Con
 * Extractos y Documentos usándolo alternadamente, eso es un bug que aparece en el segundo uso.
 */
private fun acceptDe(aceptar: TiposDeArchivo): String = when (aceptar) {
    TiposDeArchivo.EXTRACTOS -> ".pdf,.csv,.xls,.xlsx,image/*"
    // Vacío = el navegador no filtra nada. Es lo correcto para «cualquier papel»: un contrato en
    // .docx o una escritura escaneada en .zip tienen que poder elegirse.
    TiposDeArchivo.TODOS -> ""
    TiposDeArchivo.IMAGENES -> "image/*"
}

@Composable
actual fun rememberFilePicker(
    aceptar: TiposDeArchivo,
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit,
): () -> Unit {
    return remember(aceptar, onResult) {
        {
            val input = ensurePickerInput()
            input.accept = acceptDe(aceptar)
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
