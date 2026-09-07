package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Las cuotas pagadas de los créditos, en Movimientos y montado de verdad.**
 *
 * El dueño: *«en recurrentes no estoy viendo los pagos de cuota realizados para mis créditos,
 * considero que esto es importante verlo porque me permite entender mi flujo de caja mensual»*.
 * Sus ocho créditos son ~$15.500.000 mensuales de su bolsillo: es lo más grande de su flujo de
 * caja, y no aparecía.
 *
 * Lo que una función pura no alcanza a probar y esta prueba sí:
 *
 * 1. Que con el chip «Recurrentes» la cuota se **pinte**, y como UNA fila (la del dinero), no dos.
 * 2. Que en «Todo» —donde las dos patas sí están y se pliegan en un solo renglón— ese renglón
 *    lleve la misma marca de repetición, en vez de leerse distinto según el filtro.
 * 3. Que el **pago de una tarjeta**, que tiene exactamente la misma forma, siga sin marcarse ni
 *    entrar al chip. Esa es la parte que cuesta plata si se rompe: las compras ya contaron cuando
 *    se hicieron.
 * 4. Que el card de «Flujo libre» diga que ese total todavía no las cuenta.
 *
 * Mismo patrón de montaje que [ResumenRecurrentesEnMovimientosTest] y
 * [SuscripcionesActivasEnMovimientosTest], y con la ventana alta de esta última por el mismo
 * motivo: el card de «Flujo libre» va al final del chip y en 731dp queda bajo el pliegue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class CuotasRecurrentesEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 8_000_000L, "COP")
    private val vehiculo = Account("acc-carro", "Vehículo", AccountType.LOAN, -60_000_000L, "COP")
    private val nubank = Account("acc-nu", "Nubank", AccountType.CREDIT_CARD, -1_200_000L, "COP")

    private fun ev(
        id: String,
        cuenta: Account,
        type: TransactionType,
        category: String,
        description: String,
        amount: Long,
        transferId: String,
    ) = FinancialEvent(
        id = id,
        accountId = cuenta.id,
        type = type,
        amount = amount,
        category = category,
        description = description,
        timestamp = 1_756_684_800_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        transferId = transferId,
        countsAsCashFlow = isCashFlow(cuenta.type, type, category),
    )

    /**
     * Las dos patas de la cuota del carro, tal como las escribe `pagoDeCuotaLegs`: el monto
     * completo sale de la cuenta, y a la deuda solo abona el capital.
     */
    private val cuotaDinero = ev(
        "ev-cuota-dinero", bancolombia, TransactionType.EXPENSE, CUOTA_CATEGORY,
        "Cuota de Vehículo", 4_215_223L, "tr-cuota",
    )
    private val cuotaDeuda = ev(
        "ev-cuota-deuda", vehiculo, TransactionType.INCOME, CUOTA_CATEGORY,
        "Abono a capital desde Bancolombia", 1_733_905L, "tr-cuota",
    )

    /** Las dos patas del pago de la tarjeta: la misma forma, y NO es un gasto. */
    private val tarjetaDinero = ev(
        "ev-tarjeta-dinero", bancolombia, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY,
        "Pago de Nubank", 1_200_000L, "tr-tarjeta",
    )
    private val tarjetaDeuda = ev(
        "ev-tarjeta-deuda", nubank, TransactionType.INCOME, CARD_PAYMENT_CATEGORY,
        "Pago desde Bancolombia", 1_200_000L, "tr-tarjeta",
    )

    private val dia = EventDay(
        date = "2026-09-01",
        total = -4_215_223L,
        items = listOf(cuotaDinero, cuotaDeuda, tarjetaDinero, tarjetaDeuda),
    )

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia, vehiculo, nubank)
        override suspend fun getEventsByDay(): List<EventDay> = listOf(dia)
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        // Vacías a propósito: la cuota NO se reconoce por nombre —la regla de un crédito la
        // fabrica el server al vuelo y nunca llega acá—, así que si alguien la "arreglara" con un
        // match por nombre, esta prueba se pondría roja.
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)
    }

    @Before
    fun montar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        // Chip «Todo» de arranque: el par de la cuota ya plegado en un solo renglón.
        esperarTexto("Cuota de crédito")
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

    private fun marcasDeRecurrente() =
        composeRule.onAllNodesWithContentDescription("Recurrente", useUnmergedTree = true)

    // ── Chip «Todo»: los dos pares plegados, y solo uno marcado ──────────────

    /**
     * En «Todo» las dos patas están, así que `collapseTransfers` pliega cada par en UN renglón.
     * Ese renglón —el de la cuota— lleva la marca; el del pago de tarjeta no. Una sola marca en
     * pantalla es la aserción que distingue las dos cosas.
     */
    @Test
    fun `en Todo el par de la cuota va marcado y el pago de tarjeta no`() {
        composeRule.onNodeWithText("Cuota de crédito", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Pago de tarjeta", useUnmergedTree = true).assertExists()
        marcasDeRecurrente().assertCountEquals(1)
    }

    // ── Chip «Recurrentes»: lo que el dueño vino a ver ───────────────────────

    /**
     * Con el filtro puesto pasa **solo la pata del dinero**, así que la cuota se ve suelta, con el
     * concepto que ya nombra el crédito y por el monto que de verdad salió de la cuenta. La pata
     * de la deuda («Abono a capital desde…») no está: con las dos, cada cuota ocuparía dos filas
     * en la lista que él lee justamente para sumar lo que le sale al mes.
     */
    @Test
    fun `el chip Recurrentes muestra la cuota una sola vez, con el nombre del credito`() {
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("Cuota de Vehículo")

        composeRule.onAllNodesWithText("Cuota de Vehículo", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Abono a capital desde Bancolombia", useUnmergedTree = true)
            .assertCountEquals(0)
        // Por el monto completo de la cuota (la plata que salió), no por el capital que abonó.
        // Por subcadena para no depender de qué signo «menos» exacto usa `formatCOP`.
        //
        // Son DOS nodos y las dos veces es la misma cuota: la fila, y el «Flujo del día» del
        // encabezado, que con este filtro puesto vale exactamente eso. Que coincidan es la
        // aserción: si la pata de la deuda también hubiera pasado, el total del día ya no diría
        // lo mismo que la única fila de abajo.
        composeRule.onAllNodesWithText("4.215.223", substring = true, useUnmergedTree = true)
            .assertCountEquals(2)
    }

    /** Y el pago de la tarjeta no se cuela por la puerta nueva. */
    @Test
    fun `el chip Recurrentes deja afuera el pago de tarjeta`() {
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("Cuota de Vehículo")

        composeRule.onAllNodesWithText("Pago de Nubank", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Pago de tarjeta", useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * **Y el total de arriba dice que no las cuenta.** «Flujo libre» es
     * `resumenRecurrentes(reglas, suscripciones)` y las reglas de los créditos no son filas de la
     * tabla, así que la lista muestra plata que ese número no suma. Si eso debería cambiar es una
     * decisión del dueño; que la pantalla lo calle mientras tanto, no.
     */
    @Test
    fun `el card de Flujo libre avisa que las cuotas no entran en ese total`() {
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("Flujo libre")

        composeRule.onNodeWithText(
            "Las cuotas de tus créditos aparecen en la lista de abajo, pero todavía no entran en este total.",
            useUnmergedTree = true,
        ).assertExists()
    }
}
