package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.OccurrenceState
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
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.LocalCargandoElInicio
import com.jvillada.movi.ui.espacios.EspaciosScreen
import com.jvillada.movi.ui.espacios.TAG_DESGLOSE_DEL_SALDO
import com.jvillada.movi.ui.periodos.PeriodosScreen
import com.jvillada.movi.ui.plan.PlanScreen
import com.jvillada.movi.ui.plan.TAG_FILA_DEL_MES_TIPICO
import com.jvillada.movi.ui.plan.TAG_TU_MES_TIPICO
import com.jvillada.movi.ui.plan.TITULO_TU_MES_TIPICO
import com.jvillada.movi.ui.recurrentes.TAG_CARGAR_MINIMO
import com.jvillada.movi.ui.recurrentes.TAG_NO_SUMA
import com.jvillada.movi.ui.recurrentes.TAG_SIN_CONTAR
import com.jvillada.movi.ui.recurrentes.TAG_TARJETA_FALTA_POR_PAGAR
import com.jvillada.movi.ui.sdui.ProximoPagoSection
import com.jvillada.movi.ui.sdui.TAG_NO_SUMA_EN_EL_INICIO
import com.jvillada.movi.ui.sdui.TAG_NOMBRE_DEL_PROXIMO_PAGO
import com.jvillada.movi.ui.sdui.WidgetFaltaPorPagar
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
 * # Vuelta 86 · una cifra por pregunta, en pantalla
 *
 * - **Pagos** (390 y 1.280) contesta dos preguntas: sin «Flujo libre» ni «Libre sin las tarjetas»; la fila
 *   «Tu mes típico» lleva a «Tus períodos». La tarjeta de Nu sin mínimo lleva «No suma: falta el mínimo»
 *   con «Cargar mínimo» (que abre los términos de esa tarjeta) y bajo el total «+ Tarjeta Nu sin contar».
 * - **Inicio**: el widget «Falta por pagar» y «Próximo pago» dicen la fila que no suma.
 * - **Tus períodos** lleva «Tu mes típico» con el flujo libre de siempre; si su lectura falla, no se pinta.
 * - **Grupos**: el saldo dice «$198.250 de este período + $341.375 que venía de antes».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h3200dp-xhdpi")
class Vuelta86EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy: LocalDate = Clock.System.todayIn(TimeZone.of("America/Bogota"))

    private fun regla(id: String, nombre: String, monto: Long, tipo: TransactionType = TransactionType.EXPENSE, saldo: Boolean = false) =
        RecurringRule(
            id = id, name = nombre, category = "Servicios", amount = monto, dayOfMonth = hoy.dayOfMonth, type = tipo,
            montoEsSaldo = saldo,
        )

    /** Todo vence hoy: así cae en el período de hoy cualquier día que corra la prueba. */
    private fun vence(r: RecurringRule) = UpcomingPayment(r, hoy.toString(), 0, PaymentStatus.DUE_SOON)

    private val bodytech = regla("rr_body", "Bodytech", 129_900)
    private val cuota = regla("rr_cuota", "Cuota del carro", 1_090_000)
    private val nu = regla("card_nu", "Pago tarjeta Tarjeta Nu", 540_000, saldo = true)
    private val salario = regla("rr_sal", "Salario", 6_800_000, TransactionType.INCOME)

    private val tarjetaNu = CardSummary(account = Account("nu", "Tarjeta Nu", AccountType.CREDIT_CARD, -540_000), terms = null)

    private var vencimientosFallan = false

    private open inner class Repo : RepositorioDePrueba() {
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 6_800_000, egresos = 0)
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
            DashboardSummary(spentByCategory = emptyMap(), gastoVariablePorDia = emptyMap())
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "u@local", name = "U", avatarColor = "#000000", periodCutoffDay = 1)
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> =
            if (vencimientosFallan) error("sin red") else listOf(vence(cuota), vence(bodytech), vence(nu), vence(salario))
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(cuota, bodytech, salario)
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getGastoDelDiaADia(): GastoDelDiaADia = GastoDelDiaADia(porDia = 10_000L)
        override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 9_000_000))
        override suspend fun getCards(): List<CardSummary> = listOf(tarjetaNu)
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
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

    private fun hay(texto: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun contarTag(tag: String): Int = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperarTag(tag: String) = composeRule.waitUntil(timeoutMillis = 5_000) { contarTag(tag) > 0 }

    private fun textoDe(nodo: SemanticsNodeInteraction): String {
        fun deUno(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.children.flatMap(::deUno)
        return deUno(nodo.fetchSemanticsNode()).joinToString(" ")
    }

    // ── 1 y 2 · Pagos ─────────────────────────────────────────────────────

    private fun montarPagos() {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PlanScreen(onNavigate = {}) } } }
        esperarTag(TAG_NO_SUMA)
    }

    private fun pagosConDosCifras() {
        montarPagos()
        // Dos preguntas: lo que se repite al mes ya no es una cifra de Pagos.
        assertTrue(!hay("Flujo libre"), "«Flujo libre» se mudó a Tus períodos")
        assertTrue(!hay("Libre sin las tarjetas"))
        assertEquals(1, contarTag(TAG_FILA_DEL_MES_TIPICO), "la fila que lleva a «Tu mes típico»")
        // La tarjeta de Nu sin mínimo lo dice ahí mismo, con cómo arreglarlo.
        assertEquals(1, contarTag(TAG_NO_SUMA))
        assertTrue(textoDe(composeRule.onNodeWithTag(TAG_NO_SUMA, useUnmergedTree = true)).contains("No suma: falta el mínimo"))
        assertEquals(1, contarTag(TAG_CARGAR_MINIMO))
        assertTrue(hay("Cargar mínimo"))
        // Y bajo el total, lo que no está en la cifra.
        assertEquals("+ Tarjeta Nu sin contar", textoDe(composeRule.onNodeWithTag(TAG_SIN_CONTAR, useUnmergedTree = true)))
        val tarjeta = textoDe(composeRule.onNodeWithTag(TAG_TARJETA_FALTA_POR_PAGAR, useUnmergedTree = true))
        assertTrue("\$1.219.900" in tarjeta, "la cifra de siempre (cuota + Bodytech), sin la tarjeta: $tarjeta")
    }

    @Test
    fun `Pagos a 390 contesta dos preguntas y la fila sin minimo lo dice`() = pagosConDosCifras()

    @Test
    @Config(qualifiers = "w1280dp-h3200dp-mdpi")
    fun `Pagos a 1280 tambien`() = pagosConDosCifras()

    @Test
    fun `Cargar minimo abre los terminos de esa tarjeta`() {
        montarPagos()
        composeRule.onNodeWithTag(TAG_CARGAR_MINIMO, useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Pago mínimo del extracto (opcional)") }
        assertTrue(hay("Tarjeta Nu", substring = true))
    }

    // ── 2 · El Inicio ─────────────────────────────────────────────────────

    private val septiembre = PeriodoFinanciero(2026, 9)

    private fun pago(id: String, nombre: String, monto: Long, dias: Int, saldo: Boolean = false) = UpcomingPayment(
        rule = RecurringRule(
            id = id, name = nombre, category = "Servicios", amount = monto, dayOfMonth = 10 + dias, type = TransactionType.EXPENSE,
            montoEsSaldo = saldo,
        ),
        dueDate = "2026-09-" + (10 + dias).toString().padStart(2, '0'),
        daysUntil = dias,
        status = PaymentStatus.UPCOMING,
    )

    private fun inicio(vararg pagos: UpcomingPayment) = DashboardData(
        upcoming = pagos.toList(),
        ocurrencias = emptyList(),
        periodoActual = septiembre,
        ajustesDePeriodo = PeriodSettings(),
    )

    @Test
    fun `el widget de Inicio dice la fila que no suma, y lo que queda sin contar`() {
        val datos = inicio(
            pago("rr_body", "Bodytech", 129_900, 2),
            pago("card_nu", "Pago tarjeta Tarjeta Nu", 540_000, 3, saldo = true),
            pago("rr_cuota", "Cuota del carro", 1_090_000, 5),
        )
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalCargandoElInicio provides false) {
                    Column { WidgetFaltaPorPagar(datos, onNavigate = {}) }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, contarTag(TAG_NO_SUMA_EN_EL_INICIO), "la fila de Nu dice por qué no tiene monto")
        assertEquals("No suma: falta el mínimo", textoDe(composeRule.onNodeWithTag(TAG_NO_SUMA_EN_EL_INICIO, useUnmergedTree = true)))
        assertEquals("+ Tarjeta Nu sin contar", textoDe(composeRule.onNodeWithTag(TAG_SIN_CONTAR, useUnmergedTree = true)))
        assertTrue(hay("\$1.219.900"), "la cifra de siempre")
    }

    @Test
    fun `Proximo pago es el que vence primero y, si no suma, lo dice`() {
        val datos = inicio(
            pago("rr_cuota", "Cuota del carro", 1_090_000, 5),
            pago("card_nu", "Pago tarjeta Tarjeta Nu", 540_000, 1, saldo = true),
            pago("rr_body", "Bodytech", 129_900, 3),
        )
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalCargandoElInicio provides false) {
                    Column { ProximoPagoSection(datos, onNavigate = {}) }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals("Pago tarjeta Tarjeta Nu", textoDe(composeRule.onNodeWithTag(TAG_NOMBRE_DEL_PROXIMO_PAGO, useUnmergedTree = true)))
        assertEquals("No suma: falta el mínimo", textoDe(composeRule.onNodeWithTag(TAG_NO_SUMA_EN_EL_INICIO, useUnmergedTree = true)))
    }

    // ── 1 · Tus períodos con «Tu mes típico» ──────────────────────────────

    private val enCurso = ResumenDePeriodo(
        id = "2026-10", nombre = "Octubre 2026", desde = "2026-10-01", hasta = "2026-10-31",
        enCurso = true, entradas = 6_800_000L, salidas = 3_000_000L, movimientos = 12,
    )

    private inner class ConPeriodos : Repo() {
        override suspend fun getPeriodos(): List<ResumenDePeriodo> = listOf(enCurso)
    }

    @Test
    fun `Tus periodos lleva Tu mes tipico con el flujo libre de siempre`() {
        Repositories.sustitutoDePrueba = ConPeriodos()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PeriodosScreen(onNavigate = {}) } } }
        esperarTag(TAG_TU_MES_TIPICO)
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Flujo libre") }
        assertTrue(hay(TITULO_TU_MES_TIPICO))
        // Salario − (cuota + Bodytech): la cuenta de siempre (`resumenRecurrentes`); la tarjeta no es un gasto del mes.
        assertTrue(hay("\$5.580.100"), "el flujo libre de siempre")
        assertTrue(hay("Octubre", substring = true), "y los períodos siguen ahí")
    }

    @Test
    fun `si su lectura falla, Tus periodos no pinta Tu mes tipico`() {
        vencimientosFallan = true
        Repositories.sustitutoDePrueba = ConPeriodos()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PeriodosScreen(onNavigate = {}) } } }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Octubre", substring = true) }
        composeRule.waitForIdle()
        assertEquals(0, contarTag(TAG_TU_MES_TIPICO))
        assertTrue(!hay("Flujo libre"))
    }

    // ── 3 · Grupos cuadra ─────────────────────────────────────────────────

    private val casa = Espacio(
        id = "e1", nombre = "Casa con Andrés",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Laura"), MiembroDelEspacio("t_andres", "Andrés Rojas")),
    )

    private fun gasto(id: String, monto: Long, quien: String = MIEMBRO_YO) = GastoDelEspacio(
        id = id, espacioId = "e1", pagadoPor = quien, monto = monto, categoria = "Mercado", descripcion = id,
        fecha = 1_761_000_000_000L, partes = partesDelGasto(monto, casa, quien),
    )

    @Test
    fun `en Grupos el saldo se explica con lo del periodo y lo de antes`() {
        val delPeriodo = listOf(gasto("Ara", 190_000), gasto("D1", 338_900), gasto("D1 Calle 63", 130_000), gasto("Carulla", 262_400, "t_andres"))
        val todos = delPeriodo + gasto("Arriendo de agosto", 682_750)
        val detalle = DetalleDelEspacio(
            espacio = casa,
            saldo = saldoDelEspacio(casa, todos, emptyList()),
            delPeriodo = saldoDelEspacio(casa, delPeriodo, emptyList()),
            gastos = todos,
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEspacios(): List<Espacio> = listOf(casa)
            override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio = detalle
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { EspaciosScreen(onNavigate = {}) } } }
        esperarTag(TAG_DESGLOSE_DEL_SALDO)
        assertTrue(hay("\$539.625"), "el saldo de siempre")
        assertTrue(hay("Mitad \$460.650"), "«Quién puso qué» del período")
        assertEquals(
            "\$198.250 de este período + \$341.375 que venía de antes",
            textoDe(composeRule.onNodeWithTag(TAG_DESGLOSE_DEL_SALDO, useUnmergedTree = true)),
        )
    }
}
