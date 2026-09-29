package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.EstadoDeLaFila
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * # «Anotar este pago» también antes del vencimiento
 *
 * El dueño paga antes de que venza (la Master Black vence el 2 y la pagó el 27). Una fila que
 * todavía no vence (`EstadoDeLaFila.AUN_NO_VENCE`) no ofrecía NADA, así que el camino de
 * «Anotar este pago» → Agregar en «Cuota» solo existía para lo ya vencido: casi nunca. Ahora toda
 * fila pendiente sin vencer lo ofrece como acción secundaria, y sin el «Movi no encontró…» —
 * todavía no tenía por qué encontrarlo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class AnotarAntesDelVencimientoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val libre = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 40_710_555)

    /** La cuota de un crédito que vence en 18 días: sin ocurrencia, así que no vence todavía. */
    private val cuota = PagoDelPeriodo(
        ruleId = "credit_l9695",
        nombre = "Cuota Libre inversión 9695",
        monto = 1_204_064,
        pagado = false,
        diasParaVencer = 18,
        vence = "2026-10-17",
        categoria = "Cuota de crédito",
    )

    /** Un pago fijo que vence en 6 días. */
    private val arriendo = PagoDelPeriodo(
        ruleId = "rr_arriendo",
        nombre = "Arriendo",
        monto = 1_850_000,
        pagado = false,
        diasParaVencer = 6,
        vence = "2026-10-05",
        categoria = "Vivienda",
        cuentaId = "a1",
    )

    private val anotadas = mutableListOf<String>()

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun botones() = composeRule.onAllNodes(
        hasAnyDescendant(hasText(ETIQUETA_ANOTAR)) and hasClickAction(),
        useUnmergedTree = true,
    )

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun las_filas_que_todavia_no_vencen_ofrecen_anotar_el_pago() {
        assertEquals(EstadoDeLaFila.AUN_NO_VENCE, cuota.estado)
        assertEquals(EstadoDeLaFila.AUN_NO_VENCE, arriendo.estado)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = listOf(cuota, arriendo),
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { _, _ -> },
                        onAnotarMovimiento = { anotadas += it.ruleId },
                        onQuitarLaMarca = {},
                        onReintentar = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val nodos = botones().fetchSemanticsNodes()
        assertEquals(2, nodos.size, "la cuota y el pago fijo ofrecen anotar, cada uno el suyo")
        assertTrue(!hay(TEXTO_SIN_MOVIMIENTO), "antes de vencer, Movi no tenía por qué encontrarlo")

        nodos.indices.forEach { i -> botones()[i].performSemanticsAction(SemanticsActions.OnClick) }
        composeRule.waitForIdle()
        assertEquals(setOf("credit_l9695", "rr_arriendo"), anotadas.toSet())
    }

    /** El camino entero: la fila de la cuota que vence en 18 días → Agregar en «Cuota» con la deuda. */
    @Test
    fun la_cuota_que_vence_en_18_dias_abre_agregar_en_cuota_con_la_deuda() {
        val hoja = anotarYAbrir(cuota)
        assertEquals("l9695", hoja.presetDeudaId)
        assertEquals(1_204_064L, hoja.presetMonto)
        assertTrue(hay("Registrar pago"), "la pestaña «Cuota»")
        assertTrue(hay(libre.name), "con el crédito en «Hacia»")
    }

    @Test
    fun el_pago_fijo_que_vence_en_6_dias_abre_agregar_prellenado() {
        val hoja = anotarYAbrir(arriendo)
        assertEquals("Arriendo", hoja.presetNota)
        assertEquals(1_850_000L, hoja.presetMonto)
        assertTrue(hay("Guardar movimiento"), "la pestaña «Gasto»")
    }

    /** Monta solo la fila [pago]; al tocar su botón la reemplaza por Agregar con lo que devolvió [hojaParaAnotar]. */
    private fun anotarYAbrir(pago: PagoDelPeriodo): Screen.QuickAdd {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, libre)
            override suspend fun getCredits(): List<CreditSummary> =
                listOf(CreditSummary(account = libre, terms = null, paidPct = null))
        }
        var hoja by mutableStateOf<Screen.QuickAdd?>(null)
        composeRule.setContent {
            MoviTheme {
                val abierta = hoja
                if (abierta == null) {
                    Box(Modifier.fillMaxSize()) {
                        SeccionChecklistDelPeriodo(
                            checklist = listOf(pago),
                            cargando = false,
                            pudoLeer = true,
                            marcando = emptySet(),
                            onConfirmar = { _, _ -> },
                            onNoFueEste = { _, _ -> },
                            onAnotarMovimiento = { hoja = hojaParaAnotar(it) as? Screen.QuickAdd },
                            onQuitarLaMarca = {},
                            onReintentar = {},
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            QuickAddScreen(
                                onDismiss = {},
                                presetAccountId = abierta.presetAccountId,
                                presetNota = abierta.presetNota,
                                presetMonto = abierta.presetMonto,
                                presetCategoria = abierta.presetCategoria,
                                presetFecha = abierta.presetFecha,
                                presetEsIngreso = abierta.presetEsIngreso,
                                presetDeudaId = abierta.presetDeudaId,
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        botones()[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val abierta = assertIs<Screen.QuickAdd>(hoja, "«Anotar este pago» abre Agregar antes de vencer")
        composeRule.waitUntil(5_000) { hay(ahorros.name) }
        return abierta
    }
}
