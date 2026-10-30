package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.OrigenDelRecuerdo
import com.jvillada.movi.shared.model.RecuerdoDelAsistente
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_ICONO_DEL_VACIO
import com.jvillada.movi.ui.components.ds.TAG_ACCION_DEL_ENCABEZADO
import com.jvillada.movi.ui.components.ds.TAG_CAMPO
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDeIconoKey
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Lo que Movi sabe de ti» se escribe a mano, y su vacío va en el círculo neutro (vuelta 61)
 *
 * Sin crédito en Anthropic el chat no propone recordar nada, y la memoria no se podía llenar de
 * ninguna forma. Ahora «Agregar» (la acción del encabezado, y la del vacío) abre un recuerdo nuevo que
 * se guarda con el `POST /api/asistente/memoria` de siempre, con el origen `A_MANO`. Y el vacío ya no
 * pinta su ícono en el círculo amarillo lleno: el amarillo es de la acción (vuelta 42).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LaMemoriaSeEscribeAManoTest {

    @get:Rule val composeRule = createComposeRule()

    private val guardados = mutableListOf<Pair<String, OrigenDelRecuerdo>>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getMemoriaDelAsistente() = emptyList<RecuerdoDelAsistente>()
            override suspend fun guardarRecuerdo(texto: String, origen: OrigenDelRecuerdo, propuestaId: String?): RecuerdoDelAsistente {
                guardados += texto to origen
                return RecuerdoDelAsistente("m-nuevo", texto, origen = origen, creadoEn = 1_761_000_000_000L)
            }
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { LoQueMoviSabeScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay(NADA_TODAVIA) }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocarBoton(texto: String) =
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `el vacio pinta su icono en el circulo neutro`() {
        montar()
        val tono = composeRule.onNodeWithTag(TAG_ICONO_DEL_VACIO, useUnmergedTree = true).fetchSemanticsNode().config[TonoDeIconoKey]
        assertEquals(TonoDeIcono.Neutro, tono)
    }

    @Test
    fun `Agregar escribe un recuerdo a mano con el endpoint de siempre`() {
        montar()
        tocarBoton(AGREGAR_UN_RECUERDO)
        composeRule.waitUntil(5_000) { hay(ROTULO_DEL_RECUERDO_NUEVO) }

        composeRule.onNode(hasTestTag(TAG_CAMPO) and hasAnyAncestor(hasTestTag(TAG_RECUERDO_NUEVO)), useUnmergedTree = true)
            .performTextInput("  Caro es mi esposa  ")
        composeRule.waitForIdle()
        tocarBoton(GUARDAR)

        composeRule.waitUntil(5_000) { hay("Lo escribiste tú") }
        assertEquals(listOf("Caro es mi esposa" to OrigenDelRecuerdo.A_MANO), guardados)
        assertTrue(hay("Caro es mi esposa"))
        assertTrue(!hay(NADA_TODAVIA), "con un recuerdo ya no está el vacío")
    }

    @Test
    fun `el vacio tambien ofrece escribir uno`() {
        montar()
        tocarBoton(AGREGAR_UN_RECUERDO)
        composeRule.waitUntil(5_000) { hay(ROTULO_DEL_RECUERDO_NUEVO) }
        tocarBoton(CANCELAR)
        composeRule.waitUntil(5_000) { hay(NADA_TODAVIA) }
        assertTrue(guardados.isEmpty())
    }

    /**
     * Vuelta 78 · **una sola acción para agregar un recuerdo**. Con la memoria vacía, el encabezado traía
     * «Agregar» y el vacío «Escribir un recuerdo»: hacían lo mismo. Ahora va la del vacío; con un recuerdo
     * guardado, vuelve la del encabezado (y el vacío ya no está).
     */
    @Test
    fun `una sola accion para agregar, en el vacio o en el encabezado`() {
        montar()
        fun enElEncabezado() = composeRule.onAllNodesWithTag(TAG_ACCION_DEL_ENCABEZADO, useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(0, enElEncabezado(), "con el vacío, el encabezado no repite la acción")
        assertTrue(hay(AGREGAR_UN_RECUERDO))

        tocarBoton(AGREGAR_UN_RECUERDO)
        composeRule.waitUntil(5_000) { hay(ROTULO_DEL_RECUERDO_NUEVO) }
        composeRule.onNode(hasTestTag(TAG_CAMPO) and hasAnyAncestor(hasTestTag(TAG_RECUERDO_NUEVO)), useUnmergedTree = true)
            .performTextInput("Caro es mi esposa")
        composeRule.waitForIdle()
        tocarBoton(GUARDAR)
        composeRule.waitUntil(5_000) { hay("Lo escribiste tú") }

        assertEquals(1, enElEncabezado(), "con recuerdos, la acción va arriba")
        assertTrue(!hay(AGREGAR_UN_RECUERDO), "y es la única")
    }
}
