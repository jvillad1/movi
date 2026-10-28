package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import com.jvillada.movi.ui.components.LocalPunteroFino
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.components.TAG_PANEL_DE_HOJA
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 34: frente a un computador, «Agregar» no dibuja un teclado de calculadora
 *
 * A 1.280 px la hoja medía 600 × 720 y casi la mitad era el teclado numérico en pantalla, con un
 * teclado físico al lado. Ahora, con el puntero principal fino ([LocalPunteroFino]: un mouse o un
 * trackpad), el teclado en pantalla no se dibuja, el monto lleva un cursor y se escribe con el
 * teclado físico —por el mismo `onKey` de las teclas en pantalla—, y la hoja se acorta. En el
 * teléfono y en una tablet que se toca no cambia nada.
 *
 * [LocalPunteroFino] se enciende a mano: esta prueba corre en Android, donde vale `false`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AgregarConElTecladoDeVerdadTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val guardados = mutableListOf<FinancialEvent>()
    private var cerrada = false

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    @Config(qualifiers = ESCRITORIO)
    fun en_el_escritorio_no_hay_teclado_en_pantalla_y_el_monto_lleva_cursor() {
        montarAncha(punteroFino = true)
        assertSinTecladoEnPantalla()
        val cursor = composeRule.onNodeWithTag(TAG_CURSOR_DEL_MONTO, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val monto = composeRule.onNode(hasText("$0"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(cursor.left >= monto.right - 0.5.dp, "el cursor va a la derecha de la cifra: $cursor, $monto")
        assertTrue(cursor.top < monto.bottom && cursor.bottom > monto.top, "y en su renglón: $cursor, $monto")
    }

    /** **Al abrir, lo que se teclea va al monto** — sin hacer clic en nada antes. */
    @Test
    @Config(qualifiers = ESCRITORIO)
    fun escribir_4_2_0_0_0_deja_42000_y_retroceso_borra_el_ultimo() {
        montarAncha(punteroFino = true)
        teclear(listOf(Key.Four, Key.Two, Key.Zero, Key.Zero, Key.Zero))
        assertTrue(montoEnPantalla("$42.000"), "cinco teclas, $42.000")

        teclear(listOf(Key.Backspace))
        assertTrue(montoEnPantalla("$4.200"), "Retroceso borra el último dígito")

        teclear(listOf(Key.Enter))
        composeRule.waitUntil(5_000) { guardados.isNotEmpty() }
        assertEquals(4_200L, guardados.single().amount)
        assertTrue(cerrada, "Enter guarda y cierra, como el botón")
    }

    /** **La hoja se acorta a lo que tiene:** sin las cuatro filas de teclas, ~200 dp menos. */
    @Test
    @Config(qualifiers = ESCRITORIO)
    fun sin_teclado_en_pantalla_la_hoja_se_acorta() {
        var fino by mutableStateOf(false)
        montarAncha(punteroFino = { fino })
        val conTeclado = altoDelPanel()
        fino = true
        composeRule.waitForIdle()
        assertSinTecladoEnPantalla()
        val sinTeclado = altoDelPanel()
        // Cuatro filas de 50 y el respiro de debajo: 212. Con 200 alcanza para decir que se fueron.
        assertTrue(conTeclado - sinTeclado >= 200f, "la hoja pasó de $conTeclado a $sinTeclado dp")
    }

    /** **Una tablet que se toca** es ancha, pero su puntero no es fino: el teclado se queda. */
    @Test
    @Config(qualifiers = ESCRITORIO)
    fun en_una_tablet_que_se_toca_el_teclado_en_pantalla_sigue() {
        montarAncha(punteroFino = false)
        assertConTecladoEnPantalla()
    }

    @Test
    @Config(qualifiers = TELEFONO)
    fun en_el_telefono_el_teclado_en_pantalla_sigue_y_escribe() {
        montarTelefono()
        assertConTecladoEnPantalla()
        listOf("4", "2", "000").forEach { tecla ->
            composeRule.onNodeWithText(tecla).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
        assertTrue(montoEnPantalla("$42.000"))
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────────────

    private fun assertSinTecladoEnPantalla() {
        (0..9).forEach { digito ->
            assertEquals(
                0,
                composeRule.onAllNodesWithText("$digito").fetchSemanticsNodes().size,
                "la tecla «$digito» no se dibuja frente a un computador",
            )
        }
        composeRule.onAllNodesWithText("000").assertCountIs(0, "«000»")
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Borrar").fetchSemanticsNodes().size, "ni «⌫»")
    }

    private fun assertConTecladoEnPantalla() {
        (0..9).forEach { digito ->
            assertEquals(1, composeRule.onAllNodesWithText("$digito").fetchSemanticsNodes().size, "la tecla «$digito»")
        }
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Borrar").fetchSemanticsNodes().size, "«⌫»")
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_CURSOR_DEL_MONTO, useUnmergedTree = true).fetchSemanticsNodes().size, "sin cursor")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountIs(n: Int, que: String) =
        assertEquals(n, fetchSemanticsNodes().size, que)

    private fun repo() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
                guardados += event
                return event
            }
        }
    }

    private fun montarAncha(punteroFino: Boolean) = montarAncha(punteroFino = { punteroFino })

    /** La hoja centrada, en la cáscara real, como en el escritorio. */
    private fun montarAncha(punteroFino: () -> Boolean) {
        repo()
        composeRule.setContent {
            CompositionLocalProvider(LocalPunteroFino provides punteroFino()) {
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
        }
        esperarLaCuenta()
    }

    private fun montarTelefono() {
        repo()
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
        esperarLaCuenta()
    }

    private fun esperarLaCuenta() {
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(ahorros.name, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Una lista y no un `vararg`: `Key` es una value class y Kotlin no la deja ir en uno. */
    private fun teclear(teclas: List<Key>) {
        composeRule.onRoot().performKeyInput { teclas.forEach { pressKey(it) } }
        composeRule.waitForIdle()
    }

    private fun montoEnPantalla(texto: String) =
        composeRule.onAllNodes(hasText(texto), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun altoDelPanel(): Float {
        val panel = composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA).getUnclippedBoundsInRoot()
        return (panel.bottom - panel.top).value
    }
}

private const val ESCRITORIO = "w1280dp-h900dp-mdpi"
private const val TELEFONO = "w390dp-h844dp-xhdpi"
