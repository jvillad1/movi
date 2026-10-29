package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.ExtractoDeLaTarjeta
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OrigenDelRecuerdo
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.RecuerdoDelAsistente
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.TransferResult
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.quickadd.TAG_FILA_HACIA
import com.jvillada.movi.ui.quickadd.TAG_PASTILLAS_DEL_PAGO_DE_LA_TARJETA
import com.jvillada.movi.ui.recurrentes.SeccionChecklistDelPeriodo
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * # Vuelta 62 · el extracto de la tarjeta y el abono que se puede hacer, en la pantalla
 *
 * El extracto (`ExtractoDeLaTarjeta`, una suma por fechas del server) se dice igual en la ficha de la
 * tarjeta, en la fila «Pago tarjeta Nu» de Pagos y en las pastillas de «Anotar pago». Y el monto que
 * se simula en «¿Y si abonas de más?» o en «Cómo salir de tus deudas» se puede anotar (Agregar →
 * Traspaso, ya lleno) y, en el plan, pedir que Movi lo recuerde.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp-xhdpi")
class ElExtractoYElAbonoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    // ── La Nu del 29-oct ────────────────────────────────────────────────────────────────────

    private val nuAhorros = Account("n1", "Nu Ahorros", AccountType.SAVINGS, 2_300_000)
    private val nu = Account("nt", "Nu", AccountType.CREDIT_CARD, 1_583_000)
    private val extracto = ExtractoDeLaTarjeta(
        corte = "2026-10-20",
        fechaDePago = "2026-11-05",
        facturado = 1_292_700L,
        pagadoDesdeElCorte = 100_000L,
        minimo = 180_000L,
        despuesDelCorte = 290_300L,
        puedeIncluirDiferidos = true,
    )
    private val tarjeta = CardSummary(
        account = nu,
        terms = CardTerms(accountId = nu.id, bank = "Nu", cutoffDay = 20, paymentDay = 5, pagoMinimo = 180_000L),
        extracto = extracto,
    )

    private fun hay(texto: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNode(hasText(texto) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `la ficha de la tarjeta dice lo facturado al corte, el minimo, la fecha y lo que va al proximo`() {
        composeRule.montarElDetalleDe(nu, RepositorioDeLaDeuda(tarjetas = listOf(tarjeta)))

        assertTrue(hay("Del extracto del 20 oct"))
        assertTrue(hay("\$1.292.700"))
        assertTrue(hay("Mínimo: te faltan \$80.000 · pagar antes del 5 nov"), "el mínimo descuenta lo ya pagado")
        assertTrue(hay("\$1.392.700 · ya pagaste \$100.000"))
        assertTrue(hay("Después del corte: \$290.300 (va al próximo extracto)"))
        assertTrue(hay(PUEDE_INCLUIR_DIFERIDOS), "sin las cuotas conocidas, lo dice")
        assertFalse(hay("Pago mínimo"), "la fila del mínimo suelto se cambió por la del extracto")
    }

    @Test
    fun `la fila de Pagos dice el monto del extracto, con el minimo como alternativa`() {
        val fila = PagoDelPeriodo(
            ruleId = "card_${nu.id}",
            nombre = "Pago tarjeta Nu",
            monto = 1_583_000L,
            pagado = false,
            diasParaVencer = 7,
            montoEsSaldo = true,
            pagoMinimo = 180_000L,
            vence = "2026-11-05",
        )
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = listOf(fila),
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { _, _ -> },
                        onAnotarMovimiento = {},
                        onQuitarLaMarca = {},
                        onReintentar = {},
                        extractosDeTarjetas = mapOf(nu.id to extracto),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(hay("\$1.292.700"), "lo facturado, no el saldo")
        assertTrue(hay(ROTULO_DEL_EXTRACTO))
        assertTrue(hay("o lo que falta del mínimo, \$80.000"))
        assertFalse(hay("saldo"), "ya no pide el saldo entero")
        assertFalse(hay("\$1.583.000"))
    }

    @Test
    fun `la fila de Pagos no usa un extracto que ya vencio`() {
        val fila = PagoDelPeriodo(
            ruleId = "card_${nu.id}", nombre = "Pago tarjeta Nu", monto = 1_583_000L, pagado = false,
            diasParaVencer = 20, montoEsSaldo = true, vence = "2026-12-05",
        )
        assertEquals(null, extractoDeLaFila(fila, mapOf(nu.id to extracto)))
        assertEquals(extracto, extractoDeLaFila(fila.copy(vence = "2026-11-05"), mapOf(nu.id to extracto)))
    }

    @Test
    fun `al anotar el pago de la tarjeta, las pastillas ponen el total del extracto o el minimo`() {
        val pagos = mutableListOf<CreatePagoDeCuotaRequest>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(nuAhorros, nu)
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = listOf(tarjeta)
            override suspend fun payInstallment(request: CreatePagoDeCuotaRequest): PagoDeCuotaResult {
                pagos += request
                return PagoDeCuotaResult(deudaRestante = 290_300, patas = emptyList())
            }
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) { QuickAddScreen(onDismiss = {}, presetDeudaId = nu.id) }
                }
            }
        }
        composeRule.waitUntil(5_000) { hay(pastillaDelTotalDelExtracto(extracto)) }

        assertTrue(hay("Extracto \$1.292.700"))
        assertTrue(hay("Mínimo \$80.000"), "lo que falta del mínimo")
        assertTrue(hay(PASTILLA_OTRO_MONTO))
        composeRule.onAllNodesWithTag(TAG_PASTILLAS_DEL_PAGO_DE_LA_TARJETA, useUnmergedTree = true).fetchSemanticsNodes().single()

        assertTrue(hay("Pago de Nu · extracto del 20 oct"), "el encabezado nombra el corte, no el mes")
        tocar("Mínimo \$80.000")
        assertTrue(hay("\$80.000"), "el mínimo en la cifra")
        tocar("Extracto \$1.292.700")
        composeRule.onNodeWithText("Registrar pago").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { pagos.isNotEmpty() }

        assertEquals(1_292_700L, pagos.single().amount)
        assertEquals(nu.id, pagos.single().debtAccountId)
    }

    // ── El abono que se puede hacer ─────────────────────────────────────────────────────────

    private val libre = CreditSummary(
        account = Account("l9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = "l9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
            insuranceMonthly = 124_800L, debitoAutomaticoDesde = nuAhorros.id, remindMe = false,
        ),
        paidPct = 0.5,
    )

    @Test
    fun `anotar este abono desde la ficha abre Traspaso con el credito, la cuenta y el monto`() {
        val navegado = mutableListOf<Screen>()
        composeRule.montarElDetalleDe(libre.account, RepositorioDeLaDeuda(creditos = listOf(libre)), onNavigate = { navegado += it })
        composeRule.onNode(hasText(ACCION_SIMULAR_ABONO) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        tocar("Una cuota más")
        tocar(ANOTAR_ESTE_ABONO)

        val hoja = assertIs<Screen.QuickAdd>(navegado.last())
        assertEquals(PestanaDeAgregar.TRASPASO, hoja.presetPestana)
        assertEquals(libre.account.id, hoja.presetDeudaId)
        assertEquals(nuAhorros.id, hoja.presetAccountId, "desde la cuenta del débito automático")
        assertEquals(1_204_064L, hoja.presetMonto)
    }

    @Test
    fun `la hoja de Traspaso llega llena y guarda el abono de siempre`() {
        val traspasos = mutableListOf<CreateTransferRequest>()
        val bancolombia = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800)
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia, nuAhorros, libre.account)
            override suspend fun createTransfer(request: CreateTransferRequest): TransferResult {
                traspasos += request
                fun pata(id: String, cuenta: String, tipo: TransactionType) =
                    FinancialEvent(id = id, accountId = cuenta, type = tipo, amount = request.amount, category = "Traspaso", description = "", timestamp = 0L)
                return TransferResult(pata(request.fromEventId, request.fromAccountId, TransactionType.EXPENSE), pata(request.toEventId, request.toAccountId, TransactionType.INCOME))
            }
        }
        val hoja = pantallaDelAbono(libre, 1_204_064L)
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        QuickAddScreen(
                            onDismiss = {},
                            presetAccountId = hoja.presetAccountId,
                            presetMonto = hoja.presetMonto,
                            presetDeudaId = hoja.presetDeudaId,
                            presetPestana = hoja.presetPestana,
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(5_000) { hay(libre.account.name, substring = true) }

        composeRule.onNode(hasText(libre.account.name) and hasAnyAncestor(hasTestTag(TAG_FILA_HACIA)), useUnmergedTree = true).assertExists()
        assertTrue(hay("\$1.204.064"), "el monto simulado en la cifra")
        assertTrue(hay("Abono extraordinario:", substring = true), "dice que es un abono extraordinario")

        composeRule.onNodeWithText("Guardar traspaso").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { traspasos.isNotEmpty() }
        val abono = traspasos.single()
        assertEquals(1_204_064L, abono.amount)
        assertEquals(libre.account.id, abono.toAccountId)
        assertEquals(nuAhorros.id, abono.fromAccountId)
    }

    @Test
    fun `el plan de salida anota el abono y lo recuerda cada mes sin crear un fijo`() {
        val navegado = mutableListOf<Screen>()
        val recuerdos = mutableListOf<String>()
        val terminos = mutableListOf<CreditTerms>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = listOf(libre)
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
            override suspend fun getMemoriaDelAsistente(): List<RecuerdoDelAsistente> =
                recuerdos.mapIndexed { i, t -> RecuerdoDelAsistente(id = "r$i", texto = t, creadoEn = 0L, origen = OrigenDelRecuerdo.A_MANO) }
            override suspend fun guardarRecuerdo(texto: String, origen: OrigenDelRecuerdo, propuestaId: String?): RecuerdoDelAsistente {
                recuerdos += texto
                return RecuerdoDelAsistente(id = "r${recuerdos.lastIndex}", texto = texto, creadoEn = 0L, origen = origen)
            }
            override suspend fun editarRecuerdo(id: String, texto: String): RecuerdoDelAsistente {
                recuerdos[id.removePrefix("r").toInt()] = texto
                return RecuerdoDelAsistente(id = id, texto = texto, creadoEn = 0L, origen = OrigenDelRecuerdo.A_MANO)
            }
            override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary {
                terminos += terms
                return libre.copy(terms = terms)
            }
            override suspend fun createRecurringRule(rule: com.jvillada.movi.shared.model.RecurringRule) =
                error("el abono no es una regla recurrente")
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SalidaDeDeudasScreen(onNavigate = { navegado += it }) } }
        }
        composeRule.waitUntil(5_000) { hay("Si abonas al mes extra") }
        val campo = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
        campo.performSemanticsAction(SemanticsActions.RequestFocus)
        campo.performTextInput("500000")
        composeRule.waitUntil(5_000) { hay(ANOTAR_ESTE_ABONO) }

        assertTrue(hay(aQuienVaElAbono(libre.account.name)))
        assertTrue(hay(EL_ABONO_NO_ES_UN_FIJO))

        tocar(ANOTAR_ESTE_ABONO)
        val hoja = assertIs<Screen.QuickAdd>(navegado.last())
        assertEquals(PestanaDeAgregar.TRASPASO, hoja.presetPestana)
        assertEquals(libre.account.id, hoja.presetDeudaId)
        assertEquals(500_000L, hoja.presetMonto)

        tocar(RECORDARMELO_CADA_MES)
        composeRule.waitUntil(5_000) { hay(confirmacionDelRecordatorio(libre.account.name, 500_000L, 15)) }
        assertEquals(listOf(recuerdoDelAbono(libre.account.name, 500_000L, 15)), recuerdos)
        assertTrue(terminos.single().remindMe, "el aviso de la deuda queda prendido")
        assertEquals("Te avisamos cuando venza Libre inversión 9695 (el 15) y Movi AI recuerda tu plan de \$500.000.", confirmacionDelRecordatorio(libre.account.name, 500_000L, 15))

        // Otro monto, y pedirlo otra vez: reemplaza el recuerdo, no crea otro.
        campo.performTextInput("0")
        composeRule.waitUntil(5_000) { hay(RECORDARMELO_CADA_MES) && !hay("Te avisamos", substring = true) }
        tocar(RECORDARMELO_CADA_MES)
        composeRule.waitUntil(5_000) { hay(confirmacionDelRecordatorio(libre.account.name, 5_000_000L, 15)) }
        assertEquals(listOf(recuerdoDelAbono(libre.account.name, 5_000_000L, 15)), recuerdos, "uno solo, el nuevo")
    }

    @Test
    fun `el plan no anota mas que la deuda ni recuerda un monto mayor que toda la deuda`() {
        val navegado = mutableListOf<Screen>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = listOf(libre)
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SalidaDeDeudasScreen(onNavigate = { navegado += it }) } }
        }
        composeRule.waitUntil(5_000) { hay("Si abonas al mes extra") }
        val campo = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
        campo.performSemanticsAction(SemanticsActions.RequestFocus)
        campo.performTextInput("90000000")
        composeRule.waitUntil(5_000) { hay(ANOTAR_ESTE_ABONO) }

        assertTrue(hay(avisoDeAbonoMayorQueLaDeuda(libre.account.balance)), "el campo avisa que pasa de la deuda")
        tocar(ANOTAR_ESTE_ABONO)
        assertEquals(libre.account.balance, assertIs<Screen.QuickAdd>(navegado.last()).presetMonto, "nunca más que el saldo")
        composeRule.onNode(hasText(RECORDARMELO_CADA_MES) and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun `un abono menor que el extracto sella la fila pero la fila dice lo que falta`() {
        val pagada = PagoDelPeriodo(
            ruleId = "card_${nu.id}", nombre = "Pago tarjeta Nu", monto = 1_583_000L, pagado = true,
            diasParaVencer = 7, montoEsSaldo = true, vence = "2026-11-05", montoPagado = 100_000L, derivado = true,
        )
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = listOf(pagada), cargando = false, pudoLeer = true, marcando = emptySet(),
                        onConfirmar = { _, _ -> }, onNoFueEste = { _, _ -> }, onAnotarMovimiento = {},
                        onQuitarLaMarca = {}, onReintentar = {},
                        extractosDeTarjetas = mapOf(nu.id to extracto),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(hay("Abonaste \$100.000 · faltan \$1.292.700 del extracto (mínimo: faltan \$80.000)"))
    }

    @Test
    fun `con el extracto pagado la ficha dice pagado, sin minimo, fecha ni diferidos`() {
        val pagado = tarjeta.copy(extracto = extracto.copy(facturado = 0L, pagadoDesdeElCorte = 1_392_700L))
        composeRule.montarElDetalleDe(nu, RepositorioDeLaDeuda(tarjetas = listOf(pagado)))

        assertTrue(hay("Extracto del 20 oct · pagado ✓"))
        assertFalse(hay("Mínimo", substring = true))
        assertFalse(hay("pagar antes", substring = true))
        assertFalse(hay(PUEDE_INCLUIR_DIFERIDOS))
        assertTrue(hay("Después del corte: \$290.300 (va al próximo extracto)"))
    }

    @Test
    fun `la lista de Creditos dice la linea del extracto, no el minimo suelto`() {
        val hoy = kotlinx.datetime.LocalDate(2026, 10, 29)
        assertEquals("Extracto \$1.292.700 · pagar antes del 5 nov", tarjetaEnLaLista(tarjeta, hoy).subtitulo)
        val pagado = tarjeta.copy(extracto = extracto.copy(facturado = 0L))
        assertEquals("Extracto del 20 oct · pagado ✓", tarjetaEnLaLista(pagado, hoy).subtitulo)
    }
}
