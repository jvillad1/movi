package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.RecuerdoDeCategoria
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 5 — «Escribir el nombre sugiere la categoría», ejercida desde la hoja de «Agregar» de
 * verdad. [SugerenciaDeCategoriaTest] prueba [sugerenciaPorNombre] a secas; falta el cableado —que
 * [QuickAddScreen] cargue [com.jvillada.movi.data.MemoriaDeCategoriasCache], reaccione al cambio
 * de nota y no pise una categoría que el dueño ya eligió— que es justo lo que ningún test puro
 * puede ver.
 *
 * Mismo andamio que [HojaAgregarCategoriasFrecuentesTest]: `RepositorioDePrueba` para las cuentas
 * (y acá también para la memoria), `performSemanticsAction` para tocar filas y botones,
 * `performTextInput` para escribir (bajo Robolectric `performClick` no llega al composable).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD_SUGERENCIA)
class HojaAgregarSugerenciaDeCategoriaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_000_000)

    private val recuerdoMoraSoccer = RecuerdoDeCategoria(
        huella = "nombre:morasoccer",
        categoria = "Fútbol",
        nombre = "Mora Soccer",
        cuantos = 3,
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    /** El caso central del brief: escribir «Mora», que empareja por prefijo, pone «Fútbol». */
    @Test
    fun escribir_el_nombre_sugiere_la_categoria_y_muestra_la_linea() {
        montarHoja()

        escribirLaNota("Mora")

        composeRule.onNodeWithText("Fútbol").assertExists()
        composeRule.onNodeWithText("Movi la reconoce: Mora Soccer").assertExists()
    }

    /** Si la nota se borra después, la sugerencia se deshace y vuelve a lo que había antes. */
    @Test
    fun al_borrar_la_nota_la_sugerencia_se_deshace() {
        montarHoja()
        escribirLaNota("Mora")
        composeRule.onNodeWithText("Fútbol").assertExists()
        val categoriaDeAntes = "Comida" // primera del catálogo de gastos, sin datos de uso.

        borrarLaNota()

        composeRule.onNodeWithText(categoriaDeAntes).assertExists()
        composeRule.onNodeWithText("Movi la reconoce: Mora Soccer").assertDoesNotExist()
    }

    /** Si el dueño ya eligió la categoría a mano, la sugerencia no se la pisa. */
    @Test
    fun con_la_categoria_elegida_a_mano_no_la_pisa() {
        montarHoja()
        elegirCategoriaAMano("Transporte")

        escribirLaNota("Mora")

        composeRule.onNodeWithText("Transporte").assertExists()
        composeRule.onNodeWithText("Fútbol").assertDoesNotExist()
        composeRule.onNodeWithText("Movi la reconoce: Mora Soccer").assertDoesNotExist()
    }

    /**
     * Una categoría que el dueño escondió en «Más → Categorías» no se sugiere, aunque la huella
     * matchee exacto — [sugerenciaPorNombre] es pura y solo filtra reservadas (ver su KDoc); lo
     * escondido lo filtra esta pantalla ANTES de llamarla, con `UsedCategoriesCache.prefs`.
     */
    @Test
    fun una_categoria_escondida_no_se_sugiere() {
        UsedCategoriesCache.applyPref("Fútbol", CategoryPref(hidden = true))
        montarHoja()
        val categoriaDeAntes = "Comida" // primera del catálogo de gastos, sin datos de uso.

        escribirLaNota("Mora Soccer") // huella exacta: nombre:morasoccer

        composeRule.onNodeWithText(categoriaDeAntes).assertExists()
        composeRule.onNodeWithText("Fútbol").assertDoesNotExist()
        composeRule.onNodeWithText("Movi la reconoce: Mora Soccer").assertDoesNotExist()
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private fun montarHoja() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            override suspend fun getMemoriaDeCategorias(): List<RecuerdoDeCategoria> =
                listOf(recuerdoMoraSoccer)
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        QuickAddScreen(onDismiss = {})
                    }
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Abre el sub-picker de «Nota», escribe y confirma con «Guardar nota». */
    private fun escribirLaNota(texto: String) {
        tocar("Nota")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performTextInput(texto)
        tocar("Guardar nota")
    }

    /** Abre el sub-picker de «Nota», BORRA lo que tenía y confirma. */
    private fun borrarLaNota() {
        tocar("Nota")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performTextClearance()
        tocar("Guardar nota")
    }

    /** Abre el sub-picker de «Categoría» y escribe una a mano — con foco, tipear reemplaza. */
    private fun elegirCategoriaAMano(nombre: String) {
        tocar("Categoría")
        composeRule.onNode(hasSetTextAction()).performTextInput(nombre)
        cerrarSubPicker()
    }

    // Mismo motivo que en `HojaAgregarGeometriaTest`: bajo Robolectric `performClick()` no llega
    // al composable, así que se usa la acción semántica de clic.
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun cerrarSubPicker() {
        composeRule.onNodeWithTag(TAG_CERRAR_SUB_PICKER)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}

/** El AVD `Movi_Sensor` — mismo tamaño y mismo motivo que en `HojaAgregarEligeLaCuentaTest`. */
private const val TELEFONO_DEL_AVD_SUGERENCIA = "w411dp-h731dp-xhdpi"
