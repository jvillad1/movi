package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PAGO_FIJO_LISTO
import com.jvillada.movi.shared.model.PagoFijoDelPeriodo
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.NO_PUDIMOS_CARGAR
import com.jvillada.movi.ui.components.TAG_AVISO_DE_LA_PANTALLA
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import com.jvillada.movi.ui.dashboard.instantaneaEnMemoria
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 70 · «¿Me alcanza hasta que me paguen?» en Pagos
 *
 * Lo que la revisión 4 encontró en Pagos y esta vuelta cambió, montado de verdad:
 *
 * - ‹ › mueven el período de «Facturas»: el anterior (leído como en «Tus períodos») y el siguiente,
 *   de solo lectura; más allá no se va.
 * - Con 7 días o menos del período, «Lo que viene · próximos 15 días» va debajo de la lista, con su
 *   total, sin tocar «Falta por pagar».
 * - La caja no se pinta con su fuente caída: el aviso único lo dice (`r4-err-pagos.png`).
 *
 * Las tres columnas del Disponible las prueban `DisponibleEnInicioTest` y
 * `LasCifrasDelDisponibleEntranTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h2400dp-xhdpi")
class PagosHastaQueTePaguenTest {

    @get:Rule val composeRule = createComposeRule()

    private val delDueno = PeriodSettings(cutoffDay = 25)
    private val hoy: LocalDate = Clock.System.todayIn(TimeZone.of("America/Bogota"))
    private val periodoDeHoy: PeriodoFinanciero = periodoActual(Clock.System.now().toEpochMilliseconds(), delDueno)

    private fun regla(id: String, nombre: String, monto: Long, dia: Int, tipo: TransactionType = TransactionType.EXPENSE) =
        RecurringRule(id = id, name = nombre, category = "Varios", amount = monto, dayOfMonth = dia, type = tipo)

    private fun vence(regla: RecurringRule, fecha: LocalDate, desde: LocalDate = hoy) =
        UpcomingPayment(regla, fecha.toString(), desde.daysUntil(fecha), PaymentStatus.UPCOMING)

    private var upcoming: List<UpcomingPayment> = emptyList()
    private var vencimientosFallan = false
    private val detallesLeidos = mutableListOf<String>()

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 0)
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
            DashboardSummary(spentByCategory = emptyMap(), gastoVariablePorDia = emptyMap())
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "u@local", name = "U", avatarColor = "#000000", periodCutoffDay = 25)
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> =
            if (vencimientosFallan) error("sin red") else upcoming
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getGastoDelDiaADia(): GastoDelDiaADia = GastoDelDiaADia(porDia = 10_000L)
        override suspend fun getDetalleDePeriodo(id: String): DetalleDePeriodo {
            detallesLeidos += id
            return DetalleDePeriodo(
                resumen = ResumenDePeriodo(id = id, nombre = id, desde = "", hasta = ""),
                pagosFijos = listOf(
                    PagoFijoDelPeriodo(
                        ruleId = "rr_luz", nombre = "Luz de antes", monto = 120_000L, esIngreso = false,
                        vencimiento = "2026-01-10", estado = PAGO_FIJO_LISTO,
                    ),
                ),
            )
        }
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
        DashboardDataCache.data = null
        RecurringOfferGate.clear()
    }

    private fun hay(texto: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun contarTag(tag: String): Int = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    /** El botón entero (el árbol fusionado): el ícono lleva la descripción y el botón, si está apagado. */
    private fun flecha(descripcion: String) =
        composeRule.onAllNodesWithContentDescription(descripcion).onFirst()

    private fun habilitada(descripcion: String): Boolean =
        composeRule.onAllNodesWithContentDescription(descripcion).fetchSemanticsNodes()
            .any { it.config.getOrNull(SemanticsProperties.Disabled) == null }

    private fun montarPagos() {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PlanScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()
    }

    // ── ‹ › ───────────────────────────────────────────────────────────────

    @Test
    fun `las flechas de Pagos van al periodo anterior y al siguiente, y no mas alla`() {
        // Lo que vence en el período siguiente (a 3 días de su arranque): Pagos de hoy no lo lista.
        val arranque = inicioDelPeriodo(periodoSiguiente(periodoDeHoy), delDueno)
        upcoming = listOf(vence(regla("rr_arriendo", "Arriendo de noviembre", 1_800_000L, arranque.plus(3, DateTimeUnit.DAY).dayOfMonth), arranque.plus(3, DateTimeUnit.DAY)))
        montarPagos()
        composeRule.waitUntil(timeoutMillis = 5_000) { habilitada("Período anterior") }
        assertTrue(!hay("Arriendo de noviembre"), "el período de hoy no lo lista")

        // ‹ : el anterior, cerrado, leído como en «Tus períodos».
        flecha("Período anterior").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Luz de antes") }
        assertEquals(listOf(periodoAnterior(periodoDeHoy).prefijo), detallesLeidos)
        assertEquals(1, contarTag(TAG_PAGOS_DE_OTRO_PERIODO))

        // › › : el siguiente, de solo lectura, con lo que vencerá y su total.
        flecha("Período siguiente").performClick()
        composeRule.waitForIdle()
        assertEquals(0, contarTag(TAG_PAGOS_DE_OTRO_PERIODO), "de vuelta en el período de hoy")
        flecha("Período siguiente").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Arriendo de noviembre") }
        assertTrue(hay(TITULO_LO_QUE_VENCERA))
        assertTrue(hay(NOTA_DEL_PERIODO_SIGUIENTE))
        assertTrue(hay("1.800.000", substring = true))
        assertTrue(!habilitada("Período siguiente"), "más allá del siguiente no se va")
    }

    // ── Lo que viene ──────────────────────────────────────────────────────

    /** El 29-oct con el período cerrando el 31 (corte 1): el 5-nov vencen el arriendo y la cuota. */
    private fun conElCierreEnDosDias(dias: Int) {
        val hoyFijo = LocalDate(2026, 10, 31).plus(-dias, DateTimeUnit.DAY)
        upcoming = listOf(
            vence(regla("rr_varios", "Varios", 9_000L, 31), LocalDate(2026, 10, 31), hoyFijo),
            vence(regla("rr_arriendo", "Arriendo", 1_800_000L, 5), LocalDate(2026, 11, 5), hoyFijo),
            vence(regla("rr_cuota", "Cuota", 980_000L, 5), LocalDate(2026, 11, 5), hoyFijo),
        )
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TableroDeRecurrentesDePrueba(
                        ajustesDelPeriodo = PeriodSettings(cutoffDay = 1),
                        onNavigate = {},
                        periodoFijo = PeriodoFinanciero(2026, 10),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `con siete dias o menos del periodo, lo que viene va debajo de la lista con su total`() {
        conElCierreEnDosDias(2)
        composeRule.waitUntil(timeoutMillis = 5_000) { contarTag(TAG_LO_QUE_VIENE) == 1 }
        assertTrue(hay(TITULO_LO_QUE_VIENE))
        assertTrue(hay("Arriendo") && hay("Cuota"))
        assertTrue(hay("2.780.000", substring = true), "el total de lo que viene")
        assertTrue(hay(NOTA_DE_LO_QUE_VIENE))
        // «Falta por pagar» sigue siendo del período: solo los $9.000 de Varios.
        assertTrue(hay("9.000", substring = true))
    }

    @Test
    fun `a mitad del periodo no va lo que viene`() {
        conElCierreEnDosDias(15)
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Varios") }
        assertEquals(0, contarTag(TAG_LO_QUE_VIENE))
    }

    // ── La caja con su fuente caída ───────────────────────────────────────

    /**
     * Con la lista del período en la instantánea del Inicio, la caja podía proyectar con lo de antes; con
     * `/api/payments` caído no se pinta: el aviso único de arriba lo dice (revisión 4, `r4-err-pagos.png`).
     */
    private fun conLaInstantanea() {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        InstantaneaDelInicio.delAparato.guardarDatos(
            "u1",
            DashboardData(
                summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 0),
                accounts = listOf(Account("a1", "Bancolombia", AccountType.SAVINGS, 10_500_000L, "COP")),
                upcoming = emptyList(),
                ocurrencias = emptyList(),
                gastoVariablePorDia = emptyMap(),
                ajustesDePeriodo = delDueno,
                periodoActual = periodoDeHoy,
            ),
        )
    }

    @Test
    fun `con los pagos la caja se pinta, con lo libre`() {
        conLaInstantanea()
        montarPagos()
        composeRule.waitUntil(timeoutMillis = 5_000) { contarTag(TAG_FRASE_DE_PUENTE) == 1 }
        assertEquals(0, contarTag(TAG_AVISO_DE_LA_PANTALLA))
        assertTrue(hay("te quedan libres", substring = true))
    }

    @Test
    fun `sin los pagos la caja no se pinta y lo dice el aviso unico`() {
        conLaInstantanea()
        vencimientosFallan = true
        montarPagos()
        composeRule.waitUntil(timeoutMillis = 5_000) { contarTag(TAG_AVISO_DE_LA_PANTALLA) == 1 }
        composeRule.waitForIdle()
        assertTrue(hay(NO_PUDIMOS_CARGAR))
        assertEquals(0, contarTag(TAG_CAJA_PROYECTADA), "la caja no se pinta con su fuente caída")
        assertTrue(!hay(TITULO_CAJA_PROYECTADA))
        assertTrue(!hay("10,5M", substring = true), "ni la cifra de antes")
    }
}
