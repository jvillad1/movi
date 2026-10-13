package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.ui.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **El aviso «Cerró tu período»**, decidido sin red: una vez por período, solo los primeros días del
 * período nuevo, y tocarlo abre el detalle del período que cerró.
 */
class AvisoDeCierreTest {

    private val septiembre = CierreDelPeriodo(
        periodo = "2026-09", nombre = "Septiembre 2026", entro = 22_500_000, salio = 18_200_000,
        pagosEsperados = 3, pagosCumplidos = 2, pagosQueFaltaron = listOf("Gimnasio"), diaDelPeriodoNuevo = 1,
    )

    @Test
    fun `el dia siguiente al corte se avisa`() {
        assertEquals(septiembre, cierreParaAvisar(septiembre, yaAvisado = null))
        // Otro período ya avisado no cuenta.
        assertEquals(septiembre, cierreParaAvisar(septiembre, yaAvisado = "2026-08"))
    }

    @Test
    fun `el mismo periodo no suena dos veces`() {
        assertNull(cierreParaAvisar(septiembre, yaAvisado = "2026-09"))
    }

    @Test
    fun `con el telefono apagado avisa los primeros tres dias y despues ya no`() {
        assertEquals(septiembre.copy(diaDelPeriodoNuevo = 3), cierreParaAvisar(septiembre.copy(diaDelPeriodoNuevo = 3), null))
        assertNull(cierreParaAvisar(septiembre.copy(diaDelPeriodoNuevo = 4), null))
    }

    @Test
    fun `sin cierre o descartado no se avisa`() {
        assertNull(cierreParaAvisar(null, null))
        assertNull(cierreParaAvisar(septiembre.copy(descartado = true), null))
    }

    @Test
    fun `el texto es el del resumen`() {
        val texto = textoDelAvisoDeCierre(septiembre)
        assertEquals("Cerró tu período de septiembre", texto.titulo)
        assertEquals("Cerró tu período: entró \$22.500.000, salió \$18.200.000", texto.texto)
        assertEquals(listOf("Pagos: cumpliste 2 de 3 · faltó Gimnasio"), texto.lineas)
    }

    @Test
    fun `tocarlo abre el detalle del periodo`() {
        assertEquals("periodo:2026-09", abrirElPeriodo("2026-09"))
        assertEquals(Screen.DetalleDePeriodo("2026-09"), destinoDeAviso(abrirElPeriodo("2026-09")))
        assertNull(destinoDeAviso("periodo:no-es"))
        assertNull(destinoDeAviso("otra cosa"))
    }

    @Test
    fun `la hora es la mañana siguiente al corte`() {
        assertEquals(9, HORA_DEL_AVISO_DE_CIERRE)
    }
}
