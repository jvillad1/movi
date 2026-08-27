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
        assertEquals("ev_2", propuestaActual(e, descartadas = setOf(claveDescartada("rr_1", "ev_1")))?.id)
        assertNull(
            propuestaActual(
                e,
                descartadas = setOf(claveDescartada("rr_1", "ev_1"), claveDescartada("rr_1", "ev_2")),
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

        assertNull(propuestaActual(agua, rechazadoEnAgua), "en Agua ya se dijo que no")
        assertEquals(
            "ev_gas",
            propuestaActual(gas, rechazadoEnAgua)?.id,
            "en Gas sigue siendo el candidato correcto",
        )
    }

    @Test fun `el idioma sigue al tipo del recurrente, y dice de que mes habla`() {
        assertEquals("¿Ya te llegó el de agosto?", tituloPropuesta(TransactionType.INCOME, "2026-08"))
        assertEquals("¿Ya pagaste el de agosto?", tituloPropuesta(TransactionType.EXPENSE, "2026-08"))
        assertEquals("Ya me llegó", etiquetaCierreManual(TransactionType.INCOME))
        assertEquals("Ya lo pagué", etiquetaCierreManual(TransactionType.EXPENSE))
    }

    @Test fun `un periodo ilegible no imprime un numero crudo`() {
        // Nunca «el de 13» ni «el de null»: si no se entiende el periodo, la frase se acorta.
        assertEquals("¿Ya te llegó?", tituloPropuesta(TransactionType.INCOME, "basura"))
        assertEquals("Ya ocurrió", textoYaOcurrio(estado(true, null).copy(period = "2026-99")))
    }

    /**
     * Regresión del hallazgo ALTA-1: «Ya ocurrió **este mes**» era el único rastro que quedaba
     * cuando la app cerraba el periodo equivocado, y estaba escrito de la única forma que lo
     * volvía indetectable. El texto tiene que nombrar el mes.
     */
    @Test fun `una fila cerrada nombra el mes y dice si la respalda un movimiento`() {
        assertEquals("Ya ocurrió en agosto · con un movimiento", textoYaOcurrio(estado(true, "ev_1")))
        assertEquals("Ya ocurrió en agosto", textoYaOcurrio(estado(true, null)))
        assertEquals(
            "Ya ocurrió en septiembre",
            textoYaOcurrio(estado(true, null).copy(period = "2026-09")),
        )
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

    @Test fun `la ocurrencia se busca por regla`() {
        val lista = listOf(estado(), estado().copy(ruleId = "rr_2", occurred = true))
        assertFalse(ocurrenciaDe(lista, "rr_1")!!.occurred)
        assertTrue(ocurrenciaDe(lista, "rr_2")!!.occurred)
        assertNull(ocurrenciaDe(lista, "rr_3"))
    }
}
