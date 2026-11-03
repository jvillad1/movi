package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.ConversacionEnLaLista
import com.jvillada.movi.shared.model.ListaDeConversaciones
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Varias conversaciones en la pantalla de Movi AI (vuelta 117)
 *
 * En el teléfono, el botón de historial del encabezado abre la hoja con las conversaciones; tocar una
 * la abre con sus mensajes y lo que se pregunta después va con su id; «Eliminar» pregunta siempre
 * antes; «Nueva conversación» empieza otra. En pantalla ancha, la lista va en una columna al lado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class VariasConversacionesEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahora = Clock.System.now().toEpochMilliseconds()

    private val enCurso = ConversacionEnLaLista("cv_1", "¿Cómo voy este período?", ahora, ahora, 2)
    private val vieja = ConversacionEnLaLista("cv_2", "¿Cuánto gasté en restaurantes el mes…", ahora - 3_600_000, ahora - 3_600_000, 4)

    private inner class Repo : RepositorioDePrueba() {
        val pedidos = mutableListOf<AiChatRequest>()
        val borradas = mutableListOf<String>()
        var creadas = 0
        var listaFalla = false
        var lista = mutableListOf(enCurso, vieja)

        override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(
            mensajes = listOf(
                ChatMessage(ChatRole.USER, "¿Cómo voy este período?", enviadoEn = ahora),
                ChatMessage(ChatRole.ASSISTANT, "Vas bien: te quedan días.", enviadoEn = ahora),
            ),
            id = enCurso.id,
            titulo = enCurso.titulo,
        )

        override suspend fun getConversaciones(): ListaDeConversaciones {
            if (listaFalla) throw ApiException(503, null)
            return ListaDeConversaciones(lista.toList())
        }

        override suspend fun getConversacion(id: String) = ConversacionDelAsistente(
            mensajes = listOf(
                ChatMessage(ChatRole.USER, "¿Cuánto gasté en restaurantes el mes pasado?", enviadoEn = ahora),
                ChatMessage(ChatRole.ASSISTANT, "Gastaste lo de la vieja.", enviadoEn = ahora),
            ),
            id = id,
            titulo = vieja.titulo,
        )

        var crearFalla = false

        override suspend fun crearConversacion(): ConversacionEnLaLista {
            if (crearFalla) throw ApiException(503, null)
            creadas++
            return ConversacionEnLaLista("cv_nueva_$creadas", "", ahora, ahora, 0)
        }

        override suspend fun borrarConversacion(id: String) {
            borradas += id
            lista.removeAll { it.id == id }
        }

        override suspend fun chatAi(request: AiChatRequest): AiChatResponse {
            pedidos += request
            return AiChatResponse("Respuesta.", conversacionId = request.conversacionId ?: "cv_server")
        }
    }

    private val repo = Repo()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(ancho: WindowWidthClass = WindowWidthClass.Compact) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalWindowWidthClass provides ancho) {
                    Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) }
                }
            }
        }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)
            .fetchSemanticsNodes().let { assertTrue(it.isNotEmpty(), "no hay nada tocable que diga «$texto»") }
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun abrirLaHoja() {
        esperar("Vas bien")
        composeRule.onNodeWithTag(TAG_BOTON_DE_CONVERSACIONES, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        esperar(vieja.titulo)
    }

    private fun preguntar(texto: String) {
        composeRule.onNode(hasSetTextAction()).performTextReplacement(texto)
        composeRule.onAllNodes(hasClickAction() and hasAnyChild(hasContentDescription("Enviar")), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { repo.pedidos.isNotEmpty() }
    }

    @Test
    fun `la hoja lista las conversaciones con su fecha, sus mensajes y la regla de cuanto se guarda`() {
        montar()
        abrirLaHoja()

        assertTrue(hay("¿Cómo voy este período?"))
        assertTrue(hay("Hoy · 2 mensajes"), "la fecha relativa y cuántos mensajes")
        assertTrue(hay("Hoy · 4 mensajes"))
        assertTrue(hay("Movi guarda tus últimas 50 conversaciones."), "la retención se dice")
        assertEquals(2, composeRule.onAllNodesWithTag(TAG_FILA_DE_CONVERSACION, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `abrir una vieja muestra sus mensajes, y lo siguiente va a ella`() {
        montar()
        abrirLaHoja()
        tocar(vieja.titulo)

        esperar("Gastaste lo de la vieja.")
        assertFalse(hay("Vas bien: te quedan días."), "lo de la otra se fue")

        preguntar("¿y este mes?")
        assertEquals("cv_2", repo.pedidos.single().conversacionId, "la pregunta va a la conversación abierta")
        assertEquals(
            listOf("¿Cuánto gasté en restaurantes el mes pasado?", "Gastaste lo de la vieja.", "¿y este mes?"),
            repo.pedidos.single().messages.map { it.content },
            "al modelo viajan los mensajes de esta conversación",
        )
    }

    @Test
    fun `eliminar pregunta antes, y cancelar no borra`() {
        montar()
        abrirLaHoja()

        composeRule.onNode(hasClickAction() and hasContentDescription(descripcionDelMenuDeLaConversacion(vieja.titulo)))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar(ELIMINAR_CONVERSACION)
        esperar(PREGUNTA_DE_ELIMINAR_LA_CONVERSACION)
        assertTrue(hay(DETALLE_DE_ELIMINAR_LA_CONVERSACION))
        assertEquals(emptyList(), repo.borradas, "preguntar no borra nada")

        tocar("Cancelar")
        composeRule.waitUntil(5_000) { !hay(PREGUNTA_DE_ELIMINAR_LA_CONVERSACION) }
        assertEquals(emptyList(), repo.borradas)

        composeRule.onNode(hasClickAction() and hasContentDescription(descripcionDelMenuDeLaConversacion(vieja.titulo)))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar(ELIMINAR_CONVERSACION)
        esperar(PREGUNTA_DE_ELIMINAR_LA_CONVERSACION)
        tocar(ELIMINAR_CONVERSACION)

        composeRule.waitUntil(5_000) { repo.borradas.isNotEmpty() }
        assertEquals(listOf("cv_2"), repo.borradas)
        composeRule.waitUntil(5_000) { !hay(vieja.titulo) }
        assertTrue(hay("Vas bien"), "borrar otra no toca la abierta")
    }

    /**
     * Revisión de la vuelta 117 · se borra la abierta y no se puede empezar otra (sin red): lo dice, y la
     * pregunta siguiente no va a otra conversación vieja; con red de nuevo, va a una nueva.
     */
    @Test
    fun `si al borrar la abierta no se puede empezar otra, lo dice y no manda a una vieja`() {
        repo.crearFalla = true
        montar()
        abrirLaHoja()
        composeRule.onNode(hasClickAction() and hasContentDescription(descripcionDelMenuDeLaConversacion(enCurso.titulo)))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar(ELIMINAR_CONVERSACION)
        esperar(PREGUNTA_DE_ELIMINAR_LA_CONVERSACION)
        tocar(ELIMINAR_CONVERSACION)
        composeRule.waitUntil(5_000) { repo.borradas.isNotEmpty() }
        esperar(NO_PUDE_EMPEZAR_OTRA)
        assertFalse(hay("Vas bien"), "la borrada ya no se ve")

        composeRule.onNode(hasSetTextAction()).performTextReplacement("¿y ahora?")
        composeRule.onAllNodes(hasClickAction() and hasAnyChild(hasContentDescription("Enviar")), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(emptyList(), repo.pedidos, "sin conversación nueva no se manda: iría a otra vieja")
        assertTrue(hay("¿y ahora?"), "lo escrito vuelve al campo")

        repo.crearFalla = false
        preguntar("¿y ahora?")
        assertEquals("cv_nueva_1", repo.pedidos.single().conversacionId)
    }

    @Test
    fun `nueva conversacion desde la hoja empieza otra y lo siguiente va con su id`() {
        montar()
        abrirLaHoja()
        tocar(NUEVA_CONVERSACION)

        esperar(NO_REEMPLAZA_A_UN_ASESOR)
        assertEquals(1, repo.creadas)
        assertFalse(hay("Vas bien"))
        preguntar("una pregunta nueva")
        assertEquals("cv_nueva_1", repo.pedidos.single().conversacionId)
    }

    @Test
    fun `si la lista no carga lo dice, y reintenta`() {
        repo.listaFalla = true
        montar()
        esperar("Vas bien")
        composeRule.onNodeWithTag(TAG_BOTON_DE_CONVERSACIONES, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        esperar(NO_PUDE_LEER_LAS_CONVERSACIONES)

        repo.listaFalla = false
        tocar("Reintentar")
        esperar(vieja.titulo)
    }

    /** En la cáscara: el panel que le queda a Movi AI (sin el rail, con el tope de lista y detalle). */
    @Test
    fun `la lista va al lado del chat solo con lugar para las dos`() {
        val chat = com.jvillada.movi.ui.Screen.AIChat()
        assertTrue(laListaVaAlLadoDelChat(com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara(1280.dp, chat)), "a 1.280")
        assertTrue(laListaVaAlLadoDelChat(com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara(1920.dp, chat)), "a 1.920")
        assertFalse(laListaVaAlLadoDelChat(com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara(1024.dp, chat)), "a 1.024 no cabe")
        assertFalse(laListaVaAlLadoDelChat(com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara(800.dp, chat)), "en una ventana mediana no cabe")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `en pantalla ancha la lista va en una columna al lado del chat`() {
        montar(WindowWidthClass.Expanded)
        esperar("Vas bien")
        esperar(vieja.titulo)

        assertTrue(composeRule.onAllNodesWithTag(TAG_COLUMNA_DE_CONVERSACIONES).fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            composeRule.onAllNodesWithTag(TAG_BOTON_DE_CONVERSACIONES, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "con la lista a la vista no hace falta el botón del historial",
        )
        tocar(vieja.titulo)
        esperar("Gastaste lo de la vieja.")
    }
}
