package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.CategoryField
import com.jvillada.movi.ui.components.TAG_BUSCAR_CATEGORIA
import com.jvillada.movi.ui.components.TAG_CAMPO_DE_CATEGORIA
import com.jvillada.movi.ui.components.TAG_CREAR_CATEGORIA
import com.jvillada.movi.ui.components.tagDeCeldaDeCategoria
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ola B · Task 4 — **el selector de categoría es una cuadrícula**, ejercido desde la hoja de
 * «Agregar» de verdad y desde el [CategoryField] que usan las otras hojas. El orden y el filtro
 * tienen sus pruebas puras en `SelectorDeCategoriaTest`; acá va lo que solo se ve montado: que la
 * búsqueda no se lleve el foco al abrir, que las frecuentes queden ARRIBA en pantalla, y que tocar
 * una celda elija y cierre.
 *
 * Mismo andamio que [HojaAgregarCategoriasFrecuentesTest]: `RepositorioDePrueba` para las cuentas,
 * `performSemanticsAction` para tocar (bajo Robolectric `performClick` no llega al composable).
 * `UsedCategoriesCache` se ensucia sin limpiar a propósito: `AppDePrueba` lo deja en cero antes de
 * cada método.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD_SELECTOR)
class SelectorDeCategoriaEnAgregarTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_000_000)

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    /**
     * **El corazón de la tarea.** El sub-picker viejo pedía el foco al abrirse, y en un teléfono
     * eso es el teclado del sistema tapando la mitad de las categorías para elegir con un toque.
     */
    @Test
    fun al_abrir_la_busqueda_no_tiene_el_foco() {
        montarHoja()
        tocar("Categoría")

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertIsNotFocused()
        assertEquals(
            0,
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().size,
            "algo se llevó el foco al abrir el selector — en un teléfono eso levanta el teclado",
        )
    }

    /** La elegida se marca; las demás no. */
    @Test
    fun la_categoria_puesta_se_marca_en_la_cuadricula() {
        montarHoja()
        tocar("Categoría")

        // Sin usos, «Agregar» arranca en «Comida» (la primera del catálogo de gastos).
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Comida")).assertIsSelected()
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Salud")).assertIsNotSelected()
    }

    /**
     * Las frecuentes van primero **en pantalla**: la más usada arriba a la izquierda, y la
     * primera del alfabeto que no es frecuente después de ellas.
     */
    @Test
    fun las_frecuentes_van_primero() {
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Vivienda", listOf(TransactionType.EXPENSE), usosRecientes = 12),
                UsedCategory("Salud", listOf(TransactionType.EXPENSE), usosRecientes = 5),
            ),
        )
        montarHoja()
        tocar("Categoría")

        val vivienda = posicion("Vivienda")
        val salud = posicion("Salud")
        val comida = posicion("Comida") // la primera del alfabeto, que no es frecuente
        assertTrue(antes(vivienda, salud), "«Vivienda» (12 usos) tiene que ir antes que «Salud» (5)")
        assertTrue(antes(salud, comida), "las frecuentes tienen que ir antes que el resto alfabético")
    }

    /** Escribir filtra, y lo que no coincide exacto ofrece crearse. */
    @Test
    fun escribir_filtra_y_ofrece_crear() {
        montarHoja()
        tocar("Categoría")

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput("Tra")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Transporte")).assertExists()
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Comida")).assertDoesNotExist()
        composeRule.onNodeWithText("Crear \"Tra\"", useUnmergedTree = true).assertExists()
    }

    /** Tocar una celda elige esa categoría y cierra el sub-picker. */
    @Test
    fun tocar_una_celda_elige_y_cierra() {
        montarHoja()
        tocar("Categoría")

        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Salud")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertDoesNotExist()
        composeRule.onNodeWithText("Salud").assertExists()
        composeRule.onNodeWithText("Comida").assertDoesNotExist()
    }

    /** «Crear "…"» elige lo escrito, tal cual. */
    @Test
    fun crear_una_categoria_nueva_la_elige() {
        montarHoja()
        tocar("Categoría")

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput("Plata de la tía")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CREAR_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertDoesNotExist()
        composeRule.onNodeWithText("Plata de la tía").assertExists()
    }

    /**
     * Una reservada no aparece, ni aunque venga con muchos usos; y escribirla se explica en vez de
     * ofrecer crearla.
     */
    @Test
    fun una_reservada_no_aparece() {
        UsedCategoriesCache.recordFromServer(
            listOf(UsedCategory(TRANSFER_CATEGORY, listOf(TransactionType.EXPENSE), usosRecientes = 40)),
        )
        montarHoja()
        tocar("Categoría")

        composeRule.onNodeWithTag(tagDeCeldaDeCategoria(CARD_PAYMENT_CATEGORY)).assertDoesNotExist()
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria(TRANSFER_CATEGORY)).assertDoesNotExist()

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput(CARD_PAYMENT_CATEGORY)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("«$CARD_PAYMENT_CATEGORY» la usa Movi sola", useUnmergedTree = true).assertExists()
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_CREAR_CATEGORIA).fetchSemanticsNodes().size)
    }

    /**
     * Las otras hojas (Presupuestos, Recurrentes, recategorizar) llegan por [CategoryField]: el
     * campo muestra la categoría, tocarlo abre la misma cuadrícula —también sin foco— y elegir una
     * celda la pone y pliega el selector.
     */
    @Test
    fun el_campo_de_las_otras_hojas_abre_la_cuadricula_sin_foco_y_elegir_la_pliega() {
        var elegida = "Comida"
        composeRule.setContent {
            MoviTheme {
                var valor by remember { mutableStateOf(elegida) }
                CategoryField(
                    value = valor,
                    onValueChange = { valor = it; elegida = it },
                    type = TransactionType.EXPENSE,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertDoesNotExist()

        composeRule.onNodeWithTag(TAG_CAMPO_DE_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertIsNotFocused()
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Comida")).assertIsSelected()

        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Ropa")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals("Ropa", elegida)
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertDoesNotExist()
        composeRule.onNodeWithText("Ropa").assertExists()
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private fun montarHoja() {
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
    }

    /** Arriba-izquierda de la celda, sin recortar: la cuadrícula puede pasarse del borde. */
    private fun posicion(nombre: String): Pair<Float, Float> {
        val r = composeRule.onNodeWithTag(tagDeCeldaDeCategoria(nombre)).getUnclippedBoundsInRoot()
        return r.top.value to r.left.value
    }

    /** Orden de lectura: la fila de arriba primero; en la misma fila, la de la izquierda. */
    private fun antes(a: Pair<Float, Float>, b: Pair<Float, Float>): Boolean =
        a.first < b.first - 0.5f || (kotlin.math.abs(a.first - b.first) <= 0.5f && a.second < b.second)

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}

/** El AVD `Movi_Sensor` — mismo tamaño y mismo motivo que en `HojaAgregarEligeLaCuentaTest`. */
private const val TELEFONO_DEL_AVD_SELECTOR = "w411dp-h731dp-xhdpi"
