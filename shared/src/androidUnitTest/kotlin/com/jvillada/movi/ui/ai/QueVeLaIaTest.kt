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
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.AvatarPalette
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.ResumenDeLoQueMoviSabe
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
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
 * # «Qué ve la IA» (vuelta 95)
 *
 * - No visto: lo escrito a mano abre la hoja y **no** llega al modelo hasta «Entendido».
 * - Visto (por el perfil del servidor): no aparece.
 * - Las tres preguntas sugeridas no la disparan.
 * - El servidor guarda la marca una vez; «Ver qué sabe Movi» la guarda y navega sin mandar nada.
 * - «Lo que Movi sabe de ti» abre con la frase de los cuatro conteos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class QueVeLaIaTest {

    @get:Rule val composeRule = createComposeRule()

    private val mandados = mutableListOf<AiChatRequest>()
    private var marcas = 0

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun perfil(vistoEn: Long?) = UserProfile(
        id = "u", email = "u@movi.test", name = "U", avatarColor = AvatarPalette.DEFAULT, avisoIaVistoEn = vistoEn,
    )

    private fun servidor(vistoEn: Long?) {
        AvisoDeLaIa.visto = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(emptyList())
            override suspend fun getUserProfile() = perfil(vistoEn)
            override suspend fun marcarAvisoDeLaIa() { marcas++ }
            override suspend fun chatAi(request: AiChatRequest): AiChatResponse {
                mandados += request
                return AiChatResponse(text = "Listo.")
            }
        }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) =
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)

    private fun chat(pregunta: String, sugerida: String? = null, onNavigate: (Screen) -> Unit = {}) =
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    AIChatScreen(onNavigate = onNavigate, preguntaInicial = pregunta, sugeridaInicial = sugerida)
                }
            }
        }

    @Test
    fun `no visto - la pregunta libre abre la hoja y no llega al modelo hasta Entendido`() {
        servidor(vistoEn = null)
        chat("¿Cuánto gasté en Rappi?")

        composeRule.waitUntil(5_000) { hay(TITULO_QUE_VE_LA_IA) }
        assertTrue(hay(NO_ENTRENA))
        composeRule.waitForIdle()
        assertTrue(mandados.isEmpty(), "mientras la hoja está abierta no se manda nada")

        tocar(ENTENDIDO)
        composeRule.waitUntil(5_000) { mandados.size == 1 }
        composeRule.waitUntil(5_000) { marcas == 1 }
        assertEquals(true, AvisoDeLaIa.visto)
        assertFalse(hay(TITULO_QUE_VE_LA_IA))
    }

    @Test
    fun `visto en el servidor - no aparece y la pregunta sale`() {
        servidor(vistoEn = 1_760_000_000_000L)
        chat("¿Cuánto gasté en Rappi?")

        composeRule.waitUntil(5_000) { mandados.size == 1 }
        assertFalse(hay(TITULO_QUE_VE_LA_IA))
        assertEquals(0, marcas, "no vuelve a marcarse lo que ya estaba")
    }

    @Test
    fun `las preguntas sugeridas no la disparan`() {
        servidor(vistoEn = null)
        chat(PreguntaSugerida.COMO_VOY.texto, sugerida = PreguntaSugerida.COMO_VOY.id)

        composeRule.waitUntil(5_000) { mandados.size == 1 }
        assertFalse(hay(TITULO_QUE_VE_LA_IA))
        assertEquals(PreguntaSugerida.COMO_VOY.id, mandados.single().messages.last().preguntaSugerida)
        assertEquals(0, marcas)
    }

    @Test
    fun `Ver que sabe Movi lo marca visto, navega y no manda la pregunta`() {
        servidor(vistoEn = null)
        val destinos = mutableListOf<Screen>()
        chat("¿Cuánto gasté en Rappi?", onNavigate = { destinos += it })

        composeRule.waitUntil(5_000) { hay(VER_QUE_SABE_MOVI) }
        tocar(VER_QUE_SABE_MOVI)
        composeRule.waitUntil(5_000) { destinos.isNotEmpty() }
        assertEquals(Screen.LoQueMoviSabe, destinos.single())
        composeRule.waitUntil(5_000) { marcas == 1 }
        composeRule.waitForIdle()
        assertTrue(mandados.isEmpty())
        assertEquals(true, AvisoDeLaIa.visto)
    }

    @Test
    fun `cerrar la hoja sin elegir no manda ni la da por vista`() {
        servidor(vistoEn = null)
        val puerta = arrayOfNulls<PuertaDeLaIa>(1)
        var corrio = false
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    val p = rememberPuertaDeLaIa {}
                    puerta[0] = p
                    p.Hoja()
                }
            }
        }
        composeRule.runOnIdle { puerta[0]!!.pasar { corrio = true } }
        composeRule.waitUntil(5_000) { puerta[0]!!.abierta }
        composeRule.runOnIdle { puerta[0]!!.cerrar() }
        composeRule.waitForIdle()
        assertFalse(corrio)
        assertFalse(puerta[0]!!.abierta)
        assertTrue(AvisoDeLaIa.visto != true)
    }

    @Test
    fun `Lo que Movi sabe abre con la frase de los cuatro conteos`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getMemoriaDelAsistente() = emptyList<com.jvillada.movi.shared.model.RecuerdoDelAsistente>()
            override suspend fun getResumenDeLoQueMoviSabe() = ResumenDeLoQueMoviSabe(133, "2026-08-12", 3, 24, 2)
        }
        val destinos = mutableListOf<Screen>()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { LoQueMoviSabeScreen(onNavigate = { destinos += it }) } }
        }
        composeRule.waitUntil(5_000) { hay("Movi tiene 133 movimientos desde el 12 de agosto de 2026") }
        // Con la memoria vacía el número de recuerdos es el de la lista de abajo, no el del servidor.
        composeRule.waitUntil(5_000) { hay("24 reglas de categoría aprendidas y 0 recuerdos") }
        tocar(VER_CUENTAS)
        assertEquals(Screen.Accounts, destinos.single())
        tocar(VER_MOVIMIENTOS)
        assertTrue(destinos.last() is Screen.Transactions)
    }
}
