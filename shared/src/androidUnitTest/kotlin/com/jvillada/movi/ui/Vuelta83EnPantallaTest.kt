package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.LocalPunteroFino
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TAG_ICONO_DEL_SUBTITULO
import com.jvillada.movi.ui.dashboard.DURACION_DE_LA_ENTRADA_MS
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.plan.ACTIVAR_LOS_RECORDATORIOS
import com.jvillada.movi.ui.plan.AHORA_NO
import com.jvillada.movi.ui.plan.FilaDeRecordatoriosSinCanal
import com.jvillada.movi.ui.plan.TITULO_CUANTO_PUEDES_GASTAR
import com.jvillada.movi.ui.plan.TITULO_RECORDATORIOS_SIN_CANAL
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.quickadd.TAG_CABEZA_DEL_MONTO
import com.jvillada.movi.ui.quickadd.TAG_RECIENTE_PARA_REPETIR
import com.jvillada.movi.ui.quickadd.TAG_REPETIR_UNO_RECIENTE
import com.jvillada.movi.ui.sdui.TAG_CATEGORIA_DEL_WIDGET
import com.jvillada.movi.ui.sdui.TAG_PERIODO_GASTADO_DE_LA_META
import com.jvillada.movi.ui.sdui.TAG_VENTANA_EN_RENGLON
import com.jvillada.movi.ui.sdui.TarjetaDelDisponible
import com.jvillada.movi.ui.sdui.WidgetsDelInicio
import com.jvillada.movi.ui.transactions.MovementRow
import com.jvillada.movi.ui.transactions.MovementSingleRow
import com.jvillada.movi.ui.transactions.TransferRow
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 83 · filas densas y limpias, Agregar útil y Pagos sin repetir (en pantalla)
 *
 * Con el motor de texto de verdad (`sdk = [34]`, `NATIVE`) y medido con `boundsInRoot`:
 *
 * - **Agregar**: la cuenta en su nombre corto, entera, a 390 y a 1.280; «Repetir uno reciente» llena el
 *   formulario sin guardar.
 * - **Pagos**: «Período» dice lo gastado de su meta, en el mismo formato que el resto de la tarjeta, y
 *   los recordatorios son una propuesta de un renglón con «Activar» y «Ahora no» lado a lado.
 * - **«En qué se va»**: cada categoría mide 56 dp o menos.
 * - **Actividad**: las filas miden lo mismo a 360 con la letra al 130 %, el título en un renglón y el ↻
 *   en el subtítulo.
 *
 * El amarillo de Pagos lo cuenta `UnSoloAmarilloPorVistaTest`; la ficha y el panel de Actividad,
 * `MovimientosListaYDetalleEnPantallaTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class Vuelta83EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun hay(texto: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun disposicion(n: SemanticsNode): TextLayoutResult {
        val r = mutableListOf<TextLayoutResult>()
        n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single()
    }

    private fun entero(texto: String) {
        val n = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().first()
        val d = disposicion(n)
        assertEquals(1, d.lineCount, "«$texto» en un renglón")
        assertFalse(d.isLineEllipsized(0), "«$texto» entero, sin «…»")
    }

    // ── (1) y (3) Agregar ────────────────────────────────────────────────────

    private val ahorros = Account("ahorros", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 10_581_200)
    private val nu = Account("nu", "Nu", AccountType.SAVINGS, 2_890_000)

    private fun gasto(id: String, cuenta: String, descripcion: String, categoria: String, monto: Long, ts: Long) = FinancialEvent(
        id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = monto, category = categoria,
        description = descripcion, timestamp = ts, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private val historia = listOf(
        EventDay(
            "2026-10-28", -63_300,
            listOf(
                gasto("u", "nu", "Uber Rides", "Transporte", 8_400, 3_000),
                gasto("d1", "ahorros", "Tienda D1 Laureles", "Mercado", 41_000, 2_000),
                gasto("jv", "ahorros", "Juan Valdez Cafe", "Comida", 13_900, 1_000),
            ),
        ),
    )

    private fun montarAgregar(ancho: WindowWidthClass) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, nu)
            override suspend fun getEventsByDay(): List<EventDay> = historia
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalPunteroFino provides true, LocalWindowWidthClass provides ancho) {
                MoviTheme {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) { QuickAddScreen(onDismiss = {}) }
                        Spacer(Modifier.height(64.dp))
                    }
                }
            }
        }
        composeRule.waitUntil(5_000) { hay("Bancolombia ·4057") }
    }

    @Test
    fun `a 390 Agregar dice la cuenta entera en su nombre corto`() {
        montarAgregar(WindowWidthClass.Compact)
        entero("Bancolombia ·4057")
        assertFalse(hay("Bancolombia Ahorros", substring = true), "el nombre largo ya no va en la fila")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `a 1280 el dialogo de Agregar dice la cuenta entera`() {
        montarAgregar(WindowWidthClass.Expanded)
        entero("Bancolombia ·4057")
    }

    @Test
    fun `Repetir uno reciente ofrece tres y al tocar uno llena el formulario sin guardar`() {
        montarAgregar(WindowWidthClass.Compact)
        composeRule.waitUntil(5_000) { conTag(TAG_RECIENTE_PARA_REPETIR).size == 3 }
        assertTrue(conTag(TAG_REPETIR_UNO_RECIENTE).isNotEmpty())
        // El primero es el más nuevo: Uber, en Nu.
        composeRule.onNodeWithText("Uber Rides · \$8.400").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        // El monto, la cuenta, la categoría y el concepto: listos para «Guardar».
        val cabeza = composeRule.onNodeWithTag(TAG_CABEZA_DEL_MONTO, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(composeRule.onAllNodes(hasText("\$8.400", substring = true), useUnmergedTree = true).fetchSemanticsNodes().any { n ->
            generateSequence(n.parent) { it.parent }.any { it.id == cabeza.id } || n.id == cabeza.id
        }, "el monto")
        assertTrue(hay("Nu"), "la cuenta")
        assertTrue(hay("Uber Rides"), "el concepto en la nota")
        assertTrue(hay("Transporte"), "la categoría")
        assertFalse(hay("Falta el monto"), "Guardar ya se puede tocar")
    }

    // ── (4) Pagos ────────────────────────────────────────────────────────────

    private val hoy = LocalDate(2026, 9, 21)

    private val datosDePagos = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 2_345_678)),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 3_100_000, dayOfMonth = 5, type = TransactionType.EXPENSE),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true)),
        gastoVariablePorDia = mapOf("2026-09-26" to 900_000L, "2026-10-02" to 700_000L),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    @Test
    fun `en Pagos el Periodo dice lo gastado de su meta, entero como el resto de la tarjeta`() {
        val disponible = disponibleDelInicio(datosDePagos, hoy)!!
        composeRule.setContent { MoviTheme { TarjetaDelDisponible(titulo = TITULO_CUANTO_PUEDES_GASTAR, disponible = disponible) } }
        composeRule.mainClock.advanceTimeBy(DURACION_DE_LA_ENTRADA_MS.toLong() + 200L)
        composeRule.waitForIdle()
        assertEquals(3, conTag(TAG_VENTANA_EN_RENGLON).size)
        val periodo = composeRule.onNodeWithTag(TAG_PERIODO_GASTADO_DE_LA_META, useUnmergedTree = true).fetchSemanticsNode()
        val textos = descendientesConTexto(periodo)
        val gastado = com.jvillada.movi.ui.components.formatMoney(disponible.periodo.gastado, "COP")
        val meta = com.jvillada.movi.ui.components.formatMoney(disponible.periodo.meta, "COP")
        assertTrue(textos.any { it.endsWith(gastado.removePrefix("$")) }, "lo gastado: $textos")
        assertTrue(textos.any { it.endsWith(meta.removePrefix("$")) }, "de la meta: $textos")
        // Un solo formato en las cifras de la tarjeta: ninguna compacta («$1,5M») en las ventanas.
        val compactas = conTag(TAG_VENTANA_EN_RENGLON).flatMap(::descendientesConTexto)
            .filter { Regex("""\$\d+,\dM""").containsMatchIn(it) }
        assertTrue(compactas.isEmpty(), "sin cifras compactas: $compactas")
    }

    private fun descendientesConTexto(n: SemanticsNode): List<String> =
        (listOf(n) + descendientes(n)).flatMap { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { t -> t.text } }

    private fun descendientes(n: SemanticsNode): List<SemanticsNode> = n.children + n.children.flatMap(::descendientes)

    private fun androidx.compose.ui.semantics.SemanticsConfiguration.getOrNull(
        key: androidx.compose.ui.semantics.SemanticsPropertyKey<List<androidx.compose.ui.text.AnnotatedString>>,
    ) = if (contains(key)) get(key) else null

    @Test
    fun `los recordatorios son una propuesta de un renglon con Activar y Ahora no lado a lado`() {
        var activadas = 0
        var ahoraNo = 0
        composeRule.setContent {
            MoviTheme { FilaDeRecordatoriosSinCanal(bloqueadas = false, onActivar = { activadas++ }, onAhoraNo = { ahoraNo++ }) }
        }
        composeRule.waitForIdle()
        entero(TITULO_RECORDATORIOS_SIN_CANAL)
        val activar = composeRule.onNodeWithText(ACTIVAR_LOS_RECORDATORIOS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val luego = composeRule.onNodeWithText(AHORA_NO, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(activar.center.y, luego.center.y, 1f, "«Ahora no» en el renglón de «Activar», no en un tercero")
        composeRule.onNodeWithText(ACTIVAR_LOS_RECORDATORIOS).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText(AHORA_NO).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, activadas)
        assertEquals(1, ahoraNo)
    }

    // ── (6) «En qué se va», más denso ────────────────────────────────────────

    @Test
    fun `En que se va lleva filas de 56 dp o menos con la barra debajo del nombre`() {
        val data = DashboardData(
            spentByCategory = mapOf("Otros" to 1_800_000L, "Cuota de crédito" to 980_000L, "Mercado" to 499_050L),
            gastoDelAnterior = mapOf("Otros" to 1_000_000L, "Mercado" to 184_300L),
            diasComparados = 12,
        )
        composeRule.setContent { MoviTheme { WidgetsDelInicio(tipos = listOf("DONDE_SE_VA"), data = data, onNavigate = {}) } }
        composeRule.waitForIdle()
        val categorias = conTag(TAG_CATEGORIA_DEL_WIDGET)
        assertEquals(3, categorias.size)
        categorias.forEach { c ->
            val alto = c.boundsInRoot.height / composeRule.density.density
            assertTrue(alto <= 56f + 0.5f, "cada categoría mide $alto dp (eran ~72)")
        }
        // La píldora va en el renglón del monto.
        val mercado = composeRule.onNodeWithText("\$499.050", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pildora = composeRule.onNodeWithText("\$314.750 más", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(mercado.center.y, pildora.center.y, 2f * composeRule.density.density, "la píldora junto al monto")
    }

    // ── (7) Filas de Actividad de una altura ─────────────────────────────────

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 con la letra al 130 las filas de Actividad miden lo mismo`() {
        val nombres = mapOf(ahorros.id to ahorros.name, nu.id to nu.name, "vehiculo" to "Vehículo 4083")
        val tipos = mapOf(ahorros.id to AccountType.SAVINGS, nu.id to AccountType.SAVINGS, "vehiculo" to AccountType.LOAN)
        val cuotaSale = gasto("c1", ahorros.id, "Cuota de Vehículo 4083", "Cuota de crédito", 980_000, 5_000).copy(transferId = "t1")
        val cuotaEntra = cuotaSale.copy(id = "c2", accountId = "vehiculo", type = TransactionType.INCOME, transferId = "t1")
        val par = MovementRow.Transfer(out = cuotaSale, into = cuotaEntra)
        val uber = gasto("u", ahorros.id, "Uber Rides", "Transporte", 8_400, 4_000)
        val d1 = gasto("d", nu.id, "Tienda D1 Laureles con un nombre muy largo", "Mercado", 41_000, 3_000)
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.3f)) {
                    Superficie(relleno = RellenoDeSuperficie.Lista) {
                        TransferRow(row = par, accountNames = nombres, accountTypes = tipos, esRecurrente = true, onClick = {})
                        Separador()
                        MovementSingleRow(tx = uber, accountNames = nombres, cuentaHabitual = ahorros.id, onClick = {})
                        Separador()
                        MovementSingleRow(tx = d1, accountNames = nombres, cuentaHabitual = ahorros.id, esRecurrente = true, onClick = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        // Cada fila es lo que se toca (su nodo fusionado).
        val filas = composeRule.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot.height }
        assertEquals(3, filas.size)
        assertTrue(filas.all { kotlin.math.abs(it - filas.first()) < 1f }, "todas del mismo alto: $filas")
        // El título en un renglón con «…», y el ↻ en el subtítulo.
        val cuota = composeRule.onAllNodesWithText("Cuota de Vehículo 4083", useUnmergedTree = true).fetchSemanticsNodes().first()
        assertEquals(1, disposicion(cuota).lineCount)
        assertEquals(2, conTag(TAG_ICONO_DEL_SUBTITULO).size, "el ↻ de los dos recurrentes, en el subtítulo")
        // La cuenta habitual no se nombra; Nu sí, y el traspaso con los nombres cortos.
        assertTrue(hay("Transporte"))
        assertTrue(hay("Mercado · Nu"))
        assertTrue(hay("De Bancolombia ·4057 a Vehículo ·4083", substring = true))
    }
}
