package com.jvillada.movi.ui.extractos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.interop.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeSpreadsheet
import platform.darwin.NSObject
import platform.posix.memcpy

// Picker de archivos de iOS sobre UIDocumentPickerViewController (la app "Archivos").
// Cubre extractos (PDF/CSV/planilla) e imágenes para Movi AI sin pedir permiso de
// fotos: Archivos también muestra las imágenes guardadas ahí y no requiere
// NSPhotoLibraryUsageDescription.

// El delegate se retiene en un `remember` — UIDocumentPickerViewController guarda al
// delegate como referencia débil, así que sin eso ARC lo libera apenas termina la
// composición y el callback nunca llega.
private class PickerDelegate(
    private val onPicked: (NSURL) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked)
    }

    // Cancelación: no se llama onResult, igual que en Android y wasm.
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { out ->
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

private val mimeByExtension = mapOf(
    "pdf" to "application/pdf",
    "csv" to "text/csv",
    "txt" to "text/plain",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "heic" to "image/heic",
    "gif" to "image/gif",
    "webp" to "image/webp",
)

private fun mimeTypeFor(url: NSURL): String {
    val ext = url.pathExtension?.lowercase().orEmpty()
    val fromUti = ext.takeIf { it.isNotEmpty() }
        ?.let { UTType.typeWithFilenameExtension(it)?.preferredMIMEType }
    return fromUti ?: mimeByExtension[ext] ?: "application/octet-stream"
}

// Con asCopy=true el archivo ya está en el sandbox de la app, pero el acceso con
// security scope no hace daño y cubre el caso de proveedores de terceros (iCloud, Drive).
private fun readBytes(url: NSURL): ByteArray? {
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        return NSData.dataWithContentsOfURL(url)?.toByteArray()
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

// Si ya hay un modal encima (p.ej. un diálogo presentado por UIKit), hay que presentar
// desde el controller más alto; presentar desde uno que ya presenta otro falla en silencio.
private fun UIViewController.topMost(): UIViewController {
    var top: UIViewController = this
    while (true) top = top.presentedViewController ?: return top
}

@Composable
actual fun rememberFilePicker(
    aceptar: TiposDeArchivo,
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit,
): () -> Unit {
    val host = LocalUIViewController.current
    val currentOnResult by rememberUpdatedState(onResult)
    val delegate = remember {
        PickerDelegate { url ->
            val bytes = readBytes(url) ?: return@PickerDelegate
            val name = url.lastPathComponent ?: "archivo"
            currentOnResult(name, bytes, mimeTypeFor(url))
        }
    }
    return remember(host, delegate, aceptar) {
        {
            val picker = UIDocumentPickerViewController(
                // `UTTypeData` y NO `UTTypeItem`: la primera versión usaba `UTTypeItem`, que es
                // la raíz de la jerarquía y **también conforma a las carpetas**. La revisión lo
                // midió contra el SDK: con `UTTypeItem` el picker acepta una carpeta, y entonces
                // `NSData(contentsOf:)` devuelve `nil`, el delegate corta y **no pasa nada** — el
                // selector se cierra en silencio y el dueño no sabe por qué.
                //
                // `UTTypeData` cubre `.docx` y `.zip` (verificado) y deja afuera carpetas y
                // paquetes, que de todos modos no se pueden subir como bytes.
                forOpeningContentTypes = when (aceptar) {
                    TiposDeArchivo.TODOS -> listOf(UTTypeData)
                    TiposDeArchivo.IMAGENES -> listOf(UTTypeImage)
                    TiposDeArchivo.EXTRACTOS -> listOf(
                        UTTypePDF,
                        UTTypeCommaSeparatedText,
                        UTTypePlainText,
                        UTTypeSpreadsheet,
                        UTTypeImage,
                    )
                },
                asCopy = true,
            )
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            host.topMost().presentViewController(picker, animated = true, completion = null)
        }
    }
}
