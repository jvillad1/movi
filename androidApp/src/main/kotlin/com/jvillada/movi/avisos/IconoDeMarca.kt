package com.jvillada.movi.avisos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.jvillada.movi.shared.model.Marca

/**
 * **La marca de un comercio, dibujada para el `largeIcon` de una notificación** (Ola 2a): el mismo
 * logotipo tipográfico de la app —el círculo del color de la marca y su marca corta— en un `Bitmap`.
 * Sin archivos de imagen ni nada descargado: sale del catálogo de `:core` (`MarcasConocidas.kt`).
 *
 * 64 dp es el tamaño del ícono grande de una notificación; la letra se achica con el largo de la
 * marca corta, con las mismas proporciones que `CirculoDeMarca` en la app. La tipografía es la del
 * sistema en negrita: Space Grotesk vive en los recursos de Compose y no se carga en un Worker.
 */
internal fun iconoDeMarca(context: Context, marca: Marca): Bitmap {
    val lado = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(bitmap)
    val radio = lado / 2f
    lienzo.drawCircle(radio, radio, radio, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = marca.fondo.toInt() })
    val fraccion = when (marca.corta.length) {
        1 -> 0.46f
        2 -> 0.38f
        else -> 0.29f
    }
    val letra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = marca.texto.toInt()
        textSize = lado * fraccion
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    // Centrado vertical de verdad: la línea base va bajo el centro por la mitad del alto de la letra.
    val base = radio - (letra.descent() + letra.ascent()) / 2f
    lienzo.drawText(marca.corta, radio, base, letra)
    return bitmap
}
