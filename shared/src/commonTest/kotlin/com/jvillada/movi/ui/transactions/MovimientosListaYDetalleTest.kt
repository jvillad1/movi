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
        // Ola X: la lista subió a 420 (antes 360) y el detalle mínimo bajó a 380 (antes 420) para
        // compensar — ver el comentario de [ANCHO_MINIMO_DEL_PANEL_DEL_MOVIMIENTO].
        assertEquals(801.dp, UMBRAL_DE_MOVIMIENTOS_CON_PANEL)
        assertEquals(
            ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS + ANCHO_DEL_DIVISOR_DE_MOVIMIENTOS + ANCHO_MINIMO_DEL_PANEL_DEL_MOVIMIENTO,
            UMBRAL_DE_MOVIMIENTOS_CON_PANEL,
        )
        assertTrue(movimientosConPanel(801.dp))
        assertFalse(movimientosConPanel(800.dp))
    }

    /**
     * Ola X: al subir el umbral de 781 a 801, el borde de aparición ya no cae en 861 sino en 881
     * (mediana: 881 − 80 = 801). Entre 1.000 y 1.016 dp de ventana hay una franja angosta sin panel
     * —el rail salta de 80 a 216 al entrar a escritorio (784 < 801) antes de que la ventana crezca
     * lo suficiente para volver a superar el umbral (1.017: 1.017 − 216 = 801)—; ya existía esa
     * caída de ancho al cruzar a escritorio (ver `el ancho de Movimientos en cada ventana`), solo
     * que antes el umbral (781) quedaba por debajo de los 784 de esa caída y no se notaba.
     */
    @Test
    fun `con panel en toda ventana de escritorio y en las medianas anchas`() {
        listOf(881, 920, 999, 1_017, 1_024, 1_280, 1_440, 1_920).forEach { ventana ->
            assertTrue(movimientosConPanel(anchoDelPanelEnLaCascara(ventana.dp, movimientos)), "$ventana")
        }
    }

    @Test
    fun `sin panel en las medianas angostas, como hoy, y en la franja angosta de escritorio`() {
        listOf(600, 700, 768, 800, 860, 1_000, 1_010).forEach { ventana ->
            assertFalse(movimientosConPanel(anchoDelPanelEnLaCascara(ventana.dp, movimientos)), "$ventana")
        }
    }

    @Test
    fun `a 1024 el detalle mide 387 y a 1280, 643`() {
        fun detalle(ventana: Int) = anchoDelPanelEnLaCascara(ventana.dp, movimientos) -
            ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS - ANCHO_DEL_DIVISOR_DE_MOVIMIENTOS
        assertEquals(387.dp, detalle(1_024))
        assertEquals(643.dp, detalle(1_280))
        assertEquals(803.dp, detalle(1_440))
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
