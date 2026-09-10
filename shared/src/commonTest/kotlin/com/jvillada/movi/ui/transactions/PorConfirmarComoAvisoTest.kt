package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **«Por confirmar» dejó de ser un chip.**
 *
 * El dueño miró la fila de seis filtros y dijo: *«Me parece que ya tenemos muchos filtros o al
 * menos en ese nivel de jerarquía, se ve bastante mal»*, y sobre este: *«Por confirmar debería
 * saltar en otro lugar no acá en esta misma vista»*.
 *
 * No es un filtro: es una **bandeja de entrada**, y su estado normal —el de quien anota todo a
 * mano— es vacío. Ahora es un aviso que aparece solo cuando hay algo, y el filtro sigue existiendo
 * detrás.
 */
class PorConfirmarComoAvisoTest {

    private var n = 0

    private fun evento(estado: ReconciliationStatus) = FinancialEvent(
        id = "ev_${n++}",
        accountId = "acc_1",
        type = TransactionType.EXPENSE,
        amount = 18_500L,
        category = "Comida",
        description = "Carnes y Legumbres Santa Elena",
        timestamp = 1_757_000_000_000L,
        reconciliationStatus = estado,
    )

    private fun dia(vararg eventos: FinancialEvent) =
        EventDay(date = "2026-09-09", total = 0L, items = eventos.toList())

    @Test
    fun `el chip ya no se dibuja, pero la constante sigue valiendo lo mismo`() {
        assertFalse(CHIP_POR_CONFIRMAR in CHIPS_VISIBLES, "salió de la fila de filtros")
        // **Los índices no se renumeran, a propósito.** El número viaja adentro de
        // `Screen.Transactions` y puede volver desde una pila de navegación restaurada: correr
        // «Recurrentes» del 5 al 3 haría que un 3 viejo signifique otra cosa, en silencio.
        assertEquals(3, CHIP_POR_CONFIRMAR)
        assertEquals(5, CHIP_RECURRENTES)
        assertEquals(listOf(CHIP_TODO, CHIP_GASTOS, CHIP_INGRESOS, CHIP_RECURRENTES), CHIPS_VISIBLES)
    }

    @Test
    fun `sigue siendo un filtro de verdad detras del aviso`() {
        val sinConfirmar = evento(ReconciliationStatus.UNCONFIRMED)
        val confirmado = evento(ReconciliationStatus.RECONCILED)

        assertTrue(matchesChip(sinConfirmar, CHIP_POR_CONFIRMAR))
        assertFalse(matchesChip(confirmado, CHIP_POR_CONFIRMAR))
    }

    @Test
    fun `el aviso no existe cuando no hay nada que confirmar, que es el caso normal`() {
        val dias = listOf(dia(evento(ReconciliationStatus.RECONCILED), evento(ReconciliationStatus.RECONCILED)))

        assertEquals(0, cuantosPorConfirmar(dias))
        assertFalse(avisoDePorConfirmar(CHIP_TODO, cuantosPorConfirmar(dias)))
    }

    @Test
    fun `se cuentan todos los dias, no solo el visible`() {
        val dias = listOf(
            dia(evento(ReconciliationStatus.UNCONFIRMED), evento(ReconciliationStatus.RECONCILED)),
            dia(evento(ReconciliationStatus.UNCONFIRMED)),
        )
        assertEquals(2, cuantosPorConfirmar(dias))
    }

    @Test
    fun `el aviso se ve con cualquier chip, salvo adentro de la propia bandeja`() {
        // Estar mirando «Gastos» no hace que deje de haber algo por confirmar; el aviso tiene que
        // decir la verdad esté donde esté parado el dueño.
        assertTrue(avisoDePorConfirmar(CHIP_TODO, 3))
        assertTrue(avisoDePorConfirmar(CHIP_GASTOS, 3))
        assertTrue(avisoDePorConfirmar(CHIP_RECURRENTES, 3))
        // Adentro sería un botón que lleva a donde uno ya está.
        assertFalse(avisoDePorConfirmar(CHIP_POR_CONFIRMAR, 3))
    }

    @Test
    fun `el aviso nombra el hecho, no la etiqueta`() {
        // El chip viejo dejaba sin contestar la pregunta que el dueño hizo con todas las letras:
        // «¿Qué es Por confirmar?». «Entraron solos» la contesta.
        assertEquals("1 movimiento entró solo y falta confirmarlo", textoDelAvisoPorConfirmar(1))
        assertEquals("3 movimientos entraron solos y faltan confirmar", textoDelAvisoPorConfirmar(3))
    }
}
