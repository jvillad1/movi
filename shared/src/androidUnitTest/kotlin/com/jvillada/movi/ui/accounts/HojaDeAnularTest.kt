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
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # La hoja de anular dice **las dos** cifras cuando las dos mitades no valen lo mismo
 *
 * `AnulacionDeUnParTest` (en `:core`) prueba la regla y las frases. Falta el pedazo del medio, que
 * es el que de verdad falló: **que la hoja vaya a buscar la hermana y pinte lo que la regla dice**.
 * Sin esto, la función viviría en una rama que ningún call site alcanza — este repo ya tuvo una
 * feature entera así, compilando y con su prueba en verde.
 *
 * El caso es el real: la cuota del vehículo, anulada desde la cuenta de ahorros. La hoja mostraba
 * «$4.215.223» y nada más, mientras desaparecían $4.215.223 de la cuenta **y** $1.733.905 de la
 * deuda.
 *
 * Todas las búsquedas van con `useUnmergedTree = true`, por el mismo motivo que el resto de las
 * pruebas de hojas: el `clickable` del fondo fusiona a todos sus descendientes en un solo nodo, así
 * que sin eso se mediría la hoja entera y no el renglón.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD)
class HojaDeAnularTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc_ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val carro = Account("acc_carro", "Vehículo 4083", AccountType.LOAN, 177_200_000)

    /** La cuota real: $4.215.223 salen de la cuenta, $1.733.905 abonan a capital. */
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

    private val pataDeLaDeuda = FinancialEvent(
        id = "ev_deuda",
        accountId = "acc_carro",
        type = TransactionType.INCOME,
        amount = 1_733_905L,
        category = CUOTA_CATEGORY,
        description = "Abono a capital desde Bancolombia Ahorros",
        timestamp = 1_788_000_000_000L,
        transferId = "tr_cuota",
        noAmortiza = 2_481_318L,
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(event: FinancialEvent, enLaBase: List<FinancialEvent>) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEvents(accountId: String?): List<FinancialEvent> = enLaBase
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    VoidEventSheet(
                        event = event,
                        cuentas = listOf(ahorros, carro),
                        onDismiss = {},
                        onVoided = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** El defecto que abrió la rama, afirmado desde la hoja de verdad. */
    @Test
    fun anular_la_cuota_desde_los_ahorros_nombra_tambien_lo_que_le_pasa_al_credito() {
        montar(pataDelDinero, listOf(pataDelDinero, pataDeLaDeuda))

        composeRule.onNodeWithText("Bancolombia Ahorros recupera $4.215.223", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("La deuda de Vehículo 4083 vuelve a subir $1.733.905", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }

    /** Y por la otra puerta —el detalle del crédito— la hoja cuenta exactamente lo mismo. */
    @Test
    fun anular_la_misma_cuota_desde_el_credito_dice_lo_mismo() {
        montar(pataDeLaDeuda, listOf(pataDelDinero, pataDeLaDeuda))

        composeRule.onNodeWithText("Bancolombia Ahorros recupera $4.215.223", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("La deuda de Vehículo 4083 vuelve a subir $1.733.905", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }

    /** Un traspaso simétrico no gana ningún aviso: la cifra de arriba ya dice el efecto entero. */
    @Test
    fun un_traspaso_simetrico_no_agrega_ninguna_aclaracion() {
        val sale = pataDelDinero.copy(
            id = "ev_out", category = TRANSFER_CATEGORY, description = "Traspaso a CDT", transferId = "tr_simple",
        )
        val entra = FinancialEvent(
            id = "ev_in",
            accountId = "acc_carro",
            type = TransactionType.INCOME,
            amount = sale.amount,
            category = TRANSFER_CATEGORY,
            description = "Traspaso desde Bancolombia Ahorros",
            timestamp = sale.timestamp,
            transferId = "tr_simple",
        )
        montar(sale, listOf(sale, entra))

        composeRule.onNodeWithText("Bancolombia Ahorros recupera $4.215.223", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    /** Un gasto suelto ni siquiera va a buscar hermana: el repositorio no se toca. */
    @Test
    fun un_gasto_suelto_no_pregunta_por_ninguna_hermana() {
        val suelto = pataDelDinero.copy(id = "ev_solo", category = "Otros", transferId = null)
        // `RepositorioDePrueba` explota con el nombre del método si alguien lo llama, así que este
        // test falla ruidosamente si la hoja pide la lista de eventos sin necesitarla.
        Repositories.sustitutoDePrueba = RepositorioDePrueba()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    VoidEventSheet(event = suelto, cuentas = listOf(ahorros), onDismiss = {}, onVoided = {})
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Anular movimiento", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}

/** El AVD `Movi_Sensor`: 411×731 dp. Mismo tamaño que el resto de las pruebas de hojas. */
private const val TELEFONO_DEL_AVD = "w411dp-h731dp-xhdpi"
