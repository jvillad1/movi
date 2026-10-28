package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Al volver de Revisar aviso, la bandeja queda donde la dejaste (vuelta 44)
 *
 * Ocho avisos; el dueño baja hasta el quinto, lo abre, lo confirma allá y vuelve. Mientras la bandeja
 * se relee hay un esqueleto de una fila (que antes la mandaba arriba), y el quinto ya no está: la
 * lista vuelve con el sexto arriba, a la vista. Y la regla de a qué fila volver, pura.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class PorRevisarVuelveDondeLaDejasteTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = hoyEnAppZone().toString()
    private val comercios = listOf("ALFA", "BETA", "GAMA", "DELTA", "EPSILON", "ZETA", "ETA", "TETA")

    /** Del más nuevo (a1, 20:00) al más viejo (a8, 13:00): así los pinta la bandeja. */
    private val avisos = comercios.mapIndexed { i, comercio ->
        SmsMessage(
            id = "a${i + 1}", time = "$hoy ${20 - i}:00", bank = "85540", state = SMS_STATE_PENDING, det = "",
            text = "Bancolombia: Compraste \$1${i + 1}.000,00 en $comercio con tu T.Deb *4057.",
        )
    }.toMutableList()

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = avisos.toList()
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(Account("c", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 0L, "COP"))
        override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja = PropuestasDeLaBandeja(
            propuestas = avisos.filter { it.state == SMS_STATE_PENDING }.mapIndexed { _, a ->
                val i = comercios.indexOf(a.text.substringAfter(" en ").substringBefore(" con"))
                PropuestaDelAviso(a.id, leido = ParsedSms((i + 11) * 1_000.0, comercios[i], TransactionType.EXPENSE, "Comida"))
            },
        )
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `al volver con el aviso confirmado, el siguiente queda arriba a la vista`() {
        Repositories.sustitutoDePrueba = Repo()
        var enLaBandeja by mutableStateOf(true)
        composeRule.setContent {
            MoviTheme {
                // Como la pila de la app: la pantalla sale de la composición y su estado guardable queda.
                val guardado = rememberSaveableStateHolder()
                Box(Modifier.fillMaxSize()) {
                    if (enLaBandeja) guardado.SaveableStateProvider("por-revisar") { PorRevisarScreen(onNavigate = {}) }
                }
            }
        }
        composeRule.waitUntil(5_000) { hay("Alfa") }
        composeRule.waitForIdle()

        // Baja hasta el quinto («Epsilon») y lo deja arriba.
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("sms-a5")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Epsilon", useUnmergedTree = true).assertIsDisplayed()

        // Va a Revisar aviso y allá lo confirma.
        enLaBandeja = false
        composeRule.waitForIdle()
        avisos.replaceAll { if (it.id == "a5") it.copy(state = SMS_STATE_CONFIRMED) else it }
        enLaBandeja = true
        composeRule.waitUntil(5_000) { hay("Zeta") }
        composeRule.waitForIdle()

        // El siguiente, a la vista; lo de arriba de la lista, no (no volvió al principio).
        composeRule.onNodeWithText("Zeta", useUnmergedTree = true).assertIsDisplayed()
        val alfa = composeRule.onAllNodesWithText("Alfa", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(true, alfa.isEmpty() || !composeRule.onNodeWithText("Alfa", useUnmergedTree = true).isDisplayed())
        assertEquals(false, hay("Epsilon"))
    }

    @Test
    fun `a que fila volver`() {
        val antes = listOf("sms-a5", "sms-a6", "sms-a7")
        // La primera sigue: a ella, con su corrimiento.
        assertEquals(4 to 30, dondeVolver(antes, 30, listOf("subir", "titulo", "sms-a3", "sms-a4", "sms-a5", "sms-a6")))
        // La primera se fue (se confirmó): la siguiente, arriba.
        assertEquals(3 to 0, dondeVolver(antes, 30, listOf("subir", "titulo", "sms-a4", "sms-a6", "sms-a7")))
        // No queda ninguna, o no había nada que recordar: arriba de todo.
        assertNull(dondeVolver(antes, 30, listOf("subir", "todo-al-dia")))
        assertNull(dondeVolver(emptyList(), 0, listOf("subir")))
    }
}
