package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.marcaDelBanco
import androidx.compose.ui.graphics.Color
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.LocalSaldosOcultos
import com.jvillada.movi.ui.components.PLATA_OCULTA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun montar(ocultos: Boolean = false, contenido: @Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
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
                Separador()
                Fila(titulo = "Borrar mi cuenta", tonoDelTitulo = TonoDeFila.Alerta, onClick = {})
            }
            Superficie(nivel = NivelDeSuperficie.Capa2) { Text("Anidada") }
            Boton("Guardar", onClick = {}, icono = Icons.Rounded.Add)
            Boton("Borrar", onClick = {}, variante = VarianteDeBoton.Peligro, tamano = TamanoDeBoton.S)
            Pildora("Vence en 2 días", TonoDePildora.Aviso)
            PildoraDeFiltro("Gastos", seleccionada = true, onClick = {})
            PildoraDeAccion("¿Cómo voy?", onClick = {}, icono = Icons.Rounded.Add, descripcion = "¿Cómo voy este período?")
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
            // Vuelta de diseño 14: la casilla, sola (con su toque) y dentro de algo que se marca entero.
            Casilla(marcada = true, onCambiar = {})
            Casilla(marcada = false)
            EsqueletoDeFila()
            // Ola R1: la tarjeta de cuenta del carrusel y los widgets del Inicio.
            TarjetaDeCuenta(
                nombre = "Bancolombia", tipo = "Ahorros", rotuloDelSaldo = "Saldo", saldo = 13_845_800,
                marca = marcaDelBanco("Bancolombia"), icono = Icons.Rounded.AccountBalance, onClick = {},
            )
            ParDeWidgets(
                izquierdo = { m -> Widget("Falta por pagar", onClick = {}, modifier = m) { Cifra(4_312_000, VarianteDeCifra.Grande) } },
                derecho = { m -> Widget("En qué se va", onClick = {}, modifier = m, detalle = "período") { BarraDeProgreso(0.6f) } },
            )
        }
    }

    /** Vuelta 29: un solo tema, el claro de la opción 3. Todo el sistema se compone en él. */
    @Test
    fun `todo el sistema se compone en el tema de Movi`() {
        montar { TodoElSistema() }
        composeRule.onNodeWithText("Llegaron 3 avisos del banco").assertExists()
        composeRule.onNodeWithTag(TAG_ENCABEZADO).assertExists()
        composeRule.onNodeWithTag(TAG_CUADRICULA_DE_ACCIONES).assertExists()
        composeRule.onNodeWithText("Tarjetas").assertExists()
        composeRule.onNodeWithTag(TAG_BARRA_DE_PROGRESO).assertExists()
    }

    /**
     * **Vuelta 29: la sombra solo donde la maqueta la pone.** La propuesta (la tarjeta de avisos) y la
     * superficie destacada (el héroe) la llevan; una lista y una superficie común, no.
     */
    @Test
    fun `la sombra va en la propuesta y en la destacada, nunca en una lista`() {
        montar {
            Column {
                Superficie(relleno = RellenoDeSuperficie.Lista, modifier = Modifier.testTag("lista")) { Text("Lista") }
                Superficie(modifier = Modifier.testTag("comun")) { Text("Común") }
                Superficie(destacada = true, modifier = Modifier.testTag("heroe")) { Text("Héroe") }
                TarjetaDePropuesta(Icons.Rounded.Inbox, "Avisos", accion = "Ver", onAccion = {})
            }
        }
        val destacadas = composeRule.onAllNodes(SemanticsMatcher.expectValue(SuperficieDestacadaKey, true), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(2, destacadas.size, "la propuesta y el héroe")
        assertTrue(laSuperficieLlevaSombra(NivelDeSuperficie.Propuesta, destacada = false))
        assertFalse(laSuperficieLlevaSombra(NivelDeSuperficie.Capa1, destacada = false))
    }

    /**
     * **Vuelta 29: los componentes hablan la opción 3.** El botón principal es amarillo con la tinta
     * encima; el secundario, blanco con el borde de botón; el de texto, en el azul del enlace. El
     * filtro elegido va en tinta con la letra blanca. La píldora de aviso, en el tinte.
     */
    @Test
    fun `los colores de los componentes son los de la opcion 3`() {
        val c = COLORES_CLAROS
        assertEquals(c.marca to c.sobreMarca, coloresDelBoton(VarianteDeBoton.Principal, c))
        assertEquals(c.tarjeta to c.texto, coloresDelBoton(VarianteDeBoton.Secundario, c))
        assertEquals(c.bordeDeBoton, bordeDelBoton(VarianteDeBoton.Secundario, c))
        assertEquals(null, bordeDelBoton(VarianteDeBoton.Principal, c))
        assertEquals(Color.Transparent to c.enlace, coloresDelBoton(VarianteDeBoton.Fantasma, c))
        assertEquals(c.tinte to c.aviso, coloresDeLaPildora(TonoDePildora.Aviso, c))
        assertEquals(c.marca, fondoDelTonoDeIcono(TonoDeIcono.Marca, c))
        assertEquals(c.sobreMarca, colorDelTonoDeIcono(TonoDeIcono.Marca, c))
        // Vuelta 42: el círculo neutro es la capa 3 con el ícono en la tinta, y «Tú», la tinta llena.
        assertEquals(c.capa3, fondoDelTonoDeIcono(TonoDeIcono.Neutro, c))
        assertEquals(c.texto, colorDelTonoDeIcono(TonoDeIcono.Neutro, c))
        assertEquals(c.texto, fondoDelTonoDeIcono(TonoDeIcono.Tinta, c))
        assertEquals(c.tarjeta, colorDelTonoDeIcono(TonoDeIcono.Tinta, c))
    }

    /** Vuelta 29: el filtro mide 44, el de la maqueta, elegido o no. */
    @Test
    fun `el filtro mide 44 elegido y sin elegir`() {
        montar {
            Column {
                PildoraDeFiltro("Todo", seleccionada = true, onClick = {})
                PildoraDeFiltro("Gastos", seleccionada = false, onClick = {})
            }
        }
        assertEquals(Medida.altoDeFiltro, altoDe(TAG_PILDORA_DE_FILTRO, 0))
        assertEquals(Medida.altoDeFiltro, altoDe(TAG_PILDORA_DE_FILTRO, 1))
    }

    @Test
    fun `la fila mide 56 con un renglon y 58 con dos, y su esqueleto lo mismo`() {
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

    /**
     * Vuelta de diseño 14: la casilla que se toca sola mide 48 × 48 (la caja, [Icono.l]), dice
     * «casilla» y su estado, y cambia al tocarla. La de adentro de una fila que se marca entera no se
     * toca: solo dice el estado, con la misma caja.
     */
    @Test
    fun `la casilla mide 48 de toque, dice su estado y cambia`() {
        var marcada by mutableStateOf(false)
        montar {
            Column {
                Casilla(marcada = marcada, onCambiar = { marcada = it }, modifier = Modifier.testTag("sola"))
                Casilla(marcada = true, modifier = Modifier.testTag("en-una-fila"))
            }
        }
        val sola = composeRule.onNodeWithTag("sola")
        sola.assertIsOff()
        with(composeRule.density) {
            val tamano = sola.fetchSemanticsNode().size
            assertEquals(Medida.objetivoTactil, tamano.width.toDp(), "ancho de la casilla")
            assertEquals(Medida.objetivoTactil, tamano.height.toDp(), "alto de la casilla")
            assertEquals(Icono.l, composeRule.onAllNodesWithTag(TAG_CASILLA, useUnmergedTree = true)[0].fetchSemanticsNode().size.width.toDp())
        }
        assertEquals(Role.Checkbox, sola.fetchSemanticsNode().config[SemanticsProperties.Role])
        sola.performClick()
        composeRule.waitForIdle()
        sola.assertIsOn()
        assertTrue(marcada)
        // La de la fila no tiene toque propio.
        assertTrue(SemanticsActions.OnClick !in composeRule.onNodeWithTag("en-una-fila").fetchSemanticsNode().config)
    }

    /**
     * Vuelta de diseño 20: el `BotonDeIcono` secundario (al lado de un `Boton`, en Agregar) mide 48
     * × 48 como el plano, dice su descripción y se apaga sin dejar de decirla.
     */
    @Test
    fun `el boton de icono secundario mide 48, dice que hace y se apaga`() {
        var toques = 0
        montar {
            Column {
                BotonDeIcono(Icons.Rounded.Inbox, "Subir comprobante", onClick = { toques++ },
                    variante = VarianteDeBotonDeIcono.Secundario, modifier = Modifier.testTag("secundario"))
                BotonDeIcono(Icons.Rounded.Inbox, "Pagó otro", onClick = { toques++ },
                    variante = VarianteDeBotonDeIcono.Secundario, habilitado = false, modifier = Modifier.testTag("apagado"))
            }
        }
        val nodo = composeRule.onNodeWithTag("secundario").fetchSemanticsNode()
        with(composeRule.density) {
            assertEquals(Medida.objetivoTactil, nodo.size.width.toDp(), "ancho")
            assertEquals(Medida.objetivoTactil, nodo.size.height.toDp(), "alto")
        }
        assertEquals(listOf("Subir comprobante"), nodo.config[SemanticsProperties.ContentDescription])
        composeRule.onNodeWithTag("secundario").performClick()
        composeRule.onNodeWithTag("apagado").performClick()
        composeRule.waitForIdle()
        assertEquals(1, toques, "el apagado no se toca")
    }

    /**
     * Vuelta de diseño 20: la `PildoraDeAccion` con algo antes del texto (el ícono de una categoría)
     * y una flecha al final mide lo mismo que sin ellos, y apagada no se toca.
     */
    @Test
    fun `la pildora de accion con inicio mide lo mismo y apagada no se toca`() {
        var toques = 0
        montar {
            Column {
                PildoraDeAccion("Mercado", onClick = { toques++ }, modifier = Modifier.testTag("sola"))
                PildoraDeAccion(
                    "Mercado", onClick = { toques++ }, modifier = Modifier.testTag("con-inicio"),
                    inicio = { Box(Modifier.size(Icono.m)) }, iconoFinal = Icons.Rounded.Inbox, habilitada = false,
                )
            }
        }
        assertEquals(altoDe("sola"), altoDe("con-inicio"))
        composeRule.onNodeWithTag("con-inicio").performClick()
        composeRule.waitForIdle()
        assertEquals(0, toques)
    }

    /** Vuelta de diseño 5: el tono del título de una fila sale del tema, nunca de un `Color` suelto. */
    @Test
    fun `el titulo de la fila toma su tono del tema`() {
        listOf(COLORES_CLAROS).forEach { c ->
            assertEquals(c.texto, colorDelTituloDeFila(TonoDeFila.Normal, c))
            assertEquals(c.sale, colorDelTituloDeFila(TonoDeFila.Alerta, c))
            assertEquals(c.textoMedio, colorDelTituloDeFila(TonoDeFila.Apagado, c))
        }
    }

    /** El botón de texto no se rellena al apagarse; los demás sí, con la capa 3 (vuelta 29). */
    @Test
    fun `el boton de texto apagado sigue sin fondo`() {
        listOf(COLORES_CLAROS).forEach { c ->
            assertEquals(Color.Transparent, coloresDelBotonApagado(VarianteDeBoton.Fantasma, c).first)
            assertEquals(c.textoApagado, coloresDelBotonApagado(VarianteDeBoton.Fantasma, c).second)
            assertEquals(c.capa3, coloresDelBotonApagado(VarianteDeBoton.Principal, c).first)
        }
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

    /**
     * Ola R1: la tarjeta de cuenta mide [Medida.altoDeTarjetaDeCuenta] con marca y sin ella, lleva la
     * marca invertida cuando el banco tiene una, se toca entera y su cifra se tapa con los saldos ocultos.
     */
    @Test
    fun `la tarjeta de cuenta mide lo mismo con marca y sin ella, y tapa su saldo`() {
        var tocada = 0
        montar(ocultos = true) {
            Column {
                TarjetaDeCuenta(
                    nombre = "Nu", tipo = "Cuenta", rotuloDelSaldo = "Saldo", saldo = 2_890_000,
                    marca = marcaDelBanco("Nu"), icono = Icons.Rounded.AccountBalance, onClick = { tocada++ },
                )
                TarjetaDeCuenta(
                    nombre = "Efectivo", tipo = "Efectivo", rotuloDelSaldo = "Saldo", saldo = 120_000,
                    marca = null, icono = Icons.Rounded.AccountBalance, onClick = {},
                )
            }
        }
        assertEquals(Medida.altoDeTarjetaDeCuenta, altoDe(TAG_TARJETA_DE_CUENTA, 0))
        assertEquals(Medida.altoDeTarjetaDeCuenta, altoDe(TAG_TARJETA_DE_CUENTA, 1))
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_MARCA_DE_LA_TARJETA, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(2, composeRule.onAllNodesWithText(PLATA_OCULTA, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onAllNodesWithTag(TAG_TARJETA_DE_CUENTA)[0].performClick()
        assertEquals(1, tocada)
    }

    /**
     * **Vuelta 29: la tarjeta de cuenta es blanca con marca y sin ella.** El color del banco va solo en
     * el avatar: el amarillo de Bancolombia de fondo se confundía con el de la marca de Movi.
     */
    @Test
    fun `la tarjeta de cuenta es blanca, con marca o sin ella`() {
        val nu = marcaDelBanco("Nu")!!
        assertEquals(COLORES_CLAROS, coloresDeLaTarjetaDeCuenta(nu, COLORES_CLAROS))
        assertEquals(COLORES_CLAROS.tarjeta, coloresDeLaTarjetaDeCuenta(nu, COLORES_CLAROS).tarjeta)
        assertEquals(COLORES_CLAROS.tarjeta, coloresDeLaTarjetaDeCuenta(null, COLORES_CLAROS).tarjeta)
    }

    /** Dos widgets lado a lado miden lo mismo aunque uno tenga más adentro, y cada uno se toca entero. */
    @Test
    fun `los widgets de un par miden lo mismo y se tocan enteros`() {
        var tocado = ""
        montar {
            ParDeWidgets(
                izquierdo = { m ->
                    Widget("Falta por pagar", onClick = { tocado = "falta" }, modifier = m) {
                        Cifra(4_312_000, VarianteDeCifra.Grande)
                        Text("Arriendo")
                        Text("Celular")
                    }
                },
                derecho = { m -> Widget("En qué se va", onClick = { tocado = "va" }, modifier = m) { BarraDeProgreso(0.6f) } },
            )
        }
        assertEquals(altoDe(TAG_WIDGET, 0), altoDe(TAG_WIDGET, 1))
        composeRule.onAllNodesWithTag(TAG_WIDGET)[1].performClick()
        assertEquals("va", tocado)
    }

    /**
     * Vuelta de diseño 7: a media pantalla, el detalle de la derecha no se come el título. El título
     * mide lo mismo con un detalle largo que sin detalle.
     */
    @Test
    @org.robolectric.annotation.Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun `el detalle de un widget no se come el titulo`() {
        montar {
            ParDeWidgets(
                izquierdo = { m -> Widget("En qué se va", onClick = {}, modifier = m, detalle = "vs. período pasado") { Text("a") } },
                derecho = { m -> Widget("En qué se va", onClick = {}, modifier = m) { Text("b") } },
            )
        }
        val titulos = composeRule.onAllNodesWithText("En qué se va", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(2, titulos.size)
        assertTrue(titulos[0].size.width > 0)
        assertEquals(titulos[1].size.width, titulos[0].size.width, "el título con detalle se ve entero, como el que no lo tiene")
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
    fun `la pildora de accion es un boton, mide lo de un filtro y dice la pregunta entera`() {
        var tocada = false
        montar { PildoraDeAccion("¿Cómo voy?", onClick = { tocada = true }, descripcion = "¿Cómo voy este período?") }
        val nodo = composeRule.onNodeWithTag(TAG_PILDORA_DE_ACCION)
        nodo.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        nodo.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        nodo.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("¿Cómo voy este período?")))
        assertTrue(altoDe(TAG_PILDORA_DE_ACCION) >= Medida.altoChico - 0.5.dp)
        nodo.performClick()
        assertTrue(tocada)
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
