package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
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

/**
 * # La hoja de «Cuota» deja corregir el interés, y el capital cambia en vivo
 *
 * `DesgloseEnLaHojaTest` prueba las funciones puras; `InteresRealTest` (en `:core`) la aritmética
 * y las guardas. Faltaba el pedazo del medio, que es el que de verdad falla en este repo: **que la
 * hoja de verdad tenga el campo, lo prellene con la estimación y recalcule lo que muestra cuando
 * el dueño escribe otro número**. Sin esto, todo lo demás podría vivir en una rama que ningún
 * dedo alcanza.
 *
 * El caso es el real: el Libre inversión ·9695 ($40.710.555 al 11,27 % E.A., seguro $124.800).
 * Movi estima $363.905 de interés; el extracto dice $473.227. Con la cuota de $1.204.064 el
 * capital pasa de $715.359 (estimado) a **$606.037** (real), que es lo que el dueño cargó a mano.
 *
 * Mismo harness que `HojaAgregarEligeLaCuentaTest`: se monta [QuickAddScreen] entero contra un
 * [RepositorioDePrueba] que solo abre las dos puertas que la pestaña necesita —las cuentas y las
 * condiciones de los créditos—, y se toca con la acción semántica (bajo Robolectric el click no
 * llega; ver la nota allá). Nada horizontal es confiable acá; lo que se afirma es qué campos
 * existen, qué dicen, y qué pasa al escribir en ellos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD)
class HojaDeCuotaConInteresRealTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val libre = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 40_710_555)

    private val condicionesDelLibre = CreditTerms(
        accountId = libre.id,
        bank = "Bancolombia",
        principal = 50_000_000L,
        rateEa = 11.27,
        termMonths = 60,
        installment = 1_204_064L,
        dayOfMonth = 5,
        startDate = "2024-06-05",
        insuranceMonthly = 124_800L,
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montarLaPestanaCuota(terms: CreditTerms? = condicionesDelLibre) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, libre)
            override suspend fun getCredits(): List<CreditSummary> =
                listOf(CreditSummary(account = libre, terms = terms, paidPct = null))
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        QuickAddScreen(onDismiss = {})
                    }
                    Spacer(Modifier.height(ALTO_BARRA_INFERIOR))
                }
            }
        }
        composeRule.waitForIdle()
        tocar("Cuota")
        // La deuda que se paga la elige el dueño con el dedo: no hay preselección en un pago.
        tocar("Hacia")
        tocar("Libre inversión 9695")
        // Las condiciones llegan por corrutina; el campo aparece cuando llegaron.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("INTERESES DE ESTE MES").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** El `BasicTextField` de adentro de un `MoneyField` etiquetado. */
    private fun campo(tag: String): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    private fun escribirElMonto() {
        campo(TAG_CAMPO_DE_MONTO).performTextInput("1204064")
        composeRule.waitForIdle()
    }

    /** El defecto que abrió la rama, afirmado desde la hoja de verdad. */
    @Test
    fun el_campo_de_interes_arranca_con_la_estimacion_y_la_frase_dice_que_es_estimado() {
        montarLaPestanaCuota()

        campo(TAG_CAMPO_DE_INTERES).assert(hasText("363.905"))
        composeRule.onNodeWithText(INTERES_ESTIMADO_AVISO, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sin_tocar_el_interes_el_capital_que_se_muestra_es_el_estimado() {
        montarLaPestanaCuota()
        escribirElMonto()

        composeRule.onNodeWithText("$715.359 bajan la deuda", substring = true, useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun escribir_el_interes_del_extracto_cambia_el_capital_en_vivo() {
        montarLaPestanaCuota()
        escribirElMonto()

        campo(TAG_CAMPO_DE_INTERES).performTextReplacement("473227")
        composeRule.waitForIdle()

        // $1.204.064 − $473.227 − $124.800 = $606.037: el número que él cargó a mano.
        composeRule.onNodeWithText("$606.037 bajan la deuda", substring = true, useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("según tu extracto", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("$715.359 bajan la deuda", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun un_interes_que_no_cabe_se_dice_al_lado_del_campo_y_no_pinta_un_capital_negativo() {
        montarLaPestanaCuota()
        escribirElMonto()

        // $1.204.064 + seguro $124.800 no cabe en la cuota de $1.204.064.
        campo(TAG_CAMPO_DE_INTERES).performTextReplacement("1204064")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("subiría en vez de bajar", substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("bajan la deuda", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun sin_tasa_el_campo_arranca_vacio_y_el_extracto_igual_sirve() {
        montarLaPestanaCuota(terms = condicionesDelLibre.copy(rateEa = 0.0, insuranceMonthly = null))
        escribirElMonto()

        campo(TAG_CAMPO_DE_INTERES).assert(hasText(""))
        composeRule.onNodeWithText(INTERES_SIN_TASA_AVISO, useUnmergedTree = true).assertExists()

        campo(TAG_CAMPO_DE_INTERES).performTextReplacement("473227")
        composeRule.waitForIdle()

        // $1.204.064 − $473.227, sin seguro declarado.
        composeRule.onNodeWithText("$730.837 bajan la deuda", substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}

/** El AVD `Movi_Sensor`: 411×731 dp. Mismo tamaño que el resto de las pruebas de hojas. */
private const val TELEFONO_DEL_AVD = "w411dp-h731dp-xhdpi"

/** `MinBottomNav.kt:63` — leído del código, no estimado. */
private val ALTO_BARRA_INFERIOR = 64.dp
