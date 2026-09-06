package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PR 3 del rediseño de Recurrentes (2026-09): la lista que alimenta «Ya ocurrieron» y sus
 * «Deshacer».
 *
 * Importa porque **sellar un periodo apaga el aviso de una deuda real**: apenas se sella, el
 * recurrente sale de «Próximos» (su vencimiento vigente ya es el del mes que viene), así que si
 * esta lista se quedara corta el dueño no tendría dónde revertir una marca equivocada hasta el mes
 * siguiente. En la pantalla vieja ese «Deshacer» vivía en el inventario «Por día del mes», que no
 * se mudó a Movimientos.
 */
class OcurrenciasSelladasTest {

    private fun regla(id: String, nombre: String) = RecurringRule(
        id = id, name = nombre, category = "Vivienda",
        amount = 1_000_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
    )

    private fun vencimiento(rule: RecurringRule) = UpcomingPayment(
        rule = rule, dueDate = "2026-09-05", daysUntil = 3, status = PaymentStatus.DUE_SOON,
    )

    private fun estado(ruleId: String, occurred: Boolean, eventId: String? = null) = OccurrenceState(
        ruleId = ruleId, period = "2026-09", dueDate = "2026-09-05",
        occurred = occurred, eventId = eventId,
    )

    @Test fun `solo entran los periodos ya sellados`() {
        val arriendo = regla("rr_arriendo", "Arriendo")
        val gimnasio = regla("rr_gym", "Gimnasio")
        val selladas = ocurrenciasSelladas(
            upcoming = listOf(vencimiento(arriendo), vencimiento(gimnasio)),
            estados = listOf(estado("rr_arriendo", occurred = true), estado("rr_gym", occurred = false)),
        )
        assertEquals(listOf("Arriendo"), selladas.map { it.first.name })
    }

    /** El periodo viaja con la fila: es lo que «Deshacer» tiene que mandarle al server. */
    @Test fun `cada fila lleva el periodo que hay que deshacer`() {
        val arriendo = regla("rr_arriendo", "Arriendo")
        val selladas = ocurrenciasSelladas(
            upcoming = listOf(vencimiento(arriendo)),
            estados = listOf(estado("rr_arriendo", occurred = true, eventId = "ev_9")),
        )
        assertEquals("2026-09", selladas.single().second.period)
        assertEquals("ev_9", selladas.single().second.eventId)
    }

    /**
     * Sin el nombre no hay nada que mostrar: una fila que diga «Deshacer» sin decir de qué es peor
     * que no estar. Pasa si `/api/payments/upcoming` falló y `/occurrences` no.
     */
    @Test fun `una ocurrencia sin regla conocida se descarta en silencio`() {
        val selladas = ocurrenciasSelladas(
            upcoming = emptyList(),
            estados = listOf(estado("rr_fantasma", occurred = true)),
        )
        assertTrue(selladas.isEmpty())
    }

    /** Orden estable, para que las filas no bailen entre recargas. */
    @Test fun `se ordena por nombre, sin importar mayusculas`() {
        val reglas = listOf(regla("a", "Zumba"), regla("b", "arriendo"), regla("c", "Netflix"))
        val selladas = ocurrenciasSelladas(
            upcoming = reglas.map { vencimiento(it) },
            estados = reglas.map { estado(it.id, occurred = true) },
        )
        assertEquals(listOf("arriendo", "Netflix", "Zumba"), selladas.map { it.first.name })
    }
}
