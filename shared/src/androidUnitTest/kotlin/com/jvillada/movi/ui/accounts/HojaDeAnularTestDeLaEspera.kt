package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.BUSCANDO_LA_OTRA_MITAD
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.NO_SE_PUDO_LEER_LA_OTRA_MITAD
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Mientras la otra mitad no llegue, la hoja **no finge estar completa**
 *
 * Lo encontró la verificación a ojo en la web local, no una prueba: la sección «AL ANULAR» aparece
 * recién cuando vuelve la lectura de la hermana, y en la web eso tardó del orden de segundos. En esa
 * ventana la hoja se ve **entera** y dice una sola cifra — que es exactamente el defecto que la ola
 * vino a matar, ahora convertido en un problema de tiempo en vez de uno de contenido.
 *
 * Callar mientras se busca era defendible cuando el silencio duraba un parpadeo. Medido, no dura un
 * parpadeo. Así que la hoja lo dice: está buscando, o no pudo.
 *
 * **Los dos estados son distintos y por eso hay dos pruebas.** Con `runCatching{}.getOrNull()` un
 * fallo de red era indistinguible de una lectura que todavía no vuelve: la hoja se habría quedado
 * diciendo «buscando» para siempre, que es una mentira más cara que el silencio.
 *
 * El botón de anular **sigue habilitado** en los dos casos, a propósito: anular cascadea a las dos
 * patas por `transferId` en el server pase lo que pase acá, así que no hay ninguna razón para
 * impedir la acción — solo para no afirmar de más sobre ella.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DE_LA_ESPERA)
class HojaDeAnularTestDeLaEspera {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc_ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val carro = Account("acc_carro", "Vehículo 4083", AccountType.LOAN, 177_200_000)

    private val pataDelDinero = FinancialEvent(
        id = "ev_dinero",
        accountId = "acc_ahorros",
        type = TransactionType.EXPENSE,
        amount = 4_215_223L,
        category = CUOTA_CATEGORY,
        description = "Cuota de Vehículo 4083",
        timestamp = 1_788_000_000_000L,
        transferId = "tr_cuota",
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montarCon(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    VoidEventSheet(
                        event = pataDelDinero,
                        cuentas = listOf(ahorros, carro),
                        onDismiss = {},
                        onVoided = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** La lectura no volvió todavía: la hoja lo dice en vez de mostrar una sola cifra sin aclarar. */
    @Test
    fun mientras_la_hermana_no_llega_la_hoja_lo_dice() {
        val nuncaContesta = CompletableDeferred<List<FinancialEvent>>()
        montarCon(
            object : RepositorioDePrueba() {
                override suspend fun getEvents(accountId: String?): List<FinancialEvent> = nuncaContesta.await()
            },
        )

        composeRule.onNodeWithText(BUSCANDO_LA_OTRA_MITAD, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        // Y no se inventa la segunda cifra mientras tanto.
        composeRule.onNodeWithText("La deuda de Vehículo 4083 vuelve a subir $1.733.905", useUnmergedTree = true)
            .assertDoesNotExist()
        // Anular sigue disponible: el server cascadea a las dos patas igual.
        composeRule.onNodeWithText("Anular movimiento", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * Y si la lectura **falla**, la hoja no se queda buscando para siempre. Es el caso que el
     * `getOrNull()` original volvía invisible: sin red, «buscando» eterno.
     */
    @Test
    fun si_la_lectura_falla_la_hoja_lo_dice_y_no_se_queda_buscando() {
        montarCon(
            object : RepositorioDePrueba() {
                override suspend fun getEvents(accountId: String?): List<FinancialEvent> =
                    throw java.io.IOException("sin red")
            },
        )

        composeRule.onNodeWithText(NO_SE_PUDO_LEER_LA_OTRA_MITAD, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BUSCANDO_LA_OTRA_MITAD, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Anular movimiento", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}

/** El AVD `Movi_Sensor`: 411×731 dp. */
private const val TELEFONO_DE_LA_ESPERA = "w411dp-h731dp-xhdpi"
