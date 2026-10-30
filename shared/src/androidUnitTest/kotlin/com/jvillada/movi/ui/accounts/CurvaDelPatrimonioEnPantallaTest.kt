package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.HistoriaDelPatrimonio
import com.jvillada.movi.shared.model.PuntoDelPatrimonio
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La curva del patrimonio en pantalla (Ola 4 · Ver la plata)
 *
 * Con historia, Patrimonio pinta la curva debajo de la cifra, dice de dónde a dónde fue y, con plata
 * en dólares, que se cuenta con la TRM de hoy. Sin historia (o con un solo punto, hoy), no pinta
 * nada y la tarjeta sigue entera; si la lectura falla, tampoco se rompe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp-xhdpi")
class CurvaDelPatrimonioEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 9_050_000L)

    private val historia = HistoriaDelPatrimonio(
        puntos = listOf(
            PuntoDelPatrimonio("2026-08", "2026-08-31", 4_050_000L),
            PuntoDelPatrimonio("2026-09", "2026-09-30", 4_300_000L),
            PuntoDelPatrimonio("2026-10", "2026-10-20", 9_050_000L, esHoy = true),
        ),
        conLaTrmDeHoy = true,
        // Vuelta 60: «desde que usas Movi» sale del primer movimiento real, no de la curva.
        primerMovimiento = "2026-08-03",
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(leer: suspend () -> HistoriaDelPatrimonio) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(nu)
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun getHistoriaDelPatrimonio(): HistoriaDelPatrimonio = leer()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AccountsScreen(onNavigate = {}) } } }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TAG_TARJETA_DEL_PATRIMONIO).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun curvas() = composeRule.onAllNodesWithTag(TAG_CURVA_DEL_PATRIMONIO, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `con historia pinta la curva, de donde a donde fue y la TRM de hoy`() {
        montar { historia }
        composeRule.waitUntil(timeoutMillis = 5_000) { curvas() == 1 }
        // El primer punto es el mes del primer movimiento real: la curva arranca con el dueño.
        assertTrue(hay("Desde que usas Movi (ago 2026), subió"))
        assertTrue(hay(CON_LA_TRM_DE_HOY))
    }

    /** Vuelta 60: el ajuste de este mes se marca en hoy y la nota dice cuánto son ajustes. */
    @Test
    fun `un ajuste de este mes se marca en hoy y la nota lo explica`() {
        montar {
            historia.copy(
                puntos = historia.puntos.dropLast(1) +
                    historia.puntos.last().copy(neto = 4_300_000L - 34_600_000L, ajustes = -35_500_000L),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { curvas() == 1 }
        val descripcion = composeRule.onNodeWithTag(TAG_CURVA_DEL_PATRIMONIO, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue(HOY_INCLUYE_UN_AJUSTE in descripcion, descripcion)
        assertTrue(hay("Sin contar −\$35,5M de ajustes de saldo"))
    }

    @Test
    fun `sin historia no hay curva y la tarjeta sigue entera`() {
        montar { HistoriaDelPatrimonio() }
        assertEquals(0, curvas())
        assertTrue(hay("Patrimonio neto"))
        assertTrue(!hay(CON_LA_TRM_DE_HOY))
    }

    @Test
    fun `con un solo punto (hoy) no hay curva`() {
        montar { HistoriaDelPatrimonio(puntos = listOf(historia.puntos.last())) }
        assertEquals(0, curvas())
    }

    @Test
    fun `si la historia no se puede leer, Patrimonio no se rompe`() {
        montar { error("sin red") }
        assertEquals(0, curvas())
        assertTrue(hay("Patrimonio neto"))
        assertTrue(hay("Nu"))
    }
}
