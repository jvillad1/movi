package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO_DE_HOJA
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.plan.TITULO_CUANTO_PUEDES_GASTAR
import com.jvillada.movi.ui.sdui.HeroDeUnVistazo
import com.jvillada.movi.ui.sdui.TAG_CIFRA_TE_QUEDAN_EN_PAGOS
import com.jvillada.movi.ui.sdui.TarjetaDelDisponible
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Hoy y Plan, montados uno encima del otro con los mismos datos, dicen la misma cifra** (Ola 1):
 * el mismo valor, entero en la cifra grande de Hoy y compacto en la frase de Plan.
 *
 * Arriba el hero de Hoy (`HeroDeUnVistazo`), abajo la tarjeta de Plan tal como la arma
 * `PlanScreen` (`TarjetaDelDisponible` con `disponibleDelInicio(data)`). Se lee lo que de verdad se
 * pinta: el texto de la cifra grande y el de la frase de Plan, y el color de los dos.
 *
 * Vuelta de diseño 3: la columna «Período» de Plan ya no repetía lo que queda —lo decía su frase—
 * (vuelta 70: vuelve a decirlo, la MISMA cifra, para que las tres columnas midan lo mismo);
 * dice lo gastado y de cuánto.
 *
 * Escenario de `DisponibleEnInicioTest`: lunes 21-sep-2026, $10M de ingresos, $3,1M de arriendo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class TeQuedanEnHoyEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = LocalDate(2026, 9, 21)

    private fun datos(gasto: Map<String, Long>, cuentas: List<Account>) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = cuentas,
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 3_100_000,
                    dayOfMonth = 5, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true)),
        gastoVariablePorDia = gasto,
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    private val unaCuenta = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 2_345_678))
    private val dosCuentas = listOf(
        Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 2_000_000),
        Account(id = "a2", name = "Nequi", type = AccountType.CHECKING, balance = 345_678),
    )

    private var navegoA: Screen? = null
    private var rojo: Color = Color.Unspecified
    private var normal: Color = Color.Unspecified

    private fun montar(data: DashboardData) {
        navegoA = null
        composeRule.setContent {
            MoviTheme {
                rojo = Movi.colores.sale
                normal = Movi.colores.texto
                Column(Modifier.fillMaxSize()) {
                    HeroDeUnVistazo(
                        section = ScreenSection(type = "HERO_BALANCE"),
                        data = data,
                        onNavigate = { navegoA = it },
                        hoy = hoy,
                    )
                    // Lo mismo que hace `PlanScreen.SeccionCuantoPuedesGastar`.
                    TarjetaDelDisponible(titulo = TITULO_CUANTO_PUEDES_GASTAR, disponible = disponibleDelInicio(data, hoy))
                }
            }
        }
        // La cifra entra contando: se deja terminar.
        composeRule.mainClock.advanceTimeBy(DURACION_DE_LA_ENTRADA_MS.toLong() + 200L)
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun color(nodo: SemanticsNode): Color {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single().layoutInput.style.color
    }

    private fun cifraDeHoy(): SemanticsNode =
        composeRule.onNodeWithTag(TAG_CIFRA_TE_QUEDAN, useUnmergedTree = true).fetchSemanticsNode()

    private fun cifraDePagos(): SemanticsNode =
        composeRule.onNodeWithTag(TAG_CIFRA_TE_QUEDAN_EN_PAGOS, useUnmergedTree = true).fetchSemanticsNode()

    /**
     * Vuelta 45: **Pagos dice lo mismo que el Inicio.** La protagonista de la tarjeta de Pagos es «Te
     * quedan», entera y con el mismo detalle que el héroe; el total del período baja a su renglón.
     */
    @Test
    fun `te quedan - Hoy y Pagos dicen la misma cifra, y la columna Periodo no la repite`() {
        montar(datos(gasto = mapOf("2026-08-26" to 100_000L), cuentas = unaCuenta))

        val esperado = disponibleDelInicio(datos(mapOf("2026-08-26" to 100_000L), unaCuenta), hoy)!!.periodo.teQuedan
        val deHoy = texto(cifraDeHoy())
        assertEquals(formatCOP(esperado), deHoy)
        assertEquals("\$6.800.000", deHoy)
        assertEquals(deHoy, texto(cifraDePagos()), "Pagos dice la misma cifra que el Inicio")
        // El mismo detalle en los dos, y el rótulo también.
        assertEquals(2, composeRule.onAllNodesWithText("Para 4 días · \$1,7M por día", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(2, composeRule.onAllNodesWithText(ROTULO_TE_QUEDAN, useUnmergedTree = true).fetchSemanticsNodes().size)
        // Vuelta 60: la semana mide contra lo que quedaba el lunes, y esta es la última del período, así
        // que su renglón dice lo mismo que queda. Vuelta 83: entera, como el resto de la tarjeta (un solo
        // formato), y el «Período» dice lo gastado de su meta, no otra vez lo que queda.
        assertEquals(
            0,
            composeRule.onAllNodesWithText(formatMoneyCompact(esperado), useUnmergedTree = true).fetchSemanticsNodes().size,
            "nada compacto en la tarjeta de Pagos",
        )
        // Hoy y Pagos; la semana, a 4 días del cierre, se calla (revisión de la 83).
        assertEquals(2, composeRule.onAllNodesWithText("\$6.800.000", useUnmergedTree = true).fetchSemanticsNodes().size, "Hoy y Pagos")
        composeRule.onNodeWithTag(com.jvillada.movi.ui.sdui.TAG_PERIODO_GASTADO_DE_LA_META, useUnmergedTree = true).assertExists()
        // Revisión de la 83: sin el renglón «Disponible del período»: el total lo dice el «de $X» del Período.
        assertTrue(composeRule.onAllNodesWithText(ROTULO_DISPONIBLE_DEL_PERIODO, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("\$6.900.000", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // Vuelta 80: lo gastado y de cuánto, en frase, van al desglose de «¿De dónde sale?».
        assertTrue(composeRule.onAllNodesWithText("de \$6.900.000", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("¿De dónde sale?", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("de \$6.900.000", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(normal, color(cifraDeHoy()))
        assertEquals(normal, color(cifraDePagos()))
    }

    @Test
    fun `te pasaste - la misma cifra en Hoy y en Pagos, sin signo, y el mismo rojo`() {
        montar(datos(gasto = mapOf("2026-09-01" to 7_000_000L), cuentas = unaCuenta))

        assertEquals(2, composeRule.onAllNodesWithText(ROTULO_TE_PASASTE, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("\$100.000", texto(cifraDeHoy()))
        assertEquals("\$100.000", texto(cifraDePagos()))
        assertEquals(rojo, color(cifraDeHoy()))
        assertEquals(color(cifraDePagos()), color(cifraDeHoy()), "Hoy y Pagos, del mismo color")
        // La frase vieja se fue: la cifra ya dice «Te pasaste por».
        assertEquals(0, composeRule.onAllNodesWithText("Te pasaste del disponible del período por \$100.000", useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        // Vuelta 83: el «Período» dice lo gastado ($7.000.000) de su meta, en el rojo de la alerta; ya no
        // repite lo que queda (la cifra de arriba).
        assertEquals(0, composeRule.onAllNodesWithText("−\$100.000", useUnmergedTree = true).fetchSemanticsNodes().size)
        val gastadoEnElRenglon = composeRule.onAllNodesWithText("\$7.000.000", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, gastadoEnElRenglon.size, "el «Período» dice lo gastado")
        assertEquals(rojo, color(gastadoEnElRenglon.single()))
        // Vuelta 80: lo gastado ($7M) va al desglose de «¿De dónde sale?», apagado.
        composeRule.onNodeWithText("¿De dónde sale?", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val gastadoEnPlan = composeRule.onAllNodesWithText("en el día a día \$7.000.000", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, gastadoEnPlan.size)
        assertTrue(color(gastadoEnPlan.single()) != rojo, "lo gastado va apagado, no en rojo")
    }

    /**
     * Ola R1: «Tu plata» salió del héroe —ni fila ni desglose—. Tiene su casa en Patrimonio, y en el
     * Inicio vive en el carrusel de cuentas; repetida acá era la misma cifra dos veces.
     */
    @Test
    fun `Tu plata ya no está en el héroe, ni con una cuenta ni con dos`() {
        montar(datos(gasto = emptyMap(), cuentas = dosCuentas))

        composeRule.onNodeWithTag(TAG_CIFRA_TE_QUEDAN, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_BALANCE_TITLE, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Nequi", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Ver el detalle de tu plata", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * **Vuelta de diseño 28: tocar el héroe explica la cifra en la misma pantalla.** Abre «¿De dónde
     * sale?» con la cascada —cuyo total es la cifra del héroe— y «Ver tus períodos» abajo lleva a donde
     * antes llevaba el toque.
     */
    @Test
    fun `tocar el heroe abre de donde sale, y ver tus periodos lleva a Tus periodos`() {
        val data = datos(gasto = mapOf("2026-09-21" to 50_000L), cuentas = unaCuenta)
        montar(data)
        // Sin «Tu plata» ni barra: el héroe es el rótulo, la cifra y para cuántos días.
        assertTrue(composeRule.onAllNodesWithText("para", substring = true, ignoreCase = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty(), "la línea de los días y lo que da por día")
        composeRule.onNodeWithTag(TAG_CASCADA_DEL_DISPONIBLE, useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag(TAG_CIFRA_TE_QUEDAN, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(null, navegoA, "el toque ya no sale de Hoy")
        // La hoja con su título (la tarjeta de Plan, montada abajo, tiene su propio «¿De dónde sale?»).
        composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_HOJA, useUnmergedTree = true).assert(hasAnyDescendant(hasText(DE_DONDE_SALE)))
        composeRule.onNodeWithTag(TAG_CASCADA_DEL_DISPONIBLE, useUnmergedTree = true).assertIsDisplayed()
        // El total de la cascada dice la cifra del héroe, entera, con su rótulo.
        val heroe = texto(cifraDeHoy())
        val total = composeRule.onNodeWithTag(TAG_TOTAL_DE_LA_CASCADA, useUnmergedTree = true)
        total.assert(hasAnyDescendant(hasText(ROTULO_TE_QUEDAN)))
        total.assert(hasAnyDescendant(hasText(heroe)))
        // Y lo de arriba, con los nombres de las cifras de Plan.
        listOf(ROTULO_FIJOS_DEL_PERIODO, ROTULO_DISPONIBLE_DEL_PERIODO, ROTULO_GASTASTE_EN_EL_PERIODO).forEach {
            // Revisión de la 83: «Disponible del período» ya no tiene renglón en la tarjeta de Pagos: solo la cascada.
            composeRule.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(1)
        }

        // El botón (árbol mezclado: su texto es parte de él), con la acción de semántica: la hoja entra
        // animada desde abajo y un toque por coordenadas puede caer antes de que llegue.
        composeRule.onNodeWithText(VER_TUS_PERIODOS).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(Screen.Periodos, navegoA)
        composeRule.onNodeWithTag(TAG_CASCADA_DEL_DISPONIBLE, useUnmergedTree = true).assertDoesNotExist()
    }
}
