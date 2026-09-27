package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import com.jvillada.movi.ui.components.TAG_BUSCAR_CATEGORIA
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * # Ola Q — al abrir el selector de categoría en Reconciliar, la pantalla se desplaza sola
 *
 * El dueño (27-sep, con captura): al tocar «Cambiar» en Categoría, el selector completo
 * (buscador + cuadrícula) se desplegaba DEBAJO de la fila, empujando el resto de la pantalla
 * fuera de lo visible sin que nada se desplazara para mostrarlo — tenía que darse cuenta y bajar
 * él mismo.
 *
 * Ventana angosta y baja (390×653, una de las más chicas entre los simuladores de este repo) a
 * propósito: con todo lo que va antes en esta pantalla (SMS recibido, sugerencia de Movi, la
 * tarjeta «Confirma o ajusta»), el buscador del selector queda debajo del pliegue si nada
 * scrollea para mostrarlo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h653dp-xhdpi")
class ReconciliarMovimientoScrollAlAbrirSelectorTest {

    @get:Rule val composeRule = createComposeRule()

    private val cuenta = Account(id = "acc1", name = "Bancolombia", type = AccountType.CHECKING, balance = 500_000L)

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSms(id: String): SmsMessage = SmsMessage(
            id = id,
            time = "2026-09-27 10:00",
            bank = "Bancolombia",
            text = "Bancolombia le informa compra por \$45.000 en EXITO",
            state = SMS_STATE_PENDING,
            det = "sms",
        )

        override suspend fun parseSms(id: String): ParsedSms = ParsedSms(
            amount = 45_000.0,
            merchant = "Exito",
            type = TransactionType.EXPENSE,
            category = "Mercado",
        )

        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()

        override suspend fun getAccounts(): List<Account> = listOf(cuenta)
    }

    @After
    fun tearDown() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SMSReconcileScreen(onNavigate = {}, smsId = "sms1")
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun el_buscador_del_selector_queda_a_la_vista_al_tocar_cambiar() {
        montar()

        // Antes de tocar «Cambiar», el selector ni siquiera existe.
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertDoesNotExist()

        composeRule.onNodeWithTag(tagDeFilaDelSms("Categoría")).performClick()
        composeRule.waitForIdle()

        // Sin `performScrollTo`: si la pantalla no se hubiera desplazado sola, en 653 dp de alto
        // el buscador habría quedado debajo del pliegue, tapado por el recorte de la lista.
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertIsDisplayed()
    }
}
