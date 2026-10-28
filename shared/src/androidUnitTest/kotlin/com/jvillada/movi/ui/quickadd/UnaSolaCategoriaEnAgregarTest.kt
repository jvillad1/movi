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
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.tagDeCeldaDeCategoria
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 34: la categoría se lee en un solo lugar
 *
 * Debajo del monto había una fila «Categoría · Restaurantes ›» y, justo debajo, los chips con
 * «Restaurantes» marcado: lo mismo dos veces. Quedan los chips —la elegida marcada— con el rótulo
 * «Categoría» encima, y al final «Todas ›», que abre el selector entero. Y a 360 dp, elegir una
 * categoría o una cuenta no corta nada ni cambia el alto de la hoja (la hoja está anclada abajo:
 * cualquier cambio de alto mueve el teclado bajo el dedo).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_CHICO)
class UnaSolaCategoriaEnAgregarTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val nu = Account("a2", "Nu", AccountType.SAVINGS, 2_000_000)

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun la_categoria_elegida_se_ve_una_sola_vez_y_marcada() {
        montarHoja()
        assertEquals(1, composeRule.onAllNodesWithText("Mercado").fetchSemanticsNodes().size, "«Mercado» una sola vez")
        assertEquals(true, elegida("Mercado"))
        // «Categoría» es el rótulo de los chips, no una fila que se toca.
        composeRule.onNode(hasTestTag(TAG_FILA) and hasText(ROTULO_CATEGORIA_EN_AGREGAR)).assertDoesNotExist()
        composeRule.onNode(hasTestTag(TAG_ENCABEZADO_DE_LA_CATEGORIA) and hasAnyDescendant(hasText(ROTULO_CATEGORIA_EN_AGREGAR)), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun todas_va_al_final_de_los_chips_y_abre_el_selector() {
        montarHoja()
        val todas = composeRule.onNodeWithTag(TAG_PASTILLA_TODAS_LAS_CATEGORIAS_AGREGAR).getUnclippedBoundsInRoot()
        listOf("Mercado", "Transporte").forEach { chip ->
            val bordes = composeRule.onNodeWithText(chip).getUnclippedBoundsInRoot()
            assertTrue(todas.left >= bordes.right, "«Todas» va después de «$chip»")
        }

        composeRule.onNodeWithTag(TAG_PASTILLA_TODAS_LAS_CATEGORIAS_AGREGAR).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_VOLVER_DEL_SUB_EDITOR).assertExists()
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Mercado")).assertExists()
    }

    /** Una que no está entre los chips, elegida en el selector, vuelve primera y marcada. */
    @Test
    fun la_elegida_en_el_selector_que_no_es_frecuente_va_primera_y_marcada() {
        montarHoja()
        tocar(TODAS_LAS_CATEGORIAS)
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Salud")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("Salud").fetchSemanticsNodes().size)
        assertEquals(true, elegida("Salud"))
        val salud = composeRule.onNodeWithText("Salud").getUnclippedBoundsInRoot()
        val mercado = composeRule.onNodeWithText("Mercado").getUnclippedBoundsInRoot()
        assertTrue(salud.right <= mercado.left, "«Salud» va antes que las frecuentes: $salud, $mercado")
        composeRule.onNodeWithText("Salud").assertSeVeEntera("«Salud»")
    }

    /** **A 360 dp, elegir una categoría o una cuenta no mueve nada ni corta el pie.** */
    @Test
    fun a_360_elegir_una_categoria_o_una_cuenta_no_cambia_el_alto() {
        montarHoja(cuentas = listOf(ahorros, nu))
        composeRule.onNodeWithText(GUARDAR).assertSeVeEntera("«$GUARDAR»")
        val tecla = { composeRule.onNodeWithText("9").getUnclippedBoundsInRoot() }
        val antes = tecla()

        tocar("Transporte")
        assertEquals(true, elegida("Transporte"))
        assertMismoLugar(antes, tecla(), "elegir un chip")

        tocar(TODAS_LAS_CATEGORIAS)
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Salud")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertMismoLugar(antes, tecla(), "elegir en el selector")

        tocar("Cuenta")
        composeRule.onNodeWithTag(tagDeFilaDeCuentaEnAgregar(nu.id)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(nu.name).assertExists()
        assertMismoLugar(antes, tecla(), "elegir otra cuenta")
        composeRule.onNodeWithText(GUARDAR).assertSeVeEntera("«$GUARDAR» después de elegir")
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────────────

    private fun montarHoja(cuentas: List<Account> = listOf(ahorros)) {
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Mercado", listOf(TransactionType.EXPENSE), usosRecientes = 10),
                UsedCategory("Transporte", listOf(TransactionType.EXPENSE), usosRecientes = 3),
            ),
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = cuentas
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

    private fun elegida(texto: String): Boolean? =
        composeRule.onNodeWithText(texto).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)

    private fun assertMismoLugar(antes: DpRect, despues: DpRect, que: String) {
        assertTrue(
            abs(antes.top.value - despues.top.value) < 0.5f && abs(antes.bottom.value - despues.bottom.value) < 0.5f,
            "$que movió el teclado: $antes → $despues",
        )
    }

    private fun SemanticsNodeInteraction.assertSeVeEntera(que: String) {
        assertIsDisplayed()
        val recortado = getBoundsInRoot()
        val entero = getUnclippedBoundsInRoot()
        assertTrue(
            abs(recortado.left.value - entero.left.value) < 0.5f && abs(recortado.right.value - entero.right.value) < 0.5f &&
                abs(recortado.top.value - entero.top.value) < 0.5f && abs(recortado.bottom.value - entero.bottom.value) < 0.5f,
            "$que no se ve entero: se ve $recortado de $entero",
        )
    }
}

private const val TELEFONO_CHICO = "w360dp-h740dp-xhdpi"
private const val GUARDAR = "Guardar movimiento"
