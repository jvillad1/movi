package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.accounts.TAG_FILA_DE_CUENTA
import com.jvillada.movi.ui.categorias.CategoriasScreen
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TAG_SECCION
import com.jvillada.movi.ui.cuadre.CuadreDeSaldosScreen
import com.jvillada.movi.ui.destinos.DestinosScreen
import com.jvillada.movi.ui.destinos.TAG_FICHA_EN_LA_LISTA
import com.jvillada.movi.ui.mas.MasScreen
import com.jvillada.movi.ui.profile.PerfilScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * # Las pantallas del grupo R4c usan el sistema (ola R4)
 *
 * Una prueba por pantalla principal del grupo —Patrimonio, Personas y comercios, Ajustes, Perfil,
 * Categorías y Cuadre de saldos—, montada a 360 dp con datos de verdad, que mira lo que el sistema
 * promete (`docs/diseno/sistema.md`):
 *
 * - arranca con el [Encabezado][com.jvillada.movi.ui.components.ds.Encabezado];
 * - cada título de sección es una `Seccion` y **ninguno va en mayúsculas**;
 * - cada fila tocable es una `Fila` y mide al menos [Medida.altoDeFila];
 * - **ninguna cifra se recorta**: ni «…» ni un renglón más ancho que su nodo.
 *
 * Mismo método que `NadaSeRecortaEnElInicioTest` (`sdk = [34]` y `NATIVE` para el motor de texto).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class LasPantallasDeR4cUsanElSistemaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val cuentas = listOf(
        Account("acc-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L),
        Account("acc-nu", "Nu", AccountType.SAVINGS, 2_890_000L),
        Account("acc-afc", "AFC Davibank", AccountType.SAVINGS, 112_000_000L, condicionadaA = "vivienda"),
        Account("acc-skandia", "Skandia", AccountType.INVESTMENT, 98_450_300L),
        Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 1_030_600_000L),
    )

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = cuentas
        override suspend fun getCredits(): List<CreditSummary> =
            listOf(CreditSummary(account = cuentas.last(), terms = null, paidPct = null))
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> = listOf(
            DestinoConocido(id = "d1", nombre = "Caro", numero = "55500000756", totalesDelPeriodo = mapOf("COP" to 12_350_000L)),
            DestinoConocido(id = "d2", nombre = "Papá", numero = "50009876"),
        )
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "jvillad1@gmail.com", name = "Juan", avatarColor = "#7C6CF0", periodCutoffDay = 25, reminderLeadDays = 5)
        override suspend fun isScreenAdmin(): Boolean = false
        override suspend fun getCategories(): List<CategoryUsage> = listOf(
            CategoryUsage(name = "Restaurantes y domicilios", movements = 12),
            CategoryUsage(name = "Mercado", movements = 30),
        )
    }

    private fun montar(esperar: String, pantalla: @Composable () -> Unit) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { pantalla() } } }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(esperar, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    /**
     * Las filas del sistema: las que llevan el tag de `Fila` y las que la pantalla etiquetó con el
     * suyo (un tag de afuera gana sobre el de adentro: la fila de una categoría se llama
     * `categoria:fila:…`, la de una cuenta `fila-de-cuenta`).
     */
    private fun filas(vararg prefijos: String): List<SemanticsNode> =
        composeRule.onAllNodes(
            SemanticsMatcher("una Fila") { n ->
                val tag = n.config.getOrNull(SemanticsProperties.TestTag) ?: return@SemanticsMatcher false
                tag == TAG_FILA || prefijos.any { tag.startsWith(it) }
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Los textos que viven dentro de [nodo]. */
    private fun textosDentroDe(nodo: SemanticsNode): List<String> =
        listOf(texto(nodo)).filter { it.isNotEmpty() } + nodo.children.flatMap(::textosDentroDe)

    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val d = resultados.single()
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    private fun usaElSistema(pantalla: String, conFilas: Boolean = true, conCifras: Boolean = true, prefijos: List<String> = emptyList()) {
        assertTrue(nodos(TAG_ENCABEZADO).isNotEmpty(), "$pantalla arranca con el Encabezado")

        val secciones = nodos(TAG_SECCION)
        assertTrue(secciones.isNotEmpty(), "$pantalla titula sus secciones con Seccion")
        secciones.flatMap(::textosDentroDe).forEach { titulo ->
            assertTrue(titulo != titulo.uppercase() || titulo.none { it.isLetter() }, "$pantalla: «$titulo» va en mayúsculas")
        }

        if (conFilas) {
            val filas = filas(*prefijos.toTypedArray())
            assertTrue(filas.isNotEmpty(), "$pantalla usa Fila")
            val densidad = composeRule.density
            val minimo = with(densidad) { Medida.altoDeFila.toPx() } - 0.5f
            filas.forEach { fila ->
                assertTrue(fila.size.height >= minimo, "$pantalla: una fila mide ${fila.size.height}px, menos que el alto del sistema")
            }
        }

        if (conCifras) {
            val cifras = nodos(TAG_CIFRA)
            assertTrue(cifras.isNotEmpty(), "$pantalla pinta sus montos con Cifra")
            cifras.forEach { assertTrue(seVeEntero(it), "$pantalla: «${texto(it)}» se recorta a 360 dp") }
        }
    }

    @Test
    fun `Patrimonio usa el sistema y no recorta cifras`() {
        montar("Bancolombia Ahorros") { AccountsScreen(onNavigate = {}) }
        usaElSistema("Patrimonio", prefijos = listOf(TAG_FILA_DE_CUENTA))
    }

    @Test
    fun `Personas y comercios usa el sistema y no recorta cifras`() {
        montar("Caro") { DestinosScreen(onNavigate = {}) }
        usaElSistema("Personas y comercios", prefijos = listOf(TAG_FICHA_EN_LA_LISTA))
    }

    @Test
    fun `Perfil usa el sistema`() {
        montar("Cada día 25") { PerfilScreen(onNavigate = {}, onLogout = {}) }
        usaElSistema("Perfil", conCifras = false)
    }

    @Test
    fun `Categorias usa filas del sistema`() {
        montar("Mercado") { CategoriasScreen(onNavigate = {}) }
        assertTrue(nodos(TAG_ENCABEZADO).isNotEmpty())
        val minimo = with(composeRule.density) { Medida.altoDeFila.toPx() } - 0.5f
        val filas = filas("categoria:fila:")
        assertTrue(filas.size >= 2, "una Fila por categoría")
        filas.forEach { assertTrue(it.size.height >= minimo) }
    }

    @Test
    fun `Cuadre de saldos usa el sistema y no recorta cifras`() {
        montar("Bancolombia Ahorros") { CuadreDeSaldosScreen(onNavigate = {}) }
        assertTrue(nodos(TAG_ENCABEZADO).isNotEmpty())
        val cifras = nodos(TAG_CIFRA)
        assertTrue(cifras.isNotEmpty(), "«Movi dice» es una Cifra")
        cifras.forEach { assertTrue(seVeEntero(it), "«${texto(it)}» se recorta a 360 dp") }
    }

    @Test
    fun `Ajustes es la cuadricula de acciones del sistema`() {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { MasScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()
        assertTrue(nodos(TAG_ENCABEZADO).isNotEmpty())
        assertTrue(nodos(com.jvillada.movi.ui.components.ds.TAG_CUADRICULA_DE_ACCIONES).isNotEmpty())
        val minimo = with(composeRule.density) { Medida.objetivoTactil.toPx() } - 0.5f
        nodos(com.jvillada.movi.ui.components.ds.TAG_ACCION_RAPIDA).forEach { assertTrue(it.size.height >= minimo) }
    }
}
