package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.yaPagadoEnElPeriodo
import com.jvillada.movi.ui.plan.ROTULO_SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.TITULO_DE_PAGOS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * # Tus facturas del período (vuelta 40)
 *
 * La tarjeta amarilla sale de las funciones de siempre del checklist; la lista va en el orden de la
 * opción 3 (lo vencido, lo que vence por fecha, lo pagado al final); cada factura dice cuándo vence en
 * su pastilla y, pagada, a qué movimiento lleva.
 */
class FacturasDelPeriodoTest {

    private fun pago(
        id: String,
        monto: Long,
        dias: Int,
        vence: String,
        pagado: Boolean = false,
        esIngreso: Boolean = false,
        eventId: String? = null,
        pagadoEl: String? = null,
        montoPagado: Long? = null,
    ) = PagoDelPeriodo(
        ruleId = id, nombre = id, monto = monto, pagado = pagado, diasParaVencer = dias, vence = vence,
        esIngreso = esIngreso, eventId = eventId, pagadoEl = pagadoEl, montoPagado = montoPagado,
    )

    // Hoy: jueves 29 de octubre de 2026.
    private val arriendo = pago("Arriendo", 1_850_000, dias = 4, vence = "2026-11-02")
    private val internet = pago("Internet", 145_000, dias = 5, vence = "2026-11-03")
    private val celular = pago("Celular", 53_000, dias = -2, vence = "2026-10-27")
    private val gimnasio = pago("Gimnasio", 180_000, dias = 2, vence = "2026-10-31")
    private val coomeva = pago(
        "Coomeva", 138_600, dias = -4, vence = "2026-10-25", pagado = true,
        eventId = "ev_coomeva", pagadoEl = "2026-10-25", montoPagado = 138_600,
    )
    private val sueldo = pago("Sueldo", 6_200_000, dias = 0, vence = "2026-10-29", esIngreso = true)
    private val checklist = listOf(arriendo, internet, celular, gimnasio, coomeva, sueldo)

    @Test
    fun `la tarjeta amarilla dice las cifras de siempre del checklist`() {
        val resumen = resumenDeFacturas(checklist)
        assertEquals(faltaPorPagar(checklist), resumen.falta)
        assertEquals(1_850_000L + 145_000L + 53_000L + 180_000L, resumen.falta)
        assertEquals(yaPagadoEnElPeriodo(checklist), resumen.pagado)
        assertEquals(138_600L, resumen.pagado)
        assertEquals(resumen.falta + resumen.pagado, resumen.total)
        // El sueldo no es una factura: no cuenta en «quedan N de M».
        assertEquals(4, resumen.quedan)
        assertEquals(5, resumen.de)
        assertEquals("de \$2.366.600 · quedan 4 de 5 pagos", lineaDeLoQueQueda(resumen))
        assertEquals("Llevas \$138.600 pagados", lineaDeLoPagado(resumen))
        assertEquals(138_600f / 2_366_600f, resumen.fraccionPagada, 0.0001f)
    }

    @Test
    fun `primero lo vencido, despues lo que vence por fecha y lo pagado al final`() {
        val orden = enOrdenDeFacturas(listOf(arriendo, coomeva, internet, celular, gimnasio))
        assertEquals(listOf("Celular", "Gimnasio", "Arriendo", "Internet", "Coomeva"), orden.map { it.nombre })
        // El «Anotar pago» amarillo es el del primero sin pagar.
        assertEquals("Celular", laMasUrgente(orden)?.nombre)
        assertNull(laMasUrgente(listOf(coomeva)))
    }

    @Test
    fun `la pastilla dice cuando vence, en aviso lo urgente y neutra lo demas`() {
        assertEquals("Venció hace 2 días" to TonoDePildora.Aviso, pastillaDeVencimiento(celular, conPropuesta = false))
        // 31 de octubre de 2026: sábado.
        assertEquals("Vence el sábado" to TonoDePildora.Aviso, pastillaDeVencimiento(gimnasio, conPropuesta = false))
        assertEquals("Vence el 2 nov" to TonoDePildora.Neutra, pastillaDeVencimiento(arriendo, conPropuesta = false))
        assertEquals("Vence hoy" to TonoDePildora.Aviso, pastillaDeVencimiento(arriendo.copy(diasParaVencer = 0), conPropuesta = false))
        assertEquals("Llega hoy" to TonoDePildora.Aviso, pastillaDeVencimiento(sueldo, conPropuesta = false))
        // Cuando Movi pregunta «¿fue este?», eso es lo que dice.
        assertEquals("Por confirmar" to TonoDePildora.Marca, pastillaDeVencimiento(arriendo, conPropuesta = true))
    }

    @Test
    fun `una factura pagada lleva al movimiento que la pago`() {
        assertEquals("Movimiento del 25 oct", enlaceDelMovimiento(coomeva))
        // Un sello viejo sin movimiento no tiene a dónde llevar; una sin pagar tampoco.
        assertNull(enlaceDelMovimiento(coomeva.copy(eventId = null)))
        assertNull(enlaceDelMovimiento(arriendo))
    }

    @Test
    fun `Ver los N pagos del periodo`() {
        assertEquals("Ver los 7 pagos del período", textoDeVerTodas(7))
    }

    /** Vuelta 40: el título de la pestaña es «Pagos» y el segmento ya no lo repite. */
    @Test
    fun `el segmentado ya no repite Pagos`() {
        assertEquals("Pagos", TITULO_DE_PAGOS)
        assertEquals("Facturas", ROTULO_SEGMENTO_PAGOS)
        assertNotEquals(TITULO_DE_PAGOS, ROTULO_SEGMENTO_PAGOS)
    }
}
