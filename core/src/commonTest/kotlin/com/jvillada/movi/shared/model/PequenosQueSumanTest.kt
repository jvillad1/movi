package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **«Pequeños que suman»**: los gastos chicos que se repiten, por comercio y si no por categoría, con
 * los umbrales elegidos ($50.000, 3 veces, $100.000).
 */
class PequenosQueSumanTest {

    private var n = 0

    private fun gasto(
        descripcion: String,
        monto: Long,
        categoria: String = "Comida",
        moneda: String = "COP",
        tipo: TransactionType = TransactionType.EXPENSE,
    ) = FinancialEvent(
        id = "ev${n++}",
        accountId = "acc",
        type = tipo,
        amount = monto,
        currency = moneda,
        category = categoria,
        description = descripcion,
        timestamp = 1_000L * n,
    )

    private fun veces(k: Int, descripcion: String, monto: Long, categoria: String = "Comida") =
        List(k) { gasto(descripcion, monto, categoria) }

    @Test
    fun `veintisiete tintos en el mismo comercio son un grupo con su total`() {
        val grupos = pequenosQueSuman(veces(27, "JUAN VALDEZ CAFE", 6_900))
        val tintos = grupos.single()
        assertEquals("JUAN VALDEZ CAFE", tintos.nombre)
        assertTrue(tintos.porComercio)
        assertEquals(27, tintos.veces)
        assertEquals(186_300L, tintos.total)
    }

    @Test
    fun `los que no arman un comercio se juntan por categoria, y ninguno cuenta dos veces`() {
        val gastos = veces(4, "JUAN VALDEZ CAFE", 30_000) +
            gasto("PANADERIA LA 80", 40_000) + gasto("TIENDA DON JOSE", 35_000) + gasto("FRUVER", 45_000)
        val grupos = pequenosQueSuman(gastos)
        assertEquals(2, grupos.size)
        val cafe = grupos.single { it.porComercio }
        val comida = grupos.single { !it.porComercio }
        assertEquals(120_000L, cafe.total)
        assertEquals("Comida", comida.nombre)
        assertEquals(3, comida.veces)
        assertEquals(120_000L, comida.total)
    }

    @Test
    fun `por encima de 50 mil no es chico`() {
        val grupos = pequenosQueSuman(veces(5, "RESTAURANTE", 50_001))
        assertTrue(grupos.isEmpty())
        // $50.000 justos sí.
        assertEquals(250_000L, pequenosQueSuman(veces(5, "RESTAURANTE", 50_000)).single().total)
    }

    @Test
    fun `dos veces no suman, tres si`() {
        assertTrue(pequenosQueSuman(veces(2, "TAXI", 49_000)).isEmpty())
        assertEquals(3, pequenosQueSuman(veces(3, "TAXI", 49_000)).single().veces)
    }

    @Test
    fun `un grupo que no llega a 100 mil no aparece`() {
        assertTrue(pequenosQueSuman(veces(10, "TINTO", 9_000)).isEmpty())
        assertEquals(100_000L, pequenosQueSuman(veces(10, "TINTO", 10_000)).single().total)
    }

    @Test
    fun `fuera lo que no es consumo, lo que no esta en pesos y lo que no es gasto`() {
        val gastos = veces(4, "CUOTA", 40_000, CUOTA_CATEGORY) +
            List(4) { gasto("SPOTIFY", 40_000, moneda = "USD") } +
            List(4) { gasto("REINTEGRO", 40_000, tipo = TransactionType.INCOME) }
        assertTrue(pequenosQueSuman(gastos).isEmpty())
    }

    @Test
    fun `un servicio conocido se nombra como se conoce`() {
        val grupos = pequenosQueSuman(veces(3, "NETFLIX.COM BOGOTA", 40_000, "Suscripciones"))
        assertEquals("Netflix", grupos.single().nombre)
    }

    @Test
    fun `de mayor a menor y como mucho cinco`() {
        val nombres = listOf("ALFA", "BRAVO", "CHARLIE", "DELTA", "ECO", "FOXTROT", "GOLF")
        val gastos = nombres.flatMapIndexed { i, nombre -> veces(3, "TIENDA $nombre", 40_000L + i * 1_000) }
        val grupos = pequenosQueSuman(gastos)
        assertEquals(MAX_PEQUENOS_QUE_SUMAN, grupos.size)
        assertEquals("TIENDA GOLF", grupos.first().nombre)
        assertEquals(grupos.map { it.total }.sortedDescending(), grupos.map { it.total })
    }
}
