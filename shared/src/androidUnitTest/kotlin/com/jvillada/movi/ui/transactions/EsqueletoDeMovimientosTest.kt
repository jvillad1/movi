package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * # Task 7 en Movimientos: filas esqueleto, no una rueda, en la primera carga
 *
 * Mismo mecanismo que `EsqueletoDeCuentasTest`: [puerta] mantiene `getEventsByDay()` colgada.
 * `getUserProfile()` está resuelta (no es lo que esta prueba mira, y sin ella el snackbar de
 * «no pudimos leer tu período» taparía lo que se está probando); `getAccounts()`,
 * `getCardPaymentCandidates()` y `getMovimientosRechazados()` son lecturas secundarias que
 * `RepositorioDePrueba` u observa el propio código de la pantalla como opcionales.
 */
@RunWith(RobolectricTestRunner::class)
class EsqueletoDeMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val puerta = CompletableDeferred<List<EventDay>>()

    private fun repositorio() = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")
        override suspend fun getEventsByDay(): List<EventDay> = puerta.await()
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `sin un dia pintado todavia, se ven 6 filas esqueleto y no la rueda`() {
        Repositories.sustitutoDePrueba = repositorio()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(6, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)
    }

    @Test
    fun `al llegar los movimientos, el esqueleto se va y queda la fila real`() {
        Repositories.sustitutoDePrueba = repositorio()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()
        assertEquals(6, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)

        val evento = FinancialEvent(
            id = "e1",
            accountId = "a1",
            type = TransactionType.EXPENSE,
            amount = 25_000L,
            category = "Comida",
            description = "Almuerzo",
            timestamp = 1_758_326_400_000L, // 2026-09-20 00:00:00 UTC
        )
        puerta.complete(listOf(EventDay(date = "2026-09-20", total = 25_000L, items = listOf(evento))))
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Almuerzo", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * **Fix round 1, punto 2.** Con «Recurrentes» la lista de días nunca se pinta (ni cargada ni
     * cargando: [mostrarLaListaDeDias] la apaga para ese chip), así que las filas esqueleto de
     * arriba tampoco aparecen ahí — y sin este arreglo la primera carga de ese chip se quedaba
     * SIN NINGUNA señal de que algo estaba en camino. La barra de progreso vuelve a cubrir ese
     * caso: `loading && (visibleDays.isNotEmpty() || !hayListaDeDias)`.
     */
    @Test
    fun `con Recurrentes, sin dia pintado todavia, la barra de carga esta pero no las filas esqueleto`() {
        Repositories.sustitutoDePrueba = repositorio()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}, chipInicial = CHIP_RECURRENTES) } }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_BARRA_DE_CARGA_DE_MOVIMIENTOS).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)
    }
}
