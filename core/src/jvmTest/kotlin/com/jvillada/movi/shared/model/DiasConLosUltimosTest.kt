package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** `/by-day?ultimos=N` (vuelta 39): los días que hacen falta, enteros; un traspaso cuenta una vez. */
class DiasConLosUltimosTest {

    private fun ev(id: String, traspaso: String? = null) = FinancialEvent(
        id = id, accountId = "a", type = TransactionType.EXPENSE, amount = 1, category = "C", description = id,
        timestamp = 0L, transferId = traspaso,
    )

    private val dias = listOf(
        EventDay("2026-10-27", -1, listOf(ev("sale", "t1"), ev("entra", "t1"))),
        EventDay("2026-10-26", -2, listOf(ev("a"), ev("b"))),
        EventDay("2026-10-25", -3, listOf(ev("c"))),
    )

    @Test
    fun `devuelve los dias enteros hasta juntar los ultimos`() {
        assertEquals(dias.take(1), diasConLosUltimos(dias, 1))
        // El traspaso es uno: con 2 hace falta el día de antes, entero.
        assertEquals(dias.take(2), diasConLosUltimos(dias, 2))
        assertEquals(dias.take(2), diasConLosUltimos(dias, 3))
        assertEquals(dias, diasConLosUltimos(dias, 4))
        assertEquals(dias, diasConLosUltimos(dias, 99))
        assertEquals(emptyList(), diasConLosUltimos(dias, 0))
        assertEquals(emptyList(), diasConLosUltimos(emptyList(), 3))
    }
}
