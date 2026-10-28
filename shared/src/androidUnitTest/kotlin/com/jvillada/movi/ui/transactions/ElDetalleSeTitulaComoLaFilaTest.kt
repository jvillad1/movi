package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 36 · el detalle se titula como su fila
 *
 * En Movimientos la fila decía «Éxito Suba» («Mercado · Bancolombia Ahorros» debajo) y, al tocarla,
 * la hoja de detalle se titulaba «Mercado»: la fila sabía (vuelta 30) que una descripción igual a la
 * categoría no dice nada, y el detalle no. Ahora los dos salen de [textosDeLaFila]. Al editar la
 * nota, el campo sigue trayendo lo guardado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1600dp-xhdpi")
class ElDetalleSeTitulaComoLaFilaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc-ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000L, "COP")

    private val mercado = FinancialEvent(
        id = "ev-mercado",
        accountId = ahorros.id,
        type = TransactionType.EXPENSE,
        amount = 182_400L,
        category = "Mercado",
        description = "Mercado",
        merchant = "EXITO SUBA",
        timestamp = 1_787_677_200_000L,
    )

    @After
    fun despues() {
        UsedCategoriesCache.clear()
    }

    private fun montar(evento: FinancialEvent) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(event = evento, cuentas = listOf(ahorros), onDismiss = {}, onCambiado = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `la fila y el detalle salen de la misma funcion`() {
        val fila = textosDeLaFila(mercado, ahorros.name)
        assertEquals("Éxito Suba", fila.titulo)
        assertEquals("Mercado · Bancolombia Ahorros", fila.subtitulo)
        assertEquals(fila.titulo, tituloDelMovimiento(mercado, ahorros.name))
    }

    @Test
    fun `Mercado con el comercio EXITO SUBA titula su detalle Exito Suba`() {
        montar(mercado)
        assertEquals(1, cuantos("Éxito Suba"), "el detalle se titula como su fila")
        // «Mercado» queda solo en el chip de la categoría, no como título.
        assertEquals(1, cuantos("Mercado"), "la categoría va una vez: en su chip")
    }

    @Test
    fun `al editar la nota el campo trae lo guardado`() {
        montar(mercado)
        composeRule.onNodeWithTag(tagDeAccionDelMovimiento(AccionDelMovimiento.Nota), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val campos = composeRule.onAllNodes(hasSetTextAction() and hasText("Mercado"), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(1, campos.size, "el concepto se edita sobre lo guardado («Mercado»), no sobre el título")
        assertTrue(
            composeRule.onAllNodes(hasSetTextAction() and hasText("Éxito Suba"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "el título no se cuela al campo",
        )
    }

    @Test
    fun `una descripcion propia sigue siendo el titulo`() {
        montar(mercado.copy(description = "Mercado de la semana"))
        assertEquals(1, cuantos("Mercado de la semana"))
        assertEquals(0, cuantos("Éxito Suba"))
    }
}
