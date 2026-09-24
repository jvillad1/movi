package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.budgets.TAG_TARJETA_DEL_GASTO_DEL_PERIODO
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.recurrentes.TITULO_CHECKLIST_DEL_PERIODO
import com.jvillada.movi.ui.sdui.TAG_ESQUELETO_DEL_DISPONIBLE
import com.jvillada.movi.ui.sdui.TAG_TARJETA_DEL_DISPONIBLE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
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
 * # La pestaña Plan (ola C)
 *
 * Lo que la pantalla agrega a piezas que ya tienen sus propias pruebas (la tarjeta del disponible en
 * `DisponibleEnInicioTest`, el tablero en `TableroDeRecurrentesSoloTest`, Presupuestos en
 * `PresupuestosNoAfirmanMientrasCarganTest`): que los dos segmentos pintan lo suyo, que el
 * parámetro elige con cuál se entra, y que la tarjeta de arriba sale del Inicio si el Inicio ya la
 * tiene — y si no, la carga con esqueleto, sin inventar cifras y sin que lo de arriba salte.
 *
 * Todo lo que depende del período se arma con el reloj de verdad (corte el 25, el período en curso
 * sea cual sea), y el checklist va vacío: qué filas enumera ya lo prueban las pruebas del tablero.
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]`: el alto de un texto se mide con el motor de texto real
 * (ver `CreditosNoAfirmanMientrasCarganTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class PlanScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val ajustes = PeriodSettings(cutoffDay = 25)

    /** Lo que viaja para la tarjeta del disponible; cerrada, deja la carga a medio camino. */
    private val puerta = CompletableDeferred<Unit>()
    private var lecturasDelResumen = 0

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary {
            lecturasDelResumen++
            puerta.await()
            return FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 0)
        }

        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary {
            puerta.await()
            return DashboardSummary(spentByCategory = mapOf("Comida" to 300_000L), gastoVariablePorDia = emptyMap())
        }

        override suspend fun getUserProfile(): UserProfile {
            puerta.await()
            return UserProfile(id = "u", email = "u@local", name = "U", avatarColor = "#000000", periodCutoffDay = 25)
        }

        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getBudgets(): List<Budget> = listOf(Budget("Comida", 1_000_000L))
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun montar(segmento: Int = SEGMENTO_PAGOS) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { PlanScreen(onNavigate = {}, segmento = segmento) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun contarTag(tag: String): Int = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private val tituloDelChecklist = TITULO_CHECKLIST_DEL_PERIODO.uppercase()

    @Test
    fun `por defecto entra a Pagos del mes y pinta el tablero`() {
        puerta.complete(Unit)
        montar()

        assertTrue(hay("Plan"))
        assertTrue(hay(tituloDelChecklist), "el checklist del período es lo primero de Pagos del mes")
        assertTrue(hay("Este período no tiene pagos anotados"))
        assertTrue(!hay("Gastado en", substring = true), "Presupuestos no se pinta con Pagos elegido")
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_TABLERO))
    }

    @Test
    fun `el segmento inicial respeta el parametro`() {
        puerta.complete(Unit)
        montar(segmento = SEGMENTO_PRESUPUESTOS)

        assertTrue(hay("Gastado en", substring = true))
        assertEquals(1, contarTag(TAG_TARJETA_DEL_GASTO_DEL_PERIODO))
        assertTrue(hay("Nuevo"), "con Presupuestos elegido, el encabezado ofrece crear uno")
        assertTrue(!hay(tituloDelChecklist))
    }

    @Test
    fun `un segmento que no existe cae en Pagos del mes`() {
        puerta.complete(Unit)
        montar(segmento = 7)

        assertTrue(hay(tituloDelChecklist))
    }

    @Test
    fun `cambiar de segmento pinta el contenido de cada uno`() {
        puerta.complete(Unit)
        montar()
        assertTrue(!hay("Nuevo"), "«Nuevo» es de Presupuestos: con Pagos a la vista no se ofrece")

        composeRule.onNodeWithText("Presupuestos", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(hay("Gastado en", substring = true))
        assertTrue(hay("Comida"))
        assertTrue(hay("Nuevo"))
        assertTrue(!hay(tituloDelChecklist))

        composeRule.onNodeWithText("Pagos del mes", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(hay(tituloDelChecklist))
        assertTrue(!hay("Gastado en", substring = true))
    }

    @Test
    fun `con el Inicio fresco en memoria la tarjeta sale al primer cuadro, sin pedir nada`() {
        val ahora = Clock.System.now().toEpochMilliseconds()
        DashboardDataCache.data = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 0),
            upcoming = emptyList(),
            ocurrencias = emptyList(),
            gastoVariablePorDia = emptyMap(),
            ajustesDePeriodo = ajustes,
            periodoActual = periodoActual(ahora, ajustes),
        )
        DashboardDataCache.cargadoEn = ahora
        DashboardDataCache.tickDeLaCarga = 0
        // La puerta queda cerrada: si Plan pidiera algo para la tarjeta, se quedaría esperando.
        montar()

        assertTrue(hay("\$5M"), "el disponible del Inicio, sin esperar a nadie")
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_DISPONIBLE))
        assertEquals(0, lecturasDelResumen, "con el Inicio fresco no hay nada que pedir")
        assertEquals(1, contarTag(TAG_LINEA_DEL_PERIODO_DE_PLAN), "el período también viene del Inicio")
    }

    @Test
    fun `sin nada del Inicio carga con esqueleto, no inventa cifras y lo de arriba no salta`() {
        montar()

        // Cargando: la forma, sin una sola cifra.
        assertEquals(1, contarTag(TAG_ESQUELETO_DEL_DISPONIBLE))
        assertEquals(1, contarTag(TAG_LINEA_DEL_PERIODO_DE_PLAN_ESQUELETO), "sin nada recordado, la línea del período se reserva")
        assertTrue(!hay("\$", substring = true), "ninguna cifra antes de leerla")
        assertTrue(contarTag(TAG_ESQUELETO_DEL_TABLERO) > 0, "sin el corte del dueño, el checklist espera con su forma")
        assertTrue(!hay(tituloDelChecklist))
        assertEquals(1, lecturasDelResumen)
        val altoCargando = composeRule.onNodeWithTag(TAG_TARJETA_DEL_DISPONIBLE).getUnclippedBoundsInRoot().height
        val selectorCargando = composeRule.onNodeWithText("Pagos del mes", useUnmergedTree = true).getUnclippedBoundsInRoot().top

        puerta.complete(Unit)
        composeRule.waitForIdle()

        assertTrue(hay("\$5M"))
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_DISPONIBLE))
        assertEquals(0, contarTag(TAG_LINEA_DEL_PERIODO_DE_PLAN_ESQUELETO))
        assertEquals(1, contarTag(TAG_LINEA_DEL_PERIODO_DE_PLAN))
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_TABLERO))
        assertTrue(hay(tituloDelChecklist))

        val altoCargado = composeRule.onNodeWithTag(TAG_TARJETA_DEL_DISPONIBLE).getUnclippedBoundsInRoot().height
        val diferencia = abs(altoCargado.value - altoCargando.value)
        assertTrue(
            diferencia <= 8f,
            "La tarjeta del disponible mide ${altoCargando.value} dp cargando y ${altoCargado.value} dp cargada — " +
                "diferencia de $diferencia dp, el máximo son 8 dp",
        )
        val selectorCargado = composeRule.onNodeWithText("Pagos del mes", useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val corrimiento = abs(selectorCargado.value - selectorCargando.value)
        assertTrue(
            corrimiento <= 8f,
            "El selector de segmentos se corrió $corrimiento dp al llegar los datos (encabezado + tarjeta); el máximo son 8 dp",
        )
    }
}
