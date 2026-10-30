package com.jvillada.movi.ui

import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ANCHO_DE_LECTURA
import com.jvillada.movi.ui.components.Disposicion
import com.jvillada.movi.ui.components.RELLENO_DE_DOS_COLUMNAS
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara
import com.jvillada.movi.ui.components.anchoMaximoDeLaPantalla
import com.jvillada.movi.ui.components.disposicionDe
import com.jvillada.movi.ui.sdui.anchoDeLaBanda
import com.jvillada.movi.ui.transactions.AccionDelMovimiento
import com.jvillada.movi.ui.transactions.ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS
import com.jvillada.movi.ui.transactions.ANCHO_MINIMO_DEL_PANEL_DEL_MOVIMIENTO
import com.jvillada.movi.ui.transactions.ClaveDelDato
import com.jvillada.movi.ui.transactions.UMBRAL_DE_MOVIMIENTOS_CON_PANEL
import com.jvillada.movi.ui.transactions.accionesDeLaFicha
import com.jvillada.movi.ui.transactions.anchoDeLaListaDeMovimientos
import com.jvillada.movi.ui.transactions.datosDeLaFicha
import com.jvillada.movi.ui.transactions.fraccionDeLaBarraDelResumen
import com.jvillada.movi.ui.transactions.origenDelMovimiento
import com.jvillada.movi.ui.transactions.principalDeLaFicha
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 81: la pantalla ancha como app de escritorio (lo puro)
 *
 * La ficha del movimiento (sus datos, sus acciones y la regla del amarillo), las proporciones de
 * Actividad, la banda del Inicio a lo ancho y el ancho máximo de lectura de las pantallas del barrido.
 */
class Vuelta81Test {

    private val hoy = LocalDate(2026, 10, 30)

    private fun gasto(
        descripcion: String = "Éxito Suba",
        categoria: String = "Mercado",
        fuente: EventSource = EventSource.MANUAL,
        cuotas: Int? = null,
    ) = FinancialEvent(
        id = "e1", accountId = "a1", type = TransactionType.EXPENSE, amount = 50_000L, category = categoria,
        description = descripcion, timestamp = 0L, source = fuente,
        reconciliationStatus = ReconciliationStatus.RECONCILED, cuotas = cuotas,
    )

    // ── 1. La ficha ─────────────────────────────────────────────────────────────

    @Test
    fun `los datos de la ficha van en su orden y solo lo que hay`() {
        val datos = datosDeLaFicha(gasto(), "Bancolombia Ahorros 4057", espacio = null, hoy = hoy)
        assertEquals(listOf(ClaveDelDato.Cuenta, ClaveDelDato.Fecha, ClaveDelDato.Categoria, ClaveDelDato.Origen), datos.map { it.clave })
        assertEquals("Bancolombia Ahorros 4057", datos[0].valor)
        assertEquals("Mercado", datos[2].valor)
        assertEquals("Anotado a mano", datos[3].valor)
        // Cada dato que se corrige abre su sección; el origen solo informa.
        assertEquals(AccionDelMovimiento.Nota, datos[0].accion)
        assertEquals(AccionDelMovimiento.Fecha, datos[1].accion)
        assertEquals(AccionDelMovimiento.Categoria, datos[2].accion)
        assertNull(datos[3].accion)
    }

    @Test
    fun `la nota va cuando dice algo distinto del titulo, y las cuotas en una compra diferida`() {
        val datos = datosDeLaFicha(gasto(descripcion = "Mercado del sábado · con Caro"), "Nu", espacio = null, hoy = hoy)
        assertEquals("con Caro", datos.single { it.clave == ClaveDelDato.Nota }.valor)
        // Sin nota, sin el renglón: el concepto ya es el título.
        assertTrue(datosDeLaFicha(gasto(), "Nu", espacio = null, hoy = hoy).none { it.clave == ClaveDelDato.Nota })
        val diferida = datosDeLaFicha(gasto(cuotas = 3), "Nu", espacio = null, hoy = hoy)
        assertEquals("3 cuotas", diferida.single { it.clave == ClaveDelDato.Cuotas }.valor)
        // Sin cuenta conocida, sin el renglón de la cuenta.
        assertTrue(datosDeLaFicha(gasto(), null, espacio = null, hoy = hoy).none { it.clave == ClaveDelDato.Cuenta })
    }

    @Test
    fun `el origen se dice en una palabra o dos`() {
        assertEquals("Aviso del banco", origenDelMovimiento(EventSource.SMS))
        assertEquals("Comprobante", origenDelMovimiento(EventSource.OCR))
        assertEquals("Extracto", origenDelMovimiento(EventSource.STATEMENT))
    }

    @Test
    fun `las acciones de la ficha no repiten categoria ni fecha, y repartir siempre cabe`() {
        val todas = accionesDeLaFicha(ofreceRepetir = true, ofreceRepartir = true, conTercero = true, conMas = true)
        assertEquals(
            listOf(AccionDelMovimiento.Nota, AccionDelMovimiento.Repetir, AccionDelMovimiento.Repartir, AccionDelMovimiento.Tercero, AccionDelMovimiento.Mas),
            todas,
        )
    }

    @Test
    fun `una sola accion amarilla, y ninguna si ya hay otro amarillo`() {
        val acciones = accionesDeLaFicha(ofreceRepetir = true, ofreceRepartir = false, conTercero = false, conMas = true)
        assertEquals(AccionDelMovimiento.Nota, principalDeLaFicha(acciones, porConfirmar = false, otroAmarilloALaVista = false))
        assertNull(principalDeLaFicha(acciones, porConfirmar = true, otroAmarilloALaVista = false), "el amarillo es «Confirmar»")
        assertNull(principalDeLaFicha(acciones, porConfirmar = false, otroAmarilloALaVista = true), "la tarjeta de los avisos")
    }

    // ── 4. Actividad: la lista es la protagonista ─────────────────────────────────

    @Test
    fun `la lista se lleva el 60 por ciento y el detalle nunca baja de 380`() {
        assertEquals(637.8f, anchoDeLaListaDeMovimientos(1_064.dp).value, 0.05f)
        assertEquals(427f, anchoDeLaListaDeMovimientos(808.dp).value, 0.05f)
        assertEquals(733.8f, anchoDeLaListaDeMovimientos(1_224.dp).value, 0.05f)
        // El umbral no cambia: justo ahí, la lista de siempre y el detalle mínimo.
        assertEquals(ANCHO_DE_LA_LISTA_DE_MOVIMIENTOS, anchoDeLaListaDeMovimientos(UMBRAL_DE_MOVIMIENTOS_CON_PANEL))
        listOf(808.dp, 1_064.dp, 1_224.dp, 1_440.dp).forEach { ancho ->
            val lista = anchoDeLaListaDeMovimientos(ancho)
            val detalle = ancho - Medida.trazo - lista
            assertTrue(detalle >= ANCHO_MINIMO_DEL_PANEL_DEL_MOVIMIENTO, "a $ancho el detalle mide $detalle")
            assertTrue(lista > detalle, "a $ancho la lista ($lista) es más ancha que el detalle ($detalle)")
        }
    }

    @Test
    fun `la barra de una categoria es relativa a la mayor`() {
        assertEquals(1f, fraccionDeLaBarraDelResumen(500_000L, 500_000L))
        assertEquals(0.5f, fraccionDeLaBarraDelResumen(250_000L, 500_000L))
        assertEquals(0f, fraccionDeLaBarraDelResumen(250_000L, 0L))
    }

    // ── 3. El Inicio: la banda es una franja a lo ancho ──────────────────────────

    @Test
    fun `la banda del Inicio va a lo ancho, sin muesca`() {
        assertEquals(1_064.dp, anchoDeLaBanda(1_064.dp))
    }

    // ── 5. El barrido: el ancho máximo de lectura ──────────────────────────────

    @Test
    fun `el ancho de lectura es el token`() {
        assertEquals(720.dp, Medida.anchoMaximoDeLectura)
        assertEquals(Medida.anchoMaximoDeLectura, ANCHO_DE_LECTURA)
        assertEquals(520.dp, Medida.anchoDeFicha)
        assertTrue(Medida.anchoDeFicha < Medida.anchoMaximoDeLectura)
    }

    @Test
    fun `una lista de una columna nunca pasa del ancho de lectura`() {
        listOf(WindowWidthClass.Medium, WindowWidthClass.Expanded).forEach { clase ->
            assertEquals(Medida.anchoMaximoDeLectura, anchoMaximoDeLaPantalla(Screen.Buscar, clase), "Buscar en $clase")
        }
        assertEquals(Disposicion.Lectura, disposicionDe(Screen.Buscar))
    }

    @Test
    fun `en dos columnas, ninguna pasa del ancho de lectura`() {
        val tableros = listOf(Screen.Tu, Screen.Espacios, Screen.Credits, Screen.Accounts, Screen.PorRevisar)
        listOf(1_280.dp, 1_440.dp, 1_920.dp).forEach { ventana ->
            tableros.forEach { pantalla ->
                assertEquals(Disposicion.Tablero, disposicionDe(pantalla))
                val columna = (anchoDelPanelEnLaCascara(ventana, pantalla) - RELLENO_DE_DOS_COLUMNAS) / 2
                assertTrue(columna <= Medida.anchoMaximoDeLectura, "$pantalla a $ventana: columnas de $columna")
            }
        }
    }
}
