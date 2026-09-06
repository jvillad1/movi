package com.jvillada.movi.ui.transactions

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
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PR 5 del rediseño de Recurrentes (2026-09): [TransactionsScreen] montada de verdad, con el chip
 * «Recurrentes» activo, para lo que una función pura no alcanza a probar — que las suscripciones
 * ACTIVAS se pinten (entre el PR 2 y el PR 4 no tenían ninguna superficie, aunque seguían sumando
 * en «Flujo libre») y que «Quitar» de verdad escriba, y escriba **lo que corresponde según de
 * quién sea la suscripción**: borrar la que escribió el dueño, marcar DISMISSED la que encontró
 * el detector.
 *
 * Esa última distinción es la parte sutil y la que más vale fijar: las dos ramas se ven idénticas
 * en pantalla (la fila desaparece) y solo se distinguen por a qué endpoint le pegaron.
 *
 * Mismo patrón de montaje que [ResumenRecurrentesEnMovimientosTest].
 */
@RunWith(RobolectricTestRunner::class)
// Más alta que los 731dp del resto de las pruebas de esta pantalla, a propósito: la sección va al
// final del chip —debajo del «Flujo libre», ver `SeccionSuscripcionesActivas`— y en 731dp la última
// fila queda bajo el pliegue. Ahí sigue componiéndose (el card entero es UN item del LazyColumn) así
// que `onAllNodesWithText` la ve, pero un toque en sus coordenadas no llega a nada. Una ventana alta
// es más honesta que scrollear a ciegas: lo que se prueba es la acción, no el scroll.
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class SuscripcionesActivasEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private fun sub(
        id: String,
        nombre: String,
        clave: String,
        monto: Long,
        moneda: String,
        dia: Int,
        estado: SubStatus,
    ) = Subscription(
        id = id, merchantKey = clave, displayName = nombre, amount = monto, currency = moneda,
        dayOfMonth = dia, status = estado, confidence = SubConfidence.HIGH,
        firstSeen = 0, lastSeen = 0, occurrences = 4,
    )

    /**
     * Las tres variantes de origen, ordenadas por día del mes — que es el orden en que las pinta
     * la sección, y por lo tanto el orden de los enlaces «Quitar».
     */
    private var suscripciones = listOf(
        // día 3 · la escribió el dueño
        sub("s_claude", "Claude", MANUAL_SUB_PREFIX + "claude", 12L, "USD", 3, SubStatus.CONFIRMED),
        // día 12 · la encontró el detector y se activó sola
        sub("s_youtube", "YouTube", "youtube", 22_900L, "COP", 12, SubStatus.AUTO),
        // día 20 · la encontró el detector y el dueño la confirmó
        sub("s_netflix", "Netflix", "netflix", 44_900L, "COP", 20, SubStatus.CONFIRMED),
    )

    /** Lo que la pantalla le pidió al repositorio, que es lo único que distingue las dos ramas. */
    private var actualizada: Subscription? = null
    private var borrada: String? = null

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(suscripciones, monthlyTotalCop = 67_800L, usdToCop = 4_000.0)

        override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription {
            actualizada = subscription
            suscripciones = suscripciones.map { if (it.id == id) subscription else it }
            return subscription
        }

        override suspend fun deleteSubscription(id: String) {
            borrada = id
            suscripciones = suscripciones.filterNot { it.id == id }
        }
    }

    @Before
    fun montar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        esperarTexto("Sin movimientos aún")
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("SUSCRIPCIONES ACTIVAS")
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun esperarQueDesaparezca(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    /** El «Quitar» de la fila N, en el orden por día del mes de [suscripciones]. */
    private fun quitarDeLaFila(indice: Int) {
        composeRule.onAllNodesWithText("Quitar", useUnmergedTree = true)[indice].performClick()
    }

    // ── La etiqueta que el dueño pidió reponer ────────────────────────────────

    @Test
    fun `cada suscripcion activa dice de donde salio`() {
        composeRule.onNodeWithText("Suscripción · la encontró Movi y la activó sola", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithText("Suscripción · la encontró Movi", useUnmergedTree = true)
            .assertExists()
        // La del dueño no dice que la encontró nadie.
        composeRule.onNodeWithText("Suscripción", useUnmergedTree = true).assertExists()
    }

    /** En SU moneda, sin convertir: solo el «Flujo libre» de arriba pasa por la TRM. */
    @Test
    fun `el monto va en la moneda de la suscripcion`() {
        composeRule.onNodeWithText("−US$12", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("−$22.900", useUnmergedTree = true).assertExists()
    }

    // ── Las dos ramas de «Quitar» ─────────────────────────────────────────────

    /**
     * Una que encontró el detector se marca DISMISSED y NO se borra: DISMISSED es el «no me la
     * propongas más» que respeta el re-scan. Borrarla haría que el próximo barrido la volviera a
     * proponer.
     */
    @Test
    fun `quitar una que encontro el detector la marca descartada`() {
        quitarDeLaFila(2) // Netflix, día 20

        esperarQueDesaparezca("Netflix")
        assertNull("no se puede borrar lo que el detector va a volver a proponer", borrada)
        assertEquals("s_netflix", actualizada?.id)
        assertEquals(SubStatus.DISMISSED, actualizada?.status)
    }

    /**
     * Una que escribió el dueño se BORRA. Marcarla DISMISSED la dejaba en un limbo: invisible en
     * la lista, imposible de recuperar, y todavía chocando con el alta si volvía a contratar el
     * servicio.
     */
    @Test
    fun `quitar una que escribio el dueno la borra`() {
        quitarDeLaFila(0) // Claude, día 3

        esperarQueDesaparezca("Claude")
        assertEquals("s_claude", borrada)
        assertNull("una manual no deja rastro DISMISSED", actualizada)
    }

    /** La AUTO también se puede sacar — es la que más falta hacía: nadie la aprobó nunca. */
    @Test
    fun `quitar la que se activo sola la marca descartada`() {
        quitarDeLaFila(1) // YouTube, día 12

        esperarQueDesaparezca("YouTube")
        assertEquals("s_youtube", actualizada?.id)
        assertEquals(SubStatus.DISMISSED, actualizada?.status)
        assertNull(borrada)
    }
}
