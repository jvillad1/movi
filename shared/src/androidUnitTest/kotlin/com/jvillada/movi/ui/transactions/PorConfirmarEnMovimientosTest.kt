package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # «Por confirmar» sale de la fila de filtros, montado de verdad
 *
 * El dueño: *«Por confirmar debería saltar en otro lugar no acá en esta misma vista»*. Ahora es un
 * aviso arriba de la lista que **solo existe cuando hay algo que confirmar**.
 *
 * Se monta la pantalla dos veces con repositorios distintos —uno con un SMS sin confirmar y otro
 * sin nada pendiente— porque el punto entero del cambio es que en el segundo caso **no ocupe
 * espacio**, y eso no se puede afirmar con un solo montaje.
 *
 * Fecha vieja y de otro año para que el encabezado no dependa del reloj.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_POR_CONFIRMAR)
class PorConfirmarEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 853_037L, "COP")

    private fun evento(id: String, descripcion: String, estado: ReconciliationStatus, fuente: EventSource) =
        FinancialEvent(
            id = id,
            accountId = banco.id,
            type = TransactionType.EXPENSE,
            amount = 18_500L,
            category = "Comida",
            description = descripcion,
            timestamp = 1_710_500_000_000L,
            source = fuente,
            reconciliationStatus = estado,
            countsAsCashFlow = true,
        )

    private val aMano = evento("e-mano", "Carnes y Legumbres Santa Elena", ReconciliationStatus.RECONCILED, EventSource.MANUAL)
    private val porSms = evento("e-sms", "Compra Exito", ReconciliationStatus.UNCONFIRMED, EventSource.SMS)

    private fun montar(items: List<FinancialEvent>, esperar: String, chipInicial: Int? = null) {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> =
                listOf(EventDay(date = "2024-03-15", total = -18_500L, items = items))
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}, chipInicial = chipInicial) }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(esperar, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    @Test
    fun `el chip ya no esta en la fila de filtros`() {
        montar(listOf(aMano), esperar = "Carnes y Legumbres")

        // Los que sí son formas de mirar los movimientos siguen ahí.
        composeRule.onNodeWithText("Todo", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Gastos", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ingresos", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `sin nada pendiente el aviso no ocupa espacio`() {
        // El caso normal de quien anota todo a mano, y el punto entero del cambio.
        montar(listOf(aMano), esperar = "Carnes y Legumbres")

        composeRule.onNodeWithText("entró solo", substring = true, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("faltan confirmar", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `con algo pendiente el aviso lo dice, y al tocarlo se entra a la bandeja`() {
        montar(listOf(aMano, porSms), esperar = "entró solo")

        composeRule.onNodeWithText("1 movimiento entró solo y falta confirmarlo", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithText("1 movimiento entró solo y falta confirmarlo", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        // Adentro: solo lo pendiente, el encabezado que dice dónde está, y la salida.
        composeRule.onNodeWithText("Compra Exito", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Carnes y Legumbres Santa Elena", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Por confirmar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ver todos", useUnmergedTree = true).assertIsDisplayed()
        // Y el aviso ya no: sería un botón que lleva a donde uno ya está.
        composeRule.onNodeWithText("entró solo", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * **El vacío de la bandeja sigue existiendo y sigue importando**, aunque ya no se llegue por un
     * chip: confirmando el último pendiente la lista se queda vacía justo debajo de los dedos, y
     * ahí «Sin movimientos aún · + Registrar el primero» mentiría dos veces (sí hay movimientos, y
     * registrar uno nuevo no tiene nada que ver con confirmar los que entraron solos). El dueño lo
     * leyó exactamente así: *«¿Qué es Por confirmar?»*.
     *
     * Se entra por `chipInicial`, que es la misma puerta que abre el aviso.
     */
    @Test
    fun `la bandeja vacia lo dice, y no ofrece registrar`() {
        montar(listOf(aMano), esperar = "Nada por confirmar", chipInicial = CHIP_POR_CONFIRMAR)

        composeRule.onNodeWithText("Nada por confirmar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("lo registraste tú", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("+ Registrar el primero", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Sin movimientos aún", useUnmergedTree = true).assertDoesNotExist()
        // Y el movimiento anotado a mano tampoco está: el filtro lo dejó afuera.
        composeRule.onNodeWithText("Carnes y Legumbres Santa Elena", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Ver todos devuelve la lista completa`() {
        montar(listOf(aMano, porSms), esperar = "entró solo")
        composeRule.onNodeWithText("1 movimiento entró solo y falta confirmarlo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Ver todos", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Carnes y Legumbres Santa Elena", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Compra Exito", useUnmergedTree = true).assertIsDisplayed()
    }
}

/** El mismo tamaño de pantalla que usan las otras pruebas de esta carpeta (privado por archivo). */
private const val AVD_POR_CONFIRMAR = "w411dp-h731dp-xhdpi"
