package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.TransactionType
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

    @Test fun `sin estado no se pregunta nada`() {
        assertFalse(hayQuePreguntar(null))
    }

    @Test fun `un periodo ya cerrado no vuelve a preguntar`() {
        assertFalse(hayQuePreguntar(estado(occurred = true, eventId = "ev_1")))
    }

    @Test fun `un periodo abierto pregunta, aunque no haya ninguna propuesta`() {
        // Sin candidatos igual hay que ofrecer el «ya lo pagué»: esa es la salida que hace que la
        // función sirva cuando el emparejamiento no encuentra nada.
        assertTrue(hayQuePreguntar(estado()))
    }

    @Test fun `no fue este pasa a la siguiente propuesta`() {
        val e = estado(candidates = listOf(evento("ev_1"), evento("ev_2")))
        assertEquals("ev_1", propuestaActual(e)?.id)
        assertEquals("ev_2", propuestaActual(e, descartadas = setOf("ev_1"))?.id)
        assertNull(propuestaActual(e, descartadas = setOf("ev_1", "ev_2")))
    }

    @Test fun `el idioma sigue al tipo del recurrente`() {
        assertEquals("¿Ya te llegó?", tituloPropuesta(TransactionType.INCOME))
        assertEquals("¿Ya lo pagaste?", tituloPropuesta(TransactionType.EXPENSE))
        assertEquals("Ya me llegó", etiquetaCierreManual(TransactionType.INCOME))
        assertEquals("Ya lo pagué", etiquetaCierreManual(TransactionType.EXPENSE))
    }

    @Test fun `una fila cerrada dice si la respalda un movimiento`() {
        assertEquals("Ya ocurrió este mes · con un movimiento", textoYaOcurrio(estado(true, "ev_1")))
        assertEquals("Ya ocurrió este mes", textoYaOcurrio(estado(true, null)))
    }

    @Test fun `la diferencia de monto se dice, no se disimula`() {
        // El caso del dueño: anotó 5.000.000 y le entraron 4.780.000 por una retención. La
        // propuesta es válida (el monto no filtra) pero la diferencia se muestra.
        assertTrue(difiereDelEsperado(5_000_000, 4_780_000))
        assertFalse(difiereDelEsperado(5_000_000, 5_000_000))
    }

    @Test fun `la descripcion de la propuesta cae a la categoria cuando no hay nota`() {
        assertTrue(descripcionPropuesta(evento("ev_1", description = "")).endsWith("Salario"))
    }

    @Test fun `la ocurrencia se busca por regla`() {
        val lista = listOf(estado(), estado().copy(ruleId = "rr_2", occurred = true))
        assertFalse(ocurrenciaDe(lista, "rr_1")!!.occurred)
        assertTrue(ocurrenciaDe(lista, "rr_2")!!.occurred)
        assertNull(ocurrenciaDe(lista, "rr_3"))
    }
}
