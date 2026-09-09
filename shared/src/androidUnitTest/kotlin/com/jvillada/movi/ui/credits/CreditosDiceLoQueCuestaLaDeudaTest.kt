package com.jvillada.movi.ui.credits

import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import kotlin.test.assertTrue

/**
 * # La pantalla de Créditos contesta las tres preguntas
 *
 * `PlanDelCreditoTest` (en `:core`) prueba la aritmética y `BarraQueNoMienteTest` las frases. Falta
 * el pedazo que de verdad se rompe en este repo: **que las frases lleguen a la pantalla**. Una
 * función que devuelve el texto correcto y una tarjeta que no lo pinta se ven idénticas desde los
 * otros dos archivos.
 *
 * Importa sobre todo para la alerta, que se pidió que fuera **imposible de no ver**: eso no es una
 * propiedad de una función pura, es una propiedad de lo que está en la pantalla.
 *
 * Los tres créditos montados acá son los tres casos que conviven en su base: uno que amortiza, uno
 * cuya deuda crece sola, y el préstamo de su mamá —que tiene la misma aritmética que el segundo y
 * **no** es una alerta—.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD_DE_CREDITOS)
class CreditosDiceLoQueCuestaLaDeudaTest {

    @get:Rule val composeRule = createComposeRule()

    /** Libre inversión ·9695: amortiza, y es donde menos interés paga (29,8 % de la cuota). */
    private val libreInversion = CreditSummary(
        account = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
            insuranceMonthly = 124_800L,
        ),
        paidPct = 0.5,
    )

    /** Hipotecario ·2334: la cuota no cubre intereses más seguro. Deuda por encima del capital. */
    private val hipotecario = CreditSummary(
        account = Account("acc_2334", "Hipotecario 2334", AccountType.LOAN, balance = 204_183_376L),
        terms = CreditTerms(
            accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
            termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
            insuranceMonthly = 209_219L, paidBy = "Skandia",
        ),
        paidPct = 0.0,
    )

    /** Crédito Mamá: la tasa está calibrada para que el interés dé la cuota. No es una alerta. */
    private val mama = CreditSummary(
        account = Account("acc_mama", "Crédito Mamá", AccountType.LOAN, balance = 100_000_000L),
        terms = CreditTerms(
            accountId = "acc_mama", bank = "Mamá", principal = 100_000_000L, rateEa = 16.7652,
            termMonths = 240, installment = 1_300_000L, dayOfMonth = 27, startDate = "2020-01-01",
        ),
        paidPct = 0.0,
    )

    /**
     * Un crédito recién creado, en \$0 y sin un solo movimiento: el paso 1 del flujo de dos pasos
     * que registra un desembolso. Su tarjeta decía «Ya está pagada».
     */
    private val recienCreado = CreditSummary(
        account = Account("acc_nuevo", "Libranza nueva", AccountType.LOAN, balance = 0L),
        terms = CreditTerms(
            accountId = "acc_nuevo", bank = "Bancolombia", principal = 257_000_000L, rateEa = 12.5,
            termMonths = 120, installment = 3_500_000L, dayOfMonth = 5, startDate = "2026-09-01",
        ),
        paidPct = 1.0,
        hasMovements = false,
    )

    /** Y uno de verdad pagado: llegó a \$0 con movimientos. Tampoco puede fijar la fecha de nadie. */
    private val yaPagado = CreditSummary(
        account = Account("acc_pago", "Crediágil 3090", AccountType.LOAN, balance = 0L),
        terms = CreditTerms(
            accountId = "acc_pago", bank = "Bancolombia", principal = 5_000_000L, rateEa = 29.64,
            termMonths = 24, installment = 26_485L, dayOfMonth = 5, startDate = "2024-01-05",
        ),
        paidPct = 1.0,
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
     * Una fila del resumen: el **rótulo con su alcance y la cifra, en la misma fila**.
     *
     * Se afirma sobre las dos juntas (`hasAnySibling`) a propósito. Buscar la cifra sola dejaba la
     * prueba en verde con la fila borrada, porque los intereses de un crédito también salen en su
     * tarjeta; y buscar el rótulo solo no probaría que arrastra la cifra correcta.
     */
    private fun filaDelResumen(rotulo: String, valor: String) {
        composeRule.onNode(hasText(rotulo) and hasAnySibling(hasText(valor)), useUnmergedTree = true)
            .assertExists("la fila «$rotulo» tiene que mostrar $valor")
    }

    /**
     * **La alerta tiene que estar en la pantalla, no solo en una función.** Antes, un crédito cuya
     * deuda crece sola se veía igual que uno recién creado: una barra en 0 % y la palabra «pagado».
     */
    @Test
    fun `un credito cuya deuda crece lo anuncia arriba y en su tarjeta`() {
        montar(listOf(libreInversion, hipotecario))

        // Arriba, sobre toda la cartera: sale de esta pantalla sabiendo que existe.
        composeRule.onNodeWithText("En 1 crédito la cuota no cubre los intereses", substring = true).assertExists()
        // Y en la tarjeta del crédito que lo causa, con la cifra en pesos.
        composeRule.onNodeWithText("tu deuda crece $21.894 cada mes", substring = true).assertExists()
    }

    /**
     * **Y el Crédito Mamá no la dispara**, aunque su capital también sea negativo. Si esta prueba
     * cae, la app le está gritando al dueño por el acuerdo con su mamá.
     */
    @Test
    fun `el credito mama no dispara la alerta`() {
        montar(listOf(mama))

        assertEquals(
            0,
            composeRule.onAllNodesWithText("no cubre los intereses", substring = true).fetchSemanticsNodes().size,
            "el préstamo de su mamá no es una amortización negativa: es el trato",
        )
        composeRule.onNodeWithText("la deuda se queda donde está", substring = true).assertExists()
    }

    /** La primera pregunta, en la tarjeta: cuánto de la cuota es interés. */
    @Test
    fun `cada tarjeta dice cuanto de su cuota es interes`() {
        montar(listOf(libreInversion))

        composeRule.onNodeWithText("29,8 % de la cuota", substring = true).assertExists()
        composeRule.onNodeWithText("$358.488 de interés", substring = true).assertExists()
    }

    /**
     * El contracaso de la barra: un crédito que sí va bajando la sigue teniendo. Sin esto, «no
     * dibujar la barra» pasaría igual de verde si dejáramos de dibujarla siempre.
     */
    @Test
    fun `un credito que si baja conserva su barra`() {
        montar(listOf(libreInversion))

        // `useUnmergedTree`: la tarjeta entera es clicleable, así que Compose funde sus hijos en un
        // solo nodo semántico y la barra desaparece del árbol combinado.
        composeRule.onNodeWithTag(TAG_BARRA_DE_PROGRESO, useUnmergedTree = true).assertExists()
    }

    /** La tercera: cuándo se termina, y el supuesto dicho con todas las letras. */
    @Test
    fun `la pantalla dice cuando termina y con que supuesto`() {
        montar(listOf(libreInversion))

        composeRule.onNodeWithText("Te faltan 46 cuotas · la última en junio de 2030 si la cuota y la tasa no cambian", substring = true)
            .assertExists()
        composeRule.onNodeWithText(SUPUESTO_DE_LA_PROYECCION, substring = true).assertExists()
    }

    /**
     * **El plazo del contrato y la proyección son dos números distintos, y la tarjeta lo dice.**
     * Al ·9695 le quedan 42 meses de los 105 que firmó y 46 de proyección; dos cuentas de cuotas
     * sin rótulo en la misma tarjeta se leen como un error de la app.
     */
    @Test
    fun `el plazo pactado se distingue de la proyeccion`() {
        montar(listOf(libreInversion))

        composeRule.onNodeWithText("Plazo pactado 105 meses", substring = true).assertExists()
        composeRule.onNodeWithText("Te faltan 46 cuotas", substring = true).assertExists()
    }

    /**
     * **Lo que sale de su bolsillo y lo que no, los dos, y cada uno con su alcance en el rótulo.**
     * El hipotecario lo gira Skandia: sus \$2.426.389 de interés mensual no pueden sumarse a lo que
     * él paga, y tampoco pueden desaparecer de la pantalla.
     *
     * Se afirma sobre las **filas del resumen** —rótulo y cifra juntos— y no sobre «\$358.488 en
     * alguna parte»: esa cifra también está en la tarjeta del ·9695, así que borrar la fila del
     * resumen entero dejaba la prueba en verde. Es el mismo patrón que ya se corrigió en la barra.
     */
    @Test
    fun `los intereses que paga otro se muestran aparte, no se suman ni se esconden`() {
        montar(listOf(libreInversion, hipotecario))

        composeRule.onNodeWithText(TITULO_INTERES_DEL_MES, substring = true).assertExists()
        filaDelResumen(ALCANCE_INTERES_PROPIO, "\$358.488")
        filaDelResumen(ALCANCE_INTERES_AJENO, "\$2.426.389")
    }

    /**
     * **Cada cifra dice de qué alcance habla, en el rótulo y no en una nota al pie.** «Te falta en
     * intereses» eran los intereses propios **y** solo de los créditos que se terminan: el 36 % de
     * lo que de verdad falta, sin decirlo en ninguna parte.
     */
    @Test
    fun `el interes que falta dice de cuales creditos habla`() {
        montar(listOf(libreInversion, hipotecario))

        composeRule.onNodeWithText(TITULO_INTERES_POR_PAGAR, substring = true).assertExists()
        // El ·9695 se termina y lo paga él; el ·2334 no se termina, así que no aporta ninguna cifra.
        filaDelResumen(ALCANCE_FALTA_PROPIO, "\$8.857.065")
    }

    /**
     * **La fila de la fecha final no puede dar una fecha habiendo deuda que no se termina.** Con el
     * ·9695 (46 cuotas) y el ·2334 (que no baja), decía «la última en junio de 2030» sobre una
     * cartera de la que \$204.183.376 no se acaban nunca.
     */
    @Test
    fun `sin fecha final mientras haya una deuda que no se termina`() {
        montar(listOf(libreInversion, hipotecario))

        composeRule.onNodeWithText(TITULO_ULTIMA_CUOTA, substring = true).assertExists()
        filaDelResumen(ALCANCE_ULTIMA_CUOTA, "Sin fecha")
        // Y se dice cuántos son y cuánta plata hay adentro, que es lo que el resumen no dibujaba.
        composeRule.onNodeWithText("1 crédito no se termina a este ritmo: \$204.183.376 que no bajan", substring = true)
            .assertExists()
    }

    /**
     * **El Crédito Mamá también entra en esa cuenta.** No dispara la alerta roja —es el acuerdo—
     * pero \$100.000.000 que no bajan tienen que estar en el resumen: antes la única advertencia
     * visible contaba amortizaciones negativas y él no aparecía en ningún lado.
     */
    @Test
    fun `el credito mama aparece en lo que no se termina aunque no sea una alerta`() {
        montar(listOf(libreInversion, mama, hipotecario))

        composeRule.onNodeWithText("2 créditos no se terminan a este ritmo: \$304.183.376 que no bajan", substring = true)
            .assertExists()
        composeRule.onNodeWithText("En 1 crédito la cuota no cubre los intereses", substring = true).assertExists()
    }

    /**
     * **El caso extremo.** Con el único crédito que amortiza en \$0, el máximo de «las que
     * terminan» era 0 meses y la fila decía «Tu última cuota — septiembre de 2026» debiendo
     * \$304.183.376.
     */
    @Test
    fun `un credito en cero no puede fijar la fecha final de la cartera`() {
        montar(listOf(yaPagado, mama, hipotecario))

        filaDelResumen(ALCANCE_ULTIMA_CUOTA, "Sin fecha")
    }

    /**
     * **La tarjeta de un crédito recién creado no dice «Ya está pagada».** Es el paso 1 del flujo de
     * dos pasos: decía a la vez «Falta registrar el desembolso», «\$0 de interés» y «Ya está
     * pagada».
     */
    @Test
    fun `un credito recien creado no dice ya esta pagada ni cero de interes`() {
        montar(listOf(recienCreado))

        composeRule.onNodeWithText("Falta registrar el desembolso", substring = true).assertExists()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Ya está pagada", substring = true).fetchSemanticsNodes().size,
            "«Falta registrar el desembolso» y «Ya está pagada» no pueden convivir",
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("de interés", substring = true).fetchSemanticsNodes().size,
            "sin deuda registrada no hay interés que anunciar",
        )
    }

    /**
     * La barra que mentía. Con la deuda por encima del capital original la tarjeta dice cuánto se
     * pasó, en pesos, en vez de «0% pagado».
     */
    @Test
    fun `la tarjeta ya no dice cero por ciento pagado sobre una deuda que crecio`() {
        montar(listOf(hipotecario))

        composeRule.onNodeWithText("$4.183.376 más que al inicio", substring = true).assertExists()
        // Y la barra **no se dibuja**. Se afirma sobre el dibujo y no sobre el dato: lo que
        // engañaba era el rectángulo vacío, no el `Float` que lo alimenta.
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(TAG_BARRA_DE_PROGRESO, useUnmergedTree = true).fetchSemanticsNodes().size,
            "una barra vacía sobre una deuda que creció dibuja lo contrario de lo que pasó",
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("0% pagado", substring = true).fetchSemanticsNodes().size,
            "«0% pagado» sobre una deuda que creció se lee como «todavía no empezaste»",
        )
    }
}

/** Mismo tamaño que el AVD con el que se mira la app a ojo. */
private const val TELEFONO_DEL_AVD_DE_CREDITOS = "w411dp-h731dp-xhdpi"
