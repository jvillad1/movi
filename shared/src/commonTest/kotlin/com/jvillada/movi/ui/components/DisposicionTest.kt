package com.jvillada.movi.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.Screen
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `en W1 Hoy es un tablero y todo lo demas se lee en columna`() {
        assertEquals(Disposicion.Tablero, disposicionDe(Screen.Dashboard))
        listOf(
            Screen.Transactions(), Screen.Accounts, Screen.Credits, Screen.Plan(), Screen.Periodos,
            Screen.Mas, Screen.Profile, Screen.Categorias, Screen.Login, Screen.PorRevisar,
        ).forEach { assertEquals(Disposicion.Lectura, disposicionDe(it), "$it") }
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
        assertEquals(720.dp, anchoMaximoDeLaPantalla(Screen.Transactions(), WindowWidthClass.Expanded))
        assertEquals(720.dp, anchoMaximoDeLaPantalla(Screen.Accounts, WindowWidthClass.Medium))
        assertEquals(Dp.Infinity, anchoMaximoDeLaPantalla(Screen.Dashboard, WindowWidthClass.Compact))
    }

    /** El umbral de las dos columnas del Inicio es del CONTENIDO, no de la ventana: se conserva. */
    @Test
    fun `el Inicio se parte en dos columnas desde 900 dp de contenido`() {
        assertEquals(900.dp, ANCHO_PARA_DOS_COLUMNAS)
    }
}
