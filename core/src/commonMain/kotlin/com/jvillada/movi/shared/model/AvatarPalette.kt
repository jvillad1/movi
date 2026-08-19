package com.jvillada.movi.shared.model

/**
 * Paleta fija de colores de avatar — F42 · F46.
 *
 * El dueño pidió "alias, avatar de iniciales con color, cambiar contraseña". La foto real
 * queda descartada por ahora (el servidor no guarda archivos, ver F46 en el plan); lo que
 * queda es elegir el COLOR del círculo de iniciales. Dos formas de resolver eso:
 *
 *  1. Un selector de color libre (picker RGB/hex).
 *  2. Una paleta fija y acotada.
 *
 * Se elige la paleta fija — más simple de validar en el servidor (un `in` contra una lista, en
 * vez de parsear y sanear hex arbitrario) y, sobre todo, **siempre legible**: cada color de acá
 * es un tono "600" con suficiente contraste para texto blanco encima. Un picker libre dejaría
 * elegir amarillo pastel con iniciales blancas ilegibles, y este círculo se ve chiquito (32–56
 * dp) en varias pantallas (encabezado de Perfil, AvatarButton en Inicio/Movimientos/
 * Presupuestos) — no hay margen para que el contraste falle.
 *
 * Vive en `:core` por la misma razón que [PasswordPolicy]: servidor (validación autoritativa
 * en `UserRoutes.kt`) y cliente (selector de la hoja de edición) tienen que usar EXACTAMENTE
 * la misma lista, o un color válido para uno sería rechazado por el otro.
 */
object AvatarPalette {
    /** Ocho tonos "600" (Material), todos con contraste suficiente para iniciales en blanco. */
    val COLORS: List<String> = listOf(
        "#E53935", // rojo
        "#F4511E", // naranja profundo
        "#8E24AA", // púrpura
        "#3949AB", // índigo
        "#1E88E5", // azul
        "#00897B", // verde azulado
        "#43A047", // verde
        "#D81B60", // rosa
    )

    /** El color de quien todavía no eligió ninguno — primera entrada de la paleta. */
    val DEFAULT: String = COLORS.first()

    fun isValid(hex: String?): Boolean = hex != null && hex in COLORS
}
