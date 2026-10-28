package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_ACCION_DEL_ENCABEZADO
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO
import com.jvillada.movi.ui.components.ds.TAG_PILDORA
import com.jvillada.movi.ui.components.ds.ROTULO_VOLVER
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El encabezado de Movi AI es el del sistema (vuelta de diseño 4)
 *
 * «Movi AI / Conoce tus finanzas» con la marca «Beta» como `Pildora`, con el alto y los márgenes de
 * todas las pantallas; con conversación, «Nueva conversación» es la acción del encabezado, con la
 * misma forma que «Nueva cuenta» o «Nuevo crédito».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h731dp-xhdpi")
class ElEncabezadoDeMoviAiTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(vararg mensajes: ChatMessage, comoRaiz: Boolean = false) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(mensajes.toList())
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}, comoRaiz = comoRaiz) } } }
        composeRule.waitForIdle()
    }

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    /** Los textos y las descripciones (la acción en su ícono dice su nombre así: vuelta de diseño 8). */
    private fun textosDentro(nodo: SemanticsNode): List<String> =
        listOfNotNull(nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }) +
            nodo.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            nodo.children.flatMap(::textosDentro)

    @Test
    fun `sin conversacion, el encabezado del sistema con Beta en una pildora`() {
        montar()

        val encabezado = nodos(TAG_ENCABEZADO).single()
        val textos = textosDentro(encabezado)
        assertTrue("Movi AI" in textos && "Conoce tus finanzas" in textos, "título y subtítulo en el Encabezado: $textos")
        val densidad = composeRule.density.density
        assertTrue(
            abs(encabezado.size.height / densidad - Medida.altoDeEncabezado.value) <= 0.5f,
            "el alto de todos los encabezados (${Medida.altoDeEncabezado}), no ${encabezado.size.height / densidad} dp",
        )
        val beta = nodos(TAG_PILDORA).single { MARCA_BETA in textosDentro(it) }
        assertTrue(beta.boundsInRoot.right <= encabezado.boundsInRoot.right - Medida.margenDePantalla.value * densidad + 0.5f, "con el margen de la pantalla")
        assertEquals(0, nodos(TAG_ACCION_DEL_ENCABEZADO).size)
    }

    @Test
    fun `con conversacion, Nueva conversacion es la accion del encabezado`() {
        montar(ChatMessage(ChatRole.USER, "¿Cómo voy?"), ChatMessage(ChatRole.ASSISTANT, "Vas bien."))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Vas bien.", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        val accion = nodos(TAG_ACCION_DEL_ENCABEZADO).single()
        assertTrue(NUEVA_CONVERSACION in textosDentro(accion))
        assertTrue(nodos(TAG_PILDORA).none { MARCA_BETA in textosDentro(it) }, "con conversación, la acción reemplaza la marca")
    }

    /**
     * Vuelta 33: **como raíz de su pestaña** (el rail de una ventana ancha) el encabezado no lleva la
     * flecha; **apilada** encima de otra pantalla, sí. Revisión de la vuelta 38: tampoco lleva el
     * avatar, que va solo en Inicio, como en la maqueta.
     */
    @Test
    fun `como raiz de su pestana, sin flecha ni avatar`() {
        montar(comoRaiz = true)
        val textos = textosDentro(nodos(TAG_ENCABEZADO).single())
        assertTrue(ROTULO_VOLVER !in textos, "sin «←»: $textos")
        assertTrue("U" !in textos, "sin avatar: $textos")
    }

    @Test
    fun `apilada encima de otra pantalla, con flecha`() {
        montar(comoRaiz = false)
        val textos = textosDentro(nodos(TAG_ENCABEZADO).single())
        assertTrue(ROTULO_VOLVER in textos, "con «←»: $textos")
        assertTrue("U" !in textos, "sin avatar: $textos")
    }
}
