package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # La estimación llega a la pantalla, y la casilla sigue tildando
 *
 * `CuantoDeboPagarTest` prueba que las funciones devuelven el texto correcto. Falta el pedazo que
 * de verdad se rompe en este repo: **que el texto llegue a la fila**, y que agregarle un renglón a
 * la fila no se lleve puesto lo único que la fila hacía — tildar el período.
 *
 * Las filas montadas son las dos que conviven en su base: la cuota del Vehículo ·8761, con todo
 * cargado, y una cuota sin tasa registrada, que **no** puede mostrar estimación.
 *
 * Lo que NO cubre: es Robolectric, así que no dice nada de cómo parte el renglón en la web ni de
 * dónde queda la línea a 390 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ChecklistDiceCuantoDeboPagarTest {

    @get:Rule val composeRule = createComposeRule()

    private val vehiculo = CreditSummary(
        account = Account("acc_8761", "Vehículo 8761", AccountType.LOAN, balance = 177_052_715L),
        terms = CreditTerms(
            accountId = "acc_8761", bank = "Banco de Occidente", principal = 190_000_000L,
            rateEa = 18.16, termMonths = 60, installment = 4_101_123L, dayOfMonth = 19,
            startDate = "2025-09-19", insuranceMonthly = 89_100L,
        ),
        paidPct = 0.07,
    )

    /** Tasa 0 como marcador y sin la casilla «No cobra intereses»: no se sabe, no se estima. */
    private val techo = CreditSummary(
        account = Account("acc_techo", "Crédito Techo Gardenera", AccountType.LOAN, balance = 10_000_000L),
        terms = CreditTerms(
            accountId = "acc_techo", bank = "Constructora", principal = 10_000_000L, rateEa = 0.0,
            termMonths = 1, installment = 10_000_000L, dayOfMonth = 30, startDate = "2026-08-30",
        ),
        paidPct = 0.0,
    )

    private fun filaDe(credito: CreditSummary, dias: Int) = PagoDelPeriodo(
        ruleId = CREDIT_RULE_PREFIX + credito.account.id,
        nombre = "Cuota ${credito.account.name}",
        monto = credito.terms!!.installment,
        pagado = false,
        diasParaVencer = dias,
        vence = "2026-09-19",
        periodoDelSello = "2026-09",
    )

    /** Las filas que pidieron «Anotar el movimiento». */
    private val aAnotar = mutableListOf<String>()

    private fun montar(conCreditos: Boolean) {
        aAnotar.clear()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = listOf(filaDe(vehiculo, dias = 3), filaDe(techo, dias = 9)),
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { _, _ -> },
                        onAnotarMovimiento = { pago -> aAnotar += pago.ruleId },
                        onQuitarLaMarca = {},
                        onReintentar = {},
                        planesDeCuotas = if (conCreditos) {
                            planesDeLasCuotas(listOf(vehiculo, techo))
                        } else {
                            emptyMap()
                        },
                    )
                }
            }
        }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun la_fila_de_la_cuota_muestra_el_reparto_estimado_y_de_donde_sale() {
        montar(conCreditos = true)

        assertTrue(hay("Cuota Vehículo 8761"), "la fila de la cuota sigue estando")
        assertTrue(hay("\$4.101.123"), "el monto de la cuota registrada sigue estando")
        assertTrue(
            hay("Movi estima: \$2.479.256 de interés · \$89.100 el seguro · \$1.532.767 a capital"),
            "la estimación del período tiene que estar en la fila",
        )
        // Y el pie que dice que es una estimación sobre la deuda de hoy, una sola vez.
        assertEquals(
            1,
            composeRule.onAllNodesWithText(ESTIMADO_SOBRE_LA_DEUDA_DE_HOY, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun la_cuota_sin_tasa_no_muestra_ninguna_estimacion() {
        montar(conCreditos = true)

        assertTrue(hay("Cuota Crédito Techo Gardenera"), "la fila sin tasa sigue estando")
        // Solo una fila estima, así que solo hay un «Movi estima» en pantalla.
        assertEquals(
            1,
            composeRule.onAllNodesWithText(ETIQUETA_ESTIMADO, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun sin_los_creditos_cargados_la_fila_se_ve_como_antes() {
        montar(conCreditos = false)

        assertTrue(hay("Cuota Vehículo 8761"))
        assertTrue(!hay(ETIQUETA_ESTIMADO), "sin plan no se estima nada")
        assertTrue(!hay(ESTIMADO_SOBRE_LA_DEUDA_DE_HOY), "y el pie no explica lo que no está")
    }

    /**
     * Revisión final: el grupo de lo ya tildado se titula «Ya ocurrieron · X de Y» y cuenta lo
     * que lista — el sueldo recibido incluido. Antes decía «Ya salieron · 1 de 1» encima de dos
     * filas, una de ellas un ingreso.
     */
    @Test
    fun el_grupo_de_lo_ya_tildado_dice_ya_ocurrieron_y_cuenta_el_ingreso() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = listOf(
                            filaDe(vehiculo, dias = 3),
                            PagoDelPeriodo("r_arriendo", "Arriendo", 1_850_000, pagado = true, diasParaVencer = -15),
                            PagoDelPeriodo(
                                "r_sueldo", "Sueldo", 9_000_000, pagado = true, diasParaVencer = -1,
                                esIngreso = true,
                            ),
                        ),
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { _, _ -> },
                        onAnotarMovimiento = {},
                        onQuitarLaMarca = {},
                        onReintentar = {},
                    )
                }
            }
        }

        assertTrue(hay("Ya ocurrieron · 2 de 3"), "el título cuenta las dos filas que lista, sobre las tres")
        assertTrue(!hay("Ya salieron"), "un sueldo recibido no «salió»")
        assertTrue(hay("Sueldo"), "y el ingreso está en el grupo que el título cuenta")
    }

    /**
     * **La fila dejó de ser tocable, y lo que la reemplazó es «Anotar el movimiento».**
     *
     * Tildarla sellaba el período sin ninguna evidencia. El dueño lo cortó: *«no me debería dejar
     * hacer check sin que el movimiento asociado exista»*. Lo que queda es la salida honesta —que
     * el movimiento EXISTA— y para una cuota eso se registra en Créditos, decisión que toma la
     * pantalla (ver `hojaParaAnotar`), no esta sección.
     */
    @Test
    fun la_fila_no_se_tilda_y_ofrece_anotar_el_movimiento() {
        montar(conCreditos = true)

        val filas = composeRule.onAllNodes(
            hasText("Cuota Vehículo 8761", substring = true) and hasClickAction(),
        )
        assertEquals(
            0,
            filas.fetchSemanticsNodes().size,
            "la fila es de solo lectura: su estado lo decide el movimiento, no el dedo",
        )

        // Y se toca por la acción semántica y no con un clic real: el checklist es más alto que la
        // pantalla de prueba.
        val botones = composeRule.onAllNodes(
            hasAnyDescendant(hasText(ETIQUETA_ANOTAR)) and hasClickAction(),
            useUnmergedTree = true,
        )
        assertTrue(botones.fetchSemanticsNodes().isNotEmpty(), "sin movimiento, la fila ofrece anotarlo")
        botones[0].performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf("credit_acc_8761"), aAnotar)
    }
}
