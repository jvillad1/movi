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
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.sdui.ChecklistDelPeriodoSection
import com.jvillada.movi.ui.transactions.CHIP_RECURRENTES
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * **La tarjeta del Inicio dice que lo suyo es solo lo que falta.**
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

    private fun montar(data: DashboardData) {
        navegoA = null
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    ChecklistDelPeriodoSection(
                        section = ScreenSection(type = "CHECKLIST_DEL_PERIODO", title = "Pagos del período"),
                        data = data,
                        onNavigate = { navegoA = it },
                    )
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
    fun `la tarjeta declara que lista lo pendiente y cuantos pagos del periodo son`() {
        montar(conTresPagosYUnoMarcado())

        // El rótulo dejó de prometer «todos». MinSectionHeader lo pinta en mayúsculas.
        composeRule.onNodeWithText("FALTA POR PAGAR", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("PAGOS DEL PERÍODO", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithText("Te faltan 2 de 3 pagos de este período", useUnmergedTree = true)
            .assertIsDisplayed()
        // Y dice dónde está lo que no muestra — sin decir que fue el dueño quien lo marcó: fue
        // Movi quien emparejó el arriendo solo (el checklist es de solo lectura desde #363).
        composeRule.onNodeWithText(
            "Ya salió 1. El checklist completo está en «Ver todos».",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    /** Lo pendiente se lista; lo ya marcado no ocupa lugar en el resumen, pero sí en la cuenta. */
    @Test
    fun `lista lo pendiente y deja lo marcado para el checklist completo`() {
        montar(conTresPagosYUnoMarcado())

        composeRule.onNodeWithText("Celular", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Gimnasio", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Arriendo", useUnmergedTree = true).assertDoesNotExist()
        // El total es la suma de lo pendiente, sin el arriendo que ya se marcó.
        composeRule.onNodeWithText("Falta $192.900", useUnmergedTree = true).assertIsDisplayed()
    }

    /** «Ver todos» aterriza en el checklist: Movimientos con el chip «Recurrentes» puesto. */
    @Test
    fun `Ver todos lleva al checklist completo`() {
        montar(conTresPagosYUnoMarcado())

        composeRule.onNodeWithText("Ver todos", useUnmergedTree = true).performClick()

        assertEquals(Screen.Transactions(CHIP_RECURRENTES), navegoA)
    }

    /** Con todo marcado la tarjeta no miente al revés: lo dice, y no manda a ninguna parte. */
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

        composeRule.onNodeWithText("Ya salió el único pago de este período", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Ver todos", useUnmergedTree = true).assertIsDisplayed()
    }

    /** El pie en plural: dos o más pagos ya salieron y siguen sin ocupar lugar en el resumen. */
    @Test
    fun `el pie dice en plural cuando ya salio mas de un pago`() {
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

        composeRule.onNodeWithText(
            "Ya salieron 2. El checklist completo está en «Ver todos».",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }
}
