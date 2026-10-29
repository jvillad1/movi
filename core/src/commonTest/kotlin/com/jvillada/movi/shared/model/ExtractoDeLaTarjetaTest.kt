package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 62 · el extracto de la tarjeta es una suma por fechas
 *
 * El caso de la revisión del 29-oct: la Nu con corte el 20 y pago el 5. Hasta el corte había
 * $1.392.700 en compras, después del corte $290.300, y ya se pagaron $100.000: la deuda es
 * $1.583.000, y del extracto del 20 de octubre quedan $1.292.700.
 */
class ExtractoDeLaTarjetaTest {

    private val nu = CardTerms(accountId = "nt", bank = "Nu", cutoffDay = 20, paymentDay = 5, pagoMinimo = 180_000L)
    private val hoy = LocalDate(2026, 10, 29)

    private var siguiente = 0
    private fun ev(
        fecha: String,
        tipo: TransactionType,
        monto: Long,
        moneda: String = "COP",
        id: String = "e${siguiente++}",
        hora: Int = 12,
    ) = FinancialEvent(
        id = id,
        accountId = "nt",
        type = tipo,
        amount = monto,
        currency = moneda,
        category = if (tipo == TransactionType.EXPENSE) "Mercado" else CARD_PAYMENT_CATEGORY,
        description = "",
        timestamp = LocalDate.parse(fecha).atTime(LocalTime(hora, 0)).toInstant(AppTimeZone.zone).toEpochMilliseconds(),
    )

    private fun compra(fecha: String, monto: Long, moneda: String = "COP", id: String = "e${siguiente++}") =
        ev(fecha, TransactionType.EXPENSE, monto, moneda, id)

    private fun pago(fecha: String, monto: Long, moneda: String = "COP") = ev(fecha, TransactionType.INCOME, monto, moneda)

    /** La Nu del 29-oct: el extracto anterior pagado entero, compras del ciclo y después del corte. */
    private val delCiclo = listOf(
        compra("2026-09-10", 800_000L),
        pago("2026-10-03", 800_000L),
        compra("2026-09-25", 600_000L),
        compra("2026-10-12", 792_700L),
        compra("2026-10-24", 190_300L),
        compra("2026-10-27", 100_000L),
        pago("2026-10-26", 100_000L),
    )

    @Test
    fun `con compras antes y despues del corte, el extracto es lo de hasta el corte y lo demas va al proximo`() {
        val e = extractoDeLaTarjeta(nu, "COP", delCiclo, hoy)!!

        assertEquals("2026-10-20", e.corte)
        assertEquals("2026-11-05", e.fechaDePago)
        assertEquals(1_292_700L, e.facturado)
        assertEquals(100_000L, e.pagadoDesdeElCorte)
        assertEquals(180_000L, e.minimo)
        assertEquals(290_300L, e.despuesDelCorte)
        // Las dos mitades suman la deuda de hoy.
        val deuda = delCiclo.sumOf { signedDelta(AccountType.CREDIT_CARD, it.type, it.amount) }
        assertEquals(deuda, e.facturado + e.despuesDelCorte)
    }

    @Test
    fun `una compra del mismo dia del corte cuenta en ese extracto`() {
        val e = extractoDeLaTarjeta(nu, "COP", listOf(compra("2026-10-20", 50_000L), compra("2026-10-21", 7_000L)), hoy)!!
        assertEquals(50_000L, e.facturado)
        assertEquals(7_000L, e.despuesDelCorte)
    }

    @Test
    fun `antes del dia de corte del mes, el corte es el del mes anterior`() {
        val e = extractoDeLaTarjeta(nu, "COP", emptyList(), LocalDate(2026, 10, 15))!!
        assertEquals("2026-09-20", e.corte)
        assertEquals("2026-10-05", e.fechaDePago)
    }

    @Test
    fun `un pago parcial ya hecho baja el extracto, y uno de mas baja lo del proximo`() {
        val compras = listOf(compra("2026-10-10", 1_000_000L), compra("2026-10-22", 300_000L))

        val parcial = extractoDeLaTarjeta(nu, "COP", compras + pago("2026-10-25", 400_000L), hoy)!!
        assertEquals(600_000L, parcial.facturado)
        assertEquals(400_000L, parcial.pagadoDesdeElCorte)
        assertEquals(300_000L, parcial.despuesDelCorte)

        val deMas = extractoDeLaTarjeta(nu, "COP", compras + pago("2026-10-25", 1_100_000L), hoy)!!
        assertEquals(0L, deMas.facturado, "el extracto quedó pagado")
        assertEquals(1_000_000L, deMas.pagadoDesdeElCorte)
        assertEquals(200_000L, deMas.despuesDelCorte, "los 100.000 de más van contra lo del próximo")
    }

    @Test
    fun `un pago anterior al corte ya esta dentro de la deuda al corte`() {
        val e = extractoDeLaTarjeta(
            nu, "COP",
            listOf(compra("2026-10-01", 500_000L), pago("2026-10-15", 200_000L)),
            hoy,
        )!!
        assertEquals(300_000L, e.facturado)
        assertEquals(0L, e.pagadoDesdeElCorte)
    }

    @Test
    fun `sin cuotas conocidas lo dice, y no inventa ninguna`() {
        val e = extractoDeLaTarjeta(nu, "COP", listOf(compra("2026-10-05", 3_600_000L)), hoy)!!
        assertEquals(3_600_000L, e.facturado, "sin saber las cuotas, cuenta la compra entera")
        assertTrue(e.puedeIncluirDiferidos)
    }

    @Test
    fun `con las cuotas conocidas, al corte cuenta solo lo facturado de la compra`() {
        // 36 cuotas de $100.000: comprada el 5-oct, el corte del 20-oct factura la primera.
        val compraDiferida = compra("2026-10-05", 3_600_000L, id = "diferida")
        val e = extractoDeLaTarjeta(
            nu, "COP", listOf(compraDiferida, compra("2026-10-24", 50_000L, id = "despues", )), hoy,
            diferidos = listOf(DiferidoConocido("diferida", 36)),
        )!!
        assertEquals(100_000L, e.facturado)
        assertFalse(e.puedeIncluirDiferidos, "todo lo facturado tiene sus cuotas conocidas")
        assertEquals(50_000L, e.despuesDelCorte)

        // Tres cortes después (ya se pagaron las dos primeras), se factura la tercera.
        val enDiciembre = extractoDeLaTarjeta(
            nu, "COP",
            listOf(compraDiferida, pago("2026-11-01", 100_000L), pago("2026-12-01", 100_000L)),
            LocalDate(2026, 12, 22),
            diferidos = listOf(DiferidoConocido("diferida", 36)),
        )!!
        assertEquals("2026-12-20", enDiciembre.corte)
        assertEquals(100_000L, enDiciembre.facturado)
    }

    @Test
    fun `una compra diferida conocida despues del corte va al proximo con su primera cuota`() {
        val e = extractoDeLaTarjeta(
            nu, "COP", listOf(compra("2026-10-24", 1_200_000L, id = "tv")), hoy,
            diferidos = listOf(DiferidoConocido("tv", 12)),
        )!!
        assertEquals(0L, e.facturado)
        assertEquals(100_000L, e.despuesDelCorte)
    }

    @Test
    fun `lo pagado entero antes del ciclo no hace sospechar diferidos`() {
        val e = extractoDeLaTarjeta(
            nu, "COP",
            listOf(compra("2026-08-01", 900_000L, id = "vieja"), pago("2026-09-01", 900_000L), compra("2026-10-01", 40_000L, id = "nueva")),
            hoy,
            diferidos = listOf(DiferidoConocido("nueva", 1)),
        )!!
        assertEquals(40_000L, e.facturado)
        // «nueva» a 1 cuota no es un diferido: es una compra sin cuotas que digan otra cosa.
        assertTrue(e.puedeIncluirDiferidos)

        val conocida = extractoDeLaTarjeta(
            nu, "COP",
            listOf(compra("2026-08-01", 900_000L, id = "vieja"), pago("2026-09-01", 900_000L), compra("2026-10-01", 40_000L, id = "nueva")),
            hoy,
            diferidos = listOf(DiferidoConocido("nueva", 2)),
        )!!
        assertFalse(conocida.puedeIncluirDiferidos, "la compra vieja se pagó entera: no cuenta")
    }

    @Test
    fun `otra moneda no se mezcla`() {
        val eventos = listOf(compra("2026-10-10", 500_000L), compra("2026-10-11", 20L, moneda = "USD"), compra("2026-10-25", 30L, moneda = "USD"))

        val enPesos = extractoDeLaTarjeta(nu, "COP", eventos, hoy)!!
        assertEquals(500_000L, enPesos.facturado)
        assertEquals(0L, enPesos.despuesDelCorte)
        assertEquals("COP", enPesos.moneda)

        val enDolares = extractoDeLaTarjeta(nu.copy(pagoMinimo = null), "USD", eventos, hoy)!!
        assertEquals(20L, enDolares.facturado)
        assertEquals(30L, enDolares.despuesDelCorte)
        assertEquals("USD", enDolares.moneda)
        assertNull(enDolares.minimo)
    }

    @Test
    fun `sin dia de corte no hay extracto`() {
        assertNull(extractoDeLaTarjeta(nu.copy(cutoffDay = null), "COP", delCiclo, hoy))
    }

    @Test
    fun `el corte del 31 y el pago del 31 caen en el ultimo dia de un mes corto`() {
        val t = nu.copy(cutoffDay = 31, paymentDay = 15)
        assertEquals(LocalDate(2026, 2, 28), corteMasReciente(31, LocalDate(2026, 3, 10)))
        assertEquals(LocalDate(2026, 3, 15), fechaDePagoDespuesDe(LocalDate(2026, 2, 28), 15))
        assertEquals(LocalDate(2026, 4, 30), fechaDePagoDespuesDe(LocalDate(2026, 4, 2), 31))
        assertEquals("2026-02-28", extractoDeLaTarjeta(t, "COP", emptyList(), LocalDate(2026, 3, 10))!!.corte)
    }
}
