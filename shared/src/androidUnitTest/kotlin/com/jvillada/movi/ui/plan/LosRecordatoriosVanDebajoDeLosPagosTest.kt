package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_PROPUESTA
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.TonoDeCifraKey
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.recurrentes.SeccionChecklistDelPeriodo
import com.jvillada.movi.ui.recurrentes.TITULO_YA_PAGASTE
import com.jvillada.movi.ui.recurrentes.tonoDelResumen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta de diseño 28: en Plan, el aviso de los recordatorios va debajo de los pagos
 *
 * «Tus recordatorios no te van a llegar» era la segunda tarjeta más grande de Plan, encima de «Pagos
 * del período». Ahora es una fila compacta en tono de aviso, dentro de la lista y debajo del resumen
 * («Falta por pagar · Ya pagaste»), que se descarta por el período con «Ahora no». Y en ese resumen
 * «Ya pagaste $0» baja de peso: una cifra en cero va apagada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class LosRecordatoriosVanDebajoDeLosPagosTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() = PropuestasDescartadasStore.clear()

    private val arriendo = PagoDelPeriodo(
        ruleId = "rr_arriendo", nombre = "Arriendo", monto = 1_800_000, pagado = false, diasParaVencer = -22, vence = "2026-10-05",
    )

    private var activadas = 0
    private var ahoraNo = 0

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = listOf(arriendo),
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { _, _ -> },
                        onAnotarMovimiento = {},
                        onQuitarLaMarca = {},
                        onReintentar = {},
                        debajoDelResumen = {
                            FilaDeRecordatoriosSinCanal(bloqueadas = false, onActivar = { activadas++ }, onAhoraNo = { ahoraNo++ })
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `la fila va debajo del resumen de los pagos, compacta, y no es una tarjeta`() {
        montar()
        val fila = composeRule.onNodeWithTag(TAG_FILA_DE_RECORDATORIOS, useUnmergedTree = true).fetchSemanticsNode()
        val resumen = composeRule.onNodeWithText(TITULO_YA_PAGASTE, useUnmergedTree = true).fetchSemanticsNode()
        val primerPago = composeRule.onNodeWithText("Arriendo", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(resumen.boundsInRoot.bottom <= fila.boundsInRoot.top, "debajo del resumen")
        assertTrue(fila.boundsInRoot.bottom <= primerPago.boundsInRoot.top, "y encima de los pagos")
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_TARJETA_DE_PROPUESTA, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(TITULO_RECORDATORIOS_SIN_CANAL, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(SUBTITULO_RECORDATORIOS_APAGADOS, useUnmergedTree = true).assertExists()

        // Tocarla activa; «Ahora no» la descarta.
        composeRule.onNodeWithText(AHORA_NO, useUnmergedTree = true).performClick()
        assertEquals(1, ahoraNo)
        assertEquals(0, activadas)
        composeRule.onNodeWithTag(TAG_FILA_DE_RECORDATORIOS, useUnmergedTree = true).performClick()
        assertEquals(1, activadas)
    }

    @Test
    fun `ya pagaste en cero va apagado`() {
        montar()
        val cero = composeRule.onNodeWithText("\$0", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(TonoDeCifra.Apagado, cero.config[TonoDeCifraKey])
        // El primero es el del resumen («Falta por pagar»); el otro, el de la fila del Arriendo.
        val falta = composeRule.onAllNodesWithText("\$1.800.000", useUnmergedTree = true).fetchSemanticsNodes().first()
        assertEquals(TonoDeCifra.Neutro, falta.config[TonoDeCifraKey])
        assertEquals(TonoDeCifra.Apagado, tonoDelResumen(0))
        assertEquals(TonoDeCifra.Neutro, tonoDelResumen(53_000))
    }

    @Test
    fun `ahora no descarta solo este periodo`() {
        val octubre = PeriodoFinanciero(2026, 10)
        val noviembre = PeriodoFinanciero(2026, 11)
        val estado = EstadoDelTableroDeRecurrentes(CoroutineScope(Dispatchers.Unconfined), mutableStateOf(null))
        assertFalse(estado.recordatoriosAhoraNo(octubre))
        estado.descartarRecordatorios(octubre)
        assertTrue(estado.recordatoriosAhoraNo(octubre))
        assertFalse(estado.recordatoriosAhoraNo(noviembre), "el período que viene vuelve a avisar")
        assertEquals("recordatorios-sin-canal:2026-10", claveDeRecordatoriosAhoraNo(octubre))
    }
}
