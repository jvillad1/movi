package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
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
 * # Task 7 en Cuentas: filas esqueleto, no una rueda, en la primera carga
 *
 * [puerta] mantiene `getAccounts()` colgada, así que la pantalla queda en su estado de "cargando,
 * sin una sola cuenta pintada" mientras la prueba mira. El esqueleto tiene un pulso infinito
 * (`rememberInfiniteTransition`, ver el KDoc de `Esqueleto.kt`): nunca queda "idle", así que estas
 * pruebas nunca llaman `waitForIdle()` — miden el frame inicial y, para el segundo estado, esperan
 * con `waitUntil` (que sondea, no espera "idle").
 */
@RunWith(RobolectricTestRunner::class)
class EsqueletoDeCuentasTest {

    @get:Rule val composeRule = createComposeRule()

    private val puerta = CompletableDeferred<List<Account>>()

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `sin una cuenta pintada todavia, se ven 6 filas esqueleto y no la rueda`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = puerta.await()
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AccountsScreen(onNavigate = {}) } }
        }
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(6, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)
    }

    @Test
    fun `al llegar las cuentas, el esqueleto se va y quedan las filas reales`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = puerta.await()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AccountsScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()
        assertEquals(6, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)

        puerta.complete(listOf(Account(id = "a1", name = "Nu", type = AccountType.SAVINGS, balance = 558_350L)))
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Nu", useUnmergedTree = true).assertIsDisplayed()
    }
}
