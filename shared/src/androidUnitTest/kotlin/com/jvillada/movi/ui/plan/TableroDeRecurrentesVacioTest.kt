package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.recurrentes.TITULO_CHECKLIST_DEL_PERIODO
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * # Ola D, Task 3: el vacío que enseña de «Pagos del mes»
 *
 * Sin ninguna regla, ninguna suscripción y ninguna candidata, el tablero mostraba el checklist
 * («Este período no tiene pagos anotados»), «Próximos» («Nada vence en los próximos días») y el
 * card de «Flujo libre» con `$0` en ingresos, gastos y disponible — un hecho inventado sobre un
 * dueño que nunca anotó nada. Estas pruebas fijan el reemplazo: una sola tarjeta que explica qué
 * va a aparecer ahí y ofrece la MISMA hoja de alta que ya usa el tablero para editar (ver
 * [EstadoDelTableroDeRecurrentes.nuevoRecurrenteAbierto]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class TableroDeRecurrentesVacioTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private fun sub(id: String, nombre: String, monto: Long, dia: Int, estado: SubStatus) = Subscription(
        id = id, merchantKey = nombre.lowercase(), displayName = nombre, amount = monto, currency = "COP",
        dayOfMonth = dia, status = estado, confidence = SubConfidence.HIGH,
        firstSeen = 0, lastSeen = 0, occurrences = 4,
    )

    private open inner class Repo(private val suscripciones: List<Subscription> = emptyList()) : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(suscripciones, monthlyTotalCop = 0)
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun montar(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TableroDeRecurrentesDePrueba(ajustesDelPeriodo = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 1), onNavigate = {})
                }
            }
        }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `sin reglas ni suscripciones, el vacio que ensena y nada de Flujo libre ni $0`() {
        montar(Repo())
        esperarTexto("Aquí van tus pagos fijos")

        assertTrue(hay("Agregar un pago fijo"))
        assertTrue(!hay("Flujo libre"), "sin nada anotado no hay Flujo libre que mostrar")
        assertTrue(!hay("\$0"), "ningún \$0 presentado como un hecho para quien no anotó nada")
        assertTrue(!hay("Este período no tiene pagos anotados"), "el vacío único reemplaza al del checklist")
        assertTrue(!hay("Nada vence en los próximos días"), "el vacío único reemplaza al de Próximos")
        assertTrue(!hay(TITULO_CHECKLIST_DEL_PERIODO.uppercase()))
    }

    @Test
    fun `el boton abre la misma hoja de alta que usa el tablero para editar`() {
        montar(Repo())
        esperarTexto("Agregar un pago fijo")

        composeRule.onNodeWithText("Agregar un pago fijo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Modo alta de CreateRecurringRuleSheet: sin `existing` ni `existingSub` su título es
        // «Nuevo recurrente» — la MISMA hoja que ya abre este tablero para editar una regla.
        assertTrue(hay("Nuevo recurrente"))
    }

    @Test
    fun `con una candidata por confirmar no aparece el vacio`() {
        montar(Repo(suscripciones = listOf(sub("s_spotify", "Spotify", 16_900L, 8, SubStatus.CANDIDATE))))
        esperarTexto("Spotify")

        assertTrue(!hay("Aquí van tus pagos fijos"), "con una candidata ya hay algo que revisar")
        assertTrue(hay("DETECTADAS · POR CONFIRMAR"))
    }

    /**
     * Fix round 1: una suscripción DESCARTADA («Uber no es una suscripción», una interacción
     * corriente) no es una candidata ni una activa — no suma en ningún lado y no tiene fila en
     * ninguna sección—, así que sigue siendo «nada anotado» y el vacío tiene que aparecer igual.
     * Antes esto miraba `subsParaRecurrentes.subscriptions` ENTERA (que trae `DISMISSED` incluido)
     * y un descarte solo, sin ninguna regla, dejaba a alguien sin ver nunca el vacío: volvía a caer
     * en el checklist/«Próximos»/«Flujo libre» en `$0` que esta tarea existe para sacar.
     */
    @Test
    fun `con una sola suscripcion descartada, el vacio igual aparece`() {
        montar(Repo(suscripciones = listOf(sub("s_uber", "Uber", 25_000L, 3, SubStatus.DISMISSED))))
        esperarTexto("Aquí van tus pagos fijos")

        assertTrue(hay("Agregar un pago fijo"))
        assertTrue(!hay("Flujo libre"), "una descartada no es un pago fijo anotado")
        assertTrue(!hay("\$0"))
        assertTrue(!hay("Este período no tiene pagos anotados"))
        assertTrue(!hay("Nada vence en los próximos días"))
    }
}
