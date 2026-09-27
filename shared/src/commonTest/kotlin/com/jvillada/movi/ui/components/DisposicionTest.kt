package com.jvillada.movi.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # El esqueleto de la web (Ola W1), en números
 *
 * La clase de ancho se decide UNA vez en la cáscara ([claseDeAncho]) y cada pantalla dice de qué
 * tipo es ([disposicionDe]); con esas dos cosas la tabla de topes dice cuánto puede medir su
 * contenido. Estas pruebas fijan los bordes y la tabla entera, para que un cambio de número se
 * vea como lo que es: una decisión de diseño, no un retoque.
 */
class DisposicionTest {

    // ── La clase de ancho ─────────────────────────────────────────────────────

    @Test
    fun `por debajo de 600 dp es un telefono`() {
        assertEquals(WindowWidthClass.Compact, claseDeAncho(0.dp))
        assertEquals(WindowWidthClass.Compact, claseDeAncho(390.dp))
        assertEquals(WindowWidthClass.Compact, claseDeAncho(599.dp))
        assertEquals(WindowWidthClass.Compact, claseDeAncho(599.9.dp))
    }

    @Test
    fun `de 600 a 999 dp es mediano`() {
        assertEquals(WindowWidthClass.Medium, claseDeAncho(600.dp))
        assertEquals(WindowWidthClass.Medium, claseDeAncho(768.dp))
        assertEquals(WindowWidthClass.Medium, claseDeAncho(999.dp))
    }

    @Test
    fun `desde 1000 dp es expandido`() {
        assertEquals(WindowWidthClass.Expanded, claseDeAncho(1_000.dp))
        assertEquals(WindowWidthClass.Expanded, claseDeAncho(1_280.dp))
        assertEquals(WindowWidthClass.Expanded, claseDeAncho(1_920.dp))
    }

    // ── Qué tipo de pantalla es cada una ───────────────────────────────────────

    @Test
    fun `Hoy, Patrimonio, Creditos y Plan son tableros y lo que no se adapto todavia se lee en columna`() {
        listOf(Screen.Dashboard, Screen.Accounts, Screen.Credits, Screen.Plan())
            .forEach { assertEquals(Disposicion.Tablero, disposicionDe(it), "$it") }
        listOf(
            Screen.Mas, Screen.Profile, Screen.Categorias, Screen.Login, Screen.PorRevisar,
        ).forEach { assertEquals(Disposicion.Lectura, disposicionDe(it), "$it") }
    }

    /** Ola W2: la lista de períodos a la izquierda y el detalle del elegido a la derecha. */
    @Test
    fun `Tus periodos y su detalle son lista y detalle`() {
        assertEquals(Disposicion.ListaYDetalle, disposicionDe(Screen.Periodos))
        assertEquals(Disposicion.ListaYDetalle, disposicionDe(Screen.DetalleDePeriodo("2026-09")))
        assertEquals(1_440.dp, anchoMaximoDeLaPantalla(Screen.Periodos, WindowWidthClass.Expanded))
        assertEquals(840.dp, anchoMaximoDeLaPantalla(Screen.DetalleDePeriodo("2026-09"), WindowWidthClass.Medium))
        assertEquals(Dp.Infinity, anchoMaximoDeLaPantalla(Screen.Periodos, WindowWidthClass.Compact))
    }

    // ── La tabla de topes ─────────────────────────────────────────────────────

    @Test
    fun `en el telefono todo va lleno`() {
        Disposicion.entries.forEach {
            assertEquals(Dp.Infinity, anchoMaximoDe(it, WindowWidthClass.Compact), "$it")
        }
    }

    @Test
    fun `la columna de lectura mide 720 en mediano y expandido`() {
        assertEquals(720.dp, anchoMaximoDe(Disposicion.Lectura, WindowWidthClass.Medium))
        assertEquals(720.dp, anchoMaximoDe(Disposicion.Lectura, WindowWidthClass.Expanded))
    }

    @Test
    fun `el tablero mide 840 en mediano y 1280 en expandido`() {
        assertEquals(840.dp, anchoMaximoDe(Disposicion.Tablero, WindowWidthClass.Medium))
        assertEquals(1_280.dp, anchoMaximoDe(Disposicion.Tablero, WindowWidthClass.Expanded))
    }

    @Test
    fun `lista y detalle mide 840 en mediano y 1440 en expandido`() {
        assertEquals(840.dp, anchoMaximoDe(Disposicion.ListaYDetalle, WindowWidthClass.Medium))
        assertEquals(1_440.dp, anchoMaximoDe(Disposicion.ListaYDetalle, WindowWidthClass.Expanded))
    }

    @Test
    fun `el tope de una pantalla es el de su disposicion`() {
        assertEquals(1_280.dp, anchoMaximoDeLaPantalla(Screen.Dashboard, WindowWidthClass.Expanded))
        assertEquals(840.dp, anchoMaximoDeLaPantalla(Screen.Dashboard, WindowWidthClass.Medium))
        // Ola W4: Movimientos pasó a lista y detalle (ver `MovimientosListaYDetalleTest`).
        assertEquals(1_440.dp, anchoMaximoDeLaPantalla(Screen.Transactions(), WindowWidthClass.Expanded))
        assertEquals(720.dp, anchoMaximoDeLaPantalla(Screen.PorRevisar, WindowWidthClass.Expanded))
        assertEquals(720.dp, anchoMaximoDeLaPantalla(Screen.Profile, WindowWidthClass.Medium))
        assertEquals(Dp.Infinity, anchoMaximoDeLaPantalla(Screen.Dashboard, WindowWidthClass.Compact))
    }

    /**
     * Ola W3: el ancho real del panel dentro de la cáscara es la ventana menos el rail de su clase,
     * topado por la disposición. Es la cuenta que la Ola W2 hizo sin restar el rail.
     */
    @Test
    fun `el panel es la ventana menos el rail, topado por la disposicion`() {
        assertEquals(0.dp, anchoDelRail(WindowWidthClass.Compact))
        assertEquals(80.dp, anchoDelRail(WindowWidthClass.Medium))
        assertEquals(216.dp, anchoDelRail(WindowWidthClass.Expanded))
        // Teléfono: todo el ancho.
        assertEquals(390.dp, anchoDelPanelEnLaCascara(390.dp, Screen.Dashboard))
        // Mediano: menos el rail compacto; una lectura se topa en 720.
        assertEquals(688.dp, anchoDelPanelEnLaCascara(768.dp, Screen.Dashboard))
        assertEquals(720.dp, anchoDelPanelEnLaCascara(999.dp, Screen.Profile))
        // Escritorio: menos el rail ancho. Lista y detalle (W2) a 1280: 1064, no 1280.
        assertEquals(1_064.dp, anchoDelPanelEnLaCascara(1_280.dp, Screen.Periodos))
        assertEquals(1_440.dp, anchoDelPanelEnLaCascara(1_920.dp, Screen.Periodos))
        assertEquals(720.dp, anchoDelPanelEnLaCascara(1_280.dp, Screen.Profile))
    }

    @Test
    fun `dos columnas iguales descuentan 48 de aire`() {
        assertEquals(48.dp, RELLENO_DE_DOS_COLUMNAS)
        assertTrue(cabenDosColumnasIguales(848.dp, 400.dp))
        assertFalse(cabenDosColumnasIguales(847.dp, 400.dp))
    }

    /** El umbral de las dos columnas del Inicio es del CONTENIDO, no de la ventana: se conserva. */
    @Test
    fun `el Inicio se parte en dos columnas desde 900 dp de contenido`() {
        assertEquals(900.dp, ANCHO_PARA_DOS_COLUMNAS)
    }
}
