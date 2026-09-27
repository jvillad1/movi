package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_BUSCAR_CATEGORIA
import com.jvillada.movi.ui.components.TAG_CREAR_CATEGORIA
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
 * # Ola L — crear una categoría nueva al anotar un movimiento
 *
 * El dueño dijo que ahí tampoco podía crear. Se ejerce el recorrido completo (escribir un nombre
 * que no existe, tocar «Crear "…"», guardar) contra la hoja de verdad, y se comprueba lo que llega
 * al server; y como el camino sí funcionaba pero nada en la hoja lo decía, la pastilla «+ Nueva»
 * de los chips de frecuentes abre ese mismo selector con el cursor listo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h731dp-xhdpi")
class CrearCategoriaEnAgregarTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_000_000)
    private val publicados = mutableListOf<FinancialEvent>()

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private fun montar(conFrecuentes: Boolean = false) {
        if (conFrecuentes) {
            UsedCategoriesCache.recordFromServer(
                listOf(UsedCategory("Hija", listOf(TransactionType.EXPENSE), usosRecientes = 12)),
            )
        }
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) { QuickAddScreen(onDismiss = {}) }
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun crear_una_categoria_nueva_la_elige_y_se_guarda_con_ella() {
        montar()

        tocar("Categoría")
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput("Colegio de Hija")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CREAR_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        tocar("5")
        tocar("000")
        tocar("Guardar movimiento")

        assertEquals(1, publicados.size)
        assertEquals("Colegio de Hija", publicados.single().category)
    }

    @Test
    fun la_pastilla_nueva_abre_el_selector_con_el_cursor_en_la_busqueda() {
        montar(conFrecuentes = true)

        tocar("+ Nueva")

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun crear_desde_la_pastilla_nueva_tambien_guarda() {
        montar(conFrecuentes = true)

        tocar("+ Nueva")
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput("Mascota")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CREAR_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar("7")
        tocar("000")
        tocar("Guardar movimiento")

        assertEquals("Mascota", publicados.single().category)
    }

    /** Un usuario nuevo, o un arranque en frío antes del Inicio, no tiene frecuentes: igual la ve. */
    @Test
    fun la_pastilla_nueva_se_ve_aunque_no_haya_frecuentes() {
        montar(conFrecuentes = false)

        composeRule.onNodeWithTag(TAG_PASTILLA_NUEVA_CATEGORIA_AGREGAR).assertIsDisplayed()
        tocar("+ Nueva")
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun la_pastilla_nueva_va_antes_que_los_chips_de_uso() {
        montar(conFrecuentes = true)

        val nueva = composeRule.onNodeWithTag(TAG_PASTILLA_NUEVA_CATEGORIA_AGREGAR).getUnclippedBoundsInRoot()
        val hija = composeRule.onNodeWithText("Hija").getUnclippedBoundsInRoot()
        assertTrue(nueva.left < hija.left, "«+ Nueva» tiene que ir primera")
    }

    @Test
    fun la_fila_categoria_sigue_abriendo_sin_teclado() {
        montar(conFrecuentes = true)

        tocar("Categoría")

        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).assertIsNotFocused()
    }
}
