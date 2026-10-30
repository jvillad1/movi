package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
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
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.LocalPunteroFino
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
    private val vehiculo = Account("v4083", "Vehículo 4083", AccountType.LOAN, 30_000_000)
    private val condiciones = CreditTerms(
        accountId = vehiculo.id, bank = "Bancolombia", principal = 40_000_000L, rateEa = 12.0, termMonths = 60,
        installment = 980_000L, dayOfMonth = 5, startDate = "2024-06-05",
    )

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
    fun la_fila_de_pestanas_no_se_mueve_entre_los_tipos() = pestanasQuietas(punteroFino = false)

    /** Vuelta 71 (revisión): la web con mouse a 411 (sin teclado en pantalla) tampoco salta. */
    @Test
    fun la_fila_de_pestanas_no_se_mueve_sin_teclado_en_pantalla() = pestanasQuietas(punteroFino = true)

    private fun pestanasQuietas(punteroFino: Boolean) {
        montarHoja(punteroFino)
        fun pestanas() = composeRule.onNodeWithTag(TAG_PESTANAS_DE_AGREGAR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val enGasto = pestanas()
        fun igual(donde: String) {
            val ahora = pestanas()
            assertTrue(
                abs(ahora.top.value - enGasto.top.value) < 0.5f && abs(ahora.bottom.value - enGasto.bottom.value) < 0.5f,
                "Las pestañas se movieron en «$donde» (puntero fino: $punteroFino): en Gasto $enGasto, ahora $ahora",
            )
        }
        // El caso de la revisión: de Pagar a Traspaso (el cuerpo se acorta) y vuelta.
        listOf("Ingreso", "Pagar", "Traspaso", "Pagar", "Gasto", "Traspaso").forEach { tipo ->
            tocar(tipo)
            igual(tipo)
        }
        // Y lo que cambia el cuerpo adentro de un tipo: la tarjeta en Gasto (aparece la fila de
        // cuotas) y un crédito en Pagar (su encabezado, su cuota y su desglose).
        tocar("Gasto")
        tocar("Cuenta")
        tocar(nu.name)
        igual("Gasto con la tarjeta")
        tocar("Pagar")
        elegirElVehiculo()
        igual("Pagar con un crédito")
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
            composeRule.onAllNodes(hasText("En el extracto, unos $100.000 al mes, sin intereses.", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodes(hasText("Cuenta completa hoy en tus gastos.", substring = true), useUnmergedTree = true)
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
        assertEquals("En el extracto, unos $100.000 al mes, sin intereses.", lineaDeLaCuotaDelMes(1_200_000L, 12, "COP"))
        assertEquals("Cuenta completa hoy en tus gastos.", ayudaDeLasCuotas())
        // Vuelta 71 (revisión): solo se guarda lo que el dueño eligió.
        assertNull(cuotasParaGuardar(1, elegidas = false, seVeLaFila = true), "un «1» que nadie eligió no se manda")
        assertEquals(1, cuotasParaGuardar(1, elegidas = true, seVeLaFila = true))
        assertNull(cuotasParaGuardar(12, elegidas = true, seVeLaFila = false), "sin la fila a la vista, nada")
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

    // ── Revisión de la vuelta 71 ──────────────────────────────────────────────────────────────

    /** P1 · sin tocar la fila de cuotas, la compra con tarjeta se manda sin cuotas (no un «1»). */
    @Test
    fun sin_tocar_las_cuotas_se_mandan_nulas() {
        montarHoja()
        tocar("8"); tocar("000")
        tocar("Cuenta")
        tocar(nu.name)
        composeRule.onNodeWithTag(TAG_FILA_DE_CUOTAS, useUnmergedTree = true).assertIsDisplayed()
        tocar("Guardar movimiento")
        composeRule.waitUntil(5_000) { guardados.size == 1 }
        assertEquals(nu.id, guardados.single().accountId)
        assertNull(guardados.single().cuotas)
    }

    /** P1 · la cuota que Pagar propone sola no es algo escrito: cerrar no pregunta. */
    @Test
    fun la_cuota_que_propone_pagar_no_pregunta_al_cerrar() {
        montarHoja()
        tocar("Pagar")
        elegirElVehiculo()
        composeRule.onNodeWithText("$980.000", useUnmergedTree = true).assertIsDisplayed()
        cerrarConLaX()
        assertTrue(cerrada, "nadie escribió nada: cierra directo")
    }

    /** P1 · …ni viaja a Gasto: un toque en Guardar no anota $980.000 en Restaurantes. */
    @Test
    fun la_cuota_que_propone_pagar_no_viaja_a_gasto() {
        montarHoja()
        tocar("Pagar")
        elegirElVehiculo()
        tocar("Gasto")
        composeRule.onNodeWithText("$980.000", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Falta el monto").assertIsDisplayed()
        tocar("Guardar movimiento")
        assertTrue(guardados.isEmpty())
        // Lo que el dueño escribe encima de la propuesta sí es suyo.
        tocar("Pagar")
        elegirElVehiculo()
        composeRule.onNodeWithContentDescription("Borrar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar("Gasto")
        composeRule.onNodeWithText("$98.000", useUnmergedTree = true).assertIsDisplayed()
    }

    /** P1 · a 360 × 780 con una tarjeta, «Guardar movimiento» se ve entero sin desplazar. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun a_360_con_tarjeta_guardar_se_ve_sin_desplazar() {
        montarHoja()
        tocar("4"); tocar("5"); tocar("000")
        tocar("Cuenta")
        tocar(nu.name)
        composeRule.onNodeWithTag(TAG_FILA_DE_CUOTAS, useUnmergedTree = true).assertExists()
        val guardar = composeRule.onNodeWithText("Guardar movimiento")
        val visible = guardar.getBoundsInRoot()
        val entero = guardar.getUnclippedBoundsInRoot()
        assertTrue(
            abs(visible.top.value - entero.top.value) < 0.5f && abs(visible.bottom.value - entero.bottom.value) < 0.5f &&
                entero.bottom.value > entero.top.value,
            "«Guardar movimiento» recortado: se ve $visible de $entero",
        )
    }

    private fun elegirElVehiculo() {
        composeRule.onNodeWithTag(TAG_FILA_HACIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar(vehiculo.name)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("$980.000", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────────

    private fun montarHoja(punteroFino: Boolean = false) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, efectivo, nu, vehiculo)
            override suspend fun getCredits(): List<CreditSummary> =
                listOf(CreditSummary(account = vehiculo, terms = condiciones, paidPct = null))
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
                guardados += event
                return event
            }
        }
        composeRule.setContent {
            // Con puntero fino (la web con mouse) no se dibuja el teclado en pantalla.
            CompositionLocalProvider(LocalPunteroFino provides punteroFino) { MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (!cerrada) QuickAddScreen(onDismiss = { cerrada = true })
                    }
                    Spacer(Modifier.height(64.dp))
                }
            } }
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
