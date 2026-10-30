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

    // ── El mínimo de la tarjeta (revisión: el del extracto, en su fecha de pago) ──

    private val nuId = "card_nu"

    /** La tarjeta Nu: corte el 20-oct, paga el 5-nov, mínimo $180.000, y lo pagado desde el corte. */
    private fun reglaNu(pagadoDesdeElCorte: Long) = RecurringRule(
        nuId, "Pago tarjeta Nu", "Cuota de crédito", 1_583_000, 5, TransactionType.EXPENSE,
        montoEsSaldo = true, pagoMinimoCop = 180_000,
        minimoDelExtracto = MinimoDelExtracto("2026-11-05", 180_000, pagadoDesdeElCorte),
    )

    private fun fijo(nombre: String, monto: Long, pagado: Boolean = false, dias: Int = 2, vence: String = "2026-11-01") = PagoDelPeriodo(
        ruleId = "rr_$nombre", nombre = nombre, monto = monto, pagado = pagado, diasParaVencer = dias,
        montoPagado = if (pagado) monto else null, vence = vence,
    )

    /** El checklist del período que lleva al 5-nov (corte 25: del 25-oct al 24-nov), con la fila de la tarjeta. */
    private fun conLaTarjeta(pagadoDesdeElCorte: Long, ocurrencia: OccurrenceState? = null): List<PagoDelPeriodo> =
        checklistDelPeriodo(
            upcoming = listOf(UpcomingPayment(reglaNu(pagadoDesdeElCorte), "2026-11-05", 7, PaymentStatus.UPCOMING)),
            ocurrencias = listOfNotNull(ocurrencia),
            periodo = PeriodoFinanciero(2026, 11),
            settings = PeriodSettings(cutoffDay = 25),
        )

    @Test
    fun `debajo del minimo la fila del extracto no esta hecha y dice cuanto falta`() {
        // El pago de $100.000 del 28-oct (después del corte) tilda la fila del 5-nov.
        val tilde = OccurrenceState(nuId, "2026-11", "2026-11-05", occurred = true, eventId = "ev_100", derivadaDeUnMovimiento = true, montoDelPago = 100_000)
        val nu = conLaTarjeta(100_000, tilde).single()
        assertTrue(nu.pagado, "el movimiento la sigue tildando")
        assertFalse(nu.hecho)
        assertEquals(80_000L, nu.faltaDelMinimo)
        assertEquals(80_000L, nu.pagoMinimo, "lo que resta la caja y dice la cola")
        assertEquals(100_000L, nu.pagadoContraElMinimo)
        assertEquals(7, nu.diasParaVencer, "la urgencia sale de la fecha de pago del extracto")
    }

    @Test
    fun `igual o encima del minimo esta hecha, con o sin un pago que la tilde`() {
        assertTrue(conLaTarjeta(180_000).single().hecho)
        assertNull(conLaTarjeta(180_000).single().faltaDelMinimo)
        assertTrue(conLaTarjeta(1_583_000).single().hecho)
    }

    @Test
    fun `sin pagos desde el corte es la fila pendiente de siempre, con el minimo entero`() {
        val nu = conLaTarjeta(0).single()
        assertFalse(nu.hecho)
        assertNull(nu.faltaDelMinimo)
        assertEquals(180_000L, nu.pagoMinimo)
    }

    @Test
    fun `sin minimo, o en otra fila de la tarjeta, queda como hoy`() {
        val sinMinimo = PagoDelPeriodo(
            ruleId = nuId, nombre = "Nu", monto = 1_583_000, pagado = true, diasParaVencer = -20,
            montoEsSaldo = true, montoPagado = 100_000, vence = "2026-10-05",
        )
        assertTrue(sinMinimo.hecho)
        assertNull(sinMinimo.faltaDelMinimo)
        // La fila de octubre (vence el 5-oct) no es la del extracto: el pago de $100.000 la deja hecha.
        val octubre = checklistDelPeriodo(
            upcoming = listOf(UpcomingPayment(reglaNu(100_000), "2026-11-05", 7, PaymentStatus.UPCOMING)),
            ocurrencias = listOf(OccurrenceState(nuId, "2026-10", "2026-10-05", occurred = true, eventId = "ev_100", derivadaDeUnMovimiento = true, montoDelPago = 100_000)),
            periodo = PeriodoFinanciero(2026, 10),
            settings = PeriodSettings(),
        ).single()
        assertTrue(octubre.hecho, "ningún «Pago vencido · hace 24 días»")
        assertNull(octubre.faltaDelMinimo)
        assertTrue(colaDePendientes(listOf(octubre)).isEmpty())
    }

    @Test
    fun `la caja resta lo que falta del minimo una vez, en la fecha de pago, y nada al pagarlo`() {
        val tilde = OccurrenceState(nuId, "2026-11", "2026-11-05", occurred = true, eventId = "ev_100", derivadaDeUnMovimiento = true, montoDelPago = 100_000)
        val conFalta = conLaTarjeta(100_000, tilde) + fijo("Arriendo", 1_800_000, vence = "2026-11-01")
        val caja = cajaProyectada(tuPlataHoy = 3_000_000, pagos = conFalta, gastoDiario = null, hoy = "2026-10-29", ultimoDia = "2026-11-24")!!
        val salidas = caja.dias.flatMap { d -> d.movimientos.map { d.fecha to it } }.filter { it.second.nombre == "Pago tarjeta Nu" }
        assertEquals(listOf("2026-11-05" to -80_000L), salidas.map { it.first to it.second.monto })
        assertEquals(3_000_000L - 1_800_000L - 80_000L, caja.dias.last().saldo)
        // La lista, la cola y «Falta por pagar» dicen lo mismo.
        assertEquals(1_880_000L, faltaPorPagar(conFalta))
        val enLaCola = colaDePendientes(conFalta, diasPorVencer = 10).filterIsInstance<Pendiente.Pago>().single { it.pago.ruleId == nuId }
        assertEquals(80_000L, enLaCola.cifra)
        assertEquals(UrgenciaDelPendiente.POR_VENCER, enLaCola.urgencia)

        // Pagado completo ($180.000 desde el corte): no queda nada en la caja, ni en «Lo que viene».
        val cubierta = conLaTarjeta(180_000, tilde)
        val cajaCubierta = cajaProyectada(tuPlataHoy = 3_000_000, pagos = cubierta, gastoDiario = null, hoy = "2026-10-29", ultimoDia = "2026-11-24")!!
        assertTrue(cajaCubierta.dias.none { d -> d.movimientos.any { it.nombre == "Pago tarjeta Nu" } })
        assertTrue(pagosPendientes(cubierta).isEmpty())
        assertEquals(0L, loQueSaleDe(cubierta))
    }

    @Test
    fun `una tarjeta con el minimo cubierto no cambia ninguna cifra`() {
        val cubierta = conLaTarjeta(200_000) + fijo("Arriendo", 1_800_000)
        assertEquals(1_800_000L, faltaPorPagar(cubierta))
        assertTrue(colaDePendientes(cubierta).filterIsInstance<Pendiente.Pago>().none { it.pago.ruleId == nuId })
    }

    // ── Re-revisión: el mínimo efectivo, la tarjeta sin extracto y «Falta por pagar» ──

    private fun extracto(facturado: Long, pagado: Long, minimo: Long? = 180_000) = ExtractoDeLaTarjeta(
        corte = "2026-10-20", fechaDePago = "2026-11-05", facturado = facturado, pagadoDesdeElCorte = pagado, minimo = minimo,
    )

    @Test
    fun `un minimo tecleado mayor que lo facturado se salda con lo facturado`() {
        // Facturado $60.000 al corte y un pago de $200.000: `pagadoDesdeElCorte` ya viene topado en $60.000.
        val pagado = extracto(facturado = 0, pagado = 60_000)
        assertEquals(60_000L, pagado.minimoEfectivo)
        assertEquals(0L, pagado.minimoQueFalta)
        // Sin pagar todavía: el mínimo a pagar es lo facturado, no los $180.000 tecleados.
        assertEquals(60_000L, extracto(facturado = 60_000, pagado = 0).minimoQueFalta)
    }

    @Test
    fun `con lo facturado en cero no se pide minimo`() {
        val enCero = extracto(facturado = 0, pagado = 0)
        assertEquals(0L, enCero.minimoEfectivo)
        assertEquals(0L, enCero.minimoQueFalta)
        assertNull(extracto(facturado = 60_000, pagado = 0, minimo = null).minimoQueFalta)
    }

    @Test
    fun `sin extracto vale la regla de la primera ronda`() {
        // Una tarjeta sin día de corte: lo pagado en el período contra su mínimo.
        val sinCorte = PagoDelPeriodo(
            ruleId = nuId, nombre = "Pago tarjeta Nu", monto = 1_583_000, pagado = true, diasParaVencer = 7,
            montoEsSaldo = true, pagoMinimo = 180_000, montoPagado = 100_000, pagadoEnElPeriodo = 100_000, vence = "2026-11-05",
        )
        assertFalse(sinCorte.hecho)
        assertEquals(80_000L, sinCorte.faltaDelMinimo)
        assertEquals(100_000L, sinCorte.pagadoContraElMinimo)
        val caja = cajaProyectada(tuPlataHoy = 1_000_000, pagos = listOf(sinCorte), gastoDiario = null, hoy = "2026-10-29", ultimoDia = "2026-11-24")!!
        assertEquals(1_000_000L - 80_000L, caja.dias.last().saldo)
        assertEquals(80_000L, faltaPorPagar(listOf(sinCorte)))
        assertTrue(sinCorte.copy(pagadoEnElPeriodo = 180_000).hecho)
        // Con el mínimo de un extracto en la regla, otra fila de la tarjeta queda como siempre (`pagado`).
        assertTrue(sinCorte.copy(minimoDelExtractoEnLaRegla = true).hecho)
    }

    @Test
    fun `Falta por pagar no sube al abonar a la tarjeta`() {
        val arriendo = fijo("Arriendo", 1_800_000, vence = "2026-11-01")
        val sinPagos = conLaTarjeta(0) + arriendo
        assertEquals(1_980_000L, faltaPorPagar(sinPagos), "el mínimo entero suma, como lo resta la caja")
        val tilde = OccurrenceState(nuId, "2026-11", "2026-11-05", occurred = true, eventId = "ev_100", derivadaDeUnMovimiento = true, montoDelPago = 100_000)
        val conAbono = conLaTarjeta(100_000, tilde) + arriendo
        assertEquals(1_880_000L, faltaPorPagar(conAbono), "con el abono, lo que falta")
        assertEquals(1_800_000L, faltaPorPagar(conLaTarjeta(180_000, tilde) + arriendo), "cubierto, nada")
    }

    @Test
    fun `lo pagado se muestra y se suma por el total del periodo`() {
        val dosPagos = OccurrenceState(nuId, "2026-10", "2026-10-05", occurred = true, eventId = "ev_80", derivadaDeUnMovimiento = true, montoDelPago = 80_000, pagadoEnElPeriodo = 180_000)
        val fila = checklistDelPeriodo(
            upcoming = listOf(UpcomingPayment(reglaNu(180_000), "2026-11-05", 7, PaymentStatus.UPCOMING)),
            ocurrencias = listOf(dosPagos),
            periodo = PeriodoFinanciero(2026, 10),
            settings = PeriodSettings(),
        ).single()
        assertEquals(180_000L, fila.pagadoParaMostrar)
        assertEquals(80_000L, fila.montoPagado, "el último pago sigue igual (lo leen los fijos)")
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
    fun `la pausa rige desde su dia y lo vencido antes sigue pendiente`() {
        val pausada = gym.copy(pausadaDesde = "2026-10-29")
        assertTrue(pausada.pausadaParaElVencimiento("2026-11-05"), "lo que vence después de pausar")
        assertTrue(pausada.pausadaParaElVencimiento("2026-10-29"), "el mismo día de la pausa")
        assertFalse(pausada.pausadaParaElVencimiento("2026-10-05"), "lo vencido antes de pausar sigue pendiente")
        assertFalse(gym.pausadaParaElVencimiento("2026-11-05"), "una regla activa")
        assertFalse(gym.copy(pausadaDesde = "mañana").pausadaParaElVencimiento("2026-11-05"), "una fecha ilegible no pausa")
        assertTrue(pausada.estaPausadaEl("2026-10-29"))
        assertFalse(pausada.estaPausadaEl("2026-10-28"))
    }

    @Test
    fun `lo vencido sin pagar de una regla pausada sigue en la lista`() {
        val carro = RecurringRule("rr_carro", "Carro", "Carro", 9_000, 20, TransactionType.EXPENSE, pausadaDesde = "2026-10-29")
        val abierta = OccurrenceState(ruleId = "rr_carro", period = "2026-10", dueDate = "2026-10-20", occurred = false, reglaPausada = carro)
        val lista = checklistDelPeriodo(upcoming = emptyList(), ocurrencias = listOf(abierta), periodo = periodoOctubre, settings = calendario)
        val fila = lista.single()
        assertFalse(fila.pagado)
        assertTrue(fila.pausada)
        assertEquals(9_000L, faltaPorPagar(lista))
        // «No se debe»: la pausa arranca en ese vencimiento y sale.
        val noSeDebe = abierta.copy(reglaPausada = carro.copy(pausadaDesde = "2026-10-20"))
        assertTrue(checklistDelPeriodo(emptyList(), listOf(noSeDebe), periodoOctubre, calendario).isEmpty())
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
