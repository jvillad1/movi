package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.CUERPO_DEL_EXTRACTO_POR_SUBIR
import com.jvillada.movi.shared.model.ExtractoPorSubir
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vuelta 94 · **Hoy pide el extracto que falta** en «Movi propone hoy»: la pregunta, «Subir el
 * extracto» (la subida de siempre) y «Ya no» (descarta esa tarjeta y ese corte, con la tabla de
 * descartes de las anomalías). Sin extractos que falten, nada.
 */
class ExtractoPorSubirEnHoyTest {

    private val nu = ExtractoPorSubir("acc_nu", "Tarjeta Nu", "Nu", "2026-10-12")
    private val master = ExtractoPorSubir("acc_master", "Master Black 3684", "Bancolombia", "2026-10-20")

    @AfterTest
    fun limpiar() {
        AnomaliasDescartadasEnLaSesion.deshacer(nu.huella)
    }

    private fun propuesta(extractos: List<ExtractoPorSubir>?) =
        propuestasDelInicio(DashboardData(extractosPorSubir = extractos)).filter { it.tipo == TipoDePropuesta.EXTRACTO }

    @Test
    fun con_un_extracto_que_falta_lo_pide_con_sus_dos_botones() {
        val p = propuesta(listOf(nu)).single()
        assertEquals("¿Ya bajaste el extracto de Nu de octubre?", p.titulo)
        assertEquals(CUERPO_DEL_EXTRACTO_POR_SUBIR, p.cuerpo)
        assertEquals(SUBIR_EL_EXTRACTO, p.accion)
        assertEquals(AccionDeLaPropuesta.SubirElExtracto, p.alTocar)
        assertEquals(YA_NO, p.accionSecundaria)
        assertEquals(AccionDeLaPropuesta.DescartarAnomalia("extracto:acc_nu:2026-10-12"), p.alTocarSecundaria)
        assertEquals(1, p.cuantas)
    }

    @Test
    fun con_varios_una_tarjeta_que_los_cuenta() {
        val p = propuesta(listOf(nu, master)).single()
        assertEquals(2, p.cuantas)
        assertTrue(p.cuerpo!!.endsWith("Y falta 1 extracto más."), p.cuerpo)
    }

    @Test
    fun sin_extractos_o_sin_respuesta_no_dice_nada() {
        assertTrue(propuesta(emptyList()).isEmpty())
        assertTrue(propuesta(null).isEmpty())
    }

    @Test
    fun ya_no_lo_saca_en_el_mismo_toque() {
        AnomaliasDescartadasEnLaSesion.descartar(nu.huella)
        assertTrue(propuesta(listOf(nu)).isEmpty())
    }
}
