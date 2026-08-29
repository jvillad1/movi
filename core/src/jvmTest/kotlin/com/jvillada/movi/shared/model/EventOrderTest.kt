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
                   transferId: String? = null, createdAt: Long? = null) =
        FinancialEvent(
            id = id, accountId = "acc1", type = type, amount = amount,
            category = "test", description = "test", timestamp = ts, transferId = transferId,
            createdAt = createdAt,
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

    // ── El caso que el dueño estaba viendo: un día pasado ───────────────────

    /**
     * **Cinco gastos de ayer, anotados uno detrás del otro.** Al elegir una fecha que no es hoy,
     * el cliente la guarda al MEDIODÍA, así que los cinco quedan con el mismo `timestamp` y
     * `timestamp` no decide nada entre ellos. Sin [FinancialEvent.createdAt] el orden es
     * arbitrario —y con desempate por `id` sería estable pero igual de arbitrario: el segundo que
     * escribió podía quedar arriba del primero para siempre—. Con la creación, arriba va el que
     * anotó último, que es lo que el dueño pidió.
     */
    @Test
    fun `entre gastos del mismo dia pasado manda el que se anoto ultimo`() {
        val mediodiaDeAyer = 1_700_000_000_000L
        val ordenados = listOf(
            ev("ev_e", mediodiaDeAyer, createdAt = 5_000L),
            ev("ev_a", mediodiaDeAyer, createdAt = 1_000L),
            ev("ev_c", mediodiaDeAyer, createdAt = 3_000L),
            ev("ev_d", mediodiaDeAyer, createdAt = 4_000L),
            ev("ev_b", mediodiaDeAyer, createdAt = 2_000L),
        ).masRecientePrimero()

        assertEquals(listOf("ev_e", "ev_d", "ev_c", "ev_b", "ev_a"), ordenados.map { it.id })
    }

    /**
     * **La creación desempata; no manda.** Un SMS del banco de ayer a las 23:00 tiene hora real.
     * Un gasto de ayer anotado hoy a mano quedó al mediodía. Por hora del día el SMS pasó después,
     * así que va arriba — aunque el otro se haya *escrito* después. Si `createdAt` fuera el
     * criterio principal, este orden saldría al revés.
     */
    @Test
    fun `la hora real le gana a la hora en que se anoto`() {
        val ayer23 = 1_700_040_000_000L
        val ayerMediodia = 1_700_000_000_000L
        val ordenados = listOf(
            ev("ev-manual-de-ayer", ayerMediodia, createdAt = 1_800_000_000_000L), // escrito hoy
            ev("ev-sms-de-ayer", ayer23, createdAt = ayer23),                      // capturado ayer
        ).masRecientePrimero()

        assertEquals(listOf("ev-sms-de-ayer", "ev-manual-de-ayer"), ordenados.map { it.id })
    }

    /**
     * Los movimientos que ya existían no tienen creación y no se les inventa una: caen a su
     * `timestamp`. Entre dos sin creación, el orden lo sigue fijando el `id` — o sea, quedan
     * exactamente como estaban.
     */
    @Test
    fun `sin fecha de creacion se cae al timestamp y nada se mueve`() {
        val mismoInstante = 1_700_000_000_000L
        val ordenados = listOf(
            ev("ev_zzz_viejo", mismoInstante),
            ev("ev_aaa_viejo", mismoInstante),
        ).masRecientePrimero()

        assertEquals(listOf("ev_aaa_viejo", "ev_zzz_viejo"), ordenados.map { it.id })
    }

    /**
     * El caso mezclado de la migración: uno viejo (sin creación) y uno nuevo, en el mismo
     * instante. El nuevo se anotó después que el instante compartido, así que queda arriba. Es la
     * consecuencia honesta de caer a `timestamp`: no se afirma que el viejo se creó en otro
     * momento, se lo trata como si se hubiera creado cuando pasó.
     */
    @Test
    fun `uno viejo y uno nuevo en el mismo instante`() {
        val mediodia = 1_700_000_000_000L
        val ordenados = listOf(
            ev("ev-viejo", mediodia),
            ev("ev-nuevo", mediodia, createdAt = mediodia + 60_000L),
        ).masRecientePrimero()

        assertEquals(listOf("ev-nuevo", "ev-viejo"), ordenados.map { it.id })
    }

    /** El desempate por creación sigue siendo total: dos lecturas dan el mismo orden. */
    @Test
    fun `el orden con creacion tambien es estable`() {
        val a = ev("ev_a", 5_000L, createdAt = 9_000L)
        val b = ev("ev_b", 5_000L, createdAt = 9_000L)
        assertEquals(
            listOf(a, b).masRecientePrimero().map { it.id },
            listOf(b, a).masRecientePrimero().map { it.id },
        )
    }
}
