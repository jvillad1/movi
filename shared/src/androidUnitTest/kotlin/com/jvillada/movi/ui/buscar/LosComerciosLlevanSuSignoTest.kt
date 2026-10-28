package com.jvillada.movi.ui.buscar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.BusquedasRecientesStore
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.claveDeComercio
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.quickadd.todayIsoInAppZone
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Vuelta de diseño 30: en Buscar, «Comercios · Uber $40.100» iba sin signo mientras los movimientos
 * de arriba llevaban «−». Ahora el total del comercio es la `Cifra` con el tono y el signo del sistema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class LosComerciosLlevanSuSignoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahora = Clock.System.now().toEpochMilliseconds()
    private fun uber(id: String, monto: Long) = FinancialEvent(
        id = id, accountId = "a1", type = TransactionType.EXPENSE, amount = monto, category = "Transporte",
        description = "Uber", merchant = "UBER", timestamp = ahora - monto, countsAsCashFlow = true,
    )
    private val viajes = listOf(uber("u1", 25_000), uber("u2", 15_100))

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `el total de un comercio lleva el signo de un gasto`() {
        BusquedasRecientesStore.almacenDePrueba = mutableMapOf()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 1)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(EventDay(todayIsoInAppZone(), -40_100L, viajes))
            override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Nu", AccountType.SAVINGS, 0L))
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { BuscarScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSCAR).performTextReplacement("uber")
        val tag = TAG_RESULTADO_DE_BUSCAR + "com:${claveDeComercio(viajes.first())}"
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(
            composeRule.onAllNodes(hasText("−\$40.100") and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
            "«−$40.100», como los movimientos",
        )
    }
}
