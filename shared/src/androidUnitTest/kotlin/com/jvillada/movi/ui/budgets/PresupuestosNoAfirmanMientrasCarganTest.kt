package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.height
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.components.TAG_CAMPO_DE_CATEGORIA
import com.jvillada.movi.ui.components.tagDeCeldaDeCategoria
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

    /** El «se guardó algo» de la hoja de Agregar: subirlo recarga la pantalla sin sacarla de la composición. */
    private val tick = mutableIntStateOf(0)

    private val caida = ApiException(503)

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(budgets: List<Budget> = presupuestos) = montarCon(object : RepositorioDePrueba() {
        override suspend fun getBudgets(): List<Budget> = budgets
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = puertaDelGasto.await()
    })

    private fun montarCon(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalRefreshTick provides tick.intValue) {
                    Box(Modifier.fillMaxSize()) { PresupuestosScreen(onNavigate = {}) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun sinEsqueletos() {
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO))
        assertEquals(0, contarTag(TAG_ESQUELETO_FILA_DE_PRESUPUESTO))
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

    /** Ola D, Task 3: el vacío que enseña, con el mismo `abrirNuevo()` que «Nuevo» del encabezado. */
    @Test
    fun `sin presupuestos de verdad, el vacio que ensena`() {
        montar(budgets = emptyList())

        puertaDelGasto.complete(gastoDelServer)
        composeRule.waitForIdle()

        assertTrue(hay("Ponle un tope a lo que más gastas"))
        assertTrue(hay("Nuevo presupuesto"))
        // La tarjeta de siempre decía «$0 de $0»: un hecho inventado sobre categorías que no existen.
        assertTrue(!hay("Gastado en"))
        assertTrue(!hay("\$0"))
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO))
        assertEquals(0, contarTag(TAG_ESQUELETO_FILA_DE_PRESUPUESTO))

        composeRule.onNodeWithText("Nuevo presupuesto", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        // La misma hoja que «Nuevo» del encabezado: el título de la hoja de alta.
        assertTrue(hay("Guardar"))
    }

    @Test
    fun `si los presupuestos no se pueden leer, el esqueleto se va y queda el error de siempre`() {
        montarCon(object : RepositorioDePrueba() {
            override suspend fun getBudgets(): List<Budget> = throw caida
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        })

        assertTrue(hay("No pudimos cargar tus presupuestos"))
        sinEsqueletos()
    }

    /** Presupuestos sí, pero ni el gasto del server ni los movimientos: no hay gasto que decir. */
    @Test
    fun `sin ninguna lectura del gasto no inventa un cero, dice que no pudo leer`() {
        montarCon(object : RepositorioDePrueba() {
            override suspend fun getBudgets(): List<Budget> = presupuestos
            override suspend fun getEventsByDay(): List<EventDay> = throw caida
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = throw caida
        })

        assertTrue(hay("No pudimos cargar tus presupuestos"))
        assertTrue(!hay("\$0"))
        assertTrue(!hay("Mercado"))
        sinEsqueletos()
    }

    /** El vacío no necesita el gasto: sin presupuestos no hay categoría a la que ponerle una cifra. */
    @Test
    fun `sin presupuestos y sin gasto, el vacio que ensena y no el error`() {
        montarCon(object : RepositorioDePrueba() {
            override suspend fun getBudgets(): List<Budget> = emptyList()
            override suspend fun getEventsByDay(): List<EventDay> = throw caida
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = throw caida
        })

        assertTrue(hay("Ponle un tope a lo que más gastas"))
        assertTrue(hay("Nuevo presupuesto"))
        assertTrue(!hay("No pudimos cargar"))
        assertTrue(!hay("Gastado en"))
        sinEsqueletos()
    }

    @Test
    fun `una recarga con los datos ya pintados no vuelve al esqueleto`() {
        val recarga = CompletableDeferred<List<Budget>>()
        var lecturas = 0
        montarCon(object : RepositorioDePrueba() {
            override suspend fun getBudgets(): List<Budget> = if (lecturas++ == 0) presupuestos else recarga.await()
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = gastoDelServer
        })
        assertTrue(hay("Mercado"))

        tick.intValue++
        composeRule.waitForIdle()

        assertEquals(2, lecturas, "la recarga tiene que estar en vuelo")
        sinEsqueletos()
        assertTrue(hay("Mercado"))
    }

    /**
     * «Nuevo» se puede tocar desde el primer cuadro. Con el gasto todavía en camino, la hoja no puede
     * decir «Todavía no tienes gastos» ni «No tienes gastos en …»: sería sobre un cero inventado.
     */
    @Test
    fun `la hoja de crear abierta sin el gasto no dice que no hay gastos, y lo dice bien cuando llega`() {
        montar()
        composeRule.onAllNodesWithText("Nuevo", useUnmergedTree = true).onFirst().performClick()
        composeRule.waitForIdle()
        // Ola B: la categoría se elige en la cuadrícula — abrir el campo y tocar la celda.
        composeRule.onNodeWithTag(TAG_CAMPO_DE_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Comida")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(!hay("No tienes gastos"))
        assertTrue(!hay("Todavía no tienes gastos"))
        assertTrue(!hay("que no aparecen en esta lista"))

        puertaDelGasto.complete(gastoDelServer)
        composeRule.waitForIdle()

        assertTrue(hay("Ya llevas \$1.200.000 gastados en \"Comida\""))
    }
}
