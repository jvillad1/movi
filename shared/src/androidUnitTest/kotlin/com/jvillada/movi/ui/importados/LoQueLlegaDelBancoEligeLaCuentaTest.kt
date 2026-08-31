package com.jvillada.movi.ui.importados

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.extractos.StatementReviewScreen
import com.jvillada.movi.ui.sms.SMSReconcileScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Lo que llegó del banco, contra qué cuenta se anota — desde las pantallas de verdad
 *
 * `CuentaDelBancoTest` prueba la regla y `ListaDeCuentasElegiblesTest` prueba el selector.
 * Faltaba el pedazo del medio, que es el que de verdad falla: **que estas dos pantallas pidan la
 * partición correcta y ofrezcan el camino para corregirla**. Es el mismo hueco que
 * `HojaAgregarEligeLaCuentaTest` vino a tapar en la hoja de «Agregar», por el mismo motivo: este
 * repo ya tuvo una feature entera viviendo en una rama que ningún call site alcanzaba,
 * compilando, con su prueba en verde.
 *
 * El defecto concreto que se fija acá: las dos resolvían la cuenta con una cadena que terminaba
 * en `accounts.firstOrNull()` —la primera del abecedario, que en las cuentas del dueño puede ser
 * un crédito ya desembolsado— y ninguna dejaba corregirla con el dedo.
 *
 * Lo que NO cubre: es Robolectric, así que no dice nada de iOS ni de la web, y nada horizontal es
 * confiable. Lo confiable es qué se ve, qué dice, y qué pasa al tocarlo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LoQueLlegaDelBancoEligeLaCuentaTest {

    @get:Rule val composeRule = createComposeRule()

    // Ordenadas por nombre, como las devuelve `GET /api/accounts`. La AMEX va primera a propósito:
    // es lo que agarraba `firstOrNull()`.
    private val amex = Account("c2", "AMEX 9208", AccountType.CREDIT_CARD, 19_818_701)
    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val carro = Account("l1", "Vehículo 4083", AccountType.LOAN, 177_200_000)

    private val todas = listOf(amex, ahorros, carro)

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    // ── El SMS ──────────────────────────────────────────────────────────────────

    /**
     * **El respaldo peligroso, visto desde la pantalla.** Con solo el crédito del vehículo en la
     * lista, `accounts.firstOrNull()` ponía «Vehículo 4083» como origen del gasto y la pantalla lo
     * mostraba como un hecho, de solo lectura. Ahora no se resuelve nada y la fila lo dice.
     */
    @Test
    fun un_sms_sin_ninguna_cuenta_que_sirva_no_cae_en_el_credito() {
        montarSms(cuentas = listOf(carro))

        composeRule.onNodeWithText("Sin cuenta elegida").assertIsDisplayed()
        composeRule.onNodeWithText("Elígela tú").assertIsDisplayed()
        composeRule.onNodeWithText("Vehículo 4083").assertDoesNotExist()
    }

    /** Y desde ahí hay salida: el selector se abre con el dedo, y el crédito está a un toque más. */
    @Test
    fun desde_un_sms_sin_cuenta_se_puede_elegir_una_con_el_dedo() {
        montarSms(cuentas = todas)

        tocar("Cambiar")
        tocar("AMEX 9208")

        composeRule.onNodeWithText("AMEX 9208").assertIsDisplayed()
        // Elegida a mano: ya no hay nada que confesar.
        composeRule.onNodeWithText("La puso Movi").assertDoesNotExist()
    }

    /**
     * **El criterio llega al selector del SMS.** Un gasto sale del banco o de la tarjeta, nunca de
     * un crédito ya desembolsado — que sigue estando a un toque, porque esto no es un filtro duro.
     */
    @Test
    fun el_selector_del_sms_no_ofrece_el_credito_pero_no_lo_esconde() {
        montarSms(cuentas = todas)

        tocar("Cambiar")

        // La AMEX y no la cuenta de ahorros: esa ya está arriba, en el resumen, y buscarla por
        // texto encontraría dos nodos. La tarjeta solo puede estar en la lista.
        composeRule.onNodeWithText("AMEX 9208").assertIsDisplayed()
        composeRule.onNodeWithText("Vehículo 4083").assertDoesNotExist()

        tocar("Ver todas las cuentas (1 más)")
        // Desplegado, el crédito queda debajo del pliegue en una pantalla de 731 dp: se llega
        // desplazando, que es exactamente lo que haría el dueño. `assertExists()` a secas daría
        // verde también con una fila inalcanzable.
        composeRule.onNodeWithText("Vehículo 4083").performScrollTo().assertIsDisplayed()
    }

    /** Cuando la cuenta la puso Movi por descarte, la pantalla lo dice antes de guardar. */
    @Test
    fun un_sms_de_un_banco_desconocido_avisa_que_la_cuenta_la_puso_movi() {
        montarSms(cuentas = todas, banco = "Falabella")

        composeRule.onNodeWithText("La puso Movi").assertIsDisplayed()
    }

    // ── El extracto ─────────────────────────────────────────────────────────────

    @Test
    fun un_extracto_sin_ninguna_cuenta_que_sirva_pide_que_la_elijas() {
        montarExtracto(cuentas = listOf(carro))

        composeRule.onNodeWithText("Elige la cuenta").assertIsDisplayed()
        composeRule.onNodeWithText("Vehículo 4083").assertDoesNotExist()
    }

    @Test
    fun el_destino_del_extracto_se_puede_cambiar_con_el_dedo() {
        montarExtracto(cuentas = todas, banco = "Bancolombia")

        composeRule.onNodeWithText("Bancolombia Ahorros").assertIsDisplayed()

        tocar("Cambiar")
        tocar("AMEX 9208")

        composeRule.onNodeWithText("AMEX 9208").assertIsDisplayed()
    }

    /**
     * **El uso del extracto no es el del gasto.** Si acá se hubiera reusado `ORIGEN_DE_GASTO`, el
     * efectivo estaría en la lista corta —y nadie recibe un PDF de lo que tiene en el bolsillo—,
     * mientras que la pensión voluntaria, que sí manda extracto, caería detrás del «Ver todas».
     */
    @Test
    fun el_selector_del_extracto_usa_su_propio_criterio() {
        val efectivo = Account("a0", "Efectivo", AccountType.CASH, 200_000)
        val skandia = Account(
            "i1", "Pensión voluntaria Skandia", AccountType.INVESTMENT, 106_000_000,
            condicionadaA = "Vivienda",
        )
        montarExtracto(cuentas = listOf(ahorros, efectivo, skandia), banco = "Bancolombia")

        tocar("Cambiar")

        composeRule.onNodeWithText("Pensión voluntaria Skandia").assertIsDisplayed()
        composeRule.onNodeWithText("Efectivo").assertDoesNotExist()
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────

    private fun montarSms(cuentas: List<Account>, banco: String = "Bancolombia") {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = cuentas
            override suspend fun getSms(id: String): SmsMessage = SmsMessage(
                id = id,
                time = "10:24",
                bank = banco,
                text = "Compra por \$85.000 en EXITO",
                state = SMS_STATE_PENDING,
                det = "",
            )
            override suspend fun parseSms(id: String): ParsedSms = ParsedSms(
                amount = 85_000.0,
                merchant = "EXITO",
                type = TransactionType.EXPENSE,
                category = "Mercado",
            )
        }
        montar { SMSReconcileScreen(onNavigate = {}, smsId = "sms_1") }
    }

    private fun montarExtracto(cuentas: List<Account>, banco: String = "Bancolombia") {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = cuentas
        }
        val result = StatementParseResult(
            statementId = "st_1",
            bankName = banco,
            period = "Mayo 2026",
            newTransactions = emptyList(),
            matches = emptyList(),
        )
        montar { StatementReviewScreen(onNavigate = {}, result = result) }
    }

    private fun montar(pantalla: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { pantalla() }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * `performSemanticsAction` y no `performClick`: bajo Robolectric el click no llega al
     * composable —no falla, simplemente no pasa nada—, así que una prueba escrita con
     * `performClick` sería verde sin haber probado nada.
     */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
