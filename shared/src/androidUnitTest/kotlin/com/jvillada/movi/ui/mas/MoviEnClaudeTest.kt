package com.jvillada.movi.ui.mas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.platform.HojaDeCompartir
import com.jvillada.movi.platform.LocalHojaDeCompartir
import com.jvillada.movi.shared.model.ConectorDeClaude
import com.jvillada.movi.shared.model.ConexionDeClaude
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.comandoDeClaudeCode
import com.jvillada.movi.shared.model.lineaDeLaConexion
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
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
 * # Vuelta 135 · «Movi en Claude»
 *
 * La fila en Tú, la dirección con «Copiar», el comando de Claude Code, la lista de conexiones y «Desconectar»
 * (pregunta antes y saca la fila). A 360 dp con la letra del teléfono al 130 % nada se corta, y no hay amarillo.
 * Sin la pantalla, nada de esto existe.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class MoviEnClaudeTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private class HojaDePrueba : HojaDeCompartir {
        override val abreLaHojaDelSistema: Boolean = false
        override val puedeDescargar: Boolean = false
        val copiados = mutableListOf<String>()
        override fun compartir(texto: String) { copiados += texto }
        override fun copiar(texto: String) { copiados += texto }
        override fun descargar(nombre: String, contenido: String, tipo: String): Boolean = false
    }

    private companion object {
        const val URL = "https://movi-project-production.up.railway.app/mcp"
        /** 6-nov-2026, 9:00 en Bogotá. */
        const val CONECTADA = 1_793_973_600_000L
        const val AHORA = CONECTADA + 3 * 3_600_000L
    }

    private class Server(var conexiones: List<ConexionDeClaude>) : RepositorioDePrueba() {
        val desconectadas = mutableListOf<String>()
        override suspend fun getConectorDeClaude(): ConectorDeClaude = ConectorDeClaude(URL, conexiones)
        override suspend fun desconectarDeClaude(id: String) {
            desconectadas += id
            conexiones = conexiones.filterNot { it.id == id }
        }
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@local", name = "Juan", avatarColor = "#7C6CF0", periodCutoffDay = 25)
        override suspend fun isScreenAdmin(): Boolean = false
    }

    private val claude = ConexionDeClaude(id = "cnc_1", cliente = "Claude", host = "claude.ai", conectadaEn = CONECTADA, ultimoUso = AHORA - 2 * 3_600_000L)
    private val claudeCode = ConexionDeClaude(id = "cnc_2", cliente = "Claude Code", host = "localhost", conectadaEn = CONECTADA)

    private fun montar(server: Server, hoja: HojaDePrueba = HojaDePrueba(), escala: Float? = null, pantalla: @Composable () -> Unit) {
        Repositories.sustitutoDePrueba = server
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalHojaDeCompartir provides hoja,
                LocalDensity provides Density(base.density, escala ?: base.fontScale),
            ) {
                MoviTheme { Box(Modifier.fillMaxSize()) { pantalla() } }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodes(hasText(texto, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String, elUltimo: Boolean = false) {
        val nodos = composeRule.onAllNodes(hasClickAction() and hasText(texto))
        val n = nodos.fetchSemanticsNodes().size
        assertTrue(n > 0, "no encontré «$texto» para tocar")
        nodos[if (elUltimo) n - 1 else 0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun textoDe(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    @Test
    fun `la fila va en Tu, en la seccion Movi, y abre la pantalla`() {
        val destinos = mutableListOf<Screen>()
        montar(Server(emptyList())) { MasScreen(onNavigate = { destinos += it }, onLogout = {}, esAndroid = false) }
        composeRule.waitUntil(5_000) { hay(TITULO_MOVI_EN_CLAUDE) }
        composeRule.onAllNodes(hasClickAction() and hasText(TITULO_MOVI_EN_CLAUDE))[0].performScrollTo()
        tocar(TITULO_MOVI_EN_CLAUDE)
        assertEquals(listOf<Screen>(Screen.MoviEnClaude), destinos)
    }

    @Test
    fun `dice la direccion y el comando, y Copiar los copia`() {
        val hoja = HojaDePrueba()
        montar(Server(emptyList()), hoja) { MoviEnClaudeScreen(onNavigate = {}, ahora = { AHORA }) }
        composeRule.waitUntil(5_000) { hay(URL) }
        assertEquals(URL, textoDe(TAG_DIRECCION_DEL_CONECTOR))
        assertEquals("claude mcp add --transport http movi $URL", textoDe(TAG_COMANDO_DE_CLAUDE_CODE))
        assertTrue(hay(QUE_VE_CLAUDE))
        assertTrue(hay("Agregar conector personalizado"))
        assertTrue(hay(SIN_CONEXIONES))
        tocar(COPIAR)
        assertEquals(listOf(URL), hoja.copiados)
        assertTrue(hay(COPIADO))
        tocar(COPIAR_EL_COMANDO)
        assertEquals(listOf(URL, comandoDeClaudeCode(URL)), hoja.copiados)
    }

    @Test
    fun `la lista dice cada conexion y Desconectar pregunta antes y la saca`() {
        val server = Server(listOf(claude, claudeCode))
        montar(server) { MoviEnClaudeScreen(onNavigate = {}, ahora = { AHORA }) }
        composeRule.waitUntil(5_000) { hay("Claude Code") }
        assertTrue(hay(lineaDeLaConexion(claude, AHORA)))
        assertEquals("claude.ai · conectado el 6 nov · último uso hace 2 h", lineaDeLaConexion(claude, AHORA))
        assertTrue(hay("localhost · conectado el 6 nov · todavía no leyó nada"))

        tocar(DESCONECTAR)
        assertTrue(hay(preguntaDeDesconectar("Claude")), "pregunta antes")
        assertTrue(server.desconectadas.isEmpty())
        // El de la hoja es el último «Desconectar».
        tocar(DESCONECTAR, elUltimo = true)
        composeRule.waitUntil(5_000) { server.desconectadas.isNotEmpty() }
        composeRule.waitForIdle()
        assertEquals(listOf("cnc_1"), server.desconectadas)
        assertTrue(!hay("último uso hace 2 h"), "la fila se fue")
        assertTrue(hay("Claude Code"))
    }

    @Test
    fun `a 360 dp con la letra grande nada se corta y no hay amarillo`() {
        montar(Server(listOf(claude, claudeCode)), escala = ESCALA_DEL_TELEFONO_AL_130) { MoviEnClaudeScreen(onNavigate = {}, ahora = { AHORA }) }
        composeRule.waitUntil(5_000) { hay("Claude Code") }
        val raiz = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(0, amarillos(raiz), "ninguna acción principal en esta pantalla")
        val fallas = cortados(raiz)
        assertTrue(fallas.isEmpty(), "A 360 dp con la letra al ×$ESCALA_DEL_TELEFONO_AL_130:\n" + fallas.joinToString("\n"))
    }

    private fun amarillos(n: SemanticsNode): Int =
        (if (n.config.getOrNull(LlevaElAmarilloKey) == true) 1 else 0) + n.children.sumOf { hijo -> amarillos(hijo) }

    /** Lo que se corta o se sale, como `ElTextoAl130NoSeCortaTest`. */
    private fun cortados(raiz: SemanticsNode): List<String> {
        val anchoDePantalla = raiz.size.width + 0.5f
        fun textos(n: SemanticsNode): List<SemanticsNode> =
            (if (n.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) listOf(n) else emptyList<SemanticsNode>()) + n.children.flatMap { hijo -> textos(hijo) }
        return textos(raiz).mapNotNull { nodo ->
            val r = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            val d = r.firstOrNull() ?: return@mapNotNull null
            val ancho = nodo.size.width + 0.5f
            val limites = nodo.boundsInRoot
            val texto = nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { linea -> linea.text }.orEmpty()
            when {
                (0 until d.lineCount).any { d.isLineEllipsized(it) } -> "«$texto» se corta con «…»"
                d.multiParagraph.didExceedMaxLines -> "«$texto» no le caben los renglones"
                (0 until d.lineCount).any { d.getLineRight(it) - d.getLineLeft(it) > ancho } -> "«$texto» es más ancho que su caja"
                limites.left < -0.5f || limites.right > anchoDePantalla -> "«$texto» se sale de la pantalla"
                else -> null
            }
        }
    }
}
