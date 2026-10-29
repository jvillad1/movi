package com.jvillada.movi.ui.recurrentes

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.quickadd.pastillaDelAbono
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 63 (revisión) · el editor de la regla del abono no parece un gasto
 *
 * Dice «Hacia: Vehículo 4083» y esconde Gasto/Ingreso, la categoría, «Persona o comercio» y «El banco
 * lo cobra solo»; borrarla dice que Movi deja de recordar el abono, no «flujo libre».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class ElEditorDelAbonoTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private val ahorros = Account("acc_ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 2_000_000L)
    private val vehiculo = Account("v4083", "Vehículo 4083", AccountType.LOAN, 30_000_000L)
    private var guardada: RecurringRule? = null

    private val abono = RecurringRule(
        id = "rr_abono", name = "Abono extra a Vehículo 4083", category = "Traspaso", amount = 500_000L,
        dayOfMonth = 15, type = TransactionType.EXPENSE, accountId = ahorros.id, haciaDeuda = vehiculo.id,
    )

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, vehiculo)
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule {
                guardada = rule
                return rule.copy(id = id)
            }
        }
        composeRule.setContent { MoviTheme { CreateRecurringRuleSheet(onDismiss = {}, onSaved = {}, existing = abono) } }
        // «Hacia» dice «Tu deuda» hasta que llegan las cuentas.
        composeRule.waitUntil(5_000) { hay("Hacia") && !hay("Tu deuda") }
    }

    @Test
    fun `dice hacia la deuda y esconde lo que es de un gasto`() {
        montar()
        assertTrue(hay(TITULO_DEL_EDITOR_DEL_ABONO))
        assertTrue(hay("Hacia"))
        assertTrue(hay(vehiculo.name))
        assertFalse(hay("Ingreso"), "sin Gasto/Ingreso")
        assertFalse(hay("Categoría"))
        assertFalse(hay(ROTULO_DE_LA_PERSONA_O_COMERCIO))
        assertFalse(hay(TITULO_DEL_DEBITO_DE_LA_REGLA))

        composeRule.onNodeWithText("Guardar cambios").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { guardada != null }
        val regla = assertNotNull(guardada)
        assertEquals("Traspaso", regla.category, "la categoría de la regla se conserva")
        assertEquals(false, regla.seDebitaSolo)
    }

    @Test
    fun `borrarla dice que Movi deja de recordar el abono`() {
        montar()
        composeRule.onNodeWithText("Eliminar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay(DETALLE_DE_BORRAR_EL_ABONO))
        assertFalse(hay("flujo libre"))
    }

    @Test
    fun `la fila pregunta por el abono del mes y despues del dia dice este mes`() {
        val fila = PagoDelPeriodo(
            ruleId = "rr_abono", nombre = abono.name, monto = 500_000L, pagado = false, diasParaVencer = 4,
            vence = "2026-10-15", periodoDelSello = "2026-10", periodoDelDueno = "2026-10", haciaDeuda = vehiculo.id,
        )
        assertEquals("¿Ya hiciste el abono de octubre?", preguntaDeLaFila(fila))
        assertEquals("El día 15", pastillaDelAbono(fila))
        assertEquals("Este mes", pastillaDelAbono(fila.copy(diasParaVencer = -2)))
    }
}
