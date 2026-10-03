package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Lo que se sale de lo normal** (Ola 4): cada detector con su positivo, su negativo y su umbral.
 */
class LoQueSeSaleDeLoNormalTest {

    private val dia = 24L * 60 * 60 * 1000
    private val t0 = 1_790_000_000_000L
    private val cuentas = mapOf("banco" to "Bancolombia", "nu" to "Nu")
    private val diaDe: (Long) -> String = { "d${(it - t0) / dia}" }

    private fun gasto(
        id: String,
        nombre: String,
        monto: Long,
        dias: Double,
        cuenta: String = "banco",
        categoria: String = "Comida",
        moneda: String = "COP",
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = monto, currency = moneda,
        category = categoria, description = nombre, timestamp = t0 + (dias * dia).toLong(),
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    // ── Cobro duplicado ──

    @Test
    fun `dos cobros iguales en el mismo comercio y la misma cuenta son un duplicado`() {
        val avisos = cobrosDuplicados(
            listOf(gasto("a", "RAPPI COLOMBIA", 45_900L, 0.0), gasto("b", "RAPPI COLOMBIA", 45_900L, 1.0)),
            cuentas, diaDe,
        )
        val aviso = avisos.single()
        assertEquals(TipoDeAnomalia.COBRO_DUPLICADO, aviso.tipo)
        assertEquals("duplicado:a,b", aviso.huella)
        assertEquals(listOf("a", "b"), aviso.evidencia.map { it.id })
        assertTrue("2 cobros de \$45.900 en Bancolombia" in aviso.detalle, aviso.detalle)
    }

    @Test
    fun `otro monto, otra cuenta o mas de dos dias no son duplicado`() {
        val base = gasto("a", "RAPPI COLOMBIA", 45_900L, 0.0)
        assertTrue(cobrosDuplicados(listOf(base, gasto("b", "RAPPI COLOMBIA", 46_000L, 1.0)), cuentas, diaDe).isEmpty())
        assertTrue(cobrosDuplicados(listOf(base, gasto("b", "RAPPI COLOMBIA", 45_900L, 1.0, cuenta = "nu")), cuentas, diaDe).isEmpty())
        assertTrue(cobrosDuplicados(listOf(base, gasto("b", "RAPPI COLOMBIA", 45_900L, 2.5)), cuentas, diaDe).isEmpty())
        assertTrue(cobrosDuplicados(listOf(base, gasto("b", "MERCADO EXITO", 45_900L, 1.0)), cuentas, diaDe).isEmpty())
    }

    @Test
    fun `dos tintos iguales no avisan, el umbral del duplicado esta en veinte mil`() {
        assertTrue(cobrosDuplicados(listOf(gasto("a", "JUAN VALDEZ", 19_999L, 0.0), gasto("b", "JUAN VALDEZ", 19_999L, 0.5)), cuentas, diaDe).isEmpty())
        assertEquals(1, cobrosDuplicados(listOf(gasto("a", "JUAN VALDEZ", 20_000L, 0.0), gasto("b", "JUAN VALDEZ", 20_000L, 0.5)), cuentas, diaDe).size)
    }

    @Test
    fun `una cuota o un traspaso iguales no son duplicado`() {
        val cuota = gasto("a", "Cuota Vehiculo", 4_101_123L, 0.0, categoria = CUOTA_CATEGORY)
        assertTrue(cobrosDuplicados(listOf(cuota, cuota.copy(id = "b", timestamp = cuota.timestamp + 1000)), cuentas, diaDe).isEmpty())
        val traspaso = gasto("c", "A Nu", 500_000L, 0.0).copy(transferId = "tr1")
        assertTrue(cobrosDuplicados(listOf(traspaso, traspaso.copy(id = "d", transferId = "tr2")), cuentas, diaDe).isEmpty())
    }

    // ── Suscripción que subió ──

    private val netflix = Subscription(
        id = "sub_netflix", merchantKey = "netflix", displayName = "Netflix", amount = 44_900L, currency = "COP",
        dayOfMonth = 5, status = SubStatus.CONFIRMED, confidence = SubConfidence.HIGH, firstSeen = t0, lastSeen = t0, occurrences = 3,
    )
    private val periodoDe: (Long) -> String = { if (it < t0 + 30 * dia) "2026-09" else "2026-10" }

    private fun subidas(antes: Long, ahora: Long, sub: Subscription = netflix) = suscripcionesQueSubieron(
        listOf(sub),
        listOf(gasto("sep", "NETFLIX.COM", antes, 5.0, moneda = sub.currency), gasto("oct", "NETFLIX.COM", ahora, 35.0, moneda = sub.currency)),
        periodoDe, "2026-10", "2026-09", cuentas, diaDe,
    )

    @Test
    fun `netflix que subio de 44900 a 49900 avisa con los dos cobros`() {
        val aviso = subidas(44_900L, 49_900L).single()
        assertEquals("Netflix subió de precio", aviso.titulo)
        assertEquals(listOf("sep", "oct"), aviso.evidencia.map { it.id })
        assertTrue("(\$5.000 más)" in aviso.detalle, aviso.detalle)
        assertEquals("suscripcion:sub_netflix:oct", aviso.huella)
    }

    @Test
    fun `igual o mas barato no avisa, y una subida minima tampoco`() {
        assertTrue(subidas(44_900L, 44_900L).isEmpty())
        assertTrue(subidas(44_900L, 39_900L).isEmpty())
        // 2 % de $44.900 son $898: por debajo del umbral en pesos.
        assertTrue(subidas(44_900L, 45_790L).isEmpty())
        assertEquals(1, subidas(44_900L, 45_900L).size)
    }

    @Test
    fun `una suscripcion descartada o candidata no avisa`() {
        assertTrue(subidas(44_900L, 49_900L, netflix.copy(status = SubStatus.CANDIDATE)).isEmpty())
        assertTrue(subidas(44_900L, 49_900L, netflix.copy(status = SubStatus.DISMISSED)).isEmpty())
    }

    // ── Categoría muy por encima ──

    private fun periodo(vararg montos: Pair<String, Long>) = montos.mapIndexed { i, (cat, m) -> gasto("p$i$cat", "x$i", m, i.toDouble(), categoria = cat) }

    @Test
    fun `comida al doble del promedio avisa con sus gastos mas grandes`() {
        val actual = listOf(
            gasto("c1", "Carulla", 900_000L, 1.0), gasto("c2", "Rappi", 600_000L, 2.0),
            gasto("c3", "D1", 100_000L, 3.0), gasto("c4", "Ara", 50_000L, 4.0),
        )
        val avisos = categoriasPorEncima(actual, listOf(periodo("Comida" to 800_000L), periodo("Comida" to 700_000L)), "2026-10", cuentas, diaDe)
        val aviso = avisos.single()
        assertEquals("Comida va muy por encima de lo normal", aviso.titulo)
        assertTrue("Llevas \$1.650.000 este período; lo normal es \$750.000 (el promedio de los 2 anteriores)" in aviso.detalle, aviso.detalle)
        assertEquals(listOf("c1", "c2", "c3"), aviso.evidencia.map { it.id })
        assertEquals("categoria:Comida:2026-10", aviso.huella)
    }

    @Test
    fun `el umbral de categoria pide una vez y media y trescientos mil de diferencia`() {
        val antes = listOf(periodo("Comida" to 1_000_000L))
        // 1,4 veces: no.
        assertTrue(categoriasPorEncima(listOf(gasto("a", "x", 1_400_000L, 1.0)), antes, "p", cuentas, diaDe).isEmpty())
        // 1,5 veces y $500.000 de diferencia: sí.
        assertEquals(1, categoriasPorEncima(listOf(gasto("a", "x", 1_500_000L, 1.0)), antes, "p", cuentas, diaDe).size)
        // El triple, pero por $40.000: no se avisa por plata chica.
        assertTrue(
            categoriasPorEncima(listOf(gasto("a", "x", 60_000L, 1.0)), listOf(periodo("Comida" to 20_000L)), "p", cuentas, diaDe).isEmpty(),
        )
    }

    @Test
    fun `sin periodos anteriores no hay normal y no se avisa`() {
        assertTrue(categoriasPorEncima(listOf(gasto("a", "x", 9_000_000L, 1.0)), emptyList(), "p", cuentas, diaDe).isEmpty())
    }

    // ── Comercio nuevo con monto alto ──

    @Test
    fun `un comercio nunca visto con monto alto avisa`() {
        val historia = listOf(gasto("h", "CARULLA", 80_000L, 0.0))
        val nuevo = gasto("n", "ALKOSTO BOGOTA", 1_200_000L, 70.0, categoria = "Hogar")
        val aviso = comerciosNuevosConMontoAlto(listOf(nuevo), historia, desde = t0, cuentas, diaDe).single()
        assertEquals("Primera vez en ALKOSTO BOGOTA, y por \$1.200.000", aviso.titulo)
        assertEquals("nuevo:n", aviso.huella)
    }

    @Test
    fun `un comercio conocido, un monto bajo o sin historia suficiente no avisan`() {
        val historia = listOf(gasto("h", "ALKOSTO BOGOTA", 80_000L, 0.0))
        val compra = gasto("n", "ALKOSTO BOGOTA", 1_200_000L, 70.0)
        assertTrue(comerciosNuevosConMontoAlto(listOf(compra), historia, t0, cuentas, diaDe).isEmpty())
        assertTrue(comerciosNuevosConMontoAlto(listOf(compra.copy(amount = 499_999L)), emptyList(), t0, cuentas, diaDe).isEmpty())
        // Con 30 días de historia todo es «nuevo»: no se avisa.
        assertTrue(comerciosNuevosConMontoAlto(listOf(compra.copy(timestamp = t0 + 30 * dia)), emptyList(), t0, cuentas, diaDe).isEmpty())
        assertEquals(1, comerciosNuevosConMontoAlto(listOf(compra.copy(amount = 500_000L)), emptyList(), t0, cuentas, diaDe).size)
    }
}
