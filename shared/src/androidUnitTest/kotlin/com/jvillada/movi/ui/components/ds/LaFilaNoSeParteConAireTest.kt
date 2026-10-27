package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.AvatarDeLaCuenta
import com.jvillada.movi.ui.quickadd.WalletPicker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # «Bancolombia Ahorros» en un renglón cuando sobra aire (vuelta de diseño 15)
 *
 * A 390 dp, en Patrimonio y en el selector de cuenta de Agregar, la fila con la marca del banco,
 * «Bancolombia Ahorros» y «$13.845.800» partía el título en dos con la fila medio vacía: le
 * faltaban unos 2 px. Es la cuenta que el dueño más usa, así que se veía todo el tiempo.
 *
 * Se monta la [Fila] como la monta Patrimonio (`GrupoDeCuentas`): en una [Superficie] de lista,
 * dentro del margen de la pantalla, con la letra de la app (×1,12, [ConClaseDeAncho]). Motor de
 * texto real: `sdk = [34]` y `NATIVE`, como `LasAccionesDelInicioEnUnRenglonTest`.
 *
 * A 360 dp o con la letra del sistema agrandada **puede** seguir en dos renglones —eso está
 * permitido—; lo que no puede es partirse con aire de sobra.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class LaFilaNoSeParteConAireTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(escala: Float = 1.12f, nombre: String = "Bancolombia Ahorros") {
        composeRule.setContent {
            ConClaseDeAncho(escala = escala) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla)) {
                        Superficie(relleno = RellenoDeSuperficie.Lista) {
                            Fila(
                                titulo = nombre,
                                inicio = { AvatarDeLaCuenta(nombre = nombre, tipo = AccountType.SAVINGS) },
                                final = { Cifra(13_845_800) },
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    private fun titulo(nombre: String = "Bancolombia Ahorros"): SemanticsNode =
        composeRule.onNode(hasText(nombre), useUnmergedTree = true).fetchSemanticsNode()

    @Test
    fun `a 390 dp con la letra de la app el titulo va en un renglon`() {
        montar()
        val nodo = titulo()
        val d = disposicion(nodo)
        assertEquals(1, d.lineCount, "«Bancolombia Ahorros» se parte con aire de sobra")
        assertFalse(d.isLineEllipsized(0))
        // Y no por un pelo: acá (Android) antes entraba con 1 dp de sobra, y en la web —que dibuja la
        // misma letra un par de px más ancha— no entraba. Ahora sobra más que [Espacio.xxs].
        val cifra = composeRule.onNode(hasTestTag(TAG_CIFRA), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        with(composeRule.density) {
            val sobra = cifra.left - ESPACIO_ANTES_DEL_FINAL_DE_FILA.toPx() - nodo.boundsInRoot.right
            assertTrue(sobra >= Espacio.xxs.toPx(), "sobran apenas ${sobra / density} dp")
        }
    }

    /**
     * **De dónde salieron los px.** El aire entre el título y el final es [Espacio.xs] (8): era
     * [Espacio.s] (12). El relleno (16), el avatar (40) y el espacio entre el avatar y el texto (12)
     * no cambiaron: las líneas que cuelgan de una fila siguen alineadas con su texto.
     */
    @Test
    fun `el aire entre el titulo y la cifra es el chico, y el resto no cambio`() {
        // Un nombre que llena su columna (dos renglones), para que el borde del texto sea el de la columna.
        val largo = "Bancolombia Ahorros de la casa y del colegio"
        montar(nombre = largo)
        val densidad = composeRule.density
        val fila = composeRule.onNode(hasTestTag(TAG_FILA), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val texto = titulo(largo).boundsInRoot
        val cifra = composeRule.onNode(hasTestTag(TAG_CIFRA), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        with(densidad) {
            assertEquals(
                (Medida.margenDePantalla + Medida.avatar + ESPACIO_ANTES_DEL_TEXTO_DE_FILA).toPx(),
                texto.left - fila.left,
                0.5f,
            )
            // El título ocupa todo lo que la cifra le deja: termina 8 dp antes de ella.
            assertEquals(2, disposicion(titulo(largo)).lineCount)
            assertEquals(Espacio.xs.toPx(), cifra.left - texto.right, 0.5f)
            assertEquals(Medida.margenDePantalla.toPx(), fila.right - cifra.right, 0.5f)
        }
    }

    /**
     * **El selector de cuenta de Agregar, igual.** Reservaba 28 dp al final de cada fila para la
     * verificación de la elegida, y ahí sí no entraba ni con el aire nuevo: la verificación pasó a
     * la marca. Montado como en la hoja (su relleno es el margen de la pantalla).
     */
    @Test
    fun `en el selector de cuenta de Agregar tambien va en un renglon`() {
        val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800)
        val nu = Account("n1", "Nu", AccountType.SAVINGS, 2_300_000)
        composeRule.setContent {
            ConClaseDeAncho {
                Column(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla)) {
                    WalletPicker(
                        cuentas = cuentasPara(listOf(ahorros, nu), UsoDeCuenta.ORIGEN_DE_GASTO),
                        uso = UsoDeCuenta.ORIGEN_DE_GASTO,
                        selectedId = nu.id,
                        onPick = {},
                        onClose = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val d = disposicion(titulo())
        assertEquals(1, d.lineCount, "en Agregar «Bancolombia Ahorros» se parte con aire de sobra")
        assertFalse(d.isLineEllipsized(0))
    }

    /**
     * A 360 dp, o a 390 con la letra del sistema agrandada (×1,3), **puede** bajar a dos renglones
     * —está permitido—, pero partido entre palabras: ni «…» ni «Bancolom-bia».
     */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 dp puede partirse en dos renglones pero entre palabras`() {
        montar()
        enDosRenglonesEntrePalabras(disposicion(titulo()))
    }

    @Test
    fun `a 390 dp con la letra agrandada puede partirse en dos renglones pero entre palabras`() {
        montar(escala = 1.3f * 1.12f)
        enDosRenglonesEntrePalabras(disposicion(titulo()))
    }

    private fun enDosRenglonesEntrePalabras(d: TextLayoutResult) {
        assertTrue(d.lineCount <= LINEAS_DEL_TITULO_DE_FILA, "más de dos renglones")
        assertFalse(d.isLineEllipsized(d.lineCount - 1), "se cortó con «…»")
        if (d.lineCount == 2) assertEquals("Bancolombia ".length, d.getLineEnd(0), "se partió dentro de una palabra")
        composeRule.onNode(hasText("$13.845.800"), useUnmergedTree = true).assertExists()
    }
}
