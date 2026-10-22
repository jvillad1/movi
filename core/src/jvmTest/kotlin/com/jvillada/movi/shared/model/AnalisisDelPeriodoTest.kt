package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Las series de «En qué se va» (Ola 4 · Ver la plata)
 *
 * El gráfico y la lista salen de [analisisDelPeriodo] y [filasDelAnalisis]. Lo que se cuida acá:
 * la suma por día, el período anterior a la misma altura (y que sin cubrirlo entero no haya
 * comparación), que **traspasos y pagos de tarjeta no cuenten** —ni lo que espera en «Por confirmar»
 * ni lo que pasa en una cuenta de deuda—, la semana en un período largo, las filas por comercio y
 * cuenta, y que el filtro de Movimientos deje pasar lo mismo que la fila cuenta.
 */
class AnalisisDelPeriodoTest {

    /** Corte 25: octubre va del 25-sep al 24-oct; septiembre, del 25-ago al 24-sep. */
    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val octubre = PeriodoFinanciero(2026, 10)
    private val tipos = mapOf(
        "ahorros" to AccountType.SAVINGS,
        "tarjeta" to AccountType.CREDIT_CARD,
        "credito" to AccountType.LOAN,
    )

    private var n = 0

    private fun mov(
        dia: String,
        monto: Long,
        categoria: String = "Comida",
        cuenta: String = "ahorros",
        tipo: TransactionType = TransactionType.EXPENSE,
        comercio: String? = null,
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
        hora: Int = 12,
    ) = FinancialEvent(
        id = "e${n++}",
        accountId = cuenta,
        type = tipo,
        amount = monto,
        category = categoria,
        description = comercio ?: "Gasto $n",
        merchant = comercio,
        timestamp = LocalDate.parse(dia).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + hora * 3_600_000L,
        reconciliationStatus = estado,
    )

    /** El primer movimiento del dueño: antes del período anterior, así que lo cubre entero. */
    private val apertura = mov("2026-08-01", 1_000_000L, OPENING_CATEGORY, tipo = TransactionType.INCOME)

    @Test
    fun `suma el gasto de cada dia del periodo y deja lo que no llego en cero`() {
        val a = analisisDelPeriodo(
            listOf(
                apertura,
                mov("2026-09-25", 50_000L),
                mov("2026-09-25", 30_000L, "Transporte"),
                mov("2026-09-27", 120_000L),
                // Las 11 p. m. del 24 de octubre en Bogotá todavía es octubre.
                mov("2026-10-24", 10_000L, hora = 23),
                // Ya es noviembre: no entra.
                mov("2026-10-25", 999_000L),
            ),
            octubre, corte25, hoy = LocalDate(2026, 10, 24), tiposDeCuenta = tipos,
        )
        assertEquals(LocalDate(2026, 9, 25), a.desde)
        assertEquals(LocalDate(2026, 10, 24), a.hasta)
        assertFalse(a.porSemana)
        assertEquals(30, a.tramos.size)
        assertEquals(80_000L, a.tramos[0].gasto)
        assertEquals(0L, a.tramos[1].gasto)
        assertEquals(120_000L, a.tramos[2].gasto)
        assertEquals(200_000L, a.tramos[2].acumulado)
        assertEquals(10_000L, a.tramos.last().gasto)
        assertEquals(210_000L, a.total)
        assertEquals(210_000L, a.tramos.last().acumulado)
        assertEquals(30, a.diasTranscurridos)
    }

    @Test
    fun `excluye traspasos, pagos de tarjeta, lo que espera en por confirmar, los ingresos y las cuentas de deuda`() {
        val a = analisisDelPeriodo(
            listOf(
                apertura,
                mov("2026-10-01", 100_000L),
                // Las dos patas de un traspaso.
                mov("2026-10-01", 500_000L, TRANSFER_CATEGORY),
                mov("2026-10-01", 500_000L, TRANSFER_CATEGORY, tipo = TransactionType.INCOME),
                // El pago de la tarjeta desde ahorros, y su pata en la tarjeta.
                mov("2026-10-02", 800_000L, CARD_PAYMENT_CATEGORY),
                mov("2026-10-02", 800_000L, CARD_PAYMENT_CATEGORY, cuenta = "tarjeta", tipo = TransactionType.INCOME),
                // La compra con tarjeta SÍ es gasto.
                mov("2026-10-03", 60_000L, cuenta = "tarjeta"),
                // Lo que espera en «Por confirmar» no suma.
                mov("2026-10-03", 70_000L, estado = ReconciliationStatus.UNCONFIRMED),
                // Un ajuste en una cuenta de deuda no es gasto.
                mov("2026-10-04", 2_000_000L, "Intereses", cuenta = "credito"),
                // Un ingreso tampoco.
                mov("2026-10-04", 9_000_000L, "Sueldo", tipo = TransactionType.INCOME),
                // Ni los dólares (la regla de «Gastos» es en pesos).
                mov("2026-10-05", 20L).copy(currency = "USD"),
            ),
            octubre, corte25, hoy = LocalDate(2026, 10, 10), tiposDeCuenta = tipos,
        )
        assertEquals(160_000L, a.total)
        assertEquals(listOf(100_000L, 60_000L), a.gastos.map { it.amount })
    }

    @Test
    fun `aun sin el tipo de la cuenta, un traspaso o un pago de tarjeta no cuentan`() {
        val a = analisisDelPeriodo(
            listOf(
                apertura,
                mov("2026-10-01", 100_000L, cuenta = "desconocida"),
                mov("2026-10-01", 500_000L, TRANSFER_CATEGORY, cuenta = "desconocida"),
                mov("2026-10-02", 800_000L, CARD_PAYMENT_CATEGORY, cuenta = "desconocida"),
            ),
            octubre, corte25, hoy = LocalDate(2026, 10, 10),
        )
        assertEquals(100_000L, a.total)
    }

    @Test
    fun `el anterior se compara a la misma altura y la linea sigue sus mismos dias`() {
        val a = analisisDelPeriodo(
            listOf(
                apertura,
                // Septiembre (25-ago → 24-sep): día 1, día 3 y día 20.
                mov("2026-08-25", 40_000L),
                mov("2026-08-27", 60_000L),
                mov("2026-09-13", 500_000L),
                // Octubre: día 1 y día 2.
                mov("2026-09-25", 70_000L),
                mov("2026-09-26", 20_000L),
            ),
            // Hoy es el día 5 de octubre (29-sep).
            octubre, corte25, hoy = LocalDate(2026, 9, 29), tiposDeCuenta = tipos,
        )
        assertEquals(5, a.diasTranscurridos)
        assertEquals(5, a.diasComparados)
        // A la misma altura: los primeros 5 días de septiembre, no el período entero.
        assertEquals(100_000L, a.anteriorAMismaAltura)
        assertTrue(a.conComparacion)
        assertEquals(40_000L, a.tramos[0].anterior)
        assertEquals(0L, a.tramos[1].anterior)
        assertEquals(60_000L, a.tramos[2].anterior)
        assertEquals(100_000L, a.tramos[2].acumuladoAnterior)
        // El día 20 de septiembre también está en su línea (la línea es el período pasado entero).
        assertEquals(600_000L, a.tramos[19].acumuladoAnterior)
        // Lo que todavía no llega.
        assertFalse(a.tramos[4].porVenir)
        assertTrue(a.tramos[5].porVenir)
    }

    @Test
    fun `sin el periodo anterior completo no hay comparacion`() {
        val a = analisisDelPeriodo(
            // El primer movimiento es del 1-sep: septiembre (desde el 25-ago) está a medio anotar.
            listOf(mov("2026-09-01", 300_000L), mov("2026-09-26", 50_000L)),
            octubre, corte25, hoy = LocalDate(2026, 10, 1), tiposDeCuenta = tipos,
        )
        assertNull(a.anteriorAMismaAltura)
        assertFalse(a.conComparacion)
        assertTrue(a.tramos.all { it.anterior == null && it.acumuladoAnterior == null })
        assertTrue(filasDelAnalisis(a, VistaDelAnalisis.CATEGORIA).all { it.contraElAnterior == null })
    }

    @Test
    fun `si el anterior era mas corto, la linea termina donde termino el`() {
        // Corte 1: marzo (31 días) contra febrero (28).
        val a = analisisDelPeriodo(
            listOf(mov("2026-01-10", 1L), mov("2026-02-28", 100_000L), mov("2026-03-31", 5_000L)),
            PeriodoFinanciero(2026, 3), PeriodSettings(), hoy = LocalDate(2026, 4, 2), tiposDeCuenta = tipos,
        )
        assertEquals(31, a.tramos.size)
        assertEquals(100_000L, a.tramos[27].anterior)
        assertNull(a.tramos[28].anterior)
        assertNull(a.tramos[30].acumuladoAnterior)
        // Un período cerrado se compara entero: los 28 días de febrero.
        assertEquals(28, a.diasComparados)
        assertEquals(100_000L, a.anteriorAMismaAltura)
    }

    @Test
    fun `un periodo de mas de 35 dias se dibuja por semanas`() {
        // Octubre arrancó a mano el 10-sep: 45 días hasta el 24-oct.
        val largo = PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-10" to "2026-09-10"))
        val a = analisisDelPeriodo(
            listOf(apertura, mov("2026-09-10", 10_000L), mov("2026-09-16", 20_000L), mov("2026-09-17", 40_000L)),
            octubre, largo, hoy = LocalDate(2026, 10, 24), tiposDeCuenta = tipos,
        )
        assertTrue(a.porSemana)
        assertEquals(7, a.tramos.size)
        assertEquals(LocalDate(2026, 9, 10), a.tramos[0].desde)
        assertEquals(LocalDate(2026, 9, 16), a.tramos[0].hasta)
        assertEquals(30_000L, a.tramos[0].gasto)
        assertEquals(40_000L, a.tramos[1].gasto)
        // La última semana es corta: 3 días (22 al 24 de octubre).
        assertEquals(LocalDate(2026, 10, 22), a.tramos.last().desde)
        assertEquals(LocalDate(2026, 10, 24), a.tramos.last().hasta)
    }

    @Test
    fun `las filas agrupan por categoria, comercio y cuenta con su porcentaje y su flecha`() {
        val a = analisisDelPeriodo(
            listOf(
                apertura,
                // Septiembre a la misma altura: Comida $100.000 en Rappi.
                mov("2026-08-26", 100_000L, comercio = "RAPPI*LAS DOCE"),
                // Octubre.
                mov("2026-09-26", 300_000L, comercio = "RAPPI*LAS DOCE"),
                mov("2026-09-27", 100_000L, "Transporte", cuenta = "tarjeta"),
                mov("2026-09-27", 100_000L, comercio = "Carnes Santa Elena"),
            ),
            octubre, corte25, hoy = LocalDate(2026, 9, 28), tiposDeCuenta = tipos,
        )
        val porCategoria = filasDelAnalisis(a, VistaDelAnalisis.CATEGORIA)
        assertEquals(listOf("Comida", "Transporte"), porCategoria.map { it.nombre })
        assertEquals(400_000L, porCategoria[0].gasto)
        assertEquals(0.8f, porCategoria[0].fraccion, 0.0001f)
        assertEquals(Tendencia.SUBE, porCategoria[0].contraElAnterior?.tendencia)
        assertEquals(300_000L, porCategoria[0].contraElAnterior?.diferencia)
        // Transporte no tuvo gasto el período pasado: sube por todo.
        assertEquals(0L, porCategoria[1].anterior)

        val porComercio = filasDelAnalisis(a, VistaDelAnalisis.COMERCIO)
        assertEquals(3, porComercio.size)
        assertEquals("Rappi", porComercio[0].nombre)
        assertEquals(300_000L, porComercio[0].gasto)
        assertEquals(2, porComercio.count { it.gasto == 100_000L })

        val porCuenta = filasDelAnalisis(a, VistaDelAnalisis.CUENTA, mapOf("ahorros" to "Bancolombia", "tarjeta" to "Visa"))
        assertEquals(listOf("Bancolombia", "Visa"), porCuenta.map { it.nombre })
        assertEquals(listOf(400_000L, 100_000L), porCuenta.map { it.gasto })
    }

    @Test
    fun `el filtro de una fila deja pasar exactamente lo que la fila suma`() {
        val eventos = listOf(
            apertura,
            mov("2026-09-26", 300_000L, comercio = "RAPPI*LAS DOCE"),
            mov("2026-09-27", 80_000L, comercio = "Rappi Restaurantes"),
            mov("2026-09-27", 100_000L, "Transporte", cuenta = "tarjeta"),
        )
        val a = analisisDelPeriodo(eventos, octubre, corte25, hoy = LocalDate(2026, 9, 28), tiposDeCuenta = tipos)
        VistaDelAnalisis.entries.forEach { vista ->
            filasDelAnalisis(a, vista).forEach { fila ->
                val filtro = FiltroDeMovimientos.deLaFila(fila, vista)
                val dejados = a.gastos.filter { filtro.deja(it) }
                assertEquals(fila.gasto, dejados.sumOf { it.amount }, "$vista · ${fila.nombre}")
            }
        }
        // Una barra deja pasar solo su día.
        val dia = FiltroDeMovimientos.delTramo(a.tramos[2], "27 sep")
        assertEquals(listOf(80_000L, 100_000L), eventos.filter { dia.deja(it) }.map { it.amount })
    }
}
