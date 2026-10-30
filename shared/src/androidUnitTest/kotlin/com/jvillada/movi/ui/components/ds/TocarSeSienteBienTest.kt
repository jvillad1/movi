package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.LoFresco
import com.jvillada.movi.data.actualizarAMano
import com.jvillada.movi.data.debeRecargarAlVolverElFoco
import com.jvillada.movi.data.ESPERA_ENTRE_RECARGAS_POR_FOCO_MS
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.AlPedirseUnaRecarga
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.MinNavRail
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.TAG_ACTUALIZAR_DEL_RAIL
import com.jvillada.movi.ui.components.TAG_PILDORA_DE_PESTANA
import com.jvillada.movi.ui.components.tagDePestana
import org.junit.After
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
 * # Vuelta 79 · que tocar se sienta bien
 *
 * La revisión visual 7 encontró que Movi se sentía barata al tocarla: un rectángulo gris al presionar
 * una acción, un rectángulo en las pestañas, el botón amarillo que se volvía mostaza. Estas pruebas leen
 * lo que se ve **sin píxeles**: la escala que `alPresionar` deja en la semántica, y si alguien pidió la
 * indicación (el ripple de Material es una `IndicationNodeFactory`: una espía en `LocalIndication` cuenta
 * cuántas veces se creó). También: el encabezado que se separa con el scroll, el ↻ del rail, la recarga
 * pedida a mano y la cifra que cuenta cuando la cambió el dueño.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class TocarSeSienteBienTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() = CambioDelDueno.limpiar()

    /** Anota si alguien pidió la indicación (el ripple): con `indication = null` nadie la pide. */
    private class IndicacionEspia : IndicationNodeFactory {
        var creadas = 0
        override fun create(interactionSource: InteractionSource): DelegatableNode {
            creadas++
            return object : Modifier.Node() {}
        }
        override fun equals(other: Any?): Boolean = other === this
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private val espia = IndicacionEspia()

    private fun montar(reducido: Boolean = false, contenido: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalIndication provides espia, LocalMovimientoReducido provides reducido) {
                    Box(Modifier.fillMaxSize()) { contenido() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun escalaDe(tag: String, i: Int = 0): Float =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)[i].fetchSemanticsNode().config[EscalaAlPresionarKey]

    private fun veloDe(tag: String, i: Int = 0): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)[i].fetchSemanticsNode().config.getOrNull(VeloAlPresionarKey) == true

    private fun presionar(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { down(center) }
        composeRule.waitForIdle()
    }

    private fun soltar(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { up() }
        composeRule.waitForIdle()
    }

    // ── El presionado ────────────────────────────────────────────────────────

    @Test
    fun `el boton principal se hunde al 0,97, sin ripple, y vuelve al soltar`() {
        montar { Boton("Guardar", onClick = {}) }
        assertEquals(1f, escalaDe(TAG_BOTON))
        presionar(TAG_BOTON)
        assertEquals(Movimiento.ESCALA_DEL_BOTON_AL_PRESIONAR, escalaDe(TAG_BOTON))
        assertFalse(veloDe(TAG_BOTON), "el amarillo no lleva velo de tinta")
        soltar(TAG_BOTON)
        assertEquals(1f, escalaDe(TAG_BOTON))
        assertEquals(0, espia.creadas, "nadie pidió el ripple")
    }

    @Test
    fun `la accion rapida se hunde al 0,96 sin pintar un rectangulo, y su circulo pasa a la capa 3`() {
        montar { AccionRapida("Gasto", Icons.Rounded.Edit, onClick = {}) }
        presionar(TAG_ACCION_RAPIDA)
        assertEquals(Movimiento.ESCALA_AL_PRESIONAR, escalaDe(TAG_ACCION_RAPIDA))
        assertEquals(0, espia.creadas, "nadie pidió el ripple")
        val c = COLORES_CLAROS
        assertEquals(c.capa3, fondoDelCirculoDeAccion(destacada = false, presionada = true, colores = c))
        assertEquals(c.tarjeta, fondoDelCirculoDeAccion(destacada = false, presionada = false, colores = c))
        assertEquals(c.marca, fondoDelCirculoDeAccion(destacada = true, presionada = true, colores = c), "la destacada no se ensucia")
    }

    @Test
    fun `cada toque de ds se hunde sin ripple`() {
        montar {
            Column {
                Fila("Mercado", onClick = {})
                PildoraDeFiltro("Gastos", seleccionada = false, onClick = {})
                BotonDeIcono(Icons.Rounded.Edit, "Editar", onClick = {})
                Superficie(onClick = {}, modifier = Modifier.testTag("superficie")) { Text("Una tarjeta") }
            }
        }
        for ((tag, escala) in listOf(
            TAG_FILA to Movimiento.ESCALA_DE_LA_FILA_AL_PRESIONAR,
            TAG_PILDORA_DE_FILTRO to Movimiento.ESCALA_AL_PRESIONAR,
            TAG_BOTON_DE_ICONO to Movimiento.ESCALA_AL_PRESIONAR,
            "superficie" to Movimiento.ESCALA_AL_PRESIONAR,
        )) {
            presionar(tag)
            assertEquals(escala, escalaDe(tag), tag)
            soltar(tag)
        }
        assertEquals(0, espia.creadas, "nadie pidió el ripple")
    }

    @Test
    fun `con menos movimiento no hay escala, y el toque se ve en la capa`() {
        montar(reducido = true) {
            Column {
                Fila("Mercado", onClick = {})
                Boton("Guardar", onClick = {})
            }
        }
        presionar(TAG_FILA)
        assertEquals(1f, escalaDe(TAG_FILA))
        assertTrue(veloDe(TAG_FILA), "presionada con menos movimiento, la fila lleva el velo")
        soltar(TAG_FILA)
        presionar(TAG_BOTON)
        assertEquals(1f, escalaDe(TAG_BOTON))
        assertEquals(COLORES_CLAROS.marcaPresionada, fondoDelBotonPresionado(VarianteDeBoton.Principal, COLORES_CLAROS))
    }

    @Test
    fun `el velo solo en hover o presionado con menos movimiento`() {
        assertTrue(seVeElVelo(presionado = false, encima = true, reducido = false))
        assertFalse(seVeElVelo(presionado = true, encima = false, reducido = false))
        assertTrue(seVeElVelo(presionado = true, encima = false, reducido = true))
        assertEquals(1f, escalaDelToque(presionado = true, reducido = true, escala = 0.96f))
        assertEquals(0.96f, escalaDelToque(presionado = true, reducido = false, escala = 0.96f))
    }

    // ── La barra de pestañas ─────────────────────────────────────────────────

    @Test
    fun `la barra no pinta nada fuera de la pildora`() {
        montar { Column { Box(Modifier.weight(1f)); MinBottomNav(active = NavTab.HOY, onTabSelected = {}) } }
        val pagos = tagDePestana(NavTab.PLAN)
        presionar(pagos)
        // La celda entera es el toque, pero no se hunde ni lleva velo: lo que se ve va en la píldora.
        assertEquals(1f, escalaDe(pagos))
        assertFalse(veloDe(pagos))
        val pildoraDePagos = 2 // Inicio · Actividad · Pagos · Tú
        assertEquals(Movimiento.ESCALA_AL_PRESIONAR, escalaDe(TAG_PILDORA_DE_PESTANA, pildoraDePagos))
        assertEquals(0, espia.creadas, "nadie pidió el ripple de la celda")
    }

    // ── El ↻ del rail ────────────────────────────────────────────────────────

    @Test
    fun `el rail ancho lleva el actualizar y el compacto no`() {
        var actualizaciones = 0
        var compacto by mutableStateOf(false)
        montar { MinNavRail(active = NavTab.HOY, onTabSelected = {}, compacto = compacto, onActualizar = { actualizaciones++ }) }
        composeRule.onNodeWithTag(TAG_ACTUALIZAR_DEL_RAIL, useUnmergedTree = true).performClick()
        assertEquals(1, actualizaciones)
        compacto = true
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ACTUALIZAR_DEL_RAIL, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `actualizar a mano pide que la pantalla a la vista recargue, una vez por pedido`() {
        var recargas = 0
        montar { AlPedirseUnaRecarga { recargas++ } }
        assertEquals(0, recargas, "al entrar no recarga: la pantalla ya carga sola")
        val antes = LoFresco.vueltas
        composeRule.runOnIdle { actualizarAMano() }
        composeRule.waitForIdle()
        assertEquals(1, recargas)
        assertEquals(antes + 1, LoFresco.vueltas, "y olvidó lo fresco")
        composeRule.runOnIdle { actualizarAMano() }
        composeRule.waitForIdle()
        assertEquals(2, recargas)
    }

    @Test
    fun `la vuelta del foco recarga como mucho cada cinco segundos`() {
        assertTrue(debeRecargarAlVolverElFoco(ultima = null, ahora = 1_000))
        assertFalse(debeRecargarAlVolverElFoco(ultima = 1_000, ahora = 1_000 + ESPERA_ENTRE_RECARGAS_POR_FOCO_MS - 1))
        assertTrue(debeRecargarAlVolverElFoco(ultima = 1_000, ahora = 1_000 + ESPERA_ENTRE_RECARGAS_POR_FOCO_MS))
    }

    // ── El encabezado que se separa ─────────────────────────────────────────

    @Test
    fun `el encabezado lleva el hilo al bajar y lo pierde al volver arriba`() {
        montar {
            val lista = rememberLazyListState()
            Column {
                Encabezado("Pagos", desplazado = desplazadoDe(lista))
                LazyColumn(state = lista, modifier = Modifier.weight(1f).testTag("lista")) {
                    items(60) { i -> Text("Fila $i", modifier = Modifier.height(56.dp)) }
                }
            }
        }
        fun desplazado() = composeRule.onNodeWithTag(TAG_ENCABEZADO, useUnmergedTree = true).fetchSemanticsNode()
            .config[EncabezadoDesplazadoKey]
        assertFalse(desplazado(), "arriba de todo, sin hilo")
        composeRule.onNodeWithTag("lista").performScrollToIndex(20)
        composeRule.waitForIdle()
        assertTrue(desplazado(), "con la lista desplazada, el hilo")
        composeRule.onNodeWithTag("lista").performScrollToIndex(0)
        composeRule.waitForIdle()
        assertFalse(desplazado())
        val c = COLORES_CLAROS
        assertEquals(c.fondo, fondoDelEncabezado(desplazado = true, arriba = c.marca, colores = c), "la banda se vuelve crema")
        assertEquals(c.marca, fondoDelEncabezado(desplazado = false, arriba = c.marca, colores = c))
    }

    // ── La cifra que cuenta ─────────────────────────────────────────────────

    private var valor by mutableLongStateOf(1_000_000L)

    private fun montarLaCifra(reducido: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        montar(reducido) {
            val c = rememberCifraQueCuenta(valor)
            Text(c.toString(), modifier = Modifier.testTag("cifra"))
        }
        composeRule.mainClock.advanceTimeBy(32)
    }

    /** Escribe el valor en un snapshot que se aplica: sin eso, con el reloj quieto nadie recompone. */
    private fun cambiarA(nuevo: Long) {
        Snapshot.withMutableSnapshot { valor = nuevo }
    }

    private fun cifra(): Long = composeRule.onNodeWithTag("cifra").fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }.toLong()

    @Test
    fun `cuando el duenno anota, la cifra cuenta hasta la nueva, y al deshacer cuenta hacia atras`() {
        montarLaCifra()
        CambioDelDueno.marcar()
        cambiarA(900_000L)
        composeRule.mainClock.advanceTimeBy(140)
        val enMedio = cifra()
        assertTrue(enMedio in 900_001L until 1_000_000L, "a mitad de la cuenta: $enMedio")
        composeRule.mainClock.advanceTimeBy(DURACION_DE_LA_CIFRA_QUE_CUENTA_MS.toLong() + 64)
        assertEquals(900_000L, cifra())

        // Deshacer: de vuelta a la de antes, contando hacia atrás.
        CambioDelDueno.marcar()
        cambiarA(1_000_000L)
        composeRule.mainClock.advanceTimeBy(140)
        val deVuelta = cifra()
        assertTrue(deVuelta in 900_001L until 1_000_000L, "a mitad de la vuelta: $deVuelta")
        composeRule.mainClock.advanceTimeBy(DURACION_DE_LA_CIFRA_QUE_CUENTA_MS.toLong() + 64)
        assertEquals(1_000_000L, cifra())
    }

    @Test
    fun `si la cambio otra cosa, o con menos movimiento, la cifra cambia en el acto`() {
        montarLaCifra()
        cambiarA(900_000L)
        composeRule.mainClock.advanceTimeBy(48)
        assertEquals(900_000L, cifra(), "sin cambio del dueño no cuenta")
    }

    @Test
    fun `con menos movimiento la cifra no cuenta`() {
        montarLaCifra(reducido = true)
        CambioDelDueno.marcar()
        cambiarA(900_000L)
        composeRule.mainClock.advanceTimeBy(48)
        assertEquals(900_000L, cifra())
    }

    @Test
    fun `la marca del duenno se toma una sola vez y vence`() {
        CambioDelDueno.marcar(ahora = 1_000)
        assertTrue(CambioDelDueno.pendiente(ahora = 1_000 + VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS))
        assertFalse(CambioDelDueno.pendiente(ahora = 1_001 + VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS))
        assertTrue(CambioDelDueno.tomar(ahora = 2_000))
        assertFalse(CambioDelDueno.tomar(ahora = 2_000), "ya se tomó")
    }
}
