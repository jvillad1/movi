package com.jvillada.movi.ui.credits

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
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

/**
 * # «¿Y si abonas de más?», en la pantalla
 *
 * `AbonoExtraordinarioTest` (en `:core`) prueba la aritmética. Lo que se prueba acá es lo otro, que
 * es lo que de verdad se rompe en este repo: **que la respuesta llegue al dibujo**. Una función que
 * calcula bien y una hoja que no la pinta —o que la pinta sobre el crédito equivocado— se ven
 * idénticas desde el otro archivo.
 *
 * Por eso ninguna prueba de acá llama a [simularAbonoUnico]: todas tocan lo que el dueño toca (el
 * enlace de la tarjeta, un chip, el campo del monto) y afirman sobre el texto que queda en la
 * pantalla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD_DEL_SIMULADOR)
class SimuladorDeAbonoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    /** Libre inversión ·9695: amortiza, lo paga él. 46 cuotas por delante. */
    private val libreInversion = CreditSummary(
        account = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
            insuranceMonthly = 124_800L,
        ),
        paidPct = 0.5,
    )

    /** Hipotecario ·2334: la deuda crece sola, y la cuota la gira Skandia. */
    private val hipotecario = CreditSummary(
        account = Account("acc_2334", "Hipotecario 2334", AccountType.LOAN, balance = 204_183_376L),
        terms = CreditTerms(
            accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
            termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
            insuranceMonthly = 209_219L, paidBy = "Skandia",
        ),
        paidPct = 0.0,
    )

    /**
     * Libranza ·4818: $255.677.421 al 18,01 %, **la cuota se la descuentan de la nómina**. Amortiza
     * —le faltan 65 cuotas— y es su segundo crédito más grande, o sea donde un abono más ahorra.
     * La plata es suya: el empleador la retiene antes de depositar el sueldo.
     */
    private val libranza = CreditSummary(
        account = Account("acc_4818", "Libranza 4818", AccountType.LOAN, balance = 255_677_421L),
        terms = CreditTerms(
            accountId = "acc_4818", bank = "Bancolombia", principal = 283_000_000L, rateEa = 18.01,
            termMonths = 96, installment = 6_040_259L, dayOfMonth = 30, startDate = "2025-02-28",
            payrollDeduction = true,
        ),
        paidPct = 0.1,
    )

    /** Crediágil ·3090: debe $507.553. Cualquier abono normal la salda y sobra plata. */
    private val crediagil = CreditSummary(
        account = Account("acc_3090", "Crediágil 3090", AccountType.LOAN, balance = 507_553L),
        terms = CreditTerms(
            accountId = "acc_3090", bank = "Bancolombia", principal = 5_000_000L, rateEa = 29.64,
            termMonths = 24, installment = 26_485L, dayOfMonth = 5, startDate = "2024-01-05",
        ),
        paidPct = 0.9,
    )

    /**
     * Crédito Techo Gardenera: **con términos y sin tasa**. Es un crédito real del dueño —un solo
     * pago de $10.000.000, tasa 0— y es el que separa las dos guardas: el bloque de la proyección
     * sí se dibuja (hay términos), pero no hay nada que acortar. Sin este caso, tapar la guarda
     * `seProyecta` no rompía ninguna prueba, porque el crédito sin términos ya no llega ahí.
     */
    private val sinTasa = CreditSummary(
        account = Account("acc_techo", "Techo Gardenera", AccountType.LOAN, balance = 10_000_000L),
        terms = CreditTerms(
            accountId = "acc_techo", bank = "Constructora", principal = 10_000_000L, rateEa = 0.0,
            termMonths = 1, installment = 10_000_000L, dayOfMonth = 30, startDate = "2026-03-30",
        ),
        paidPct = 0.0,
    )

    /** Sin términos: no hay tasa ni cuota, así que no hay nada que simular. */
    private val sinTerminos = CreditSummary(
        account = Account("acc_x", "Crédito sin términos", AccountType.LOAN, balance = 3_000_000L),
        terms = null,
        paidPct = 0.0,
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(creditos: List<CreditSummary>) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = creditos
            override suspend fun getCards(): List<CardSummary> = emptyList()
        }
        composeRule.setContent { MoviTheme { CreditosScreen(onNavigate = {}) } }
        composeRule.waitForIdle()
    }

    /**
     * Abre la hoja desde la tarjeta del único crédito montado.
     *
     * Dos precauciones, y las dos se pagaron con pruebas en verde que no abrían nada:
     *
     * - **`useUnmergedTree`**: la tarjeta entera es clicleable —tocarla abre el historial de la
     *   cuenta— así que Compose funde sus hijos en un solo nodo semántico, y un `onNodeWithText`
     *   sobre el árbol combinado devuelve la TARJETA, no el enlace.
     * - **`performSemanticsAction` y no `performClick`**: bajo Robolectric el toque sintético no
     *   llega al `clickable` (mismo hallazgo que documenta `LoQueLlegaDelBancoEligeLaCuentaTest`).
     *   `performClick` no falla: no hace nada, y las seis pruebas de abajo pasaban sin haber
     *   abierto la hoja.
     */
    private fun abrirElSimulador() {
        composeRule.onNodeWithText(ACCION_SIMULAR_ABONO, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocarElChip(etiqueta: String) {
        composeRule.onNodeWithText(etiqueta, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun escribirElMonto(digitos: String) {
        val campo = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
        campo.performSemanticsAction(SemanticsActions.RequestFocus)
        campo.performTextInput(digitos)
        composeRule.waitForIdle()
    }

    private fun cuantasVeces(texto: String): Int =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    /**
     * **La pregunta se ofrece donde se puede contestar**, y hay dos formas de no poder: sin términos
     * (no hay ni tasa ni cuota) y **con términos pero sin tasa** —el Techo Gardenera—, que es la que
     * de verdad prueba la guarda: ahí el bloque de la proyección sí se dibuja. Abrir el simulador en
     * cualquiera de las dos solo podría contestar «no se sabe». Ver [ComoVaLaDeuda.seProyecta].
     */
    @Test
    fun `la pregunta solo aparece donde hay una proyeccion que acortar`() {
        montar(listOf(libreInversion, sinTasa, sinTerminos))

        assertEquals(1, cuantasVeces(ACCION_SIMULAR_ABONO), "una sola de las tres tarjetas puede contestarla")
        // Y las otras dos siguen diciendo lo suyo: la del Techo, que no se sabe cuánto es interés.
        composeRule.onNodeWithText("Sin términos registrados", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Sin tasa registrada", substring = true, useUnmergedTree = true).assertExists()
    }

    /**
     * El caso normal, extremo a extremo: se toca el enlace, se toca un monto y **la pantalla dice
     * cuánto se ahorra y contra qué fecha**. Sin las dos fechas, «terminas 2 cuotas antes» no dice
     * antes de qué.
     */
    @Test
    fun `una cuota mas al 9695 muestra el ahorro y las dos fechas`() {
        montar(listOf(libreInversion))
        abrirElSimulador()
        tocarElChip("Una cuota más")

        composeRule.onNodeWithText("Te ahorras unos $590.000 en intereses", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Terminas 2 cuotas antes: en ", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(" en vez de ", substring = true, useUnmergedTree = true).assertExists()
    }

    /**
     * **Los tres supuestos, en la hoja.** El de la proyección (la tasa y la cuota de hoy), el del
     * abono (que el banco acorte el plazo y no la cuota) y el de la estimación (que el interés que
     * Movi calcula se queda corto contra el extracto). El segundo además es accionable: es lo que
     * él tiene que pedirle al banco para que esta fecha se cumpla. El tercero es el único que no
     * habla del futuro sino de la cifra misma. Ver [SUPUESTO_DE_LA_ESTIMACION].
     */
    @Test
    fun `la hoja declara los tres supuestos`() {
        montar(listOf(libreInversion))
        abrirElSimulador()

        composeRule.onNodeWithText(SUPUESTO_DEL_ABONO, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(SUPUESTO_DE_LA_ESTIMACION, useUnmergedTree = true).assertExists()
        // Dos veces en la pantalla: la tarjeta de resumen de atrás ya lo declara, y la hoja lo
        // repite porque para cuando el dueño llega acá el de atrás está tapado por la hoja misma.
        assertEquals(2, cuantasVeces(SUPUESTO_DE_LA_PROYECCION), "la hoja trae su propia copia")
    }

    /**
     * **De «nunca» a una fecha, y en la pantalla.** El ·2334 crece $21.894 al mes; el chip del
     * mínimo le pone final. Es la información más valiosa de esta hoja, y la que era imposible de
     * obtener antes: nadie estima a ojo que el 1,2 % de una deuda de $204 millones la cambia de
     * categoría.
     */
    @Test
    fun `en la deuda que crece sola el minimo le pone fecha`() {
        montar(listOf(hipotecario))
        abrirElSimulador()
        tocarElChip("Lo mínimo para que se termine")

        composeRule.onNodeWithText("Esta deuda pasa a tener final: ", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Hoy la deuda crece $21.894 cada mes.", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("te faltarían 479 cuotas", substring = true, useUnmergedTree = true).assertExists()
    }

    /**
     * **Y cuando no alcanza, lo dice y dice cuánto haría falta.** $1.000.000 al ·2334 bajan la
     * amortización negativa a $10.011 al mes y la deuda sigue creciendo. Anunciar un ahorro acá
     * sería peor que no contestar.
     *
     * La frase mira el saldo **ya abonado** ($10.011) y no el de hoy ($21.894): son las dos cifras
     * que esta hoja está comparando, y confundirlas dejaría al dueño sin saber si el abono sirvió.
     */
    @Test
    fun `un abono que no alcanza lo dice en vez de prometer un ahorro`() {
        montar(listOf(hipotecario))
        abrirElSimulador()
        escribirElMonto("1000000")

        composeRule.onNodeWithText("Con este abono la deuda sigue sin terminarse", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("seguiría creciendo $10.011 cada mes", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(
            "Harían falta $2.549.401, y aun así te faltarían 479 cuotas: hasta ",
            substring = true, useUnmergedTree = true,
        ).assertExists()
        assertEquals(0, cuantasVeces("Te ahorras"), "no hay ahorro que prometer sobre una deuda que sigue creciendo")
    }

    /**
     * **De quién sería el ahorro.** La cuota del ·2334 la gira Skandia: la deuda es del dueño y
     * puede abonarle, pero el interés que se ahorra lo deja de pagar otro. Sin esta línea la hoja
     * le atribuiría cientos de millones de ahorro propio. Ver [AVISO_DE_ABONO_AJENO].
     */
    @Test
    fun `en un credito que paga un tercero la hoja avisa que el ahorro no es tuyo`() {
        montar(listOf(hipotecario))
        abrirElSimulador()

        composeRule.onNodeWithText(AVISO_DE_ABONO_AJENO, useUnmergedTree = true).assertExists()
    }

    /**
     * **Y a una libranza se le dice lo contrario, porque es lo contrario.** El sueldo le llega neto:
     * esa cuota ya salió de su plata, y el ahorro es suyo entero. Decirle acá que no lo era lo
     * desalentaba de abonarle a la Libranza ·4818 —su segundo crédito más grande, al 18,01 %—, que
     * es de lejos el error más caro que podía cometer esta hoja. Ver [AVISO_DE_ABONO_POR_LIBRANZA].
     */
    @Test
    fun `en una libranza la hoja dice que el ahorro SI es tuyo`() {
        montar(listOf(libranza))
        abrirElSimulador()

        composeRule.onNodeWithText(AVISO_DE_ABONO_POR_LIBRANZA, useUnmergedTree = true).assertExists()
        assertEquals(0, cuantasVeces(AVISO_DE_ABONO_AJENO), "una libranza sí la paga él")

        // Y el titular tampoco se la quita: «te ahorras», con nombre y apellido.
        tocarElChip("Una cuota más")
        composeRule.onNodeWithText("Te ahorras unos $8.500.000 en intereses", useUnmergedTree = true).assertExists()
    }

    /**
     * Y en uno que sí sale de su cuenta, ninguno de los dos avisos está: los dos serían una
     * aclaración sobre nada. **Con la hoja abierta**, que es la mitad que faltaba: un
     * `assertEquals(0, …)` sobre una pantalla en la que nunca se abrió nada pasa igual.
     */
    @Test
    fun `en un credito propio no se aclara quien paga la cuota`() {
        montar(listOf(libreInversion))
        abrirElSimulador()

        composeRule.onNodeWithText(TITULO_DEL_SIMULADOR, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(PIDE_UN_MONTO, useUnmergedTree = true).assertExists()
        assertEquals(0, cuantasVeces(AVISO_DE_ABONO_AJENO))
        assertEquals(0, cuantasVeces(AVISO_DE_ABONO_POR_LIBRANZA))
    }

    /**
     * **Lo que sobra no ahorró nada, y la hoja lo dice.** El Crediágil ·3090 debe $507.553: con un
     * millón se salda y sobran $492.447. «Con esto la saldas» sobre el millón entero sugeriría que
     * el millón entero hizo falta.
     */
    @Test
    fun `saldar el crediagil dice cuanto sobra`() {
        montar(listOf(crediagil))
        abrirElSimulador()
        escribirElMonto("1000000")

        composeRule.onNodeWithText("Con esto la saldas hoy y te ahorras unos $160.000 en intereses", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Te sobran $492.447", substring = true, useUnmergedTree = true).assertExists()
    }
}

/** Mismo tamaño que el AVD con el que se mira la app a ojo. */
private const val TELEFONO_DEL_AVD_DEL_SIMULADOR = "w411dp-h731dp-xhdpi"
