package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Vuelta 72 · pagos que se manejan solos**, lo puro:
 *
 * - **El mínimo de la tarjeta**: pagada por debajo del mínimo no está hecha («queda 1 de 5», la cola, la
 *   caja y «Falta por pagar» dicen lo que falta); igual o encima, hecha; sin mínimo, como siempre.
 * - **Los umbrales del emparejamiento**: el doble y la mitad se proponen, un peso más allá no.
 * - **La regla pausada fuera del checklist**: lo pendiente desde la pausa no existe; lo pagado queda.
 */
class Vuelta72LogicaTest {

    // ── El mínimo de la tarjeta ──────────────────────────────────────────────

    private fun tarjeta(pagado: Long?, minimo: Long? = 180_000, moneda: String = "COP") = PagoDelPeriodo(
        ruleId = "card_nu", nombre = "Pago tarjeta Nu", monto = 1_583_000, pagado = pagado != null,
        diasParaVencer = 3, montoEsSaldo = true, pagoMinimo = minimo, moneda = moneda,
        montoPagado = pagado, pagadoEnElPeriodo = pagado, vence = "2026-11-05",
    )

    private fun fijo(nombre: String, monto: Long, pagado: Boolean = false, dias: Int = 2) = PagoDelPeriodo(
        ruleId = "rr_$nombre", nombre = nombre, monto = monto, pagado = pagado, diasParaVencer = dias,
        montoPagado = if (pagado) monto else null, vence = "2026-11-01",
    )

    @Test
    fun `debajo del minimo no esta hecho y dice cuanto falta`() {
        val nu = tarjeta(pagado = 100_000)
        assertTrue(nu.pagado, "el movimiento la sigue tildando")
        assertFalse(nu.hecho)
        assertEquals(80_000L, nu.faltaDelMinimo)
        assertEquals(100_000L, nu.pagadoContraElMinimo)
    }

    @Test
    fun `igual o encima del minimo esta hecho`() {
        assertTrue(tarjeta(pagado = 180_000).hecho)
        assertNull(tarjeta(pagado = 180_000).faltaDelMinimo)
        assertTrue(tarjeta(pagado = 1_583_000).hecho)
    }

    @Test
    fun `sin minimo queda como hoy`() {
        val sinMinimo = tarjeta(pagado = 100_000, minimo = null)
        assertTrue(sinMinimo.hecho)
        assertNull(sinMinimo.faltaDelMinimo)
        // Y una tarjeta en dólares no se compara con un mínimo en pesos.
        assertNull(tarjeta(pagado = 100, moneda = "USD").faltaDelMinimo)
        // Sin pagar no hay «falta del mínimo»: es la fila pendiente de siempre.
        assertNull(tarjeta(pagado = null).faltaDelMinimo)
    }

    @Test
    fun `la lista, la caja, la cola y falta por pagar dicen lo mismo`() {
        val checklist = listOf(
            tarjeta(pagado = 100_000),
            fijo("Arriendo", 1_800_000),
            fijo("Celular", 53_000, pagado = true),
        )
        // «Falta por pagar»: el arriendo y lo que le falta al mínimo de la tarjeta.
        assertEquals(1_880_000L, faltaPorPagar(checklist))
        // Pendientes y la cola: la tarjeta sigue ahí, con su cifra en lo que falta.
        assertTrue(pagosPendientes(checklist).any { it.ruleId == "card_nu" })
        val enLaCola = colaDePendientes(checklist).filterIsInstance<Pendiente.Pago>().single { it.pago.ruleId == "card_nu" }
        assertEquals(80_000L, enLaCola.cifra)
        // La caja resta lo que falta del mínimo, no el mínimo entero ni nada.
        val caja = cajaProyectada(
            tuPlataHoy = 3_000_000, pagos = checklist, gastoDiario = null,
            hoy = "2026-10-29", ultimoDia = "2026-11-24",
        )!!
        assertEquals(3_000_000L - 1_800_000L - 80_000L, caja.dias.last().saldo)
    }

    @Test
    fun `una tarjeta con el minimo cubierto no cambia ninguna cifra`() {
        val cubierta = listOf(tarjeta(pagado = 200_000), fijo("Arriendo", 1_800_000))
        assertEquals(1_800_000L, faltaPorPagar(cubierta))
        assertTrue(colaDePendientes(cubierta).filterIsInstance<Pendiente.Pago>().none { it.pago.ruleId == "card_nu" })
    }

    // ── Los umbrales del emparejamiento ──────────────────────────────────────

    private fun candidato(monto: Long, moneda: String = "COP") = FinancialEvent(
        id = "ev", accountId = "acc", type = TransactionType.EXPENSE, amount = monto,
        category = "Carro", description = "Terpel", timestamp = 0L, currency = moneda,
    )

    @Test
    fun `Carro de 9000 contra el tanqueo de 260000`() {
        assertTrue(candidatoFueraDeProporcion(9_000, false, "COP", candidato(260_000)))
    }

    @Test
    fun `el doble y la mitad son el borde`() {
        assertFalse(candidatoFueraDeProporcion(9_000, false, "COP", candidato(18_000)))
        assertTrue(candidatoFueraDeProporcion(9_000, false, "COP", candidato(18_001)))
        assertFalse(candidatoFueraDeProporcion(9_000, false, "COP", candidato(4_500)))
        assertTrue(candidatoFueraDeProporcion(9_000, false, "COP", candidato(4_499)))
    }

    @Test
    fun `sin un monto comparable no se afirma nada`() {
        assertFalse(candidatoFueraDeProporcion(1_583_000, true, "COP", candidato(100)), "una tarjeta: su monto es el saldo")
        assertFalse(candidatoFueraDeProporcion(0, false, "COP", candidato(260_000)), "una regla sin monto")
        assertFalse(candidatoFueraDeProporcion(9_000, false, "COP", candidato(260_000, "USD")), "otra moneda")
    }

    // ── La regla pausada ─────────────────────────────────────────────────────

    private val gym = RecurringRule("rr_gym", "Gimnasio", "Gimnasio", 180_000, 5, TransactionType.EXPENSE)

    @Test
    fun `la pausa mira el vencimiento y el dia de hoy`() {
        val pausada = gym.copy(pausadaDesde = "2026-10-29")
        assertTrue(pausada.pausadaParaElVencimiento("2026-11-05"), "lo que vence después de pausar")
        assertTrue(pausada.pausadaParaElVencimiento("2026-10-05", hoy = "2026-10-29"), "lo vencido sin pagar, mirado hoy")
        assertFalse(pausada.pausadaParaElVencimiento("2026-10-05", hoy = "2026-10-20"), "un período mirado antes de pausar")
        assertFalse(gym.pausadaParaElVencimiento("2026-11-05", hoy = "2026-10-29"), "una regla activa")
        assertFalse(gym.copy(pausadaDesde = "mañana").pausadaParaElVencimiento("2026-11-05"), "una fecha ilegible no pausa")
        assertTrue(pausada.estaPausadaEl("2026-10-29"))
        assertFalse(pausada.estaPausadaEl("2026-10-28"))
    }

    private val periodoOctubre = PeriodoFinanciero(2026, 10)
    private val calendario = PeriodSettings()

    private fun proximo(regla: RecurringRule, vence: String, dias: Int) =
        UpcomingPayment(regla, vence, dias, PaymentStatus.UPCOMING)

    @Test
    fun `una regla pausada sin pagar sale del checklist`() {
        val carro = RecurringRule("rr_carro", "Carro", "Carro", 9_000, 30, TransactionType.EXPENSE, pausadaDesde = "2026-10-29")
        val arriendo = RecurringRule("rr_arriendo", "Arriendo", "Vivienda", 1_800_000, 30, TransactionType.EXPENSE)
        val lista = checklistDelPeriodo(
            upcoming = listOf(proximo(carro, "2026-10-30", 1), proximo(arriendo, "2026-10-30", 1)),
            ocurrencias = emptyList(),
            periodo = periodoOctubre,
            settings = calendario,
        )
        assertEquals(listOf("rr_arriendo"), lista.map { it.ruleId })
    }

    @Test
    fun `lo pagado de una regla pausada queda, con la regla de su ocurrencia`() {
        val pausada = gym.copy(pausadaDesde = "2026-10-29")
        val arriendo = RecurringRule("rr_arriendo", "Arriendo", "Vivienda", 1_800_000, 30, TransactionType.EXPENSE)
        val pagada = OccurrenceState(
            ruleId = "rr_gym", period = "2026-10", dueDate = "2026-10-05", occurred = true,
            eventId = "ev_gym", montoDelPago = 180_000, reglaPausada = pausada,
        )
        val lista = checklistDelPeriodo(
            upcoming = listOf(proximo(arriendo, "2026-10-30", 1)),
            ocurrencias = listOf(pagada),
            periodo = periodoOctubre,
            settings = calendario,
        )
        val fila = lista.single { it.ruleId == "rr_gym" }
        assertTrue(fila.pagado)
        assertEquals("Gimnasio", fila.nombre)
        assertEquals(180_000L, fila.montoPagado)
    }
}
