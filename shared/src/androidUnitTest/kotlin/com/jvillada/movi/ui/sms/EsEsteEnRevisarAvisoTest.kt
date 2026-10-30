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
 * crea nada), y crear otro pasa a «Anotar aparte», secundario y con una línea que lo dice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
open class EsEsteEnRevisarAvisoTest {

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
    fun `con un anotado, Es este es el principal y Anotar aparte el secundario`() {
        montar(listOf(restaurantes))

        composeRule.onNodeWithTag(TAG_ES_ESTE_EN_RECONCILIAR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ANOTAR_APARTE, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_ANOTAR_APARTE_CREA_OTRO).assertTextEquals(ANOTAR_APARTE_CREA_OTRO)
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
        assertEquals("Ya estaba anotado · Restaurantes \$32.000", aviso.actual?.texto)
        assertNotNull(aviso.actual?.deshacer, "con «Deshacer»")
    }

    @Test
    fun `con dos anotados, cada fila lleva su Es este y abajo no hay amarillo`() {
        montar(listOf(restaurantes, restaurantes.copy(id = "ev-2", description = "Almuerzo")))

        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ES_ESTE_EN_RECONCILIAR).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("$TAG_ES_ESTE_EN_RECONCILIAR:0").assertExists()
        composeRule.onNodeWithTag("$TAG_ES_ESTE_EN_RECONCILIAR:1").assertExists()
        composeRule.onNodeWithTag(TAG_ANOTAR_APARTE_CREA_OTRO).assertExists()
    }

    /** Revisión de la vuelta 74: los dos botones caben enteros, y «Es este» no es más angosto que el otro. */
    @Test
    fun `Es este y Anotar aparte caben enteros`() {
        montar(listOf(restaurantes))

        for (texto in listOf(ES_ESTE, ANOTAR_APARTE)) {
            val resultados = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            composeRule.onNode(androidx.compose.ui.test.hasText(texto), useUnmergedTree = true).fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
            val r = resultados.single()
            assertTrue(r.lineCount == 1 && !r.isLineEllipsized(0), "«$texto» se corta: ${r.lineCount} renglones")
        }
        val esEste = composeRule.onNodeWithTag(TAG_ES_ESTE_EN_RECONCILIAR).fetchSemanticsNode().boundsInRoot.width
        val aparte = composeRule.onNodeWithTag(TAG_ANOTAR_APARTE).fetchSemanticsNode().boundsInRoot.width
        assertTrue(esEste >= aparte, "«Es este» ($esEste) más angosto que «Anotar aparte» ($aparte)")
    }

    @Test
    fun `sin nada anotado, Confirmar sigue siendo el principal`() {
        montar(emptyList())

        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ES_ESTE_EN_RECONCILIAR).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ANOTAR_APARTE_CREA_OTRO).fetchSemanticsNodes().size)
    }
}

/** Lo mismo, a 360 dp: donde «Anotarlo como nuevo» se cortaba. */
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class EsEsteEnRevisarAvisoA360Test : EsEsteEnRevisarAvisoTest()
