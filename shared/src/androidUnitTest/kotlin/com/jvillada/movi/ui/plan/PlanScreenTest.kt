package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import com.jvillada.movi.ui.dashboard.instantaneaEnMemoria
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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
import com.jvillada.movi.ui.budgets.TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO
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

    /** Lo que el tablero enumera; vacío salvo en la prueba del checklist. */
    private var pagos: List<UpcomingPayment> = emptyList()
    private var ocurrencias: List<OccurrenceState> = emptyList()
    private val sellos = mutableListOf<Triple<String, String, String?>>()

    /** Solo las suscripciones fallan: el aviso del tablero, sin tocar la tarjeta. */
    private var suscripcionesFallan = false

    /** Sin red: todo lo que la tarjeta y el tablero leen falla. */
    private var sinRed = false

    private fun cortar() {
        if (sinRed) error("sin red")
    }

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary {
            lecturasDelResumen++
            cortar()
            puerta.await()
            return FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 0)
        }

        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary {
            cortar()
            puerta.await()
            return DashboardSummary(spentByCategory = mapOf("Comida" to 300_000L), gastoVariablePorDia = emptyMap())
        }

        override suspend fun getUserProfile(): UserProfile {
            cortar()
            puerta.await()
            return UserProfile(id = "u", email = "u@local", name = "U", avatarColor = "#000000", periodCutoffDay = 25)
        }

        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = pagos.also { cortar() }
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = ocurrencias.also { cortar() }
        override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence {
            sellos += Triple(ruleId, period, eventId)
            ocurrencias = ocurrencias.map {
                if (it.ruleId == ruleId) it.copy(occurred = true, eventId = eventId, candidates = emptyList()) else it
            }
            return RecurringOccurrence(ruleId = ruleId, period = period, eventId = eventId)
        }
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult {
            if (suscripcionesFallan) error("sin señal")
            return SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        }
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
        InstantaneaDelInicio.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    /** Lo último que el Inicio guardó en el aparato: $5M disponibles en el período de hoy. */
    private fun conInstantaneaDelInicio() {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        InstantaneaDelInicio.delAparato.guardarDatos("u1", datosDelInicio())
    }

    private fun datosDelInicio(): DashboardData {
        val ahora = Clock.System.now().toEpochMilliseconds()
        return DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 0),
            upcoming = emptyList(),
            ocurrencias = emptyList(),
            gastoVariablePorDia = emptyMap(),
            ajustesDePeriodo = ajustes,
            periodoActual = periodoActual(ahora, ajustes),
        )
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
        // Ola D, Task 3: sin reglas, sin suscripciones y sin candidatas, el tablero ya no pinta el
        // checklist vacío ni «Flujo libre» en $0 — un solo vacío que enseña los reemplaza.
        assertTrue(hay("Aquí van tus pagos fijos"))
        assertTrue(!hay(tituloDelChecklist))
        assertTrue(!hay("Gastado en", substring = true), "Presupuestos no se pinta con Pagos elegido")
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_TABLERO))
    }

    /**
     * **El tablero por el camino de Plan**, no por el envoltorio de las pruebas del tablero: con el
     * corte del dueño leído del perfil, los vencimientos pedidos aunque el segmento cambie
     * (`vencimientosSiempre`) y el esqueleto hasta que las dos cosas llegan. Un pago de hoy con un
     * movimiento candidato; «Sí, fue este» lo sella contra ese movimiento y la fila pasa a pagada.
     */
    @Test
    fun `en Pagos del mes un pago pendiente se confirma con su movimiento`() {
        val hoy = Clock.System.todayIn(TimeZone.of("America/Bogota"))
        val arriendo = RecurringRule(
            id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
            amount = 1_800_000L, dayOfMonth = hoy.dayOfMonth, type = TransactionType.EXPENSE,
        )
        val movimiento = FinancialEvent(
            id = "ev_arriendo", accountId = "acc-banco", type = TransactionType.EXPENSE, amount = 1_800_000L,
            category = "Vivienda", description = "Arriendo", source = EventSource.MANUAL,
            timestamp = Clock.System.now().toEpochMilliseconds(),
        )
        pagos = listOf(UpcomingPayment(rule = arriendo, dueDate = hoy.toString(), daysUntil = 0, status = PaymentStatus.DUE_TODAY))
        ocurrencias = listOf(
            OccurrenceState(
                ruleId = "rr_arriendo", period = hoy.toString().take(7), dueDate = hoy.toString(),
                occurred = false, candidates = listOf(movimiento),
            ),
        )
        puerta.complete(Unit)
        montar()

        composeRule.waitUntil(timeoutMillis = 5_000) { hay("¿Ya pagaste", substring = true) }
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_TABLERO))
        assertTrue(composeRule.onAllNodesWithContentDescription("Pagado", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText("Sí, fue este")), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(timeoutMillis = 5_000) { sellos.isNotEmpty() }
        assertEquals<List<Triple<String, String, String?>>>(listOf(Triple("rr_arriendo", hoy.toString().take(7), "ev_arriendo")), sellos)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Pagado", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(!hay("¿Ya pagaste", substring = true), "la pregunta se fue: ya está sellado")
    }

    /**
     * El «Reintentar» del aviso del tablero recarga también la tarjeta de arriba: los dos leen del
     * mismo período, y reintentar uno solo dejaba la tarjeta con lo de antes.
     */
    @Test
    fun `el Reintentar del aviso del tablero recarga tambien la tarjeta`() {
        suscripcionesFallan = true
        puerta.complete(Unit)
        montar()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Reintentar") }
        val lecturasAntes = lecturasDelResumen

        suscripcionesFallan = false
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { lecturasDelResumen > lecturasAntes }
        composeRule.waitForIdle()
        assertTrue(!hay("Reintentar"))
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

        assertTrue(hay("Aquí van tus pagos fijos"))
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
        assertTrue(hay("Aquí van tus pagos fijos"))
        assertTrue(!hay("Gastado en", substring = true))
    }

    @Test
    fun `con el Inicio fresco en memoria la tarjeta sale al primer cuadro, sin pedir nada`() {
        val ahora = Clock.System.now().toEpochMilliseconds()
        DashboardDataCache.data = datosDelInicio()
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
        // Ola D, Task 3: sin nada anotado, el esqueleto del tablero da paso al vacío que enseña,
        // no al checklist vacío de antes.
        assertTrue(hay("Aquí van tus pagos fijos"))

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

    /**
     * Fix round 1: cambiar de segmento cancela la carga de Presupuestos a medio camino. La lectura
     * cancelada no puede darse por contestada: si lo hacía, al volver se pintaba el gasto del mes de
     * calendario (sin el del server ni el período del dueño) y la lista se reordenaba al llegar.
     */
    @Test
    fun `ir y volver de Presupuestos con la carga a medias no pinta cifras hasta que contesta`() {
        montar(segmento = SEGMENTO_PRESUPUESTOS)
        assertEquals(1, contarTag(TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO))

        composeRule.onNodeWithText("Pagos del mes", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Presupuestos", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(!hay("Gastado en", substring = true), "la carga cancelada no cuenta como contestada")
        assertTrue(!hay("Comida"))
        assertEquals(1, contarTag(TAG_ESQUELETO_DEL_GASTO_DEL_PERIODO))

        puerta.complete(Unit)
        composeRule.waitForIdle()
        assertTrue(hay("Gastado en", substring = true))
        assertTrue(hay("Comida"))
    }

    /**
     * «¿De dónde sale?» no hace nada mientras la tarjeta es esqueleto: si el toque quedara
     * guardado, la tarjeta llegaría ya abierta, más alta que su esqueleto, y empujaría todo.
     */
    @Test
    fun `De donde sale no se abre mientras la tarjeta carga`() {
        montar()
        assertEquals(1, contarTag(TAG_ESQUELETO_DEL_DISPONIBLE))

        composeRule.onNodeWithText("¿De dónde sale?", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(!hay("Ocultar"))

        puerta.complete(Unit)
        composeRule.waitForIdle()
        assertTrue(hay("\$5M"))
        assertTrue(hay("¿De dónde sale?"), "llega cerrada")
        assertTrue(!hay("Ocultar"))
    }

    /** Fix round 1: lo de antes, mientras se actualiza, no se hace pasar por lo de ahora. */
    @Test
    fun `con la instantanea del Inicio pinta la cifra y dice Actualizando mientras recarga`() {
        conInstantaneaDelInicio()
        montar()

        assertTrue(hay("\$5M"), "lo último que se supo, en el primer cuadro")
        assertTrue(hay("Actualizando…"), "con la recarga en vuelo, la cabecera lo dice")

        puerta.complete(Unit)
        composeRule.waitForIdle()
        assertTrue(hay("\$5M"))
        assertTrue(!hay("Actualizando…"))
    }

    /** Fix round 1: si la recarga no contesta, las cifras de antes no quedan como si fueran de hoy. */
    @Test
    fun `con la instantanea y sin red, cambia la tarjeta por el reintento`() {
        sinRed = true
        conInstantaneaDelInicio()
        montar()

        assertTrue(hay("No pudimos calcular cuánto puedes gastar"))
        assertTrue(!hay("\$5M"), "las cifras de la instantánea no se pueden dar por actuales")
        assertTrue(!hay("Actualizando…"))
    }

    @Test
    fun `el segmento elegido sobrevive a ir a otra pantalla y volver`() {
        puerta.complete(Unit)
        var enPlan by mutableStateOf(true)
        composeRule.setContent {
            MoviTheme {
                // Lo mismo que hace App.kt con cada pantalla de la pila.
                val guardado = rememberSaveableStateHolder()
                Box(Modifier.fillMaxSize()) {
                    if (enPlan) {
                        guardado.SaveableStateProvider("plan") { PlanScreen(onNavigate = {}) }
                    } else {
                        Text("Otra pantalla")
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Presupuestos", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        enPlan = false
        composeRule.waitForIdle()
        assertTrue(hay("Otra pantalla"))
        enPlan = true
        composeRule.waitForIdle()

        assertTrue(hay("Gastado en", substring = true), "vuelve a Presupuestos, no al segmento de entrada")
        assertTrue(!hay(tituloDelChecklist))
    }
}
