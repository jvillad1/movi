package com.jvillada.movi.compartir

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import com.jvillada.movi.shared.model.MAX_DOCUMENTO_BYTES
import com.jvillada.movi.ui.papeles.ArchivoCompartido

/**
 * # «Compartir con Movi» en Android (Ola 2)
 *
 * Movi aparece en la hoja de compartir del sistema para imágenes y PDF, uno o varios (los
 * `intent-filter` de `MainActivity`). Esto es lo que lee lo que llegó: los bytes, el nombre y el
 * tipo de cada archivo. **No sube nada**: deja los archivos en `PapelesCompartidos`, y `App()` los
 * procesa recién cuando la puerta de «Entrar con huella» se pasó (ver `sePuedeLeerLoCompartido`).
 *
 * Vive en `com.jvillada.movi.compartir` y no en el paquete raíz: lo que va en `com.jvillada.movi` a
 * secas se pierde del dex (así crashearon el APK 1.14 y el 1.18).
 */

/** Cuántos archivos se aceptan de una vez: compartir 40 fotos por error no son 40 lecturas. */
const val MAX_ARCHIVOS_COMPARTIDOS: Int = 10

/** ¿Este `Intent` es alguien compartiendo con Movi algo que Movi sabe leer? */
fun esCompartirConMovi(accion: String?, tipo: String?): Boolean {
    if (accion != Intent.ACTION_SEND && accion != Intent.ACTION_SEND_MULTIPLE) return false
    val t = tipo?.lowercase() ?: return false
    return t.startsWith("image/") || t == "application/pdf"
}

/** Los `content://` que trae el `Intent`, como mucho [MAX_ARCHIVOS_COMPARTIDOS]. */
fun urisCompartidos(intent: Intent): List<Uri> {
    val uris = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
    return uris.take(MAX_ARCHIVOS_COMPARTIDOS)
}

/**
 * Lee cada archivo **con tope**: un PDF de 200 MB compartido por error no puede tumbar la app. Lo
 * que no se pudo abrir o se pasa del tope vuelve con su `problema`, para decirlo en la hoja en vez
 * de perderlo en silencio. Bloqueante: se llama desde un hilo de fondo.
 */
fun leerLoCompartido(context: Context, uris: List<Uri>, fallbackDeTipo: String?): List<ArchivoCompartido> =
    uris.map { uri ->
        val nombre = nombreDe(context, uri)
        val mime = context.contentResolver.getType(uri) ?: fallbackDeTipo ?: "application/octet-stream"
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { entrada ->
                val tope = MAX_DOCUMENTO_BYTES.toInt()
                val leidos = entrada.readNBytesCompat(tope + 1)
                leidos
            }
        }.getOrNull()
        when {
            bytes == null -> ArchivoCompartido(nombre, ByteArray(0), mime, problema = "Android no dejó abrir este archivo.")
            bytes.size > MAX_DOCUMENTO_BYTES -> ArchivoCompartido(
                nombre, ByteArray(0), mime,
                problema = "Pesa más de ${MAX_DOCUMENTO_BYTES / (1024 * 1024)} MB, el máximo que Movi guarda.",
            )
            else -> ArchivoCompartido(nombre, bytes, mime)
        }
    }

/** El nombre de verdad («Transferencia.jpg»), no el último pedazo del `content://`. */
private fun nombreDe(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && cursor.moveToFirst()) cursor.getString(i) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "archivo"

/** `InputStream.readNBytes` es de Java 11 (API 33 en Android): esto es lo mismo para todas. */
private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
    val salida = java.io.ByteArrayOutputStream()
    val bloque = ByteArray(64 * 1024)
    var faltan = n
    while (faltan > 0) {
        val leidos = read(bloque, 0, minOf(bloque.size, faltan))
        if (leidos < 0) break
        salida.write(bloque, 0, leidos)
        faltan -= leidos
    }
    return salida.toByteArray()
}
