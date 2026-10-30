package com.jvillada.movi.ui.transactions

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.requestFocus
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.VoidEvent
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.LocalRelevoDeScroll
import com.jvillada.movi.ui.components.RelevoDeScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.geometry.Offset
import com.jvillada.movi.ui.components.TAG_PANEL_DE_HOJA
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Movimientos en lista + detalle (Ola W4), montada y medida
 *
 * Dentro de la cáscara de verdad ([EsqueletoDeLaCascara]: el rail de 216 dp en escritorio y el tope
 * de 1.440 son parte de la cuenta), con la clase de ancho que le toca a cada ventana
 * ([ConClaseDeAncho]) y la letra ×1,12 de la app. Se mide con `boundsInRoot` en `mdpi` (un px = un
 * dp), nunca con capturas: `captureToImage` se cuelga en Robolectric. `sdk = [34]` y `NATIVE`: el
 * texto se mide con el motor real.
 *
 * Todas las búsquedas con `useUnmergedTree = true`: el `clickable` de cada fila fusiona sus textos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MovimientosListaYDetalleEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val ahora = Clock.System.now().toEpochMilliseconds()

    private fun gasto(id: String, descripcion: String, monto: Long, categoria: String) = FinancialEvent(
        id = id, accountId = banco.id, type = TransactionType.EXPENSE, amount = monto, category = categoria,
        description = descripcion, timestamp = ahora, reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    /** Lo que el «server» tiene; las escrituras de la prueba lo cambian, como el de verdad. */
    private var eventos = listOf(
        gasto("e1", "Señor Gol", 46_489L, "Comida"),
        gasto("e2", "Las Doce", 23_000L, "Comida"),
        gasto("e3", "Supermercado Central", 120_000L, "Mercado"),
    )

    private inner class ConMovimientos : RepositorioDePruebaDeMovimientos() {
        val categoriasCambiadas = mutableListOf<Pair<String, String>>()
        val anulados = mutableListOf<String>()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado", periodCutoffDay = 1)
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun getEventsByDay(): List<EventDay> =
            listOf(EventDay(HOY, -eventos.sumOf { it.amount }, eventos))
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), 0)
        override suspend fun updateEventCategory(id: String, category: String): FinancialEvent {
            categoriasCambiadas += id to category
            eventos = eventos.map { if (it.id == id) it.copy(category = category) else it }
            return eventos.first { it.id == id }
        }
        /** Tu parte de lo que pagaron otros (R3); por defecto, nada. */
        var tuParte: List<FinancialEvent> = emptyList()
        override suspend fun getTuParteDeOtros(): List<FinancialEvent> = tuParte
        /** Los «parecidos» que el server devolvería por movimiento; por defecto, ninguno. */
        var parecidos: Map<String, List<FinancialEvent>> = emptyMap()
        override suspend fun getParecidos(id: String): List<FinancialEvent> = parecidos[id].orEmpty()
        override suspend fun updateEventTimestamp(id: String, timestamp: Long): FinancialEvent {
            eventos = eventos.map { if (it.id == id) it.copy(timestamp = timestamp) else it }
            return eventos.first { it.id == id }
        }
        override suspend fun voidEvent(id: String, reason: String?): VoidEvent {
            anulados += id
            eventos = eventos.filterNot { it.id == id }
            return VoidEvent(id = "v-$id", originalEventId = id, reason = reason, timestamp = ahora)
        }
    }

    private fun montar(): ConMovimientos = montar2("Señor Gol")

    /** A dónde pidió ir la pantalla. */
    private val navegaciones = mutableListOf<Screen>()

    /** El ancho de la ventana, para poder achicarla en medio de una prueba. `null` = la del qualifier. */
    private val anchoDeLaVentana = mutableStateOf<Int?>(null)

    private fun montar2(primera: String, antes: (ConMovimientos) -> Unit = {}): ConMovimientos {
        DiasPlegadosStore.clear()
        val repo = ConMovimientos()
        antes(repo)
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            // El relevo de los márgenes puesto como en App.kt: la lista se registra en él.
            val relevo = remember { RelevoDeScroll() }
            val ancho = anchoDeLaVentana.value
            Box(if (ancho == null) Modifier.fillMaxSize() else Modifier.fillMaxHeight().requiredWidth(ancho.dp)) {
            ConClaseDeAncho {
                CompositionLocalProvider(LocalRelevoDeScroll provides relevo) {
                    EsqueletoDeLaCascara(
                        pantalla = Screen.Transactions(),
                        activeTab = NavTab.MOVIMIENTOS,
                        conNavegacion = true,
                        onTabSelected = {},
                        relevoDeScroll = relevo,
                    ) {
                        TransactionsScreen(onNavigate = { navegaciones += it })
                    }
                }
            }
            }
        }
        esperar { filaDe(primera).fetchSemanticsNodes().isNotEmpty() }
        return repo
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun esperar(condicion: () -> Boolean) = composeRule.waitUntil(timeoutMillis = 5_000, condition = condicion)

    private fun hayTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun limites(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private val enLaLista = hasAnyAncestor(hasTestTag(TAG_LISTA_DE_MOVIMIENTOS_AL_LADO))
    private val enElPanel = hasAnyAncestor(hasTestTag(TAG_PANEL_DEL_MOVIMIENTO))

    /** La fila de la lista con [descripcion] (con panel o sin él). */
    private fun filaDe(descripcion: String) = composeRule.onAllNodes(
        hasTestTag(TAG_FILA_DE_MOVIMIENTO_SUELTO) and hasAnyDescendant(hasText(descripcion)),
        useUnmergedTree = true,
    )

    private fun fila(descripcion: String): SemanticsNodeInteraction = filaDe(descripcion).onFirst()

    private fun hayEnElPanel(texto: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodes(hasText(texto, substring = substring) and enElPanel, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun hayEnLaLista(texto: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodes(hasText(texto, substring = substring) and enLaLista, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun elegir(descripcion: String) {
        fila(descripcion).performClick()
        esperar { hayEnElPanel(descripcion) }
    }

    /** Ola 2b: las secciones del detalle se abren desde su acción (ver [AccionDelMovimiento]). */
    private fun tocarAccion(accion: AccionDelMovimiento) {
        composeRule.onNode(hasTestTag(tagDeAccionDelMovimiento(accion)) and enElPanel, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    // ── Escritorio ──────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 la lista y el panel se ven a la vez, el panel vacio resume el periodo`() {
        montar()

        val lista = limites(TAG_LISTA_DE_MOVIMIENTOS_AL_LADO)
        val panel = limites(TAG_PANEL_DEL_MOVIMIENTO)
        // Vuelta 81: la lista es la protagonista, el 60 % de 1280 − 216 del rail − 1 del divisor.
        assertEquals(637.8f, lista.width, 0.5f)
        assertEquals(425.2f, panel.width, 0.5f, "el desglose, el 40 %")
        assertTrue(lista.width > panel.width, "la lista, más ancha que el desglose")
        assertTrue(panel.left >= lista.right, "$lista / $panel")
        // La lista es la de siempre (desde la vuelta de diseño 14, el día sin el rótulo «Flujo del día»).
        assertTrue(hayEnLaLista("Señor Gol"))
        assertTrue(!hayEnLaLista("Flujo del día"))
        // Nada elegido: el resumen del período (vuelta de diseño 9), y ninguna hoja abierta.
        assertTrue(hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL))
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA), "no se abrió ninguna hoja")
    }

    // ── Vuelta de diseño 9: el panel sin nada elegido resume el período ────────────

    private val enElResumen = hasAnyAncestor(hasTestTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL))

    private fun hayEnElResumen(texto: String): Boolean =
        composeRule.onAllNodes(hasText(texto) and enElResumen, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `sin nada elegido el panel dice lo que entro, lo que salio, el neto y el gasto por categoria`() {
        montar()
        esperar { hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL) }

        assertTrue(hayEnElResumen("Entró"))
        assertTrue(hayEnElResumen("Salió"))
        assertTrue(hayEnElResumen("Neto"))
        // 46.489 + 23.000 + 120.000: lo que salió, con la Cifra del sistema; el neto, en negativo.
        assertTrue(hayEnElResumen("\$189.489"), "lo que salió")
        assertTrue(hayEnElResumen("−\$189.489"), "el neto")
        // Una fila por categoría, de mayor a menor, y el enlace a «En qué se va».
        val categorias = composeRule.onAllNodes(hasTestTag(TAG_CATEGORIA_DEL_RESUMEN), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(2, categorias.size)
        assertTrue(categorias[0].boundsInRoot.top < categorias[1].boundsInRoot.top)
        assertTrue(hayEnElResumen("Mercado"))
        assertTrue(hayEnElResumen("Comida"))
        assertTrue(hayEnElResumen(VER_EN_QUE_SE_VA))
        // Todo dentro del panel: nada se sale a la lista.
        val panel = limites(TAG_PANEL_DEL_MOVIMIENTO)
        val resumen = limites(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL)
        assertTrue(resumen.left >= panel.left - 0.5f && resumen.right <= panel.right + 0.5f, "$panel / $resumen")
    }

    /**
     * Revisión de la vuelta 9: el resumen cuenta tu parte de lo que pagó otro, como «En qué se va» y
     * el widget del Inicio. Caro pagó un mercado y tu parte es $120.000: Mercado dice $240.000 (los
     * $120.000 del supermercado más tu parte) y Salió $309.489.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `el resumen cuenta tu parte de lo que pago otro, como En que se va`() {
        montar2("Señor Gol") { repo ->
            repo.tuParte = listOf(
                FinancialEvent(
                    id = "espacio:g1", accountId = "espacio:casa", type = TransactionType.EXPENSE, amount = 120_000L,
                    category = "Mercado", description = "Mercado del sábado", timestamp = ahora,
                    reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true, espacioId = "casa",
                ),
            )
        }
        esperar { hayEnElResumen("\$309.489") }
        assertTrue(
            composeRule.onAllNodes(
                hasTestTag(TAG_CATEGORIA_DEL_RESUMEN) and hasAnyDescendant(hasText("Mercado")) and
                    hasAnyDescendant(hasText("\$240.000")),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty(),
            "Mercado con tu parte del de Caro",
        )
        // Vuelta 66: toda cifra de Actividad sale de las filas — tu parte de lo que pagó otro es una
        // fila en su día (no es un movimiento de tus cuentas: la fila abre el gasto del grupo).
        assertTrue(filaDe("Mercado del sábado").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `tocar una categoria del resumen filtra la lista con su chip`() {
        montar()
        esperar { hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL) }

        composeRule.onNode(
            hasTestTag(TAG_CATEGORIA_DEL_RESUMEN) and hasAnyDescendant(hasText("Mercado")),
            useUnmergedTree = true,
        ).performClick()

        esperar { filaDe("Señor Gol").fetchSemanticsNodes().isEmpty() }
        assertTrue(filaDe("Supermercado Central").fetchSemanticsNodes().isNotEmpty())
        assertTrue(hayTag(TAG_CHIP_DEL_FILTRO), "el filtro se ve como un chip con su ×")
        // El panel sigue en el resumen: no se eligió ningún movimiento.
        assertTrue(hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL))
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA))
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `ver en que se va abre su pantalla en el periodo de la lista`() {
        montar()
        esperar { hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL) }

        composeRule.onNode(hasText(VER_EN_QUE_SE_VA) and enElResumen, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        val pedida = navegaciones.filterIsInstance<Screen.EnQueSeVa>().single()
        assertEquals(HOY.substring(0, 7), pedida.periodo, "el período de la lista (corte 1: el mes de hoy)")
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `con un movimiento elegido el panel es su detalle y no el resumen`() {
        montar()
        elegir("Las Doce")

        assertTrue(!hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL))
        assertTrue(hayEnElPanel("Las Doce"))
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `tocar una fila la elige y llena el panel, sin abrir ninguna hoja`() {
        montar()

        elegir("Las Doce")

        assertTrue(!hayTag(TAG_PANEL_DE_HOJA), "con panel no se abre la hoja modal")
        assertTrue(!hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL))
        assertTrue(
            composeRule.onAllNodes(hasTestTag(TAG_MONTO_DEL_MOVIMIENTO) and enElPanel, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
            "el mismo detalle de la hoja",
        )
        // La fila elegida lo dice.
        val marcada = fila("Las Doce").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected) { false }
        assertTrue(marcada, "la fila elegida está marcada")
        val otra = fila("Señor Gol").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected) { false }
        assertTrue(!otra)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `elegir otra fila cambia el panel`() {
        montar()
        elegir("Las Doce")

        elegir("Supermercado Central")

        assertTrue(!hayEnElPanel("Las Doce"), "el panel ya no muestra el anterior")
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA))
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `cambiar la categoria en el panel actualiza la fila de la lista sin cerrar el panel`() {
        val repo = montar()
        elegir("Señor Gol")
        // Vuelta 83: con una sola cuenta, es la habitual y la fila no la repite.
        assertTrue(filaConSubtitulo("Señor Gol", "Comida"))

        // «Mercado» ya es una categoría usada (la tiene el supermercado): el selector la ofrece.
        tocarAccion(AccionDelMovimiento.Categoria)
        val celda = composeRule.onAllNodes(hasText("Mercado") and enElPanel, useUnmergedTree = true)
            .onFirst()
        celda.performScrollTo()
        celda.performClick()

        esperar { filaConSubtitulo("Señor Gol", "Mercado") }
        assertEquals(listOf("e1" to "Mercado"), repo.categoriasCambiadas)
        assertTrue(hayEnElPanel("Señor Gol"), "el panel sigue abierto con el mismo movimiento")
        assertTrue(!hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL))
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA))
    }

    private fun filaConSubtitulo(descripcion: String, subtitulo: String): Boolean =
        composeRule.onAllNodes(
            hasTestTag(TAG_FILA_DE_MOVIMIENTO_SUELTO) and hasAnyDescendant(hasText(descripcion)) and
                hasAnyDescendant(hasText(subtitulo)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `anular desde el panel abre un dialogo encima y al confirmar el panel vuelve al resumen`() {
        val repo = montar()
        elegir("Las Doce")
        tocarAccion(AccionDelMovimiento.Mas)

        val anular = composeRule.onNode(hasText("Anular este movimiento") and enElPanel, useUnmergedTree = true)
        anular.performScrollTo()
        anular.performClick()
        esperar { hayTag(TAG_PANEL_DE_HOJA) }

        // El diálogo se dibuja en la cáscara, fuera del panel, y el panel sigue detrás.
        val dialogo = composeRule.onNode(hasTestTag(TAG_PANEL_DE_HOJA) and !enElPanel, useUnmergedTree = true)
        dialogo.fetchSemanticsNode()
        assertTrue(hayEnElPanel("Las Doce"))

        // Confirmar: el botón del diálogo.
        composeRule.onAllNodes(
            hasClickAction() and hasAnyAncestor(hasTestTag(TAG_PANEL_DE_HOJA)) and
                hasAnyDescendant(hasText("Anular", substring = true)),
            useUnmergedTree = true,
        ).onLast().performClick()

        esperar { hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL) }
        assertEquals(listOf("e2"), repo.anulados)
        esperar { filaDe("Las Doce").fetchSemanticsNodes().isEmpty() }
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA), "el diálogo se cerró")
    }

    /**
     * El bug del teclado de la web, en lo que Robolectric sí puede ver: con un campo del panel
     * enfocado, elegir otro movimiento **suelta el foco** —no queda ningún nodo enfocado— y el campo
     * del anterior ya no existe. En wasm eso se verifica además a mano en el navegador.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `con un campo del panel enfocado, elegir otro movimiento suelta el foco`() {
        montar()
        elegir("Las Doce")
        // La búsqueda de categorías, que es el campo del bug original.
        tocarAccion(AccionDelMovimiento.Categoria)
        val campo = composeRule.onAllNodes(hasSetTextAction() and enElPanel, useUnmergedTree = true).onFirst()
        campo.performScrollTo()
        campo.requestFocus()
        esperar { composeRule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

        elegir("Señor Gol")

        assertEquals(
            0,
            composeRule.onAllNodes(isFocused() and hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
            "ningún campo quedó con el foco",
        )
        assertTrue(!hayEnElPanel("Las Doce"))
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `la X del panel lo vacia`() {
        montar()
        elegir("Las Doce")

        composeRule.onNode(hasContentDescriptionCerrar() and enElPanel, useUnmergedTree = true).performClick()

        esperar { hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL) }
    }

    private fun hasContentDescriptionCerrar() =
        hasClickAction() and hasAnyDescendant(androidx.compose.ui.test.hasContentDescription("Cerrar"))

    @Test
    @Config(qualifiers = "w1024dp-h900dp-mdpi")
    fun `a 1024 tambien va en lista y detalle, con 380 de detalle`() {
        montar()
        // Vuelta 81: el 40 % de 807 no llega al mínimo del detalle (380); la lista se lleva el resto.
        assertEquals(427f, limites(TAG_LISTA_DE_MOVIMIENTOS_AL_LADO).width, 0.5f)
        assertEquals(380f, limites(TAG_PANEL_DEL_MOVIMIENTO).width, 0.5f)

        elegir("Las Doce")
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA))
    }

    @Test
    @Config(qualifiers = "w1440dp-h900dp-mdpi")
    fun `a 1440 el detalle mide 489 y su contenido va en la ficha`() {
        montar()
        // Vuelta 81: el 40 % de 1440 − 216 − 1.
        assertEquals(489.2f, limites(TAG_PANEL_DEL_MOVIMIENTO).width, 0.5f)
        elegir("Las Doce")
        val monto = composeRule.onNode(hasTestTag(TAG_MONTO_DEL_MOVIMIENTO) and enElPanel, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val panel = limites(TAG_PANEL_DEL_MOVIMIENTO)
        val ficha = limites(TAG_FICHA_DEL_MOVIMIENTO)
        // La ficha, al margen de la pantalla (16) del panel; la cifra, a su relleno (16).
        assertEquals(panel.left + 16f, ficha.left, 1f)
        assertEquals(ficha.left + 16f, monto.left, 1f)
    }

    /**
     * Visto en la web: la rueda sobre el panel, pasado su propio final (o sin nada que desplazar,
     * con la invitación), seguía hasta el relevo de los márgenes de la cáscara y **movía la lista
     * de la izquierda**. El relevo es para los márgenes, no para el panel.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `la rueda sobre el panel no mueve la lista`() {
        eventos = (1..30).map { gasto("m$it", "Gasto número $it", 1_000L * it, "Comida") }
        montar2("Gasto número 1")
        val antes = fila("Gasto número 1").fetchSemanticsNode().boundsInRoot.top

        composeRule.onNodeWithTag(TAG_PANEL_DEL_MOVIMIENTO, useUnmergedTree = true).performMouseInput {
            moveTo(center)
            repeat(10) { scroll(120f) }
        }
        composeRule.waitForIdle()

        val despues = fila("Gasto número 1").fetchSemanticsNode().boundsInRoot.top
        assertEquals(antes, despues, 0.5f, "la lista no se movió")
    }

    @Test
    @Config(qualifiers = "w1280dp-h600dp-mdpi")
    fun `la rueda sobre el panel sigue moviendo su propio contenido`() {
        montar()
        elegir("Las Doce")
        // Ola 2b: el detalle abre corto (se lee primero). Con el selector de categorías abierto, y
        // en una ventana de 600 de alto, es más alto que el panel y se desplaza.
        tocarAccion(AccionDelMovimiento.Categoria)
        fun etiqueta() = composeRule.onNode(hasTestTag(TAG_MONTO_DEL_MOVIMIENTO) and enElPanel, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        val antes = etiqueta()
        val lista = fila("Señor Gol").fetchSemanticsNode().boundsInRoot.top

        composeRule.onNodeWithTag(TAG_PANEL_DEL_MOVIMIENTO, useUnmergedTree = true).performMouseInput {
            moveTo(center)
            repeat(3) { scroll(120f) }
        }
        composeRule.waitForIdle()

        assertTrue(etiqueta() < antes - 1f, "el contenido del panel se desplazó")
        assertEquals(lista, fila("Señor Gol").fetchSemanticsNode().boundsInRoot.top, 0.5f, "la lista no")
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `la rueda sobre la lista la sigue moviendo`() {
        eventos = (1..30).map { gasto("m$it", "Gasto número $it", 1_000L * it, "Comida") }
        montar2("Gasto número 1")
        val antes = fila("Gasto número 1").fetchSemanticsNode().boundsInRoot.top

        composeRule.onNodeWithTag(TAG_LISTA_DE_MOVIMIENTOS_AL_LADO, useUnmergedTree = true).performMouseInput {
            moveTo(center)
            repeat(3) { scroll(120f) }
        }
        composeRule.waitForIdle()

        val despues = filaDe("Gasto número 1").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
        assertTrue(despues == null || despues < antes - 1f, "la lista tenía que moverse: $antes / $despues")
    }

    /**
     * Revisión final, I-1. Con parecidos, la categoría ya quedó guardada cuando el panel pregunta
     * «¿Y los parecidos?». En la hoja la única salida era contestar o cerrar (que avisa); en el
     * panel hay otra —tocar otra fila— y la lista se quedaba con la categoría vieja.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `tocar otra fila sin contestar lo de los parecidos igual actualiza la lista`() {
        val repo = montar2("Señor Gol") {
            it.parecidos = mapOf("e1" to listOf(gasto("p1", "Señor Gol Centro", 30_000L, "Comida")))
        }
        elegir("Señor Gol")
        tocarAccion(AccionDelMovimiento.Categoria)
        val celda = composeRule.onAllNodes(hasText("Mercado") and enElPanel, useUnmergedTree = true).onFirst()
        celda.performScrollTo()
        celda.performClick()
        esperar { hayEnElPanel("¿Y los parecidos?") }
        assertEquals(listOf("e1" to "Mercado"), repo.categoriasCambiadas)

        // Sin contestar: otra fila.
        elegir("Las Doce")

        esperar { filaConSubtitulo("Señor Gol", "Mercado") }
        assertTrue(!hayEnElPanel("¿Y los parecidos?"))
    }

    /** Revisión final, M-2: en el panel el selector de fecha se cierra al guardar, como en la hoja. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `guardar una fecha en el panel cierra el selector`() {
        montar()
        elegir("Las Doce")
        // Ola 2b: la acción «Fecha» abre la sección con el selector ya abierto.
        tocarAccion(AccionDelMovimiento.Fecha)
        val ayer = composeRule.onNode(hasText("Ayer") and enElPanel, useUnmergedTree = true)
        ayer.performScrollTo()
        ayer.performClick()
        val mover = composeRule.onNode(hasText("Mover a", substring = true) and enElPanel, useUnmergedTree = true)
        mover.performScrollTo()
        mover.performClick()

        esperar { !hayEnElPanel("Mover a", substring = true) && !hayEnElPanel("Guardando…") }
        composeRule.waitForIdle()
        assertTrue(!hayEnElPanel("Elige otro día"), "el selector se cerró")
        assertTrue(!hayEnElPanel("Mover a", substring = true))
        assertTrue(hayEnElPanel("Las Doce"), "el panel sigue con el movimiento")
    }

    /**
     * Revisión final, M-3: achicar la ventana con un movimiento en el panel no abre una hoja modal
     * que nadie pidió. Después, tocar una fila en el teléfono sí la abre.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `achicar la ventana con un movimiento elegido no abre la hoja sola`() {
        montar()
        elegir("Las Doce")

        anchoDeLaVentana.value = 390
        composeRule.waitForIdle()
        assertTrue(!hayTag(TAG_PANEL_DEL_MOVIMIENTO), "ya no hay panel")
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA), "y no se abrió ninguna hoja")

        fila("Señor Gol").performClick()
        esperar { hayTag(TAG_PANEL_DE_HOJA) }
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `pasar a una ventana mediana angosta con un movimiento elegido tampoco abre la hoja`() {
        montar()
        elegir("Las Doce")

        anchoDeLaVentana.value = 800
        composeRule.waitForIdle()
        assertTrue(!hayTag(TAG_PANEL_DEL_MOVIMIENTO))
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA))
    }

    // ── Sin lugar al lado: como hoy ─────────────────────────────────────────────

    @Test
    @Config(qualifiers = "w800dp-h900dp-mdpi")
    fun `en una ventana mediana angosta tocar una fila abre la hoja modal como hoy`() {
        montar()
        assertTrue(!hayTag(TAG_PANEL_DEL_MOVIMIENTO), "800 − 80 = 720 < 801: sin panel")
        assertTrue(!hayTag(TAG_LISTA_DE_MOVIMIENTOS_AL_LADO))

        fila("Las Doce").performClick()

        esperar { hayTag(TAG_PANEL_DE_HOJA) }
    }

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `en el telefono tocar una fila abre la hoja como siempre`() {
        montar()
        assertTrue(!hayTag(TAG_PANEL_DEL_MOVIMIENTO))

        fila("Las Doce").performClick()

        esperar { hayTag(TAG_PANEL_DE_HOJA) }
        // Vuelta 81: el teléfono no cambia: la fila de acciones de siempre, sin la ficha.
        assertTrue(!hayTag(TAG_ACCIONES_DE_LA_FICHA))
        assertTrue(!hayTag(TAG_DATO_DE_LA_FICHA))
        assertTrue(hayTag(TAG_CHIP_DE_CATEGORIA))
    }

    // ── Vuelta 81: la pantalla ancha como app de escritorio ─────────────────────────

    /** Los nodos de [tag] dentro de [dentro], sin fusionar. */
    private fun nodos(tag: String, dentro: SemanticsMatcher) =
        composeRule.onAllNodes(hasTestTag(tag) and dentro, useUnmergedTree = true).fetchSemanticsNodes()

    /**
     * A 1.280 el detalle es una **ficha**: una superficie de 520 dp como mucho, con sus datos en filas
     * (cuenta, fecha, categoría, origen) y las acciones **alineadas a la izquierda en un renglón**, la
     * primera al relleno de la ficha y cada una pegada a la anterior (antes, seis repartidas cada 100 px).
     * Una sola amarilla: «Editar».
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 el detalle es una ficha de 520 como mucho con las acciones a la izquierda`() {
        montar()
        elegir("Las Doce")
        val panel = limites(TAG_PANEL_DEL_MOVIMIENTO)
        val ficha = limites(TAG_FICHA_DEL_MOVIMIENTO)
        assertTrue(ficha.width <= 520f + 0.5f, "la ficha mide ${ficha.width}")
        assertTrue(ficha.left >= panel.left && ficha.right <= panel.right, "$ficha / $panel")
        // Los datos, como filas.
        assertTrue(nodos(TAG_DATO_DE_LA_FICHA, enElPanel).size >= 4)
        listOf("Cuenta", "Fecha", "Categoría", "Origen").forEach { assertTrue(hayEnElPanel(it), "falta «$it»") }
        assertTrue(!hayTag(TAG_CHIP_DE_CATEGORIA), "la categoría es una fila, no el chip")
        // Las acciones: a la izquierda, juntas.
        val acciones = AccionDelMovimiento.entries
            .mapNotNull { a -> nodos(tagDeAccionDelMovimiento(a), hasAnyAncestor(hasTestTag(TAG_ACCIONES_DE_LA_FICHA))).firstOrNull() }
            .map { it.boundsInRoot }
            .sortedBy { it.left }
        assertTrue(acciones.size >= 2, "$acciones")
        assertEquals(ficha.left + 16f, acciones.first().left, 1f, "la primera, al relleno de la ficha")
        acciones.zipWithNext().forEach { (a, b) ->
            if (kotlin.math.abs(a.top - b.top) < 1f) assertTrue(b.left - a.right <= 8f + 0.5f, "repartidas: $a / $b")
        }
        // Una sola amarilla a la vista.
        val amarillas = composeRule.onAllNodes(
            SemanticsMatcher.expectValue(com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey, true),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(1, amarillas.size, "un solo amarillo por vista")
    }

    /**
     * Vuelta 83 · **el «⋯» a la derecha del título**, y abajo «Editar · Repetir · Repartir» en un solo
     * renglón. Iba al final de las acciones y no cabía: quedaba solo en un segundo renglón.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 el mas de la ficha va arriba junto a la X, y las acciones en un renglon`() {
        montar()
        elegir("Las Doce")
        val mas = nodos(tagDeAccionDelMovimiento(AccionDelMovimiento.Mas), enElPanel).single().boundsInRoot
        val primerDato = nodos(TAG_DATO_DE_LA_FICHA, enElPanel).minOf { it.boundsInRoot.top }
        assertTrue(mas.bottom <= primerDato + 0.5f, "el «⋯» va arriba de los datos: $mas")
        // Revisión de la 83: lado a lado con la X, en su mismo renglón (iban apilados).
        val x = composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Cerrar") and enElPanel, useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot
        assertEquals(x.center.y, mas.center.y, 1f, "el «⋯» y la X en el mismo renglón: $mas / $x")
        assertTrue(mas.right <= x.left + 0.5f, "el «⋯» a la izquierda de la X: $mas / $x")
        assertTrue(
            nodos(tagDeAccionDelMovimiento(AccionDelMovimiento.Mas), hasAnyAncestor(hasTestTag(TAG_ACCIONES_DE_LA_FICHA))).isEmpty(),
            "ya no está entre las acciones de abajo",
        )
        val acciones = nodos(TAG_ACCIONES_DE_LA_FICHA, enElPanel).single()
        val botones = AccionDelMovimiento.entries
            .mapNotNull { a -> nodos(tagDeAccionDelMovimiento(a), hasAnyAncestor(hasTestTag(TAG_ACCIONES_DE_LA_FICHA))).firstOrNull() }
            .map { it.boundsInRoot }
        assertTrue(botones.size >= 2, "$botones")
        assertTrue(botones.all { kotlin.math.abs(it.top - botones.first().top) < 1f }, "en un solo renglón: $botones")
        assertTrue(acciones.boundsInRoot.height < 48f + 0.5f, "un renglón de botones chicos: ${acciones.boundsInRoot}")
    }

    /** Revisión de la 83: «Gasto por categoría» con la fila densa del Inicio (la barra dentro de la fila). */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 las categorias del resumen son filas densas con la barra adentro`() {
        montar()
        esperar { hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL) }
        val categorias = nodos(TAG_CATEGORIA_DEL_RESUMEN, enElResumen)
        assertTrue(categorias.isNotEmpty())
        categorias.forEach { assertTrue(it.boundsInRoot.height <= 66f, "cada categoría mide ${it.boundsInRoot.height} (eran ~74)") }
        val barras = nodos(TAG_BARRA_DEL_RESUMEN, enElResumen)
        assertEquals(categorias.size, barras.size)
        barras.zip(categorias).forEach { (b, c) -> assertTrue(b.boundsInRoot.bottom <= c.boundsInRoot.bottom + 0.5f, "la barra va dentro de su fila") }
    }

    /** Vuelta 83: el panel se titula «Resumen del período»; el mes ya lo dice el navegador de la lista. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 el panel se titula Resumen del periodo y el mes no se repite`() {
        montar()
        esperar { hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL) }
        assertTrue(hayEnElResumen(TITULO_DEL_RESUMEN_EN_EL_PANEL))
        val conAnio = composeRule.onAllNodes(hasText(" 20", substring = true) and enElResumen, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }.orEmpty() }
            .map { it.text }
            .filter { Regex("""^[A-ZÁÉÍÓÚ][a-záéíóú]+ \d{4}""").containsMatchIn(it) }
        assertTrue(conAnio.isEmpty(), "el panel no repite «Octubre 2026»: $conAnio")
    }

    /** A 1.920 el panel mide 575: la ficha se queda en 520. */
    @Test
    @Config(qualifiers = "w1920dp-h900dp-mdpi")
    fun `a 1920 la ficha no pasa de 520`() {
        montar()
        elegir("Las Doce")
        assertEquals(520f, limites(TAG_FICHA_DEL_MOVIMIENTO).width, 0.5f)
    }

    /** Tocar la fila de la categoría abre la sección de siempre, debajo de las acciones. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `en la ficha la fila de la categoria abre el selector`() {
        montar()
        elegir("Las Doce")
        tocarAccion(AccionDelMovimiento.Categoria)
        esperar { hayEnElPanel("Categoría · Comida") }
    }

    /**
     * A 1.280 la lista es la protagonista y el desglose es una columna lateral: Entró, Salió y Neto en
     * filas, y cada categoría con su barra de 4 dp.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 el desglose es una columna lateral con barras`() {
        montar()
        esperar { hayTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL) }
        assertTrue(limites(TAG_LISTA_DE_MOVIMIENTOS_AL_LADO).width > limites(TAG_PANEL_DEL_MOVIMIENTO).width)
        val cifras = limites(TAG_CIFRAS_DEL_RESUMEN)
        val filas = composeRule.onAllNodes(hasTestTag(com.jvillada.movi.ui.components.ds.TAG_FILA) and hasAnyAncestor(hasTestTag(TAG_CIFRAS_DEL_RESUMEN)), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(3, filas.size, "Entró, Salió y Neto, una debajo de la otra")
        assertTrue(filas.zipWithNext().all { (a, b) -> b.top >= a.bottom - 0.5f })
        assertTrue(filas.all { it.width >= cifras.width - 0.5f })
        val categorias = nodos(TAG_CATEGORIA_DEL_RESUMEN, hasAnyAncestor(hasTestTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL)))
        val barras = nodos(TAG_BARRA_DEL_RESUMEN, hasAnyAncestor(hasTestTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL)))
        assertEquals(categorias.size, barras.size, "una barra por categoría")
        barras.forEach { assertEquals(4f, it.boundsInRoot.height, 0.5f, "la barra mide 4 dp") }
    }

    /** Sin lugar para el panel (800 dp), el detalle se abre como diálogo con la misma ficha, de 520. */
    @Test
    @Config(qualifiers = "w800dp-h900dp-mdpi")
    fun `en una ventana mediana el dialogo del detalle es la misma ficha`() {
        montar()
        fila("Las Doce").performClick()
        esperar { hayTag(TAG_PANEL_DE_HOJA) }
        assertTrue(limites(TAG_PANEL_DE_HOJA).width <= 520f + 0.5f, "${limites(TAG_PANEL_DE_HOJA)}")
        assertTrue(hayTag(TAG_ACCIONES_DE_LA_FICHA))
        assertTrue(hayTag(TAG_DATO_DE_LA_FICHA))
    }

}

private val HOY: String = com.jvillada.movi.ui.quickadd.todayIsoInAppZone()
