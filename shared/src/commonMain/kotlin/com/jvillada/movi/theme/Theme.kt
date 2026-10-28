package com.jvillada.movi.theme

import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.ui.components.LocalSaldosOcultos

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
internal fun esquemaDe(c: ColoresDeMovi): ColorScheme {
    return lightColorScheme().copy(
        primary = c.marca,
        onPrimary = c.sobreMarca,
        primaryContainer = c.tinte,
        onPrimaryContainer = c.aviso,
        // El amarillo no se lee como letra (vuelta 29): lo que Material pinta «en el color
        // secundario» va en el del enlace.
        secondary = c.enlace,
        onSecondary = c.tarjeta,
        background = c.fondo,
        onBackground = c.texto,
        surface = c.tarjeta,
        onSurface = c.texto,
        surfaceContainer = c.tarjeta,
        surfaceContainerLow = c.tarjeta,
        // Las capas del sistema (ola R0): lo que Material pone «más alto» va en la capa de arriba.
        surfaceContainerHigh = c.capa2,
        surfaceContainerHighest = c.capa3,
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
 * ### Un solo tema (vuelta 29)
 *
 * La opción 3 es clara y nada más: no hay parámetro, ni preferencia, ni pregunta al sistema. Con el
 * teléfono en modo oscuro, Movi se ve igual (lo vigila `SoloHayUnTemaTest`).
 */
@Composable
fun MoviTheme(content: @Composable () -> Unit) {
    val colores = COLORES_CLAROS
    val familia = familiaDeMovi()
    CompositionLocalProvider(
        LocalColores provides colores,
        LocalEspacios provides EspaciosDeMovi(),
        LocalFormas provides FormasDeMovi(),
        LocalTextos provides TextosDeMovi().conFamilia(familia),
        // «Ocultar los saldos»: acá y no en App() para que toda pantalla montada con el tema —la
        // app, y cada prueba— lo vea. Es `mutableStateOf`: alternarlo recompone lo que lo lee.
        LocalSaldosOcultos provides SaldosOcultosStore.ocultos,
    ) {
        MaterialTheme(
            colorScheme = esquemaDe(colores),
            typography = tipografiaDeMaterial(familia),
            content = content,
        )
    }
}
