package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.masRecientePrimero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El orden nuevo de un día, visto desde la pantalla: **el más reciente arriba, el más viejo
 * abajo**, y que eso sobreviva a lo que la pantalla le hace encima a la lista — los chips y el
 * colapso de traspasos.
 *
 * La pantalla no ordena nada: recibe `EventDay.items` ya ordenado (del server en la web, del
 * espejo local en el teléfono) y solo filtra y colapsa. Estos tests fijan justamente eso: que
 * `filter` y `collapseTransfers` **conserven** el orden en vez de rehacerlo por su cuenta.
 */
class OrdenDentroDelDiaTest {

    private fun ev(
        id: String,
        ts: Long,
        type: TransactionType = TransactionType.EXPENSE,
        category: String = "Comida",
        transferId: String? = null,
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
        amount: Long = 10_000L,
    ) = FinancialEvent(
        id = id, accountId = "acc1", type = type, amount = amount,
        category = category, description = "test", timestamp = ts,
        transferId = transferId, reconciliationStatus = estado,
    )

    /** Un día real: varias horas, un empate, un traspaso y el saldo inicial. */
    private fun dia(): List<FinancialEvent> = listOf(
        ev("ev-apertura", 1_000L, TransactionType.INCOME, OPENING_CATEGORY, amount = 900_000L),
        ev("ev_bbb", 5_000L),
        ev("ev_aaa", 5_000L),
        ev("ev-tarde", 9_000L),
        ev("ev-sueldo", 7_000L, TransactionType.INCOME, "Salario", amount = 500_000L),
        ev("ev-tr-out", 3_000L, TransactionType.EXPENSE, TRANSFER_CATEGORY, transferId = "tr-1", amount = 200_000L),
        ev("ev-tr-in", 3_000L, TransactionType.INCOME, TRANSFER_CATEGORY, transferId = "tr-1", amount = 200_000L),
        ev("ev-pendiente", 6_000L, estado = ReconciliationStatus.UNCONFIRMED),
    ).masRecientePrimero()

    @Test
    fun `el dia entra ordenado de mas nuevo a mas viejo`() {
        assertEquals(
            listOf("ev-tarde", "ev-sueldo", "ev-pendiente", "ev_aaa", "ev_bbb", "ev-tr-in", "ev-tr-out", "ev-apertura"),
            dia().map { it.id },
        )
    }

    /**
     * Cada chip conserva el orden de la lista que filtra: filtrar quita renglones, no los baraja.
     *
     * La salida esperada va **escrita a mano**, no derivada de `filtrado`. La primera versión de
     * este test comparaba contra `ordenado.filter { it in filtrado }`, que es exactamente
     * `filtrado`: una tautología que pasaba aunque el filtro barajara la lista entera.
     */
    @Test
    fun `el orden sobrevive a cada chip`() {
        val ordenado = dia()
        val esperadoPorChip = mapOf(
            // Todo: el día entero, tal cual entró.
            CHIP_TODO to listOf(
                "ev-tarde", "ev-sueldo", "ev-pendiente", "ev_aaa", "ev_bbb",
                "ev-tr-in", "ev-tr-out", "ev-apertura",
            ),
            // Gastos: sin el pendiente, sin las patas del traspaso y sin la apertura.
            CHIP_GASTOS to listOf("ev-tarde", "ev_aaa", "ev_bbb"),
            // Ingresos: el sueldo y nada más (la pata IN es traspaso; la apertura no es ingreso).
            CHIP_INGRESOS to listOf("ev-sueldo"),
            CHIP_POR_CONFIRMAR to listOf("ev-pendiente"),
        )
        for ((chip, esperado) in esperadoPorChip) {
            val filtrado = ordenado.filter { matchesChip(it, chip) }
            assertEquals(esperado, filtrado.map { it.id }, "el chip $chip barajó o cambió la lista")
            // Y sigue siendo decreciente por instante.
            filtrado.zipWithNext { a, b ->
                assertTrue(a.timestamp >= b.timestamp, "chip $chip: ${a.id} quedó arriba de ${b.id}")
            }
        }
    }

    /** El saldo inicial queda fuera de Gastos y de Ingresos, como antes. */
    @Test
    fun `el saldo inicial sigue fuera de Gastos y de Ingresos`() {
        val ordenado = dia()
        assertTrue(ordenado.none { matchesChip(it, CHIP_GASTOS) && isOpeningBalance(it) })
        assertTrue(ordenado.none { matchesChip(it, CHIP_INGRESOS) && isOpeningBalance(it) })
        assertTrue(ordenado.any { matchesChip(it, CHIP_TODO) && isOpeningBalance(it) })
    }

    /**
     * El emparejamiento de un traspaso no depende de que las patas estén pegadas
     * ([collapseTransfers] agrupa por `transferId` sobre la lista entera), pero el renglón
     * colapsado sí cae en el lugar de la primera pata. Con el orden nuevo las dos patas comparten
     * instante y quedan juntas, así que el renglón cae donde le toca por hora.
     */
    @Test
    fun `el traspaso se sigue juntando en un solo renglon y en su lugar del dia`() {
        val filas = collapseTransfers(dia())

        val traspasos = filas.filterIsInstance<MovementRow.Transfer>()
        assertEquals(1, traspasos.size)
        assertEquals("ev-tr-out", traspasos.first().out.id)
        assertEquals("ev-tr-in", traspasos.first().into.id)

        assertEquals(
            listOf("ev-tarde", "ev-sueldo", "ev-pendiente", "ev_aaa", "ev_bbb", "tr-1", "ev-apertura"),
            filas.map { it.key },
        )
    }

    /** Bajo «Todo», el traspaso queda entre lo que pasó después y lo que pasó antes. */
    @Test
    fun `el renglon colapsado no salta de lugar`() {
        val filas = collapseTransfers(dia())
        val posTraspaso = filas.indexOfFirst { it.key == "tr-1" }
        val posMasNuevo = filas.indexOfFirst { it.key == "ev_bbb" }     // 5_000
        val posMasViejo = filas.indexOfFirst { it.key == "ev-apertura" } // 1_000
        assertTrue(posMasNuevo < posTraspaso, "el traspaso (3_000) quedó arriba de un evento de las 5_000")
        assertTrue(posTraspaso < posMasViejo, "el traspaso (3_000) quedó abajo de la apertura (1_000)")
    }
}
