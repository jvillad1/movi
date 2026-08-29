package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El orden con el que se leen los movimientos dentro de un día.
 *
 * El pedido del dueño, textual: «asegurate de mostrar en los grupos siempre el último arriba en
 * cada grupo y el más viejo de último, más abajo en la lista». Ver [MAS_RECIENTE_PRIMERO].
 */
class EventOrderTest {

    private fun ev(id: String, ts: Long, type: TransactionType = TransactionType.EXPENSE, amount: Long = 1_000L,
                   transferId: String? = null) =
        FinancialEvent(
            id = id, accountId = "acc1", type = type, amount = amount,
            category = "test", description = "test", timestamp = ts, transferId = transferId,
        )

    @Test
    fun `el mas reciente arriba y el mas viejo abajo`() {
        val ordenados = listOf(
            ev("a", 1_000L),
            ev("b", 3_000L),
            ev("c", 2_000L),
        ).masRecientePrimero()

        assertEquals(listOf("b", "c", "a"), ordenados.map { it.id })
    }

    /**
     * Sin desempate, dos movimientos del mismo milisegundo bailan entre recargas — que es la
     * mitad del defecto que esto vino a corregir. Se ordena la MISMA lista en dos órdenes de
     * entrada distintos: si el criterio es total, las dos salidas tienen que ser idénticas.
     */
    @Test
    fun `dos movimientos del mismo instante salen siempre en el mismo orden`() {
        val a = ev("ev_aaa", 5_000L)
        val b = ev("ev_bbb", 5_000L)

        val unaLectura   = listOf(a, b).masRecientePrimero().map { it.id }
        val otraLectura  = listOf(b, a).masRecientePrimero().map { it.id }

        assertEquals(unaLectura, otraLectura)
        assertEquals(listOf("ev_aaa", "ev_bbb"), unaLectura)
    }

    /** Reordenar es idempotente: volver a aplicarlo no mueve nada. */
    @Test
    fun `ordenar dos veces da lo mismo que ordenar una`() {
        val lista = listOf(ev("c", 5_000L), ev("a", 5_000L), ev("b", 9_000L), ev("d", 1_000L))
        val unaVez = lista.masRecientePrimero()
        assertEquals(unaVez.map { it.id }, unaVez.masRecientePrimero().map { it.id })
    }

    /**
     * Las dos patas de un traspaso comparten instante (es un solo hecho). El desempate por id no
     * las puede separar: tienen que quedar juntas para que el renglón colapsado de Movimientos
     * caiga en un solo lugar del día.
     */
    @Test
    fun `las dos patas de un traspaso quedan pegadas`() {
        val ordenados = listOf(
            ev("ev_otro_viejo", 4_000L),
            ev("ev_pata_in", 7_000L, TransactionType.INCOME, transferId = "tr-1"),
            ev("ev_otro_nuevo", 9_000L),
            ev("ev_pata_out", 7_000L, TransactionType.EXPENSE, transferId = "tr-1"),
        ).masRecientePrimero()

        val posiciones = ordenados.withIndex().filter { it.value.transferId == "tr-1" }.map { it.index }
        assertEquals(2, posiciones.size)
        assertEquals(1, posiciones[1] - posiciones[0], "las patas quedaron separadas: ${ordenados.map { it.id }}")
    }

    /**
     * El total del día es una suma: no depende del orden. Se comprueba igual porque es la cifra
     * que el dueño lee arriba de cada grupo.
     */
    @Test
    fun `el total del dia no cambia al reordenar`() {
        val dia = listOf(
            ev("a", 1_000L, TransactionType.INCOME, amount = 500_000L),
            ev("b", 3_000L, TransactionType.EXPENSE, amount = 120_000L),
            ev("c", 3_000L, TransactionType.EXPENSE, amount = 80_000L),
        )
        fun total(items: List<FinancialEvent>) = items
            .filter { it.countsAsCashFlow }
            .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }

        assertEquals(total(dia), total(dia.masRecientePrimero()))
        assertEquals(300_000L, total(dia.masRecientePrimero()))
    }

    /** Ordenar no agrega, no quita y no duplica renglones. */
    @Test
    fun `no se pierde ni se duplica ningun movimiento`() {
        val dia = listOf(ev("a", 1L), ev("b", 1L), ev("c", 2L), ev("d", 3L))
        val ordenados = dia.masRecientePrimero()
        assertEquals(dia.size, ordenados.size)
        assertTrue(ordenados.map { it.id }.toSet() == dia.map { it.id }.toSet())
    }
}
