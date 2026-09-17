package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Volver a tocar «Guardar» después de un fallo no anota el movimiento dos veces
 *
 * El escenario, que es el de todos los días con señal mala: el POST **llega** al server y la
 * respuesta se pierde —se cortó la red, la app se fue al fondo, venció el timeout—. El dueño lee
 * «revisa tu conexión» y vuelve a tocar Guardar, porque eso es lo que dice el cartel.
 *
 * `POST /api/events` tiene una sola defensa contra ese duplicado: **el id**. Un reenvío con el
 * mismo id se trata como «este movimiento ya está» y se contesta 200 en vez de insertar otro. Con
 * el `newId("ev")` adentro de `save()`, cada toque acuñaba un id nuevo, así que esa defensa no
 * tenía de dónde agarrarse y quedaban dos gastos idénticos.
 *
 * Es el mismo arreglo que `TransferForm` ya tenía (`TransferDraftIds`), traído a esta hoja.
 *
 * Y de paso se fija lo otro que decidía mal la misma función: **la moneda del rótulo**.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD_REINTENTO)
class HojaAgregarReintentoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val masterBlack = Account(
        "c-usd", "Master Black", AccountType.CREDIT_CARD, 0L, currency = "USD",
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    /**
     * El doble de prueba se comporta como el server que se comió la respuesta: el primer POST
     * falla, el segundo anda. Lo que se afirma es que los **dos** llevaron el mismo id.
     */
    @Test
    fun el_reintento_manda_el_mismo_id_que_el_intento_que_fallo() {
        val idsQueLlegaron = mutableListOf<String>()
        montarHoja(listOf(ahorros)) { evento ->
            idsQueLlegaron += evento.id
            if (idsQueLlegaron.size == 1) throw IllegalStateException("se cortó la red")
            evento
        }

        tocar("5")
        tocar("000")
        tocar("0")
        tocar("Guardar movimiento")
        tocar("Guardar movimiento")

        assertEquals(2, idsQueLlegaron.size, "se intentó dos veces")
        assertEquals(
            idsQueLlegaron[0], idsQueLlegaron[1],
            "el reintento tiene que ser el MISMO pedido, o el server no puede reconocerlo",
        )
    }

    /**
     * **El rótulo de la moneda dice la de la cuenta elegida.** Con la Master Black puesta, esta
     * hoja decía «$500.000 · COP» y guardaba US$500.000: el número era el mismo, la plata no.
     */
    @Test
    fun con_una_cuenta_en_dolares_la_hoja_dice_USD_y_no_COP() {
        montarHoja(listOf(masterBlack))

        composeRule.onNodeWithText("USD").assertIsDisplayed()
        composeRule.onNodeWithText("COP").assertDoesNotExist()
    }

    @Test
    fun con_una_cuenta_en_pesos_sigue_diciendo_COP() {
        montarHoja(listOf(ahorros))

        composeRule.onNodeWithText("COP").assertIsDisplayed()
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private fun montarHoja(
        cuentas: List<Account>,
        alGuardar: ((FinancialEvent) -> FinancialEvent)? = null,
    ) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = cuentas
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent =
                alGuardar?.invoke(event) ?: super.postEvent(event)
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        QuickAddScreen(onDismiss = {})
                    }
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Ver la nota de [HojaAgregarEligeLaCuentaTest]: bajo Robolectric `performClick` no llega. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}

/** El AVD `Movi_Sensor`, igual que las otras pruebas de esta hoja. */
private const val TELEFONO_DEL_AVD_REINTENTO = "w411dp-h731dp-xhdpi"
