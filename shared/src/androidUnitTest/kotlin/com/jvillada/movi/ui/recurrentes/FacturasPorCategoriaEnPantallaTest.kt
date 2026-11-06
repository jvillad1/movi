package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.GruposPlegadosDeFacturas
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.VistaDeFacturas
import com.jvillada.movi.data.VistaDeFacturasStore
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.facturasPorCategoria
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.ESTADO_DESPLEGADO
import com.jvillada.movi.ui.components.ds.ESTADO_PLEGADO
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO_PLEGABLE
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 120 · «Por fecha | Por categoría» en Pagos
 *
 * La segunda vista de «Tus facturas del período»: el selector recuerda la última elegida en el aparato, los
 * grupos se pliegan (lo pagado arranca plegado) y se recuerdan en la sesión, cada fila conserva sus acciones,
 * nada salta al cargar, y a 360 dp con la letra grande no se corta el encabezado de ningún grupo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h3200dp-xhdpi")
class FacturasPorCategoriaEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val almacen = mutableMapOf<String, String>()

    @Before
    fun preparar() {
        VistaDeFacturasStore.almacenDePrueba = almacen
    }

    private val arriendo = PagoDelPeriodo(
        ruleId = "rr_arriendo", nombre = "Arriendo", monto = 1_800_000, pagado = false, diasParaVencer = -2,
        vence = "2026-10-25", categoria = "Vivienda",
    )
    private val administracion = PagoDelPeriodo(
        ruleId = "rr_admin", nombre = "Administración del conjunto", monto = 450_000, pagado = true, diasParaVencer = -5,
        vence = "2026-10-22", periodoDelSello = "2026-10", eventId = "ev_admin", montoPagado = 450_000, categoria = "Vivienda",
    )
    private val luz = PagoDelPeriodo(
        ruleId = "rr_luz", nombre = "Luz", monto = 120_000, pagado = false, diasParaVencer = 9, vence = "2026-11-05", categoria = "Servicios",
    )
    private val internet = PagoDelPeriodo(
        ruleId = "rr_internet", nombre = "Internet", monto = 145_000, pagado = true, diasParaVencer = -1,
        vence = "2026-10-26", periodoDelSello = "2026-10", eventId = "ev_internet", montoPagado = 145_000, categoria = "Telecomunicaciones",
    )
    private val cuota = PagoDelPeriodo(
        ruleId = "${CREDIT_RULE_PREFIX}veh", nombre = "Cuota Vehículo 4083", monto = 980_000, pagado = false, diasParaVencer = 4,
        vence = "2026-10-31", categoria = "Transporte",
    )
    private val nu = PagoDelPeriodo(
        ruleId = "${CARD_RULE_PREFIX}nu", nombre = "Pago tarjeta Tarjeta Nu", monto = 540_000, pagado = false, diasParaVencer = 6,
        vence = "2026-11-02", montoEsSaldo = true,
    )
    private val salario = PagoDelPeriodo(
        ruleId = "rr_salario", nombre = "Salario", monto = 6_000_000, pagado = false, diasParaVencer = 20,
        vence = "2026-11-24", esIngreso = true, categoria = "Salario",
    )
    private val lista = listOf(arriendo, administracion, luz, internet, cuota, nu, salario)

    private val anotados = mutableListOf<String>()
    private val quitadas = mutableListOf<String>()
    private var cargando by mutableStateOf(false)
    private var checklist by mutableStateOf(emptyList<PagoDelPeriodo>())
    private var ocultos by mutableStateOf(false)
    private var periodo by mutableStateOf("2026-10")

    private fun montar(escala: Float = 1f, filas: List<PagoDelPeriodo> = lista) {
        checklist = filas
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                MoviTheme {
                    CompositionLocalProvider(com.jvillada.movi.ui.components.LocalSaldosOcultos provides ocultos) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SeccionChecklistDelPeriodo(
                            checklist = checklist,
                            claveDelPeriodo = periodo,
                            cargando = cargando,
                            pudoLeer = true,
                            marcando = emptySet(),
                            onConfirmar = { _, _ -> },
                            onNoFueEste = { _, _ -> },
                            onAnotarMovimiento = { anotados += it.ruleId },
                            onQuitarLaMarca = { quitadas += it.ruleId },
                            onReintentar = {},
                            onAbrir = {},
                            onVerMovimiento = {},
                            onCargarMinimo = {},
                        )
                    }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun grupos() = composeRule.onAllNodesWithTag(TAG_GRUPO_DE_FACTURAS, useUnmergedTree = true).fetchSemanticsNodes()

    private fun encabezado(nombre: String) =
        composeRule.onNode(hasTestTag(TAG_ENCABEZADO_PLEGABLE) and hasAnyDescendant(hasText(nombre)), useUnmergedTree = true)

    private fun tocar(nodo: androidx.compose.ui.test.SemanticsNodeInteraction) {
        nodo.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun pildora(rotulo: String) = composeRule.onNode(
        hasClickAction() and hasAnyDescendant(hasText(rotulo)) and hasAnyAncestor(hasTestTag(TAG_SELECTOR_DE_VISTA_DE_FACTURAS)),
        useUnmergedTree = true,
    )

    private fun elegir(rotulo: String) = tocar(pildora(rotulo))

    private fun selector() = composeRule.onNode(hasTestTag(TAG_SELECTOR_DE_VISTA_DE_FACTURAS), useUnmergedTree = true)

    @Test
    fun `por defecto es Por fecha, y Por categoria se recuerda en el aparato`() {
        montar()
        assertTrue(grupos().isEmpty(), "por defecto, la lista de siempre")
        assertEquals(VistaDeFacturas.POR_FECHA, VistaDeFacturasStore.vista)

        elegir(ROTULO_POR_CATEGORIA)

        assertEquals(5, grupos().size, "Vivienda, Créditos, Servicios, Telecomunicaciones y Tarjetas; el salario no")
        assertEquals("categoria", almacen["pagos_vista_de_facturas"], "guardada en el aparato")
        assertEquals(VistaDeFacturas.POR_CATEGORIA, VistaDeFacturasStore.leer())
        // Los ingresos siguen en su sección, fuera de los grupos.
        assertTrue(hay(TITULO_TUS_INGRESOS))
        assertTrue(
            composeRule.onAllNodes(hasText("Salario") and hasAnyAncestor(hasTestTag(TAG_GRUPO_DE_FACTURAS)), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )

        elegir(ROTULO_POR_FECHA)
        assertTrue(grupos().isEmpty())
        assertEquals("fecha", almacen["pagos_vista_de_facturas"])
    }

    @Test
    fun `con Por categoria guardada arranca asi desde el primer cuadro, y al cerrar sesion vuelve a Por fecha`() {
        almacen["pagos_vista_de_facturas"] = "categoria"
        VistaDeFacturasStore.almacenDePrueba = almacen
        cargando = true
        montar()
        // Mientras carga, el selector ya dice «Por categoría».
        assertEquals(true, pildora(ROTULO_POR_CATEGORIA).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected), "la elegida es «Por categoría»")
        assertEquals(false, pildora(ROTULO_POR_FECHA).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        cargando = false
        composeRule.waitForIdle()
        assertEquals(5, grupos().size)

        SessionManager.clear()
        assertEquals(VistaDeFacturas.POR_FECHA, VistaDeFacturasStore.vista)
        assertTrue(almacen.isEmpty(), "nada guardado para el siguiente")
    }

    @Test
    fun `lo pendiente arranca desplegado, lo pagado plegado, y tocar el encabezado lo pliega y lo recuerda`() {
        montar()
        elegir(ROTULO_POR_CATEGORIA)

        assertTrue(hay("Arriendo"), "Vivienda tiene algo pendiente: desplegado")
        assertFalse(hay("Internet"), "Telecomunicaciones está pagado: plegado, solo el resumen")
        assertEquals(ESTADO_PLEGADO, encabezado("Telecomunicaciones").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(ESTADO_DESPLEGADO, encabezado("Vivienda").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))

        tocar(encabezado("Vivienda"))
        assertFalse(hay("Arriendo"), "plegado")
        assertTrue(hay("Falta \$1.800.000"), "plegado queda el resumen")
        assertTrue(GruposPlegadosDeFacturas.plegado(periodo, "categoria:vivienda", porDefecto = false), "se recuerda en la sesión")

        tocar(encabezado("Telecomunicaciones"))
        assertTrue(hay("Internet"), "lo pagado también se despliega")

        tocar(encabezado("Vivienda"))
        assertTrue(hay("Arriendo"))
    }

    @Test
    fun `el encabezado dice el total y lo que falta, con las cifras de Falta por pagar`() {
        montar()
        elegir(ROTULO_POR_CATEGORIA)

        assertTrue(hay("Falta \$1.800.000"), "Vivienda")
        assertTrue(hay("\$2.250.000"), "el total de Vivienda: lo que falta y lo pagado")
        assertTrue(hay(GRUPO_PAGADO), "Telecomunicaciones, sin nada por pagar")
        assertTrue(hay(GRUPO_PENDIENTE_SIN_SUMAR), "Tarjetas: la Nu sin mínimo no suma")
        assertEquals(2_900_000L, faltaPorPagar(lista))
        assertEquals(faltaPorPagar(lista), facturasPorCategoria(lista).sumOf { it.falta }, "la suma de los grupos es la tarjeta amarilla")
        assertTrue(hay("\$2.900.000"), "la tarjeta amarilla sigue arriba, con la misma cifra")
    }

    @Test
    fun `el encabezado se anuncia como encabezado, con su frase y su estado`() {
        montar()
        elegir(ROTULO_POR_CATEGORIA)

        val nodo = composeRule.onNode(
            isHeading() and hasContentDescription("Vivienda, falta 1.800.000 pesos de 2.250.000 pesos"),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        assertEquals(ESTADO_DESPLEGADO, nodo.config.getOrNull(SemanticsProperties.StateDescription))
        composeRule.onNode(
            isHeading() and hasContentDescription("Telecomunicaciones, pagado, 145.000 pesos"),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `una fila dentro de un grupo conserva sus acciones`() {
        montar()
        elegir(ROTULO_POR_CATEGORIA)

        // Vuelta 136: las acciones de una fila viven en su hoja; tocar la fila dentro del grupo la abre.
        composeRule.abrirLaFactura("Arriendo")
        composeRule.waitForIdle()
        tocar(composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(ETIQUETA_ANOTAR)) and hasAnyAncestor(hasTestTag(TAG_HOJA_DE_LA_FACTURA)), useUnmergedTree = true).onFirst())
        assertEquals(listOf("rr_arriendo"), anotados, "«Anotar pago» de la hoja, el de siempre")

        composeRule.abrirLaFactura("Administración del conjunto")
        composeRule.waitForIdle()
        tocar(composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(ETIQUETA_QUITAR_LA_MARCA)) and hasAnyAncestor(hasTestTag(TAG_HOJA_DE_LA_FACTURA)), useUnmergedTree = true).onFirst())
        assertEquals(listOf("rr_admin"), quitadas, "hoja → «Quitar la marca» de la pagada")

        // La tarjeta sin mínimo dice en su hoja que no suma, con su botón.
        composeRule.abrirLaFactura("Pago tarjeta Tarjeta Nu")
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag(TAG_NO_SUMA) and hasAnyAncestor(hasTestTag(TAG_HOJA_DE_LA_FACTURA)), useUnmergedTree = true).assertExists()
    }


    /**
     * Revisión: el selector iba debajo de la tarjeta amarilla y bajaba todo su alto al llegar los datos (y
     * cargando con un período vacío aparecía para irse). Ahora es lo primero de la sección: su posición ABSOLUTA
     * no se mueve más de 8 dp, como el selector de segmentos en `PlanScreenTest`.
     */
    private fun noSaltaAlCargar(filas: List<PagoDelPeriodo>) {
        cargando = true
        montar(filas = filas)
        val antes = selector().getUnclippedBoundsInRoot().top.value

        cargando = false
        composeRule.waitForIdle()
        val despues = selector().getUnclippedBoundsInRoot().top.value
        assertTrue(abs(despues - antes) <= 8f, "el selector se corrió ${abs(despues - antes)} dp al llegar los datos; el máximo son 8")
    }

    @Test
    fun `el selector no salta al llegar los datos`() = noSaltaAlCargar(lista)

    @Test
    fun `con un periodo vacio el selector tampoco aparece para irse`() = noSaltaAlCargar(emptyList())

    @Test
    fun `con solo ingresos el selector se queda en su lugar`() = noSaltaAlCargar(listOf(salario))

    @Test
    fun `cambiar de vista no mueve el selector`() {
        montar()
        val porFecha = selector().getUnclippedBoundsInRoot()
        elegir(ROTULO_POR_CATEGORIA)
        val porCategoria = selector().getUnclippedBoundsInRoot()
        assertTrue(abs(porCategoria.top.value - porFecha.top.value) <= 1f, "cambiar de vista no mueve el selector")
        assertTrue(abs((porCategoria.bottom - porCategoria.top).value - (porFecha.bottom - porFecha.top).value) <= 1f)
    }

    @Test
    fun `un grupo con total en cero no dice 0 pesos`() {
        montar(filas = listOf(arriendo, nu))
        elegir(ROTULO_POR_CATEGORIA)
        val tarjetas = composeRule.onNode(
            hasTestTag(TAG_ENCABEZADO_PLEGABLE) and hasAnyDescendant(hasText(com.jvillada.movi.shared.model.GRUPO_DE_LAS_TARJETAS)),
            useUnmergedTree = true,
        )
        assertTrue(
            composeRule.onAllNodes(hasText("\$0") and hasAnyAncestor(hasTestTag(TAG_ENCABEZADO_PLEGABLE)), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
            "ningún encabezado dice «\$0»",
        )
        assertEquals("Tarjetas, pendiente, no suma en lo que falta", tarjetas.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single())
    }

    @Test
    fun `anotar el ultimo pago de un grupo abierto no lo pliega solo`() {
        montar()
        elegir(ROTULO_POR_CATEGORIA)
        assertTrue(hay("Luz"), "Servicios arranca desplegado (la luz falta)")

        // El dueño anota la luz: Servicios queda todo pagado, pero sigue abierto con la fila a la vista.
        checklist = conLaLuzPagada()
        composeRule.waitForIdle()
        assertTrue(hay("Luz"), "la fila sobre la que actuó no desaparece")
        assertEquals(ESTADO_DESPLEGADO, encabezado("Servicios").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        assertTrue(hay(GRUPO_PAGADO))
    }

    private fun conLaLuzPagada() = lista.map { if (it.ruleId == "rr_luz") it.copy(pagado = true, eventId = "ev_luz", montoPagado = 120_000) else it }

    private fun estadoDe(nombre: String) = encabezado(nombre).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    /** Segunda revisión (a): el defecto fijado vive en la sesión, no en la composición de la vista. */
    @Test
    fun `alternar la vista no cambia el plegado fijado`() {
        montar()
        elegir(ROTULO_POR_CATEGORIA)
        assertEquals(ESTADO_DESPLEGADO, estadoDe("Servicios"))

        elegir(ROTULO_POR_FECHA)
        checklist = conLaLuzPagada()
        composeRule.waitForIdle()
        elegir(ROTULO_POR_CATEGORIA)

        assertEquals(ESTADO_DESPLEGADO, estadoDe("Servicios"), "visto abierto, sigue abierto aunque ya esté pagado")
        assertTrue(hay("Luz"))
    }

    /** Segunda revisión (b): otro período es otra clave, con su propio defecto. */
    @Test
    fun `en otro periodo un grupo con algo pendiente arranca desplegado aunque en el anterior estuviera plegado`() {
        montar(filas = conLaLuzPagada())
        elegir(ROTULO_POR_CATEGORIA)
        assertEquals(ESTADO_PLEGADO, estadoDe("Servicios"), "en octubre, todo pagado: plegado")

        periodo = "2026-11"
        checklist = lista
        composeRule.waitForIdle()

        assertEquals(ESTADO_DESPLEGADO, estadoDe("Servicios"), "en noviembre la luz falta: desplegado")
        assertTrue(hay("Luz"))
    }

    /** Segunda revisión (c): cerrar sesión borra lo fijado, y lo que se ve vuelve a la regla de siempre. */
    @Test
    fun `olvidar la sesion borra el plegado fijado`() {
        montar()
        elegir(ROTULO_POR_CATEGORIA)
        checklist = conLaLuzPagada()
        composeRule.waitForIdle()
        assertEquals(ESTADO_DESPLEGADO, estadoDe("Servicios"), "fijado abierto")
        assertTrue(GruposPlegadosDeFacturas.cuantos > 0)

        GruposPlegadosDeFacturas.olvidar()
        composeRule.waitForIdle()

        assertEquals(ESTADO_PLEGADO, estadoDe("Servicios"), "sin lo fijado, lo pagado se pliega")
        // Y el logout de verdad lo vacía (`ElForkLlegaLimpioTest` lo vigila entre clases).
        GruposPlegadosDeFacturas.plegado(periodo, "categoria:otra", porDefecto = true)
        SessionManager.clear()
        assertEquals(0, GruposPlegadosDeFacturas.cuantos)
    }

    @Test
    fun `con los saldos ocultos el lector no dice cifras y el encabezado las tapa`() {
        ocultos = true
        montar()
        elegir(ROTULO_POR_CATEGORIA)
        composeRule.onNode(isHeading() and hasContentDescription("Vivienda, falta pagar, cifra oculta"), useUnmergedTree = true).assertExists()
        composeRule.onNode(isHeading() and hasContentDescription("Telecomunicaciones, pagado"), useUnmergedTree = true).assertExists()
        assertTrue(
            composeRule.onAllNodes(hasText("000", substring = true) and hasAnyAncestor(hasTestTag(TAG_ENCABEZADO_PLEGABLE)), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
            "ningún encabezado deja ver una cifra",
        )
    }

    @Test
    fun `un grupo sin icono propio lleva el generico, no el de su primera fila`() {
        val grupo = facturasPorCategoria(listOf(arriendo.copy(categoria = "", nombre = "Gimnasio"))).single()
        assertEquals(com.jvillada.movi.shared.model.GRUPO_SIN_CATEGORIA, nombreParaElIconoDelGrupo(grupo))
    }

    // ── La letra grande a 360 ───────────────────────────────────────────────

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Los textos dentro de [raiz] que se cortan con «…» o no les caben los renglones. */
    private fun cortados(raiz: SemanticsNode): List<String> {
        fun textos(n: SemanticsNode): List<SemanticsNode> =
            (if (n.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) listOf(n) else emptyList()) + n.children.flatMap(::textos)
        return textos(raiz).mapNotNull { nodo ->
            val r = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            val d = r.firstOrNull() ?: return@mapNotNull null
            when {
                (0 until d.lineCount).any { d.isLineEllipsized(it) } -> "«${texto(nodo)}» se corta con «…»"
                d.multiParagraph.didExceedMaxLines -> "«${texto(nodo)}» no le caben los renglones"
                else -> null
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 con la letra al 130 por ciento de verdad ningun encabezado ni el selector se cortan`() {
        montar(escala = 1.3f * 1.12f)
        elegir(ROTULO_POR_CATEGORIA)
        val nodos = composeRule.onAllNodesWithTag(TAG_ENCABEZADO_PLEGABLE, useUnmergedTree = true).fetchSemanticsNodes() +
            selector().fetchSemanticsNode()
        assertEquals(6, nodos.size)
        val fallas = nodos.flatMap(::cortados)
        assertTrue(fallas.isEmpty(), "a 360 dp con la letra ×1,456:\n" + fallas.joinToString("\n"))
        // Y las cifras de los encabezados se ven enteras.
        assertTrue(hay("Falta \$1.800.000"))
        assertTrue(hay("\$2.250.000"))
    }
}
