package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.recurrentes.ETIQUETA_ANOTAR
import com.jvillada.movi.ui.recurrentes.SeccionChecklistDelPeriodo
import com.jvillada.movi.ui.recurrentes.TAG_FUERA_DE_TU_PLATA
import com.jvillada.movi.ui.recurrentes.TAG_HOJA_DE_LA_FACTURA
import com.jvillada.movi.ui.recurrentes.TAG_LO_MAS_URGENTE
import com.jvillada.movi.ui.recurrentes.abrirLaFactura
import com.jvillada.movi.ui.recurrentes.hojaParaAnotar
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * # Vuelta 141 · la cuota de la Hipoteca 1254, en Pagos, sale de la AFC (issue #701)
 *
 * La fila de la cuota que se paga desde una cuenta fuera de Tu plata está en «Tus facturas del período»; su
 * hoja dice «Sale de AFC Davibank 9497: no toca Tu plata» y ofrece «Anotar pago», que abre Pagar con el crédito
 * y «Sale de» la AFC ya puestos (aunque esté condicionada) y guarda el pago de cuota de siempre desde la AFC.
 *
 * Sin el cambio no compila (`PagoDelPeriodo.saleDeFueraDeTuPlata`, `CreditTerms.sePagaDesde`), y sin la cuenta
 * conservada en «Sale de» la AFC no se podía elegir (una cuenta condicionada no está en la plata propia).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xhdpi")
class LaCuotaDesdeLaAfcEnPagosTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a8133", "Bancolombia 8133", AccountType.SAVINGS, 5_000_000)
    private val afc = Account("afc9497", "AFC Davibank 9497", AccountType.SAVINGS, 30_000_000, condicionadaA = "vivienda")
    private val hipoteca = Account("h1254", "Hipoteca 1254", AccountType.LOAN, 300_000_000)

    private val condiciones = CreditTerms(
        accountId = hipoteca.id, bank = "Davibank", principal = 400_000_000L, rateEa = 12.0, termMonths = 240,
        installment = 9_145_256L, dayOfMonth = 5, startDate = "2020-01-05", sePagaDesde = afc.id,
    )

    private val laCuota = PagoDelPeriodo(
        ruleId = "$CREDIT_RULE_PREFIX${hipoteca.id}", nombre = "Cuota Hipoteca 1254", monto = 9_145_256L, pagado = false,
        diasParaVencer = 4, vence = "2026-12-05", categoria = "Cuota de crédito",
        sePagaDesde = afc.id, saleDeFueraDeTuPlata = true,
    )

    private val pagos = mutableListOf<CreatePagoDeCuotaRequest>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `la fila dice de donde sale, Anotar pago abre Pagar con la AFC puesta y paga desde ahi`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, afc, hipoteca)
            override suspend fun getCredits(): List<CreditSummary> =
                listOf(CreditSummary(account = hipoteca, terms = condiciones, paidPct = null))
            override suspend fun payInstallment(request: CreatePagoDeCuotaRequest): PagoDeCuotaResult {
                pagos += request
                return PagoDeCuotaResult(deudaRestante = 292_000_000, patas = emptyList())
            }
        }
        var hoja by mutableStateOf<Screen.QuickAdd?>(null)
        composeRule.setContent {
            MoviTheme {
                val abierta = hoja
                if (abierta == null) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SeccionChecklistDelPeriodo(
                            checklist = listOf(laCuota),
                            cargando = false,
                            pudoLeer = true,
                            marcando = emptySet(),
                            onConfirmar = { _, _ -> },
                            onNoFueEste = { _, _ -> },
                            onAnotarMovimiento = { p -> hoja = hojaParaAnotar(p) as? Screen.QuickAdd },
                            onQuitarLaMarca = {},
                            onReintentar = {},
                            onAbrir = {},
                            nombresDeCuentas = mapOf(afc.id to afc.name, ahorros.id to ahorros.name),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            QuickAddScreen(
                                onDismiss = {},
                                presetAccountId = abierta.presetAccountId,
                                presetMonto = abierta.presetMonto,
                                presetDeudaId = abierta.presetDeudaId,
                                presetMesDeLaCuota = abierta.presetMesDeLaCuota,
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Está en la lista, pero no es «lo más urgente» de «Falta por pagar»: no suma.
        assertTrue(hay("Cuota Hipoteca 1254"), "la cuota sale en Pagos")
        assertTrue(
            composeRule.onAllNodesWithTag(TAG_LO_MAS_URGENTE, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "no apura en «Falta por pagar»",
        )

        // La hoja de la factura: de dónde sale, y «Anotar pago».
        composeRule.abrirLaFactura("Cuota Hipoteca 1254")
        composeRule.waitForIdle()
        composeRule.onNode(
            hasTestTag(TAG_FUERA_DE_TU_PLATA) and hasText("Sale de AFC Davibank 9497: no toca Tu plata"),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onAllNodes(
            hasClickAction() and hasAnyDescendant(hasText(ETIQUETA_ANOTAR)) and hasAnyAncestor(hasTestTag(TAG_HOJA_DE_LA_FACTURA)),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Pagar, con el crédito en «Pagas» y la AFC en «Sale de», diciendo por qué.
        val abierta = assertIs<Screen.QuickAdd>(hoja)
        assertEquals(hipoteca.id, abierta.presetDeudaId)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(afc.name) and hasAnyAncestor(hasTestTag(TAG_FILA_DESDE)), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("La de la cuota", substring = true) and hasAnyAncestor(hasTestTag(TAG_FILA_DESDE)), useUnmergedTree = true)
            .assertExists()
        assertTrue(hay("\$9.145.256"), "la cuota del mes, en la cifra")

        composeRule.onNodeWithText("Registrar pago").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { pagos.isNotEmpty() }
        val pago = pagos.single()
        assertEquals(afc.id, pago.fromAccountId, "el pago sale de la AFC")
        assertEquals(hipoteca.id, pago.debtAccountId)
        assertEquals(9_145_256L, pago.amount)
    }
}
