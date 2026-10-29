package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El recurrente que cambió de categoría (vuelta 60)
 *
 * La regla «Arriendo» tenía la categoría Otros y en septiembre el arriendo fue Hogar: Movi propone
 * «¿Arriendo va en Hogar, como en septiembre?», nunca lo cambia solo, y mientras tanto lo compara
 * donde estaba.
 */
class RecurrenteQueCambioDeCategoriaTest {

    private val dia = 24L * 60 * 60 * 1000
    private val septiembre = 1_790_000_000_000L
    private val octubre = septiembre + 30 * dia

    private fun gasto(id: String, monto: Long, categoria: String, cuando: Long, descripcion: String = "Arriendo") = FinancialEvent(
        id = id, accountId = "acc-1", type = TransactionType.EXPENSE, amount = monto, category = categoria,
        description = descripcion, timestamp = cuando, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private val reglaArriendo = RecurringRule(
        id = "rr-arriendo", name = "Arriendo", category = "Otros", amount = 1_800_000, dayOfMonth = 26, type = TransactionType.EXPENSE,
    )

    @Test
    fun `la ocurrencia de una regla en otra categoria que la del periodo pasado se propone, con la regla`() {
        val cambios = recurrentesQueCambiaronDeCategoria(
            actual = listOf(gasto("oct", 1_800_000, "Otros", octubre)),
            anterior = listOf(gasto("sep", 1_850_000, "Hogar", septiembre)),
            reglas = listOf(reglaArriendo),
            // Los dos sellados en la regla: el monto no se mira (un recibo varía).
            reglaDelMovimiento = mapOf("oct" to "rr-arriendo", "sep" to "rr-arriendo"),
            periodoAnterior = "2026-09",
        )
        val c = cambios.single()
        assertEquals("oct", c.eventoId)
        assertEquals("Otros", c.categoria)
        assertEquals("Hogar", c.categoriaAnterior)
        assertEquals("sep", c.eventoAnteriorId)
        assertEquals("rr-arriendo", c.reglaId, "la regla tiene Otros: «Sí, cambiar» también la corrige")

        val aviso = anomaliaDelCambioDeCategoria(c, emptyList())
        assertEquals(TipoDeAnomalia.RECURRENTE_CAMBIO_DE_CATEGORIA, aviso.tipo)
        assertEquals("¿Arriendo va en Hogar, como en septiembre?", aviso.titulo)
        assertTrue(aviso.detalle.contains("quedó en Otros"))
        assertTrue(aviso.detalle.contains("regla «Arriendo»"))
        assertEquals(c, aviso.cambioDeCategoria)
        assertEquals("recurrente-categoria:oct", aviso.huella, "«No» vale para este movimiento")
    }

    @Test
    fun `sin regla, el mismo nombre y el monto a mas o menos cinco por ciento`() {
        val anterior = listOf(gasto("sep", 1_800_000, "Hogar", septiembre))
        fun con(monto: Long, descripcion: String = "ARRIENDO") = recurrentesQueCambiaronDeCategoria(
            actual = listOf(gasto("oct", monto, "Otros", octubre, descripcion)),
            anterior = anterior,
            reglas = emptyList(),
            reglaDelMovimiento = emptyMap(),
            periodoAnterior = "2026-09",
        )
        assertEquals(1, con(1_890_000).size, "+5 % es el mismo")
        assertEquals(1, con(1_710_000).size, "−5 % es el mismo")
        assertTrue(con(1_900_000).isEmpty(), "más de 5 % ya es otro gasto")
        assertTrue(con(1_800_000, "Mercado del mes").isEmpty(), "otro nombre no es el mismo")
        assertNull(con(1_800_000).single().reglaId, "sin regla no hay regla que corregir")
    }

    @Test
    fun `la misma categoria, un traspaso o una cuota no se proponen`() {
        assertTrue(
            recurrentesQueCambiaronDeCategoria(
                actual = listOf(gasto("oct", 1_800_000, "Hogar", octubre)),
                anterior = listOf(gasto("sep", 1_800_000, "Hogar", septiembre)),
                reglas = emptyList(), reglaDelMovimiento = emptyMap(), periodoAnterior = "2026-09",
            ).isEmpty(),
        )
        assertTrue(
            recurrentesQueCambiaronDeCategoria(
                actual = listOf(gasto("oct", 980_000, CUOTA_CATEGORY, octubre, "Cuota carro")),
                anterior = listOf(gasto("sep", 980_000, "Transporte", septiembre, "Cuota carro")),
                reglas = emptyList(), reglaDelMovimiento = emptyMap(), periodoAnterior = "2026-09",
            ).isEmpty(),
            "la cuota se escribe con sus dos patas: no se recategoriza desde acá",
        )
        assertTrue(
            recurrentesQueCambiaronDeCategoria(
                actual = listOf(gasto("oct", 500_000, "Otros", octubre, "Ahorro").copy(transferId = "t1")),
                anterior = listOf(gasto("sep", 500_000, "Ahorro", septiembre, "Ahorro")),
                reglas = emptyList(), reglaDelMovimiento = emptyMap(), periodoAnterior = "2026-09",
            ).isEmpty(),
        )
    }

    @Test
    fun `una regla que ya tiene la categoria de antes no se toca`() {
        val c = recurrentesQueCambiaronDeCategoria(
            actual = listOf(gasto("oct", 1_800_000, "Otros", octubre)),
            anterior = listOf(gasto("sep", 1_800_000, "Hogar", septiembre)),
            reglas = listOf(reglaArriendo.copy(category = "Hogar")),
            reglaDelMovimiento = mapOf("oct" to "rr-arriendo"),
            periodoAnterior = "2026-09",
        ).single()
        assertNull(c.reglaId)
    }

    @Test
    fun `mientras no se resuelve, la comparacion lo cuenta donde estaba`() {
        val cambio = recurrentesQueCambiaronDeCategoria(
            actual = listOf(gasto("oct", 1_800_000, "Otros", octubre)),
            anterior = listOf(gasto("sep", 1_800_000, "Hogar", septiembre)),
            reglas = emptyList(), reglaDelMovimiento = emptyMap(), periodoAnterior = "2026-09",
        )
        val comparable = comoEnElPeriodoAnterior(mapOf("Otros" to 2_000_000L, "Mercado" to 300_000L), cambio)
        assertEquals(200_000L, comparable["Otros"], "Otros sin el arriendo")
        assertEquals(1_800_000L, comparable["Hogar"], "el arriendo, en Hogar como el período pasado")
        assertEquals(300_000L, comparable["Mercado"])
    }
}
