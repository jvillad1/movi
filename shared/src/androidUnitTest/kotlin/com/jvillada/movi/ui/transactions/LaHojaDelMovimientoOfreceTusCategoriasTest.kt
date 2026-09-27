package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.CeldaDeCategoria
import com.jvillada.movi.ui.components.TAG_BUSCAR_CATEGORIA
import com.jvillada.movi.ui.components.TAG_CREAR_CATEGORIA
import com.jvillada.movi.ui.components.contenidoDelSelectorDeCategoria
import com.jvillada.movi.ui.components.tagDeCeldaDeCategoria
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
 * # Ola L — la hoja de un movimiento ofrece **tus** categorías, y deja crear una nueva
 *
 * El dueño (26-sep): «Colegio Hija de mes pasado quedó con una categoría que ahora revisando un
 * movimiento no puedo seleccionar… ahora parece que no puedo crear nuevas categorías». La lista de
 * filas de esta hoja salía solo de `PREDEFINED_CATEGORIES`: no traía «Fútbol», «Hija» ni «Gimnasio».
 * Ahora sale del [com.jvillada.movi.ui.components.SelectorDeCategoria] de todas las pantallas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h731dp-xhdpi")
class LaHojaDelMovimientoOfreceTusCategoriasTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private fun gasto(categoria: String = "Otros") = FinancialEvent(
        id = "ev-colegio",
        accountId = banco.id,
        type = TransactionType.EXPENSE,
        amount = 480_000L,
        category = categoria,
        description = "Colegio Hija",
        timestamp = 1_757_952_000_000L,
    )

    /** Guarda las categorías que se pidieron. */
    private class Repo : RepositorioDePrueba() {
        val pedidas = mutableListOf<String>()
        override suspend fun updateEventCategory(id: String, category: String): FinancialEvent {
            pedidas += category
            return FinancialEvent(
                id = id, accountId = "acc-banco", type = TransactionType.EXPENSE, amount = 480_000L,
                category = category, description = "Colegio Hija", timestamp = 1_757_952_000_000L,
            )
        }
    }

    private lateinit var repo: Repo

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private fun suyas() {
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Hija", listOf(TransactionType.EXPENSE), usosRecientes = 12),
                UsedCategory("Fútbol", listOf(TransactionType.EXPENSE), usosRecientes = 7),
                UsedCategory("Gimnasio", listOf(TransactionType.EXPENSE), usosRecientes = 0),
                UsedCategory("Gardenera", listOf(TransactionType.INCOME), usosRecientes = 4),
            ),
        )
    }

    private fun montar(event: FinancialEvent = gasto(), onCambiado: (FinancialEvent) -> Unit = {}) {
        repo = Repo()
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    ChangeCategorySheet(
                        event = event,
                        cuentas = listOf(banco),
                        onDismiss = {},
                        onEventChanged = onCambiado,
                        // Como en producción ([HojaDelMovimiento] siempre lo pasa): un gasto muestra
                        // además «¿Se repite todos los meses?», encima de la categoría.
                        onMarcarComoRecurrente = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tocarCelda(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun las_categorias_propias_del_dueno_estan_a_un_toque() {
        suyas()
        montar()

        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Hija")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Fútbol")).performScrollTo().assertIsDisplayed()
        // Una propia sin usos recientes también: es suya.
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Gimnasio")).performScrollTo().assertIsDisplayed()
        // La de ingresos no se le ofrece a un gasto.
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Gardenera")).assertDoesNotExist()
    }

    @Test
    fun elegir_una_propia_guarda_esa_categoria() {
        suyas()
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        tocarCelda(tagDeCeldaDeCategoria("Hija"))

        assertEquals(listOf("Hija"), repo.pedidas)
        assertEquals("Hija", cambiado?.category)
    }

    @Test
    fun se_puede_crear_una_categoria_nueva_desde_la_hoja() {
        suyas()
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performScrollTo().performTextInput("Colegio de Hija")
        composeRule.waitForIdle()
        tocarCelda(TAG_CREAR_CATEGORIA)

        assertEquals(listOf("Colegio de Hija"), repo.pedidas)
        assertEquals("Colegio de Hija", cambiado?.category)
    }

    @Test
    fun la_busqueda_esta_a_la_vista_sin_buscarla_debajo_de_una_lista_larga() {
        suyas()
        montar()

        // Sin `performScrollTo`: si estuviera bajo veinte filas, en 731 dp no se vería. Y con la
        // sección «¿Se repite?» montada, que es como la ve el dueño.
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertIsDisplayed()
    }

    @Test
    fun una_categoria_actual_que_no_esta_en_ninguna_lista_se_sigue_viendo_marcada() {
        suyas()
        montar(gasto("Importada del extracto"))

        // No solo el texto: la fila tiene que estar declarada como la elegida.
        composeRule.onNode(
            isSelected() and hasAnyDescendant(hasText("Importada del extracto")),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun la_categoria_actual_se_marca_en_la_cuadricula() {
        suyas()
        montar(gasto("Hija"))

        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Hija")).performScrollTo().assertIsSelected()
    }

    @Test
    fun pago_de_tarjeta_se_sigue_pudiendo_marcar_y_dice_que_implica() {
        suyas()
        montar()

        composeRule.onNodeWithText(
            "Deja de contar en tus gastos del mes: la compra ya se contó al usar la tarjeta",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithText(CARD_PAYMENT_CATEGORY, useUnmergedTree = true).performScrollTo()
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(CARD_PAYMENT_CATEGORY)), useUnmergedTree = true)
            .onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(CARD_PAYMENT_CATEGORY), repo.pedidas)
    }

    @Test
    fun una_reservada_escrita_no_ofrece_crear_y_se_explica() {
        suyas()
        montar()

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performScrollTo().performTextInput("Saldo inicial")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_CREAR_CATEGORIA).assertDoesNotExist()
        composeRule.onNodeWithText("«Saldo inicial» la usa Movi sola", useUnmergedTree = true).assertExists()
        assertTrue(repo.pedidas.isEmpty(), "escribir no guarda nada")
    }

    /** Ola L, revisión: lo creado acá tiene que conocerse en el siguiente movimiento. */
    @Test
    fun una_categoria_creada_queda_en_el_cache_y_no_se_vuelve_a_ofrecer_como_crear() {
        suyas()
        montar()

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performScrollTo().performTextInput("Colegio")
        composeRule.waitForIdle()
        tocarCelda(TAG_CREAR_CATEGORIA)

        assertTrue("Colegio" in UsedCategoriesCache.used.keys, "el caché no la conoce: ${UsedCategoriesCache.used.keys}")
        val siguiente = contenidoDelSelectorDeCategoria(
            "Colegio", TransactionType.EXPENSE, UsedCategoriesCache.used, UsedCategoriesCache.prefs,
            UsedCategoriesCache.usosRecientes,
        )
        assertTrue(siguiente.celdas.none { it is CeldaDeCategoria.Crear }, "se vuelve a ofrecer «Crear»")
        assertTrue(siguiente.celdas.any { it is CeldaDeCategoria.Existente && it.nombre == "Colegio" })
    }
}
