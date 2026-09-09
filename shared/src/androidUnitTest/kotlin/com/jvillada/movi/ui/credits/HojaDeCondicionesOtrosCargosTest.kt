package com.jvillada.movi.ui.credits

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * # Los otros cargos de la cuota se ven y se editan en la hoja de condiciones
 *
 * `DesgloseDeCuotaTest` (en `:core`) prueba la aritmética, `CreditRoutesTest` que el server los
 * guarde y no los borre. Falta el pedazo del medio, que es el que de verdad falla en este repo:
 * **que la hoja tenga el campo, muestre lo que hay guardado y lo mande de vuelta al guardar**.
 * Hay una regla del proyecto justo para esto — nada se configura tocando código —, y sin este
 * test el campo podía existir en el modelo, en la columna y en la ruta, y no en ningún dedo.
 *
 * Se afirma el **viaje redondo** y no un tecleo: se abre la hoja con los $25.000 ya guardados, se
 * toca «Guardar crédito» sin escribir nada, y se mira qué `CreditTerms` llega al repositorio. Si
 * la hoja se olvidara de poner el campo en el objeto que arma —el olvido más probable, porque
 * `save()` construye el `CreditTerms` a mano campo por campo— llegaría en `null` y esta prueba lo
 * dice. Bajo Robolectric el click no llega solo; se usa la acción semántica, igual que el resto
 * de las pruebas de hoja de este repo.
 *
 * El caso es el real: el Vehículo 8761 del Banco de Occidente, cuya cuota de $4.101.123 trae
 * $89.100 de seguro **y** $25.000 de «otros conceptos».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD)
class HojaDeCondicionesOtrosCargosTest {

    @get:Rule val composeRule = createComposeRule()

    private val vehiculo = Account("acc_8761", "Vehículo 8761", AccountType.LOAN, 177_200_000)

    private val condicionesDelVehiculo = CreditTerms(
        accountId = vehiculo.id,
        bank = "Banco de Occidente",
        principal = 190_000_000L,
        rateEa = 18.16,
        termMonths = 60,
        installment = 4_101_123L,
        dayOfMonth = 20,
        startDate = "2024-06-01",
        insuranceMonthly = 89_100L,
        otrosCargosMensuales = 25_000L,
    )

    /** Lo que la hoja le mandó al repositorio al guardar. */
    private var guardado: CreditTerms? = null

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montarLaHoja(terms: CreditTerms = condicionesDelVehiculo) {
        guardado = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(vehiculo)
            override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary {
                guardado = terms
                return CreditSummary(account = vehiculo, terms = terms, paidPct = null)
            }
        }
        composeRule.setContent {
            MoviTheme {
                CreditTermsSheet(
                    editing = CreditSummary(account = vehiculo, terms = terms, paidPct = null),
                    candidates = emptyList(),
                    onDismiss = {},
                    onSaved = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `la hoja muestra los otros cargos guardados, con su explicacion`() {
        montarLaHoja()

        // El campo está, con la cifra guardada adentro y no en blanco: abrir la hoja para corregir
        // el día de pago no puede hacer desaparecer de la vista un dato que sí está en la base.
        composeRule.onNodeWithText("25.000", substring = true).assertExists()
        // Y la explicación usa la palabra del extracto («otros conceptos»), que es por dónde el
        // dueño va a reconocer de qué se le está hablando.
        composeRule.onNodeWithText("otros conceptos", substring = true).assertExists()
    }

    @Test
    fun `guardar sin tocar nada devuelve los otros cargos, no un null`() {
        montarLaHoja()

        composeRule.onNodeWithText("Guardar crédito").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { guardado != null }

        val terms = assertNotNull(guardado, "la hoja tiene que haber guardado algo")
        assertEquals(25_000L, terms.otrosCargosMensuales, "la hoja se olvidó de mandar los otros cargos")
        assertEquals(89_100L, terms.insuranceMonthly, "y el seguro sigue siendo un campo aparte")
    }

    @Test
    fun `un credito sin otros cargos igual ofrece el campo`() {
        // El caso normal —ocho de los nueve créditos del dueño no tienen— tiene que poder
        // declararlos: si el campo solo apareciera cuando ya hay un valor, no habría forma de
        // escribir el primero.
        montarLaHoja(condicionesDelVehiculo.copy(otrosCargosMensuales = null))

        composeRule.onNodeWithText("Otros cargos mensuales", substring = true).assertExists()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("25.000", substring = true).fetchSemanticsNodes().size,
            "sin declararlos, la hoja no puede inventar una cifra",
        )
    }
}

/** Mismo tamaño que el AVD con el que se mira la app a ojo. */
private const val TELEFONO_DEL_AVD = "w411dp-h731dp-xhdpi"
