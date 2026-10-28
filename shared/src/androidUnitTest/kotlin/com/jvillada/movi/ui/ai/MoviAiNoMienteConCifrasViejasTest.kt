package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.fecha.epochAlMediodia
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
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
 * # Movi AI no muestra cifras viejas como de hoy (vuelta de diseño 30)
 *
 * Al abrir el chat aparecía un turno de ayer sin fecha («Te quedan $10.869.550 para 5 días»). Ahora
 * dice de qué día es, lo atenúa con «Estas cifras eran de ayer» y «Preguntar de nuevo», y ofrece
 * arriba empezar otra conversación. Y sin la IA no sugiere preguntas que van a dar el aviso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class MoviAiNoMienteConCifrasViejasTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = hoyEnAppZone()
    private val ayer = hoy.minus(DatePeriod(days = 1))
    private val enviadas = mutableListOf<ChatMessage>()
    private var nuevas = 0

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DashboardDataCache.data = null
    }

    private fun conConversacion(mensajes: List<ChatMessage>, iaDisponible: Boolean = true) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(mensajes, iaDisponible = iaDisponible)
            override suspend fun empezarConversacionNueva() {
                nuevas++
            }
            override suspend fun chatAi(request: AiChatRequest): AiChatResponse {
                enviadas += request.messages.last()
                return AiChatResponse(text = "Te quedan \$2.784.550 para 4 días.")
            }
        }
    }

    private fun montar() =
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) } } }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    private val deAyer = listOf(
        ChatMessage(ChatRole.USER, PreguntaSugerida.COMO_VOY.texto, preguntaSugerida = PreguntaSugerida.COMO_VOY.id, enviadoEn = epochAlMediodia(ayer)),
        ChatMessage(ChatRole.ASSISTANT, "Te quedan \$10.869.550 para 5 días.", enviadoEn = epochAlMediodia(ayer)),
    )

    @Test
    fun `lo de ayer dice su dia, se atenua y se puede preguntar de nuevo con su id`() {
        conConversacion(deAyer)
        montar()
        esperar("Te quedan \$10.869.550 para 5 días.")

        assertTrue(hay("Ayer"), "el separador del día")
        assertTrue(hay("Estas cifras eran de ayer"))
        composeRule.onNodeWithTag(TAG_CIFRAS_DE_OTRO_DIA, useUnmergedTree = true).fetchSemanticsNode()

        composeRule.onAllNodes(hasText(PREGUNTAR_DE_NUEVO) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { enviadas.isNotEmpty() }
        assertEquals(PreguntaSugerida.COMO_VOY.texto, enviadas.single().content)
        assertEquals(PreguntaSugerida.COMO_VOY.id, enviadas.single().preguntaSugerida, "la sigue contestando Movi sin la IA")
        esperar("Te quedan \$2.784.550 para 4 días.")
        assertTrue(hay("Hoy"), "la respuesta nueva va bajo «Hoy»")
    }

    @Test
    fun `una conversacion de otro dia ofrece empezar otra sin borrar nada sola`() {
        conConversacion(deAyer)
        montar()
        esperar(EMPEZAR_UNA_CONVERSACION_NUEVA)
        assertTrue(hay("Esta conversación es de ayer"))
        assertEquals(0, nuevas, "no se empieza sola")

        composeRule.onAllNodes(hasText(EMPEZAR_UNA_CONVERSACION_NUEVA) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { nuevas == 1 }
        composeRule.waitUntil(5_000) { !hay("Te quedan \$10.869.550") }
        assertFalse(hay(EMPEZAR_UNA_CONVERSACION_NUEVA))
    }

    @Test
    fun `Seguir con esta quita la tarjeta y deja la conversacion`() {
        conConversacion(deAyer)
        montar()
        esperar(SEGUIR_ESTA)
        composeRule.onAllNodes(hasText(SEGUIR_ESTA) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { !hay(EMPEZAR_UNA_CONVERSACION_NUEVA) }
        assertTrue(hay("Te quedan \$10.869.550 para 5 días."))
        assertEquals(0, nuevas)
    }

    @Test
    fun `una conversacion de hoy no lleva separadores ni tarjeta`() {
        conConversacion(deAyer.map { it.copy(enviadoEn = epochAlMediodia(hoy)) })
        montar()
        esperar("Te quedan \$10.869.550 para 5 días.")
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_DIA_DEL_CHAT, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertFalse(hay("Estas cifras eran"))
        assertFalse(hay(EMPEZAR_UNA_CONVERSACION_NUEVA))
    }

    @Test
    fun `sin la IA el arranque no sugiere preguntas que irian al modelo`() {
        // El Inicio dejó en su caché un período en rojo: con la IA, la primera sugerencia es esa.
        DashboardDataCache.data = DashboardData(summary = FinanceSummary(Scope.SELF, 0L, ingresos = 850_000, egresos = 1_752_250))
        conConversacion(emptyList(), iaDisponible = false)
        montar()
        esperar(PreguntaSugerida.COMO_VOY.texto)
        assertFalse(hay("¿Por qué este período salieron"), "va al modelo, y el modelo no está")
        PreguntaSugerida.entries.forEach { assertTrue(hay(it.texto), "${it.id} se contesta sin la IA: queda") }
    }

    @Test
    fun `con la IA la pregunta del periodo en rojo si se sugiere`() {
        DashboardDataCache.data = DashboardData(summary = FinanceSummary(Scope.SELF, 0L, ingresos = 850_000, egresos = 1_752_250))
        conConversacion(emptyList(), iaDisponible = true)
        montar()
        esperar("¿Por qué este período salieron")
    }

    @Test
    fun `el encabezado va en su propia capa opaca`() {
        conConversacion(deAyer)
        montar()
        esperar("Te quedan \$10.869.550")
        composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_MOVI_AI, useUnmergedTree = true).fetchSemanticsNode()
    }
}
