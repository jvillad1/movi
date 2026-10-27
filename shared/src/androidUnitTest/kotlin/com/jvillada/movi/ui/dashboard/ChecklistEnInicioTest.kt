package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.sdui.TAG_WIDGET_FALTA_POR_PAGAR
import com.jvillada.movi.ui.sdui.TODO_PAGADO
import com.jvillada.movi.ui.sdui.WidgetFaltaPorPagar
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * **El widget «Falta por pagar» del Inicio dice que lo suyo es solo lo que falta.** (Ola R1: era una
 * tarjeta con su lista; ahora es el widget compacto, con los dos pagos que vencen primero.)
 *
 * El dueño, mirando esa tarjeta: *«En Inicio veo donde dice Pagos del período y solo muestra los
 * faltantes, no muestra todos; debería indicar que esos son los faltantes nada más»*. La tarjeta
 * sigue listando lo pendiente —el Inicio es un resumen— y lo que cambió es que lo declara: el
 * rótulo, la línea de avance y el pie que manda al checklist completo.
 *
 * Se monta la sección sola y no el Inicio entero: lo que hay que probar es lo que esta tarjeta
 * AFIRMA, y montar la pantalla completa metería una docena de lecturas que no dicen nada sobre eso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ChecklistEnInicioTest {

    @get:Rule val composeRule = createComposeRule()

    private val septiembre = PeriodoFinanciero(2026, 9)
    private val mesDeCalendario = PeriodSettings()

    private fun regla(id: String, nombre: String, monto: Long, dia: Int) = RecurringRule(
        id = id, name = nombre, category = "Vivienda", amount = monto,
        dayOfMonth = dia, type = TransactionType.EXPENSE,
    )

    private fun pago(rule: RecurringRule, vence: String, dias: Int) = UpcomingPayment(
        rule = rule, dueDate = vence, daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    private var navegoA: Screen? = null

    private fun montar(data: DashboardData, conPropuestas: Boolean = false) {
        navegoA = null
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    WidgetFaltaPorPagar(data = data, onNavigate = { navegoA = it }, conPropuestas = conPropuestas)
                }
            }
        }
    }

    /**
     * Tres pagos, uno ya marcado. El arriendo llega con su vencimiento **ya rodado a octubre** —es
     * lo que contesta el server en cuanto algo se sella— y aun así cuenta como pago de septiembre,
     * porque su ocurrencia dice que ese es el mes del que habla.
     */
    private fun conTresPagosYUnoMarcado() = DashboardData(
        upcoming = listOf(
            pago(regla("rr_gym", "Gimnasio", 139_900, 20), "2026-09-20", 8),
            pago(regla("rr_cel", "Celular", 53_000, 10), "2026-09-10", -2),
            pago(regla("rr_arriendo", "Arriendo", 1_850_000, 5), "2026-10-05", 23),
        ),
        ocurrencias = listOf(
            OccurrenceState(
                ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true,
            ),
        ),
        ajustesDePeriodo = mesDeCalendario,
        periodoActual = septiembre,
    )

    @Test
    fun `el widget declara que lista lo pendiente y cuantos pagos del periodo son`() {
        montar(conTresPagosYUnoMarcado())

        composeRule.onNodeWithText("Falta por pagar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Pagos del período", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Faltan 2 de 3 pagos", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Lo pendiente se lista; lo ya marcado no ocupa lugar en el resumen, pero sí en la cuenta. */
    @Test
    fun `lista lo pendiente y deja lo marcado para el checklist completo`() {
        montar(conTresPagosYUnoMarcado())

        composeRule.onNodeWithText("Celular", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Gimnasio", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Arriendo", useUnmergedTree = true).assertDoesNotExist()
        // El total es la suma de lo pendiente (`faltaPorPagar`), sin el arriendo que ya se marcó.
        composeRule.onNodeWithText("$192.900", useUnmergedTree = true).assertIsDisplayed()
        // El que venció va primero y lo dice.
        composeRule.onNodeWithText("Vencido hace 2 días", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Tocar el widget aterriza en el checklist completo: Plan · Pagos del mes. */
    @Test
    fun `tocar el widget lleva al checklist completo`() {
        montar(conTresPagosYUnoMarcado())

        composeRule.onNodeWithTag(TAG_WIDGET_FALTA_POR_PAGAR).performClick()

        assertEquals(Screen.Plan(SEGMENTO_PAGOS), navegoA)
    }

    /** Con todo marcado el widget no miente al revés: lo dice, sin cifra. */
    @Test
    fun `sin nada pendiente lo dice y no promete que haya algo escondido`() {
        montar(
            DashboardData(
                upcoming = listOf(pago(regla("rr_cel", "Celular", 53_000, 10), "2026-10-10", 23)),
                ocurrencias = listOf(
                    OccurrenceState(
                        ruleId = "rr_cel", period = "2026-09", dueDate = "2026-09-10", occurred = true,
                    ),
                ),
                ajustesDePeriodo = mesDeCalendario,
                periodoActual = septiembre,
            ),
        )

        composeRule.onNodeWithText(TODO_PAGADO, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Celular", useUnmergedTree = true).assertDoesNotExist()
    }

    /** En singular cuando falta uno solo: dos de tres ya salieron. */
    @Test
    fun `dice en singular cuando falta un solo pago`() {
        montar(
            DashboardData(
                upcoming = listOf(
                    pago(regla("rr_gym", "Gimnasio", 139_900, 20), "2026-09-20", 8),
                    pago(regla("rr_cel", "Celular", 53_000, 10), "2026-10-10", 23),
                    pago(regla("rr_arriendo", "Arriendo", 1_850_000, 5), "2026-10-05", 23),
                ),
                ocurrencias = listOf(
                    OccurrenceState(
                        ruleId = "rr_cel", period = "2026-09", dueDate = "2026-09-10", occurred = true,
                    ),
                    OccurrenceState(
                        ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true,
                    ),
                ),
                ajustesDePeriodo = mesDeCalendario,
                periodoActual = septiembre,
            ),
        )

        composeRule.onNodeWithText("Falta 1 de 3 pagos", useUnmergedTree = true).assertIsDisplayed()
    }
    /**
     * Vuelta de diseño 22: «Faltan 3 de 3 pagos» listaba dos y callaba el tercero. Una lista que se
     * recorta lo dice; y con «Movi propone hoy» en el Inicio, el pago que ya está arriba no se repite.
     */
    private fun conTresPendientes() = DashboardData(
        upcoming = listOf(
            pago(regla("rr_arriendo", "Arriendo", 1_850_000, 5), "2026-09-05", -3),
            pago(regla("rr_cel", "Celular", 53_000, 10), "2026-09-10", 2),
            pago(regla("rr_gym", "Gimnasio", 139_900, 12), "2026-09-12", 4),
        ),
        ocurrencias = emptyList(),
        ajustesDePeriodo = mesDeCalendario,
        periodoActual = septiembre,
    )

    @Test
    fun `una lista recortada dice y N mas`() {
        montar(conTresPendientes())

        composeRule.onNodeWithText("Faltan 3 de 3 pagos", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Arriendo", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Gimnasio", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("y 1 más", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `el pago de Movi propone hoy no se repite en el widget`() {
        montar(conTresPendientes(), conPropuestas = true)

        // El Arriendo vencido es la propuesta de arriba: acá no tiene fila.
        composeRule.onNodeWithText("Arriendo", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Gimnasio", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Celular", useUnmergedTree = true).assertIsDisplayed()
        // Las dos filas y «y 1 más» dan los tres de «Faltan 3 de 3 pagos».
        composeRule.onNodeWithText("y 1 más", useUnmergedTree = true).assertIsDisplayed()
    }
}
