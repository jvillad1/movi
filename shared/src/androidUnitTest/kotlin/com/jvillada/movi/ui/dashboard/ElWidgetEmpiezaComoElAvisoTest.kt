package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.colaDePendientes
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.Pendiente
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_NOMBRE_DEL_PROXIMO_PAGO
import com.jvillada.movi.ui.sdui.TAG_NOMBRE_EN_EL_WIDGET_FALTA_POR_PAGAR
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Vuelta 78 · el widget «Falta por pagar» empieza por el mismo pago que el aviso de arriba
 *
 * La revisión 6 (`r6-inicio-tras-credito.png`): el aviso «Pago vencido» ponía primero la cuota de
 * $770.000 y el widget, más abajo, Carro $9.000 —saltaba el de arriba (vuelta 68)—. Ahora los dos salen
 * de la misma cola, en el mismo orden: lo vencido primero y, dentro, el monto más grande.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class ElWidgetEmpiezaComoElAvisoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ajustes = PeriodSettings()
    private val hoy = Clock.System.todayIn(TimeZone.of("America/Bogota"))

    private fun vencido(id: String, nombre: String, monto: Long, haceDias: Int) = UpcomingPayment(
        RecurringRule(id = id, name = nombre, category = "Varios", amount = monto, dayOfMonth = 1, type = TransactionType.EXPENSE),
        dueDate = hoy.minus(DatePeriod(days = haceDias)).toString(),
        daysUntil = -haceDias,
        status = PaymentStatus.OVERDUE,
    )

    private val data = DashboardData(
        accounts = listOf(Account("a1", "Bancolombia", AccountType.SAVINGS, 10_589_700, "COP")),
        // El carro vence después que la cuota pero va primero en la lista: el orden lo pone la cola.
        upcoming = listOf(
            vencido("rr_carro", "Carro", 9_000, haceDias = 0),
            vencido("rr_cuota", "Cuota Libre Inversión R6", 770_000, haceDias = 0),
        ),
        ocurrencias = emptyList(),
        ajustesDePeriodo = ajustes,
        periodoActual = periodoActual(Clock.System.now().toEpochMilliseconds(), ajustes),
    )

    private fun textos(tag: String): List<String> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }

    @Test
    fun `el widget y el aviso empiezan por el mismo pago, en el orden de la cola`() {
        val cola = colaDePendientes(checklistDelPeriodoDe(data)).filterIsInstance<Pendiente.Pago>().map { it.pago.nombre }
        assertEquals(listOf("Cuota Libre Inversión R6", "Carro"), cola, "la cola: el monto más grande primero")

        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(definition = defaultDashboardDefinition(), data = data, modifier = Modifier.fillMaxSize(), onNavigate = {})
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(listOf("Cuota Libre Inversión R6"), textos(TAG_NOMBRE_DEL_PROXIMO_PAGO))
        assertEquals(cola, textos(TAG_NOMBRE_EN_EL_WIDGET_FALTA_POR_PAGAR), "el widget, desde el primero de la cola")
    }
}
