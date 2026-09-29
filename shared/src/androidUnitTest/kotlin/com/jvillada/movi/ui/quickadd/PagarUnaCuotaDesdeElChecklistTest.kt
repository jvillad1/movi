package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.recurrentes.ETIQUETA_ANOTAR
import com.jvillada.movi.ui.recurrentes.SeccionChecklistDelPeriodo
import com.jvillada.movi.ui.recurrentes.hojaParaAnotar
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Pagar una cuota en un toque, desde el checklist
 *
 * Revisión del 29-sep: «Anotar este pago» en la fila de una cuota llevaba a Créditos, y Créditos
 * no tiene ningún botón de pagar — el flujo moría ahí. Ahora abre Agregar en la pestaña «Cuota»
 * con todo puesto, y lo que se prueba es el camino entero que recorre el dedo: el botón de la
 * fila → [hojaParaAnotar] (el mismo `onAnotarMovimiento` que usa el tablero) → la hoja montada con
 * esos presets.
 *
 * Mismo arnés que [HojaDeCuotaConInteresRealTest]: [QuickAddScreen] entero contra un
 * [RepositorioDePrueba] con cuentas y créditos, tocando con la acción semántica.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class PagarUnaCuotaDesdeElChecklistTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val libre = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 40_710_555)
    private val master = Account("mb", "Master Black", AccountType.CREDIT_CARD, 27_501_150)

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    /**
     * Monta la fila [pago] del checklist; al tocar «Anotar este pago» la reemplaza por la hoja de
     * Agregar abierta con lo que devolvió [hojaParaAnotar]. Devuelve esa pantalla.
     */
    private fun anotarDesdeLaFila(pago: PagoDelPeriodo): Screen.QuickAdd {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, libre, master)
            override suspend fun getCredits(): List<CreditSummary> =
                listOf(CreditSummary(account = libre, terms = null, paidPct = null))
        }
        var hoja by mutableStateOf<Screen.QuickAdd?>(null)
        var navegoA: Screen? = null
        composeRule.setContent {
            MoviTheme {
                val abierta = hoja
                if (abierta == null) {
                    Box(Modifier.fillMaxSize()) {
                        SeccionChecklistDelPeriodo(
                            checklist = listOf(pago),
                            cargando = false,
                            pudoLeer = true,
                            marcando = emptySet(),
                            onConfirmar = { _, _ -> },
                            onNoFueEste = { _, _ -> },
                            onAnotarMovimiento = { p ->
                                val destino = hojaParaAnotar(p)
                                navegoA = destino
                                hoja = destino as? Screen.QuickAdd
                            },
                            onQuitarLaMarca = {},
                            onReintentar = {},
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            QuickAddScreen(
                                onDismiss = {},
                                presetAccountId = abierta.presetAccountId,
                                presetNota = abierta.presetNota,
                                presetMonto = abierta.presetMonto,
                                presetCategoria = abierta.presetCategoria,
                                presetFecha = abierta.presetFecha,
                                presetEsIngreso = abierta.presetEsIngreso,
                                presetDeudaId = abierta.presetDeudaId,
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val botones = composeRule.onAllNodes(
            hasAnyDescendant(hasText(ETIQUETA_ANOTAR)) and hasClickAction(),
            useUnmergedTree = true,
        )
        assertTrue(botones.fetchSemanticsNodes().isNotEmpty(), "la fila sin movimiento ofrece anotarlo")
        botones[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val abierta = assertIs<Screen.QuickAdd>(navegoA, "ya no lleva a Créditos: abre Agregar")
        // Las cuentas llegan por corrutina; la deuda se pone en «Hacia» cuando llegaron.
        composeRule.waitUntil(5_000) { hay(pago.let { if (it.ruleId.startsWith("card_")) master.name else libre.name }) }
        return abierta
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    /** El `BasicTextField` de adentro del `MoneyField` del monto. */
    private fun campoDelMonto(): SemanticsNodeInteraction =
        composeRule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_CAMPO_DE_MONTO)),
            useUnmergedTree = true,
        )

    private fun textoDelMonto(): String =
        campoDelMonto().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun la_cuota_de_un_credito_abre_agregar_en_cuota_con_todo_puesto() {
        val hoja = anotarDesdeLaFila(
            PagoDelPeriodo(
                ruleId = "credit_l9695",
                nombre = "Cuota Libre inversión 9695",
                monto = 1_204_064,
                pagado = false,
                diasParaVencer = -3,
                vence = "2026-09-05",
                periodoDelSello = "2026-09",
                categoria = "Cuota de crédito",
            ),
        )

        assertEquals("l9695", hoja.presetDeudaId, "la deuda es el sufijo del ruleId")
        assertEquals(1_204_064L, hoja.presetMonto)
        assertNull(hoja.presetFecha, "la fecha es hoy, no el vencimiento")

        // La pestaña «Cuota»: solo ella dice «Registrar pago» y «Concepto del pago».
        assertTrue(hay("Registrar pago"), "abre en la pestaña Cuota")
        assertTrue(hay("Concepto del pago"))
        assertTrue(hay(libre.name), "el crédito ya está en «Hacia»")
        assertTrue(hay(ahorros.name), "y la plata sale de una cuenta de dinero")
        assertEquals("1.204.064", textoDelMonto().filter { it.isDigit() || it == '.' })
        assertTrue(hay("Hoy"), "fechado hoy")
    }

    @Test
    fun el_pago_de_una_tarjeta_abre_cuota_sin_prellenar_el_saldo() {
        val hoja = anotarDesdeLaFila(
            PagoDelPeriodo(
                ruleId = "card_mb",
                nombre = "Pago tarjeta Master Black",
                monto = 27_501_150,
                pagado = false,
                diasParaVencer = -1,
                montoEsSaldo = true,
                vence = "2026-09-18",
                periodoDelSello = "2026-09",
            ),
        )

        assertEquals("mb", hoja.presetDeudaId)
        assertNull(hoja.presetMonto, "el saldo de una tarjeta no es lo que se va a pagar")

        assertTrue(hay("Registrar pago"), "abre en la pestaña Cuota")
        assertTrue(hay(master.name), "la tarjeta ya está en «Hacia»")
        assertEquals("", textoDelMonto(), "el campo del monto arranca vacío")
    }
}
