package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.porrevisar.TAG_RENGLON_POR_REVISAR
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Entre cuentas» es un modo claro (vuelta de diseño 37)
 *
 * Desde Patrimonio → Herramientas → «Movimientos entre cuentas», Movimientos se abría con ningún chip
 * marcado, la franja Entró · Salió · Neto de **todo** el período, la tarjeta «N por revisar» y un
 * encabezado «Entre cuentas · Ver todos». Ahora el modo es un chip marcado «Entre cuentas ×», y lo de
 * arriba que habla de todo el período no va. Al quitarlo con la ×, vuelve todo: así se sabe que la
 * prueba no pasa solo porque no había nada que mostrar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class EntreCuentasEsUnModoClaroTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 853_037L, "COP")
    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val hoyIso = epochMillisToAppDate(ahora).toString()

    private val panaderia = FinancialEvent(
        id = "ev-pan", accountId = banco.id, type = TransactionType.EXPENSE, amount = 50_000L,
        category = "Comida", description = "Panadería", timestamp = ahora - 1_000L,
        source = EventSource.MANUAL, reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    /** El pago de tarjeta suelto: entra en «Entre cuentas» sin necesitar la otra pata. */
    private val pagoDeTarjeta = FinancialEvent(
        id = "ev-pago", accountId = banco.id, type = TransactionType.EXPENSE, amount = 115_113L,
        category = CARD_PAYMENT_CATEGORY, description = "Pago de Nu Tarjeta", timestamp = ahora - 2_000L,
        source = EventSource.MANUAL, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private fun montar() {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePruebaDeMovimientos() {
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(
                EventDay(date = hoyIso, total = -165_113L, items = listOf(panaderia, pagoDeTarjeta)),
            )
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            // Un aviso pendiente: fuera del modo, la tarjeta «1 por revisar» aparece.
            override suspend fun getSmsMessages(): List<SmsMessage> = listOf(
                SmsMessage(
                    id = "s1", time = "2026-09-03 07:15", bank = "Bancolombia",
                    text = "Compra aprobada \$28.500 en Uber BV.", state = SMS_STATE_PENDING, det = "Uber",
                ),
            )
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}, chipInicial = CHIP_ENTRE_CUENTAS) }
            }
        }
        esperar("Nu Tarjeta")
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun esperar(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto) }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun cuantos(tag: String): Int =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `en el modo el chip esta marcado y no hay franja ni por revisar`() {
        montar()

        // El chip del modo dice dónde está uno y se puede quitar.
        val chip = composeRule.onAllNodes(hasTestTag(TAG_CHIP_DEL_MODO), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, chip.size, "un chip «Entre cuentas ×»")
        assertEquals(
            listOf("Salir de Entre cuentas"),
            chip.single().config.getOrNull(SemanticsProperties.ContentDescription),
        )
        assertTrue(hay("Entre cuentas"))
        // La lista es la del modo: el pago de tarjeta sí, la panadería no.
        assertTrue(!hay("Panadería"), "la panadería no es entre cuentas")
        // Lo que habla de todo el período no va.
        assertEquals(0, cuantos(TAG_RESUMEN_EN_EL_TELEFONO), "la franja Entró · Salió · Neto no va en el modo")
        assertEquals(0, cuantos(TAG_RENGLON_POR_REVISAR), "«por revisar» no va en el modo")
        assertEquals(0, cuantos(TAG_TARJETA_DE_AVISOS), "ni la tarjeta de los avisos")
        assertTrue(!hay("Entró"), "ninguna cifra de todo el período")
        assertTrue(!hay("por revisar"))
        // Y el «Ver todos» del encabezado viejo se fue.
        assertTrue(!hay("Ver todos"))
    }

    @Test
    fun `la x del chip sale del modo y vuelve lo de siempre`() {
        montar()

        composeRule.onNode(hasTestTag(TAG_CHIP_DEL_MODO), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        esperar("Panadería")
        // Vuelta 40: con avisos del banco va su tarjeta; sin ellos, el renglón «N por revisar».
        composeRule.waitUntil(timeoutMillis = 5_000) { cuantos(TAG_RENGLON_POR_REVISAR) + cuantos(TAG_TARJETA_DE_AVISOS) > 0 }

        assertEquals(0, cuantos(TAG_CHIP_DEL_MODO), "fuera del modo no hay chip del modo")
        assertEquals(1, cuantos(TAG_RESUMEN_EN_EL_TELEFONO), "la franja vuelve en «Todo»")
        assertEquals(1, cuantos(TAG_RENGLON_POR_REVISAR) + cuantos(TAG_TARJETA_DE_AVISOS), "«por revisar» vuelve en «Todo»")
    }
}
