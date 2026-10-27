package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.COLORES_OSCUROS
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_PILDORA_DE_ACCION
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Movi AI conversa con formato (vuelta de diseño 12)
 *
 * La respuesta con lista se pinta con viñetas (no con «- »), la burbuja del dueño usa los tokens, y
 * debajo de la última respuesta van las preguntas que no se hicieron: con su id, y no mientras Movi
 * escribe. Lo del encabezado (el subtítulo entero a 360 dp con la letra a ×1,3) lo mide
 * `LaAccionDelEncabezadoCedeAlTituloTest`, con el motor de texto real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp-xhdpi")
class MoviAiConversaConFormatoTest {

    @get:Rule val composeRule = createComposeRule()

    private val enviados = mutableListOf<AiChatRequest>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DashboardDataCache.data = null
    }

    private val respuestaConLista = "Te faltan 2 pagos del período por \$2.000.000:\n" +
        "- Arriendo \$1.800.000 · venció hace 22 días\n" +
        "- Internet \$200.000 · vence mañana\n\n" +
        "Te quedan **\$1.200.000** para 9 días."

    private val yaPreguntada = listOf(
        ChatMessage(ChatRole.USER, PreguntaSugerida.COMO_VOY.texto, preguntaSugerida = PreguntaSugerida.COMO_VOY.id),
        ChatMessage(ChatRole.ASSISTANT, respuestaConLista),
    )

    private val oscuro = mutableStateOf(false)

    private fun montar(
        mensajes: List<ChatMessage>,
        contestar: suspend (AiChatRequest) -> AiChatResponse = { AiChatResponse(text = "Listo.") },
    ) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(mensajes)
            override suspend fun chatAi(request: AiChatRequest): AiChatResponse {
                enviados += request
                return contestar(request)
            }
        }
        composeRule.setContent { MoviTheme(oscuro = oscuro.value) { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { textos().any { "Arriendo" in it } || textos().any { "Vas bien" in it } }
    }

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun textos(): List<String> =
        composeRule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }

    private fun medidaDe(nodo: SemanticsNode): TextLayoutResult {
        val medidas = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
        return medidas.single()
    }

    private fun chips(): List<String> =
        nodos(TAG_PILDORA_DE_ACCION).mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }

    @Test
    fun `una respuesta con lista se pinta con vinetas, no con guiones`() {
        montar(yaPreguntada)
        val todos = textos()
        assertTrue(todos.none { it.lines().any { renglon -> renglon.startsWith("- ") } }, "quedó un «- » crudo: $todos")
        assertTrue(todos.none { "**" in it }, "quedaron los asteriscos de la negrita: $todos")
        val marcas = nodos(TAG_MARCA_DE_LISTA).map { it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } }
        assertEquals(listOf(VINETA, VINETA), marcas)
        assertTrue("Arriendo \$1.800.000 · venció hace 22 días" in todos)
        assertTrue("Te quedan \$1.200.000 para 9 días." in todos)
    }

    @Test
    fun `la burbuja del dueno usa los tokens, en los dos temas`() {
        montar(yaPreguntada)
        for ((enOscuro, colores) in listOf(false to COLORES_CLAROS, true to COLORES_OSCUROS)) {
            oscuro.value = enOscuro
            composeRule.waitForIdle()
            val texto = composeRule.onAllNodes(
                hasText(PreguntaSugerida.COMO_VOY.texto) and hasAnyAncestor(hasTestTag(TAG_BURBUJA_DEL_DUENO)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().single()
            assertEquals(textoDeLaBurbujaDelDueno(colores), medidaDe(texto).layoutInput.style.color)
            assertEquals(colores.lavado(colores.marca), fondoDeLaBurbujaDelDueno(colores))
            assertTrue(fondoDeLaBurbujaDelDueno(colores) != colores.texto, "ya no es la burbuja del color del texto")
        }
    }

    @Test
    fun `tras una respuesta aparecen las que no se hicieron, y una tocada va con su id`() {
        val respuesta = CompletableDeferred<AiChatResponse>()
        montar(yaPreguntada) { respuesta.await() }
        composeRule.waitForIdle()
        assertEquals(1, nodos(TAG_PREGUNTAS_PARA_SEGUIR).size)
        assertEquals(listOf(PreguntaSugerida.EN_QUE_SE_VA.texto, PreguntaSugerida.QUE_REVISAR.texto), chips())

        composeRule.onAllNodesWithTag(TAG_PILDORA_DE_ACCION)[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        val mandado = enviados.single().messages.last()
        assertEquals(PreguntaSugerida.EN_QUE_SE_VA.texto, mandado.content)
        assertEquals(PreguntaSugerida.EN_QUE_SE_VA.id, mandado.preguntaSugerida)
        // Mientras Movi escribe, las píldoras no van.
        assertEquals(0, nodos(TAG_PREGUNTAS_PARA_SEGUIR).size)

        respuesta.complete(AiChatResponse(text = "Lo que más pesa:\n1. Vivienda \$1.500.000"))
        composeRule.waitUntil(5_000) { nodos(TAG_PREGUNTAS_PARA_SEGUIR).isNotEmpty() }
        assertEquals(listOf(PreguntaSugerida.QUE_REVISAR.texto), chips())
    }

    @Test
    fun `la pildora dice la version corta`() {
        montar(yaPreguntada)
        val visibles = textos()
        assertTrue(cortaDeRespaldo(PreguntaSugerida.EN_QUE_SE_VA) in visibles, "la corta se ve: $visibles")
    }
}
