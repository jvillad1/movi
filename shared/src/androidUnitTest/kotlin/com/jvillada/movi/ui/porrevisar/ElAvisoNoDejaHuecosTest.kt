package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sms.TAG_LINEA_DE_LO_ENTENDIDO
import com.jvillada.movi.ui.sms.TAG_LO_QUE_DICE_EL_BANCO
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
 * # La tarjeta de un aviso no deja huecos (vuelta de diseño 7)
 *
 * El aviso de Rappi de la demo, sin «Se anota: …», tenía ~30 px en blanco entre el texto del banco y
 * los botones: el detalle vacío (`det = ""`, como lo suben el teléfono y el correo) se pintaba como un
 * renglón. Ahora la distancia entre el último texto y la fila de botones es la misma con «Se anota» y
 * sin él.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ElAvisoNoDejaHuecosTest {

    @get:Rule val composeRule = createComposeRule()

    private val sms = SmsMessage(
        id = "s1", time = "2026-10-26 11:02", bank = "Falabella",
        text = "Falabella: Compraste \$27.500,00 en RAPPI con tu tarjeta *3684.", state = SMS_STATE_PENDING, det = "",
    )

    private var conLoEntendido by mutableStateOf(true)

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla)) {
                    TarjetaDeMensajeDelBanco(
                        sms,
                        onRevisar = {},
                        onIgnorar = {},
                        leido = ParsedSms(27_500.0, "Rappi", TransactionType.EXPENSE, "Restaurantes"),
                        entendido = if (conLoEntendido) "Restaurantes · Nu · \$27.500" else null,
                        onConfirmar = if (conLoEntendido) ({}) else null,
                        conPildoraDePendiente = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** De lo de abajo del último texto [tagDelUltimo] a lo de arriba del botón «Revisar». */
    private fun distanciaAlBoton(tagDelUltimo: String): Float {
        val ultimo = composeRule.onAllNodesWithTag(tagDelUltimo, useUnmergedTree = true).fetchSemanticsNodes().single()
        val boton = composeRule.onAllNodesWithText("Revisar").fetchSemanticsNodes().single()
        return boton.boundsInRoot.top - ultimo.boundsInRoot.bottom
    }

    @Test
    fun `la distancia al boton es la misma con Se anota y sin el`() {
        montar()
        val conSeAnota = distanciaAlBoton(TAG_LINEA_DE_LO_ENTENDIDO)
        conLoEntendido = false
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag(TAG_LINEA_DE_LO_ENTENDIDO, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        val sinSeAnota = distanciaAlBoton(TAG_LO_QUE_DICE_EL_BANCO)
        assertTrue(conSeAnota > 0f, "hay aire entre el texto y los botones: $conSeAnota")
        assertEquals(conSeAnota, sinSeAnota, "sin «Se anota» no queda hueco")
    }
}
