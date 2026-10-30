package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.TAG_DEUDA_SIN_SALDO
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 59: los créditos dicen la verdad, en pantalla
 *
 * «Vehículo 4083» se creó con $40M de capital y sin saldo, y la primera cuota lo dejó en −$980.000:
 * «+$980.000» en verde en la ficha, «−$980.000» en la lista, y «Deuda total $603.000» arriba. Ahora la
 * ficha y la lista dicen lo mismo —«Sin saldo en Movi»—, la deuda total no lo resta y lo nombra con
 * «Cuadrar», y el alta de un crédito que ya venía propone el saldo del plan en vez de suponer cero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class LosCreditosDicenLaVerdadEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val vehiculo = CreditSummary(
        account = Account("acc_4083", "Vehículo 4083", AccountType.LOAN, balance = -980_000L),
        terms = CreditTerms(
            accountId = "acc_4083", bank = "Banco", principal = 40_000_000L, rateEa = 18.0,
            termMonths = 60, installment = 980_000L, dayOfMonth = 5, startDate = "2026-01-10",
        ),
        paidPct = null,
        hasMovements = true,
    )

    private val nu = CardSummary(
        account = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, 1_583_000L, balancesByCurrency = mapOf("COP" to 1_583_000L)),
        terms = CardTerms(accountId = "acc_nu", bank = "Nu", paymentDay = 7),
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun cuantos(texto: String): Int =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `la ficha dice sin saldo en Movi, sin el signo al reves, y ofrece cuadrarla`() {
        composeRule.montarElDetalleDe(vehiculo.account, RepositorioDeLaDeuda(creditos = listOf(vehiculo)))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_DEUDA_SIN_SALDO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Vuelta 73: un crédito con condiciones dice qué le falta, con «Cargarlo».
        // Vuelta 80: la pieza de toda la app, con el nombre («Falta el saldo de Vehículo 4083»).
        assertEquals(1, cuantos(com.jvillada.movi.ui.credits.textoDelCreditoSinSaldo("Vehículo 4083")), "la ficha lo dice una vez")
        assertEquals(0, cuantos(FALTA_EL_SALDO_DE_LA_DEUDA), "sin la frase de las tarjetas")
        assertFalse(hay("+\$980.000"), "el «+$980.000» en verde de antes")
        assertFalse(hay("−\$980.000"))
        // «Cargarlo» —o, con sus condiciones completas, «Usar este» (vuelta 77: una sola acción de saldo)—
        // abre la hoja de ajustar el saldo de su ficha, que tampoco dice la cifra al revés.
        composeRule.onNode(hasText(com.jvillada.movi.ui.credits.USAR_EL_SALDO_DEL_PLAN) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { hay("Deuda en Movi") }
        assertFalse(hay("−\$980.000"), "la hoja decía «Deuda en Movi −$980.000»")
        assertTrue(hay(SIN_SALDO_EN_MOVI), "la hoja dice que Movi no tiene el saldo")
    }

    @Test
    fun `la lista dice lo mismo que la ficha y la deuda total no lo resta`() {
        val destinos = mutableListOf<Screen>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = listOf(vehiculo)
            override suspend fun getCards(): List<CardSummary> = listOf(nu)
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CreditosScreen(onNavigate = { destinos += it }) } } }
        composeRule.waitUntil(5_000) { hay("Vehículo 4083") }
        composeRule.waitForIdle()

        // Vuelta 80: la fila lleva la pieza del préstamo sin saldo (la misma de la ficha), no la píldora.
        assertFalse(hay(SIN_SALDO_EN_MOVI), "sin la píldora: la pieza de abajo ya lo dice")
        assertFalse(hay("−\$980.000"), "la fila decía «−$980.000»")
        // Deuda total: los $1.583.000 de Nu, no $603.000.
        assertTrue(hay("\$1.583.000"))
        assertFalse(hay("\$603.000"))
        // Y el crédito sin saldo se nombra con su salida: la misma pieza que Patrimonio y la ficha (vuelta 80),
        // en el resumen y en su fila, con «Usar este» (sus condiciones están completas).
        assertEquals(2, cuantos(com.jvillada.movi.ui.credits.textoDelCreditoSinSaldo("Vehículo 4083")))
        composeRule.onAllNodesWithTag(TAG_DEUDAS_SIN_SALDO, useUnmergedTree = true).fetchSemanticsNodes().single()
        composeRule.onAllNodes(hasText(com.jvillada.movi.ui.credits.USAR_EL_SALDO_DEL_PLAN) and hasClickAction())[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay(com.jvillada.movi.ui.credits.ES_LA_CIFRA_DE_TU_PLAN), "la hoja llega con la cifra del plan")
        assertTrue(destinos.isEmpty())
    }

    /** Patrimonio decía «Deudas −$603.000» y abajo «Créditos y tarjetas $1.583.000», sin explicar por qué. */
    @Test
    fun `patrimonio dice que sus deudas incluyen el credito sin saldo`() {
        val destinos = mutableListOf<Screen>()
        val ahorros = Account("acc_ah", "Ahorros", AccountType.SAVINGS, 10_000_000L, balancesByCurrency = mapOf("COP" to 10_000_000L))
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, vehiculo.account, nu.account)
            override suspend fun getCredits(): List<CreditSummary> = listOf(vehiculo)
            override suspend fun getCards(): List<CardSummary> = listOf(nu)
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun getApartados(): List<com.jvillada.movi.shared.model.Apartado> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { com.jvillada.movi.ui.accounts.AccountsScreen(onNavigate = { destinos += it }) } }
        }
        // Vuelta 73: con un solo crédito con condiciones sin saldo, dice cuál y ofrece cargarlo.
        composeRule.waitUntil(5_000) { hay("Falta el saldo de Vehículo 4083") }
        composeRule.onAllNodesWithTag(com.jvillada.movi.ui.accounts.TAG_DEUDAS_SIN_SALDO_EN_EL_PATRIMONIO, useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        // Vuelta 77: con sus condiciones completas la fila ofrece «Usar este», que abre la hoja del saldo ya
        // llena con la cifra del plan (sin «Cargarlo» al lado).
        assertFalse(hay(com.jvillada.movi.ui.credits.CARGAR_EL_SALDO_DEL_CREDITO))
        composeRule.onNode(hasText(com.jvillada.movi.ui.credits.USAR_EL_SALDO_DEL_PLAN) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay(com.jvillada.movi.ui.credits.ES_LA_CIFRA_DE_TU_PLAN), "la hoja llega con la cifra del plan")
    }

    @Test
    fun `el alta de un credito que ya venia propone el saldo del plan`() {
        var pedido: CreateCreditRequest? = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun createCredit(request: CreateCreditRequest): CreditSummary {
                pedido = request
                return CreditSummary(account = vehiculo.account, terms = request.terms, paidPct = null)
            }
        }
        composeRule.setContent {
            MoviTheme { CreditTermsSheet(editing = null, candidates = emptyList(), onDismiss = {}, onSaved = {}) }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("No, ya lo tenía desde antes") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        fun escribir(rotulo: String, texto: String) {
            composeRule.onNode(hasSetTextAction() and hasContentDescription(rotulo), useUnmergedTree = true).apply {
                performScrollTo()
                performTextInput(texto)
            }
            composeRule.waitForIdle()
        }
        escribir("Nombre", "Vehículo 4083")
        // Sin términos todavía: la hoja no supone cero, dice qué le falta al plan.
        assertTrue(hay("Para proponerte la deuda, falta el capital original"))

        escribir("Banco", "Banco")
        escribir("Capital original", "40000000")
        escribir("Tasa E.A.", "18")
        escribir("Plazo (meses)", "60")
        escribir("Cuota mensual", "1000000")
        escribir("Día de pago", "5")
        escribir("Fecha del desembolso", "2026-01-02")

        composeRule.waitUntil(5_000) { hay("Por tu plan deberías unos") }
        // Sin una cifra no se guarda: así nació el crédito en −$980.000.
        assertTrue(hay(FALTA_LA_DEUDA_ACTUAL))

        composeRule.onNode(hasText(USAR_EL_SALDO_DEL_PLAN) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertFalse(hay(FALTA_LA_DEUDA_ACTUAL))
        composeRule.onNode(hasText("Guardar crédito") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { pedido != null }

        val enviado = assertNotNull(pedido)
        assertTrue(enviado.initialDebt in 30_000_000L..39_000_000L, "la deuda del plan, no 0: ${enviado.initialDebt}")
    }
}
