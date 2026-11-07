package com.jvillada.movi.server.ai

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El total de un comercio, sin base (vuelta 142, issue #697)
 *
 * Datos inventados: 43 pagos a un peaje electrónico con variantes del nombre, como los escribe el
 * banco. Lo que se fija es lo que el asistente no pudo decir: el total exacto, cuántos son, los tres
 * más recientes en orden, la otra moneda aparte y, si lo escrito casa con varios comercios, cada uno.
 */
class LoGastadoEnUnComercioTest {

    private val tipos = mapOf("aho" to AccountType.SAVINGS, "tc" to AccountType.CREDIT_CARD)
    private val nombres = mapOf("aho" to "Ahorros de prueba", "tc" to "Tarjeta de prueba")
    private val mensual = PeriodSettings(cutoffDay = 1)

    private fun gasto(
        id: String,
        nombre: String,
        monto: Long,
        fecha: String,
        cuenta: String = "aho",
        moneda: String = "COP",
        tipo: TransactionType = TransactionType.EXPENSE,
        categoria: String = "Transporte",
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
        hora: Int = 12,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, currency = moneda, category = categoria,
        description = nombre, timestamp = appDateToEpochMillis(LocalDate.parse(fecha)) + hora * 3_600_000L,
        reconciliationStatus = estado,
    )

    /** 40 «PAGO ELECTRONICO FLYPASS», 2 «Pago electronico Flypass» y 1 «… FLYPASS CXC»: 43 pagos. */
    private fun loDelPeaje(): List<FinancialEvent> {
        val base = LocalDate.parse("2026-07-01")
        val montos = (0 until 43).map { 5_000L + it * 317L }
        return montos.mapIndexed { i, monto ->
            val nombre = when (i) {
                41 -> "PAGO ELECTRONICO FLYPASS CXC"
                40, 39 -> "Pago electronico Flypass"
                else -> "PAGO ELECTRONICO FLYPASS"
            }
            gasto("p$i", nombre, monto, base.plusDays(i * 2L).toString(), hora = 8 + (i % 5))
        }
    }

    private fun sumar(eventos: List<FinancialEvent>, consulta: String, desde: String = "2026-01-01", hasta: String = "2026-11-06") =
        loGastadoEnUnComercio(
            eventos, consulta, tipos, nombres, mensual,
            appDateToEpochMillis(LocalDate.parse(desde)), appDateToEpochMillis(LocalDate.parse(hasta).plusDays(1)),
        )

    @Test
    fun `suma exacto los 43 pagos, con variantes del nombre y sin importar mayusculas`() {
        val pagos = loDelPeaje()
        val r = sumar(pagos, "flypass")

        assertEquals(43, r.pagos)
        assertEquals(pagos.sumOf { it.amount }, r.total)
        assertEquals("2026-07-01", r.primerPago)
    }

    @Test
    fun `los tres mas recientes salen en orden, del mas nuevo al mas viejo`() {
        // Dos del mismo día a distinta hora: manda la hora, no el orden en que llegaron.
        val pagos = loDelPeaje() + gasto("tarde", "PAGO ELECTRONICO FLYPASS", 9_900, "2026-10-26", hora = 18) +
            gasto("temprano", "PAGO ELECTRONICO FLYPASS", 1_100, "2026-10-26", hora = 7)
        val r = sumar(pagos.shuffled(kotlin.random.Random(7)), "FlyPass")

        val elUltimoDelPeaje = loDelPeaje().maxBy { it.timestamp }
        assertEquals(listOf("2026-10-26", "2026-10-26", "2026-09-23"), r.recientes.map { it.fecha })
        assertEquals(listOf(9_900L, 1_100L, elUltimoDelPeaje.amount), r.recientes.map { it.monto })
        assertEquals("Ahorros de prueba", r.recientes.first().cuenta)
    }

    @Test
    fun `lo que no es gasto no suma y la otra moneda va aparte`() {
        val pagos = loDelPeaje()
        val ruido = listOf(
            gasto("usd1", "FLYPASS ONLINE", 12, "2026-08-03", moneda = "USD"),
            gasto("usd2", "FLYPASS ONLINE", 8, "2026-09-03", moneda = "USD"),
            gasto("reintegro", "Reintegro Flypass", 50_000, "2026-08-04", tipo = TransactionType.INCOME),
            gasto("por-confirmar", "PAGO ELECTRONICO FLYPASS", 70_000, "2026-08-05", estado = ReconciliationStatus.UNCONFIRMED),
            gasto("pago-tc", "Pago Flypass tarjeta", 90_000, "2026-08-06", cuenta = "tc", categoria = CARD_PAYMENT_CATEGORY, tipo = TransactionType.INCOME),
            gasto("otro", "Almuerzo", 30_000, "2026-08-07"),
        )
        val r = sumar(pagos + ruido, "flypass")

        assertEquals(43, r.pagos)
        assertEquals(pagos.sumOf { it.amount }, r.total)
        assertEquals(listOf(GastoEnOtraMoneda("USD", 20, 2)), r.enOtraMoneda)
    }

    @Test
    fun `con dos comercios que casan, los lista por separado con su total`() {
        val pagos = loDelPeaje()
        val r = sumar(pagos, "flypass")

        val cxc = r.comercios.single { "CXC" in it.nombre }
        assertEquals(1, cxc.pagos)
        assertEquals(pagos.single { "CXC" in it.description }.amount, cxc.total)
        assertTrue(r.comercios.size >= 2, r.comercios.toString())
        assertEquals(r.total, r.comercios.sumOf { it.total })
        assertEquals(r.comercios.sortedByDescending { it.total }, r.comercios)
    }

    @Test
    fun `una marca del catalogo casa por la marca, y lo que solo contiene el texto va aparte`() {
        val eventos = listOf(
            gasto("d1a", "TIENDAS D1 CALLE 80", 45_000, "2026-10-01", categoria = "Mercado"),
            gasto("d1b", "Tiendas D1 Suba", 38_000, "2026-10-08", categoria = "Mercado"),
            gasto("otra", "PAGO D1SOFT LICENCIA", 20_000, "2026-10-09", categoria = "Tecnología"),
        )
        val r = sumar(eventos, "D1")

        assertEquals(103_000, r.total)
        assertEquals(listOf("D1" to 83_000L, "PAGO D1SOFT LICENCIA" to 20_000L), r.comercios.map { it.nombre to it.total })
    }

    @Test
    fun `el rango manda y el promedio es por periodo, contando los que no tuvieron pagos`() {
        val eventos = listOf(
            gasto("a", "PAGO ELECTRONICO FLYPASS", 10_000, "2026-07-10"),
            gasto("b", "PAGO ELECTRONICO FLYPASS", 20_000, "2026-09-10"),
            gasto("fuera", "PAGO ELECTRONICO FLYPASS", 99_000, "2026-06-30"),
        )
        val r = sumar(eventos, "flypass", desde = "2026-07-01", hasta = "2026-09-30")

        assertEquals(30_000, r.total)
        assertEquals(3, r.periodos.size)
        assertEquals(10_000, r.promedioPorPeriodo)
    }

    @Test
    fun `sin nada que case, total cero y sin promedio`() {
        val r = sumar(loDelPeaje(), "peaje del norte")
        assertEquals(0, r.pagos)
        assertNull(r.promedioPorPeriodo)
        assertTrue("Ningún gasto dice" in textoDeLoGastadoEnUnComercio(r, "del 2026-01-01 al 2026-11-06", mensual))
    }

    @Test
    fun `el texto dice el total arriba como la cifra de la respuesta, y lo acepta el verificador`() {
        val pagos = loDelPeaje()
        val r = sumar(pagos, "flypass")
        val texto = textoDeLoGastadoEnUnComercio(r, "del 2026-01-01 al 2026-11-06", mensual)

        assertTrue("TOTAL EN PESOS: ${pagos.sumOf { it.amount }} en 43 pagos" in texto, texto)
        val respuesta = "Llevas \$${miles(r.total)} en Flypass en 43 pagos, unos \$${miles(r.promedioPorPeriodo!!)} por período."
        assertEquals(emptyList(), cifrasSinRespaldo(respuesta, listOf(texto)), texto)
    }

    private fun miles(n: Long) = n.toString().reversed().chunked(3).joinToString(".").reversed()
}
