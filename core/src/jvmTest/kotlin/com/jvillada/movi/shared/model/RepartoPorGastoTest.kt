package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 97 · partes distintas por gasto
 *
 * Cada gasto compartido puede llevar su reparto (porcentajes, montos exactos, «Solo de …»); sin él,
 * la regla del grupo. Las partes las arma siempre [partesDelGasto], la validación es una sola
 * ([problemaDelReparto]) y lo que cuenta sigue siendo [montoContado].
 */
class RepartoPorGastoTest {

    private val caro = MiembroDelEspacio("dst_caro", "Caro")
    private val andres = MiembroDelEspacio("dst_andres", "Andrés")
    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), caro))
    private val viaje = Espacio(id = "esp_viaje", nombre = "Viaje", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), caro, andres))

    private fun porcentajes(yo: Int, ella: Int) =
        RepartoDelGasto(FormaDeRepartir.PORCENTAJES, porcentajes = mapOf(MIEMBRO_YO to yo, caro.id to ella))

    private fun montos(yo: Long, ella: Long) =
        RepartoDelGasto(FormaDeRepartir.MONTOS, montos = mapOf(MIEMBRO_YO to yo, caro.id to ella))

    private val soloDeCaro = RepartoDelGasto(FormaDeRepartir.SOLO_DE, soloDe = caro.id)

    // ── Las partes ──────────────────────────────────────────────────────────

    @Test
    fun `sin reparto propio, la regla del grupo de siempre`() {
        assertEquals(partesDelGasto(100_001, casa, MIEMBRO_YO), partesDelGasto(100_001, casa, MIEMBRO_YO, null))
        assertEquals(partesDelGasto(100_001, casa, MIEMBRO_YO), partesDelGasto(100_001, casa, MIEMBRO_YO, RepartoDelGasto.DEL_GRUPO))
        assertEquals(mapOf(MIEMBRO_YO to 50_001L, caro.id to 50_000L), partesDelGasto(100_001, casa, MIEMBRO_YO, null))
    }

    @Test
    fun `porcentajes - 70 30 con el peso que sobra para quien pago`() {
        assertEquals(mapOf(MIEMBRO_YO to 70_000L, caro.id to 30_000L), partesDelGasto(100_000, casa, MIEMBRO_YO, porcentajes(70, 30)))
        // 100.001 × 70 % = 70.000,7 → 70.000; × 30 % = 30.000,3 → 30.000; sobra 1, lo pone quien pagó.
        assertEquals(mapOf(MIEMBRO_YO to 70_001L, caro.id to 30_000L), partesDelGasto(100_001, casa, MIEMBRO_YO, porcentajes(70, 30)))
        assertEquals(mapOf(MIEMBRO_YO to 70_000L, caro.id to 30_001L), partesDelGasto(100_001, casa, caro.id, porcentajes(70, 30)))
    }

    @Test
    fun `porcentajes - si quien pago va en 0, el peso lo pone el de la parte mas grande`() {
        val tres = RepartoDelGasto(FormaDeRepartir.PORCENTAJES, porcentajes = mapOf(MIEMBRO_YO to 0, caro.id to 50, andres.id to 50))
        val partes = partesDelGasto(101, viaje, MIEMBRO_YO, tres)
        assertEquals(0L, partes[MIEMBRO_YO])
        assertEquals(101L, partes.values.sum())
    }

    @Test
    fun `montos exactos - tal cual si suman el total`() {
        assertEquals(mapOf(MIEMBRO_YO to 35_000L, caro.id to 65_000L), partesDelGasto(100_000, casa, MIEMBRO_YO, montos(35_000, 65_000)))
    }

    @Test
    fun `montos exactos - si el movimiento cambio de monto, en proporcion y el peso para quien pago`() {
        // Se guardaron 30.000 / 70.000 de un gasto de 100.000; el dueño lo corrigió a 100.001.
        val partes = partesDelGasto(100_001, casa, MIEMBRO_YO, montos(30_000, 70_000))
        assertEquals(mapOf(MIEMBRO_YO to 30_001L, caro.id to 70_000L), partes)
    }

    @Test
    fun `solo de Caro - todo es de ella`() {
        assertEquals(mapOf(MIEMBRO_YO to 0L, caro.id to 240_000L), partesDelGasto(240_000, casa, MIEMBRO_YO, soloDeCaro))
    }

    @Test
    fun `un solo de alguien que ya no esta en el grupo vuelve a la regla del grupo`() {
        val deOtro = RepartoDelGasto(FormaDeRepartir.SOLO_DE, soloDe = "dst_nadie")
        assertEquals(partesDelGasto(100_000, casa, MIEMBRO_YO), partesDelGasto(100_000, casa, MIEMBRO_YO, deOtro))
    }

    // ── La validación ───────────────────────────────────────────────────────

    @Test
    fun `porcentajes - suman 100`() {
        assertNull(problemaDelReparto(porcentajes(70, 30), 100_000, casa))
        assertEquals("Faltan 10 %", problemaDelReparto(porcentajes(60, 30), 100_000, casa))
        assertEquals("Te pasaste por 5 %", problemaDelReparto(porcentajes(75, 30), 100_000, casa))
        assertEquals("Cada porcentaje va de 0 a 100", problemaDelReparto(porcentajes(130, -30), 100_000, casa))
    }

    @Test
    fun `montos - suman exacto el total`() {
        assertNull(problemaDelReparto(montos(35_000, 65_000), 100_000, casa))
        assertEquals("Faltan $10.000", problemaDelReparto(montos(35_000, 55_000), 100_000, casa))
        assertEquals("Te pasaste por $1", problemaDelReparto(montos(35_001, 65_000), 100_000, casa))
        assertEquals("Ningún monto puede ser negativo", problemaDelReparto(montos(-1, 100_001), 100_000, casa))
    }

    @Test
    fun `solo se nombra a miembros del grupo`() {
        val conOtro = RepartoDelGasto(FormaDeRepartir.PORCENTAJES, porcentajes = mapOf(MIEMBRO_YO to 50, "dst_nadie" to 50))
        assertEquals("Ese reparto nombra a alguien que no está en el grupo", problemaDelReparto(conOtro, 100_000, casa))
        assertEquals("Elige de quién es el gasto", problemaDelReparto(RepartoDelGasto(FormaDeRepartir.SOLO_DE, soloDe = "x"), 1, casa))
        assertNull(problemaDelReparto(soloDeCaro, 100_000, casa))
        assertNull(problemaDelReparto(null, 100_000, casa))
    }

    // ── montoContado: lo único que decide cuánto cuenta ─────────────────────

    private fun repartido(monto: Long, reparto: RepartoDelGasto?, eventoId: String = "e_1") = GastoDelEspacio(
        id = "g_$eventoId", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = monto, categoria = "Mercado",
        descripcion = "Mercado", fecha = 1L, eventoId = eventoId, partes = partesDelGasto(monto, casa, MIEMBRO_YO, reparto),
        reparto = reparto,
    )

    private fun evento(id: String, monto: Long) = FinancialEvent(
        id = id, accountId = "acc", type = TransactionType.EXPENSE, amount = monto, category = "Mercado",
        description = "Mercado", timestamp = 1L, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    @Test
    fun `montoContado con 70 30 cuenta tu 70`() {
        val indice = loCompartidoDe(listOf(repartido(200_000, porcentajes(70, 30))), emptyList())
        assertEquals(140_000, montoContado(evento("e_1", 200_000).conLoCompartido(indice)))
    }

    @Test
    fun `montoContado con solo de Caro es cero y Caro te debe el gasto entero`() {
        val gasto = repartido(240_000, soloDeCaro)
        val indice = loCompartidoDe(listOf(gasto), emptyList())
        assertEquals(0, montoContado(evento("e_1", 240_000).conLoCompartido(indice)))
        val saldo = saldoDelEspacio(casa, listOf(gasto), emptyList())
        assertEquals(240_000, saldo.netoDe(MIEMBRO_YO))
        assertEquals(listOf(Reembolso(de = caro.id, a = MIEMBRO_YO, monto = 240_000)), saldo.reembolsos)
    }

    @Test
    fun `saldo con repartos distintos mezclados`() {
        // 50/50 de 100.000 (Caro debe 50.000) + 70/30 de 200.000 (Caro debe 60.000) + solo tuyo de 80.000
        // (nada) + Caro pagó 90.000 en montos 60.000 tuyos / 30.000 suyos (le debes 60.000).
        val deCaro = GastoDelEspacio(
            id = "g_caro", espacioId = casa.id, pagadoPor = caro.id, monto = 90_000, categoria = "Mercado",
            descripcion = "Mercado", fecha = 1L, partes = partesDelGasto(90_000, casa, caro.id, montos(60_000, 30_000)),
            reparto = montos(60_000, 30_000),
        )
        val gastos = listOf(
            repartido(100_000, null, "e_a"),
            repartido(200_000, porcentajes(70, 30), "e_b"),
            repartido(80_000, RepartoDelGasto(FormaDeRepartir.SOLO_DE, soloDe = MIEMBRO_YO), "e_c"),
            deCaro,
        )
        val saldo = saldoDelEspacio(casa, gastos, emptyList())
        assertEquals(50_000 + 60_000 - 60_000L, saldo.netoDe(MIEMBRO_YO))
        assertEquals(-50_000L, saldo.netoDe(caro.id))
        assertEquals(470_000, saldo.total)
    }

    // ── El wire ─────────────────────────────────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `un gasto sin la forma nueva se lee como la regla del grupo`() {
        val viejo = """{"id":"g","espacioId":"esp_casa","pagadoPor":"yo","monto":100000,"partes":{"yo":50000,"dst_caro":50000}}"""
        val g = json.decodeFromString(GastoDelEspacio.serializer(), viejo)
        assertNull(g.reparto)
        assertEquals(partesDelGasto(100_000, casa, MIEMBRO_YO), partesDelGasto(g.monto, casa, g.pagadoPor, g.reparto))
        val pedido = json.decodeFromString(CompartirMovimientoRequest.serializer(), """{"eventoId":"e","gastoId":"g"}""")
        assertNull(pedido.reparto)
    }

    @Test
    fun `el reparto viaja y vuelve igual`() {
        val g = repartido(100_000, porcentajes(70, 30))
        val vuelta = json.decodeFromString(GastoDelEspacio.serializer(), json.encodeToString(GastoDelEspacio.serializer(), g))
        assertEquals(g, vuelta)
    }

    @Test
    fun `la ultima vez con montos se lleva al monto nuevo`() {
        val r = repartoParaOtroMonto(montos(1_200_000, 1_200_000), 2_500_000, casa)!!
        assertEquals(mapOf(MIEMBRO_YO to 1_250_000L, caro.id to 1_250_000L), r.montos)
        assertNull(problemaDelReparto(r, 2_500_000, casa))
        assertEquals(porcentajes(70, 30), repartoParaOtroMonto(porcentajes(70, 30), 1, casa))
    }
}
