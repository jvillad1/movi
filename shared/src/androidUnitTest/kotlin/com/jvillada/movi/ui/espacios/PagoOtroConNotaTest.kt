package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # «Pagó otro» con su nota (R3)
 *
 * La descripción del gasto es la nota que escribe el dueño («Mercado del sábado»); sin nota, la
 * categoría, como antes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1400dp-xhdpi")
class PagoOtroConNotaTest {

    @get:Rule val composeRule = createComposeRule()

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun anotar(nota: String?): GastoDelEspacio {
        val anotados = mutableListOf<GastoDelEspacio>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun anotarGastoDeOtro(espacioId: String, gasto: GastoDelEspacio): GastoDelEspacio =
                gasto.also { anotados += it }
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaPagoOtro(espacio = casa, onDismiss = {}, onGuardado = {}, categoriaInicial = "Mercado")
                }
            }
        }
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("240000")
        if (nota != null) composeRule.onNodeWithTag(TAG_NOTA_PAGO_OTRO, useUnmergedTree = true).performTextInput(nota)
        composeRule.waitForIdle()
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText("Guardar")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { anotados.isNotEmpty() }
        return anotados.single()
    }

    @Test
    fun `la nota es la descripcion del gasto`() {
        val g = anotar("Mercado del sábado")
        assertEquals("Mercado del sábado", g.descripcion)
        assertEquals("Mercado", g.categoria)
        assertEquals(240_000L, g.monto)
        assertEquals("dst_caro", g.pagadoPor)
    }

    @Test
    fun `sin nota la descripcion es la categoria`() {
        assertEquals("Mercado", anotar(nota = null).descripcion)
    }

    /**
     * Vuelta 44: la categoría de «Pagó otro» se elige con el selector del sistema —el de Agregar:
     * «Categoría … Todas ›» y las pastillas con la elegida marcada—, no con el desplegable y su
     * «Administrar categorías».
     */
    @Test
    fun `la categoria se elige con las pastillas de Agregar`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {}
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaPagoOtro(espacio = casa, onDismiss = {}, onGuardado = {}, categoriaInicial = "Mercado")
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CATEGORIA_PAGO_OTRO, useUnmergedTree = true).assertExists()
        assertEquals(0, composeRule.onAllNodes(hasText("Administrar categorías"), useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(TAG_TODAS_PAGO_OTRO, useUnmergedTree = true).assertExists()
        // La elegida, marcada.
        val mercado = composeRule.onNode(
            hasText("Mercado") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(TAG_CATEGORIA_PAGO_OTRO)),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val seleccionada = androidx.compose.ui.semantics.SemanticsProperties.Selected
        val conSeleccion = generateSequence(mercado) { it.parent }.firstOrNull { seleccionada in it.config }
        assertEquals(true, conSeleccion?.config?.get(seleccionada))
    }

    @Test
    fun `una nota en blanco tambien cae a la categoria`() {
        assertEquals("Mercado", descripcionDelPagoOtro("   ", "Mercado"))
        assertEquals("Pan", descripcionDelPagoOtro(" Pan ", "Mercado"))
    }
}
