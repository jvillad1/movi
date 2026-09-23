package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
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
 * # El chat de Movi AI no arranca en blanco
 *
 * Lo que se vio en producción: el chat abría con «Pregúntame lo que quieras» y el dueño escribió
 * «Hola, me puedes ayudar?». Esto fija el arranque nuevo —saludo con su nombre, qué mira Movi, tres
 * preguntas de SUS datos y la nota de que no reemplaza a un asesor— y las dos puertas que mandan
 * una pregunta sin teclear: tocar un chip, y abrir la pantalla con una pregunta ya lista (lo que
 * va a usar el Inicio).
 *
 * Los clics van por la acción semántica, como en `BienesEnCuentasTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_CHAT)
class ElChatNoArrancaEnBlancoTest {

    @get:Rule val composeRule = createComposeRule()

    /** Lo que llegó al repositorio: una lista por cada pregunta mandada. */
    private val enviados = mutableListOf<AiChatRequest>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DashboardDataCache.data = null
    }

    private fun conRepositorioQueContesta() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun chatAi(request: AiChatRequest): AiChatResponse {
                enviados += request
                return AiChatResponse(text = "Respuesta de prueba")
            }
        }
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `vacio saluda por su nombre, dice que mira, sugiere tres preguntas de sus datos y avisa que no es un asesor`() {
        conRepositorioQueContesta()
        SessionManager.save("tok", "usr", "Camilo Villada", "camilo@correo.com")
        DashboardDataCache.data = DashboardData(
            summary = FinanceSummary(Scope.SELF, balance = 0L, ingresos = 22_200_000L, egresos = 33_900_000L),
            budgets = listOf(Budget("Fútbol", 400_000L)),
            spentByCategory = mapOf("Fútbol" to 963_456L),
        )

        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) } } }

        esperarTexto("¡Hola, Camilo!")
        assertTrue(hay(QUE_MIRA_MOVI))
        assertTrue(hay("¿Qué hago para no pasarme en Fútbol el próximo período?"))
        assertTrue(hay("¿Por qué este período salieron \$11,7M más de los que entraron?"))
        assertTrue(hay(PREGUNTAS_DE_RESPALDO.first()))
        assertTrue(hay(NO_REEMPLAZA_A_UN_ASESOR))
        // Nada se mandó solo: sin pregunta inicial, el chat espera.
        assertEquals(0, enviados.size)
    }

    @Test
    fun `tocar una sugerencia la manda y el arranque se va`() {
        conRepositorioQueContesta()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) } } }
        esperarTexto(PREGUNTAS_DE_RESPALDO.first())

        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(PREGUNTAS_DE_RESPALDO.first())), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        esperarTexto("Respuesta de prueba")

        assertEquals(1, enviados.size)
        val mandado = enviados.single().messages
        assertEquals(ChatRole.USER, mandado.first().role, "la API exige que el primer mensaje sea del usuario")
        assertEquals(PREGUNTAS_DE_RESPALDO.first(), mandado.single().content)
        assertFalse(hay(NO_REEMPLAZA_A_UN_ASESOR), "con conversación, el arranque ya no se pinta")
    }

    @Test
    fun `abierta con una pregunta lista la manda sola, una vez`() {
        conRepositorioQueContesta()
        val pregunta = "¿Qué deuda me conviene abonar primero?"
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}, preguntaInicial = pregunta) } }
        }

        esperarTexto("Respuesta de prueba")
        composeRule.waitForIdle()

        assertEquals(1, enviados.size, "la pregunta del Inicio se manda una sola vez")
        assertEquals(pregunta, enviados.single().messages.single().content)
    }
}

private const val AVD_MOVI_CHAT = "w411dp-h731dp-xhdpi"
