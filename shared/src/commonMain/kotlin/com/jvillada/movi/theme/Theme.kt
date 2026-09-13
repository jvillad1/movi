package com.jvillada.movi.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * **El esquema de Material, armado desde los tokens.**
 *
 * Movi pinta casi todo a mano, pero no todo: el `Switch` de Perfil, el `SnackbarHost` del Inicio,
 * la barra de progreso, los diálogos y los campos de texto salen de Material y leen
 * `MaterialTheme.colorScheme`. Mientras eso fuera un `darkColorScheme` fijo —lo que era hasta
 * acá— el tema claro habría quedado a medias: la app clara con los componentes de Material
 * oscuros adentro. No es una hipótesis, es lo que pasa si esta función no existe.
 *
 * Los roles de Material que Movi no usa se dejan en su default: rellenarlos todos con valores
 * inventados sería peor que no tocarlos.
 */
private fun esquemaDe(c: ColoresDeMovi, oscuro: Boolean): ColorScheme {
    val base = if (oscuro) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.marca,
        onPrimary = c.sobreMarca,
        primaryContainer = c.marca.copy(alpha = 0.16f),
        onPrimaryContainer = c.marca,
        background = c.fondo,
        onBackground = c.texto,
        surface = c.tarjeta,
        onSurface = c.texto,
        surfaceContainer = c.tarjeta,
        surfaceContainerLow = c.tarjeta,
        surfaceContainerHigh = c.tarjeta,
        surfaceContainerHighest = c.tarjeta,
        onSurfaceVariant = c.textoMedio,
        outline = c.borde,
        outlineVariant = c.hilo,
        error = c.sale,
        onError = c.sobreMarca,
    )
}

/**
 * El tema de Movi.
 *
 * Provee las cuatro escalas del sistema (ver `Tokens.kt`) —color por rol, espaciado, formas y
 * tipografía— y además el `colorScheme` de Material armado con esos mismos colores, para que los
 * componentes que Movi no dibuja a mano hablen el mismo idioma.
 *
 * ### Por qué el default es oscuro
 *
 * Porque es el único tema que Movi tuvo siempre. Quién lo elige de verdad es `TemaStore`, que lo
 * guarda en el aparato; este default es para las vistas previas y las pruebas, que tienen que ver
 * lo mismo que ve la app recién instalada.
 *
 * @param oscuro qué juego de colores se provee.
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
            colorScheme = esquemaDe(colores, oscuro),
            content = content,
        )
    }
}
