package com.jvillada.movi.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

/**
 * # Safari en un iPhone dice qué falta para avisar
 *
 * Desde una pestaña de Safari no hay `PushManager`, y el interruptor de avisos desaparecía sin
 * explicar nada. La tarjeta dice los dos toques que faltan (Compartir → «Agregar a Inicio»). En el
 * teléfono Android —donde corre esta prueba— no se pide instalar nada, y Perfil no la muestra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class AvisosEnElIphoneTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `la tarjeta dice el paso que falta`() {
        composeRule.setContent { MoviTheme { AvisosEnElIphone() } }
        composeRule.onNodeWithText("Avisos en tu iPhone").assertIsDisplayed()
        composeRule.onNodeWithText("Agregar a Inicio", substring = true).assertIsDisplayed()
    }

    @Test
    fun `fuera de Safari en un iPhone no se pide instalar nada`() {
        assertFalse(PushOptIn.necesitaInstalar)
    }
}
