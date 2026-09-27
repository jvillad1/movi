package com.jvillada.movi.ui.transactions

import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.Disposicion
import com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara
import com.jvillada.movi.ui.components.disposicionDe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * # Movimientos en lista + detalle (Ola W4), en números
 *
 * El umbral se prueba contra el ancho REAL de la pantalla dentro de la cáscara
 * ([anchoDelPanelEnLaCascara]: ventana − rail, topado a 840 / 1.440), no contra el de la ventana:
 * la Ola W2 se equivocó justo ahí.
 */
class MovimientosListaYDetalleTest {

    private val movimientos = Screen.Transactions()

    @Test
    fun `Movimientos es lista y detalle`() {
        assertEquals(Disposicion.ListaYDetalle, disposicionDe(movimientos))
        assertEquals(Disposicion.ListaYDetalle, disposicionDe(Screen.Transactions(chipInicial = CHIP_GASTOS)))
    }

    @Test
    fun `el ancho de Movimientos en cada ventana, ya sin el rail`() {
        // Mediano: ventana − 80, topado a 840.
        assertEquals(688.dp, anchoDelPanelEnLaCascara(768.dp, movimientos))
        assertEquals(780.dp, anchoDelPanelEnLaCascara(860.dp, movimientos))
        assertEquals(840.dp, anchoDelPanelEnLaCascara(999.dp, movimientos))
        // Escritorio: ventana − 216, topado a 1.440.
        assertEquals(808.dp, anchoDelPanelEnLaCascara(1_024.dp, movimientos))
        assertEquals(1_064.dp, anchoDelPanelEnLaCascara(1_280.dp, movimientos))
        assertEquals(1_224.dp, anchoDelPanelEnLaCascara(1_440.dp, movimientos))
        assertEquals(1_440.dp, anchoDelPanelEnLaCascara(1_920.dp, movimientos))
    }

    @Test
    fun `el umbral es la lista mas el divisor mas el detalle minimo`() {
        assertEquals(781.dp, UMBRAL_DE_MOVIMIENTOS_CON_PANEL)
        assertEquals(
            ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS + ANCHO_DEL_DIVISOR_DE_MOVIMIENTOS + ANCHO_MINIMO_DEL_PANEL_DEL_MOVIMIENTO,
            UMBRAL_DE_MOVIMIENTOS_CON_PANEL,
        )
        assertTrue(movimientosConPanel(781.dp))
        assertFalse(movimientosConPanel(780.dp))
    }

    @Test
    fun `con panel en toda ventana de escritorio y en las medianas anchas`() {
        listOf(861, 920, 999, 1_000, 1_024, 1_280, 1_440, 1_920).forEach { ventana ->
            assertTrue(movimientosConPanel(anchoDelPanelEnLaCascara(ventana.dp, movimientos)), "$ventana")
        }
    }

    @Test
    fun `sin panel en las medianas angostas, como hoy`() {
        listOf(600, 700, 768, 800, 860).forEach { ventana ->
            assertFalse(movimientosConPanel(anchoDelPanelEnLaCascara(ventana.dp, movimientos)), "$ventana")
        }
    }

    @Test
    fun `a 1024 el detalle mide 447 y a 1280, 703`() {
        fun detalle(ventana: Int) = anchoDelPanelEnLaCascara(ventana.dp, movimientos) -
            ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS - ANCHO_DEL_DIVISOR_DE_MOVIMIENTOS
        assertEquals(447.dp, detalle(1_024))
        assertEquals(703.dp, detalle(1_280))
        assertEquals(863.dp, detalle(1_440))
    }

    // ── Qué muestra el panel ─────────────────────────────────────────────────

    private fun gasto(id: String, categoria: String = "Comida") = FinancialEvent(
        id = id, accountId = "acc", type = TransactionType.EXPENSE, amount = 10_000L,
        category = categoria, description = "Gasto $id", timestamp = 0L,
    )

    @Test
    fun `sin nada elegido el panel queda vacio`() {
        assertNull(movimientoDelPanel(null, listOf(EventDay("2026-09-01", -10_000L, listOf(gasto("a"))))))
    }

    @Test
    fun `el panel muestra la version releida del elegido, no la vieja`() {
        val viejo = gasto("a", categoria = "Comida")
        val releido = gasto("a", categoria = "Mercado")
        val dias = listOf(
            EventDay("2026-09-02", -10_000L, listOf(gasto("b"))),
            EventDay("2026-09-01", -10_000L, listOf(releido)),
        )
        assertSame(releido, movimientoDelPanel(viejo, dias))
    }

    @Test
    fun `si el elegido desaparecio de la lista (se anulo) el panel queda vacio`() {
        val dias = listOf(EventDay("2026-09-01", -10_000L, listOf(gasto("b"))))
        assertNull(movimientoDelPanel(gasto("a"), dias))
    }

    @Test
    fun `sin lista leida se sigue mostrando el elegido`() {
        val elegido = gasto("a")
        assertSame(elegido, movimientoDelPanel(elegido, null))
    }
}
