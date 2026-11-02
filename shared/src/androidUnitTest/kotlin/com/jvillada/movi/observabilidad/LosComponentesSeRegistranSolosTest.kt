package com.jvillada.movi.observabilidad

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.shared.model.EventoDeUsoEnviado
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.ds.Accion
import com.jvillada.movi.ui.components.ds.AccionDeEncabezado
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.Casilla
import com.jvillada.movi.ui.components.ds.CuadriculaDeAcciones
import com.jvillada.movi.ui.components.ds.Encabezado
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.Interruptor
import com.jvillada.movi.ui.components.ds.PildoraDeAccion
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.SelectorDeDiaDelMes
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TAG_ACCION_DEL_ENCABEZADO
import com.jvillada.movi.ui.components.ds.TAG_BOTON_DE_ICONO
import com.jvillada.movi.ui.components.ds.TAG_CASILLA
import com.jvillada.movi.ui.components.ds.TAG_INTERRUPTOR
import com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_CUENTA
import com.jvillada.movi.ui.components.ds.TarjetaDeCuenta
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.ui.components.ds.renglonTocable
import com.jvillada.movi.ui.components.ds.tagDelDiaDelMes
import com.jvillada.movi.ui.components.tagDePestana
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 104 · cada componente de `ds/` tocado se anota solo
 *
 * Se monta cada componente tocable como lo monta una pantalla —sin hacer nada por el uso— y se toca:
 * queda un evento ACCION `toque:<id>` con el id esperado, la ruta de ese momento y sus datos. Un texto
 * dinámico no llega al nombre, y la hoja anota que se abrió y cómo se cerró.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class LosComponentesSeRegistranSolosTest {

    @get:Rule val composeRule = createComposeRule()

    private val ruta = "Dashboard>PorRevisar"

    @Before
    fun preparar() {
        Uso.almacenDePrueba = mutableMapOf()
        Uso.releerElInterruptor()
        Uso.ponerLaPila(listOf("Dashboard", "PorRevisar"))
    }

    private fun toques(): List<EventoDeUsoEnviado> = Uso.pendientes.filter { it.nombre.startsWith("toque:") }

    private fun ultimo(): EventoDeUsoEnviado = toques().last()

    private fun montar(contenido: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { MoviTheme { Column(Modifier.verticalScroll(rememberScrollState())) { contenido() } } }
    }

    private fun tocarYVer(tag: String, nombre: String, vararg datos: Pair<String, String>) {
        val antes = toques().size
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(antes + 1, toques().size, "tocar «$tag» anota un solo toque")
        val e = ultimo()
        assertEquals(nombre, e.nombre)
        assertEquals(ruta, e.ruta, "con la ruta de ese momento")
        datos.forEach { (k, v) -> assertEquals(v, e.propiedades[k], "$nombre lleva $k") }
        assertTrue(problemaSinNada(e), "el evento pasa la lista cerrada")
    }

    private fun problemaSinNada(e: EventoDeUsoEnviado) =
        com.jvillada.movi.shared.model.problemaDelEvento(e.nombre, e.propiedades, e.ruta) == null

    @Test
    fun losBotonesYLasFilasSeAnotanConSuRotuloOSuTag() {
        montar {
            Boton("Guardar", onClick = {}, modifier = Modifier.testTag("t-boton"))
            Boton("Cancelar", onClick = {}, modifier = Modifier.testTag(com.jvillada.movi.ui.components.ds.TAG_PAR_DE_BOTONES))
            Boton("Pagarle a Caro \$27.500", onClick = {}, modifier = Modifier.testTag("t-dinamico"))
            BotonDeIcono(Icons.Rounded.Search, "Buscar", onClick = {}, modifier = Modifier.testTag("t-icono"))
            Fila(titulo = "Nombre", onClick = {}, modifier = Modifier.testTag("t-fila"))
            Fila(titulo = "Rappi · Las Doce", onClick = {}, entidadDeUso = "ev_9c1d2e3f4a5b6c7d", modifier = Modifier.testTag("t-fila-mov"))
            PildoraDeFiltro("Hoy", seleccionada = false, onClick = {}, modifier = Modifier.testTag("t-filtro"))
            PildoraDeAccion("Copiar", onClick = {}, modifier = Modifier.testTag("t-pildora"))
            Seccion(titulo = "Movi propone hoy", accion = "Ver todo", onAccion = {})
            Superficie(onClick = {}, onClickLabel = "Editar", modifier = Modifier.testTag("t-superficie")) { Text("adentro") }
            Row(Modifier.testTag("t-renglon").renglonTocable(onClickLabel = "Deshacer") {}) { Text("renglón") }
        }
        tocarYVer("t-boton", "toque:guardar", "componente" to "Boton")
        tocarYVer(com.jvillada.movi.ui.components.ds.TAG_PAR_DE_BOTONES, "toque:ds-par-de-botones")
        tocarYVer("t-dinamico", "toque:boton")
        tocarYVer("t-icono", "toque:buscar", "componente" to "BotonDeIcono")
        tocarYVer("t-fila", "toque:nombre", "componente" to "Fila")
        tocarYVer("t-fila-mov", "toque:fila", "movimiento_id" to "ev_9c1d2e3f4a5b6c7d")
        tocarYVer("t-filtro", "toque:hoy", "componente" to "PildoraDeFiltro")
        tocarYVer("t-pildora", "toque:copiar")
        composeRule.onNodeWithText("Ver todo").performClick()
        assertEquals("toque:ver-todo", ultimo().nombre)
        tocarYVer("t-superficie", "toque:editar", "componente" to "Superficie")
        tocarYVer("t-renglon", "toque:deshacer")
        // Ningún texto dinámico quedó en un nombre.
        assertTrue(toques().none { "caro" in it.nombre || "rappi" in it.nombre || "27" in it.nombre })
    }

    @Test
    fun lasPropuestasLaCuadriculaYLoQueSePrende() {
        var encendido by mutableStateOf(false)
        var marcada by mutableStateOf(false)
        montar {
            TarjetaDePropuesta(
                icono = Icons.Rounded.Add,
                titulo = "Llegaron 3 avisos del banco",
                accion = "Confirmar",
                onAccion = {},
                accionSecundaria = "Ver todo",
                onAccionSecundaria = {},
                accionTerciaria = "Ignorar",
                onAccionTerciaria = {},
                tipoDeUso = "PAGO",
            )
            CuadriculaDeAcciones(listOf(Accion("Gasto", Icons.Rounded.Add, {}), Accion("Ingreso", Icons.Rounded.Add, {})))
            Interruptor(encendido = encendido, onCambio = { encendido = it }, descripcion = "Mandar datos de uso para mejorar Movi")
            Casilla(marcada = marcada, onCambiar = { marcada = it }, modifier = Modifier.testTag("t-casilla"))
            SelectorDeDiaDelMes(elegido = null, onElegir = {})
        }
        composeRule.onNodeWithText("Confirmar").performClick()
        assertEquals("toque:propuesta:pago", ultimo().nombre)
        assertEquals("PRINCIPAL", ultimo().propiedades["boton"])
        assertEquals("PAGO", ultimo().propiedades["propuesta"])
        composeRule.onNodeWithText("Ver todo").performClick()
        assertEquals("SECUNDARIA", ultimo().propiedades["boton"])
        composeRule.onNodeWithText("Ignorar").performClick()
        assertEquals("TERCERA", ultimo().propiedades["boton"])
        composeRule.onNodeWithText("Gasto").performClick()
        assertEquals("toque:gasto", ultimo().nombre)
        assertEquals("Cuadricula", ultimo().propiedades["componente"])
        tocarYVer(TAG_INTERRUPTOR, "toque:mandar-datos-de-uso-para-mejorar", "encendido" to "SI")
        tocarYVer(TAG_INTERRUPTOR, "toque:mandar-datos-de-uso-para-mejorar", "encendido" to "NO")
        composeRule.onNodeWithTag("t-casilla", useUnmergedTree = true).performClick()
        assertEquals("toque:casilla", ultimo().nombre)
        assertEquals("SI", ultimo().propiedades["encendido"])
        tocarYVer(tagDelDiaDelMes(15), "toque:dia-del-mes:15")
    }

    @Test
    fun laFlechaLaBarraYElEncabezado() {
        montar {
            Encabezado("Detalle", onVolver = {}, accion = AccionDeEncabezado("Nueva cuenta", Icons.Rounded.Add, {}))
            MinBottomNav(active = NavTab.HOY, onTabSelected = {})
        }
        composeRule.onAllNodesWithTag(TAG_BOTON_DE_ICONO, useUnmergedTree = true)[0].performClick()
        assertEquals("toque:atras", ultimo().nombre)
        assertEquals("FLECHA", ultimo().propiedades["como"])
        composeRule.onNodeWithTag(TAG_ACCION_DEL_ENCABEZADO, useUnmergedTree = true).performClick()
        assertEquals("toque:nueva-cuenta", ultimo().nombre)
        tocarYVer(tagDePestana(NavTab.PLAN), "toque:pestana:plan")
    }

    @Test
    fun unaTarjetaDinamicaLlevaSuEntidadYNoSuTexto() {
        montar {
            CompositionLocalProvider(LocalEntidadDeUso provides "sms_rt_9c1d2e3f4a5b6c7d") {
                Boton("Confirmar", onClick = {}, modifier = Modifier.testTag("t-confirmar"))
                Fila(titulo = "Guardar", onClick = {}, modifier = Modifier.testTag("t-fila-aviso"))
            }
            TarjetaDeCuenta(nombre = "Nu", tipo = "Ahorros", rotuloDelSaldo = "Saldo", saldo = 27_500, marca = null, icono = Icons.Rounded.Add, onClick = {})
        }
        tocarYVer("t-confirmar", "toque:confirmar", "aviso_id" to "sms_rt_9c1d2e3f4a5b6c7d")
        // «Guardar» es un rótulo de la app, pero adentro de un aviso la fila es dinámica: no se usa su título.
        tocarYVer("t-fila-aviso", "toque:fila", "aviso_id" to "sms_rt_9c1d2e3f4a5b6c7d")
        tocarYVer(TAG_TARJETA_DE_CUENTA, "toque:tarjeta-de-cuenta")
        assertFalse(ultimo().nombre.contains("nu"))
    }

    @Test
    fun laHojaAnotaQueSeAbrioYComoSeCerro() {
        var abierta by mutableStateOf(true)
        // Sin el scroll de `montar`: la hoja se mide contra la pantalla entera. Con menos movimiento la
        // hoja ya está en su lugar al abrir (sin la entrada animada, la X está donde se ve).
        composeRule.setContent {
            MoviTheme {
            CompositionLocalProvider(com.jvillada.movi.theme.LocalMovimientoReducido provides true) {
            if (abierta) {
                MarcoDeHoja(onDismiss = { abierta = false }) {
                    EncabezadoDeHoja("Nueva cuenta")
                    Text("contenido")
                }
            }
            }
            }
        }
        composeRule.waitForIdle()
        val abrir = Uso.pendientes.last { it.nombre == "HOJA_ABIERTA" }
        assertEquals("nueva-cuenta", abrir.propiedades["hoja"])
        // Como `AgregarNoPierdeNadaTest`: la acción de la X (en Robolectric el toque cae en el velo).
        composeRule.onNodeWithContentDescription("Cerrar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertFalse(abierta)
        val cerrar = Uso.pendientes.last { it.nombre == "HOJA_CERRADA" }
        assertEquals("X", cerrar.propiedades["como"])
        assertEquals("nueva-cuenta", cerrar.propiedades["hoja"])
        // Otra vez, y ahora tocando afuera: el velo.
        abierta = true
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(20f, 20f)) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertFalse(abierta)
        assertEquals("VELO", Uso.pendientes.last { it.nombre == "HOJA_CERRADA" }.propiedades["como"])
        assertEquals(2, Uso.pendientes.count { it.nombre == "HOJA_ABIERTA" })
    }

    @Test
    fun unCampoAnotaQueEmpezoYTerminoNuncaLoEscrito() {
        montar {
            var texto by remember { mutableStateOf("") }
            val otro = remember { FocusRequester() }
            Campo(valor = texto, alCambiar = { texto = it }, rotulo = "Nombre", tag = "t-campo")
            Campo(valor = "", alCambiar = {}, rotulo = "Monto", tag = "t-otro", foco = otro)
            Boton("Guardar", onClick = { otro.requestFocus() }, modifier = Modifier.testTag("t-mover-el-foco"))
        }
        composeRule.onNodeWithTag("t-campo").performTextInput("Mercado de Caro 27500")
        composeRule.onNodeWithTag("t-campo").performTextInput(" más")
        assertEquals(1, Uso.pendientes.count { it.nombre == "CAMPO_EMPEZADO" }, "una vez, con la primera tecla")
        composeRule.onNodeWithTag("t-mover-el-foco", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        val terminado = Uso.pendientes.last { it.nombre == "CAMPO_TERMINADO" }
        assertEquals("nombre", terminado.propiedades["campo"])
        // Nada de lo escrito en ningún evento.
        Uso.pendientes.forEach { e ->
            val todo = e.nombre + e.propiedades.values.joinToString() + e.ruta
            assertFalse("ercado" in todo || "Caro" in todo || "27500" in todo, "lo escrito llegó a ${e.nombre}")
        }
    }
}
