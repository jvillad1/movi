package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.TAG_PROXIMO_PAGO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Con la instantánea de la generación 10, el Inicio no salta al cargar (vuelta 39)
 *
 * La revisión vio, mientras decía «Actualizando…», el arriendo en «Movi propone hoy» y, al llegar el
 * dato, «Próximo pago» apareciendo arriba y todo bajando. Era la instantánea de la generación 9 (sin
 * PROXIMO_PAGO): pasa una vez, el primer arranque después del despliegue. Con la definición y los datos
 * de la generación 10 en el aparato, «Próximo pago» se pinta desde el primer cuadro —sale de los
 * vencimientos que la instantánea ya guarda— y no se mueve cuando llega lo nuevo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class ElInicioNoSaltaConLaInstantaneaTest {

    @get:Rule val composeRule = createComposeRule()

    private val puerta = CompletableDeferred<Unit>()

    private val hoy = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds())

    private val arriendo = UpcomingPayment(
        rule = RecurringRule(id = "r_arr", name = "Arriendo", category = "Vivienda", amount = 1_850_000, dayOfMonth = hoy.dayOfMonth, type = TransactionType.EXPENSE),
        dueDate = hoy.toString(),
        daysUntil = 0,
        status = PaymentStatus.UPCOMING,
    )

    private val cuentas = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 558_350))

    private val deAyer = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0),
        accounts = cuentas,
        credits = emptyList(),
        cards = emptyList(),
        upcoming = listOf(arriendo),
        ocurrencias = emptyList<OccurrenceState>(),
        ultimosMovimientos = emptyList(),
        gastoVariablePorDia = emptyMap(),
        spentByCategory = emptyMap(),
        ajustesDePeriodo = PeriodSettings(),
        periodoActual = periodoDe(Clock.System.now().toEpochMilliseconds(), PeriodSettings()),
    )

    @Before
    fun entrar() {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? { puerta.await(); return null }
            override suspend fun getFinanceSummary(scope: Scope): FinanceSummary { puerta.await(); return deAyer.summary!! }
            override suspend fun getAccounts(): List<Account> { puerta.await(); return cuentas }
            override suspend fun getCredits(): List<CreditSummary> { puerta.await(); return emptyList() }
            override suspend fun getCards(): List<CardSummary> { puerta.await(); return emptyList() }
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> { puerta.await(); return listOf(arriendo) }
            override suspend fun getOccurrenceStates(): List<OccurrenceState> { puerta.await(); return emptyList() }
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary { puerta.await(); return DashboardSummary(gastoVariablePorDia = emptyMap()) }
        }
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
    }

    @Test
    fun `con la instantanea de la generacion 10 el proximo pago esta desde el primer cuadro y no se mueve`() {
        InstantaneaDelInicio.delAparato.guardarDefinicion("u1", defaultDashboardDefinition())
        InstantaneaDelInicio.delAparato.guardarDatos("u1", deAyer)
        composeRule.setContent { MoviTheme { DashboardScreen(onNavigate = {}) } }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("Actualizando…").fetchSemanticsNodes().size, "todavía está cargando")
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_PROXIMO_PAGO, useUnmergedTree = true).fetchSemanticsNodes().size)
        val antes = composeRule.onNodeWithTag(TAG_PROXIMO_PAGO, useUnmergedTree = true).getUnclippedBoundsInRoot().top

        puerta.complete(Unit)
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithText("Actualizando…").fetchSemanticsNodes().size, "ya cargó")
        assertEquals(antes, composeRule.onNodeWithTag(TAG_PROXIMO_PAGO, useUnmergedTree = true).getUnclippedBoundsInRoot().top)
        // Y «Movi propone hoy» no repitió el arriendo en ningún momento.
        assertEquals(0, composeRule.onAllNodesWithText("Arriendo vence hoy", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
