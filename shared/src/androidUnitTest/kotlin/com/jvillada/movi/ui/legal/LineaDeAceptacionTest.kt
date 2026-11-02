package com.jvillada.movi.ui.legal

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.LinkAnnotation
import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.auth.RegisterScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Vuelta 109 · **«Al crear tu cuenta aceptas los Términos y la Política de privacidad»** en el registro,
 * sin casilla, con los dos nombres como enlaces a las páginas del server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class LineaDeAceptacionTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `el registro dice lo que se acepta y enlaza las dos paginas`() {
        composeRule.setContent { MoviTheme { RegisterScreen(onNavigate = {}) } }
        val nodo = composeRule.onNodeWithTag(TAG_LINEA_DE_ACEPTACION, useUnmergedTree = true).fetchSemanticsNode()
        val texto = nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().single()
        assertEquals(LINEA_DE_ACEPTACION, texto.text)
        val enlaces = texto.getLinkAnnotations(0, texto.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf("$apiBaseUrl/terminos", "$apiBaseUrl/privacidad"), enlaces)
    }
}
