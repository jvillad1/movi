package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Presupuestos no afirma nada mientras carga (ola B)
 *
 * Los presupuestos llegaban antes que el gasto, y en ese medio la pantalla decía «$0» gastado y
 * cada categoría «$0 … 0 % … $1.000.000 disponibles»; al llegar el gasto aparecía «2
 * Sobrepasados», las categorías se reordenaban y las filas crecían. Acá los presupuestos y los
 * movimientos contestan enseguida y [puertaDelGasto] deja colgado el gasto del server: exactamente
 * ese medio.
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]`: ver `CreditosNoAfirmanMientrasCarganTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class PresupuestosNoAfirmanMientrasCarganTest {

    @get:Rule val composeRule = createComposeRule()

    private val puertaDelGasto = CompletableDeferred<DashboardSummary>()

    /** Llegan en este orden y se tienen que pintar en el del porcentaje: Comida primero. */
    private val presupuestos = listOf(
        Budget("Mercado", 1_000_000L),
        Budget("Hija", 1_000_000L),
        Budget("Comida", 1_000_000L),
    )

    /** Comida sobrepasada, Hija justo en el límite, Mercado holgado: la fila de avisos aparece. */
    private val gastoDelServer = DashboardSummary(
        scope = Scope.SELF,
        spentByCategory = mapOf("Comida" to 1_200_000L, "Hija" to 1_000_000L, "Mercado" to 300_000L),
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(budgets: List<Budget> = presupuestos) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getBudgets(): List<Budget> = budgets
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = puertaDelGasto.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PresupuestosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun contarTag(tag: String): Int = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun `con los presupuestos pero sin el gasto no dice cero ni porcentajes, y muestra los esqueletos`() {
        montar()

        assertTrue(!hay("\$0"), "sin el gasto no puede decir \$0")
        assertTrue(!hay("0%"))
        assertTrue(!hay("disponibles"))
        assertTrue(!hay("Mercado"), "la lista espera al gasto: si se pinta antes, se reordena al llegar")
        assertTrue(!hay("Gastado en"))
        assertEquals(1, contarTag(TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO))
        assertEquals(4, contarTag(TAG_ESQUELETO_FILA_DE_PRESUPUESTO))
        assertTrue(hay("Nuevo"), "el alta del encabezado está desde el primer cuadro")
    }

    @Test
    fun `al llegar el gasto se pinta ya ordenado, sin saltar la tarjeta de arriba`() {
        montar()
        val altoCargando = composeRule.onNodeWithTag(TAG_TARJETA_DEL_GASTO_DEL_PERIODO).getUnclippedBoundsInRoot().height
        val tituloCargando = composeRule.onNodeWithText("Presupuestos", useUnmergedTree = true).getUnclippedBoundsInRoot()

        puertaDelGasto.complete(gastoDelServer)
        composeRule.waitForIdle()

        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO))
        assertEquals(0, contarTag(TAG_ESQUELETO_FILA_DE_PRESUPUESTO))
        assertTrue(hay("Sobrepasados"))
        val arribaDe = { texto: String ->
            composeRule.onNodeWithText(texto, useUnmergedTree = true).getUnclippedBoundsInRoot().top
        }
        assertTrue(arribaDe("Comida") < arribaDe("Hija") && arribaDe("Hija") < arribaDe("Mercado"))

        val altoCargado = composeRule.onNodeWithTag(TAG_TARJETA_DEL_GASTO_DEL_PERIODO).getUnclippedBoundsInRoot().height
        val diferencia = abs(altoCargado.value - altoCargando.value)
        assertTrue(
            diferencia <= 8f,
            "La tarjeta de «Gastado en …» mide ${altoCargando.value} dp cargando y ${altoCargado.value} dp " +
                "cargada — diferencia de $diferencia dp, el máximo son 8 dp",
        )
        assertEquals(tituloCargando, composeRule.onNodeWithText("Presupuestos", useUnmergedTree = true).getUnclippedBoundsInRoot())
    }

    @Test
    fun `sin presupuestos de verdad, el vacio de siempre`() {
        montar(budgets = emptyList())

        puertaDelGasto.complete(gastoDelServer)
        composeRule.waitForIdle()

        assertTrue(hay("Nuevo presupuesto"))
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO))
        assertEquals(0, contarTag(TAG_ESQUELETO_FILA_DE_PRESUPUESTO))
    }
}
