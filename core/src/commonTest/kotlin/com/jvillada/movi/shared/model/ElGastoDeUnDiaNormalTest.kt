package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 108 · el gasto de un día normal
 *
 * El diagnóstico del 2-nov-2026 (`diagnostico-108.md`): la caja del 24-nov decía −$7M porque el supuesto
 * del gasto diario era $471.350, el total de dos períodos cerrados (59 días, $27.809.676) dividido por sus
 * días. Ese total traía la obra de la casa (Gardenera, $17,2M), las compras con tarjeta ($3,1M, que la
 * caja ya resta cuando se paga la tarjeta) y la otra mitad del colegio de agosto ($1M). Acá se arma el
 * caso con esas cifras y se pide lo que el diagnóstico calculó: ≈ $90.700 por día.
 */
class ElGastoDeUnDiaNormalTest {

    private fun gasto(id: String, monto: Long, cuenta: String = "ahorros", dia: Long = 0L, categoria: String = "Comida") = FinancialEvent(
        id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = monto, category = categoria,
        description = id, timestamp = dia * 86_400_000L, reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    private val tarjetas = setOf("master-black", "nu-tarjeta")

    /**
     * Los dos períodos del diagnóstico, con cada componente de la tabla 1.5. El día a día va en compras
     * chicas, como en la vida real ($2,5M en septiembre y $2.400.623 en octubre).
     */
    private fun septiembre(): List<FinancialEvent> = listOf(
        gasto("Gardenera anticipo", 6_420_000L, dia = 1),
        gasto("Gardenera segunda", 3_500_000L, dia = 10),
        gasto("Colegio Hija", 3_000_000L, dia = 6, categoria = "Hija"),
        gasto("Colegio Hija · parte desde Bancolombia", 1_000_000L, dia = 6, categoria = "Hija"),
        gasto("Arreglo de la tubería", 1_200_000L, dia = 15, categoria = "Casa"),
        gasto("Contador", 450_000L, dia = 20, categoria = "Servicios"),
        gasto("Libros con tarjeta", 1_500_000L, cuenta = "master-black", dia = 12, categoria = "Hija"),
    ) + (1..5).map { gasto("Comida $it", 500_000L, dia = 16L + it) }

    private fun octubre(): List<FinancialEvent> = listOf(
        gasto("Gardenera tercera", 6_170_000L, dia = 40),
        gasto("Gardenera cierre", 1_073_570L, dia = 54),
        gasto("Tecnología con tarjeta", 1_595_483L, cuenta = "nu-tarjeta", dia = 45, categoria = "Tecnología"),
        gasto("Fútbol", 623L, dia = 50),
    ) + (1..4).map { gasto("Comida de octubre $it", 600_000L, dia = 45L + it) }

    /** El colegio: los $3M sellados y la otra parte de $1M, los dos fuera del variable (vuelta 108, B3). */
    private val delColegio = mapOf("Colegio Hija" to 3_000_000L, "Colegio Hija · parte desde Bancolombia" to 1_000_000L)

    @Test
    fun `antes, el supuesto repartia la obra y las tarjetas como gasto de todos los dias`() {
        // La cuenta de siempre (`gastoVariablePorDia`), con solo los $3M del colegio sellados.
        val antes = listOf(
            GastoVariableDeUnPeriodo("2026-09", gastoVariablePorDia(septiembre(), mapOf("Colegio Hija" to 3_000_000L)) { "d" }.values.sum(), 30),
            GastoVariableDeUnPeriodo("2026-10", gastoVariablePorDia(octubre(), emptyMap()) { "d" }.values.sum(), 29),
        )
        assertEquals(27_809_676L, antes.sumOf { it.total })
        assertEquals(471_350L, gastoDelDiaADia(antes).porDia)
    }

    @Test
    fun `sin tarjetas, sin gastos de una vez y sin la otra mitad del colegio da unos 90 mil por dia`() {
        val sep = gastoDeUnDiaNormalDelPeriodo("2026-09", 30, septiembre(), delColegio, tarjetas) { "d$it" }
        val oct = gastoDeUnDiaNormalDelPeriodo("2026-10", 29, octubre(), emptyMap(), tarjetas) { "d$it" }
        val gasto = gastoDelDiaADia(listOf(oct, sep))

        assertEquals(90_688L, gasto.porDia, "(4.900.623 de día a día + 450.000 del contador) / 59 días")
        assertEquals(gasto.porDia, gasto.estimado)
        assertNull(gasto.elegido)
        assertEquals(3_095_483L, gasto.conTarjeta, "las compras con tarjeta, aparte")
        assertEquals(17_163_570L + 1_200_000L, gasto.deUnaVez.sumOf { it.monto }, "la obra y la tubería")
        assertEquals("Gardenera anticipo", gasto.deUnaVez.first().nombre, "del más grande al más chico")
        assertTrue(gasto.deUnaVez.none { it.nombre == "Contador" }, "$450.000 vuelve: no es de una vez")
    }

    @Test
    fun `una compra con tarjeta en un periodo cerrado no sube el supuesto`() {
        val sinTarjeta = gastoDeUnDiaNormalDelPeriodo("2026-09", 30, listOf(gasto("mercado", 300_000L)), emptyMap(), tarjetas) { "d" }
        val conTarjeta = gastoDeUnDiaNormalDelPeriodo(
            "2026-09", 30, listOf(gasto("mercado", 300_000L), gasto("cena", 900_000L, cuenta = "master-black")), emptyMap(), tarjetas,
        ) { "d" }
        assertEquals(sinTarjeta.total, conTarjeta.total)
        assertEquals(900_000L, conTarjeta.conTarjeta)
    }

    @Test
    fun `un gasto de una vez desde el umbral no entra, y uno de un peso menos si`() {
        val p = gastoDeUnDiaNormalDelPeriodo(
            "2026-09", 30,
            listOf(gasto("obra", UMBRAL_DE_UN_GASTO_DE_UNA_VEZ), gasto("nevera", UMBRAL_DE_UN_GASTO_DE_UNA_VEZ - 1)),
            emptyMap(), emptySet(),
        ) { "d" }
        assertEquals(UMBRAL_DE_UN_GASTO_DE_UNA_VEZ - 1, p.total)
        assertEquals(listOf("obra"), p.deUnaVez.map { it.nombre })
    }

    @Test
    fun `el umbral mira la parte variable y un fijo grande pagado no es de una vez ni dia a dia`() {
        // El arriendo de $1,8M lo paga un fijo del checklist: su parte variable es 0.
        val p = gastoDeUnDiaNormalDelPeriodo("2026-09", 30, listOf(gasto("arriendo", 1_800_000L)), mapOf("arriendo" to 1_800_000L), emptySet()) { "d" }
        assertEquals(0L, p.total)
        assertTrue(p.deUnaVez.isEmpty())
    }

    @Test
    fun `el gasto que elige el duenio manda, y el estimado queda de referencia`() {
        val cerrados = listOf(GastoVariableDeUnPeriodo("2026-09", 3_000_000L, 30))
        val elegido = gastoDelDiaADia(cerrados, elegido = 120_000L)
        assertEquals(120_000L, elegido.porDia)
        assertEquals(100_000L, elegido.estimado)
        assertEquals(120_000L, elegido.elegido)
        // Sin período cerrado también: el dueño sabe cuánto gasta aunque Movi todavía no.
        assertEquals(80_000L, gastoDelDiaADia(emptyList(), elegido = 80_000L).porDia)
        // 0 es «usar el estimado».
        assertEquals(100_000L, gastoDelDiaADia(cerrados, elegido = 0L).porDia)
        assertNull(gastoDelDiaADia(emptyList()).porDia)
    }

    @Test
    fun `lo que el duenio escribe se valida igual en la hoja y en el server`() {
        assertNull(problemaDelGastoDeUnDiaNormal(0L))
        assertNull(problemaDelGastoDeUnDiaNormal(91_000L))
        assertTrue(problemaDelGastoDeUnDiaNormal(-1L) != null)
        assertTrue(problemaDelGastoDeUnDiaNormal(MAX_GASTO_DE_UN_DIA_NORMAL + 1) != null)
    }
}
