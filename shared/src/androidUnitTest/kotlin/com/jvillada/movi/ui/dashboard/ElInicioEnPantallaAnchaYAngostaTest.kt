package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.EvidenciaDeAnomalia
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_ACCION_RAPIDA
import com.jvillada.movi.ui.components.ds.partePalabras
import com.jvillada.movi.ui.sdui.ALTO_DE_LA_BANDA_EN_EL_SCROLL
import com.jvillada.movi.ui.sdui.AnchoDeLaBandaKey
import com.jvillada.movi.ui.sdui.TAG_BANDA_DEL_INICIO
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_DOS_COLUMNAS_DEL_INICIO
import com.jvillada.movi.ui.sdui.TAG_NOMBRE_DEL_PROXIMO_PAGO
import com.jvillada.movi.ui.sdui.TAG_SUPERFICIE_DE_LAS_ACCIONES
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # El Inicio de la opción 3 a 360, 390 y 1.280 (la revisión de la vuelta 39)
 *
 * - Ningún círculo amarillo de la cuadrícula queda sobre la banda amarilla, en ningún ancho: la banda
 *   llega hasta media tarjeta héroe y las acciones van sobre el crema. A 1.280 la banda va a lo ancho,
 *   sin muesca, y las acciones van en su superficie blanca, montada sobre ella como el héroe (vuelta 81).
 * - A 1.280 las dos columnas quedan parejas: la más corta mide al menos el 70 % de la más larga.
 * - A 360, el nombre del próximo pago no se parte («Arrien / do»).
 *
 * Motor de texto real (`NATIVE`, sdk 34) y la letra de la app (×1,12).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ElInicioEnPantallaAnchaYAngostaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        rule = RecurringRule(id = id, name = nombre, category = "Vivienda", amount = monto, dayOfMonth = 10, type = TransactionType.EXPENSE),
        dueDate = "2026-09-10",
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    private fun gasto(id: String, descripcion: String, monto: Long) = FinancialEvent(
        id = id, accountId = "a1", type = TransactionType.EXPENSE, amount = monto, category = "Mercado",
        description = descripcion, timestamp = 0L, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    /** Un Inicio de todos los días: cuentas, pagos, una anomalía, movimientos y gasto por categoría. */
    private val datos = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(
            Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800),
            Account("a2", "Nu", AccountType.SAVINGS, 2_890_000),
            Account("a3", "Efectivo", AccountType.CASH, 120_000),
        ),
        credits = emptyList<CreditSummary>(),
        cards = emptyList<CardSummary>(),
        upcoming = listOf(pago("r_arr", "Arriendo", 1_850_000, 2), pago("r_cel", "Celular", 53_000, 5)),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(2026, 9),
        ajustesDePeriodo = PeriodSettings(),
        pendingSms = 2,
        spentByCategory = mapOf("Mercado" to 392_100L, "Restaurantes" to 236_000L),
        anomalias = listOf(
            Anomalia(
                huella = "dup:a,b", tipo = TipoDeAnomalia.COBRO_DUPLICADO, titulo = "¿Cobro duplicado en RAPPI?",
                detalle = "2 cobros de \$45.900", evidencia = listOf(EvidenciaDeAnomalia("a", "2026-09-28", "RAPPI", 45_900)),
            ),
        ),
        ultimosMovimientos = listOf(gasto("e1", "Panadería", 27_500), gasto("e2", "Ferretería", 64_300), gasto("e3", "Taller", 18_700)),
    )

    private fun montar() {
        composeRule.setContent {
            // Vuelta 81: con la clase de ancho de verdad (a 1.280, la cuadrícula va en su superficie).
            com.jvillada.movi.ConClaseDeAncho {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = datos,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = {},
                        conBanda = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** El ancho de la banda, en px, como lo declara su semántica (vuelta 41). */
    private fun anchoDeLaBandaEnPx(): Float {
        val banda = composeRule.onNodeWithTag(TAG_BANDA_DEL_INICIO).fetchSemanticsNode()
        return with(composeRule.density) { banda.config[AnchoDeLaBandaKey].toPx() }
    }

    /**
     * Ningún círculo de la cuadrícula —el amarillo de «Anotar gasto» incluido— queda dentro de la banda:
     * o empieza debajo de ella, o a la derecha de donde termina (vuelta 41: en dos columnas la banda va
     * solo detrás del héroe).
     */
    private fun ningunaAccionSobreLaBanda() {
        val acciones = composeRule.onAllNodesWithTag(TAG_ACCION_RAPIDA).fetchSemanticsNodes()
        assertTrue(acciones.isNotEmpty())
        val finDeLaBanda = with(composeRule.density) { ALTO_DE_LA_BANDA_EN_EL_SCROLL.toPx() }
        val bordeDeLaBanda = anchoDeLaBandaEnPx()
        acciones.forEach { accion ->
            assertTrue(
                accion.boundsInRoot.top >= finDeLaBanda || accion.boundsInRoot.left >= bordeDeLaBanda,
                "una acción en (${accion.boundsInRoot.left}, ${accion.boundsInRoot.top}) px queda dentro de la " +
                    "banda (que acaba en $finDeLaBanda px de alto y $bordeDeLaBanda px de ancho)",
            )
        }
    }

    /**
     * Vuelta 81: a 1.280 la banda es **una franja limpia a lo ancho** —sin la muesca de la vuelta 41, que
     * bajaba solo por la izquierda mientras el encabezado seguía a lo ancho— y las dos columnas arrancan a
     * la misma altura, montadas sobre ella: el héroe a la izquierda y la cuadrícula, en su superficie
     * blanca, a la derecha. Ningún amarillo vacío encima de las acciones.
     */
    @Test
    @Config(qualifiers = "w1280dp-h3000dp-mdpi")
    fun `a 1280 la banda es una franja a lo ancho sin muesca`() {
        montar()
        val columnas = composeRule.onNodeWithTag(TAG_DOS_COLUMNAS_DEL_INICIO).fetchSemanticsNode()
        val ancho = composeRule.onNodeWithTag(TAG_BANDA_DEL_INICIO).fetchSemanticsNode().boundsInRoot.width
        assertTrue(anchoDeLaBandaEnPx() >= ancho - 1f, "la banda (${anchoDeLaBandaEnPx()} px) no llega al borde ($ancho px): muesca")
        val mitad = columnas.boundsInRoot.left + columnas.boundsInRoot.width / 2
        val hijos = columnas.children.map { it.boundsInRoot }
        val arribaIzquierda = hijos.filter { it.left < mitad }.minOf { it.top }
        val arribaDerecha = hijos.filter { it.left >= mitad }.minOf { it.top }
        assertTrue(kotlin.math.abs(arribaIzquierda - arribaDerecha) <= 1f, "las columnas arrancan a $arribaIzquierda y $arribaDerecha px")
        val acciones = composeRule.onAllNodesWithTag(TAG_ACCION_RAPIDA).fetchSemanticsNodes()
        assertTrue(acciones.all { it.boundsInRoot.left >= mitad }, "las acciones van en la columna derecha")
        val finDeLaBanda = with(composeRule.density) { ALTO_DE_LA_BANDA_EN_EL_SCROLL.toPx() }
        val superficie = composeRule.onNodeWithTag(TAG_SUPERFICIE_DE_LAS_ACCIONES, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(superficie.top < finDeLaBanda, "la superficie de las acciones se monta sobre la banda, como el héroe: ${superficie.top}")
    }

    /** En una columna la banda sigue a todo el ancho. */
    @Test
    @Config(qualifiers = "w390dp-h3000dp-xhdpi")
    fun `a 390 la banda va a todo el ancho`() {
        montar()
        val ancho = composeRule.onNodeWithTag(TAG_BANDA_DEL_INICIO).fetchSemanticsNode().boundsInRoot.width
        assertTrue(anchoDeLaBandaEnPx() >= ancho - 1f, "la banda (${anchoDeLaBandaEnPx()} px) no llega al borde ($ancho px)")
    }

    /**
     * Vuelta 81: a 1.280 ninguna acción queda **sobre** la banda: todas van dentro de su superficie blanca
     * (el círculo amarillo de «Gasto» nunca sobre el amarillo).
     */
    @Test
    @Config(qualifiers = "w1280dp-h3000dp-mdpi")
    fun `a 1280 ninguna accion queda sobre la banda`() {
        montar()
        assertTrue(composeRule.onAllNodesWithTag(TAG_DOS_COLUMNAS_DEL_INICIO).fetchSemanticsNodes().isNotEmpty(), "dos columnas")
        val superficie = composeRule.onNodeWithTag(TAG_SUPERFICIE_DE_LAS_ACCIONES, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.onAllNodesWithTag(TAG_ACCION_RAPIDA).fetchSemanticsNodes().forEach { accion ->
            val b = accion.boundsInRoot
            assertTrue(
                b.left >= superficie.left - 0.5f && b.right <= superficie.right + 0.5f &&
                    b.top >= superficie.top - 0.5f && b.bottom <= superficie.bottom + 0.5f,
                "una acción en $b sale de su superficie ($superficie)",
            )
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h3000dp-xhdpi")
    fun `a 390 ninguna accion queda sobre la banda`() {
        montar()
        ningunaAccionSobreLaBanda()
    }

    @Test
    @Config(qualifiers = "w360dp-h3000dp-xhdpi")
    fun `a 360 ninguna accion queda sobre la banda`() {
        montar()
        ningunaAccionSobreLaBanda()
    }

    /** A 1.280, la columna más corta mide al menos el 70 % de la más larga. */
    @Test
    @Config(qualifiers = "w1280dp-h3000dp-mdpi")
    fun `a 1280 las dos columnas quedan parejas`() {
        montar()
        val columnas = composeRule.onNodeWithTag(TAG_DOS_COLUMNAS_DEL_INICIO).fetchSemanticsNode()
        val mitad = columnas.boundsInRoot.left + columnas.boundsInRoot.width / 2
        // El fondo de cada columna: lo más bajo de sus hijos directos.
        val hijos = columnas.children.map { it.boundsInRoot }
        val izquierda = hijos.filter { it.left < mitad }.maxOf { it.bottom } - columnas.boundsInRoot.top
        val derecha = hijos.filter { it.left >= mitad }.maxOf { it.bottom } - columnas.boundsInRoot.top
        assertTrue(
            minOf(izquierda, derecha) >= 0.7f * maxOf(izquierda, derecha),
            "izquierda $izquierda px, derecha $derecha px: disparejas",
        )
    }

    /** A 360 el nombre del próximo pago va entero, en un renglón, sin partirse. */
    @Test
    @Config(qualifiers = "w360dp-h3000dp-xhdpi")
    fun `a 360 el nombre del proximo pago no se parte`() {
        montar()
        val nodo = composeRule.onNodeWithTag(TAG_NOMBRE_DEL_PROXIMO_PAGO, useUnmergedTree = true).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val d = resultados.single()
        assertTrue(d.lineCount == 1, "«Arriendo» en ${d.lineCount} renglones")
        assertFalse(d.isLineEllipsized(0), "«Arriendo» se corta con «…»")
        assertFalse(partePalabras(d, "Arriendo"))
    }
}
