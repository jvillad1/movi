package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * # Aprobar un aviso sin anotarlo dos veces
 *
 * El 25-sep el dueño aprobó los dos avisos del mismo pago con la Glim —el de Google Wallet y el de
 * la app de Glim— con seis segundos de diferencia. El «¿Ya lo anotaste?» solo aparecía si la lectura
 * de coincidencias ya había llegado, y el botón de confirmar ya estaba prendido antes: un toque
 * rápido se lo saltaba. Estas pruebas montan la pantalla de verdad con un repositorio que tarda.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// `sdk = [34]` y el motor de texto real: en el SDK por defecto un texto con `lineHeight` mide ancho
// cero y cuenta como «no se ve». Alta para que la columna perezosa componga todo.
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class ReconciliarSinDuplicarTest {

    @get:Rule val composeRule = createComposeRule()

    private val glim = Account("g1", "Glim Alimentación 3037", AccountType.SAVINGS, 180_000)

    private val deGlim = SmsMessage(
        id = "g",
        time = "2026-09-25 09:15",
        bank = "Notificación · Glim",
        text = "¡Usaste tus beneficios!: Pagaste \$15.100,00 COP con tu tarjeta de beneficios Glim " +
            "el 25/09/2026 a las 14:15 en TOSTAO CAFE Y PAN.",
        state = SMS_STATE_PENDING,
        det = "",
    )
    private val deWallet = SmsMessage(
        id = "w",
        time = "2026-09-25 09:15",
        bank = "Notificación · Google Wallet",
        text = "TOSTAO CAFE Y PAN VISC: COP15,100 with Glim ••3037",
        state = SMS_STATE_CONFIRMED,
        det = "",
    )

    private val publicados = mutableListOf<FinancialEvent>()

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private inner class Repo(
        private val sms: SmsMessage = deGlim,
        private val coincidencias: suspend (Int) -> List<FinancialEvent> = { emptyList() },
    ) : RepositorioDePrueba() {
        var lecturasDeCoincidencias = 0
        override suspend fun getAccounts(): List<Account> = listOf(glim)
        override suspend fun getSms(id: String): SmsMessage = if (id == deWallet.id) deWallet else sms
        override suspend fun parseSms(id: String): ParsedSms =
            ParsedSms(15_100.0, "TOSTAO CAFE Y PAN", TransactionType.EXPENSE, "Comida")
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> =
            coincidencias(++lecturasDeCoincidencias)
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
        override suspend fun confirmSms(id: String) {}
    }

    // ── El botón espera la revisión ─────────────────────────────────────────────

    @Test
    fun confirmar_queda_apagado_hasta_que_contesta_la_revision() {
        val respuesta = CompletableDeferred<List<FinancialEvent>>()
        montar(Repo(coincidencias = { respuesta.await() }))

        composeRule.onNodeWithText("Revisando si ya está anotado…").assertIsDisplayed()
        composeRule.onNodeWithText("Confirmar").assertIsNotEnabled()
        tocar("Confirmar")
        assertEquals(0, publicados.size, "se anotó antes de revisar si ya estaba")

        respuesta.complete(emptyList())
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Revisando si ya está anotado…").assertDoesNotExist()
        composeRule.onNodeWithText("Confirmar").assertIsEnabled()
        tocar("Confirmar")
        assertEquals(1, publicados.size)
    }

    @Test
    fun si_la_revision_falla_se_dice_y_hace_falta_un_segundo_toque() {
        montar(Repo(coincidencias = { error("sin señal") }))

        composeRule.onNodeWithText("No pudimos revisar si ya estaba anotado").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").assertIsDisplayed()

        tocar("Confirmar")
        assertEquals(0, publicados.size, "un solo toque anotó sin haber revisado")
        tocar("Anotar de todas formas")
        assertEquals(1, publicados.size)
    }

    @Test
    fun reintentar_con_exito_vuelve_al_camino_de_siempre() {
        val repo = Repo(coincidencias = { vez -> if (vez == 1) error("sin señal") else emptyList() })
        montar(repo)

        tocar("Reintentar")

        assertEquals(2, repo.lecturasDeCoincidencias)
        composeRule.onNodeWithText("No pudimos revisar si ya estaba anotado").assertDoesNotExist()
        tocar("Confirmar")
        assertEquals(1, publicados.size)
    }

    @Test
    fun con_coincidencias_se_ofrece_como_siempre() {
        val anotado = FinancialEvent(
            id = "ev1", accountId = glim.id, type = TransactionType.EXPENSE, amount = 15_100,
            category = "Comida", description = "Tostao", timestamp = 1_790_000_000_000L,
        )
        montar(Repo(coincidencias = { listOf(anotado) }))

        // El encabezado va en versales («¿YA LO ANOTASTE?»): se busca la frase de abajo.
        composeRule.onNodeWithText("Encontramos un movimiento igual", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Es este").assertIsDisplayed()
    }

    // ── El aviso del mismo pago ─────────────────────────────────────────────────

    @Test
    fun arriba_dice_que_parece_el_mismo_pago_que_el_otro_aviso() {
        montar(Repo(sms = deGlim.copy(parecidoA = deWallet.id)))

        composeRule.onNodeWithText("Parece el mismo pago que el aviso de Google Wallet de las 9:15 a. m.")
            .assertIsDisplayed()
    }

    @Test
    fun sin_parecido_no_dice_nada() {
        montar(Repo())
        composeRule.onNodeWithText("Parece el mismo pago", substring = true).assertDoesNotExist()
    }

    @Test
    fun la_fila_de_por_revisar_lo_dice_tambien() {
        composeRule.setContent {
            MoviTheme {
                TarjetaDeMensajeDelBanco(deGlim.copy(parecidoA = deWallet.id), parecidoA = deWallet, onRevisar = {})
            }
        }
        composeRule.onNodeWithText("Parece el mismo pago que el aviso de Google Wallet de las 9:15 a. m.")
            .assertIsDisplayed()
    }

    // ── El comercio se puede corregir ───────────────────────────────────────────

    @Test
    fun el_comercio_editado_es_el_que_se_guarda() {
        montar(Repo())

        composeRule.onNode(hasSetTextAction()).performTextReplacement("Tostao")
        composeRule.waitForIdle()
        tocar("Confirmar")

        assertEquals(1, publicados.size)
        assertEquals("Tostao", publicados.single().description)
        assertEquals("Tostao", publicados.single().merchant)
        // El monto lo dice el banco, y no se toca.
        assertEquals(15_100L, publicados.single().amount)
    }

    @Test
    fun un_comercio_vacio_vuelve_al_leido() {
        montar(Repo())

        composeRule.onNode(hasSetTextAction()).performTextReplacement("   ")
        composeRule.waitForIdle()
        tocar("Confirmar")

        assertEquals("TOSTAO CAFE Y PAN", publicados.single().description)
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────

    private fun montar(repo: Repo) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = deGlim.id) }
            }
        }
        composeRule.waitForIdle()
    }

    /** `performSemanticsAction`: bajo Robolectric `performClick` no llega al composable. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
