package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecategorizarEnLoteResponse
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # Arreglar uno pregunta por los parecidos, y solo cambia lo que el dueño dijo.
 *
 * El recorrido entero, en una pantalla de teléfono: elegir una categoría, ver la pregunta con los
 * movimientos que se van a mover, y que lo que llega al repositorio sea exactamente esa lista.
 *
 * Lo que más importa acá es la mitad negativa: **cerrar por «No, solo este» no manda ningún lote**.
 * Son movimientos que el dueño no está mirando, y moverlos sin que los haya confirmado sería
 * cambiarle cifras a sus espaldas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class OfrecerLosParecidosTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private val gasto = FinancialEvent(
        id = "ev-qr",
        accountId = banco.id,
        type = TransactionType.EXPENSE,
        amount = 25_000L,
        category = "Otros",
        description = "Mora Soccer",
        merchant = "Pago QR · llave 0092184713",
        timestamp = 1_757_952_000_000L,
    )

    private fun parecido(id: String, categoria: String = "Otros") =
        gasto.copy(id = id, category = categoria, timestamp = gasto.timestamp - 86_400_000L)

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    /** El repositorio de prueba anota qué lote se pidió, o `null` si no se pidió ninguno. */
    private class RepoConParecidos(private val parecidos: List<FinancialEvent>) : RepositorioDePrueba() {
        var lotePedido: Pair<List<String>, String>? = null
        override suspend fun getParecidos(id: String): List<FinancialEvent> = parecidos
        override suspend fun updateEventCategory(id: String, category: String): FinancialEvent =
            FinancialEvent(
                id = id, accountId = "acc-banco", type = TransactionType.EXPENSE, amount = 25_000L,
                category = category, description = "Mora Soccer", timestamp = 1_757_952_000_000L,
            )
        override suspend fun recategorizarEnLote(ids: List<String>, category: String): RecategorizarEnLoteResponse {
            lotePedido = ids to category
            return RecategorizarEnLoteResponse(cambiados = ids, omitidos = 0)
        }
    }

    private fun montar(onCambiado: (FinancialEvent) -> Unit = {}) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    ChangeCategorySheet(
                        event = gasto,
                        cuentas = listOf(banco),
                        onDismiss = {},
                        onEventChanged = onCambiado,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun elegir(categoria: String) {
        composeRule.onNodeWithText(categoria, useUnmergedTree = true).performScrollTo()
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(categoria)), useUnmergedTree = true)
            .onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(texto)), useUnmergedTree = true)
            .onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun alElegirCategoriaLaHojaPreguntaPorLosParecidos() {
        Repositories.sustitutoDePrueba = RepoConParecidos(listOf(parecido("ev-2"), parecido("ev-3")))
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")

        composeRule.onNodeWithText("Hay 2 movimientos más de «Mora Soccer»", useUnmergedTree = true)
            .assertIsDisplayed()
        assertNull(cambiado, "la hoja no puede cerrarse antes de que él conteste")
    }

    @Test
    fun decirQueSiMandaElLoteConEsosMovimientos() {
        val repo = RepoConParecidos(listOf(parecido("ev-2"), parecido("ev-3")))
        Repositories.sustitutoDePrueba = repo
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")
        tocar("Sí, ponlos en «Comida»")

        assertEquals(listOf("ev-2", "ev-3") to "Comida", repo.lotePedido)
        assertNotNull(cambiado, "después del lote la hoja se cierra con el movimiento ya cambiado")
        assertEquals("Comida", cambiado?.category)
    }

    @Test
    fun decirQueNoNoMandaNingunLote() {
        val repo = RepoConParecidos(listOf(parecido("ev-2")))
        Repositories.sustitutoDePrueba = repo
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")
        tocar("No, solo este")

        assertNull(repo.lotePedido, "«solo este» no puede mover nada más")
        assertNotNull(cambiado, "y el movimiento que él abrió sí queda cambiado")
    }

    /** Sin parecidos no hay pregunta: la hoja hace lo de siempre y se cierra. */
    @Test
    fun sinParecidosLaHojaNoPreguntaNada() {
        Repositories.sustitutoDePrueba = RepoConParecidos(emptyList())
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")

        composeRule.onAllNodesWithText("¿Y LOS PARECIDOS?", useUnmergedTree = true).assertCountEquals(0)
        assertNotNull(cambiado)
    }

    /** Un parecido que YA está en esa categoría no se ofrece: preguntar por nada gasta la pregunta. */
    @Test
    fun loQueYaEstaEnEsaCategoriaNoSeOfrece() {
        Repositories.sustitutoDePrueba = RepoConParecidos(listOf(parecido("ev-2", categoria = "Comida")))
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")

        composeRule.onAllNodesWithText("¿Y LOS PARECIDOS?", useUnmergedTree = true).assertCountEquals(0)
        assertNotNull(cambiado)
    }
}
