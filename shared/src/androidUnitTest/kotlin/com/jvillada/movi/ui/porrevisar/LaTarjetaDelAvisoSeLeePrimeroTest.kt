package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_AVATAR_DE_MARCA
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_PILDORA
import com.jvillada.movi.ui.sms.RENGLONES_DEL_TEXTO_PLEGADO
import com.jvillada.movi.ui.sms.TAG_LO_QUE_DICE_EL_BANCO
import com.jvillada.movi.ui.sms.TAG_TITULO_DE_LA_TARJETA_DE_AVISO
import com.jvillada.movi.ui.sms.TarjetaDeMensajeDelBanco
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La tarjeta de un aviso en «Por revisar» se lee primero (vuelta de diseño 2)
 *
 * El comercio con su marca y el monto con la `Cifra` del sistema arriba, «banco · hora» debajo, el
 * texto del banco apagado en dos renglones (se abre al tocarlo) y sin la píldora «Pendiente», que en
 * la bandeja no dice nada. A 360 dp con la letra de Movi (×1,12).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LaTarjetaDelAvisoSeLeePrimeroTest {

    @get:Rule val composeRule = createComposeRule()

    private val texto = "Bancolombia: Compraste \$42.500,00 en RAPPI*RESTAURANTE LAS DOCE con tu tarjeta Mastercard " +
        "*3684 el 26/10/2026 a las 11:02. Si tienes dudas, llámanos a la línea de atención 01 8000 912 345."

    private val sms = SmsMessage(
        id = "s1", time = "2026-10-26 11:02", bank = "Falabella", text = texto, state = SMS_STATE_PENDING, det = "",
    )

    private fun montar(conPildoraDePendiente: Boolean = false) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla)) {
                        TarjetaDeMensajeDelBanco(
                            sms,
                            onRevisar = {},
                            onIgnorar = {},
                            leido = ParsedSms(42_500.0, "Rappi", TransactionType.EXPENSE, "Restaurantes"),
                            conPildoraDePendiente = conPildoraDePendiente,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single()
    }

    @Test
    fun `el comercio con su marca y el monto arriba, el banco y la hora debajo`() {
        montar()
        val titulo = nodos(TAG_TITULO_DE_LA_TARJETA_DE_AVISO).single()
        assertEquals("Rappi", titulo.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text })
        assertEquals(1, nodos(TAG_AVATAR_DE_MARCA).size, "la marca del comercio")
        val cifra = nodos(TAG_CIFRA).single()
        assertEquals("−\$42.500", cifra.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text })
        assertTrue(cifra.boundsInRoot.top < nodos(TAG_LO_QUE_DICE_EL_BANCO).single().boundsInRoot.top, "el monto antes que el texto")
        assertTrue(composeRule.onAllNodesWithText("Falabella ·", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `en la bandeja no lleva la pildora Pendiente, en el historial si`() {
        montar(conPildoraDePendiente = false)
        assertTrue(composeRule.onAllNodesWithText("Pendiente", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(nodos(TAG_PILDORA).isEmpty())
    }

    @Test
    fun `el historial conserva la pildora Pendiente`() {
        montar(conPildoraDePendiente = true)
        assertEquals(1, composeRule.onAllNodesWithText("Pendiente", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `el texto del banco va en dos renglones y se abre al tocarlo`() {
        montar()
        val plegado = disposicion(nodos(TAG_LO_QUE_DICE_EL_BANCO).single())
        assertEquals(RENGLONES_DEL_TEXTO_PLEGADO, plegado.lineCount)
        assertTrue(plegado.isLineEllipsized(RENGLONES_DEL_TEXTO_PLEGADO - 1), "se corta con «…»")

        // El toque es del renglón entero (la raya y el texto): se busca por la acción.
        composeRule.onAllNodesWithText(texto, useUnmergedTree = false)[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val abierto = disposicion(nodos(TAG_LO_QUE_DICE_EL_BANCO).single())
        assertTrue(abierto.lineCount > RENGLONES_DEL_TEXTO_PLEGADO, "abierto se lee entero: ${abierto.lineCount} renglones")
        assertTrue((0 until abierto.lineCount).none { abierto.isLineEllipsized(it) })
    }
}
