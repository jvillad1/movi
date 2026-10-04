package com.jvillada.movi.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Hoy avisa las cuentas a las que les envías plata sin nombre** (4-oct-2026) y lleva a «Cuentas
 * de otros». No es urgente: no le gana a lo que vence.
 */
class CuentasSinNombreEnHoyTest {

    private fun cosas(sinNombre: Int, smsPorConfirmar: Int = 0) = cosasParaRevisar(
        checklist = emptyList(), categorias = emptyList(), flujoDelPeriodo = 0,
        smsPorConfirmar = smsPorConfirmar, candidatosAPagoDeTarjeta = 0, cuentasDeOtrosSinNombre = sinNombre,
    )

    @Test
    fun con_sugeridos_avisa_y_lleva_a_cuentas_de_otros() {
        val cosa = cosas(3).single()
        assertEquals("3 cuentas a las que les envías plata no tienen nombre", cosa.texto)
        assertEquals(DestinoDeRevision.CUENTAS_DE_OTROS, cosa.destino)
        assertFalse(cosa.urgente)
        assertEquals("1 cuenta a la que le envías plata no tiene nombre", textoDeCuentasSinNombre(1))
    }

    @Test
    fun sin_sugeridos_no_dice_nada() {
        assertTrue(cosas(0).isEmpty())
    }

    @Test
    fun va_despues_de_lo_urgente() {
        val lista = cosas(2, smsPorConfirmar = 12)
        assertEquals(DestinoDeRevision.POR_REVISAR, lista.first().destino)
        assertEquals(DestinoDeRevision.CUENTAS_DE_OTROS, lista.last().destino)
    }
}
