package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.CreditSummary
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Traspaso y Cuota con el lenguaje de Gasto (vuelta 47)
 *
 * La revisión de la 46 (r46-10): «NOTA (OPCIONAL)» en mayúsculas, el monto en un campo chico, la
 * fecha en una caja «Hoy», «Bancolombia Ahorros …» cortado y dos líneas de ayuda. Ahora: la cifra
 * protagonista con el teclado de Gasto, la píldora de la fecha, «Desde» y «Hacia» como filas con el
 * nombre entero, la fila «Nota» y una sola línea de ayuda; caben a 360 y guardan lo mismo que antes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class TraspasoYCuotaComoGastoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros 4057 Nómina", AccountType.SAVINGS, 13_845_800)
    private val nu = Account("n1", "Nu Ahorros", AccountType.SAVINGS, 2_300_000)
    private val libre = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 40_710_555)

    private val traspasos = mutableListOf<CreateTransferRequest>()
    private val pagos = mutableListOf<CreatePagoDeCuotaRequest>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun pata(id: String, cuenta: String, tipo: TransactionType, monto: Long) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, category = "Traspaso", description = "", timestamp = 0L,
    )

    private fun montar(pestana: String) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, nu, libre)
            override suspend fun getCredits(): List<CreditSummary> = listOf(CreditSummary(account = libre, terms = null, paidPct = null))
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
                    Box(Modifier.fillMaxWidth().weight(1f)) { QuickAddScreen(onDismiss = {}) }
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

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocarElTag(tag: String) {
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun teclear(digitos: String) = digitos.forEach { tocar(it.toString()) }

    @Test
    fun `Traspaso tiene la cifra, el teclado, la fecha en pildora, las filas y una sola ayuda`() {
        montar("Traspaso")

        assertTrue(hay("\$0"), "la cifra protagonista, en cero")
        composeRule.onNodeWithTag(TAG_FECHA_DEL_TRASPASO, useUnmergedTree = true).assertExists()
        assertTrue(!hay("NOTA (OPCIONAL)") && !hay("Desde:"), "sin el rótulo que grita ni «Desde:» pegado")
        // La fila «Desde»: el nombre entero de título y el rótulo debajo.
        composeRule.onNode(hasText(ahorros.name) and hasAnyAncestor(hasTestTag(TAG_FILA_DESDE)), useUnmergedTree = true).assertExists()
        composeRule.onNode(hasText("Desde", substring = true) and hasAnyAncestor(hasTestTag(TAG_FILA_DESDE)), useUnmergedTree = true).assertExists()
        assertTrue(hay("Agregar nota…"), "la nota como en Gasto")
        // Una sola línea de ayuda.
        composeRule.onNodeWithTag(TAG_AYUDA_DEL_TRASPASO, useUnmergedTree = true).assertExists()
        assertTrue(!hay("las dos puntas se registran juntas o no se registra ninguna"), "la segunda línea se fue")
    }

    private fun cabeA360(pestana: String, boton: String) {
        montar(pestana)
        val pantalla = composeRule.onRoot().getUnclippedBoundsInRoot()
        val tecla = composeRule.onNodeWithText("000", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(tecla.bottom <= pantalla.bottom, "$pestana: el teclado entra")
        // La hoja se desplaza de arriba abajo; lo que importa es que nada se salga de lado.
        val elBoton = composeRule.onNodeWithText(boton, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(elBoton.right <= pantalla.right, "$pestana: el botón no se sale de lado")
        val fila = composeRule.onNodeWithTag(TAG_FILA_DESDE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(fila.right <= pantalla.right, "$pestana: la fila entra a lo ancho")
        // El nombre entero de la cuenta, en la fila (dos renglones si hace falta), no «… …».
        composeRule.onNode(hasText(ahorros.name) and hasAnyAncestor(hasTestTag(TAG_FILA_DESDE)), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `Traspaso cabe a 360`() = cabeA360("Traspaso", "Guardar traspaso")

    @Test
    fun `Cuota cabe a 360`() = cabeA360("Pagar", "Registrar pago")

    @Test
    fun `el traspaso guarda lo mismo que antes`() {
        montar("Traspaso")
        teclear("150000")
        assertTrue(hay("\$150.000"))
        composeRule.onNodeWithText("Guardar traspaso").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { traspasos.isNotEmpty() }

        val pedido = traspasos.single()
        assertEquals(150_000L, pedido.amount)
        assertTrue(pedido.fromAccountId != pedido.toAccountId)
        assertTrue(pedido.fromAccountId in setOf(ahorros.id, nu.id) && pedido.toAccountId in setOf(ahorros.id, nu.id))
        assertNull(pedido.note)
    }

    @Test
    fun `la cuota guarda lo mismo que antes`() {
        montar("Pagar")
        tocarElTag(TAG_FILA_HACIA)
        tocar(libre.name)
        teclear("1204064")
        composeRule.onNodeWithText("Registrar pago").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { pagos.isNotEmpty() }

        val pedido = pagos.single()
        assertEquals(1_204_064L, pedido.amount)
        assertEquals(libre.id, pedido.debtAccountId)
        assertTrue(pedido.fromAccountId in setOf(ahorros.id, nu.id))
        assertNull(pedido.interesReal, "sin tocar el interés, que lo estime el server")
    }
}
