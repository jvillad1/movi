package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.LocalAvisoDeDeshacer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 74 · en Revisar aviso, «Es este» es el botón amarillo
 *
 * Antes, con el gasto ya anotado, arriba salía «¿Ya lo anotaste? · Es este» como pastilla con borde y
 * el amarillo de abajo seguía siendo «Confirmar», que crea otro. Ahora el amarillo es «Es este» (no
 * crea nada), y crear otro pasa a «Anotarlo como nuevo», secundario y con una línea que lo dice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class EsEsteEnRevisarAvisoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc-4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 5_000_000L)
    private val crepes = SmsMessage(
        id = "crepes", time = "2026-10-29 13:05", bank = "85540",
        text = "Bancolombia: Compraste \$32.000,00 en CREPES Y WAFFLES OVIEDO con tu T.Deb *4057, el 29/10/2026 a las 13:05.",
        state = SMS_STATE_PENDING, det = "",
    )
    private val restaurantes = FinancialEvent(
        id = "ev-restaurantes", accountId = ahorros.id, type = TransactionType.EXPENSE, amount = 32_000L,
        category = "Restaurantes", description = "Restaurantes", timestamp = 1_793_300_000_000L,
    )

    private val publicados = mutableListOf<FinancialEvent>()
    private val confirmados = mutableListOf<Pair<String, String>>()
    private val aviso = AvisoDeDeshacer()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(coincidencias: List<FinancialEvent>) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            override suspend fun getSms(id: String): SmsMessage = crepes
            override suspend fun parseSms(id: String): ParsedSms =
                ParsedSms(32_000.0, "CREPES Y WAFFLES OVIEDO", TransactionType.EXPENSE, "Comida")
            override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = coincidencias
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
            override suspend fun confirmSmsCon(id: String, eventoId: String) {
                confirmados += id to eventoId
            }
        }
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalAvisoDeDeshacer provides aviso) {
                    Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = crepes.id) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `con un anotado, Es este es el principal y Anotarlo como nuevo el secundario`() {
        montar(listOf(restaurantes))

        composeRule.onNodeWithTag(TAG_ES_ESTE_EN_RECONCILIAR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ANOTARLO_COMO_NUEVO, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_ANOTARLO_COMO_NUEVO_CREA_OTRO).assertTextEquals(ANOTARLO_COMO_NUEVO_CREA_OTRO)
        // Un solo «Es este»: el de abajo. La fila de la lista ya no lleva el suyo.
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_ES_ESTE_EN_RECONCILIAR, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("$TAG_ES_ESTE_EN_RECONCILIAR:0").fetchSemanticsNodes().size)
    }

    @Test
    fun `Es este vincula sin crear y deja el aviso de abajo`() {
        montar(listOf(restaurantes))

        composeRule.onNodeWithTag(TAG_ES_ESTE_EN_RECONCILIAR).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { confirmados.isNotEmpty() }
        composeRule.waitForIdle()

        assertEquals(listOf("crepes" to restaurantes.id), confirmados)
        assertTrue(publicados.isEmpty(), "«Es este» no crea un movimiento")
        assertEquals("Anotado · Restaurantes \$32.000", aviso.actual?.texto)
        assertNotNull(aviso.actual?.deshacer, "con «Deshacer»")
    }

    @Test
    fun `con dos anotados, cada fila lleva su Es este y abajo no hay amarillo`() {
        montar(listOf(restaurantes, restaurantes.copy(id = "ev-2", description = "Almuerzo")))

        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ES_ESTE_EN_RECONCILIAR).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("$TAG_ES_ESTE_EN_RECONCILIAR:0").assertExists()
        composeRule.onNodeWithTag("$TAG_ES_ESTE_EN_RECONCILIAR:1").assertExists()
        composeRule.onNodeWithTag(TAG_ANOTARLO_COMO_NUEVO_CREA_OTRO).assertExists()
    }

    @Test
    fun `sin nada anotado, Confirmar sigue siendo el principal`() {
        montar(emptyList())

        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ES_ESTE_EN_RECONCILIAR).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ANOTARLO_COMO_NUEVO_CREA_OTRO).fetchSemanticsNodes().size)
    }
}
