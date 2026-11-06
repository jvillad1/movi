package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** La tarjeta de Por revisar: las pastillas del número abren solas aunque la línea llegue después de pintarse. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class LaTarjetaOfreceLasDelNumeroTest {

    @get:Rule val composeRule = createComposeRule()

    private val sms = SmsMessage("sms_1", "2026-10-27 09:00", "85540", "Bancolombia: Transferiste \$9,000.00 a @pedro", SMS_STATE_PENDING, "")
    private var pide by mutableStateOf<EligeLaCategoria?>(null)

    @Test
    fun `la linea llega despues de la primera composicion y las pastillas se abren solas`() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla)) {
                    TarjetaDeMensajeDelBanco(
                        sms,
                        onRevisar = {},
                        onIgnorar = {},
                        leido = ParsedSms(9_000.0, "@pedro", TransactionType.EXPENSE, "Otros"),
                        eligeLaCategoria = pide,
                        conPildoraDePendiente = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag(TAG_LINEA_DEL_NUMERO, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        pide = EligeLaCategoria(
            categorias = listOf("Parqueadero", "Gasolina", "Comida"),
            elegida = null,
            onElegir = {},
            onTodas = {},
            delNumero = "A esta llave le pusiste Parqueadero 3 veces y Gasolina 2",
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_LINEA_DEL_NUMERO, useUnmergedTree = true)
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_LINEA_DEL_NUMERO, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(composeRule.onAllNodesWithTag(TAG_CATEGORIAS_EN_LA_TARJETA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "las pastillas se ven")
    }
}
