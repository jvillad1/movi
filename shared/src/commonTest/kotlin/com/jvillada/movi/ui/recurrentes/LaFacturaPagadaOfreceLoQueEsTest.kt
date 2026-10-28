package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.EstadoDeLaFila
import com.jvillada.movi.shared.model.PagoDelPeriodo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Vuelta 45 · una factura pagada desde «Anotar pago» no ofrece «No fue este».** La fila pagada
 * ofrecía «No fue este» como si Movi la hubiera emparejado. Ahora lo que ofrece depende de quién la dio
 * por pagada: lo que Movi emparejó solo se discute («No fue este»); lo que selló el dueño —«Anotar pago»
 * o «Sí, fue este»— se ve o se desmarca («Ver movimiento», «Quitar la marca»).
 */
class LaFacturaPagadaOfreceLoQueEsTest {

    private val base = PagoDelPeriodo(
        ruleId = "rr_arriendo", nombre = "Arriendo", monto = 1_800_000, pagado = true, diasParaVencer = -23,
        vence = "2026-10-05", periodoDelSello = "2026-10",
    )

    @Test
    fun `lo que Movi empareja solo ofrece No fue este`() {
        val emparejada = base.copy(automatica = true, derivado = true, eventId = "ev_1", pagadoEl = "2026-10-06")
        assertEquals(EstadoDeLaFila.LISTO, emparejada.estado)
        assertEquals(listOf(AccionDeLaFilaPagada.NO_FUE_ESTE), accionesDeLaFilaPagada(emparejada))
    }

    @Test
    fun `lo que sello el duenno con Anotar pago o Si fue este ofrece Ver movimiento y Quitar la marca`() {
        // «Anotar pago» y «Sí, fue este» sellan igual: un sello con movimiento, ni derivado ni automático.
        val sellada = base.copy(eventId = "ev_pago")
        assertEquals(EstadoDeLaFila.LISTO, sellada.estado)
        assertEquals(
            listOf(AccionDeLaFilaPagada.VER_MOVIMIENTO, AccionDeLaFilaPagada.QUITAR_LA_MARCA),
            accionesDeLaFilaPagada(sellada),
        )
    }

    @Test
    fun `la cuota probada por su pago y lo pendiente no ofrecen nada de esto`() {
        val cuota = base.copy(ruleId = "credit_1", derivado = true, eventId = "ev_cuota")
        assertEquals(emptyList(), accionesDeLaFilaPagada(cuota))
        val pendiente = base.copy(pagado = false)
        assertEquals(emptyList(), accionesDeLaFilaPagada(pendiente))
        // El sello viejo sin movimiento sigue con su «Quitar la marca», en su propio estado.
        val aMano = base.copy(eventId = null)
        assertEquals(EstadoDeLaFila.MARCADA_A_MANO, aMano.estado)
        assertEquals(emptyList(), accionesDeLaFilaPagada(aMano))
    }
}
