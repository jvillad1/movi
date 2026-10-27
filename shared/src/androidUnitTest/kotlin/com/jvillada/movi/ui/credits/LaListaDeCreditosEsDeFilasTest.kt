package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TAG_PILDORA
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Créditos se lee como una lista (vuelta de diseño 4)
 *
 * Cada préstamo y cada tarjeta era una tarjeta con botones adentro («Ajustar saldo», «Editar»), un
 * «0% pagado» y una barra; «Vehículo 4083» decía «Sin términos registrados · $0 · 0% pagado» y la Nu
 * «Sin corte ni pago — edita…», cortado. Ahora cada deuda es una `Fila` que abre su detalle.
 *
 * A 360 dp —el teléfono más angosto— y con el motor de texto de verdad (`sdk = [34]`, `NATIVE`),
 * como `LasPantallasDePlanUsanElSistemaTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class LaListaDeCreditosEsDeFilasTest {

    @get:Rule val composeRule = createComposeRule()

    private val libreInversion = CreditSummary(
        account = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
        ),
        paidPct = 0.5,
    )

    /** El de la demo: sin términos y en \$0. */
    private val vehiculo = CreditSummary(
        account = Account("acc_4083", "Vehículo 4083", AccountType.LOAN, balance = 0L),
        terms = null,
        paidPct = null,
        hasMovements = false,
    )

    /** Llegó a \$0 con movimientos: de verdad pagado. */
    private val crediagil = CreditSummary(
        account = Account("acc_3090", "Crediágil 3090", AccountType.LOAN, balance = 0L),
        terms = CreditTerms(
            accountId = "acc_3090", bank = "Bancolombia", principal = 5_000_000L, rateEa = 29.64,
            termMonths = 24, installment = 26_485L, dayOfMonth = 5, startDate = "2024-01-05",
        ),
        paidPct = 1.0,
    )

    /** La Nu de la demo: sin corte ni pago. */
    private val nu = CardSummary(
        account = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, 1_998_960L, balancesByCurrency = mapOf("COP" to 1_998_960L)),
        terms = null,
    )

    private val master = CardSummary(
        account = Account("acc_3684", "Master Black 3684", AccountType.CREDIT_CARD, 3_450_000L, balancesByCurrency = mapOf("COP" to 3_450_000L)),
        terms = CardTerms(accountId = "acc_3684", bank = "Bancolombia", cutoffDay = 28, paymentDay = 14, pagoMinimo = 412_300L),
        available = 16_550_000L,
    )

    private val destinos = mutableListOf<Screen>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = listOf(libreInversion, vehiculo, crediagil)
            override suspend fun getCards(): List<CardSummary> = listOf(nu, master)
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CreditosScreen(onNavigate = { destinos += it }) } } }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Master Black 3684") }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textosDentro(nodo: SemanticsNode): List<SemanticsNode> =
        listOf(nodo).filter { texto(it).isNotEmpty() } + nodo.children.flatMap(::textosDentro)

    /** El texto se dibuja entero: sin «…» y cada renglón cabe en el ancho del nodo. */
    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val d = resultados.single()
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    private fun tocar(texto: String) {
        composeRule.onNode(hasText(texto) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `la lista no tiene botones, ni Editar ni Ajustar saldo`() {
        montar()

        listOf("Editar", AJUSTAR_EL_SALDO, ACCION_SIMULAR_ABONO, "Registrar descuento").forEach { boton ->
            assertEquals(
                0,
                composeRule.onAllNodes(hasText(boton) and hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
                "«$boton» vive en el detalle de la deuda, no en la lista",
            )
        }
        // Cada deuda es una fila: tres préstamos y dos tarjetas.
        assertEquals(5, nodos(TAG_FILA_DE_DEUDA).size)
    }

    @Test
    fun `una deuda sin terminos dice que faltan datos, sin cortarse y sin cero por ciento`() {
        montar()

        val faltan = composeRule.onAllNodesWithText(FALTAN_DATOS_DE_LA_DEUDA, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(2, faltan.size, "el Vehículo 4083 y la Nu")
        faltan.forEach { assertTrue(seVeEntero(it), "«$FALTAN_DATOS_DE_LA_DEUDA» se corta a 360 dp") }
        assertTrue(!hay("Sin términos registrados"), "la frase de antes")
        assertTrue(!hay("Sin corte ni pago"), "la frase cortada de antes")
        listOf("0% pagado", "0 % pagado").forEach { cero ->
            assertEquals(0, composeRule.onAllNodesWithText(cero, useUnmergedTree = true).fetchSemanticsNodes().size, "«$cero» suelto")
        }
    }

    @Test
    fun `una deuda en cero dice Pagado en una pildora`() {
        montar()

        assertTrue(nodos(TAG_PILDORA).any { p -> textosDentro(p).any { texto(it) == DEUDA_PAGADA } }, "el Crediágil va con «Pagado»")
    }

    @Test
    fun `el subtitulo dice el proximo pago`() {
        montar()

        assertTrue(hay("Cuota \$1.204.064 · 15 de"), "el préstamo dice su cuota y cuándo vence")
        assertTrue(hay("Pago mínimo \$412.300 · 14 de"), "la tarjeta dice su mínimo y cuándo vence")
        assertTrue(hay("50 % pagado"), "la barra del que sí baja, con su avance")
    }

    @Test
    fun `nada se recorta a 360 dp`() {
        montar()

        val densidad = composeRule.density.density
        nodos(TAG_FILA).forEach { fila ->
            assertTrue(fila.size.height / densidad >= Medida.altoDeFila.value - 0.5f, "una fila mide menos que ${Medida.altoDeFila}")
        }
        nodos(TAG_CIFRA).forEach { assertTrue(seVeEntero(it), "«${texto(it)}» se recorta a 360 dp") }
        nodos(TAG_FILA_DE_DEUDA).flatMap(::textosDentro).forEach { nodo ->
            assertTrue(seVeEntero(nodo), "«${texto(nodo)}» se recorta a 360 dp")
        }
    }

    @Test
    fun `tocar una deuda abre su detalle, y una sin terminos los pide`() {
        montar()

        tocar("Libre inversión 9695")
        assertEquals(Screen.AccountDetail("acc_9695", AccountGroup.DEUDA), destinos.single())

        tocar("Vehículo 4083")
        assertTrue(hay("TÉRMINOS"), "sin términos, tocarla abre la hoja de completarlos")
        assertEquals(1, destinos.size, "y no navega")
    }
}
