package com.jvillada.movi.ui.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.TAG_VACIO_QUE_ENSENA
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.dashboard.TipoDeNotificacion
import com.jvillada.movi.ui.dashboard.notificationRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La campana se lee de un vistazo (vuelta de diseño 8)
 *
 * Eran renglones de texto plano («Internet · Vencido ayer») con un chevron. Ahora cada aviso es una
 * `Fila` con su ícono en el tono de su estado, el título en frase, el monto con la `Cifra` del
 * sistema cuando lo hay, y lo urgente arriba. Sin nada, un `Vacio`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp-xhdpi")
class LaCampanaHablaElSistemaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        RecurringRule(id = id, name = nombre, category = "Servicios", amount = monto, dayOfMonth = 5, type = TransactionType.EXPENSE),
        dueDate = "2026-10-26",
        daysUntil = dias,
        status = PaymentStatus.UPCOMING,
    )

    private val datos = DashboardData(
        upcoming = listOf(pago("rr_col", "Colegio", 700_000, 3), pago("rr_int", "Internet", 90_000, -1)),
        // Vuelta de diseño 22: la campana lee la cola, que sale del checklist del período.
        ocurrencias = emptyList(),
        periodoActual = com.jvillada.movi.shared.model.PeriodoFinanciero(2026, 10),
        ajustesDePeriodo = com.jvillada.movi.shared.model.PeriodSettings(),
        budgets = listOf(Budget("Mercado", 100_000)),
        spentByCategory = mapOf("Mercado" to 150_000L),
        pendingSms = 2,
    )

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `cada aviso es una Fila con su monto, lo urgente primero`() {
        val filas = notificationRows(datos)
        var abierta: Screen? = null
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { NotificationsPanel(filas, onDismiss = {}, onRowClick = { abierta = it }) } }
        }
        composeRule.waitForIdle()

        assertEquals("Internet venció ayer", filas.first().titulo, "lo vencido va primero")
        filas.forEachIndexed { i, fila ->
            val nodos = composeRule.onAllNodesWithTag("$TAG_AVISO_DE_LA_CAMPANA:$i", useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, nodos.size, "el aviso $i se pinta")
            assertTrue(hay(fila.titulo), "«${fila.titulo}» se lee tal cual")
        }
        // Cada aviso es la Fila del sistema: se toca entera y mide su alto (64 con subtítulo, 56 sin).
        val densidad = composeRule.density.density
        filas.forEachIndexed { i, fila ->
            val nodo = composeRule.onAllNodesWithTag("$TAG_AVISO_DE_LA_CAMPANA:$i", useUnmergedTree = true).fetchSemanticsNodes().single()
            assertTrue(SemanticsActions.OnClick in nodo.config, "el aviso $i se toca")
            val minimo = if (fila.subtitulo != null) Medida.altoDeFilaDoble else Medida.altoDeFila
            assertTrue(nodo.size.height / densidad >= minimo.value - 0.5f, "el aviso $i mide lo de una Fila")
        }
        // Los dos pagos llevan su monto con la Cifra; las alertas, no.
        assertEquals(2, composeRule.onAllNodesWithTag(TAG_CIFRA, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(hay("$90.000") && hay("$700.000"))
        // Y tocar uno lleva a donde se resuelve.
        composeRule.onAllNodesWithTag("$TAG_AVISO_DE_LA_CAMPANA:0", useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(Screen.Plan(SEGMENTO_PAGOS), abierta)
    }

    @Test
    fun `sin nada pendiente dice una frase corta en un Vacio`() {
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { NotificationsPanel(emptyList(), onDismiss = {}, onRowClick = {}) } }
        }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_VACIO_QUE_ENSENA, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(hay(CAMPANA_SIN_AVISOS))
        assertEquals(
            0,
            composeRule.onAllNodes(hasTestTag(TAG_FILA) and hasAnyAncestor(hasTestTag(TAG_VACIO_QUE_ENSENA)), useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `el tono dice el estado`() {
        assertEquals(TonoDeIcono.Peligro, iconoDelAviso(TipoDeNotificacion.PagoVencido).second)
        assertEquals(TonoDeIcono.Aviso, iconoDelAviso(TipoDeNotificacion.PagoPorVencer).second)
        assertEquals(TonoDeIcono.Peligro, iconoDelAviso(TipoDeNotificacion.BancoMudo).second)
        assertEquals(TonoDeIcono.Peligro, iconoDelAviso(TipoDeNotificacion.PresupuestoSuperado).second)
        assertEquals(TonoDeIcono.Aviso, iconoDelAviso(TipoDeNotificacion.MensajesDelBanco).second)
        assertEquals(TonoDeIcono.Aviso, iconoDelAviso(TipoDeNotificacion.DebitoPorConfirmar).second)
        assertEquals(TonoDeIcono.Aviso, iconoDelAviso(TipoDeNotificacion.PagoDeTarjetaPorConfirmar).second)
        // Cada tipo tiene su ícono: ninguno se queda sin forma propia.
        assertEquals(
            TipoDeNotificacion.entries.size,
            TipoDeNotificacion.entries.map { iconoDelAviso(it).first }.toSet().size,
        )
    }
}
