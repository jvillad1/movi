package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 71 · Agregar no pierde nada, y la compra con tarjeta queda completa
 *
 * Contra la hoja real (con un repositorio de prueba), lo que la revisión fresca 4 encontró roto:
 *
 * - el monto, la nota y la fecha **viajan** entre Gasto, Traspaso y Pagar;
 * - cerrar con algo escrito pregunta **«¿Descartar $X?»**; sin nada, cierra directo;
 * - la fila de pestañas **no se mueve** entre los cuatro tipos;
 * - **«¿A cuántas cuotas?»** solo con una tarjeta de crédito, y las cuotas llegan a `postEvent`;
 * - la pestaña se llama **«Pagar»** y su destino vacío dice **«¿Qué pagas?»**.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xhdpi")
class AgregarNoPierdeNadaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val efectivo = Account("e1", "Efectivo", AccountType.CASH, 200_000)
    private val nu = Account("nu", "Tarjeta Nu", AccountType.CREDIT_CARD, 0)

    private val guardados = mutableListOf<FinancialEvent>()
    private var cerrada = false

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    // ── 1. El monto, la nota y la fecha viajan ────────────────────────────────────────────────

    @Test
    fun el_monto_la_nota_y_la_fecha_viajan_entre_pestanas() {
        montarHoja()
        tocar("4"); tocar("5"); tocar("000")
        tocar("Hoy"); tocar("Ayer")
        tocar("Nota")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performTextReplacement("Almuerzo")
        tocar("Guardar nota")

        tocar("Traspaso")
        composeRule.onNodeWithText("$45.000", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Almuerzo", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ayer", useUnmergedTree = true).assertIsDisplayed()

        tocar("Pagar")
        composeRule.onNodeWithText("$45.000", useUnmergedTree = true).assertIsDisplayed()

        // Y de vuelta: lo corregido en Traspaso llega a Gasto.
        tocar("Traspaso")
        composeRule.onNodeWithContentDescription("Borrar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar("Gasto")
        composeRule.onNodeWithText("$4.500", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Almuerzo", useUnmergedTree = true).assertIsDisplayed()

        tocar("Guardar movimiento")
        composeRule.waitUntil(5_000) { guardados.size == 1 }
        assertEquals(4_500L, guardados.single().amount)
        assertEquals("Almuerzo", guardados.single().description)
        assertEquals(hoyEnAppZone().minus(DatePeriod(days = 1)), epochMillisToAppDate(guardados.single().timestamp))
    }

    // ── 2. «¿Descartar $X?» ───────────────────────────────────────────────────────────────────

    @Test
    fun con_algo_escrito_cerrar_pregunta_y_descartar_cierra() {
        montarHoja()
        tocar("1"); tocar("2"); tocar("5"); tocar("0"); tocar("000")
        cerrarConLaX()

        assertFalse(cerrada, "con $1.250.000 escritos, la X no cierra sin preguntar")
        composeRule.onNodeWithText("¿Descartar $1.250.000?", useUnmergedTree = true).assertIsDisplayed()

        // «Cancelar» vuelve a la hoja con todo.
        tocar("Cancelar")
        assertFalse(cerrada)
        composeRule.onNodeWithText("¿Descartar $1.250.000?", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("$1.250.000", useUnmergedTree = true).assertIsDisplayed()

        cerrarConLaX()
        tocar(DESCARTAR)
        assertTrue(cerrada, "«Descartar» cierra")
        assertTrue(guardados.isEmpty())
    }

    @Test
    fun sin_nada_escrito_cierra_directo() {
        montarHoja()
        cerrarConLaX()
        assertTrue(cerrada)
        composeRule.onNodeWithText(DESCARTAR).assertDoesNotExist()
    }

    @Test
    fun la_pregunta_dice_el_monto_o_la_nota() {
        assertEquals("¿Descartar $1.250.000?", preguntaDeDescartar("1250000", "COP"))
        assertEquals("¿Descartar US$120?", preguntaDeDescartar("120", "USD"))
        assertEquals("¿Descartar la nota?", preguntaDeDescartar("", "COP"))
        assertTrue(hayAlgoQuePerder("45000", "", "", ""))
        assertFalse(hayAlgoQuePerder("", "", "", ""))
        assertFalse(hayAlgoQuePerder("9000", "9000", "Arriendo", "Arriendo"), "lo que trajo «Anotar pago» no se pregunta")
        assertTrue(hayAlgoQuePerder("", "", "Almuerzo", ""))
    }

    // ── 3. Las pestañas no saltan ─────────────────────────────────────────────────────────────

    @Test
    fun la_fila_de_pestanas_no_se_mueve_entre_los_tipos() {
        montarHoja()
        fun pestanas() = composeRule.onNodeWithTag(TAG_PESTANAS_DE_AGREGAR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val enGasto = pestanas()
        // El caso de la revisión: de Pagar a Traspaso (el cuerpo se acorta) y vuelta.
        listOf("Ingreso", "Pagar", "Traspaso", "Pagar", "Gasto", "Traspaso").forEach { tipo ->
            tocar(tipo)
            val ahora = pestanas()
            assertTrue(
                abs(ahora.top.value - enGasto.top.value) < 0.5f && abs(ahora.bottom.value - enGasto.bottom.value) < 0.5f,
                "Las pestañas se movieron en «$tipo»: en Gasto $enGasto, ahora $ahora",
            )
        }
    }

    // ── 4. «¿A cuántas cuotas?» ───────────────────────────────────────────────────────────────

    @Test
    fun la_fila_de_cuotas_va_solo_con_una_tarjeta_y_las_cuotas_llegan_al_guardar() {
        montarHoja()
        composeRule.onNodeWithTag(TAG_FILA_DE_CUOTAS, useUnmergedTree = true).assertDoesNotExist()

        // El monto antes de elegir la tarjeta: después, la pastilla «1» de las cuotas y la tecla «1»
        // dicen lo mismo.
        tocar("1"); tocar("2"); tocar("0"); tocar("0"); tocar("000")
        tocar("Cuenta")
        tocar(nu.name)
        composeRule.onNodeWithTag(TAG_FILA_DE_CUOTAS, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(PREGUNTA_DE_LAS_CUOTAS, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(tagDeLaPastillaDeCuotas(12)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodes(hasText("≈ $100.000 al mes, sin intereses", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodes(hasText("en el extracto de la tarjeta va $100.000 por mes", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )

        // En Ingreso no va: una tarjeta no difiere lo que entra.
        tocar("Ingreso")
        composeRule.onNodeWithTag(TAG_FILA_DE_CUOTAS, useUnmergedTree = true).assertDoesNotExist()
        tocar("Gasto")

        tocar("Guardar movimiento")
        composeRule.waitUntil(5_000) { guardados.size == 1 }
        val compra = guardados.single()
        assertEquals(nu.id, compra.accountId)
        assertEquals(1_200_000L, compra.amount, "la compra no cambia: cuenta entera")
        assertEquals(12, compra.cuotas)
    }

    @Test
    fun sin_tarjeta_no_se_mandan_cuotas() {
        montarHoja()
        tocar("8"); tocar("000")
        tocar("Guardar movimiento")
        composeRule.waitUntil(5_000) { guardados.size == 1 }
        assertNull(guardados.single().cuotas)
    }

    @Test
    fun la_ayuda_de_las_cuotas_dice_la_regla_de_siempre() {
        assertNull(lineaDeLaCuotaDelMes(1_200_000L, 1, "COP"))
        assertEquals("≈ $100.000 al mes, sin intereses", lineaDeLaCuotaDelMes(1_200_000L, 12, "COP"))
        assertEquals("Cuenta completa hoy en tus gastos y en el extracto de la tarjeta.", ayudaDeLasCuotas(1_200_000L, 1, "COP"))
        assertEquals(
            "Cuenta completa hoy en tus gastos; en el extracto de la tarjeta va $100.000 por mes.",
            ayudaDeLasCuotas(1_200_000L, 12, "COP"),
        )
        assertTrue(preguntaLasCuotas(AccountType.CREDIT_CARD, esGasto = true, enModoPago = false))
        assertFalse(preguntaLasCuotas(AccountType.SAVINGS, esGasto = true, enModoPago = false))
        assertFalse(preguntaLasCuotas(AccountType.CREDIT_CARD, esGasto = false, enModoPago = false))
        assertFalse(preguntaLasCuotas(AccountType.CREDIT_CARD, esGasto = true, enModoPago = true))
    }

    // ── 6. Un solo nombre ─────────────────────────────────────────────────────────────────────

    @Test
    fun la_pestana_se_llama_pagar_y_su_destino_pregunta_que_pagas() {
        assertEquals(listOf("Gasto", "Ingreso", "Traspaso", "Pagar"), ETIQUETAS_DE_LAS_PESTANAS_DE_AGREGAR)
        assertEquals(PAGAR_TARJETA, AtajoDeAgregar.PAGO_DE_TARJETA.rotulo)
        montarHoja()
        tocar("Pagar")
        composeRule.onNodeWithText(QUE_PAGAS, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Elegir cuenta", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────────

    private fun montarHoja() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, efectivo, nu)
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
                guardados += event
                return event
            }
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (!cerrada) QuickAddScreen(onDismiss = { cerrada = true })
                    }
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(ahorros.name, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** La X de la hoja: el marco la saca animada y después pregunta (o cierra). */
    private fun cerrarConLaX() {
        composeRule.onNodeWithContentDescription("Cerrar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    /** Bajo Robolectric `performClick` no llega al composable: ver [HojaAgregarEligeLaCuentaTest]. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
