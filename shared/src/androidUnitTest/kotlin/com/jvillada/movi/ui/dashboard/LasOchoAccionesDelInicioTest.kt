package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LocalAtajosDeAgregar
import com.jvillada.movi.ui.components.ds.COLUMNAS_DE_ACCIONES
import com.jvillada.movi.ui.components.ds.TAG_ACCION_RAPIDA
import com.jvillada.movi.ui.components.ds.TAG_ROTULO_DE_ACCION
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import com.jvillada.movi.ui.sdui.AccionesDelInicioSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Las 8 acciones del Inicio (vuelta 39, la opción 3)
 *
 * En dos filas de cuatro y en el orden de la maqueta; desde la vuelta 79 los rótulos van en **un
 * renglón** («Comprobante», «Preguntar»), y a 360 dp —el teléfono más angosto— con la letra de la app
 * (×1,12) y con la del teléfono al 130 % **ninguno pasa a dos renglones** ni se corta con «…». Y una prueba por acción: cada una abre lo mismo que su atajo del menú
 * del «+» (la cáscara, por `LocalAtajosDeAgregar`) o su pantalla de siempre.
 *
 * Se monta la sección como la monta el Inicio. `sdk = [34]` y `NATIVE` para el motor de texto real.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LasOchoAccionesDelInicioTest {

    @get:Rule val composeRule = createComposeRule()

    private val navegado = mutableListOf<Screen>()
    private val atajos = mutableListOf<AtajoDeAgregar>()

    private fun montar(escala: Float = 1.12f) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, base.fontScale * escala),
                LocalAtajosDeAgregar provides { atajos += it },
            ) {
                MoviTheme {
                    Box(Modifier.fillMaxWidth()) {
                        AccionesDelInicioSection(DashboardData(pendingSms = 3), onNavigate = { navegado += it })
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun rotulos(): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(TAG_ROTULO_DE_ACCION, useUnmergedTree = true).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    @Test
    fun `son ocho, en el orden de la maqueta, en dos filas de cuatro`() {
        montar()
        assertEquals(
            listOf(
                "Gasto", "Ingreso", "Traspasar", "Pagar tarjeta",
                "Repartir", "Comprobante", "Revisar avisos", "Preguntar",
            ),
            rotulos().map(::texto),
        )
        val acciones = composeRule.onAllNodesWithTag(TAG_ACCION_RAPIDA).fetchSemanticsNodes()
        assertEquals(8, acciones.size)
        val filas = acciones.map { it.boundsInRoot.top }.distinct()
        assertEquals(2, filas.size, "dos filas")
        assertEquals(COLUMNAS_DE_ACCIONES, acciones.count { it.boundsInRoot.top == filas.first() })
        assertEquals(1, acciones.map { it.size.height }.distinct().size, "todas del mismo alto: el rótulo tiene alto fijo")
    }

    /**
     * Vuelta 79: **un renglón**, sin «…» y sin salirse, a 360 dp con la letra de la app (×1,12) y con la del
     * teléfono al 130 % (×1,3 × 1,12). Antes «Subir comprobante» y «Preguntar a Movi» iban en dos.
     */
    private fun cadaRotuloEnUnRenglon(escala: Float) {
        montar(escala)
        val fallas = rotulos().mapNotNull { nodo ->
            val d = disposicion(nodo)
            val t = texto(nodo)
            when {
                d.lineCount != 1 -> "«$t» en ${d.lineCount} renglones"
                d.isLineEllipsized(0) -> "«$t» se corta con «…»"
                d.hasVisualOverflow -> "«$t» se sale de su lugar"
                else -> null
            }
        }
        assertTrue(fallas.isEmpty(), "a ×$escala: " + fallas.joinToString("; "))
    }

    @Test
    fun `a 360 dp cada rotulo va en un renglon`() = cadaRotuloEnUnRenglon(1.12f)

    @Test
    fun `a 360 dp con la letra al 130 por ciento cada rotulo sigue en un renglon`() =
        cadaRotuloEnUnRenglon(com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130)

    /** Vuelta 79: entre las dos filas, `Espacio.m`; el círculo, de 52. */
    @Test
    fun `las dos filas van a Espacio m y los circulos miden 52`() {
        montar()
        val acciones = composeRule.onAllNodesWithTag(TAG_ACCION_RAPIDA).fetchSemanticsNodes()
        val primera = acciones.first().boundsInRoot
        val quinta = acciones[COLUMNAS_DE_ACCIONES].boundsInRoot
        val aire = with(composeRule.density) { (quinta.top - primera.bottom).toDp() }
        assertEquals(com.jvillada.movi.theme.Espacio.m.value, aire.value, 0.6f)
        assertEquals(52f, com.jvillada.movi.theme.Medida.circuloDeAccion.value)
    }

    @Test
    fun `Anotar gasto abre Agregar en Gasto`() {
        montar()
        composeRule.onNodeWithContentDescription("Anotar un gasto", useUnmergedTree = true).performClick()
        assertEquals(listOf(AtajoDeAgregar.GASTO), atajos)
    }

    @Test
    fun `Anotar ingreso abre Agregar en Ingreso`() {
        montar()
        composeRule.onNodeWithContentDescription("Anotar un ingreso", useUnmergedTree = true).performClick()
        assertEquals(listOf(AtajoDeAgregar.INGRESO), atajos)
    }

    @Test
    fun `Traspasar abre Agregar en Traspaso`() {
        montar()
        composeRule.onNodeWithContentDescription("Anotar un traspaso entre tus cuentas", useUnmergedTree = true).performClick()
        assertEquals(listOf(AtajoDeAgregar.TRASPASO), atajos)
    }

    @Test
    fun `Pagar tarjeta abre Agregar en el pago de tarjeta`() {
        montar()
        composeRule.onNodeWithContentDescription("Anotar el pago de una tarjeta", useUnmergedTree = true).performClick()
        assertEquals(listOf(AtajoDeAgregar.PAGO_DE_TARJETA), atajos)
    }

    @Test
    fun `Repartir abre las hojas de repartir`() {
        montar()
        composeRule.onNodeWithContentDescription("Repartir un gasto con alguien", useUnmergedTree = true).performClick()
        assertEquals(listOf(AtajoDeAgregar.REPARTIR), atajos)
    }

    @Test
    fun `Subir comprobante elige el papel`() {
        montar()
        composeRule.onNodeWithContentDescription("Subir un comprobante para que Movi lo lea", useUnmergedTree = true).performClick()
        assertEquals(listOf(AtajoDeAgregar.SUBIR_COMPROBANTE), atajos)
    }

    @Test
    fun `Revisar avisos abre Por revisar, con su contador`() {
        montar()
        composeRule.onNodeWithContentDescription("Revisar los avisos (3 avisos), 3", useUnmergedTree = true).performClick()
        assertEquals(listOf<Screen>(Screen.PorRevisar), navegado)
        assertTrue(atajos.isEmpty())
    }

    @Test
    fun `Preguntar a Movi abre Movi AI`() {
        montar()
        composeRule.onNodeWithContentDescription("Preguntarle a Movi", useUnmergedTree = true).performClick()
        assertEquals(listOf<Screen>(Screen.AIChat()), navegado)
    }
}
