package com.jvillada.movi.ui.quickadd

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.height
import com.jvillada.movi.platform.BackHandlerEffect
import com.jvillada.movi.shared.model.MAX_CONCEPTO_LENGTH
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.theme.TextosDeMovi
import com.jvillada.movi.ui.AtrasCierraEstaHoja
import com.jvillada.movi.ui.LocalPilaDeHojas
import com.jvillada.movi.ui.PilaDeHojas
import com.jvillada.movi.ui.components.TAG_PANEL_DE_HOJA
import com.jvillada.movi.ui.components.TAG_ROTULO_DE_CELDA
import com.jvillada.movi.ui.components.tagDeCeldaDeCategoria
import com.jvillada.movi.ui.components.ds.TAG_CAJA_DEL_CAMPO
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
 * # Vuelta de diseño 13: Agregar, por dentro
 *
 * Los sub-editores de la hoja de «Agregar» —Categoría, Fecha, Nota y Cuenta— se habían quedado atrás
 * de la hoja: una «×» propia debajo de la de la hoja, rótulos de categoría cada uno a su tamaño, una
 * nota sin la caja del sistema con un «Guardar nota» que parecía apagado y media pantalla vacía.
 *
 * A 360 dp (el teléfono más angosto que se prueba) y con el motor de texto de verdad
 * (`GraphicsMode.NATIVE`, SDK 34: el de SDK 24 mide el texto con `lineHeight` en cero; ver
 * `RotuloDeCeldaNoSePartTest`). La hoja se monta con el mismo cableado del atrás que arma `App.kt`:
 * el `BackHandlerEffect` de la cáscara, la pila de hojas, y la hoja anotada en ella.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class AgregarPorDentroTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var cerrada by mutableStateOf(false)
    private val hojas = PilaDeHojas()

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                BackHandlerEffect(enabled = hojas.hayHojaAbierta) { hojas.cerrarLaDeArriba() }
                CompositionLocalProvider(LocalPilaDeHojas provides hojas) {
                    Box(Modifier.fillMaxSize()) {
                        if (!cerrada) {
                            AtrasCierraEstaHoja { cerrada = true }
                            QuickAddScreen(onDismiss = { cerrada = true })
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun volver() {
        composeRule.onNodeWithTag(TAG_VOLVER_DEL_SUB_EDITOR).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun apretarAtras() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun montoEnPantalla(texto: String) =
        composeRule.onAllNodes(hasText(texto), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    // ── 1. Un sub-editor se sale volviendo, y la hoja tiene una sola «×» ─────────────────

    @Test
    fun abrir_un_sub_editor_muestra_la_flecha_con_su_titulo_y_una_sola_x() {
        montar()
        listOf("Hoy" to "Fecha", "Agregar nota…" to "Nota", "Categoría" to "Categoría").forEach { (fila, titulo) ->
            tocar(fila)
            composeRule.onNodeWithTag(TAG_VOLVER_DEL_SUB_EDITOR).assertIsDisplayed()
            composeRule.onNodeWithText(titulo, useUnmergedTree = true).assertIsDisplayed()
            // La única «×» es la de la hoja (`SheetHandleWithClose`); el sub-editor ya no trae la suya.
            composeRule.onAllNodesWithContentDescription("Cerrar", useUnmergedTree = true)
                .assertCountEquals(1)
            // Y la flecha va a la izquierda del título, en la misma línea.
            val flecha = composeRule.onNodeWithTag(TAG_VOLVER_DEL_SUB_EDITOR).getUnclippedBoundsInRoot()
            val rotulo = composeRule.onNodeWithText(titulo, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(flecha.right <= rotulo.left, "«‹» va antes de «$titulo»: $flecha, $rotulo")
            assertTrue(rotulo.top >= flecha.top && rotulo.bottom <= flecha.bottom, "«$titulo» va en la línea de la flecha")
            volver()
            composeRule.onNodeWithTag(TAG_VOLVER_DEL_SUB_EDITOR).assertDoesNotExist()
        }
        assertFalse(cerrada, "volver de un sub-editor no cierra la hoja")
    }

    // ── 2. Atrás vuelve al formulario con el monto intacto ───────────────────────────────

    @Test
    fun atras_con_un_sub_editor_vuelve_al_formulario_con_el_monto_intacto() {
        montar()
        tocar("4")
        tocar("5")
        assertTrue(montoEnPantalla("$45"), "el monto escrito se ve")

        listOf("Agregar nota…", "Categoría", "Hoy").forEach { fila ->
            tocar(fila)
            assertEquals(2, hojas.cuantas, "el sub-editor «$fila» se anota encima de la hoja")
            apretarAtras()
            composeRule.onNodeWithTag(TAG_VOLVER_DEL_SUB_EDITOR).assertDoesNotExist()
            assertFalse(cerrada, "el atrás con «$fila» abierto vuelve al formulario, no cierra la hoja")
            assertEquals(1, hojas.cuantas)
            assertTrue(montoEnPantalla("$45"), "el monto sigue ahí después de volver de «$fila»")
        }

        // Sin sub-editor, el atrás cierra la hoja, como siempre.
        apretarAtras()
        assertTrue(cerrada)
    }

    /**
     * **El segundo toque de un doble toque, al volver, no le llega al formulario.** La Nota ya no
     * ocupa el alto del formulario, así que la hoja se estira al volver y bajo el dedo queda otra
     * cosa: una tecla, o «Guardar movimiento». Mientras dura la vuelta ([Movimiento.LARGO_MS]), el
     * formulario no toma toques; después, todo igual que siempre.
     *
     * Con toques de verdad (`performTouchInput`), no con la acción semántica: lo que se prueba es
     * justamente el camino del dedo, y la accesibilidad no pasa por ahí.
     */
    @Test
    fun al_volver_de_la_nota_un_toque_inmediato_no_escribe_en_el_monto() {
        montar()
        tocar("Agregar nota…")
        volver()
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).assertDoesNotExist()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText("7").performTouchInput { click() }
        composeRule.mainClock.advanceTimeByFrame()
        assertFalse(montoEnPantalla("$7"), "un toque dentro de la vuelta no tiene que escribir")

        composeRule.mainClock.advanceTimeBy(Movimiento.LARGO_MS.toLong() + 100)
        composeRule.onNodeWithText("7").performTouchInput { click() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue(montoEnPantalla("$7"), "pasada la vuelta, el teclado escribe como siempre")
    }

    // ── 3. Los rótulos de la cuadrícula, a la misma letra ────────────────────────────────

    @Test
    fun los_rotulos_de_la_cuadricula_tienen_el_mismo_tamano_a_360_dp() = rotulosALaMismaLetra()

    /**
     * A 390 dp —el teléfono del dueño, donde se vio— la cuadrícula daba cuatro columnas y
     * «Entretenimiento» no entraba: se achicaba solo él. Ahora la cuadrícula baja a tres.
     */
    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun los_rotulos_de_la_cuadricula_tienen_el_mismo_tamano_a_390_dp() = rotulosALaMismaLetra()

    private fun rotulosALaMismaLetra() {
        montar()
        tocar("Categoría")
        val rotulos = composeRule.onAllNodesWithTag(TAG_ROTULO_DE_CELDA, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(rotulos.size >= 8, "la cuadrícula muestra el catálogo (${rotulos.size} rótulos)")

        val layouts = rotulos.map(::layoutDe)
        val tamanos = layouts.map { it.layoutInput.style.fontSize }.toSet()
        assertEquals(setOf(TextosDeMovi().apoyo.fontSize), tamanos, "todos los rótulos a la letra de `apoyo`, sin achicarse")
        layouts.forEach { layout ->
            val texto = layout.layoutInput.text.text
            assertTrue(layout.lineCount <= 2, "«$texto» va en dos renglones como mucho")
            assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "«$texto» entra entero, sin «…»")
            (0 until layout.lineCount - 1).forEach { renglon ->
                val fin = layout.getLineEnd(renglon)
                assertTrue(texto[fin - 1] == ' ' || texto.getOrNull(fin) == ' ', "«$texto» se parte entre palabras")
            }
        }
        // «Entretenimiento», el que antes quedaba diminuto, se ve entero.
        composeRule.onNodeWithText("Entretenimiento", useUnmergedTree = true).assertIsDisplayed()

        // Las celdas de un mismo renglón miden lo mismo.
        val celdas = composeRule.onAllNodes(esCeldaDeCategoria, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertEquals(rotulos.size, celdas.size)
        celdas.groupBy { it.top }.values.forEach { renglon ->
            assertEquals(1, renglon.map { it.height }.toSet().size, "un renglón con celdas de alto distinto: $renglon")
        }
    }

    // ── 4. La nota: la caja del sistema, el principal encendido con texto, Enter guarda ───

    @Test
    fun la_nota_tiene_caja_y_con_texto_el_boton_esta_habilitado() {
        montar()
        tocar("Agregar nota…")

        val caja = composeRule.onNode(
            hasTestTag(TAG_CAJA_DEL_CAMPO) and hasAnyDescendant(hasTestTag(TAG_CAMPO_DE_NOTA)),
            useUnmergedTree = true,
        )
        caja.assertIsDisplayed()
        assertTrue(caja.getUnclippedBoundsInRoot().height >= Medida.objetivoTactil, "la caja llega a 48")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).assertIsFocused()

        composeRule.onNodeWithText(GUARDAR_NOTA).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performTextInput("Almuerzo")
        composeRule.onNodeWithText(GUARDAR_NOTA).assertIsEnabled()
    }

    /** Sin media pantalla vacía: el editor de la Nota termina donde termina su botón. */
    @Test
    fun la_nota_ocupa_solo_lo_que_necesita() {
        montar()
        tocar("Agregar nota…")
        val panel = composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA).getUnclippedBoundsInRoot()
        val boton = composeRule.onNodeWithText(GUARDAR_NOTA).getUnclippedBoundsInRoot()
        val sobra = panel.bottom - boton.bottom
        assertTrue(sobra <= Medida.entreSecciones, "debajo de «Guardar nota» quedan $sobra de hoja vacía")
    }

    @Test
    fun enter_guarda_la_nota() {
        montar()
        tocar("Agregar nota…")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performTextInput("Almuerzo con Ana")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).assertDoesNotExist()
        composeRule.onNodeWithText("Almuerzo con Ana", useUnmergedTree = true).assertIsDisplayed()
        assertFalse(cerrada)
    }

    @Test
    fun el_listo_del_teclado_del_telefono_tambien_guarda_la_nota() {
        montar()
        tocar("Agregar nota…")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performTextInput("Taxi")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performImeAction()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).assertDoesNotExist()
        composeRule.onNodeWithText("Taxi", useUnmergedTree = true).assertIsDisplayed()
    }

    /** El tope de la columna (`varchar(255)`): pegar algo más largo deja lo que entra, no nada. */
    @Test
    fun una_nota_pegada_demasiado_larga_se_recorta_al_tope() {
        montar()
        tocar("Agregar nota…")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performTextInput("a".repeat(400))
        val texto = composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals(MAX_CONCEPTO_LENGTH, texto.length)
    }

    /** Vaciar una nota que ya estaba es quitarla: ahí el botón sigue encendido. */
    @Test
    fun se_puede_guardar_la_nota_con_texto_o_para_quitar_la_que_habia() {
        assertFalse(sePuedeGuardarLaNota(inicial = "", escrita = ""))
        assertFalse(sePuedeGuardarLaNota(inicial = "", escrita = "   "))
        assertTrue(sePuedeGuardarLaNota(inicial = "", escrita = "Taxi"))
        assertTrue(sePuedeGuardarLaNota(inicial = "Taxi", escrita = ""))
    }

    private val esCeldaDeCategoria = SemanticsMatcher("celda de la cuadrícula de categorías") { nodo ->
        nodo.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(tagDeCeldaDeCategoria("")) == true
    }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        val accion = nodo.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(accion?.invoke(resultados) == true, "el rótulo no expuso su layout")
        return resultados.single()
    }
}
