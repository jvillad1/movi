package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CuotaPorSaldar
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoParecidoALaCuota
import com.jvillada.movi.shared.model.SI_ES_LA_CUOTA
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.claveDeLaCuotaPorSaldar
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La fila de Pagos ofrece el gasto que se parece a la cuota (vuelta 57)
 *
 * Revisión de la vuelta 56: la cuota pendiente del Vehículo 4083 solo decía «¿Se cobró?», y el detalle
 * del gasto de $980.000 en «Otros» sí sugería «¿Es la cuota de Vehículo 4083?». Ahora la fila ofrece
 * «¿Es el de $980.000?»; al tocarlo, «¿Es la cuota de Vehículo 4083?» con «Sí, es la cuota» (lo mismo
 * que el detalle) y «No fue este». Nunca se tilda solo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaCuotaParecidaEnPagosTest {

    @get:Rule val composeRule = createComposeRule()

    private val cuota = PagoDelPeriodo(
        ruleId = "${CREDIT_RULE_PREFIX}v4083", nombre = "Cuota Vehículo 4083", monto = 980_000L, pagado = false,
        diasParaVencer = -1, vence = "2026-10-27", periodoDelSello = "2026-11", seCobraSola = true,
    )
    private val gasto = FinancialEvent(
        id = "ev-980", accountId = "a4057", type = TransactionType.EXPENSE, amount = 980_000L, category = "Otros",
        description = "Banco de Occidente S A ATH", timestamp = 1_793_000_000_000L,
    )
    private val parecido = GastoParecidoALaCuota(
        CuotaPorSaldar(creditoId = "v4083", nombre = "Vehículo 4083", cuota = 980_000L, moneda = "COP", vence = "2026-10-27"),
        gasto,
    )

    private val esLaCuota = mutableListOf<String>()
    private val noEs = mutableListOf<String>()

    private fun montar(parecidos: Map<String, GastoParecidoALaCuota>) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = listOf(cuota),
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { _, _ -> },
                        onAnotarMovimiento = {},
                        onQuitarLaMarca = {},
                        onReintentar = {},
                        cuotasQueSeParecen = parecidos,
                        onEsLaCuota = { _, p -> esLaCuota += p.gasto.id },
                        onNoEsLaCuota = { _, p -> noEs += p.gasto.id },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `la cuota pendiente ofrece el gasto parecido y Si es la cuota hace lo del detalle`() {
        montar(mapOf(claveDeLaCuotaPorSaldar("v4083", "2026-10-27") to parecido))

        composeRule.onNodeWithTag(TAG_CUOTA_QUE_SE_PARECE_EN_PAGOS).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¿Es la cuota de Vehículo 4083?").assertExists()
        assertTrue(esLaCuota.isEmpty(), "nunca se tilda solo")
        tocar(SI_ES_LA_CUOTA)
        assertEquals(listOf("ev-980"), esLaCuota)
    }

    @Test
    fun `No fue este deja de ofrecerlo, y sin parecido la fila sigue como antes`() {
        montar(mapOf(claveDeLaCuotaPorSaldar("v4083", "2026-10-27") to parecido))
        composeRule.onNodeWithText("¿Es el de \$980.000?").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar(ETIQUETA_NO_FUE_ESTE)
        assertEquals(listOf("ev-980"), noEs)
    }

    @Test
    fun `sin un gasto parecido no hay enlace`() {
        // La clave de otro vencimiento no es la de esta fila.
        montar(mapOf(claveDeLaCuotaPorSaldar("v4083", "2026-11-27") to parecido))
        assertTrue(composeRule.onAllNodesWithText("¿Es el de", substring = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText(TEXTO_SIN_MOVIMIENTO_DEL_DEBITO).assertExists()
    }
}
