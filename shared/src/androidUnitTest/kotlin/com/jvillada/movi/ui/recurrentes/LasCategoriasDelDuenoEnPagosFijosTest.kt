package com.jvillada.movi.ui.recurrentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.data.ENFRIAMIENTO_DE_LAS_CATEGORIAS_MS
import com.jvillada.movi.data.asegurarLasCategoriasDelDueno
import com.jvillada.movi.ui.components.ds.TAG_CAMPO_DE_MONTO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.assertFalse
import kotlin.test.assertNull
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_BUSCAR_CATEGORIA
import com.jvillada.movi.ui.components.TAG_CAMPO_DE_CATEGORIA
import com.jvillada.movi.ui.components.TAG_CREAR_CATEGORIA
import com.jvillada.movi.ui.components.tagDeCeldaDeCategoria
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 119 · las categorías del dueño en la hoja de un pago fijo
 *
 * El dueño (2-nov-2026): «En recurrentes parece que no está tomando las categorías creadas, siempre
 * se hace necesario crear nuevamente la categoría; esto no pasa con movimientos».
 *
 * Dos causas, una prueba por cada una:
 * - La hoja se abría con el caché de categorías vacío cuando no se había pasado por el Inicio (Pagos
 *   tras recargar la web): el selector solo ofrecía el catálogo. Ahora el selector se asegura las
 *   categorías con la misma lectura del Inicio.
 * - La categoría creada en la hoja no se anotaba al guardar (Agregar sí lo hace), y el server tampoco
 *   la devolvía porque solo miraba los movimientos (eso lo prueba `DashboardRoutesTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class LasCategoriasDelDuenoEnPagosFijosTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc_ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 2_000_000L)
    private var pedidosDelResumen = 0
    private var guardada: RecurringRule? = null

    private var creada: RecurringRule? = null

    private open inner class Repo(private val delServer: List<UsedCategory>) : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary {
            pedidosDelResumen++
            return DashboardSummary(usedCategories = delServer)
        }
        override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule {
            creada = rule
            return rule.copy(id = "rr_nueva")
        }
        override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule {
            guardada = rule
            return rule.copy(id = id)
        }
    }

    private fun hayCelda(nombre: String) =
        composeRule.onAllNodesWithTag(tagDeCeldaDeCategoria(nombre), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun abrirElSelector() {
        composeRule.onNodeWithTag(TAG_CAMPO_DE_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `sin pasar por el Inicio, la hoja de un pago fijo ofrece las categorias del duenno`() {
        // Como en la web recién recargada en Pagos: nadie llenó el caché.
        assertTrue(UsedCategoriesCache.used.isEmpty())
        Repositories.sustitutoDePrueba = Repo(listOf(UsedCategory("Gardenera", listOf(TransactionType.EXPENSE))))
        composeRule.setContent { MoviTheme { CreateRecurringRuleSheet(onDismiss = {}, onSaved = {}) } }
        composeRule.waitForIdle()

        abrirElSelector()
        composeRule.waitUntil(5_000) { hayCelda("Gardenera") }
        assertEquals(1, pedidosDelResumen, "una sola lectura, aunque el campo y el selector la pidan los dos")
    }

    @Test
    fun `una categoria creada en la hoja aparece la proxima vez`() {
        // Un server que no la devuelve (sin red, o el de antes de esta vuelta): lo que queda es lo anotado.
        Repositories.sustitutoDePrueba = Repo(emptyList())
        val regla = RecurringRule(
            id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 1_800_000L,
            dayOfMonth = 5, type = TransactionType.EXPENSE, accountId = ahorros.id,
        )
        var vez by mutableIntStateOf(0)
        composeRule.setContent {
            MoviTheme {
                key(vez) {
                    if (vez == 0) {
                        CreateRecurringRuleSheet(onDismiss = {}, onSaved = { vez++ }, existing = regla)
                    } else {
                        CreateRecurringRuleSheet(onDismiss = {}, onSaved = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()

        abrirElSelector()
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput("Paisajismo")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CREAR_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Guardar cambios").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { vez == 1 }
        assertEquals("Paisajismo", assertNotNull(guardada).category)

        // La hoja nueva, la de la regla siguiente.
        composeRule.waitForIdle()
        abrirElSelector()
        composeRule.waitUntil(5_000) { hayCelda("Paisajismo") }
    }

    @Test
    fun `crear un pago fijo con una categoria nueva la deja para la regla siguiente`() {
        Repositories.sustitutoDePrueba = Repo(emptyList())
        var vez by mutableIntStateOf(0)
        composeRule.setContent {
            MoviTheme {
                key(vez) {
                    CreateRecurringRuleSheet(onDismiss = {}, onSaved = { vez++ }, nombreNuevo = "Jardinero", diaNuevo = 10)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CAMPO_DE_MONTO).performTextInput("180000")
        abrirElSelector()
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput("Jardineria")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CREAR_CATEGORIA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Crear recurrente").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { vez == 1 }
        assertEquals("Jardineria", assertNotNull(creada).category)

        composeRule.waitForIdle()
        abrirElSelector()
        composeRule.waitUntil(5_000) { hayCelda("Jardineria") }
    }

    @Test
    fun `tras una lectura fallida, dos montajes no la piden otra vez hasta que pasa el enfriamiento`() {
        Repositories.sustitutoDePrueba = object : Repo(emptyList()) {
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary {
                pedidosDelResumen++
                error("sin señal")
            }
        }
        var vez by mutableIntStateOf(0)
        composeRule.setContent {
            MoviTheme { key(vez) { CreateRecurringRuleSheet(onDismiss = {}, onSaved = {}) } }
        }
        composeRule.waitForIdle()
        abrirElSelector()
        vez++
        composeRule.waitForIdle()
        abrirElSelector()
        assertEquals(1, pedidosDelResumen, "la falla se recuerda: el segundo montaje no sale a pedirla")

        // Pasado el enfriamiento, sí se reintenta.
        val despues = assertNotNull(UsedCategoriesCache.ultimoFalloDeLasCategoriasMs) + ENFRIAMIENTO_DE_LAS_CATEGORIAS_MS
        runBlocking { asegurarLasCategoriasDelDueno(ahora = { despues }) }
        assertEquals(2, pedidosDelResumen)
    }

    @Test
    fun `varias lecturas a la vez son un solo pedido`() {
        val respuesta = CompletableDeferred<DashboardSummary>()
        Repositories.sustitutoDePrueba = object : Repo(emptyList()) {
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary {
                pedidosDelResumen++
                return respuesta.await()
            }
        }
        runBlocking {
            val tres = List(3) { async { asegurarLasCategoriasDelDueno() } }
            yield()
            respuesta.complete(DashboardSummary(usedCategories = listOf(UsedCategory("Gardenera"))))
            tres.awaitAll()
        }
        assertEquals(1, pedidosDelResumen)
        assertTrue("Gardenera" in UsedCategoriesCache.used)
    }

    @Test
    fun `cerrar la sesion olvida que llegaron`() {
        Repositories.sustitutoDePrueba = Repo(listOf(UsedCategory("Gardenera")))
        runBlocking { asegurarLasCategoriasDelDueno() }
        assertTrue(UsedCategoriesCache.llegaronDelServer)
        UsedCategoriesCache.clear()
        assertFalse(UsedCategoriesCache.llegaronDelServer)
    }

    /** Vuelta 125: antes la prueba miraba el campo sin haber provocado nunca una falla. */
    @Test
    fun `cerrar la sesion olvida la falla reciente`() {
        Repositories.sustitutoDePrueba = object : Repo(emptyList()) {
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = error("sin señal")
        }
        runBlocking { asegurarLasCategoriasDelDueno() }
        assertNotNull(UsedCategoriesCache.ultimoFalloDeLasCategoriasMs, "la falla quedó anotada")
        UsedCategoriesCache.clear()
        assertNull(UsedCategoriesCache.ultimoFalloDeLasCategoriasMs)
    }

    /**
     * Vuelta 125: un 401 a la lectura en vuelo cierra la sesión (y `clear()` baja la falla) ANTES de
     * que la excepción llegue al `catch`. Sin la guarda de la generación, el `catch` la volvía a anotar
     * y quien entraba en menos de 30 s no cargaba sus categorías.
     */
    @Test
    fun `una falla que vuelve despues de cerrar la sesion no re-arma el enfriamiento`() {
        Repositories.sustitutoDePrueba = object : Repo(emptyList()) {
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary {
                pedidosDelResumen++
                SessionManager.clear() // lo que hace el interceptor con un 401
                error("401")
            }
        }
        runBlocking { asegurarLasCategoriasDelDueno() }
        assertNull(UsedCategoriesCache.ultimoFalloDeLasCategoriasMs)

        // Quien entra enseguida sí sale a pedir las suyas.
        Repositories.sustitutoDePrueba = Repo(listOf(UsedCategory("Gardenera")))
        runBlocking { asegurarLasCategoriasDelDueno() }
        assertEquals(2, pedidosDelResumen)
        assertTrue("Gardenera" in UsedCategoriesCache.used)
    }

    /** Vuelta 125: al revés, lo que contesta bien después del cierre es del usuario que se fue. */
    @Test
    fun `una respuesta que vuelve despues de cerrar la sesion no se anota`() {
        Repositories.sustitutoDePrueba = object : Repo(emptyList()) {
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary {
                pedidosDelResumen++
                SessionManager.clear()
                return DashboardSummary(usedCategories = listOf(UsedCategory("Del anterior")))
            }
        }
        runBlocking { asegurarLasCategoriasDelDueno() }
        assertFalse(UsedCategoriesCache.llegaronDelServer)
        assertFalse("Del anterior" in UsedCategoriesCache.used)
        assertNull(UsedCategoriesCache.ultimoFalloDeLasCategoriasMs)
    }
}
