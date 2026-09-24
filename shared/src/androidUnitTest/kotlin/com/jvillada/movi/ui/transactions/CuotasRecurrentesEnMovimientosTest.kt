package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.plan.TableroDeRecurrentes
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Las cuotas pagadas de los créditos, montadas de verdad: en Movimientos y en el tablero.**
 *
 * El dueño: *«en recurrentes no estoy viendo los pagos de cuota realizados para mis créditos,
 * considero que esto es importante verlo porque me permite entender mi flujo de caja mensual»*.
 * Sus ocho créditos son ~$15.500.000 mensuales de su bolsillo: es lo más grande de su flujo de
 * caja, y no aparecía.
 *
 * Lo que una función pura no alcanza a probar y esta prueba sí:
 *
 * 1. Que en Movimientos —donde las dos patas de cada par están y se pliegan en un solo renglón—
 *    la cuota y el **pago de una tarjeta** lleven la marca de repetición, y que el título los
 *    distinga. Ola C: el chip «Recurrentes» se fue a Plan, pero esta marca se queda.
 * 2. Que el card de «Flujo libre» del tablero (Plan · Pagos del mes) cuente las cuotas —el dueño lo
 *    decidió después del PR anterior— y que diga cuánto, con qué queda afuera y por qué.
 *
 * (Hasta la ola C también probaba el chip «Recurrentes» de Movimientos: una fila por cuota y el pago
 * de la tarjeta sin signo. El chip ya no existe; qué pata se reconoce lo sigue fijando
 * `NombreRecurrenteDeTest`, y el tono de un pago de tarjeta, `ColorYEntreCuentasTest`.)
 *
 * Ventana alta, como `SuscripcionesActivasEnElTableroTest`: el card de «Flujo libre» va al final
 * del tablero y en 731dp queda bajo el pliegue.
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

    // Whole-branch review, final fix wave: HOY, no un "2026-09-01" fijo — con corte 1 (esta
    // clase no manda perfil, y `getUserProfile()` falla adrede, ver `Repo`), `diasDelPeriodo`
    // filtra por el mes de CALENDARIO de verdad, así que una fecha fija deja de estar en el
    // período apenas cambia el mes de la máquina que corre la prueba. Mismo patrón que
    // `PorConfirmarEnMovimientosTest.HOY_ISO`.
    private val dia = EventDay(
        date = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString(),
        total = -4_215_223L,
        items = listOf(cuotaDinero, cuotaDeuda, tarjetaDinero, tarjetaDeuda),
    )

    /**
     * Las reglas sintéticas de sus créditos, tal como se las manda el server por
     * `/api/payments/upcoming`: la del carro (una cuota de verdad) y la del techo, que es un pago
     * único de $10.000.000 a un mes.
     */
    private val reglaDelCarro = RecurringRule(
        id = "credit_acc-carro", name = "Cuota Vehículo", category = "Créditos",
        amount = 4_215_223L, dayOfMonth = 1, type = TransactionType.EXPENSE,
    )
    private val reglaDelTecho = RecurringRule(
        id = "credit_acc-techo", name = "Cuota Crédito Techo Gardenera", category = "Créditos",
        amount = 10_000_000L, dayOfMonth = 1, type = TransactionType.EXPENSE, esPagoUnico = true,
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

        // La única puerta por la que las cuotas llegan al cliente. Sin esto el card no muestra
        // ninguna cifra (a propósito: ver el `takeIf { vencimientosOk }` de la pantalla).
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> =
            listOf(reglaDelCarro, reglaDelTecho).map {
                UpcomingPayment(
                    rule = it, dueDate = "2026-10-01", daysUntil = 25,
                    status = PaymentStatus.UPCOMING,
                )
            }

        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
    }

    private fun montarMovimientos() {
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        // Chip «Todo» de arranque: el par de la cuota ya plegado en un solo renglón.
        esperarTexto("Cuota de crédito")
    }

    /** El tablero de Plan · Pagos del mes, solo: ahí vive el card de «Flujo libre» desde la ola C. */
    private fun montarTablero() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TableroDeRecurrentes(ajustesDelPeriodo = PeriodSettings(), onNavigate = {})
                }
            }
        }
        esperarTexto("Flujo libre")
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

    // ── Chip «Todo»: los dos pares plegados, los dos marcados ────────────────

    /**
     * En «Todo» las dos patas de cada par están, así que `collapseTransfers` pliega cada par en UN
     * renglón. Los dos renglones llevan la marca —son plata que sale todos los meses— y lo que los
     * distingue es el título que ya tenían: «Cuota de crédito» y «Pago de tarjeta». Dos marcas y
     * **dos títulos distintos** es la aserción: el ícono no borra la diferencia, la dice el rótulo.
     */
    @Test
    fun `en Todo los dos pares van marcados, y el titulo los distingue`() {
        montarMovimientos()
        composeRule.onNodeWithText("Cuota de crédito", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Pago de tarjeta", useUnmergedTree = true).assertExists()
        marcasDeRecurrente().assertCountEquals(2)
    }

    // ── El tablero (Plan · Pagos del mes): el card de «Flujo libre» ───────────

    /**
     * **Y el total de arriba las cuenta.** El PR anterior dejó acá una línea que admitía que
     * «Flujo libre» no las sumaba; el dueño decidió que sí deben sumar, así que esa línea sería
     * hoy una mentira y esta prueba fija que no está.
     *
     * El total es la cuota del carro y nada más: la del techo es un pago único de $10.000.000 y
     * no entra. Que el número sea $4.215.223 y no $14.215.223 es la aserción.
     */
    @Test
    fun `el card de Flujo libre cuenta las cuotas y dice cuanto`() {
        montarTablero()

        composeRule.onAllNodesWithText(
            "Las cuotas de tus créditos entran en este total: $4.215.223 al mes.",
            substring = true, useUnmergedTree = true,
        ).assertCountEquals(1)
        // La confesión del PR anterior ya no puede estar en ninguna forma.
        composeRule.onAllNodesWithText(
            "todavía no entran en este total", substring = true, useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    /** Y el pago único queda afuera **diciéndolo**: es plata que vence, solo que una sola vez. */
    @Test
    fun `el card explica el credito que se paga de una sola vez`() {
        montarTablero()

        composeRule.onAllNodesWithText(
            "Un crédito tuyo se paga de una sola vez", substring = true, useUnmergedTree = true,
        ).assertCountEquals(1)
        // Los $10.000.000 del techo no están en ninguna cifra del card.
        composeRule.onAllNodesWithText("14.215.223", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }
}
