package com.jvillada.movi.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColors = darkColorScheme(
    primary = MinPrimary,
    onPrimary = MinBg,
    primaryContainer = MinPrimaryContainer,
    onPrimaryContainer = MinOnPrimaryContainer,
    background = MinBg,
    onBackground = MinText,
    surface = MinSurface,
    onSurface = MinText,
    surfaceContainer = MinSurfaceContainer,
    surfaceContainerLow = MinSurfaceContainerLow,
    surfaceContainerHigh = MinSurfaceContainerHigh,
    surfaceContainerHighest = MinSurfaceContainerHighest,
    onSurfaceVariant = MinTextMute,
    outline = MinBorderStrong,
    outlineVariant = MinHairline,
    error = MinExpense,
    onError = MinBg,
)

/**
 * El tema de Movi.
 *
 * Además del `colorScheme` de Material —que es lo único que había hasta acá— provee las cuatro
 * escalas del sistema (ver `Tokens.kt`): color por rol, espaciado, formas y tipografía. Se leen
 * desde cualquier pantalla con `Movi.colores`, `Movi.espacios`, `Movi.formas` y `Movi.textos`.
 *
 * ### Por qué el default sigue siendo oscuro
 *
 * Porque es el único tema que Movi tiene hoy, y esta entrega **no cambia cómo se ve la app**: los
 * `Min*` de `Color.kt` siguen valiendo lo mismo y todas las pantallas los siguen usando. Lo que
 * cambia es que ahora existe a dónde migrarlas, y existe [COLORES_CLAROS] para el día que el dueño
 * pueda elegir. Mover 83 archivos en el mismo cambio que crea el sistema sería hacerlo a ciegas.
 *
 * @param oscuro qué juego de colores se provee. `true` —el default— es el de siempre.
 */
@Composable
fun MoviTheme(oscuro: Boolean = true, content: @Composable () -> Unit) {
    val colores = if (oscuro) COLORES_OSCUROS else COLORES_CLAROS
    CompositionLocalProvider(
        LocalColores provides colores,
        LocalEspacios provides EspaciosDeMovi(),
        LocalFormas provides FormasDeMovi(),
        LocalTextos provides TextosDeMovi(),
    ) {
        MaterialTheme(
            colorScheme = DarkColors,
            content = content,
        )
    }
}
