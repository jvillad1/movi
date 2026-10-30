package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.TransferResult
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.epochDeFecha
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.quickadd.AbonoDeLaRegla
import com.jvillada.movi.ui.quickadd.EL_ABONO_PENDIENTE_NO_RESTA
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.quickadd.lineaDelAbonoPlaneado
import com.jvillada.movi.ui.recurrentes.SeccionChecklistDelPeriodo
import com.jvillada.movi.ui.recurrentes.hojaParaAnotar
import kotlinx.coroutines.runBlocking
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
 * # Vuelta 63 · el abono mensual, en la pantalla
 *
 * «Recordármelo cada mes» crea la regla del abono, que sale en Pagos como «Abono extra a Libre
 * inversión 9695 · $500.000 · El día 15» sin sumar en «Falta por pagar» (va aparte). «Anotar este
 * abono» abre el Traspaso ya lleno y, al guardar, sella la fila con la pata que salió: queda «Pagado ✓».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp-xhdpi")
class ElAbonoQueSeTildaEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private val nuAhorros = Account("n1", "Nu Ahorros", AccountType.SAVINGS, 2_300_000)
    private val libre = CreditSummary(
        account = Account("l9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = "l9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
            debitoAutomaticoDesde = nuAhorros.id,
        ),
        paidPct = 0.5,
    )

    private fun hay(texto: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNode(hasText(texto) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** La fila de Pagos que sale de [regla]: el checklist de siempre sobre su vencimiento del 15-nov. */
    private fun filaDe(regla: RecurringRule, dias: Int = 10): List<PagoDelPeriodo> {
        val ajustes = PeriodSettings()
        val arriendo = RecurringRule("rr_arriendo", "Arriendo", "Vivienda", 1_800_000L, 10, TransactionType.EXPENSE)
        return checklistDelPeriodo(
            upcoming = listOf(
                UpcomingPayment(regla, "2026-11-15", dias, PaymentStatus.UPCOMING),
                UpcomingPayment(arriendo, "2026-11-10", dias - 5, PaymentStatus.UPCOMING),
            ),
            ocurrencias = emptyList(),
            periodo = periodoDe(epochDeFecha("2026-11-05"), ajustes),
            settings = ajustes,
        )
    }

    @Test
    fun `recordarmelo crea la regla y la fila sale en Pagos, sin sumar en Falta por pagar`() {
        val reglas = mutableListOf<RecurringRule>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getRecurringRules() = reglas.toList()
            override suspend fun createRecurringRule(rule: RecurringRule) = rule.copy(id = "rr_abono").also { reglas += it }
        }
        runBlocking { recordarElAbono(libre, 500_000L) }
        val regla = reglas.single()
        assertEquals(libre.account.id, regla.haciaDeuda)

        val filas = filaDe(regla)
        val abono = filas.single { it.ruleId == "rr_abono" }
        assertTrue(abono.esAbono)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = filas, cargando = false, pudoLeer = true, marcando = emptySet(),
                        onConfirmar = { _, _ -> }, onNoFueEste = { _, _ -> }, onAnotarMovimiento = {},
                        onQuitarLaMarca = {}, onReintentar = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(hay("Abono extra a Libre inversión 9695"))
        assertTrue(hay("El día 15"), "un abono no «vence»")
        assertTrue(hay(ANOTAR_ESTE_ABONO))
        assertTrue(hay(EL_ABONO_PENDIENTE_NO_RESTA))
        assertTrue(hay(lineaDelAbonoPlaneado(500_000L)), "va aparte de lo que falta")
        assertTrue(hay("de \$1.800.000 · queda 1 de 1 pago"), "«Falta por pagar» es solo el arriendo")
    }

    @Test
    fun `a una tarjeta la fila dice que el abono no cambia Te quedan`() {
        val aNu = RecurringRule(
            id = "rr_abono", name = "Abono extra a Nu", category = "Traspaso", amount = 500_000L,
            dayOfMonth = 15, type = TransactionType.EXPENSE, accountId = nuAhorros.id, haciaDeuda = "nu",
        )
        val filas = com.jvillada.movi.ui.quickadd.conLosAbonosATarjeta(filaDe(aNu), setOf("nu"))
        assertTrue(filas.single { it.ruleId == "rr_abono" }.abonoATarjeta)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = filas, cargando = false, pudoLeer = true, marcando = emptySet(),
                        onConfirmar = { _, _ -> }, onNoFueEste = { _, _ -> }, onAnotarMovimiento = {},
                        onQuitarLaMarca = {}, onReintentar = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(hay(com.jvillada.movi.shared.model.EL_ABONO_A_UNA_TARJETA_NO_CAMBIA_TE_QUEDAN))
        assertTrue(!hay(EL_ABONO_PENDIENTE_NO_RESTA), "no promete que baja «Te quedan»")
        assertEquals(
            "A una tarjeta, pagar de más no cambia «Te quedan»: lo que compraste ya contó.",
            elAbonoNoEsUnFijo(aTarjeta = true),
        )
        assertEquals(EL_ABONO_NO_ES_UN_FIJO, elAbonoNoEsUnFijo(aTarjeta = false))
    }

    @Test
    fun `sin debito automatico la regla sale de la cuenta con que mas se le paga a la deuda`() {
        val bancolombia = "a1"
        fun pago(t: String, desde: String) = listOf(
            FinancialEvent(id = "s-$t", accountId = desde, type = TransactionType.EXPENSE, amount = 1L, category = "Pago de tarjeta", description = "", timestamp = 0L, transferId = t),
            FinancialEvent(id = "e-$t", accountId = "nu", type = TransactionType.INCOME, amount = 1L, category = "Pago de tarjeta", description = "", timestamp = 0L, transferId = t),
        )
        val eventos = pago("t1", bancolombia) + pago("t2", bancolombia) + pago("t3", nuAhorros.id)
        assertEquals(bancolombia, cuentaQueMasLePagaA("nu", eventos))
        assertEquals(null, cuentaQueMasLePagaA("otra", eventos))

        val reglas = mutableListOf<RecurringRule>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getRecurringRules() = reglas.toList()
            override suspend fun createRecurringRule(rule: RecurringRule) = rule.copy(id = "rr_abono").also { reglas += it }
            override suspend fun getEvents(accountId: String?) = eventos
        }
        runBlocking { recordarElAbonoA(deudaId = "nu", nombre = "Nu", monto = 500_000L, dia = 5, desde = null) }
        assertEquals(bancolombia, reglas.single().accountId, "«Anotar este abono» abre con «Desde»")
    }

    @Test
    fun `recordarmelo tambien desde la ficha del credito`() {
        val reglas = mutableListOf<RecurringRule>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getRecurringRules() = reglas.toList()
            override suspend fun createRecurringRule(rule: RecurringRule) = rule.copy(id = "rr_abono").also { reglas += it }
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SimuladorDeAbonoSheet(
                        credit = libre,
                        periodoActual = periodoDe(epochDeFecha("2026-11-05"), PeriodSettings()),
                        onDismiss = {},
                        onAnotarAbono = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        tocar("Una cuota más")
        tocar(RECORDARMELO_CADA_MES)
        composeRule.waitUntil(5_000) { hay(confirmacionDelRecordatorio(libre.account.name, 1_204_064L, 15)) }
        val regla = reglas.single()
        assertEquals(libre.account.id, regla.haciaDeuda)
        assertEquals(1_204_064L, regla.amount)
        assertEquals(nuAhorros.id, regla.accountId)
    }

    @Test
    fun `anotar este abono desde la fila abre el Traspaso lleno y al guardar la deja pagada`() {
        val regla = RecurringRule(
            id = "rr_abono", name = "Abono extra a Libre inversión 9695", category = "Traspaso", amount = 500_000L,
            dayOfMonth = 15, type = TransactionType.EXPENSE, accountId = nuAhorros.id, haciaDeuda = libre.account.id,
        )
        val filas = mutableStateOf(filaDe(regla))
        // Pagos y, al tocar «Anotar este abono», la hoja de Agregar que abre (como App.kt).
        val hojaAbierta = mutableStateOf<Screen.QuickAdd?>(null)
        val traspasos = mutableListOf<CreateTransferRequest>()
        val sellos = mutableListOf<Triple<String, String, String?>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(nuAhorros, libre.account)
            override suspend fun createTransfer(request: CreateTransferRequest): TransferResult {
                traspasos += request
                fun pata(id: String, cuenta: String, tipo: TransactionType) =
                    FinancialEvent(id = id, accountId = cuenta, type = tipo, amount = request.amount, category = "Traspaso", description = "", timestamp = 0L)
                return TransferResult(pata(request.fromEventId, request.fromAccountId, TransactionType.EXPENSE), pata(request.toEventId, request.toAccountId, TransactionType.INCOME))
            }
            override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence {
                sellos += Triple(ruleId, period, eventId)
                // Lo que la pantalla vuelve a leer después: la fila ya sellada con ese movimiento.
                filas.value = filas.value.map { if (it.ruleId == ruleId) it.copy(pagado = true, eventId = eventId, montoPagado = 500_000L) else it }
                return RecurringOccurrence(ruleId = ruleId, period = period, eventId = eventId, confirmedAt = 1L)
            }
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    val hoja = hojaAbierta.value
                    if (hoja != null) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            QuickAddScreen(
                                onDismiss = {},
                                onSaved = { hojaAbierta.value = null },
                                presetAccountId = hoja.presetAccountId,
                                presetMonto = hoja.presetMonto,
                                presetDeudaId = hoja.presetDeudaId,
                                presetPestana = hoja.presetPestana,
                                presetAbono = hoja.presetAbono,
                                presetAbonoExtra = hoja.presetAbonoExtra,
                            )
                        }
                    } else {
                        SeccionChecklistDelPeriodo(
                            checklist = filas.value, cargando = false, pudoLeer = true, marcando = emptySet(),
                            onConfirmar = { _, _ -> }, onNoFueEste = { _, _ -> },
                            onAnotarMovimiento = { hojaAbierta.value = hojaParaAnotar(it) as Screen.QuickAdd },
                            onQuitarLaMarca = {}, onReintentar = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(hay("El día 15"))
        tocar(ANOTAR_ESTE_ABONO)
        val hoja = assertIs<Screen.QuickAdd>(hojaAbierta.value)
        // Vuelta 77: a un crédito, Pagar en modo abono (era Traspaso).
        assertEquals(PestanaDeAgregar.PAGO_DE_TARJETA, hoja.presetPestana)
        assertTrue(hoja.presetAbonoExtra)
        assertEquals(libre.account.id, hoja.presetDeudaId)
        assertEquals(nuAhorros.id, hoja.presetAccountId)
        assertEquals(500_000L, hoja.presetMonto)
        assertEquals(AbonoDeLaRegla("rr_abono", "2026-11"), hoja.presetAbono)

        composeRule.waitUntil(5_000) { hay(libre.account.name, substring = true) }
        assertTrue(hay("\$500.000"), "el monto planeado en la cifra")
        composeRule.onNodeWithText("Registrar abono").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { sellos.isNotEmpty() && hojaAbierta.value == null }

        val traspaso = traspasos.single()
        assertEquals(libre.account.id, traspaso.toAccountId)
        assertEquals(nuAhorros.id, traspaso.fromAccountId)
        assertEquals(Triple("rr_abono", "2026-11", traspaso.fromEventId), sellos.single(), "se sella con la pata que salió")
        composeRule.waitForIdle()
        assertTrue(hay("Pagado"), "la fila queda «Pagado ✓»")
    }
}
