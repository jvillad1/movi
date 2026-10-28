package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.LecturaParaReconciliar
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * # «Anotarlo igual» en Reconciliar
 *
 * El 20-oct-2026 un aviso de Nu que Movi no entendía dejó Reconciliar con el botón apagado y solo
 * «Ignorar». Ahora, si el server encontró un monto, el botón principal es «Anotarlo igual»: abre el
 * formulario con el monto puesto, el dueño elige gasto o ingreso y la categoría, y confirmar va por el
 * mismo camino que cualquier movimiento suelto de un aviso (`postEvent` y `confirmSmsCon`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// `sdk = [34]` y el motor de texto real (ver `ReconciliarSinDuplicarTest`); alta para que la columna
// perezosa componga todo.
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class AnotarloIgualEnReconciliarTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("nu", "Nu", AccountType.SAVINGS, 500_000)
    private val motivo = "Este mensaje no trae un movimiento para anotar. Puedes ignorarlo."

    private val aviso = SmsMessage(
        id = "notif_nu",
        time = "2026-10-20 09:41",
        bank = "Notificación · Nu",
        text = "Nu: Movimos \$1.000,00 desde tu cuenta el 20/10/2026 a las 09:41. Si no reconoces este movimiento, escríbenos.",
        state = SMS_STATE_PENDING,
        det = "",
    )
    private val sinLeer = AvisoSinLeer(
        monto = 1_000.0,
        moneda = "COP",
        tipo = TransactionType.EXPENSE,
        momento = momentoDelSms(aviso.time, ahora = System.currentTimeMillis()),
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private inner class Repo(
        private val sms: SmsMessage = aviso,
        private val lectura: LecturaParaReconciliar = LecturaParaReconciliar(noSePudoLeer = motivo, sinLeer = sinLeer),
    ) : RepositorioDePrueba() {
        val publicados = mutableListOf<FinancialEvent>()
        val confirmados = mutableListOf<String>()
        val ignorados = mutableListOf<String>()
        override suspend fun getAccounts(): List<Account> = listOf(nu)
        override suspend fun getSms(id: String): SmsMessage = sms
        override suspend fun leerParaReconciliar(id: String): LecturaParaReconciliar = lectura
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
        override suspend fun confirmSms(id: String) { confirmados += id }
        override suspend fun ignoreSms(id: String) { ignorados += id }
    }

    @Test
    fun aparece_anotarlo_igual_con_el_monto_que_trae() {
        montar(Repo())

        composeRule.onNodeWithText("Movi no entendió este aviso, pero trae \$1.000. Puedes anotarlo igual.").assertIsDisplayed()
        composeRule.onNodeWithText(ANOTARLO_IGUAL).assertIsEnabled()
        composeRule.onNodeWithText("Ignorar").assertIsEnabled()
        composeRule.onNodeWithText("Confirmar").assertDoesNotExist()
    }

    @Test
    fun anotarlo_igual_abre_el_formulario_con_el_monto_puesto_y_gasto_por_defecto() {
        montar(Repo())

        tocar(ANOTARLO_IGUAL)

        composeRule.onNodeWithTag(TAG_MONTO_DE_ANOTARLO_IGUAL).assertExists()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_MONTO_DE_ANOTARLO_IGUAL)))
            .assertTextContains("1.000")
        composeRule.onNodeWithText("−\$1.000").assertIsDisplayed()
        // Sin categoría propuesta, confirmar espera a que elija una.
        composeRule.onNodeWithText("Confirmar").assertIsNotEnabled()
    }

    @Test
    fun confirmar_va_por_el_mismo_camino_que_un_aviso_leido() {
        val repo = Repo()
        montar(repo)

        tocar(ANOTARLO_IGUAL)
        // Vuelta 42: las pastillas salen al tocar «Cambiar» en la fila «Categoría».
        tocarTag(tagDeFilaDelSms("Categoría"))
        tocarTag(tagDePastillaDeCategoriaDelSms("Comida"))
        tocar("Confirmar")

        val evento = repo.publicados.single()
        assertEquals(1_000L, evento.amount)
        assertEquals("COP", evento.currency)
        assertEquals(TransactionType.EXPENSE, evento.type)
        assertEquals("Comida", evento.category)
        assertEquals(nu.id, evento.accountId)
        assertEquals(EventSource.SMS, evento.source)
        assertEquals(aviso.text, evento.rawPayload)
        assertEquals(sinLeer.momento, evento.timestamp, "la fecha es la del aviso")
        // El aviso se confirma con ese movimiento: `confirmSmsCon` → `confirmSms`, como siempre.
        assertEquals(listOf(aviso.id), repo.confirmados)
    }

    @Test
    fun el_monto_se_corrige_y_el_tipo_se_elige() {
        val repo = Repo()
        montar(repo)

        tocar(ANOTARLO_IGUAL)
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_MONTO_DE_ANOTARLO_IGUAL)))
            .performTextReplacement("1200")
        composeRule.waitForIdle()
        tocarTag(tagDelTipoDeAnotarloIgual(TransactionType.INCOME))
        tocarTag(tagDeFilaDelSms("Categoría"))
        tocarTag(tagDePastillaDeCategoriaDelSms("Salario"))
        tocar("Confirmar")

        val evento = repo.publicados.single()
        assertEquals(1_200L, evento.amount)
        assertEquals(TransactionType.INCOME, evento.type)
        assertEquals("Salario", evento.category)
    }

    @Test
    fun la_categoria_que_recuerda_movi_viene_puesta() {
        val repo = Repo(lectura = LecturaParaReconciliar(noSePudoLeer = motivo, sinLeer = sinLeer.copy(comercio = "Carolina R.", categoria = "Familia")))
        montar(repo)

        tocar(ANOTARLO_IGUAL)
        tocar("Confirmar")

        assertEquals("Familia", repo.publicados.single().category)
        assertEquals("Carolina R.", repo.publicados.single().description)
    }

    @Test
    fun sin_monto_todo_queda_como_antes() {
        montar(Repo(lectura = LecturaParaReconciliar(noSePudoLeer = motivo)))

        composeRule.onNodeWithText(motivo).assertIsDisplayed()
        composeRule.onNodeWithText(ANOTARLO_IGUAL).assertDoesNotExist()
        composeRule.onNodeWithText("Confirmar").assertIsNotEnabled()
        composeRule.onNodeWithText("Ignorar").assertIsEnabled()
    }

    @Test
    fun un_aviso_apartado_no_lo_ofrece() {
        // El server no manda `sinLeer` para lo apartado; y aunque lo mandara, el aviso ya no espera nada.
        val apartado = aviso.copy(
            text = "Nu: Aprovecha 20% de dcto en tu próxima compra de \$100.000,00 hasta el 31/10/2026. Aplican TyC.",
            state = SMS_STATE_IGNORED,
            apartadoPor = "PROMOCION",
        )
        montar(Repo(sms = apartado, lectura = LecturaParaReconciliar(noSePudoLeer = motivo, sinLeer = sinLeer)))

        composeRule.onNodeWithText(ANOTARLO_IGUAL).assertDoesNotExist()
    }

    @Test
    fun ignorar_sigue_ahi() {
        val repo = Repo()
        montar(repo)

        tocar("Ignorar")

        assertEquals(listOf(aviso.id), repo.ignorados)
        assertEquals(0, repo.publicados.size)
    }

    @Test
    fun un_aviso_leido_sigue_con_confirmar() {
        val leido = ParsedSms(15_100.0, "TOSTAO CAFE Y PAN", TransactionType.EXPENSE, "Comida")
        montar(Repo(lectura = LecturaParaReconciliar(leido = leido)))

        composeRule.onNodeWithText(ANOTARLO_IGUAL).assertDoesNotExist()
        composeRule.onNodeWithText("Confirmar").assertIsEnabled()
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────

    private fun montar(repo: Repo) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = aviso.id) }
            }
        }
        composeRule.waitForIdle()
    }

    /** `performSemanticsAction`: bajo Robolectric `performClick` no llega al composable. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocarTag(tag: String) {
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
