package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_BOTON
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TAG_PILDORA_DE_FILTRO
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta de diseño 20: Agregar, lo que quedaba hecho a mano
 *
 * El teclado, el monto y la lógica de guardar no se tocaron (los vigilan `HojaAgregarGeometriaTest`,
 * `AgregarPorDentroTest` y las del teclado). Lo que se fija acá:
 *
 * - **los chips de categoría son `PildoraDeFiltro`** —la elegida dice «elegida»— y «Nueva» y la
 *   fecha son `PildoraDeAccion`;
 * - **«Guardar y anotar otro» es un `Boton` `Fantasma`** (era un `Text` tocable) y no le cambia el
 *   alto a la hoja: su renglón reservado mide lo mismo con el botón o con «Falta el monto»;
 * - **«Categoría», «Cuenta» y «Nota» son `Fila`** compactas, de al menos el objetivo táctil;
 * - **«Subir comprobante» es un `BotonDeIcono`** de 48 × 48 con su descripción.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h914dp-xhdpi")
class AgregarEnElSistemaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montarHoja() {
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Mercado", listOf(TransactionType.EXPENSE), usosRecientes = 10),
                UsedCategory("Transporte", listOf(TransactionType.EXPENSE), usosRecientes = 3),
            ),
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
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
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(ahorros.name, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun los_chips_de_categoria_son_pildoras_del_sistema() {
        montarHoja()
        val filtros = composeRule.onAllNodesWithTag(TAG_PILDORA_DE_FILTRO).fetchSemanticsNodes()
        val textos = filtros.map { it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } }
        assertTrue("Transporte" in textos, "«Transporte» es un PildoraDeFiltro: $textos")

        // Elegir un chip lo marca como elegido (la semántica de un filtro, no un color).
        composeRule.onNode(hasTestTag(TAG_PILDORA_DE_FILTRO) and hasText("Transporte"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val elegido = composeRule.onNode(hasTestTag(TAG_PILDORA_DE_FILTRO) and hasText("Transporte"))
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        assertEquals(true, elegido)

        // «Nueva» y la fecha hacen algo: son PildoraDeAccion.
        composeRule.onNodeWithTag(TAG_PASTILLA_NUEVA_CATEGORIA_AGREGAR).assertExists()
        composeRule.onNode(hasTestTag(TAG_FECHA_EN_AGREGAR) and hasText("Hoy")).assertExists()
    }

    @Test
    fun guardar_y_anotar_otro_es_un_Boton_fantasma_y_no_mueve_la_hoja() {
        montarHoja()
        val tecla = { composeRule.onNodeWithText("9").fetchSemanticsNode().boundsInRoot }
        val antes = tecla()

        tocar("8")
        val boton = composeRule.onNode(hasTestTag(TAG_GUARDAR_Y_ANOTAR_OTRO) and hasText(GUARDAR_Y_ANOTAR_OTRO))
            .fetchSemanticsNode()
        assertEquals(VarianteDeBoton.Fantasma, boton.config.getOrNull(VarianteDeBotonKey))
        val alto = with(composeRule.density) { boton.size.height.toDp() }
        assertTrue(alto >= TamanoDeBoton.S.alto - 0.5.dp, "el botón mide lo suyo (${alto}), no el renglón de 16")

        assertEquals(antes, tecla(), "aparecer «Guardar y anotar otro» no movió el teclado")
    }

    @Test
    fun categoria_cuenta_y_nota_son_filas_del_sistema() {
        montarHoja()
        listOf("Categoría", "Cuenta", "Nota").forEach { titulo ->
            val fila = composeRule.onNode(hasTestTag(TAG_FILA) and hasText(titulo)).fetchSemanticsNode()
            val alto = with(composeRule.density) { fila.size.height.toDp() }
            // Compacta (48, lo que medían los `CardRow` de antes): la hoja no puede crecer (ver
            // `HojaAgregarGeometriaTest`); igual llega al objetivo táctil.
            assertTrue(alto >= Medida.objetivoTactil - 0.5.dp, "«$titulo» mide $alto, menos que el objetivo táctil")
        }
    }

    @Test
    fun subir_comprobante_es_un_BotonDeIcono_de_48() {
        montarHoja()
        val nodo = composeRule.onNodeWithTag(TAG_SUBIR_PAPEL_EN_AGREGAR).fetchSemanticsNode()
        val lado = with(composeRule.density) { nodo.size.width.toDp() }
        assertEquals(Medida.objetivoTactil.value, lado.value, 0.5f)
        val descripcion = nodo.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertTrue("Subir comprobante o extracto" in descripcion, "el ícono solo dice qué hace: $descripcion")
        // El botón principal sigue siendo el `Boton` de siempre.
        composeRule.onNode(hasTestTag(TAG_BOTON) and hasText("Guardar movimiento")).assertExists()
    }
}
