package com.jvillada.movi

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.CascaraDeAncho

/**
 * Monta [content] como lo monta `App.kt`: con el tema, con la letra 12 % más grande que la app le
 * pone a todo, y **con la clase de ancho que le toca de verdad al ancho de la prueba** (la decide
 * [CascaraDeAncho], la misma raíz que la de la app).
 *
 * Sin esto una prueba ve siempre `WindowWidthClass.Compact` —el valor por defecto de
 * `LocalWindowWidthClass`, a propósito para que las pruebas de teléfono no cambien—, así que para
 * probar la web hay que montar con esto y un `@Config(qualifiers = "w1280dp-…")`.
 */
@Composable
fun ConClaseDeAncho(escala: Float = 1.12f, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
        MoviTheme {
            CascaraDeAncho(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}
