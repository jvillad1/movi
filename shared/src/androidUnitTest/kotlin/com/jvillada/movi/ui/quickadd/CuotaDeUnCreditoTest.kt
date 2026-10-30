package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.TransferResult
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 48 · En Cuota, tocar un crédito es pagar su cuota del mes
 *
 * La revisión de la 47 (r47): la sección «Créditos» de «¿Qué estás pagando?» decía «Elige uno para
 * registrar el desembolso que te entró a la cuenta, o un abono extraordinario…» —en una pestaña que se
 * llama Cuota y con un botón «Registrar pago»—, la cuota del mes no se proponía, y la hoja crecía ~70
 * px al aparecer «Deuda de Nu: $X pasa a $Y». Ahora:
 *
 * - elegir el crédito deja la hoja en «Cuota de X · mes», con su cuota en la cifra y «Sale de» en la
 *   cuenta del débito automático; guardar escribe el pago de cuota de siempre (dos patas, que saldan
 *   la cuota del período en el server — ver `PagoDeCuotaSaldaElPeriodoTest` en `:server`);
 * - el abono extra es un enlace de la misma hoja, y guarda un traspaso;
 * - el desembolso ya no se ofrece en Cuota y sigue en Traspaso;
 * - el renglón de la deuda reserva su alto: la hoja no salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class CuotaDeUnCreditoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800)
    private val nuAhorros = Account("n1", "Nu Ahorros", AccountType.SAVINGS, 2_300_000)
    private val nuTarjeta = Account("nt", "Nu", AccountType.CREDIT_CARD, 1_683_000)
    private val libre = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 40_710_555)

    private val condiciones = CreditTerms(
        accountId = libre.id, bank = "Bancolombia", principal = 50_000_000L, rateEa = 11.27, termMonths = 60,
        installment = 1_204_064L, dayOfMonth = 5, startDate = "2024-06-05", insuranceMonthly = 124_800L,
        debitoAutomaticoDesde = nuAhorros.id,
    )

    private val traspasos = mutableListOf<CreateTransferRequest>()
    private val pagos = mutableListOf<CreatePagoDeCuotaRequest>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun pata(id: String, cuenta: String, tipo: TransactionType, monto: Long) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, category = "Traspaso", description = "", timestamp = 0L,
    )

    private val navegado = mutableListOf<com.jvillada.movi.ui.Screen>()

    private fun montar(pestana: String, credito: Account = libre) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, nuAhorros, nuTarjeta, credito)
            override suspend fun getCredits(): List<CreditSummary> =
                listOf(CreditSummary(account = credito, terms = condiciones, paidPct = null))
            override suspend fun createTransfer(request: CreateTransferRequest): TransferResult {
                traspasos += request
                return TransferResult(
                    pata(request.fromEventId, request.fromAccountId, TransactionType.EXPENSE, request.amount),
                    pata(request.toEventId, request.toAccountId, TransactionType.INCOME, request.amount),
                )
            }
            override suspend fun payInstallment(request: CreatePagoDeCuotaRequest): PagoDeCuotaResult {
                pagos += request
                return PagoDeCuotaResult(deudaRestante = 39_000_000, patas = emptyList())
            }
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) { QuickAddScreen(onDismiss = {}, onNavigate = { navegado += it }) }
                    Spacer(Modifier.height(Medida.altoDeBarraDePestanas))
                }
            }
        }
        composeRule.waitForIdle()
        tocar(pestana)
        composeRule.waitUntil(5_000) { hay(ahorros.name) }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocarElTag(tag: String) {
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun teclear(digitos: String) = digitos.forEach { tocar(it.toString()) }

    private fun borrar(veces: Int) = repeat(veces) {
        composeRule.onNodeWithContentDescription("Borrar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun elegirLaDeuda(nombre: String) {
        tocarElTag(TAG_FILA_HACIA)
        tocar(nombre)
    }

    private fun encabezado(): String =
        composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_LA_CUOTA, useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }

    @Test
    fun `elegir un credito deja la hoja en la cuota del mes, con su monto y el debito automatico`() {
        montar("Pagar")
        assertEquals(CUOTA_SIN_ELEGIR, encabezado())

        elegirLaDeuda(libre.name)
        esperar("\$1.204.064")

        assertTrue(encabezado().startsWith("Cuota de Libre inversión 9695 · "), "«Cuota de X · mes»: ${encabezado()}")
        assertTrue(hay("\$1.204.064"), "la cuota del mes, en la cifra")
        assertTrue(hay("Hoy"), "fechada hoy")
        // «Sale de»: la cuenta del débito automático, diciendo por qué.
        composeRule.onNode(hasText(nuAhorros.name) and hasAnyAncestor(hasTestTag(TAG_FILA_DESDE)), useUnmergedTree = true).assertExists()
        composeRule.onNode(hasText("Débito automático", substring = true) and hasAnyAncestor(hasTestTag(TAG_FILA_DESDE)), useUnmergedTree = true).assertExists()

        composeRule.onNodeWithText("Registrar pago").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { pagos.isNotEmpty() }

        val pago = pagos.single()
        assertEquals(1_204_064L, pago.amount)
        assertEquals(libre.id, pago.debtAccountId)
        assertEquals(nuAhorros.id, pago.fromAccountId)
        assertTrue(traspasos.isEmpty(), "la cuota es el pago de cuota de siempre, no un traspaso")
    }

    /**
     * Vuelta 49 · el crédito de la demo tenía términos pero ningún movimiento: su deuda en Movi es $0,
     * y la hoja decía «Deuda de Vehículo 4083: $0 pasa a −$980.000». Una deuda nunca se muestra
     * negativa: el renglón dice que Movi no tiene el saldo y lleva a cuadrarlo.
     */
    @Test
    fun `un credito sin saldo en Movi no muestra una deuda negativa y ofrece cuadrarlo`() {
        montar("Pagar", credito = libre.copy(balance = 0L))
        elegirLaDeuda(libre.name)
        esperar("\$1.204.064")

        esperar(SIN_EL_SALDO_DEL_CREDITO)
        assertTrue(hay("$SIN_EL_SALDO_DEL_CREDITO · $CUADRAR_EL_CREDITO"))
        assertFalse(hay("pasa a"), "sin la cuenta que daría una deuda negativa")
        assertFalse(hay("−\$"), "ninguna cifra negativa")

        tocar("$SIN_EL_SALDO_DEL_CREDITO · $CUADRAR_EL_CREDITO")
        assertEquals(listOf<com.jvillada.movi.ui.Screen>(com.jvillada.movi.ui.Screen.Credits), navegado)
    }

    @Test
    fun `en Cuota los creditos se eligen para pagar su cuota, no para un desembolso`() {
        montar("Pagar")
        tocarElTag(TAG_FILA_HACIA)

        assertTrue(hay(AYUDA_DE_CREDITOS_EN_CUOTA))
        assertFalse(hay("desembolso"), "el desembolso ya no está en Cuota")
    }

    @Test
    fun `el abono extra es la opcion secundaria y guarda un traspaso`() {
        montar("Pagar")
        elegirLaDeuda(libre.name)
        esperar("\$1.204.064")

        tocarElTag(TAG_ENLACE_DEL_ABONO)
        assertEquals("Abono extra a Libre inversión 9695", encabezado())
        assertTrue(hay(QUE_ES_UN_ABONO_EXTRA))
        assertFalse(hay("\$1.204.064"), "la cuota propuesta se va: un abono es otra cifra")
        assertTrue(hay(ES_LA_CUOTA_DEL_MES), "y se puede volver a la cuota")

        teclear("500000")
        composeRule.onNodeWithText("Registrar abono").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { traspasos.isNotEmpty() }

        val abono = traspasos.single()
        assertEquals(500_000L, abono.amount)
        assertEquals(libre.id, abono.toAccountId)
        assertEquals(nuAhorros.id, abono.fromAccountId)
        assertTrue(pagos.isEmpty(), "un abono extra no es la cuota")
    }

    @Test
    fun `el desembolso sigue en Traspaso, con el credito de Desde`() {
        montar("Traspaso")
        tocarElTag(TAG_FILA_DESDE)
        assertTrue(hay(AYUDA_DE_CREDITOS_EN_TRASPASO), "en Traspaso un crédito sí es un desembolso o un abono")
        tocar(libre.name)
        esperar("Desembolso:")

        teclear("1000000")
        composeRule.onNodeWithText("Guardar traspaso").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { traspasos.isNotEmpty() }
        assertEquals(libre.id, traspasos.single().fromAccountId)
    }

    /** Lo que mide la hoja de Cuota, del encabezado al botón. */
    private fun alto(boton: String = "Registrar pago"): Dp {
        val arriba = composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_LA_CUOTA, useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val abajo = composeRule.onNodeWithText(boton, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom
        return abajo - arriba
    }

    private fun assertQuieta(antes: Dp, despues: Dp, que: String) =
        assertTrue(abs((despues - antes).value) <= 8f, "$que: la hoja se movió ${despues - antes}")

    @Test
    fun `la hoja de Cuota no salta al elegir la tarjeta ni al llegar la cifra`() {
        montar("Pagar")
        val sinElegir = alto()

        elegirLaDeuda(nuTarjeta.name)
        esperar("Deuda de Nu: \$1.683.000")
        val conLaTarjeta = alto()
        assertQuieta(sinElegir, conLaTarjeta, "elegir la tarjeta")

        teclear("100000")
        esperar("pasa a \$1.583.000")
        assertQuieta(sinElegir, alto(), "llegar la cifra")
    }

    @Test
    fun `la hoja de un credito no salta al borrar ni escribir el monto`() {
        montar("Pagar")
        elegirLaDeuda(libre.name)
        esperar("bajan la deuda")
        val conLaCuota = alto()

        borrar(7)
        esperar("Escribe el monto")
        assertQuieta(conLaCuota, alto(), "borrar el monto")

        teclear("1000000")
        esperar("bajan la deuda")
        assertQuieta(conLaCuota, alto(), "escribir otro monto")
    }
}
