package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MovimientoRechazado
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Lo que el server rechazó quedaba solo en el teléfono, reintentándose en silencio: se veía en
 * Movimientos pero nunca llegaba al Inicio ni a la web. Ahora Movimientos lo dice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class AvisoDeLoQueNoSubioTest {

    @get:Rule val composeRule = createComposeRule()

    private fun rechazado(id: String, nombre: String) = MovimientoRechazado(
        FinancialEvent(id = id, accountId = "a", type = TransactionType.EXPENSE, amount = 9_000, category = "Comida", description = nombre, timestamp = 0),
        "Esa cuenta ya no existe.",
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `el texto nombra el movimiento y el motivo`() {
        assertNull(textoDeRechazados(emptyList()))
        assertEquals(
            "«Almuerzo» no se pudo subir: Esa cuenta ya no existe. Solo está en este teléfono; corrígelo o anúlalo.",
            textoDeRechazados(listOf(rechazado("1", "Almuerzo"))),
        )
        assertEquals(
            "2 movimientos no se pudieron subir: Esa cuenta ya no existe. Solo está en este teléfono; corrígelo o anúlalo.",
            textoDeRechazados(listOf(rechazado("1", "Almuerzo"), rechazado("2", "Taxi"))),
        )
    }

    @Test
    fun `Movimientos muestra el aviso`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getMovimientosRechazados(): List<MovimientoRechazado> = listOf(rechazado("1", "Almuerzo"))
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("«Almuerzo» no se pudo subir", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
