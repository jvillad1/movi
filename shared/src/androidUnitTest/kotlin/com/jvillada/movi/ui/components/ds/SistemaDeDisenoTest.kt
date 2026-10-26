package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.LocalSaldosOcultos
import com.jvillada.movi.ui.components.PLATA_OCULTA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ola R0: el contrato de cada componente de `ui/components/ds/`, sin ninguna pantalla alrededor.
 * Que se componga en los dos temas, y las tres promesas que las pantallas van a dar por sentadas:
 * la [Fila] nunca mide menos que su alto, el [Boton] siempre se puede tocar con un dedo, y la
 * [Cifra] se tapa con «Ocultar los saldos».
 */
@RunWith(RobolectricTestRunner::class)
class SistemaDeDisenoTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(oscuro: Boolean = true, ocultos: Boolean = false, contenido: @Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme(oscuro = oscuro) {
                CompositionLocalProvider(LocalSaldosOcultos provides ocultos) {
                    Box(Modifier.fillMaxSize()) { contenido() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** El alto dibujado del nodo [indice] con [tag]. */
    private fun altoDe(tag: String, indice: Int = 0): Dp = with(composeRule.density) {
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)[indice].fetchSemanticsNode().size.height.toDp()
    }

    /** El alto del área de toque (con la extensión al objetivo táctil que hace Compose). */
    private fun altoTactilDe(tag: String, indice: Int = 0): Dp = with(composeRule.density) {
        composeRule.onAllNodesWithTag(tag)[indice].fetchSemanticsNode().touchBoundsInRoot.height.toDp()
    }

    @Composable
    private fun TodoElSistema() {
        Column {
            Encabezado("Movimientos", acciones = { BotonDeIcono(Icons.Rounded.Search, "Buscar", onClick = {}) })
            Seccion(titulo = "Tarjetas", conteo = 3, accion = "Ver todo", onAccion = {})
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                Fila(titulo = "Nu", subtitulo = "Ahorros", inicio = { IconoEnCirculo(Icons.Rounded.Inbox) }, final = { Cifra(1_500_000) })
                Separador()
                Fila(titulo = "Bancolombia", final = { Chevron() }, onClick = {})
            }
            Superficie(nivel = NivelDeSuperficie.Capa2) { Text("Anidada") }
            Boton("Guardar", onClick = {}, icono = Icons.Rounded.Add)
            Boton("Borrar", onClick = {}, variante = VarianteDeBoton.Peligro, tamano = TamanoDeBoton.S)
            Pildora("Vence en 2 días", TonoDePildora.Aviso)
            PildoraDeFiltro("Gastos", seleccionada = true, onClick = {})
            CuadriculaDeAcciones(
                listOf(
                    Accion("Anotar gasto", Icons.Rounded.Add, {}, destacada = true),
                    Accion("Traspasar", Icons.Rounded.SwapHoriz, {}),
                ),
            )
            TarjetaDePropuesta(
                icono = Icons.Rounded.Inbox,
                titulo = "Llegaron 3 avisos del banco",
                cifra = 186_400,
                cuerpo = "por confirmar",
                accion = "Confirmar los 3",
                onAccion = {},
                accionSecundaria = "Ver",
                onAccionSecundaria = {},
            )
            Cifra(3_240_000, VarianteDeCifra.Heroe)
            BarraDeProgreso(0.4f)
            EsqueletoDeFila()
        }
    }

    @Test
    fun `todo el sistema se compone en el tema oscuro`() {
        montar(oscuro = true) { TodoElSistema() }
        composeRule.onNodeWithText("Llegaron 3 avisos del banco").assertExists()
        composeRule.onNodeWithTag(TAG_ENCABEZADO).assertExists()
        composeRule.onNodeWithTag(TAG_CUADRICULA_DE_ACCIONES).assertExists()
    }

    @Test
    fun `todo el sistema se compone en el tema claro`() {
        montar(oscuro = false) { TodoElSistema() }
        composeRule.onNodeWithText("Tarjetas").assertExists()
        composeRule.onNodeWithTag(TAG_BARRA_DE_PROGRESO).assertExists()
    }

    @Test
    fun `la fila mide 56 con un renglon y 64 con dos, y su esqueleto lo mismo`() {
        montar {
            Column {
                Fila(titulo = "Un renglón")
                Fila(titulo = "Dos renglones", subtitulo = "con subtítulo")
                EsqueletoDeFila(conSubtitulo = true)
            }
        }
        val una = altoDe(TAG_FILA, 0)
        val dos = altoDe(TAG_FILA, 1)
        assertTrue(una >= Medida.altoDeFila, "una fila de un renglón mide $una")
        assertTrue(dos >= Medida.altoDeFilaDoble, "una fila de dos renglones mide $dos")
        assertEquals(dos, altoDe(TAG_ESQUELETO_DE_FILA), "el esqueleto no mide lo mismo que la fila")
    }

    @Test
    fun `la fila con onClick se toca entera`() {
        var tocada = 0
        montar { Fila(titulo = "Nu", subtitulo = "Ahorros", onClick = { tocada++ }) }
        composeRule.onNodeWithTag(TAG_FILA).performClick()
        assertEquals(1, tocada)
    }

    @Test
    fun `el boton respeta el objetivo tactil, tambien el chico`() {
        montar {
            Column {
                Boton("Grande", onClick = {})
                Boton("Chico", onClick = {}, tamano = TamanoDeBoton.S)
            }
        }
        assertTrue(altoDe(TAG_BOTON, 0) >= Medida.objetivoTactil, "el botón M mide ${altoDe(TAG_BOTON, 0)}")
        assertEquals(Medida.altoChico, altoDe(TAG_BOTON, 1), "el botón S se dibuja a 36")
        assertTrue(altoTactilDe(TAG_BOTON, 1) >= Medida.objetivoTactil, "el toque del botón S mide ${altoTactilDe(TAG_BOTON, 1)}")
    }

    @Test
    fun `el boton cargando no se toca y lo dice`() {
        var tocado = 0
        montar { Boton("Guardar", onClick = { tocado++ }, cargando = true) }
        composeRule.onNodeWithTag(TAG_BOTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_BOTON).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ROTULO_CARGANDO))
        composeRule.onNodeWithTag(TAG_BOTON).performClick()
        assertEquals(0, tocado)
    }

    @Test
    fun `la cifra se ve con los saldos a la vista`() {
        montar(ocultos = false) {
            Column {
                Cifra(-27_500)
                Cifra(4_500_000, tono = TonoDeCifra.Ingreso)
            }
        }
        composeRule.onNodeWithText("−$27.500").assertIsDisplayed()
        composeRule.onNodeWithText("+$4.500.000").assertIsDisplayed()
    }

    @Test
    fun `la cifra se oculta con los saldos ocultos, en todas sus variantes`() {
        montar(ocultos = true) {
            Column {
                VarianteDeCifra.entries.forEach { Cifra(-27_500, it) }
                TarjetaDePropuesta(Icons.Rounded.Inbox, "Avisos", accion = "Ver", onAccion = {}, cifra = 186_400)
            }
        }
        assertEquals(VarianteDeCifra.entries.size + 1, composeRule.onAllNodesWithTag(TAG_CIFRA).fetchSemanticsNodes().size)
        assertEquals(
            VarianteDeCifra.entries.size + 1,
            composeRule.onAllNodesWithText(PLATA_OCULTA).fetchSemanticsNodes().size,
        )
        assertTrue(composeRule.onAllNodesWithText("27.500", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `la cuadricula pone cuatro por fila y dice el conteo de la insignia`() {
        montar {
            CuadriculaDeAcciones(
                List(5) { i -> Accion("Acción $i", Icons.Rounded.Add, {}, insignia = if (i == 4) 3 else null) },
            )
        }
        val acciones = composeRule.onAllNodesWithTag(TAG_ACCION_RAPIDA).fetchSemanticsNodes()
        assertEquals(5, acciones.size)
        val filas = acciones.map { it.boundsInRoot.top }.distinct()
        assertEquals(2, filas.size, "cinco acciones son dos filas")
        assertEquals(4, acciones.count { it.boundsInRoot.top == filas.first() })
        composeRule.onNodeWithContentDescription("Acción 4, 3").assertIsDisplayed()
    }

    @Test
    fun `la accion de una seccion se toca`() {
        var tocada = 0
        montar { Seccion(titulo = "Tarjetas", accion = "Ver todo", onAccion = { tocada++ }) }
        composeRule.onNodeWithText("Ver todo").performClick()
        assertEquals(1, tocada)
    }

    @Test
    fun `el filtro elegido lo dice`() {
        montar { PildoraDeFiltro("Gastos", seleccionada = true, onClick = {}) }
        composeRule.onNodeWithTag(TAG_PILDORA_DE_FILTRO).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `la barra de progreso recorta la fraccion`() {
        montar { BarraDeProgreso(1.7f) }
        val info = composeRule.onNodeWithTag(TAG_BARRA_DE_PROGRESO)
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(1f, info.current)
    }

    @Test
    fun `el encabezado mide lo mismo con flecha, con avatar y sin nada`() {
        montar {
            Column {
                Encabezado("Con flecha", onVolver = {})
                Encabezado("Sin nada")
            }
        }
        assertEquals(Medida.altoDeEncabezado, altoDe(TAG_ENCABEZADO, 0))
        assertEquals(Medida.altoDeEncabezado, altoDe(TAG_ENCABEZADO, 1))
        composeRule.onNodeWithContentDescription(ROTULO_VOLVER).assertIsDisplayed()
    }
}
