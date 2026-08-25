package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fija qué cuenta como **un movimiento** para [movementCount]: la pregunta que responde
 * `FinanceSummary.eventCount` y que decide si la guía de primeros pasos sigue prendida.
 *
 * Lo que estos tests blindan, en una línea: un traspaso es UNA cosa aunque sean dos filas, la
 * apertura de cuenta no es nada, y todo lo demás cuenta —incluido lo que `isCashFlow` deja
 * fuera del mes, que el dueño igual anotó con sus propios dedos.
 */
class MovementCountTest {

    private fun evento(
        id: String,
        category: String = "Mercado",
        type: TransactionType = TransactionType.EXPENSE,
        transferId: String? = null,
    ) = FinancialEvent(
        id = id,
        accountId = "acc_1",
        type = type,
        amount = 10_000L,
        category = category,
        description = id,
        timestamp = 0L,
        transferId = transferId,
    )

    @Test
    fun `sin eventos no hay movimientos`() {
        assertEquals(0, movementCount(emptyList()))
    }

    @Test
    fun `dos gastos sueltos son dos movimientos`() {
        assertEquals(2, movementCount(listOf(evento("ev_1"), evento("ev_2"))))
    }

    /** El corazón de T1: mover plata entre dos cuentas propias es UNA cosa que pasó. */
    @Test
    fun `las dos patas de un traspaso cuentan una sola vez`() {
        val patas = listOf(
            evento("ev_from", TRANSFER_CATEGORY, TransactionType.EXPENSE, transferId = "tr_1"),
            evento("ev_to", TRANSFER_CATEGORY, TransactionType.INCOME, transferId = "tr_1"),
        )
        assertEquals(1, movementCount(patas))
    }

    @Test
    fun `dos traspasos distintos son dos movimientos`() {
        val patas = listOf(
            evento("ev_a1", TRANSFER_CATEGORY, TransactionType.EXPENSE, transferId = "tr_1"),
            evento("ev_a2", TRANSFER_CATEGORY, TransactionType.INCOME, transferId = "tr_1"),
            evento("ev_b1", TRANSFER_CATEGORY, TransactionType.EXPENSE, transferId = "tr_2"),
            evento("ev_b2", TRANSFER_CATEGORY, TransactionType.INCOME, transferId = "tr_2"),
        )
        assertEquals(2, movementCount(patas))
    }

    /**
     * Una pata sola —porque el filtro de quien llama la separó de su hermana, o porque la cuenta
     * de la otra punta ya no existe— sigue contando una vez, nunca cero.
     */
    @Test
    fun `una sola pata de un traspaso cuenta una vez`() {
        val pata = evento("ev_from", TRANSFER_CATEGORY, TransactionType.EXPENSE, transferId = "tr_1")
        assertEquals(1, movementCount(listOf(pata)))
    }

    @Test
    fun `la apertura de cuenta no es un movimiento`() {
        assertEquals(0, movementCount(listOf(evento("ev_open", OPENING_CATEGORY, TransactionType.INCOME))))
    }

    @Test
    fun `la apertura no cuenta pero el gasto que vino despues si`() {
        val eventos = listOf(
            evento("ev_open", OPENING_CATEGORY, TransactionType.INCOME),
            evento("ev_mercado"),
        )
        assertEquals(1, movementCount(eventos))
    }

    /**
     * `countsAsCashFlow` diría `false` para los dos (ver [isCashFlow]) y sin embargo el dueño los
     * anotó: por eso [movementCount] no se apoya en esa bandera. Ver su KDoc.
     */
    @Test
    fun `el abono a una deuda y el pago del extracto si son movimientos`() {
        val eventos = listOf(
            evento("ev_cuota", "Cuota libranza", TransactionType.INCOME),
            evento("ev_pago_tc", CARD_PAYMENT_CATEGORY, TransactionType.EXPENSE),
        )
        assertEquals(2, movementCount(eventos))
    }
}
