package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El campo de Movi AI no queda debajo del teclado (vuelta 117)
 *
 * El dueño: «que el teclado funcione bien, porque a veces tapa el campo para chatear con la IA». La
 * raíz de `App.kt` descuenta el teclado (`imePadding`; en la web, `altoDelTecladoDeLaWeb`), así que la
 * pantalla vive en lo que queda arriba de él. Lo que fallaba era adentro: la tarjeta «¿Seguimos con
 * esta?» y un campo que crecía sin tope se medían antes que la barra de escribir, y con un teléfono
 * chico y la letra grande la barra se quedaba sin alto.
 *
 * Teléfono chico (360×640 dp), letra al 130 % del teléfono por el 1,12 de la app, y el teclado
 * simulado: la pantalla pasa a vivir en 320 dp, lo que queda con el teclado arriba.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ElCampoDeMoviAiSeVeConElTecladoTest {

    @get:Rule val composeRule = createComposeRule()

    private val pedidos = mutableListOf<AiChatRequest>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun conConversacion(enviadoEn: Long, vararg mensajes: ChatMessage) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() =
                ConversacionDelAsistente(mensajes.map { it.copy(enviadoEn = enviadoEn) }, id = "cv_1")
            override suspend fun chatAi(request: AiChatRequest): AiChatResponse {
                pedidos += request
                return AiChatResponse("Listo.", conversacionId = "cv_1")
            }
        }
    }

    private var alto by mutableStateOf(640.dp)

    private fun montar(altoInicial: Dp) {
        alto = altoInicial
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * ESCALA_DEL_TELEFONO_AL_130)) {
                    Box(Modifier.fillMaxWidth().height(alto)) { AIChatScreen(onNavigate = {}) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun limites(n: SemanticsNodeInteraction): Rect = n.fetchSemanticsNode().boundsInRoot

    private fun altoVisible(): Float = with(composeRule.density) { alto.toPx() }

    private fun campo() = composeRule.onNodeWithTag(TAG_CAMPO_DE_MOVI_AI)

    private fun enviar() = composeRule.onAllNodes(hasClickAction() and hasAnyChild(hasContentDescription("Enviar")), useUnmergedTree = true)[0]

    /** Entero dentro de lo que se ve arriba del teclado. */
    private fun seVe(n: SemanticsNodeInteraction, que: String) {
        val r = limites(n)
        assertTrue(r.height > 0f, "$que tiene alto: $r")
        assertTrue(r.top >= -0.5f && r.bottom <= altoVisible() + 0.5f, "$que se ve entero arriba del teclado: $r en ${altoVisible()}")
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `con la tarjeta de otro dia y la letra grande, el campo y Enviar se ven y se puede mandar`() {
        val haceTresDias = Clock.System.now().toEpochMilliseconds() - 3 * 24 * 3_600_000L
        conConversacion(
            haceTresDias,
            ChatMessage(ChatRole.USER, "¿Cuánto gasté en restaurantes?"),
            ChatMessage(ChatRole.ASSISTANT, "Llevas \$412.000 en restaurantes este período."),
        )
        montar(320.dp)
        composeRule.waitUntil(5_000) { hay("Llevas") || hay(EMPEZAR_UNA_CONVERSACION_NUEVA) }

        seVe(campo(), "el campo")
        seVe(enviar(), "Enviar")

        campo().performTextReplacement("¿y en Mercado?")
        enviar().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { pedidos.isNotEmpty() }
        assertEquals("¿y en Mercado?", pedidos.single().messages.last().content)
        assertEquals("cv_1", pedidos.single().conversacionId)
    }

    @Test
    fun `una pregunta larga no empuja el campo debajo del teclado`() {
        conConversacion(Clock.System.now().toEpochMilliseconds(), ChatMessage(ChatRole.USER, "hola"), ChatMessage(ChatRole.ASSISTANT, "¡Hola!"))
        montar(320.dp)
        composeRule.waitUntil(5_000) { hay("¡Hola!") }

        campo().performTextReplacement((1..30).joinToString("\n") { "renglón $it de una pregunta muy larga" })
        composeRule.waitForIdle()

        seVe(campo(), "el campo, con treinta renglones")
        seVe(enviar(), "Enviar")
    }

    @Test
    fun `al abrirse el teclado el ultimo mensaje sigue a la vista, arriba del campo`() {
        val larga = (1..12).joinToString("\n\n") { "Párrafo $it de una respuesta que ocupa bastante." } + "\n\nFin de la respuesta."
        conConversacion(
            Clock.System.now().toEpochMilliseconds(),
            ChatMessage(ChatRole.USER, "explícame todo"),
            ChatMessage(ChatRole.ASSISTANT, larga),
        )
        montar(640.dp)
        composeRule.waitUntil(5_000) { hay("Fin de la respuesta.") }

        // Se abre el teclado: lo que queda arriba de él son 320 dp.
        alto = 320.dp
        composeRule.waitForIdle()

        val fin = composeRule.onAllNodesWithText("Fin de la respuesta.", substring = true, useUnmergedTree = true)
        composeRule.waitUntil(5_000) { fin.fetchSemanticsNodes().isNotEmpty() }
        val ultimo = fin.fetchSemanticsNodes().last().boundsInRoot
        val arribaDelCampo = limites(campo()).top
        assertTrue(ultimo.bottom <= arribaDelCampo + 0.5f && ultimo.bottom > 0f, "el final de la respuesta ($ultimo) se ve arriba del campo ($arribaDelCampo)")
        seVe(campo(), "el campo")
        assertTrue(composeRule.onRoot().fetchSemanticsNode().boundsInRoot.height >= altoVisible() - 0.5f)
    }
}
