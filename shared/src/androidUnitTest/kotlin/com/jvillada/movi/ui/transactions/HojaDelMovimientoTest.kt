package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Tocar un movimiento ofrece **corregirlo**, no solo anularlo.
 *
 * El defecto que esta clase fija: hasta acá había dos puertas al mismo renglón y ofrecían cosas
 * distintas. Movimientos abría la hoja completa; el **detalle de la cuenta** abría directamente la
 * de anular. O sea que en la pantalla donde el dueño *nota* que un saldo está mal, lo único que se
 * le ofrecía sobre la fila era el rodeo destructivo —anular y volver a crear, perdiendo el id del
 * movimiento— que la edición vino justamente a reemplazar.
 *
 * Se prueba [HojaDelMovimiento], que es lo que ahora abren **las dos** pantallas. Lo que esta
 * prueba puede afirmar es que ese juego de hojas ofrece las dos cosas a la vez y en el orden
 * decidido (corregir arriba, anular abajo y separado). Lo que **no** puede afirmar es que cada
 * pantalla lo llame: `AccountDetailScreen` y `TransactionsScreen` leen de `Repositories`, que es
 * un `object` no inyectable, así que montarlas en Robolectric no traería ninguna fila que tocar.
 * Esa mitad queda garantizada por construcción —ninguna de las dos abre ya `VoidEventSheet` sobre
 * una fila— y no por este test; decir otra cosa sería prometer de más.
 *
 * Tamaño de teléfono chico, por la misma regla que `HojaAgregarGeometriaTest`: a 800×1000 entra
 * todo y la prueba no ejercitaría el desplazamiento de una hoja que hoy mide bastante más que la
 * ventana.
 *
 * **Todas las búsquedas van con `useUnmergedTree = true`**, por el mismo motivo que ya estaba
 * anotado en `HojaAgregarGeometriaTest`: el `clickable` del fondo de la hoja fusiona a todos sus
 * descendientes en un solo nodo, así que sin eso el texto lo absorbe la hoja entera — se mediría
 * la hoja y no el renglón, y la prueba sería verde sin haber mirado nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_SENSOR)
class HojaDelMovimientoTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 0L, "COP")

    private val gasto = FinancialEvent(
        id = "ev-hija",
        accountId = bancolombia.id,
        type = TransactionType.EXPENSE,
        amount = 4_000_000L,
        category = "Otros",
        description = "Hija",
        timestamp = 1_754_406_000_000L,
    )

    private fun montar(event: FinancialEvent = gasto, cuentas: List<Account> = listOf(bancolombia, nu)) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(
                        event = event,
                        cuentas = cuentas,
                        onDismiss = {},
                        onCambiado = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * **La sección de corregir está, y está ARRIBA**: se ve sin ningún gesto. Es lo que el dueño
     * vino a arreglar cuando abre esta hoja frente a un saldo que no cuadra, y todo lo que quede
     * debajo de veinte renglones de categorías es, en la práctica, invisible.
     */
    @Test
    fun corregirElMontoYLaCuentaSeVeSinDesplazar() {
        montar()
        composeRule.onNodeWithText("MONTO Y CUENTA", useUnmergedTree = true).assertIsDisplayed()
        // Y con el monto y la cuenta actuales a la vista, no un id ni un campo en blanco.
        composeRule.onNodeWithText("Bancolombia", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Anular sigue estando — al final, que es donde se decidió que viva. */
    @Test
    fun anularSigueOfreciendoseAlFinal() {
        montar()
        composeRule.onNodeWithText("Anular este movimiento", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * Y «esto se repite», que es la otra acción que el detalle de la cuenta no tenía. Sobre un
     * gasto común aplica; el porqué de cuándo no, en `puedeOfrecerseComoRecurrenteDesdeElDetalle`.
     */
    @Test
    fun laHojaOfreceMarcarloComoRecurrente() {
        montar()
        composeRule.onNodeWithText("¿SE REPITE TODOS LOS MESES?", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * Sin la lista de cuentas la hoja **no se cae ni se bloquea**: lo dice y sigue dejando
     * corregir. Es el caso real de esta pantalla, donde las cuentas se piden al tocar la fila y
     * esa lectura puede fallar.
     */
    @Test
    fun sinLaListaDeCuentasLaHojaSigueSirviendo() {
        montar(cuentas = emptyList())
        composeRule.onNodeWithText("MONTO Y CUENTA", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Anular este movimiento", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}

/** El AVD `Movi_Sensor`: 411×731 dp. Mismo tamaño que usa `HojaAgregarGeometriaTest`. */
private const val AVD_MOVI_SENSOR = "w411dp-h731dp-xhdpi"
