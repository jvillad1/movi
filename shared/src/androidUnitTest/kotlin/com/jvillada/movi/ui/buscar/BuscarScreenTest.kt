package com.jvillada.movi.ui.buscar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.BusquedasRecientesStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Ola 5 · «Buscar», montada de verdad
 *
 * Escribir muestra los resultados agrupados; tocar uno navega a su lugar y anota la búsqueda en las
 * recientes; las recientes se ven con el campo vacío y se van con el cierre de sesión.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class BuscarScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val rappi = FinancialEvent(
        id = "r1", accountId = "a1", type = TransactionType.EXPENSE, amount = 27_500L, category = "Comida",
        description = "Rappi", merchant = "RAPPI", timestamp = 1_790_000_000_000L,
    )
    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000L)

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 25)
        override suspend fun getEventsByDay(): List<EventDay> = listOf(EventDay(date = "2026-09-21", total = -27_500L, items = listOf(rappi)))
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<DestinoConocido> = listOf(DestinoConocido(id = "d1", nombre = "Caro", numero = "31973270756"))
        override suspend fun getCategories(): List<CategoryUsage> = listOf(CategoryUsage(name = "Comida"))
    }

    private val navegaciones = mutableListOf<Screen>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        BusquedasRecientesStore.almacenDePrueba = mutableMapOf()
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { BuscarScreen(onNavigate = { navegaciones += it }) } }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun escribir(texto: String) {
        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSCAR).performTextReplacement(texto)
        composeRule.waitForIdle()
    }

    @Test
    fun `escribir muestra los resultados agrupados por tipo`() {
        montar()
        escribir("rappi")
        composeRule.waitUntil(5_000) { conTag(TAG_RESULTADO_DE_BUSCAR + "mov:r1") > 0 }

        assertTrue(hay("Movimientos"))
        assertEquals(1, conTag(TAG_RESULTADO_DE_BUSCAR + "mov:r1"))

        // Un número busca el monto exacto.
        escribir("27500")
        assertEquals(1, conTag(TAG_RESULTADO_DE_BUSCAR + "mov:r1"))

        // Personas, categorías y cuentas, cada una en su grupo.
        escribir("caro")
        assertTrue(hay(PERSONAS_Y_COMERCIOS))
        escribir("comida")
        assertTrue(hay("Categorías"))
        escribir("bancolombia")
        assertTrue(hay("Cuentas"))
    }

    @Test
    fun `tocar una pantalla navega a ella y anota la busqueda en las recientes`() {
        montar()
        escribir("captura")
        composeRule.waitUntil(5_000) { conTag(TAG_RESULTADO_DE_BUSCAR + "pan:Captura del banco") > 0 }

        composeRule.onNodeWithTag(TAG_RESULTADO_DE_BUSCAR + "pan:Captura del banco", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<Screen>(Screen.CapturaDelBanco), navegaciones)
        assertEquals(listOf("captura"), BusquedasRecientesStore.recientes)
    }

    @Test
    fun `tocar una cuenta y una persona lleva a su detalle`() {
        montar()
        escribir("caro")
        composeRule.waitUntil(5_000) { conTag(TAG_RESULTADO_DE_BUSCAR + "ter:d1") > 0 }
        composeRule.onNodeWithTag(TAG_RESULTADO_DE_BUSCAR + "ter:d1", useUnmergedTree = true).performClick()
        escribir("ahorros")
        composeRule.waitUntil(5_000) { conTag(TAG_RESULTADO_DE_BUSCAR + "cta:a1") > 0 }
        composeRule.onNodeWithTag(TAG_RESULTADO_DE_BUSCAR + "cta:a1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(Screen.Destinos(abrir = "d1"), Screen.AccountDetail("a1", com.jvillada.movi.shared.model.AccountGroup.DINERO)),
            navegaciones,
        )
    }

    @Test
    fun `con el campo vacio se ven las recientes, y tocarlas vuelve a buscar`() {
        montar()
        BusquedasRecientesStore.anotar("rappi")
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { conTag(TAG_RECIENTE_DE_BUSCAR + "rappi") > 0 }

        composeRule.onNodeWithTag(TAG_RECIENTE_DE_BUSCAR + "rappi", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) { conTag(TAG_RESULTADO_DE_BUSCAR + "mov:r1") > 0 }
    }

    @Test
    fun `las recientes son ocho como mucho, sin repetirse, y se van con el cierre de sesion`() {
        BusquedasRecientesStore.almacenDePrueba = mutableMapOf()
        (1..10).forEach { BusquedasRecientesStore.anotar("busqueda $it") }
        BusquedasRecientesStore.anotar("BUSQUEDA 10")

        assertEquals(8, BusquedasRecientesStore.recientes.size)
        assertEquals("BUSQUEDA 10", BusquedasRecientesStore.recientes.first())
        assertEquals(1, BusquedasRecientesStore.recientes.count { it.equals("busqueda 10", ignoreCase = true) })
        assertEquals(BusquedasRecientesStore.recientes, BusquedasRecientesStore.leer(), "se guardan en el aparato")

        SessionManager.clear()

        assertTrue(BusquedasRecientesStore.recientes.isEmpty())
        assertTrue(BusquedasRecientesStore.leer().isEmpty(), "y no queda nada guardado")
    }
}
