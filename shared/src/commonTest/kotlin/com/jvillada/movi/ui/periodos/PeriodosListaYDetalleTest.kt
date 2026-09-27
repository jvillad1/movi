package com.jvillada.movi.ui.periodos

import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.WindowWidthClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «Tus períodos» en lista + detalle (Ola W2), en números
 *
 * Qué período muestra el panel de la derecha, qué hace tocar una fila y desde qué anchos hay lugar
 * para la lista al lado del detalle (y para el detalle en dos columnas).
 */
class PeriodosListaYDetalleTest {

    private val septiembre = ResumenDePeriodo(
        id = "2026-09", nombre = "Septiembre 2026", desde = "2026-08-25", hasta = "2026-09-24", enCurso = true,
    )
    private val agosto = ResumenDePeriodo(id = "2026-08", nombre = "Agosto 2026", desde = "2026-07-25", hasta = "2026-08-24")
    private val periodos = listOf(septiembre, agosto)

    // ── La selección inicial ───────────────────────────────────────────────────

    @Test
    fun `sin un id pedido arranca en el periodo en curso`() {
        // El vigente se sabe por el perfil antes de que llegue la lista: el detalle ya puede leerse.
        assertEquals("2026-09", periodoElegido(elegido = null, periodoVigente = "2026-09", periodos = null))
        // Sin perfil todavía, el «En curso» de la lista.
        assertEquals("2026-09", periodoElegido(elegido = null, periodoVigente = null, periodos = periodos))
        // Sin «En curso» en la lista, el primero (el más nuevo).
        assertEquals("2026-08", periodoElegido(elegido = null, periodoVigente = null, periodos = listOf(agosto)))
    }

    @Test
    fun `un id pedido gana la seleccion inicial`() {
        assertEquals("2026-08", periodoElegido(elegido = "2026-08", periodoVigente = "2026-09", periodos = periodos))
        // Aunque la lista todavía no llegó, ni el perfil.
        assertEquals("2026-07", periodoElegido(elegido = "2026-07", periodoVigente = null, periodos = null))
    }

    @Test
    fun `sin nada que saber todavia no hay periodo elegido`() {
        assertNull(periodoElegido(elegido = null, periodoVigente = null, periodos = null))
        assertNull(periodoElegido(elegido = null, periodoVigente = null, periodos = emptyList()))
    }

    // ── Tocar una fila ─────────────────────────────────────────────────────────

    @Test
    fun `con el detalle al lado tocar una fila la elige sin navegar`() {
        assertEquals(AlTocarUnPeriodo.Elegir("2026-08"), alTocarUnPeriodo("2026-08", conDetalleAlLado = true))
    }

    @Test
    fun `en el telefono tocar una fila navega al detalle como siempre`() {
        assertEquals(
            AlTocarUnPeriodo.Navegar(Screen.DetalleDePeriodo("2026-08")),
            alTocarUnPeriodo("2026-08", conDetalleAlLado = false),
        )
    }

    // ── Los anchos ──────────────────────────────────────────────────────────────

    @Test
    fun `la lista mide 360 en mediano y 400 en expandido`() {
        assertEquals(360.dp, anchoDeLaListaDePeriodos(WindowWidthClass.Medium))
        assertEquals(400.dp, anchoDeLaListaDePeriodos(WindowWidthClass.Expanded))
    }

    @Test
    fun `la lista va al lado del detalle solo si al detalle le queda al menos un telefono chico`() {
        // 768 dp de ventana menos el rail compacto de 80: 688. Lista 360 + detalle 327.
        assertTrue(hayLugarParaListaYDetalle(688.dp))
        assertTrue(hayLugarParaListaYDetalle(680.dp))
        // 600 dp de ventana menos el rail: 520. El detalle quedaría de 159 dp: una hoja sola.
        assertFalse(hayLugarParaListaYDetalle(520.dp))
        assertFalse(hayLugarParaListaYDetalle(679.dp))
    }

    @Test
    fun `el detalle se parte en dos columnas desde 840 dp de panel`() {
        assertTrue(detalleEnDosColumnas(840.dp))
        assertTrue(detalleEnDosColumnas(1_040.dp))
        assertFalse(detalleEnDosColumnas(839.dp))
        assertFalse(detalleEnDosColumnas(680.dp))
    }
}
