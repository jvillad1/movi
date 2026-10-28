package com.jvillada.movi.ui.accounts

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.Disposicion
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.components.disposicionDe
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El detalle de una cuenta en dos columnas (vuelta 33), dentro de la cáscara y medido
 *
 * A 1.280 px era una columna de lectura de 720 dp con aire a los lados. Ahora la cuenta (el saldo
 * con su cuadre) va a la izquierda y sus movimientos a la derecha; a 390 dp, una lista como siempre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LaCuentaEnDosColumnasTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L, balancesByCurrency = mapOf("COP" to 558_350L))
    private val almuerzo = FinancialEvent(
        id = "e1", accountId = nu.id, type = TransactionType.EXPENSE, amount = 42_000L, category = "Comida",
        description = "Almuerzo con el equipo", timestamp = Clock.System.now().toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccount(id: String): Account = nu
            override suspend fun getEvents(accountId: String?): List<FinancialEvent> = listOf(almuerzo)
            override suspend fun getApartados(): List<Apartado> = emptyList()
        }
        val pantalla = Screen.AccountDetail(nu.id, AccountGroup.DINERO)
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = pantalla,
                    activeTab = NavTab.PATRIMONIO,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    AccountDetailScreen(onNavigate = {}, accountId = nu.id, group = AccountGroup.DINERO)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Almuerzo con el equipo", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hayTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun limites(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun limitesDelTexto(texto: String): Rect =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

    @Test
    fun `el detalle de una cuenta es un tablero`() {
        assertEquals(Disposicion.Tablero, disposicionDe(Screen.AccountDetail("acc-nu", AccountGroup.DINERO)))
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 dp la cuenta va a la izquierda y sus movimientos a la derecha`() {
        montar()
        val cuenta = limites(TAG_COLUMNA_DE_LA_CUENTA)
        val movimientos = limites(TAG_COLUMNA_DE_LOS_MOVIMIENTOS_DE_LA_CUENTA)
        assertTrue(movimientos.left >= cuenta.right - 0.5f, "$cuenta / $movimientos")
        assertEquals(cuenta.width, movimientos.width, 0.5f)
        assertEquals(1_064f, cuenta.width + movimientos.width, 0.5f, "llenan el panel (1280 − 216 del rail)")
        val encabezado = limites(TAG_ENCABEZADO_DE_LA_CUENTA)
        assertTrue(encabezado.right <= cuenta.right + 0.5f, "el saldo va a la izquierda: $encabezado")
        val fila = limitesDelTexto("Almuerzo con el equipo")
        assertTrue(fila.left >= movimientos.left, "el movimiento va a la derecha: $fila")
        // «Movimientos» de la columna (el rail también dice «Movimientos»): arriba, a la altura del saldo.
        val titulo = composeRule.onAllNodesWithText("Movimientos", useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.single { it.left >= movimientos.left - 0.5f }
        assertTrue(titulo.top < encabezado.bottom, "las dos columnas arrancan arriba: $titulo / $encabezado")
    }

    @Test
    @Config(qualifiers = "w390dp-h900dp-mdpi")
    fun `a 390 dp una sola lista, como siempre`() {
        montar()
        assertTrue(!hayTag(TAG_COLUMNA_DE_LA_CUENTA) && !hayTag(TAG_COLUMNA_DE_LOS_MOVIMIENTOS_DE_LA_CUENTA))
        assertTrue(limitesDelTexto("Almuerzo con el equipo").top > limites(TAG_ENCABEZADO_DE_LA_CUENTA).bottom, "los movimientos, debajo del saldo")
    }
}
