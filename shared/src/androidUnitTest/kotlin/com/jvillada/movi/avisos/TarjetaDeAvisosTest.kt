package com.jvillada.movi.avisos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * La tarjeta de Hoy que ofrece los avisos (Ola 1): dice para qué antes de pedir nada, y cualquiera
 * de los dos botones la cierra —el diálogo del sistema sale solo con «Activar avisos»—.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class TarjetaDeAvisosTest {

    @get:Rule val composeRule = createComposeRule()

    private var pidio = 0
    private var descarto = 0
    private val oferta = object : OfertaDeAvisos {
        override fun pedir() { pidio++ }
        override fun descartar() { descarto++ }
    }

    private fun montar() {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { TarjetaDeAvisos(oferta) } } }
    }

    @Test
    fun `dice para que son los avisos`() {
        montar()
        composeRule.onNodeWithText(PARA_QUE_SON_LOS_AVISOS).assertIsDisplayed()
    }

    @Test
    fun `activar pide el permiso y la tarjeta se va`() {
        montar()
        composeRule.onNodeWithText("Activar avisos").performClick()
        assertEquals(1, pidio)
        assertEquals(0, descarto)
        composeRule.onNodeWithTag(TAG_TARJETA_DE_AVISOS).assertDoesNotExist()
    }

    @Test
    fun `ahora no la descarta sin pedir nada`() {
        montar()
        composeRule.onNodeWithText("Ahora no").performClick()
        assertEquals(0, pidio)
        assertEquals(1, descarto)
        composeRule.onNodeWithTag(TAG_TARJETA_DE_AVISOS).assertDoesNotExist()
    }
}
