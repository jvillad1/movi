package com.jvillada.movi.ui.analisis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RangoDelAnalisis
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LocalAtajosDeAgregar
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «En qué se va» con más que el período, en pantalla (vuelta 140, issue #696)
 *
 * El caso de Caro, medido en producción el 6-nov: corte 30 (día hábil anterior), 6 gastos del período
 * en curso (Hipoteca, Hija, Tirze, Salud), la historia de julio a septiembre que entró con sus
 * extractos, y **octubre vacío**. Antes de esta vuelta la pantalla solo mostraba las 4 categorías, sin
 * decir que había más: no existían el selector, la línea «Ver 3 meses» ni la del hueco (estas pruebas
 * no encuentran sus nodos y fallan).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class EnQueSeVaMasQueElPeriodoTest {

    @get:Rule val composeRule = createComposeRule()

    private val perfil = UserProfile(
        id = "caro", email = "caro@b.c", name = "Caro", avatarColor = "#FF0000",
        periodCutoffDay = 30, periodWeekendRule = "DIA_HABIL_ANTERIOR",
    )
    private val hoy = LocalDate(2026, 11, 6)
    private val cuentas = listOf(Account("ahorros", "Nu Ahorros", AccountType.SAVINGS, 5_000_000L))

    private var n = 0
    private fun mov(dia: LocalDate, monto: Long, categoria: String) = FinancialEvent(
        id = "e${n++}", accountId = "ahorros", type = TransactionType.EXPENSE, amount = monto, category = categoria,
        description = categoria,
        timestamp = dia.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private val deLaHistoria = listOf("Comida", "Transporte", "Impuestos", "Home", "Mercado", "Créditos")

    /** Julio a septiembre, cada 3 días (del 1-jul al 26-sep): las seis categorías también desde el 28-ago. */
    private val historia = (0 until 30).map { i ->
        mov(LocalDate.fromEpochDays(LocalDate(2026, 7, 1).toEpochDays() + i * 3), 50_000L + i * 1_000L, deLaHistoria[i % 6])
    }
    private val delPeriodo = listOf(
        mov(LocalDate(2026, 11, 1), 4_000_000L, "Hipoteca"),
        mov(LocalDate(2026, 11, 2), 300_000L, "Hija"),
        mov(LocalDate(2026, 11, 3), 200_000L, "Tirze"),
        mov(LocalDate(2026, 11, 4), 90_000L, "Salud"),
        mov(LocalDate(2026, 11, 5), 50_000L, "Hija"),
        mov(LocalDate(2026, 11, 6), 20_000L, "Salud"),
    )

    private val navegado = mutableListOf<Screen>()
    private val atajos = mutableListOf<AtajoDeAgregar>()

    private fun montar(resumen: DashboardSummary? = null) {
        val eventos = historia + delPeriodo
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = resumen ?: error("sin resumen")
            override suspend fun getUserProfile(): UserProfile = perfil
            override suspend fun getAccounts(): List<Account> = cuentas
            override suspend fun getEventsByDay(): List<EventDay> =
                eventos.groupBy { it.timestamp }.map { (_, items) -> EventDay(date = "x", total = 0L, items = items) }
        }
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalAtajosDeAgregar provides { atajos += it }) {
                    Box(Modifier.fillMaxSize()) {
                        EnQueSeVaScreen(onNavigate = { navegado += it }, hoy = hoy)
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { filas().isNotEmpty() }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RangoDeEnQueSeVa.olvidar()
    }

    private fun filas() = composeRule.onAllNodesWithTag(TAG_FILA_DEL_ANALISIS, useUnmergedTree = true).fetchSemanticsNodes()

    private fun nombresDeLasFilas(): List<String> =
        composeRule.onAllNodesWithTag(TAG_FILA_DEL_ANALISIS).fetchSemanticsNodes().map { nodo ->
            val textos = nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            textos[textos.indexOfFirst { " % · " in it } - 1]
        }

    private fun hay(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textoDe(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    /** Toca el nodo tocable que dice [texto] (exacto: «3 meses» no es «Ver 3 meses ›»). */
    private fun tocar(texto: String, substring: Boolean = false) {
        composeRule.onNode(hasClickAction() and hasText(texto, substring = substring))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** El borde de arriba de [tag], en dp. */
    private fun arribaDe(tag: String): Float {
        val nodo = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return nodo.boundsInRoot.top / composeRule.density.density
    }

    @Test
    fun `con 6 gastos y la historia de julio a septiembre la pantalla ofrece Ver 3 meses`() {
        montar()
        assertEquals(listOf("Hipoteca", "Hija", "Tirze", "Salud"), nombresDeLasFilas(), "el período en curso, como lo vio Caro")
        assertTrue(hay(TAG_VA_EMPEZANDO))
        assertTrue(
            composeRule.onAllNodesWithText(
                "Este período va empezando: llevas 6 movimientos. Tienes historia desde julio.",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        // En el período no hay hueco que decir.
        assertTrue(!hay(TAG_HUECO_EN_LA_HISTORIA))
        // Revisión de la vuelta 140: octubre (el período pasado) está vacío. Antes decía «$4,7M más que a
        // esta altura del período pasado ($0)» con flechas de subida en todo.
        assertTrue(composeRule.onAllNodesWithText("El período pasado no tiene movimientos: no hay con qué comparar.", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Sin flechas: el período pasado (30 sep – 29 oct) no tiene movimientos.", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("más que a esta altura", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        tocar("Ver 3 meses", substring = true)
        assertEquals(RangoDelAnalisis.TRES_MESES, RangoDeEnQueSeVa.elegido)
        assertTrue(
            composeRule.onAllNodesWithText("28 ago – 29 nov", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "el encabezado dice el rango",
        )
        val nombres = nombresDeLasFilas()
        assertTrue(nombres.containsAll(deLaHistoria), "las categorías de la historia que cae en los 3 meses: $nombres")
        assertTrue(nombres.containsAll(listOf("Hipoteca", "Hija", "Tirze", "Salud")))
        assertTrue(!hay(TAG_VA_EMPEZANDO), "en un rango ya no va")
    }

    @Test
    fun `en 3 meses aparece octubre vacio y Subir extracto abre la subida de siempre`() {
        montar()
        tocar("3 meses")
        assertTrue(hay(TAG_HUECO_EN_LA_HISTORIA))
        assertTrue(
            composeRule.onAllNodesWithText(
                "No hay movimientos del 30 sep al 29 oct. Si tienes el extracto, súbelo y aparecen aquí.",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        tocar("Subir extracto", substring = true)
        assertEquals(listOf(AtajoDeAgregar.SUBIR_COMPROBANTE), atajos)
        // Los 3 meses anteriores (desde el 29-may) no están cubiertos: sin flechas, y el pie lo dice.
        assertTrue(
            composeRule.onAllNodesWithText("Sin flechas", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `tocar una fila de un rango abre Movimientos con la categoria y los dias del rango`() {
        montar()
        tocar("3 meses")
        composeRule.onAllNodesWithTag(TAG_FILA_DEL_ANALISIS, useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val destino = navegado.last() as Screen.Transactions
        val filtro = destino.filtro!!
        assertEquals("Hipoteca", filtro.categoria)
        assertEquals("2026-08-28", filtro.desde)
        assertEquals("2026-11-29", filtro.hasta)
        assertEquals("Hipoteca · 28 ago – 29 nov", filtro.rotulo, "el chip de Movimientos dice el rango")
    }

    @Test
    fun `cambiar el rango no corre el selector ni el grafico, sin desglose`() {
        montar()
        assertNadaSalta()
    }

    @Test
    fun `cambiar el rango no corre el grafico tampoco con el desglose del periodo`() {
        // Con el gasto del día a día del período, «Este período» tiene el renglón del desglose; en un
        // rango, ese renglón dice cuántos gastos junta: el gráfico se queda donde estaba.
        montar(DashboardSummary(scope = Scope.SELF, gastoVariablePorDia = mapOf("2026-11-02" to 300_000L, "2026-11-04" to 90_000L)))
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(TAG_DESGLOSE_DEL_ANALISIS) }
        assertNadaSalta()
        assertTrue(textoDe(TAG_DESGLOSE_DEL_ANALISIS).startsWith("Día a día"), "en el período, el desglose de siempre")
        tocar("3 meses")
        // Del 28-ago: 10 gastos de la historia (las seis categorías) y los 6 del período (4 categorías).
        assertEquals("16 gastos en 10 categorías", textoDe(TAG_DESGLOSE_DEL_ANALISIS), "en el rango, el renglón dice cuántos gastos")
    }

    /**
     * Revisión de la vuelta 140: mirando un período pasado (sin desglose, que es solo del en curso), elegir
     * «3 meses» hacía aparecer el renglón del rango —decidido con el período en curso— y el gráfico bajaba
     * unos 20 dp.
     */
    @Test
    fun `desde un periodo pasado cambiar el rango tampoco corre el grafico`() {
        montar(DashboardSummary(scope = Scope.SELF, gastoVariablePorDia = mapOf("2026-11-02" to 300_000L, "2026-11-04" to 90_000L)))
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(TAG_DESGLOSE_DEL_ANALISIS) }
        composeRule.onNode(hasClickAction() and hasContentDescription("Período anterior"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(!hay(TAG_DESGLOSE_DEL_ANALISIS), "un período pasado no tiene desglose")
        assertNadaSalta()
    }

    /** Revisión de la vuelta 140: en un rango las flechas no solo se ven grises, se anuncian apagadas. */
    @Test
    fun `en un rango las flechas se anuncian apagadas y dicen por que`() {
        montar()
        tocar("3 meses")
        listOf("Período anterior: $NO_DISPONIBLE_EN_UN_RANGO", "Período siguiente: $NO_DISPONIBLE_EN_UN_RANGO").forEach { d ->
            val nodo = composeRule.onNode(hasContentDescription(d)).fetchSemanticsNode()
            assertTrue(nodo.config.contains(SemanticsProperties.Disabled), "$d: se anuncia desactivada")
        }
    }

    private fun assertNadaSalta() {
        val selector = arribaDe(TAG_SELECTOR_DEL_RANGO)
        val grafico = arribaDe(TAG_GRAFICA_DEL_ANALISIS)
        for (rango in listOf("3 meses", "12 meses", "Este período")) {
            tocar(rango)
            assertTrue(abs(arribaDe(TAG_SELECTOR_DEL_RANGO) - selector) <= 8f, "el selector saltó con $rango")
            assertTrue(abs(arribaDe(TAG_GRAFICA_DEL_ANALISIS) - grafico) <= 8f, "el gráfico saltó con $rango: ${arribaDe(TAG_GRAFICA_DEL_ANALISIS)} vs $grafico")
        }
    }
}
