package com.jvillada.movi.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Un solo tema (vuelta 29)
 *
 * La opción 3 es clara y nada más: la preferencia de tema salió de Ajustes, y **con el teléfono en
 * modo oscuro Movi se ve igual**. Se monta con el sistema de noche (`qualifiers = "night"`) y se mira
 * lo que el tema provee: la paleta del sistema de diseño y el esquema de Material, los dos claros.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
class SoloHayUnTemaTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `con el telefono en modo oscuro, Movi se pinta clara`() {
        var sistemaOscuro = false
        var colores: ColoresDeMovi? = null
        var fondoDeMaterial = Color.Unspecified
        composeRule.setContent {
            sistemaOscuro = isSystemInDarkTheme()
            MoviTheme {
                colores = Movi.colores
                fondoDeMaterial = MaterialTheme.colorScheme.background
            }
        }
        composeRule.waitForIdle()
        assertTrue(sistemaOscuro, "la prueba tiene que correr con el sistema de noche")
        assertEquals(COLORES_CLAROS, colores)
        assertEquals(COLORES_CLAROS.fondo, fondoDeMaterial)
    }

    @Test
    fun `fuera del tema tambien es el claro`() {
        var colores: ColoresDeMovi? = null
        composeRule.setContent { colores = Movi.colores }
        composeRule.waitForIdle()
        assertEquals(COLORES_CLAROS, colores)
    }
}
