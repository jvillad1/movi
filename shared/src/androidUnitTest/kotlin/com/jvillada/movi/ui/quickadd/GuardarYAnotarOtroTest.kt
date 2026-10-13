package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.jvillada.movi.ui.components.LocalHayTecladoFisico
import com.jvillada.movi.ui.components.categoriaPorDefectoPara
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * # «Guardar y anotar otro» en Agregar
 *
 * Guarda y deja la hoja abierta para el siguiente. Lo que se fija acá es la regla entera, contra lo
 * que de verdad le llega a `postEvent`:
 *
 * - **se conservan** la cuenta, la fecha y la pestaña (Ingreso, en la prueba: el caso en que
 *   perderla sería anotar el siguiente con el signo al revés);
 * - **se limpian** el monto, la nota y la categoría (que vuelve a la de por defecto de la pestaña);
 * - la hoja **no se cierra** ([QuickAddScreen]'s `onSaved` no se llama) pero avisa que guardó
 *   (`onSavedSinCerrar`), y dice qué anotó.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xhdpi")
class GuardarYAnotarOtroTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val efectivo = Account("e1", "Efectivo", AccountType.CASH, 200_000)

    private val guardados = mutableListOf<FinancialEvent>()
    private var cerrada = false
    private var guardadoYCerrada = 0
    private var guardadoSinCerrar = 0

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun conserva_cuenta_fecha_y_tipo_y_limpia_monto_nota_y_categoria() {
        montarHoja()
        val ayer = hoyEnAppZone().minus(DatePeriod(days = 1))
        val porDefecto = categoriaPorDefectoPara(TransactionType.INCOME)
        val elegida = listOf("Freelance", "Inversiones").first { it != porDefecto }

        // Un ingreso, en efectivo, de ayer, con categoría y nota propias.
        tocar("Ingreso")
        tocar("Cuenta")
        tocar(efectivo.name)
        tocar("Hoy")
        tocar("Ayer")
        tocar("4"); tocar("5"); tocar("000")
        tocar("Categoría")
        tocar(elegida)
        tocar("Nota")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performTextReplacement("Venta del sofá")
        tocar("Guardar nota")

        tocar(GUARDAR_Y_ANOTAR_OTRO)
        composeRule.waitUntil(5_000) { guardados.size == 1 }

        val primero = guardados.single()
        assertEquals(TransactionType.INCOME, primero.type)
        assertEquals(efectivo.id, primero.accountId)
        assertEquals(45_000L, primero.amount)
        assertEquals(elegida, primero.category)
        assertEquals("Venta del sofá", primero.description)
        assertEquals(ayer, epochMillisToAppDate(primero.timestamp))

        // La hoja sigue abierta, avisó que guardó y dice qué anotó.
        assertFalse(cerrada, "la hoja no se cierra")
        assertEquals(0, guardadoYCerrada, "no es el «Guardar» de siempre")
        assertEquals(1, guardadoSinCerrar, "la pantalla de atrás tiene que volver a leer")
        composeRule.onNodeWithTag(TAG_CONFIRMACION_DE_LO_ANOTADO, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Anotado: $45.000 · Venta del sofá", useUnmergedTree = true).assertIsDisplayed()

        // Limpio: sin monto, sin nota, la categoría de por defecto.
        composeRule.onNodeWithText("Falta el monto").assertIsDisplayed()
        composeRule.onNodeWithText("Agregar nota…", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(porDefecto, useUnmergedTree = true).assertIsDisplayed()
        // Conservado a la vista: la cuenta y la fecha.
        composeRule.onNodeWithText(efectivo.name, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ayer", useUnmergedTree = true).assertIsDisplayed()

        // Y el siguiente sale con lo conservado, sin tocar nada más que el monto.
        tocar("7"); tocar("000")
        tocar("Guardar movimiento")
        composeRule.waitUntil(5_000) { guardados.size == 2 }

        val segundo = guardados[1]
        assertNotEquals(primero.id, segundo.id, "es otro movimiento, no un reintento del anterior")
        assertEquals(TransactionType.INCOME, segundo.type, "la pestaña se conserva")
        assertEquals(efectivo.id, segundo.accountId, "la cuenta se conserva")
        assertEquals(ayer, epochMillisToAppDate(segundo.timestamp), "la fecha se conserva")
        assertEquals(7_000L, segundo.amount)
        assertEquals(porDefecto, segundo.category, "la categoría se limpió")
        assertEquals(porDefecto, segundo.description, "la nota se limpió (sin nota, la descripción es la categoría)")
        // El «Guardar» de siempre sí cierra.
        assertEquals(1, guardadoYCerrada)
    }

    /** Con el monto vacío no hay nada que guardar: el renglón dice lo que falta, no el enlace. */
    @Test
    fun sin_monto_no_se_ofrece() {
        montarHoja()
        composeRule.onNodeWithText("Falta el monto").assertIsDisplayed()
        composeRule.onNodeWithText(GUARDAR_Y_ANOTAR_OTRO).assertDoesNotExist()

        tocar("8")
        composeRule.onNodeWithText(GUARDAR_Y_ANOTAR_OTRO).assertIsDisplayed()
    }

    /** Si el guardado falla, la hoja no se limpia: lo escrito sigue ahí para reintentar. */
    @Test
    fun si_falla_no_limpia_nada() {
        montarHoja(fallar = true)
        tocar("9"); tocar("000")
        tocar(GUARDAR_Y_ANOTAR_OTRO)

        composeRule.onNodeWithText("$9.000", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CONFIRMACION_DE_LO_ANOTADO, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, guardadoSinCerrar)
    }

    /**
     * **⇧Enter con el teclado físico** (la web): guarda y deja la hoja abierta, con el atajo dicho al
     * lado del enlace. Lo que se afirma es funcional —qué le llega a `postEvent` y que la hoja siga—,
     * como en `TecladoFisicoEnAgregarTest`.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun shift_enter_guarda_y_deja_la_hoja_abierta() {
        montarHoja(tecladoFisico = true)
        composeRule.onRoot().performKeyInput {
            pressKey(Key.One); pressKey(Key.Two); pressKey(Key.Zero); pressKey(Key.Zero); pressKey(Key.Zero)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("$GUARDAR_Y_ANOTAR_OTRO  ·  ⇧ Enter").assertIsDisplayed()

        composeRule.onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
        composeRule.waitUntil(5_000) { guardados.size == 1 }

        assertEquals(12_000L, guardados.single().amount)
        assertFalse(cerrada, "⇧Enter no cierra la hoja")
        assertEquals(1, guardadoSinCerrar)
        composeRule.onNodeWithText("Falta el monto").assertIsDisplayed()
    }

    // ── El atajo, puro ───────────────────────────────────────────────────────────────────

    @Test
    fun shift_enter_es_guardar_y_anotar_otro_y_shift_con_otra_tecla_no_es_de_la_hoja() {
        assertEquals(AccionDeTecla.GuardarYOtro, accionDeTecla(Key.Enter, KeyEventType.KeyDown, false, conShift = true))
        assertEquals(AccionDeTecla.GuardarYOtro, accionDeTecla(Key.NumPadEnter, KeyEventType.KeyDown, false, conShift = true))
        // Soltar Enter con Shift todavía apretado se consume, igual que sin Shift.
        assertEquals(AccionDeTecla.Consumir, accionDeTecla(Key.Enter, KeyEventType.KeyUp, false, conShift = true))
        assertNull(accionDeTecla(Key.One, KeyEventType.KeyDown, false, conShift = true), "Shift+1 es «!», no un dígito")
        assertNull(accionDeTecla(Key.Enter, KeyEventType.KeyDown, true, conShift = true), "⌘⇧Enter no es de la hoja")
        assertEquals(AccionDeTecla.Guardar, accionDeTecla(Key.Enter, KeyEventType.KeyDown, false))
    }

    @Test
    fun la_confirmacion_dice_la_nota_o_si_no_la_categoria() {
        val base = FinancialEvent(
            id = "ev1", accountId = "a1", type = TransactionType.EXPENSE, amount = 45_000,
            category = "Comida", description = "Almuerzo", timestamp = 0L,
        )
        assertEquals("Anotado: $45.000 · Almuerzo", confirmacionDeLoAnotado(base))
        assertEquals("Anotado: $45.000 · Comida", confirmacionDeLoAnotado(base.copy(description = "Comida")))
        assertEquals("Anotado: US$120 · Comida", confirmacionDeLoAnotado(base.copy(amount = 120, currency = "USD", description = "")))
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private fun montarHoja(fallar: Boolean = false, tecladoFisico: Boolean = false) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, efectivo)
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
                if (fallar) throw IllegalStateException("se cortó la red")
                guardados += event
                return event
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalHayTecladoFisico provides tecladoFisico) { MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (!cerrada) {
                            QuickAddScreen(
                                onDismiss = { cerrada = true },
                                onSaved = { guardadoYCerrada++ },
                                onSavedSinCerrar = { guardadoSinCerrar++ },
                            )
                        }
                    }
                    Spacer(Modifier.height(64.dp))
                }
            } }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(ahorros.name, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Bajo Robolectric `performClick` no llega al composable: ver [HojaAgregarEligeLaCuentaTest]. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
