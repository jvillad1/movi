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
import com.jvillada.movi.shared.model.TransactionType
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

    /** «Comida» es del catálogo y SOLO de gasto — a diferencia de «Fútbol», que es propia y sin
     *  tipo fijado (por eso [seOfreceParaTipo] la deja pasar para cualquier tipo). Sirve para
     *  probar que la sugerencia SÍ respeta el tipo de la pestaña — ver fix round 1, hallazgo 2. */
    private val recuerdoCrepes = RecuerdoDeCategoria(
        huella = "nombre:crepesywaffles",
        categoria = "Comida",
        nombre = "Crepes & Waffles",
        cuantos = 5,
    )

    /** Misma categoría que [recuerdoMoraSoccer] (mismo grupo que «Fútbol»), escrita distinto —
     *  sin tilde y en mayúsculas — para probar que esconder compara normalizado. */
    private val recuerdoPizzaPalace = RecuerdoDeCategoria(
        huella = "nombre:pizzapalace",
        categoria = "FUTBOL",
        nombre = "Pizza Palace",
        cuantos = 2,
    )

    /** «Arriendo recibido» es del catálogo y de ingreso: se ofrece en la pestaña Ingreso. */
    private val recuerdoInquilino = RecuerdoDeCategoria(
        huella = "nombre:inquilinoperez",
        categoria = "Arriendo recibido",
        nombre = "Inquilino Pérez",
        cuantos = 4,
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

    /**
     * Fix round 1, hallazgo 2: una categoría del catálogo que SOLO sirve para gasto («Comida»)
     * no se sugiere anotando un ingreso, aunque el nombre matchee. Antes el filtro solo miraba
     * `categoryPrefs[…]?.hidden` por clave exacta — ni el tipo ni la normalización entraban.
     */
    @Test
    fun en_la_pestana_ingreso_no_sugiere_una_categoria_que_solo_sirve_para_gasto() {
        montarHoja(recuerdos = listOf(recuerdoCrepes))
        tocar("Ingreso")
        val categoriaDeIngresoPorDefecto = "Salario" // primera del catálogo de ingresos.

        escribirLaNota("Crepes") // huella nombre:crepesywaffles, prefijo de sobra (>= 4).

        composeRule.onNodeWithText(categoriaDeIngresoPorDefecto).assertExists()
        composeRule.onNodeWithText("Comida").assertDoesNotExist()
        composeRule.onNodeWithText("Movi la reconoce: Crepes & Waffles").assertDoesNotExist()
    }

    /** La misma categoría escondida, escrita sin tilde y en mayúsculas: esconder compara normalizado. */
    @Test
    fun una_categoria_escondida_con_otra_capitalizacion_o_tildes_no_se_sugiere() {
        UsedCategoriesCache.applyPref("Fútbol", CategoryPref(hidden = true))
        montarHoja(recuerdos = listOf(recuerdoPizzaPalace)) // categoria = "FUTBOL", sin tilde

        escribirLaNota("Pizza Palace") // huella exacta: nombre:pizzapalace

        composeRule.onNodeWithText("FUTBOL").assertDoesNotExist()
        composeRule.onNodeWithText("Movi la reconoce: Pizza Palace").assertDoesNotExist()
    }

    /**
     * Fix round 1, hallazgo 5 — la otra mitad del commit `e610d394`: cuando la reconciliación de
     * Gasto↔Ingreso pisa la categoría por su cuenta, la sugerencia vigente se limpia con ella. Si
     * no se limpiara, la línea de apoyo seguiría hablando de un nombre («Crepes & Waffles») que ya
     * no tiene nada que ver con la categoría que quedó puesta («Salario»).
     */
    @Test
    fun al_cambiar_de_tipo_la_reconciliacion_limpia_el_estado_de_la_sugerencia() {
        montarHoja(recuerdos = listOf(recuerdoCrepes))
        escribirLaNota("Crepes")
        composeRule.onNodeWithText("Comida").assertExists()
        composeRule.onNodeWithText("Movi la reconoce: Crepes & Waffles").assertExists()

        tocar("Ingreso") // «Comida» no sirve para ingreso: la reconciliación la reemplaza sola.

        composeRule.onNodeWithText("Salario").assertExists()
        composeRule.onNodeWithText("Movi la reconoce: Crepes & Waffles").assertDoesNotExist()
    }

    /**
     * Fix round 2 — la regresión que introdujo el round anterior. «Fútbol» es propia, sin tipo
     * fijado y usada SOLO en gastos: bajo `categoriaSirveParaTipo` (la de la reconciliación,
     * permisiva con una categoría propia sin nada fijado) «sirve» para cualquier tipo, así que la
     * reconciliación no la toca al cambiar de pestaña ni limpia la sugerencia vigente. Sin el
     * arreglo, cuando la sugerencia dejaba de aplicar en Ingreso (por `seOfreceParaTipo`, que sí
     * mira el uso) el efecto volvía a «lo de antes» — la categoría de GASTO («Comida»)— y la
     * colaba sobre un ingreso. Ahora, si la pestaña cambió desde que se aplicó la sugerencia, no
     * hay «antes» que valga: cae al valor por defecto de la pestaña nueva.
     */
    @Test
    fun al_cambiar_a_ingreso_no_restaura_la_categoria_de_gasto_de_antes() {
        UsedCategoriesCache.record("Fútbol", TransactionType.EXPENSE) // propia, usada SOLO en gastos
        montarHoja() // recuerdoMoraSoccer: nombre:morasoccer -> Fútbol
        escribirLaNota("Mora")
        composeRule.onNodeWithText("Fútbol").assertExists() // sugerencia aplicada, todavía en Gasto

        tocar("Ingreso") // la nota sigue diciendo "Mora": no cambió, cambió la pestaña.

        composeRule.onNodeWithText("Comida").assertDoesNotExist() // NO la categoría de gasto de antes
        composeRule.onNodeWithText("Fútbol").assertDoesNotExist() // ni la sugerida (no sirve en Ingreso)
        composeRule.onNodeWithText("Salario").assertExists() // el valor por defecto de Ingreso
        composeRule.onNodeWithText("Movi la reconoce: Mora Soccer").assertDoesNotExist()
    }

    /**
     * Revisión final: cuando la reconciliación Gasto↔Ingreso reemplaza por su cuenta una
     * categoría que el dueño había elegido a mano, la que queda ya no es «de él» — la puso la app
     * — y la pestaña nueva tiene que poder recibir una sugerencia por nombre. Antes la marca de
     * «a mano» sobrevivía al cambio de pestaña y la sugerencia no llegaba nunca.
     */
    @Test
    fun si_la_reconciliacion_reemplaza_la_eleccion_a_mano_la_pestana_nueva_acepta_sugerencias() {
        montarHoja(recuerdos = listOf(recuerdoInquilino))
        elegirCategoriaAMano("Transporte") // del catálogo, SOLO de gasto.

        tocar("Ingreso") // «Transporte» no sirve para ingreso: la reconciliación pone «Salario».
        composeRule.onNodeWithText("Salario").assertExists()

        escribirLaNota("Inquilino")

        composeRule.onNodeWithText("Arriendo recibido").assertExists()
        composeRule.onNodeWithText("Movi la reconoce: Inquilino Pérez").assertExists()
    }

    /**
     * La otra mitad: si la reconciliación CONSERVA la elección a mano (una categoría propia sin
     * tipo fijado sirve para los dos lados), sigue siendo del dueño y la sugerencia no la pisa.
     */
    @Test
    fun si_la_reconciliacion_conserva_la_eleccion_a_mano_la_sugerencia_no_la_pisa() {
        montarHoja(recuerdos = listOf(recuerdoInquilino))
        elegirCategoriaAMano("Plata de la tía") // propia, nueva, sin tipo fijado.

        tocar("Ingreso")
        composeRule.onNodeWithText("Plata de la tía").assertExists()

        escribirLaNota("Inquilino")

        composeRule.onNodeWithText("Plata de la tía").assertExists()
        composeRule.onNodeWithText("Arriendo recibido").assertDoesNotExist()
        composeRule.onNodeWithText("Movi la reconoce: Inquilino Pérez").assertDoesNotExist()
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private fun montarHoja(recuerdos: List<RecuerdoDeCategoria> = listOf(recuerdoMoraSoccer)) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            override suspend fun getMemoriaDeCategorias(): List<RecuerdoDeCategoria> = recuerdos
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
