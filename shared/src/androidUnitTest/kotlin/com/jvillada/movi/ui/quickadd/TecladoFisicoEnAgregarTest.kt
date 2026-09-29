package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LocalHayTecladoFisico
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El teclado físico en «Agregar» (web y escritorio)
 *
 * Revisión del 29-sep: en escritorio los dígitos no escribían el monto, Backspace no borraba y
 * Enter no guardaba — había que clickear el teclado en pantalla tecla por tecla. Ahora la hoja
 * atiende el teclado cuando el foco NO está en la Nota.
 *
 * Dos partes: el mapeo puro ([accionDeTecla]) y la hoja real, a 390 dp (la hoja desde abajo) y a
 * 1.280 dp (la centrada, en la cáscara). Lo que se afirma de la hoja es funcional: **qué monto le
 * llega a `postEvent`** después de teclear y apretar Enter — no se lee el canvas.
 *
 * Esto prueba que el manejador hace lo correcto cuando la tecla llega. Que llegue en el navegador
 * de verdad (Compose-wasm mapea `Key` por `event.code`) se verificó aparte con Playwright; ver la
 * memoria «teclado en la web».
 */
/** Envoltorio: `Key` es una value class y Kotlin no la deja ir en un `vararg`. */
private class Tecla(val key: Key)

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TecladoFisicoEnAgregarTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val guardados = mutableListOf<FinancialEvent>()
    private var cerrada = false

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    // ── El mapeo, puro ──────────────────────────────────────────────────────────────────

    @Test
    fun los_digitos_de_arriba_y_del_teclado_numerico_escriben_el_monto() {
        val arriba = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        val numerico = listOf(
            Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
            Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
        )
        (arriba.indices).forEach { i ->
            assertEquals(AccionDeTecla.Digito("$i"), accionDeTecla(arriba[i], KeyEventType.KeyDown, false))
            assertEquals(AccionDeTecla.Digito("$i"), accionDeTecla(numerico[i], KeyEventType.KeyDown, false))
        }
    }

    @Test
    fun backspace_borra_y_enter_guarda() {
        assertEquals(AccionDeTecla.Borrar, accionDeTecla(Key.Backspace, KeyEventType.KeyDown, false))
        assertEquals(AccionDeTecla.Guardar, accionDeTecla(Key.Enter, KeyEventType.KeyDown, false))
        assertEquals(AccionDeTecla.Guardar, accionDeTecla(Key.NumPadEnter, KeyEventType.KeyDown, false))
    }

    @Test
    fun soltar_la_tecla_un_modificador_o_una_letra_no_hacen_nada() {
        assertNull(accionDeTecla(Key.Five, KeyEventType.KeyUp, false), "soltarla sería escribir dos veces")
        assertNull(accionDeTecla(Key.One, KeyEventType.KeyDown, true), "Ctrl+1, ⌘1 o Shift+1 no son dígitos")
        assertNull(accionDeTecla(Key.Enter, KeyEventType.KeyDown, true))
        assertNull(accionDeTecla(Key.A, KeyEventType.KeyDown, false))
        assertNull(accionDeTecla(Key.Escape, KeyEventType.KeyDown, false), "Escape lo atiende el marco")
    }

    // ── La hoja real ────────────────────────────────────────────────────────────────────

    private fun repo() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
                guardados += event
                return event
            }
        }
    }

    /**
     * La hoja desde abajo, como en la web angosta. [LocalHayTecladoFisico] se enciende a mano: esta
     * prueba corre en Android, donde por defecto está apagado (en el teléfono el panel no toma el
     * foco — ver `SelectorDeCategoriaEnAgregarTest`).
     */
    private fun montarAngosta() {
        repo()
        composeRule.setContent {
            CompositionLocalProvider(LocalHayTecladoFisico provides true) { MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (!cerrada) QuickAddScreen(onDismiss = { cerrada = true })
                    }
                    Spacer(Modifier.height(64.dp))
                }
            } }
        }
        composeRule.waitForIdle()
    }

    /** La hoja centrada, en la cáscara real, como en el escritorio. */
    private fun montarAncha() {
        repo()
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Dashboard,
                    activeTab = NavTab.HOY,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    Box(Modifier.fillMaxSize())
                    if (!cerrada) QuickAddScreen(onDismiss = { cerrada = true }, onSaved = { cerrada = true })
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun teclear(vararg teclas: Tecla) {
        composeRule.onRoot().performKeyInput { teclas.forEach { pressKey(it.key) } }
        composeRule.waitForIdle()
    }

    private fun esperarLaCuenta() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(ahorros.name, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tecladoEscribeYEnterGuarda() {
        esperarLaCuenta()
        // 1-2-5-0-0-0-7, Backspace: $125.000.
        teclear(*listOf(Key.One, Key.Two, Key.Five, Key.Zero, Key.Zero, Key.NumPad0, Key.Seven, Key.Backspace).map(::Tecla).toTypedArray())
        teclear(Tecla(Key.Enter))
        composeRule.waitUntil(5_000) { guardados.isNotEmpty() }
        assertEquals(125_000L, guardados.single().amount)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun a_390_los_digitos_escriben_el_monto_y_enter_guarda() {
        montarAngosta()
        tecladoEscribeYEnterGuarda()
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun a_1280_los_digitos_escriben_el_monto_y_enter_guarda() {
        montarAncha()
        tecladoEscribeYEnterGuarda()
        assertTrue(cerrada, "guardar cierra la hoja, como el botón")
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun enter_sin_monto_no_guarda_nada() {
        montarAncha()
        esperarLaCuenta()
        teclear(Tecla(Key.Enter))
        assertTrue(guardados.isEmpty(), "el botón está deshabilitado: Enter tampoco guarda")
        assertTrue(!cerrada)
    }

    /**
     * **Con la Nota abierta, las teclas son de la nota.** Si la hoja se las comiera, escribir
     * «Mercado 2» metería un 2 en el monto.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun con_la_nota_abierta_los_digitos_van_a_la_nota_y_no_al_monto() {
        montarAncha()
        esperarLaCuenta()
        teclear(Tecla(Key.Four))
        composeRule.onNodeWithText("Agregar nota…").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).assertIsFocused()

        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performKeyInput { pressKey(Key.Two) }
        composeRule.waitForIdle()
        val nota = composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals("2", nota, "el dígito entró en la nota")

        // Escape cierra la Nota; el monto sigue siendo el 4 de antes.
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()
        teclear(Tecla(Key.Enter))
        composeRule.waitUntil(5_000) { guardados.isNotEmpty() }
        assertEquals(4L, guardados.single().amount, "el 2 de la nota no se coló en el monto")
    }
}
