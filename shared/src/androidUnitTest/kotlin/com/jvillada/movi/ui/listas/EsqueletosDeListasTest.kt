package com.jvillada.movi.ui.listas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.categorias.CategoriasScreen
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
import com.jvillada.movi.ui.cuadre.CuadreDeSaldosScreen
import com.jvillada.movi.ui.destinos.DestinosScreen
import com.jvillada.movi.ui.documentos.DocumentosScreen
import com.jvillada.movi.ui.documentos.TAG_FILA_DE_DOCUMENTO
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Ola B, tarea 9: Categorías, Documentos, Cuadre de saldos y Cuentas de otros cargan con su forma
 *
 * Las cuatro pantallas quedaban EN BLANCO —ni una rueda— entre el encabezado (y, en Documentos, la
 * barra de progreso) y la primera fila real: la lista arrancaba en `emptyList()` y nada distinguía
 * «no llegó» de «llegó vacía» a los ojos de quien mira la pantalla. Cada bloque de acá prueba las
 * tres fases que pide la tarea, con el mismo mecanismo que `EsqueletoDeCuentasTest`
 * ([CompletableDeferred] colgando la lectura que le importa a esa pantalla):
 *
 * 1. Con la lectura colgada, el esqueleto — y nada que afirme una cifra o un vacío.
 * 2. Al contestar, el esqueleto se va y queda la fila de verdad.
 * 3. Con una lectura que contesta vacía DE VERDAD (no colgada, no caída), el vacío de siempre —
 *    sin ningún tag de esqueleto de por medio.
 *
 * `Movimientos` tiene su propia clase (`EsqueletoDeMovimientosTest`, en `ui.transactions`) porque
 * ya existía desde la ola A; acá van las cuatro que esta tarea deja con esqueleto por primera vez.
 */
// `@GraphicsMode(NATIVE)` + `sdk = [34]` (fix round 1): hace falta el motor de texto real para
// medir el Y de una fila contra otra — ver el KDoc de `Esqueleto.kt`. Las pruebas que solo cuentan
// tags o buscan texto siguen andando igual bajo NATIVE (mismo criterio que `EsqueletoDeCuentasTest`).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h731dp-xhdpi")
class EsqueletosDeListasTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun contarEsqueletos(): Int =
        composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size

    // ── Categorías ───────────────────────────────────────────────────────────────

    @Test
    fun `Categorías — con la lectura colgada, 6 filas esqueleto y nada mas`() {
        val puerta = CompletableDeferred<List<CategoryUsage>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = puerta.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        assertEquals(6, contarEsqueletos())
        assertTrue(!hay("Nada por aquí todavía"))
        assertTrue(!hay("No pudimos cargar"))
        assertTrue(!hay("0 categorías"))
    }

    @Test
    fun `Categorías — al contestar, el esqueleto se va y queda la categoria real`() {
        val puerta = CompletableDeferred<List<CategoryUsage>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = puerta.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        puerta.complete(listOf(CategoryUsage(name = "Comida")))
        composeRule.waitForIdle()

        assertEquals(0, contarEsqueletos())
        composeRule.onNodeWithText("Comida", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `Categorías — sin categorias de verdad, el vacio de siempre y ningun esqueleto`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        assertTrue(hay("Nada por aquí todavía"))
        assertEquals(0, contarEsqueletos())
    }

    // ── Documentos ───────────────────────────────────────────────────────────────

    @Test
    fun `Documentos — con la lectura colgada, 4 filas esqueleto y la accion del encabezado ya puesta`() {
        val puerta = CompletableDeferred<List<Documento>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDocuments(): List<Documento> = puerta.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DocumentosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        assertEquals(4, contarEsqueletos())
        // Ola B, tarea 9: «Subir archivo» ya no espera a que la lista conteste — vive en el
        // encabezado desde el primer cuadro (ver el KDoc de `DocumentosScreen`).
        assertTrue(hay("Subir archivo"))
        assertTrue(!hay("Aquí se guardan tus extractos"))
        assertTrue(!hay("No pudimos cargar"))
    }

    @Test
    fun `Documentos — al contestar, el esqueleto se va y queda el documento real`() {
        val puerta = CompletableDeferred<List<Documento>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDocuments(): List<Documento> = puerta.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DocumentosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        puerta.complete(
            listOf(
                Documento(
                    id = "doc1",
                    nombre = "Cedula.pdf",
                    tipo = TipoDeDocumento.CONTRATO,
                    mimeType = "application/pdf",
                    bytes = 1_024L,
                    subidoEn = 1_758_326_400_000L,
                ),
            ),
        )
        composeRule.waitForIdle()

        assertEquals(0, contarEsqueletos())
        // Substring y no el nombre completo: `nombreQueSePartePorSusSeparadores` mete un corte de
        // línea invisible después del `.`, y buscar el nombre entero con ese caracter en el medio
        // no encuentra nada. Lo que importa es que el título real (no un bloque) esté en pantalla.
        composeRule.onNodeWithText("Cedula", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * **Fix round 1, hallazgo 2.** La lista real arranca con un `MinSectionHeader` por tipo
     * («Contratos · 1») ANTES de la primera fila; el esqueleto original no lo tenía, así que la
     * primera fila esqueleto quedaba más arriba que la primera fila real y todo bajaba de golpe
     * al llegar los datos. Se agregó `RotuloDeSeccionEsqueleto()` arriba de las filas; esta
     * prueba mide el TOP de la primera fila esqueleto (`TAG_FILA_DE_LISTA_ESQUELETO`) contra el
     * de la primera fila real (`TAG_FILA_DE_DOCUMENTO`).
     */
    @Test
    fun `Documentos — la primera fila esqueleto arranca en el mismo Y que la primera fila real`() {
        val puerta = CompletableDeferred<List<Documento>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDocuments(): List<Documento> = puerta.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DocumentosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        val yEsqueleto = composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO, useUnmergedTree = true)
            .onFirst().getUnclippedBoundsInRoot().top

        puerta.complete(
            listOf(
                Documento(
                    id = "doc1",
                    nombre = "Cedula.pdf",
                    tipo = TipoDeDocumento.CONTRATO,
                    mimeType = "application/pdf",
                    bytes = 1_024L,
                    subidoEn = 1_758_326_400_000L,
                ),
            ),
        )
        composeRule.waitForIdle()

        val yReal = composeRule.onNodeWithTag(TAG_FILA_DE_DOCUMENTO, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top

        val diferencia = abs(yReal.value - yEsqueleto.value)
        assertTrue(
            diferencia <= 2f,
            "La fila esqueleto arrancaba en ${yEsqueleto.value} dp y la real en ${yReal.value} dp " +
                "— diferencia de $diferencia dp, el máximo son 2 dp",
        )
    }

    @Test
    fun `Documentos — sin documentos de verdad, el vacio de siempre y ningun esqueleto`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDocuments(): List<Documento> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DocumentosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        assertTrue(hay("Aquí se guardan tus extractos"))
        assertEquals(0, contarEsqueletos())
    }

    // ── Cuadre de saldos ─────────────────────────────────────────────────────────

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 500_000L)

    @Test
    fun `Cuadre — con la lectura colgada, 3 filas esqueleto y nada mas`() {
        val puerta = CompletableDeferred<List<Account>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = puerta.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CuadreDeSaldosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        assertEquals(3, contarEsqueletos())
        assertTrue(!hay("Todavía no tienes cuentas"))
        assertTrue(!hay("No pudimos cargar"))
    }

    @Test
    fun `Cuadre — al contestar, el esqueleto se va y queda la fila de la cuenta real`() {
        val puerta = CompletableDeferred<List<Account>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = puerta.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CuadreDeSaldosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        puerta.complete(listOf(nu))
        composeRule.waitForIdle()

        assertEquals(0, contarEsqueletos())
        composeRule.onNodeWithText("Nu", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Ola D, Task 2: el vacío que enseña, con el mismo texto de siempre. */
    @Test
    fun `Cuadre — sin cuentas de verdad, el vacio que ensena y ningun esqueleto`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CuadreDeSaldosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        assertTrue(hay("Todavía no hay nada que cuadrar"))
        assertEquals(0, contarEsqueletos())
    }

    // ── Cuentas de otros ─────────────────────────────────────────────────────────

    @Test
    fun `Destinos — con la lectura colgada, 3 fichas esqueleto y nada mas`() {
        val puerta = CompletableDeferred<List<DestinoConocido>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDestinos(): List<DestinoConocido> = puerta.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        assertEquals(3, contarEsqueletos())
        assertTrue(!hay("Aquí guardas cuentas que no son tuyas"))
        assertTrue(!hay("No pudimos cargar"))
    }

    @Test
    fun `Destinos — al contestar, el esqueleto se va y queda la ficha real`() {
        val puerta = CompletableDeferred<List<DestinoConocido>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDestinos(): List<DestinoConocido> = puerta.await()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        puerta.complete(listOf(DestinoConocido(id = "dst1", nombre = "Caro", numero = "1234567890")))
        composeRule.waitForIdle()

        assertEquals(0, contarEsqueletos())
        composeRule.onNodeWithText("Caro", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `Destinos — sin destinos de verdad, el vacio de siempre y ningun esqueleto`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        assertTrue(hay("Aquí guardas cuentas que no son tuyas"))
        assertEquals(0, contarEsqueletos())
    }
}
