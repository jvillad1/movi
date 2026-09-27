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
                        TransactionsScreen(onNavigate = {})
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

    // ── Escritorio ──────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 la lista y el panel se ven a la vez, el panel vacio invita a elegir`() {
        montar()

        val lista = limites(TAG_LISTA_DE_MOVIMIENTOS_AL_LADO)
        val panel = limites(TAG_PANEL_DEL_MOVIMIENTO)
        // Ola X: la lista subió de 360 a 420 dp — el dueño la vio apretada.
        assertEquals(420f, lista.width, 0.5f)
        // 1280 − 216 del rail − 420 de la lista − 1 del divisor.
        assertEquals(643f, panel.width, 0.5f, "el panel se lleva lo que sobra")
        assertTrue(panel.left >= lista.right, "$lista / $panel")
        // La lista es la de siempre, con su «Flujo del día».
        assertTrue(hayEnLaLista("Señor Gol"))
        assertTrue(hayEnLaLista("Flujo del día"))
        // Nada elegido: la invitación, y ninguna hoja abierta.
        assertTrue(hayEnElPanel(TITULO_DE_LA_INVITACION_A_ELEGIR))
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA), "no se abrió ninguna hoja")
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `tocar una fila la elige y llena el panel, sin abrir ninguna hoja`() {
        montar()

        elegir("Las Doce")

        assertTrue(!hayTag(TAG_PANEL_DE_HOJA), "con panel no se abre la hoja modal")
        assertTrue(!hayEnElPanel(TITULO_DE_LA_INVITACION_A_ELEGIR))
        assertTrue(hayEnElPanel("MONTO, CUENTA Y CONCEPTO"), "el mismo contenido de la hoja")
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
        assertTrue(filaConSubtitulo("Señor Gol", "Comida · Bancolombia"))

        // «Mercado» ya es una categoría usada (la tiene el supermercado): el selector la ofrece.
        val celda = composeRule.onAllNodes(hasText("Mercado") and enElPanel, useUnmergedTree = true)
            .onFirst()
        celda.performScrollTo()
        celda.performClick()

        esperar { filaConSubtitulo("Señor Gol", "Mercado · Bancolombia") }
        assertEquals(listOf("e1" to "Mercado"), repo.categoriasCambiadas)
        assertTrue(hayEnElPanel("Señor Gol"), "el panel sigue abierto con el mismo movimiento")
        assertTrue(!hayEnElPanel(TITULO_DE_LA_INVITACION_A_ELEGIR))
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
    fun `anular desde el panel abre un dialogo encima y al confirmar el panel vuelve a la invitacion`() {
        val repo = montar()
        elegir("Las Doce")

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

        esperar { hayEnElPanel(TITULO_DE_LA_INVITACION_A_ELEGIR) }
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

        esperar { hayEnElPanel(TITULO_DE_LA_INVITACION_A_ELEGIR) }
    }

    private fun hasContentDescriptionCerrar() =
        hasClickAction() and hasAnyDescendant(androidx.compose.ui.test.hasContentDescription("Cerrar"))

    @Test
    @Config(qualifiers = "w1024dp-h900dp-mdpi")
    fun `a 1024 tambien va en lista y detalle, con 387 de detalle`() {
        montar()
        assertEquals(420f, limites(TAG_LISTA_DE_MOVIMIENTOS_AL_LADO).width, 0.5f)
        // 1024 − 216 − 420 − 1.
        assertEquals(387f, limites(TAG_PANEL_DEL_MOVIMIENTO).width, 0.5f)

        elegir("Las Doce")
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA))
    }

    @Test
    @Config(qualifiers = "w1440dp-h900dp-mdpi")
    fun `a 1440 el detalle mide 803 y su contenido va en la columna de lectura`() {
        montar()
        assertEquals(803f, limites(TAG_PANEL_DEL_MOVIMIENTO).width, 0.5f)
        elegir("Las Doce")
        val monto = composeRule.onNode(hasText("MONTO, CUENTA Y CONCEPTO") and enElPanel, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val panel = limites(TAG_PANEL_DEL_MOVIMIENTO)
        // Centrado en 720: (803 − 720) / 2 = 41.5 de aire, más los 20 de relleno del panel.
        assertEquals(panel.left + 41.5f + 20f, monto.left, 1f)
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
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `la rueda sobre el panel sigue moviendo su propio contenido`() {
        montar()
        elegir("Las Doce")
        fun etiqueta() = composeRule.onNode(hasText("MONTO, CUENTA Y CONCEPTO") and enElPanel, useUnmergedTree = true)
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
        val celda = composeRule.onAllNodes(hasText("Mercado") and enElPanel, useUnmergedTree = true).onFirst()
        celda.performScrollTo()
        celda.performClick()
        esperar { hayEnElPanel("¿Y LOS PARECIDOS?") }
        assertEquals(listOf("e1" to "Mercado"), repo.categoriasCambiadas)

        // Sin contestar: otra fila.
        elegir("Las Doce")

        esperar { filaConSubtitulo("Señor Gol", "Mercado · Bancolombia") }
        assertTrue(!hayEnElPanel("¿Y LOS PARECIDOS?"))
    }

    /** Revisión final, M-2: en el panel el selector de fecha se cierra al guardar, como en la hoja. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `guardar una fecha en el panel cierra el selector`() {
        montar()
        elegir("Las Doce")
        // La fila de FECHA: su «Cambiar» es el segundo del panel (el primero es el del monto).
        val cambiarFecha = composeRule.onAllNodes(hasText("Cambiar") and enElPanel, useUnmergedTree = true)[1]
        cambiarFecha.performScrollTo()
        cambiarFecha.performClick()
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
    }

}

private val HOY: String = com.jvillada.movi.ui.quickadd.todayIsoInAppZone()
