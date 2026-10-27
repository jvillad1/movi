package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.IA_SIN_CREDITO
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_PILDORA_DE_ACCION
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Las tres preguntas que Movi contesta sin la IA, en el chat
 *
 * Tocar una de las tres de respaldo manda su id (y el server la contesta con los números de Movi, sin
 * el modelo); tocar una de las que arman las reglas, o escribir, no lo manda. Y cuando la IA no está
 * disponible, la burbuja lo dice y deja las tres a mano para tocarlas.
 *
 * El repositorio es de prueba: contesta lo que el server contestaría según el id. Nada toca la red.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LasPreguntasSugeridasEnElChatTest {

    @get:Rule val composeRule = createComposeRule()

    private val enviados = mutableListOf<AiChatRequest>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DashboardDataCache.data = null
    }

    /** Lo que contestaría el server: las sugeridas con su respuesta fija; lo libre, sin crédito. */
    private fun conServerSinCredito() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(emptyList())
            override suspend fun chatAi(request: AiChatRequest): AiChatResponse {
                enviados += request
                return when (PreguntaSugerida.deId(request.messages.last().preguntaSugerida)) {
                    PreguntaSugerida.COMO_VOY -> AiChatResponse(text = "Te quedan \$2.877.000 para 19 días.")
                    PreguntaSugerida.EN_QUE_SE_VA -> AiChatResponse(text = "Lo que más pesa: 1. Vivienda \$1.500.000")
                    PreguntaSugerida.QUE_REVISAR -> AiChatResponse(text = "Revisa esto, en este orden:")
                    null -> AiChatResponse(text = MOVI_AI_NO_DISPONIBLE, codigo = IA_SIN_CREDITO)
                }
            }
        }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    private fun tocar(texto: String) =
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `tocar una de las tres en el chat manda su id y muestra la respuesta`() {
        conServerSinCredito()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) } } }
        esperar(PreguntaSugerida.COMO_VOY.texto)

        tocar(PreguntaSugerida.COMO_VOY.texto)
        esperar("Te quedan \$2.877.000 para 19 días.")

        val mandado = enviados.single().messages.single()
        assertEquals(PreguntaSugerida.COMO_VOY.texto, mandado.content)
        assertEquals(PreguntaSugerida.COMO_VOY.id, mandado.preguntaSugerida)
    }

    @Test
    fun `una de las que arman las reglas no lleva id`() {
        conServerSinCredito()
        DashboardDataCache.data = DashboardData(
            summary = FinanceSummary(Scope.SELF, balance = 0L, ingresos = 22_200_000L, egresos = 33_900_000L),
        )
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) } } }
        val deLasReglas = "¿Por qué este período salieron \$11,7M más de los que entraron?"
        esperar(deLasReglas)

        tocar(deLasReglas)
        esperar(MOVI_AI_NO_DISPONIBLE)

        assertNull(enviados.single().messages.single().preguntaSugerida)
    }

    @Test
    fun `abierto desde Hoy con una de las tres la manda con su id`() {
        conServerSinCredito()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    AIChatScreen(
                        onNavigate = {},
                        preguntaInicial = PreguntaSugerida.EN_QUE_SE_VA.texto,
                        sugeridaInicial = PreguntaSugerida.EN_QUE_SE_VA.id,
                    )
                }
            }
        }
        esperar("Lo que más pesa: 1. Vivienda \$1.500.000")
        assertEquals(PreguntaSugerida.EN_QUE_SE_VA.id, enviados.single().messages.single().preguntaSugerida)
    }

    @Test
    fun `sin credito una pregunta libre lo dice y deja las tres para tocar`() {
        conServerSinCredito()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}, preguntaInicial = "¿Cuánto gasté en agosto?") } }
        }
        esperar(MOVI_AI_NO_DISPONIBLE)
        assertTrue(MOVI_AI_NO_DISPONIBLE.contains("sí funcionan"))
        PreguntaSugerida.entries.forEach { assertTrue(hay(it.texto), "falta «${it.texto}» debajo del aviso") }

        tocar(PreguntaSugerida.QUE_REVISAR.texto)
        esperar("Revisa esto, en este orden:")

        assertEquals(2, enviados.size)
        assertNull(enviados.first().messages.last().preguntaSugerida)
        assertEquals(PreguntaSugerida.QUE_REVISAR.id, enviados.last().messages.last().preguntaSugerida)
        // Ya contestó: las tres de debajo del aviso se van hasta el próximo aviso (su texto entero ya
        // no se ve)...
        composeRule.waitForIdle()
        assertTrue(!hay(PreguntaSugerida.COMO_VOY.texto), "con respuesta, las tres de debajo del aviso se van")
        // ...y quedan, para seguir (vuelta de diseño 12), las que no se hicieron, en su versión corta.
        val paraSeguir = composeRule.onAllNodesWithTag(TAG_PILDORA_DE_ACCION, useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        assertEquals(listOf(PreguntaSugerida.COMO_VOY.texto, PreguntaSugerida.EN_QUE_SE_VA.texto), paraSeguir)
    }
}
