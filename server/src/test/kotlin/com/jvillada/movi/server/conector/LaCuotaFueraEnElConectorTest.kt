package com.jvillada.movi.server.conector

import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.faltaPorPagar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 141 · el conector marca la cuota que sale de fuera de Tu plata
 *
 * `pagos_del_periodo` lista la cuota de la Hipoteca 1254 (la paga la AFC) pero no la suma en `faltaPorPagar`:
 * sin la marca `noTocaTuPlata`, quien sume los montos pendientes de la lista no llega al total que dice.
 */
class LaCuotaFueraEnElConectorTest {

    private val arriendo = PagoDelPeriodo(ruleId = "rr_arriendo", nombre = "Arriendo", monto = 1_800_000L, pagado = false, diasParaVencer = 3, vence = "2026-11-10")
    private val laCuota = PagoDelPeriodo(
        ruleId = "credit_acc_1254", nombre = "Cuota Hipoteca 1254", monto = 9_145_256L, pagado = false, diasParaVencer = 1,
        vence = "2026-11-05", sePagaDesde = "acc_afc", saleDeFueraDeTuPlata = true,
    )

    @Test
    fun `la fila de la AFC va marcada y el total cuadra con las que no lo estan`() {
        val pagos = listOf(arriendo, laCuota).map(::pagoDelConector)
        assertTrue(pagos.single { it.nombre == "Cuota Hipoteca 1254" }.noTocaTuPlata)
        assertFalse(pagos.single { it.nombre == "Arriendo" }.noTocaTuPlata)
        val sumados = pagos.filter { it.estado != "pagado" && !it.noTocaTuPlata }.sumOf { it.monto }
        assertEquals(faltaPorPagar(listOf(arriendo, laCuota)), sumados)
        // Y viaja en el JSON (el conector codifica los valores por defecto).
        assertTrue("\"noTocaTuPlata\":true" in jsonDelConector.encodeToString(PagoDelConector.serializer(), pagos[1]))
    }
}
