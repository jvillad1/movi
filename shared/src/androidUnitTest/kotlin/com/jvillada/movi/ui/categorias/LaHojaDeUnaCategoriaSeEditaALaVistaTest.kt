package com.jvillada.movi.ui.categorias

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.SemanticsProperties
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 138 · la hoja de una categoría se edita a la vista
 *
 * La foto del iPhone de Caro: la hoja de «Vivienda» era el nombre, el uso, y una cuadrícula de íconos
 * que llenaba la pantalla; «Renombrar», «Unificar» y «Esconder» quedaban abajo, sin verse, y a una del
 * catálogo ni siquiera se le ofrecía renombrar. Acá, a 390×844 con la letra de la app (×1,12): las
 * filas Nombre, Ícono y color, Unificar y Esconder se ven sin desplazar; la cuadrícula aparece al tocar
 * «Ícono y color»; y «Nombre» de una del catálogo abre renombrar con el nombre lleno y seleccionado.
 *
 * Fallan sin el cambio: no había fila «Nombre» ni «Ícono y color» (la cuadrícula estaba abierta) y a
 * una del catálogo no se le ofrecía renombrar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class LaHojaDeUnaCategoriaSeEditaALaVistaTest {

    @get:Rule val composeRule = createComposeRule()

    @Before fun antes() = UsedCategoriesCache.clear()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        UsedCategoriesCache.clear()
    }

    private val vivienda = CategoryUsage(
        name = "Vivienda",
        scope = CategoryScope.PREDEFINED,
        usedTypes = listOf(TransactionType.EXPENSE),
        movements = 6,
        total = 7_820_533,
        monthMovements = 2,
        monthTotal = 1_900_000,
    )

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** La fila tocable dentro de la caja con [tag] (el tag va afuera, ver `ConTag`), o el nodo mismo. */
    private fun tocable(tag: String) =
        composeRule.onAllNodes(
            hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
            useUnmergedTree = true,
        )

    private fun tocarTag(tag: String) {
        tocable(tag).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun abrirLaHoja(repositorio: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repositorio
        composeRule.setContent { ConClaseDeAncho { CategoriasScreen(onNavigate = {}) } }
        composeRule.waitUntil(5_000) { hay("Vivienda") }
        tocar("Vivienda")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_FILA_DEL_NOMBRE_DE_LA_CATEGORIA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // La hoja entra animada: se espera a que termine para medir dónde quedó.
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun repositorio(lista: () -> List<CategoryUsage> = { listOf(vivienda) }) = object : RepositorioDePrueba() {
        override suspend fun getCategories(): List<CategoryUsage> = lista()
    }

    /** Entera dentro de la ventana de 844 dp: su alto recortado es el de la fila. */
    private fun seVeEntera(tag: String) {
        val nodo = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val ventana = 844f * nodo.layoutInfo.density.density
        val visible = nodo.boundsInRoot
        assertTrue(visible.height >= nodo.size.height - 1f, "«$tag» queda recortada: ${visible.height} de ${nodo.size.height}")
        assertTrue(visible.bottom <= ventana + 1f, "«$tag» termina en ${visible.bottom}, debajo de la ventana ($ventana)")
    }

    @Test
    fun `a 390 por 844 Nombre, Icono y color, Unificar y Esconder se ven sin desplazar`() {
        abrirLaHoja(repositorio())
        seVeEntera(TAG_FILA_DEL_NOMBRE_DE_LA_CATEGORIA)
        seVeEntera(TAG_FILA_DE_LA_APARIENCIA_DE_LA_CATEGORIA)
        seVeEntera(TAG_FILA_DE_UNIFICAR_LA_CATEGORIA)
        seVeEntera(TAG_FILA_DE_ESCONDER_LA_CATEGORIA)
        // La explicación vieja se fue.
        assertFalse(hay("no se renombran"))
    }

    @Test
    fun `la cuadricula de iconos aparece al tocar Icono y color`() {
        abrirLaHoja(repositorio())
        assertFalse(hay("Comer fuera"), "plegada por defecto")
        assertTrue(hay("Casa"), "la fila dice el ícono de hoy")

        tocarTag(TAG_FILA_DE_LA_APARIENCIA_DE_LA_CATEGORIA)
        composeRule.waitUntil(5_000) { hay("Comer fuera") }
        assertTrue(composeRule.onAllNodesWithTag(tagDeIconoDelCatalogo("restaurante"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        // Y se vuelve a plegar.
        tocarTag(TAG_FILA_DE_LA_APARIENCIA_DE_LA_CATEGORIA)
        composeRule.waitUntil(5_000) { !hay("Comer fuera") }
    }

    @Test
    fun `el tipo se despliega con sus pastillas`() {
        abrirLaHoja(repositorio())
        assertTrue(hay("Gasto · automático"))
        assertFalse(hay("Fija uno para decidirlo"))
        tocarTag(TAG_FILA_DEL_TIPO_DE_LA_CATEGORIA)
        composeRule.waitUntil(5_000) { hay("Fija uno para decidirlo") }
    }

    @Test
    fun `Nombre de una del catalogo abre renombrar lleno, seleccionado y con la linea de solo para ti`() {
        val pedidos = mutableListOf<List<String?>>()
        abrirLaHoja(object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(vivienda)
            override suspend fun renameCategory(from: String, to: String, icono: String?, color: String?): CategoryRewriteResult {
                pedidos += listOf(from, to, icono, color)
                return CategoryRewriteResult(name = to, movements = 6)
            }
        })
        tocarTag(TAG_FILA_DEL_NOMBRE_DE_LA_CATEGORIA)
        composeRule.waitUntil(5_000) { hay("Renombrar «Vivienda»") }

        // El campo de la hoja (el último editable: el primero es el buscador) llega lleno y entero seleccionado.
        val campos = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        val campo = campos[campos.fetchSemanticsNodes().size - 1]
        val nodo = campo.fetchSemanticsNode()
        assertEquals("Vivienda", nodo.config[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(0, "Vivienda".length), nodo.config[SemanticsProperties.TextSelectionRange])

        campo.performTextReplacement("Hogar")
        composeRule.waitForIdle()
        assertTrue(hay("Solo cambia para ti: tus movimientos, presupuestos y pagos fijos pasan a «Hogar»."))
        tocar("Guardar")
        composeRule.waitUntil(5_000) { pedidos.isNotEmpty() }
        // Manda la apariencia que se veía (la casa ámbar), para que el nombre nuevo no la cambie.
        assertEquals(listOf("Vivienda", "Hogar", "casa", "ambar"), pedidos.single())
        // Y Agregar deja de ofrecer «Vivienda» y ofrece «Hogar» en su lugar, sin tipo fijado.
        assertEquals("Hogar", UsedCategoriesCache.prefs["Vivienda"]?.renombradaA)
        assertEquals(null, UsedCategoriesCache.prefs["Hogar"]?.pinnedType)
    }

    @Test
    fun `tocar el titulo tambien abre renombrar`() {
        abrirLaHoja(repositorio())
        tocarTag(TAG_TITULO_DE_LA_HOJA_DE_CATEGORIA)
        composeRule.waitUntil(5_000) { hay("Renombrar «Vivienda»") }
    }

    @Test
    fun `una renombrada dice de donde viene y ofrece volver al nombre de Movi`() {
        val pedidos = mutableListOf<Pair<String, String>>()
        // Sin uso ni tipo fijado: «Automático» es el de «Vivienda».
        val hogar = vivienda.copy(name = "Hogar", scope = CategoryScope.CUSTOM, usedTypes = emptyList(), nombreDeMovi = "Vivienda")
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(hogar)
            override suspend fun renameCategory(from: String, to: String, icono: String?, color: String?): CategoryRewriteResult {
                pedidos += from to to
                return CategoryRewriteResult(name = to)
            }
        }
        composeRule.setContent { ConClaseDeAncho { CategoriasScreen(onNavigate = {}) } }
        composeRule.waitUntil(5_000) { hay("Hogar") }
        tocar("Hogar")
        composeRule.waitUntil(5_000) { hay("antes «Vivienda», de Movi") }
        assertTrue(hay("Gasto · automático"), "el tipo de su nombre de Movi, sin «Fijado por ti»")
        tocarTag(TAG_FILA_DE_VOLVER_AL_NOMBRE_DE_MOVI)
        composeRule.waitUntil(5_000) { pedidos.isNotEmpty() }
        assertEquals("Hogar" to "Vivienda", pedidos.single())
    }

    @Test
    fun `la cuota de un credito no ofrece renombrar y dice por que`() {
        Repositories.sustitutoDePrueba = repositorio { listOf(CategoryUsage(name = "Cuota de crédito", movements = 3)) }
        composeRule.setContent { ConClaseDeAncho { CategoriasScreen(onNavigate = {}) } }
        composeRule.waitUntil(5_000) { hay("Cuota de crédito") }
        tocar("Cuota de crédito")
        composeRule.waitUntil(5_000) { hay("No se puede renombrar ni unificar") }
        assertTrue(composeRule.onAllNodesWithTag(TAG_FILA_DEL_NOMBRE_DE_LA_CATEGORIA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(tocable(TAG_FILA_DEL_NOMBRE_DE_LA_CATEGORIA).fetchSemanticsNodes().isEmpty(), "la fila «Nombre» no se toca")
        assertFalse(hay("Unificar en otra"))
    }

    /** Revisión: «Otros» es lo que Movi no entendió; la hoja no ofrece renombrarla ni unificarla. */
    @Test
    fun `Otros no ofrece renombrar ni unificar y dice por que`() {
        Repositories.sustitutoDePrueba = repositorio { listOf(CategoryUsage(name = "Otros", scope = CategoryScope.PREDEFINED, movements = 9)) }
        composeRule.setContent { ConClaseDeAncho { CategoriasScreen(onNavigate = {}) } }
        composeRule.waitUntil(5_000) { hay("Otros") }
        tocar("Otros")
        composeRule.waitUntil(5_000) { hay("donde Movi pone lo que no entendió") }
        assertTrue(tocable(TAG_FILA_DEL_NOMBRE_DE_LA_CATEGORIA).fetchSemanticsNodes().isEmpty(), "la fila «Nombre» no se toca")
        assertFalse(hay("Unificar en otra"))
        assertTrue(hay("Esconder"), "esconderla sí se puede")
    }

    /** Revisión: las filas plegables dicen su estado al lector de pantalla. */
    @Test
    fun `las filas plegables anuncian si estan abiertas`() {
        abrirLaHoja(repositorio())
        fun estado(tag: String) = tocable(tag).onFirst().fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("Cerrado", estado(TAG_FILA_DE_LA_APARIENCIA_DE_LA_CATEGORIA))
        assertEquals("Cerrado", estado(TAG_FILA_DEL_TIPO_DE_LA_CATEGORIA))
        tocarTag(TAG_FILA_DE_LA_APARIENCIA_DE_LA_CATEGORIA)
        assertEquals("Abierto", estado(TAG_FILA_DE_LA_APARIENCIA_DE_LA_CATEGORIA))
    }

    /** Revisión: escribir un nombre del catálogo ya retirado ofrece unificar en el de hoy. */
    @Test
    fun `renombrar hacia un nombre retirado ofrece unificar en el de hoy`() {
        UsedCategoriesCache.applyPref("Vivienda", com.jvillada.movi.shared.model.CategoryPref(hidden = true, renombradaA = "Hogar"))
        val hogar = CategoryUsage(name = "Hogar", movements = 6, nombreDeMovi = "Vivienda")
        val carro = CategoryUsage(name = "Carro", movements = 2)
        Repositories.sustitutoDePrueba = repositorio { listOf(hogar, carro) }
        composeRule.setContent { ConClaseDeAncho { CategoriasScreen(onNavigate = {}) } }
        composeRule.waitUntil(5_000) { hay("Carro") }
        tocar("Carro")
        composeRule.waitUntil(5_000) { hay("Ícono y color") }
        tocarTag(TAG_FILA_DEL_NOMBRE_DE_LA_CATEGORIA)
        composeRule.waitUntil(5_000) { hay("Renombrar «Carro»") }
        val campos = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        campos[campos.fetchSemanticsNodes().size - 1].performTextReplacement("vivienda")
        composeRule.waitForIdle()
        assertTrue(hay("«Vivienda» ahora se llama «Hogar»."))
        assertTrue(hay("Unificar en «Hogar»"))
    }
}
