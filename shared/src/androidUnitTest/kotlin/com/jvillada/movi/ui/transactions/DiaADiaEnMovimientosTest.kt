package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Día a día» en Movimientos
 *
 * Debajo del «Flujo del día», cuánto gastaste ese día de lo que podías (la meta diaria del
 * Disponible de Plan). Solo en los días del período en curso; sin meta, o con el período cerrado a
 * la vista, no se dibuja nada. Montado como en la app: teléfono de 390 dp con la escala de letra
 * ×1,12.
 *
 * El período arranca hace unos días y los ingresos se eligen para que la meta sea justo $40.000.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class DiaADiaEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val hoy = epochMillisToAppDate(ahora)

    /** Un corte que deja el inicio del período unos días atrás, con hoy y los dos días anteriores adentro. */
    private val offset = (4..20).first { hoy.minus(it, DateTimeUnit.DAY).dayOfMonth in 1..28 }
    private val corte = hoy.minus(offset, DateTimeUnit.DAY).dayOfMonth
    private val ajustes = PeriodSettings(cutoffDay = corte)
    private val periodo = periodoActual(ahora, ajustes)
    private val inicio = inicioDelPeriodo(periodo, ajustes)
    private val diasDelPeriodo = inicio.daysUntil(inicioDelPeriodo(periodoSiguiente(periodo), ajustes))
    private val meta = 40_000L

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val ayer = hoy.minus(1, DateTimeUnit.DAY)
    private val anteayer = hoy.minus(2, DateTimeUnit.DAY)
    private val delAnterior = inicio.minus(3, DateTimeUnit.DAY)

    private fun evento(id: String, tipo: TransactionType, monto: Long, nombre: String, dia: kotlinx.datetime.LocalDate) =
        FinancialEvent(
            id = id, accountId = banco.id, type = tipo, amount = monto, category = "Comida", description = nombre,
            timestamp = ahora - hoy.daysUntil(dia).let { -it } * 24L * 3600 * 1000,
            reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
        )

    private val dias = listOf(
        EventDay(hoy.toString(), 1_000_000L, listOf(evento("e1", TransactionType.INCOME, 1_000_000L, "Sueldo de hoy", hoy))),
        EventDay(ayer.toString(), -60_000L, listOf(evento("e2", TransactionType.EXPENSE, 60_000L, "Cena de ayer", ayer))),
        EventDay(anteayer.toString(), -10_000L, listOf(evento("e3", TransactionType.EXPENSE, 10_000L, "Tinto de antier", anteayer))),
        EventDay(delAnterior.toString(), -5_000L, listOf(evento("e4", TransactionType.EXPENSE, 5_000L, "Pan del período anterior", delAnterior))),
    )

    private var resumenDelInicio: suspend () -> DashboardSummary = {
        DashboardSummary(gastoVariablePorDia = mapOf(ayer.toString() to 60_000L, anteayer.toString() to 10_000L))
    }

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile = UserProfile(
            id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado", periodCutoffDay = corte,
        )
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun getEventsByDay(): List<EventDay> = dias
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = diasDelPeriodo * meta, egresos = 0)
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = resumenDelInicio()
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
    }

    private var colorDeSale = Color.Unspecified
    private var colorDeEntra = Color.Unspecified

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = repositorio
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private var enPantalla by mutableStateOf(true)

    private fun montar(periodoInicial: String? = null) {
        composeRule.setContent {
            if (enPantalla) MoviTheme {
                colorDeSale = Movi.colores.sale
                colorDeEntra = Movi.colores.entra
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}, periodoInicial = periodoInicial) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun lineas(): List<SemanticsNodeInteraction> {
        val n = composeRule.onAllNodesWithTag(TAG_DIA_A_DIA, useUnmergedTree = true).fetchSemanticsNodes().size
        return (0 until n).map { composeRule.onAllNodesWithTag(TAG_DIA_A_DIA, useUnmergedTree = true)[it] }
    }

    private fun descripciones(): List<String> = lineas().map {
        it.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty()
    }

    private fun tops(): List<Float> {
        val n = composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA, useUnmergedTree = true).fetchSemanticsNodes().size
        return (0 until n).map {
            composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA, useUnmergedTree = true)[it]
                .fetchSemanticsNode().boundsInRoot.top
        }
    }

    @Test
    fun `en el periodo en curso cada dia dice cuanto gasto de la meta y el pasado, cuanto se paso`() {
        montar()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Cena de ayer") }
        composeRule.waitUntil(timeoutMillis = 5_000) { lineas().size == 3 }

        assertEquals(
            listOf(
                "Día a día: \$0 de \$40.000",
                "Día a día: \$60.000 de \$40.000 · te pasaste \$20.000",
                "Día a día: \$10.000 de \$40.000",
            ),
            descripciones(),
        )
        assertTrue(!hay("Pan del período anterior"), "el día del período cerrado ni siquiera está en la lista")
        // A 390 dp con la letra ×1,12 la más larga entra en UNA línea: ninguna se corta ni se parte.
        val altos = lineas().map { it.fetchSemanticsNode().boundsInRoot.height }.toSet()
        assertEquals(1, altos.size, "las tres líneas miden lo mismo (una sola línea): $altos")
    }

    @Test
    fun `lo pasado va en el rojo de la plata que sale y lo que quedo dentro en el de la que entra`() {
        montar()
        composeRule.waitUntil(timeoutMillis = 5_000) { lineas().size == 3 }

        fun spans(i: Int) = lineas()[i].fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)!!.first().let { texto ->
                texto.spanStyles.associate { texto.text.substring(it.start, it.end) to it.item.color }
            }
        val pasada = spans(1)
        assertEquals(colorDeSale, pasada["te pasaste \$20.000"])
        val dentro = spans(2)
        assertEquals(colorDeEntra, dentro["Día a día: \$10.000 de \$40.000"])
        assertTrue(dentro.values.none { it == colorDeSale })
    }

    @Test
    fun `con un periodo cerrado a la vista no se dibuja ninguna linea`() {
        montar(periodoInicial = periodoAnterior(periodo).prefijo)
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Pan del período anterior") }
        composeRule.waitForIdle()

        assertEquals(0, lineas().size)
    }

    @Test
    fun `si no llega el disponible no se dice nada y la lista es la de siempre`() {
        resumenDelInicio = { error("sin red") }
        montar()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Cena de ayer") }
        composeRule.waitForIdle()

        assertEquals(0, lineas().size)
        assertTrue(!hay("Actualizando…"), "una lectura de adorno no anuncia que actualiza")
        assertTrue(!hay("No pudimos actualizar", ), "ni que falló")
    }

    @Test
    fun `sin margen que dividir tampoco se dibuja nada`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = repositorio.getUserProfile()
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> = dias
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
                FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0)
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = resumenDelInicio()
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
            override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        }
        montar()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Cena de ayer") }
        composeRule.waitForIdle()

        assertEquals(0, lineas().size)
    }

    /**
     * Mientras la meta viaja se reserva el alto de la línea: cuando llega, los días de abajo no se
     * mueven ni un píxel.
     */
    @Test
    fun `la linea llega sin empujar la lista`() {
        val puerta = CompletableDeferred<DashboardSummary>()
        resumenDelInicio = { puerta.await() }
        montar()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Cena de ayer") }
        composeRule.waitForIdle()
        assertEquals(0, lineas().size, "todavía no llegó")
        val antes = tops()
        assertEquals(3, antes.size)

        puerta.complete(DashboardSummary(gastoVariablePorDia = mapOf(ayer.toString() to 60_000L, anteayer.toString() to 10_000L)))
        composeRule.waitUntil(timeoutMillis = 5_000) { lineas().size == 3 }
        composeRule.waitForIdle()

        assertEquals(antes, tops(), "el alto ya estaba reservado")
    }

    /**
     * Ir a otra pantalla y volver no vuelve a esconder la línea: la meta se recuerda como el resto
     * de las lecturas (ver `CacheDeLecturas`) y al primer cuadro ya está, con la lectura nueva
     * todavía en vuelo.
     */
    @Test
    fun `al volver a Movimientos la linea ya esta al primer cuadro`() {
        montar()
        composeRule.waitUntil(timeoutMillis = 5_000) { lineas().size == 3 }
        composeRule.waitForIdle()
        enPantalla = false
        composeRule.waitForIdle()

        val puerta = CompletableDeferred<DashboardSummary>()
        resumenDelInicio = { puerta.await() }
        enPantalla = true
        composeRule.waitForIdle()

        assertEquals(3, lineas().size, "lo último que se vio ya está a la vista")
        assertTrue(hay("Cena de ayer"))
        puerta.complete(DashboardSummary(gastoVariablePorDia = mapOf(ayer.toString() to 60_000L, anteayer.toString() to 10_000L)))
        composeRule.waitForIdle()
        assertEquals(3, lineas().size)
    }
}
