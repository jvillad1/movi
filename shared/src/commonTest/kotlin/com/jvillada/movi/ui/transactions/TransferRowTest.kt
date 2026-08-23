package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [collapseTransfers] es lo que hace que un traspaso se lea en Movimientos como **un solo
 * hecho** ("Traspaso · Ahorros → CDT") y no como dos renglones sueltos que parecen un gasto y un
 * ingreso sin relación.
 */
class TransferRowTest {

    private fun evento(
        id: String,
        accountId: String = "acc_1",
        type: TransactionType = TransactionType.EXPENSE,
        amount: Long = 250_000L,
        category: String = "Mercado",
        transferId: String? = null,
    ) = FinancialEvent(
        id = id,
        accountId = accountId,
        type = type,
        amount = amount,
        category = category,
        description = id,
        timestamp = 0L,
        transferId = transferId,
        countsAsCashFlow = transferId == null,
        // RECONCILED por defecto: lo anotado a mano ya está confirmado (F12), y el chip
        // «Gastos» excluye a propósito lo que todavía está por confirmar.
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private fun patas(transferId: String = "tr_1") = listOf(
        evento("ev_from", "acc_ahorros", TransactionType.EXPENSE, category = TRANSFER_CATEGORY, transferId = transferId),
        evento("ev_to", "acc_cdt", TransactionType.INCOME, category = TRANSFER_CATEGORY, transferId = transferId),
    )

    @Test
    fun `sin traspasos, cada movimiento sigue siendo su propio renglon`() {
        val filas = collapseTransfers(listOf(evento("ev_1"), evento("ev_2")))

        assertEquals(2, filas.size)
        assertTrue(filas.all { it is MovementRow.Single })
    }

    @Test
    fun `las dos patas se juntan en un solo renglon`() {
        val filas = collapseTransfers(patas())

        assertEquals(1, filas.size)
        val fila = assertIs<MovementRow.Transfer>(filas.single())
        assertEquals("ev_from", fila.out.id)
        assertEquals("ev_to", fila.into.id)
        assertEquals(250_000L, fila.amount)
    }

    @Test
    fun `el orden de las patas en la lista no importa`() {
        val filas = collapseTransfers(patas().reversed())

        val fila = assertIs<MovementRow.Transfer>(filas.single())
        assertEquals("ev_from", fila.out.id, "la pata de origen es siempre el EXPENSE")
        assertEquals("ev_to", fila.into.id)
    }

    @Test
    fun `el renglon del traspaso conserva el lugar de su primera pata`() {
        val filas = collapseTransfers(listOf(evento("ev_antes")) + patas() + evento("ev_despues"))

        assertEquals(3, filas.size)
        assertIs<MovementRow.Single>(filas[0])
        assertIs<MovementRow.Transfer>(filas[1])
        assertIs<MovementRow.Single>(filas[2])
    }

    @Test
    fun `dos traspasos distintos no se mezclan`() {
        val filas = collapseTransfers(patas("tr_1") + patas("tr_2").map { it.copy(id = it.id + "_b") })

        assertEquals(2, filas.size)
        assertTrue(filas.all { it is MovementRow.Transfer })
    }

    /**
     * Si un filtro (chips «Egresos», búsqueda) dejó una sola pata a la vista, se muestra tal cual
     * en vez de esconderla: la lista tiene que seguir mostrando lo que el filtro pidió, y media
     * pareja con su descripción ("Traspaso a CDT") sigue siendo legible.
     */
    @Test
    fun `una pata sola, sin su hermana a la vista, se muestra como renglon suelto`() {
        val filas = collapseTransfers(listOf(patas().first()))

        assertEquals(1, filas.size)
        assertIs<MovementRow.Single>(filas.single())
    }

    @Test
    fun `el subtitulo dice de que cuenta a que cuenta fue`() {
        val fila = assertIs<MovementRow.Transfer>(collapseTransfers(patas()).single())

        assertEquals(
            "De Ahorros a CDT",
            transferRowSubtitle(fila, mapOf("acc_ahorros" to "Ahorros", "acc_cdt" to "CDT")),
        )
    }

    /**
     * Con palabras, sin flecha: «→» sale como ▯ en wasm (la fuente del canvas no trae el glifo,
     * el mismo motivo por el que el «›» ya se había reemplazado por un ícono Material).
     */
    @Test
    fun `el subtitulo no usa ningun glifo que la web no sepa dibujar`() {
        val fila = assertIs<MovementRow.Transfer>(collapseTransfers(patas()).single())
        val subtitulo = transferRowSubtitle(fila, mapOf("acc_ahorros" to "Ahorros", "acc_cdt" to "CDT"))

        listOf('→', '›', '⟶', '▶').forEach {
            assertFalse(subtitulo.contains(it), "el subtítulo no debería traer '$it'")
        }
    }

    /** Sin los nombres cargados todavía, el renglón no inventa ninguno. */
    @Test
    fun `si los nombres de las cuentas no llegaron, el subtitulo no miente`() {
        val fila = assertIs<MovementRow.Transfer>(collapseTransfers(patas()).single())

        assertEquals("De Origen a Destino", transferRowSubtitle(fila, emptyMap()))
    }

    // ── La categoría reservada no se toca desde la UI ─────────────────────────

    @Test
    fun `una pata de traspaso se reconoce por el transferId`() {
        assertTrue(isTransferLeg(patas().first()))
        assertFalse(isTransferLeg(evento("ev_1")))
    }

    /**
     * Y también por la categoría sola: si una fila vieja o un espejo a medio migrar quedara sin
     * `transferId`, la hoja de cambiar categoría igual tiene que negarse — sacarla de la
     * categoría reservada la devolvería al gasto del mes.
     */
    @Test
    fun `la categoria reservada alcanza para reconocerla aunque falte el transferId`() {
        assertTrue(isTransferLeg(evento("ev_raro", category = TRANSFER_CATEGORY)))
    }


    // ── M5: los chips tampoco pueden mostrar media pareja ─────────────────────

    /**
     * Son DOS chips, no uno. «Gastos» dejaba pasar la pata de salida y «Ingresos» la de entrada;
     * como en cada caso queda UNA sola pata a la vista, `collapseTransfers` cae a `Single` y el
     * traspaso vuelve a leerse como «−$500.000 · Traspaso» — exactamente la lectura que esta
     * feature vino a eliminar, solo que una pestaña más allá.
     */
    @Test
    fun `el chip de gastos no muestra la pata de salida de un traspaso`() {
        val salida = patas().first()

        assertFalse(matchesChip(salida, CHIP_GASTOS))
    }

    @Test
    fun `el chip de ingresos no muestra la pata de entrada`() {
        val entrada = patas()[1]

        assertFalse(matchesChip(entrada, CHIP_INGRESOS))
    }

    @Test
    fun `en Todo el traspaso si aparece, colapsado en un solo renglon`() {
        val visibles = patas().filter { matchesChip(it, CHIP_TODO) }

        assertEquals(2, visibles.size)
        assertIs<MovementRow.Transfer>(collapseTransfers(visibles).single())
    }

    @Test
    fun `un gasto normal sigue apareciendo en su chip`() {
        val gasto = evento("ev_1", type = TransactionType.EXPENSE)

        assertTrue(matchesChip(gasto, CHIP_GASTOS))
        assertFalse(matchesChip(gasto, CHIP_INGRESOS))
    }

    @Test
    fun `un ingreso normal sigue apareciendo en su chip`() {
        val ingreso = evento("ev_2", type = TransactionType.INCOME)

        assertTrue(matchesChip(ingreso, CHIP_INGRESOS))
        assertFalse(matchesChip(ingreso, CHIP_GASTOS))
    }

    /** «Por confirmar» es para lo que entró solo; una pata la anotó el dueño, nunca está ahí. */
    @Test
    fun `el chip de por confirmar no cambia de comportamiento`() {
        val porConfirmar = evento("ev_3").copy(reconciliationStatus = ReconciliationStatus.UNCONFIRMED)

        assertTrue(matchesChip(porConfirmar, CHIP_POR_CONFIRMAR))
        assertFalse(matchesChip(evento("ev_4"), CHIP_POR_CONFIRMAR))
    }
}
