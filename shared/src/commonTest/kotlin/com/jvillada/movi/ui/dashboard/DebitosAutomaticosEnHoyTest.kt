package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.OrigenDelDebito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Hoy dice lo que el banco debió cobrar solo** (hueco de #440): con débitos por confirmar, «Para
 * revisar» lleva a «Por revisar»; sin ellos, no dice nada. Y la cuota que ya tiene su fila de débito
 * no se repite como «venció y no está marcado».
 */
class DebitosAutomaticosEnHoyTest {

    private fun debito(ruleId: String, nombre: String, origen: OrigenDelDebito = OrigenDelDebito.CUOTA_DE_CREDITO) =
        DebitoAutomaticoPorConfirmar(
            ruleId = ruleId, periodo = "2026-10", origen = origen, nombre = nombre, monto = 1_204_064L,
            vence = "2026-10-05", cuentaId = "acc-ahorros", cuentaNombre = "Bancolombia Ahorros",
            categoria = "Cuota de crédito", pataDelDineroId = "ev_deb_$ruleId",
        )

    private val libre = debito("credit_9695", "Cuota Libre inversión 9695")

    private fun cosas(
        debitos: List<DebitoAutomaticoPorConfirmar>,
        checklist: List<PagoDelPeriodo> = emptyList(),
    ) = cosasParaRevisar(
        checklist = checklist, categorias = emptyList(), flujoDelPeriodo = 0,
        smsPorConfirmar = 0, candidatosAPagoDeTarjeta = 0, debitosPorConfirmar = debitos,
    )

    @Test
    fun con_un_debito_por_confirmar_avisa_y_lleva_a_por_revisar() {
        val cosa = cosas(listOf(libre)).single()
        assertEquals("¿Se cobró la cuota de Libre inversión 9695?", cosa.texto)
        assertEquals(DestinoDeRevision.POR_REVISAR, cosa.destino)
        assertTrue(cosa.urgente, "es plata que probablemente ya salió")
    }

    @Test
    fun con_varios_los_cuenta() {
        val seguro = debito("rule-seguro", "Seguro del carro", OrigenDelDebito.RECURRENTE)
        assertEquals("2 débitos automáticos por confirmar", cosas(listOf(libre, seguro)).single().texto)
    }

    @Test
    fun sin_debitos_no_dice_nada() {
        assertTrue(cosas(emptyList()).isEmpty())
    }

    @Test
    fun la_cuota_vencida_con_debito_no_se_repite_pero_otra_vencida_si() {
        val cuotaVencida = PagoDelPeriodo("credit_9695", "Cuota Libre inversión 9695", 1_204_064L, pagado = false, diasParaVencer = -1)
        val otraVencida = PagoDelPeriodo("rule-celular", "Celular", 53_000L, pagado = false, diasParaVencer = -2)
        val lista = cosas(listOf(libre), checklist = listOf(cuotaVencida, otraVencida))
        assertEquals(DestinoDeRevision.POR_REVISAR, lista.first().destino)
        assertEquals("«Celular» venció y no está marcado", lista[1].texto)
        assertEquals(2, lista.size)
    }

    @Test
    fun el_inicio_lo_toma_de_lo_que_llego_aparte() {
        assertTrue(cosasParaRevisarDe(DashboardData()).none { it.destino == DestinoDeRevision.POR_REVISAR })
        val conDebito = cosasParaRevisarDe(DashboardData(debitosPorConfirmar = listOf(libre)))
        assertEquals("¿Se cobró la cuota de Libre inversión 9695?", conDebito.single { it.destino == DestinoDeRevision.POR_REVISAR }.texto)
    }
}
