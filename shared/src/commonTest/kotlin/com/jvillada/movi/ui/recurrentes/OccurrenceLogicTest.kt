package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OccurrenceLogicTest {

    private fun evento(id: String, description: String = "Salario", amount: Long = 5_000_000) =
        FinancialEvent(
            id = id,
            accountId = "acc_1",
            type = TransactionType.INCOME,
            amount = amount,
            category = "Salario",
            description = description,
            timestamp = 1_756_000_000_000,
        )

    private fun estado(
        occurred: Boolean = false,
        eventId: String? = null,
        candidates: List<FinancialEvent> = emptyList(),
    ) = OccurrenceState(
        ruleId = "rr_1",
        period = "2026-08",
        dueDate = "2026-08-25",
        occurred = occurred,
        eventId = eventId,
        candidates = candidates,
    )

    /** La fila del checklist con los candidatos de [e], para probar [propuestaDeLaFila]. */
    private fun fila(e: OccurrenceState) = PagoDelPeriodo(
        ruleId = e.ruleId, nombre = "Salario", monto = 5_000_000, pagado = false, diasParaVencer = -1,
        periodoDelSello = e.period, candidatos = e.candidates,
    )

    /** La media frase de evidencia de una ocurrencia ya dada por ocurrida. */
    private fun origen(e: OccurrenceState) = origenDeLoOcurrido(
        automatica = e.automatica,
        derivada = e.derivadaDeUnMovimiento,
        hayMovimiento = e.eventId != null,
        monto = e.montoDelPago,
        moneda = e.monedaDelPago,
    )

    @Test fun `no fue este pasa a la siguiente propuesta`() {
        val e = estado(candidates = listOf(evento("ev_1"), evento("ev_2")))
        assertEquals("ev_1", propuestaDeLaFila(fila(e), emptySet())?.id)
        assertEquals("ev_2", propuestaDeLaFila(fila(e), setOf(claveDescartada("rr_1", "ev_1")))?.id)
        assertNull(
            propuestaDeLaFila(
                fila(e),
                setOf(claveDescartada("rr_1", "ev_1"), claveDescartada("rr_1", "ev_2")),
            ),
        )
    }

    /**
     * Regresión del hallazgo MEDIA-2: el descarte estaba indexado por id de EVENTO, así que
     * rechazar una propuesta en una regla se la quitaba a todas. Con «Agua», «Gas» e «Internet»
     * todas en «Servicios», el mismo pago del gas era la primera propuesta de las tres: decir «no
     * fue este» en Agua le borraba a Gas su candidato correcto.
     */
    @Test fun `no fue este solo afecta a la regla donde se dijo`() {
        val elPagoDelGas = evento("ev_gas")
        val agua = estado(candidates = listOf(elPagoDelGas)).copy(ruleId = "rr_agua")
        val gas = estado(candidates = listOf(elPagoDelGas)).copy(ruleId = "rr_gas")
        val rechazadoEnAgua = setOf(claveDescartada("rr_agua", "ev_gas"))

        assertNull(propuestaDeLaFila(fila(agua), rechazadoEnAgua), "en Agua ya se dijo que no")
        assertEquals(
            "ev_gas",
            propuestaDeLaFila(fila(gas), rechazadoEnAgua)?.id,
            "en Gas sigue siendo el candidato correcto",
        )
    }

    @Test fun `el idioma sigue al tipo del recurrente, y dice de que mes habla`() {
        assertEquals("¿Ya te llegó el de agosto?", tituloPropuesta(TransactionType.INCOME, "2026-08"))
        assertEquals("¿Ya pagaste el de agosto?", tituloPropuesta(TransactionType.EXPENSE, "2026-08"))
    }

    @Test fun `un periodo ilegible no imprime un numero crudo`() {
        // Nunca «el de 13» ni «el de null»: si no se entiende el periodo, la frase se acorta.
        assertEquals("¿Ya te llegó?", tituloPropuesta(TransactionType.INCOME, "basura"))
    }

    /**
     * **Un sello sin movimiento lo dice.** La fila que MENOS respaldo tiene no puede leerse igual
     * que una anclada a plata que se puede mirar. Desde que la casilla del checklist dejó de marcar
     * sin evidencia, estos sellos no se pueden crear más — pero en la base del dueño hay varios.
     */
    @Test fun `un sello sin movimiento dice que se marco a mano`() {
        assertEquals("marcado a mano, sin movimiento", origen(estado(true, null)))
    }

    /**
     * **Lo que el dueño marcó y lo que un movimiento prueba no se leen igual.** La cuota de un
     * crédito llega derivada del movimiento que bajó la deuda: nadie la marcó.
     */
    @Test fun `una fila derivada dice que la prueba un movimiento`() {
        val derivada = estado(true, "ev_1").copy(derivadaDeUnMovimiento = true)
        assertEquals("lo prueba un movimiento", origen(derivada))
    }

    /**
     * **Y dice cuánta plata.** El monto no filtra: un abono de $50.000 sobre un extracto de
     * $1.008.902 salda el periodo igual que un pago completo, porque movi no conoce el extracto
     * (ver `PagosDeDeuda.kt` en el server). Sin el número el dueño no tendría cómo notarlo.
     */
    @Test fun `una fila derivada dice cuanta plata la prueba`() {
        val derivada = estado(true, "ev_1")
            .copy(derivadaDeUnMovimiento = true, montoDelPago = 50_000, monedaDelPago = "COP")
        assertEquals("lo prueba un pago de $50.000", origen(derivada))
    }

    /** Una tarjeta en dólares no se lee en pesos: la moneda viaja con el monto. */
    @Test fun `el monto derivado respeta la moneda`() {
        val derivada = estado(true, "ev_1")
            .copy(derivadaDeUnMovimiento = true, montoDelPago = 181, monedaDelPago = "USD")
        assertEquals("lo prueba un pago de US$181", origen(derivada))
    }

    /**
     * Un sello del dueño con movimiento dice «con un movimiento», no «lo prueba»: lo confirmó él.
     * Con el monto cuando se sabe (el detalle de un período lo manda): una fila que dice «con un
     * movimiento» sin decir de cuánto no deja notar un abono parcial.
     */
    @Test fun `un sello a mano no se disfraza de derivado`() {
        assertEquals("con un movimiento", origen(estado(true, "ev_1")))
        val conMonto = estado(true, "ev_1").copy(montoDelPago = 50_000, monedaDelPago = "COP")
        assertEquals("con un movimiento de $50.000", origen(conMonto))
    }

    /**
     * **Lo que Movi emparejó solo lo dice con todas las letras.** Un emparejamiento automático puede
     * estar equivocado; si sonara igual que una cuota probada, le pediría al dueño la misma
     * confianza justo donde corresponde revisar (y donde tiene un «no fue este»).
     */
    @Test fun `una fila automatica dice que la empareja Movi`() {
        val automatica = estado(true, "ev_1")
            .copy(derivadaDeUnMovimiento = true, automatica = true, montoDelPago = 180_000, monedaDelPago = "COP")
        assertEquals("Movi lo emparejó con un movimiento de $180.000", origen(automatica))
    }

    /** Sin monto —que hoy no pasa, pero el default del campo lo permite— se dice igual de dónde sale. */
    @Test fun `una fila automatica sin monto igual dice quien la emparejo`() {
        val automatica = estado(true, "ev_1").copy(derivadaDeUnMovimiento = true, automatica = true)
        assertEquals("lo emparejó Movi", origen(automatica))
    }

    @Test fun `la diferencia de monto se dice, no se disimula`() {
        // El caso del dueño: anotó 5.000.000 y le entraron 4.780.000 por una retención. La
        // propuesta es válida (el monto no filtra) pero la diferencia se muestra.
        assertTrue(difiereDelEsperado(5_000_000, 4_780_000))
        assertFalse(difiereDelEsperado(5_000_000, 5_000_000))
    }

    /**
     * Regresión del hallazgo MEDIA-3: caía a la CATEGORÍA, que es justo la palabra que comparten
     * todos los candidatos. Con cuatro servicios y las notas vacías, las tres tarjetas decían
     * literalmente lo mismo — un solo movimiento ofrecido como respuesta a tres deudas distintas.
     */
    @Test fun `la descripcion usa el comercio cuando no hay nota, nunca la categoria`() {
        val sinNota = evento("ev_1", description = "").copy(merchant = "EPM")
        val texto = descripcionPropuesta(sinNota)
        assertTrue(texto.endsWith("EPM"), texto)
        assertFalse(texto.contains("Salario"), "la categoría no identifica: no se muestra")
    }

    @Test fun `sin nota y sin comercio la propuesta no se inventa un que`() {
        val pelado = evento("ev_1", description = "").copy(merchant = null)
        assertEquals("Movimiento del 25 de agosto", descripcionPropuesta(pelado.copy(timestamp = 1_787_677_200_000)))
    }

    /**
     * «Movimiento del 25» a secas era indistinguible entre el 25 de este mes y el del anterior —
     * la ambigüedad que dejaba pasar el emparejamiento del mes equivocado sin que nada en pantalla
     * lo delatara. El mes va siempre.
     */
    @Test fun `la propuesta dice el dia con su mes`() {
        // 1787677200000 = 25 de agosto de 2026, 12:00 en Bogotá.
        val texto = descripcionPropuesta(evento("ev_1").copy(timestamp = 1_787_677_200_000))
        assertTrue(texto.startsWith("Movimiento del 25 de agosto"), texto)
    }
}
