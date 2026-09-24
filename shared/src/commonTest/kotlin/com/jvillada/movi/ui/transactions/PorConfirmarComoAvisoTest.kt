package com.jvillada.movi.ui.transactions

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
 * mano— es vacío. Fue un aviso arriba de la lista y, desde la ola C, es parte de la bandeja «Por
 * revisar»; el filtro sigue existiendo detrás.
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

    @Test
    fun `el chip ya no se dibuja, pero la constante sigue valiendo lo mismo`() {
        assertFalse(CHIP_POR_CONFIRMAR in CHIPS_VISIBLES, "salió de la fila de filtros")
        // **Los índices no se renumeran, a propósito.** El número viaja adentro de
        // `Screen.Transactions` y puede volver desde una pila de navegación restaurada: correr
        // «Recurrentes» del 5 al 3 haría que un 3 viejo signifique otra cosa, en silencio.
        assertEquals(3, CHIP_POR_CONFIRMAR)
        assertEquals(5, CHIP_RECURRENTES)
        // Ola C: «Recurrentes» también salió de la fila (se mudó a Plan) y tampoco se renumeró.
        assertEquals(listOf(CHIP_TODO, CHIP_GASTOS, CHIP_INGRESOS), CHIPS_VISIBLES)
    }

    @Test
    fun `sigue siendo un filtro de verdad`() {
        val sinConfirmar = evento(ReconciliationStatus.UNCONFIRMED)
        val confirmado = evento(ReconciliationStatus.RECONCILED)

        assertTrue(matchesChip(sinConfirmar, CHIP_POR_CONFIRMAR))
        assertFalse(matchesChip(confirmado, CHIP_POR_CONFIRMAR))
    }

    // Ola C, tarea 5: el aviso «N entraron solos» se juntó con los mensajes del banco y los pagos
    // de tarjeta en un solo renglón «N por revisar» — ver `PorRevisarLogicaTest` y
    // `PorConfirmarEnMovimientosTest`.
}
