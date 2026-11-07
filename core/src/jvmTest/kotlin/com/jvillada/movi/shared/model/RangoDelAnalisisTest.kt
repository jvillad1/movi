package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «En qué se va» con más que el período (vuelta 140, issue #696)
 *
 * Las funciones de [RangoDelAnalisis.kt]: los bordes del rango con el corte de Caro (30, día hábil
 * anterior), que un rango sume lo mismo que sus períodos, que compare solo contra un rango anterior
 * cubierto, la línea «va empezando» y el hueco. Ninguna existía antes de esta vuelta: sin el cambio,
 * este archivo no compila (y cada caso falla con la regla que cuida).
 */
class RangoDelAnalisisTest {

    /** El corte de Caro: el 30, y si cae en fin de semana o festivo, el día hábil anterior. */
    private val caro = PeriodSettings(cutoffDay = 30, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)

    private fun periodoDe(dia: String) = periodoDeLaFecha(dia, caro)!!

    private var n = 0
    private fun mov(
        dia: String,
        monto: Long,
        categoria: String = "Comida",
        tipo: TransactionType = TransactionType.EXPENSE,
    ) = FinancialEvent(
        id = "e${n++}", accountId = "ahorros", type = tipo, amount = monto, category = categoria,
        description = categoria,
        timestamp = LocalDate.parse(dia).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private val tipos = mapOf("ahorros" to AccountType.SAVINGS)

    // ── Los bordes ──────────────────────────────────────────────────────────

    @Test
    fun `3 meses son los ultimos 3 periodos de Caro con el 30 de agosto corrido al viernes 28`() {
        // Hoy 6-nov-2026: el período arranca el viernes 30-oct. El 30-ago-2026 es domingo → viernes 28.
        val periodos = periodosDelRango(periodoDe("2026-11-06"), RangoDelAnalisis.TRES_MESES, caro)
        assertEquals(
            listOf(LocalDate(2026, 8, 28), LocalDate(2026, 9, 30), LocalDate(2026, 10, 30)),
            periodos.map { inicioDelPeriodo(it, caro) },
        )
        val a = analisisDelRango(emptyList(), periodoDe("2026-11-06"), RangoDelAnalisis.TRES_MESES, caro, LocalDate(2026, 11, 6))
        assertEquals(LocalDate(2026, 8, 28), a.desde)
        assertEquals(LocalDate(2026, 11, 29), a.hasta)
        assertEquals("28 ago – 29 nov", nombreDelRango(a.desde, a.hasta))
        assertTrue(a.porSemana, "unos 90 días van por semana")
        assertFalse(a.porMes)
        assertEquals(3, a.cuantosPeriodos)
    }

    @Test
    fun `12 meses cruzan el anio y el nombre lo dice en el primer borde`() {
        val a = analisisDelRango(emptyList(), periodoDe("2026-11-06"), RangoDelAnalisis.DOCE_MESES, caro, LocalDate(2026, 11, 6))
        // 30-nov-2025 es domingo → viernes 28.
        assertEquals(LocalDate(2025, 11, 28), a.desde)
        assertEquals(LocalDate(2026, 11, 29), a.hasta)
        assertEquals("28 nov 2025 – 29 nov", nombreDelRango(a.desde, a.hasta))
        assertTrue(a.porMes, "un año va por meses")
        assertEquals(12, a.tramos.size)
        assertEquals(12, a.cuantosPeriodos)
    }

    @Test
    fun `el fin de febrero y el festivo de San Pedro corren el arranque al dia habil anterior`() {
        // Febrero no tiene 30: el corte cae el último día (sábado 28-feb-2026) → viernes 27.
        val conFebrero = periodosDelRango(periodoDe("2026-04-10"), RangoDelAnalisis.TRES_MESES, caro)
        assertEquals(
            listOf(LocalDate(2026, 1, 30), LocalDate(2026, 2, 27), LocalDate(2026, 3, 30)),
            conFebrero.map { inicioDelPeriodo(it, caro) },
        )
        // San Pedro y San Pablo 2025 se corrió al lunes 30 de junio (festivo) → viernes 27.
        val conFestivo = periodosDelRango(periodoDe("2025-08-15"), RangoDelAnalisis.TRES_MESES, caro)
        assertEquals(
            listOf(LocalDate(2025, 5, 30), LocalDate(2025, 6, 27), LocalDate(2025, 7, 30)),
            conFestivo.map { inicioDelPeriodo(it, caro) },
        )
    }

    @Test
    fun `en la quincena 3 meses son 6 quincenas`() {
        val quincenal = PeriodSettings(cutoffDay = 15, frecuencia = FrecuenciaDelPeriodo.QUINCENAL, segundoDia = 30)
        assertEquals(6, cuantosPeriodosDelRango(RangoDelAnalisis.TRES_MESES, quincenal))
        assertEquals(24, cuantosPeriodosDelRango(RangoDelAnalisis.DOCE_MESES, quincenal))
        assertEquals(3, cuantosPeriodosDelRango(RangoDelAnalisis.TRES_MESES, caro))
    }

    // ── La suma ─────────────────────────────────────────────────────────────

    @Test
    fun `un rango suma lo mismo que la suma de sus periodos`() {
        val hoy = LocalDate(2026, 11, 6)
        val eventos = listOf(
            mov("2026-06-01", 1L, OPENING_CATEGORY, TransactionType.INCOME),
            mov("2026-08-29", 100_000L, "Comida"),
            mov("2026-09-15", 250_000L, "Transporte"),
            mov("2026-09-29", 40_000L, "Comida"),
            mov("2026-10-02", 70_000L, "Salud"),
            mov("2026-11-03", 30_000L, "Comida"),
            // Un traspaso no es gasto ni en un rango.
            mov("2026-09-20", 5_000_000L, TRANSFER_CATEGORY),
        )
        val ultimo = periodoDe("2026-11-06")
        val rango = analisisDelRango(eventos, ultimo, RangoDelAnalisis.TRES_MESES, caro, hoy, tipos)
        val porPeriodo = periodosDelRango(ultimo, RangoDelAnalisis.TRES_MESES, caro)
            .sumOf { analisisDelPeriodo(eventos, it, caro, hoy, tipos).total }
        assertEquals(porPeriodo, rango.total)
        assertEquals(490_000L, rango.total)
        assertEquals(rango.total, rango.tramos.sumOf { it.gasto }, "las barras suman el total")
        // La lista suma el rango entero.
        val filas = filasDelAnalisis(rango, VistaDelAnalisis.CATEGORIA)
        assertEquals(listOf("Transporte", "Comida", "Salud"), filas.map { it.nombre })
        assertEquals(170_000L, filas.first { it.nombre == "Comida" }.gasto)

        // Por mes: cada barra es un período, con lo mismo que su análisis solo.
        val anio = analisisDelRango(eventos, ultimo, RangoDelAnalisis.DOCE_MESES, caro, hoy, tipos)
        assertEquals(
            periodosDelRango(ultimo, RangoDelAnalisis.DOCE_MESES, caro).map { analisisDelPeriodo(eventos, it, caro, hoy, tipos).total },
            anio.tramos.map { it.gasto },
        )
    }

    @Test
    fun `con un periodo es exactamente el analisis de siempre`() {
        val hoy = LocalDate(2026, 11, 6)
        val eventos = listOf(mov("2026-08-01", 1L, OPENING_CATEGORY, TransactionType.INCOME), mov("2026-11-02", 9_000L))
        assertEquals(
            analisisDelPeriodo(eventos, periodoDe("2026-11-06"), caro, hoy, tipos),
            analisisDelRango(eventos, periodoDe("2026-11-06"), RangoDelAnalisis.PERIODO, caro, hoy, tipos),
        )
    }

    // ── La comparación ──────────────────────────────────────────────────────

    @Test
    fun `el rango compara contra el anterior del mismo largo solo si esta cubierto entero`() {
        val hoy = LocalDate(2026, 11, 6)
        val ultimo = periodoDe("2026-11-06")
        // Los 3 anteriores van del 29-may (el 30 es sábado) al 27-ago. Con el primer movimiento en julio, no hay flechas.
        val desdeJulio = listOf(mov("2026-07-10", 80_000L), mov("2026-09-10", 50_000L))
        val sin = analisisDelRango(desdeJulio, ultimo, RangoDelAnalisis.TRES_MESES, caro, hoy, tipos)
        assertNull(sin.anteriorAMismaAltura)
        assertTrue(filasDelAnalisis(sin, VistaDelAnalisis.CATEGORIA).all { it.contraElAnterior == null })
        assertEquals(LocalDate(2026, 5, 29), sin.desdeDelAnterior)
        assertEquals(LocalDate(2026, 8, 27), sin.hastaDelAnterior)
        assertTrue(pieDeLasFlechasDelRango(sin, RangoDelAnalisis.TRES_MESES).startsWith("Sin flechas"))

        // Con un movimiento antes del 29-may, sí: el gasto de julio está dentro de la altura.
        val cubierto = desdeJulio + mov("2026-05-01", 1L, OPENING_CATEGORY, TransactionType.INCOME)
        val con = analisisDelRango(cubierto, ultimo, RangoDelAnalisis.TRES_MESES, caro, hoy, tipos)
        assertEquals(80_000L, con.anteriorAMismaAltura)
        assertNotNull(filasDelAnalisis(con, VistaDelAnalisis.CATEGORIA).single().contraElAnterior)
        assertTrue(pieDeLasFlechasDelRango(con, RangoDelAnalisis.TRES_MESES).startsWith("Las flechas comparan con los 3 meses anteriores"))
    }

    // ── «Va empezando» ──────────────────────────────────────────────────────

    private fun caroDeVerdad(): List<FinancialEvent> {
        val historia = (0 until 30).map { i ->
            val dia = LocalDate(2026, 7, 1).plusDays(i * 3)
            mov(dia.toString(), 10_000L + i, listOf("Comida", "Transporte", "Impuestos", "Home", "Mercado", "Créditos")[i % 6])
        }
        val delPeriodo = listOf(
            mov("2026-11-01", 4_000_000L, "Hipoteca"),
            mov("2026-11-02", 300_000L, "Hija"),
            mov("2026-11-03", 200_000L, "Tirze"),
            mov("2026-11-04", 90_000L, "Salud"),
            mov("2026-11-05", 50_000L, "Hija"),
            mov("2026-11-06", 20_000L, "Salud"),
        )
        return historia + delPeriodo
    }

    private fun LocalDate.plusDays(d: Int) = LocalDate.fromEpochDays(toEpochDays() + d)

    @Test
    fun `con 6 gastos y la historia de julio a septiembre el periodo va empezando y propone 3 meses`() {
        val hoy = LocalDate(2026, 11, 6)
        val eventos = caroDeVerdad()
        val a = analisisDelPeriodo(eventos, periodoDe("2026-11-06"), caro, hoy, tipos)
        val v = assertNotNull(periodoQueVaEmpezando(a, eventos, caro, hoy, tipos))
        assertEquals(6, v.gastos)
        assertEquals(LocalDate(2026, 7, 1), v.historiaDesde)
        assertEquals(RangoDelAnalisis.TRES_MESES, v.rango)
        assertEquals("Este período va empezando: llevas 6 movimientos. Tienes historia desde julio.", textoDelPeriodoQueVaEmpezando(v, 2026))
        assertEquals("Ver 3 meses", accionDelPeriodoQueVaEmpezando(v))
    }

    @Test
    fun `el umbral es 10 gastos y 5 categorias, y sin historia antes no hay linea`() {
        val hoy = LocalDate(2026, 11, 20)
        val historia = listOf(mov("2026-09-05", 10_000L))
        fun conDelPeriodo(cuantos: Int, categorias: Int) = historia + (0 until cuantos).map { i ->
            mov(LocalDate(2026, 11, 1).plusDays(i % 19).toString(), 1_000L, "Cat ${i % categorias}")
        }
        fun linea(eventos: List<FinancialEvent>) =
            periodoQueVaEmpezando(analisisDelPeriodo(eventos, periodoDe("2026-11-20"), caro, hoy, tipos), eventos, caro, hoy, tipos)

        assertNull(linea(conDelPeriodo(10, 5)), "10 gastos en 5 categorías ya dicen algo")
        assertNotNull(linea(conDelPeriodo(9, 5)), "9 gastos todavía no")
        assertNotNull(linea(conDelPeriodo(20, 4)), "4 categorías todavía no")
        assertNull(linea((0 until 3).map { mov("2026-11-0${it + 1}", 1_000L) }), "sin historia antes no hay a dónde mandarlo")

        // Un período ya cerrado no «va empezando».
        val eventos = conDelPeriodo(3, 3)
        val pasado = analisisDelPeriodo(eventos, periodoDe("2026-10-10"), caro, hoy, tipos)
        assertNull(periodoQueVaEmpezando(pasado, eventos, caro, hoy, tipos))
    }

    @Test
    fun `si la historia es mas vieja que 3 meses propone 12, y mas vieja que 12 nada`() {
        val hoy = LocalDate(2026, 11, 6)
        val a = { e: List<FinancialEvent> -> analisisDelPeriodo(e, periodoDe("2026-11-06"), caro, hoy, tipos) }
        val deMarzo = listOf(mov("2026-03-10", 10_000L), mov("2026-11-02", 5_000L))
        assertEquals(RangoDelAnalisis.DOCE_MESES, periodoQueVaEmpezando(a(deMarzo), deMarzo, caro, hoy, tipos)?.rango)
        val deHaceDosAnios = listOf(mov("2024-03-10", 10_000L), mov("2026-11-02", 5_000L))
        assertNull(periodoQueVaEmpezando(a(deHaceDosAnios), deHaceDosAnios, caro, hoy, tipos))
        val v = PeriodoQueVaEmpezando(1, LocalDate(2025, 7, 3), RangoDelAnalisis.DOCE_MESES)
        assertEquals("Este período va empezando: llevas 1 movimiento. Tienes historia desde julio de 2025.", textoDelPeriodoQueVaEmpezando(v, 2026))
    }

    // ── El hueco ────────────────────────────────────────────────────────────

    @Test
    fun `octubre sin movimientos entre septiembre y noviembre es un hueco`() {
        val periodos = periodosDelRango(periodoDe("2026-11-06"), RangoDelAnalisis.TRES_MESES, caro)
        val huecos = huecosEnLaHistoria(caroDeVerdad(), periodos, caro)
        assertEquals(listOf(HuecoEnLaHistoria(LocalDate(2026, 9, 30), LocalDate(2026, 10, 29))), huecos)
        assertEquals(
            "No hay movimientos del 30 sep al 29 oct. Si tienes el extracto, súbelo y aparecen aquí.",
            textoDelHueco(huecos.single()),
        )
    }

    @Test
    fun `antes del primer movimiento no hay hueco, ni en el periodo en curso vacio, y los vacios seguidos son uno`() {
        val doce = periodosDelRango(periodoDe("2026-11-06"), RangoDelAnalisis.DOCE_MESES, caro)
        // Empezó a usar Movi en julio: los meses de antes no son huecos.
        val desdeJulio = listOf(mov("2026-07-05", 1_000L), mov("2026-08-05", 1_000L), mov("2026-09-05", 1_000L), mov("2026-10-05", 1_000L))
        assertEquals(emptyList(), huecosEnLaHistoria(desdeJulio, doce, caro), "y noviembre vacío es el en curso: nada después")
        // Agosto y septiembre vacíos entre julio y octubre: un solo hueco.
        val conDosVacios = listOf(mov("2026-07-05", 1_000L), mov("2026-10-05", 1_000L))
        assertEquals(
            listOf(HuecoEnLaHistoria(LocalDate(2026, 7, 30), LocalDate(2026, 9, 29))),
            huecosEnLaHistoria(conDosVacios, doce, caro),
        )
        // Un movimiento que no es gasto (un ingreso) también llena el período: el hueco es de movimientos.
        val conIngreso = conDosVacios + mov("2026-08-10", 1_000L, "Salario", TransactionType.INCOME)
        assertEquals(
            listOf(HuecoEnLaHistoria(LocalDate(2026, 8, 28), LocalDate(2026, 9, 29))),
            huecosEnLaHistoria(conIngreso, doce, caro),
        )
    }
}
