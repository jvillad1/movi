package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 126 · cada cuánto vence un pago fijo ([FrecuenciaDelPago], [vencimientoEnElPeriodo] por regla).
 *
 * Sin el cambio no compila: ni [RecurringRule.frecuencia] ni la regla por frecuencia existían, y toda regla
 * vencía todos los meses.
 */
class FrecuenciaDelPagoTest {

    private val calendario = PeriodSettings(cutoffDay = 1)
    private val delDueno = PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)

    private fun regla(
        frecuencia: FrecuenciaDelPago?,
        ancla: String?,
        dia: Int = ancla?.let { LocalDate.parse(it).dayOfMonth } ?: 15,
        id: String = "rr_x",
        haciaDeuda: String? = null,
    ) = RecurringRule(
        id = id, name = "Administración", category = "Vivienda", amount = 450_000L, dayOfMonth = dia,
        type = TransactionType.EXPENSE, frecuencia = frecuencia, primerVencimiento = ancla, haciaDeuda = haciaDeuda,
    )

    private fun mes(anio: Int, mes: Int) = PeriodoFinanciero(anio, mes)

    @Test
    fun `cada frecuencia vence en sus meses y en los demas no existe`() {
        val ancla = "2026-12-15"
        val esperado = mapOf(
            FrecuenciaDelPago.MENSUAL to listOf("2026-11-15", "2026-12-15", "2027-01-15", "2027-02-15", "2027-03-15", "2027-06-15", "2027-12-15"),
            FrecuenciaDelPago.BIMESTRAL to listOf(null, "2026-12-15", null, "2027-02-15", null, "2027-06-15", "2027-12-15"),
            FrecuenciaDelPago.TRIMESTRAL to listOf(null, "2026-12-15", null, null, "2027-03-15", "2027-06-15", "2027-12-15"),
            FrecuenciaDelPago.SEMESTRAL to listOf(null, "2026-12-15", null, null, null, "2027-06-15", "2027-12-15"),
            FrecuenciaDelPago.ANUAL to listOf(null, "2026-12-15", null, null, null, null, "2027-12-15"),
            FrecuenciaDelPago.UNA_VEZ to listOf(null, "2026-12-15", null, null, null, null, null),
        )
        val periodos = listOf(mes(2026, 11), mes(2026, 12), mes(2027, 1), mes(2027, 2), mes(2027, 3), mes(2027, 6), mes(2027, 12))
        esperado.forEach { (frecuencia, fechas) ->
            val r = regla(frecuencia, ancla)
            periodos.zip(fechas).forEach { (periodo, fecha) ->
                assertEquals(fecha, vencimientoEnElPeriodo(r, periodo, calendario)?.toString(), "$frecuencia en $periodo")
            }
        }
    }

    @Test
    fun `el ancla a fin de mes se recorta al largo de cada mes`() {
        val trimestral = regla(FrecuenciaDelPago.TRIMESTRAL, "2027-01-31")
        assertEquals("2027-04-30", vencimientoEnElPeriodo(trimestral, mes(2027, 4), calendario)?.toString())
        assertEquals("2027-07-31", vencimientoEnElPeriodo(trimestral, mes(2027, 7), calendario)?.toString())
        assertEquals("2027-10-31", vencimientoEnElPeriodo(trimestral, mes(2027, 10), calendario)?.toString())
        val bimestral = regla(FrecuenciaDelPago.BIMESTRAL, "2026-12-31")
        assertEquals("2027-02-28", vencimientoEnElPeriodo(bimestral, mes(2027, 2), calendario)?.toString())
        assertEquals("2027-04-30", vencimientoEnElPeriodo(bimestral, mes(2027, 4), calendario)?.toString())
        assertNull(vencimientoEnElPeriodo(bimestral, mes(2027, 3), calendario))
        val anual = regla(FrecuenciaDelPago.ANUAL, "2028-02-29")
        assertEquals("2029-02-28", vencimientoEnElPeriodo(anual, mes(2029, 2), calendario)?.toString())
    }

    @Test
    fun `con el corte 25 y el dia habil anterior el vencimiento cae en el periodo que lo contiene`() {
        // El 28 de diciembre cae en el período «enero de 2027» (arranca el jueves 24 de diciembre: el 25 es
        // Navidad); el de «diciembre» (25-nov al 23-dic) no lo trae.
        val trimestral = regla(FrecuenciaDelPago.TRIMESTRAL, "2026-12-28")
        assertNull(vencimientoEnElPeriodo(trimestral, mes(2026, 12), delDueno))
        assertEquals("2026-12-28", vencimientoEnElPeriodo(trimestral, mes(2027, 1), delDueno)?.toString())
        assertNull(vencimientoEnElPeriodo(trimestral, mes(2027, 2), delDueno))
        assertNull(vencimientoEnElPeriodo(trimestral, mes(2027, 3), delDueno))
        // Tres meses después: el 28 de marzo, en el período «abril» (arranca el 24 de marzo, Semana Santa).
        assertEquals("2027-03-28", vencimientoEnElPeriodo(trimestral, mes(2027, 4), delDueno)?.toString())
        // Un ancla del día 10 vence en el período que se llama por su mismo mes.
        val semestral = regla(FrecuenciaDelPago.SEMESTRAL, "2027-01-10")
        assertEquals("2027-01-10", vencimientoEnElPeriodo(semestral, mes(2027, 1), delDueno)?.toString())
        assertEquals("2027-07-10", vencimientoEnElPeriodo(semestral, mes(2027, 7), delDueno)?.toString())
        assertNull(vencimientoEnElPeriodo(semestral, mes(2027, 6), delDueno))
    }

    @Test
    fun `una sola vez vence solo en su ancla`() {
        val unaVez = regla(FrecuenciaDelPago.UNA_VEZ, "2027-01-20")
        assertTrue(unaVez.venceEnElMes(2027, 1))
        assertFalse(unaVez.venceEnElMes(2026, 12))
        assertFalse(unaVez.venceEnElMes(2027, 2))
        assertFalse(unaVez.venceEnElMes(2028, 1))
        assertEquals("2027-01-20", primerVencimientoDesde(unaVez, LocalDate(2026, 11, 3))?.toString())
        // Pasada su fecha ya no tiene «próximo»: si no se pagó, la sigue el server como vencida.
        assertNull(primerVencimientoDesde(unaVez, LocalDate(2027, 1, 21)))
        assertEquals("Una sola vez · 20 ene", cadenciaDelPago(unaVez, "2027-01-20"))
    }

    @Test
    fun `mensual y sin frecuencia dan exactamente lo de antes`() {
        // Lo de antes: el vencimiento por día ([vencimientoEnElPeriodo] con el día), para todo día, en dos
        // años de períodos y con los ajustes de tres usuarios distintos.
        val ajustes = listOf(
            calendario,
            delDueno,
            PeriodSettings(cutoffDay = 15, iniciosPropios = mapOf("2027-03" to "2027-02-12")),
        )
        ajustes.forEach { a ->
            var periodo = mes(2026, 9)
            repeat(26) {
                (1..31).forEach { dia ->
                    val antes = vencimientoEnElPeriodo(dia, periodo, a)
                    assertEquals(antes, vencimientoEnElPeriodo(regla(null, null, dia), periodo, a), "null $dia $periodo")
                    assertEquals(antes, vencimientoEnElPeriodo(regla(FrecuenciaDelPago.MENSUAL, null, dia), periodo, a), "MENSUAL $dia $periodo")
                    // Una no mensual sin ancla legible no se pierde: cuenta como mensual.
                    assertEquals(antes, vencimientoEnElPeriodo(regla(FrecuenciaDelPago.TRIMESTRAL, "15/12/2026", dia), periodo, a))
                }
                periodo = periodoSiguiente(periodo, a)
            }
        }
    }

    @Test
    fun `las reglas especiales siguen siendo de cada mes`() {
        val abono = regla(FrecuenciaDelPago.TRIMESTRAL, "2026-12-15", haciaDeuda = "acc_vehiculo")
        val cuota = regla(FrecuenciaDelPago.TRIMESTRAL, "2026-12-15", id = "${CREDIT_RULE_PREFIX}acc_1")
        val tarjeta = regla(FrecuenciaDelPago.TRIMESTRAL, "2026-12-15", id = "${CARD_RULE_PREFIX}acc_2")
        listOf(abono, cuota, tarjeta).forEach {
            assertEquals(FrecuenciaDelPago.MENSUAL, it.frecuenciaEfectiva)
            assertTrue(it.venceEnElMes(2027, 1))
        }
    }

    @Test
    fun `la cadencia se dice en una linea`() {
        assertNull(cadenciaDelPago(regla(null, null), "2026-12-15"))
        assertEquals("Cada 3 meses · el próximo, 15 dic", cadenciaDelPago(regla(FrecuenciaDelPago.TRIMESTRAL, "2026-12-15"), "2026-12-15"))
        assertEquals("Cada año · el próximo, 5 mar", cadenciaDelPago(regla(FrecuenciaDelPago.ANUAL, "2027-03-05"), "2027-03-05"))
        assertEquals("Cada 2 meses", cadenciaDelPago(regla(FrecuenciaDelPago.BIMESTRAL, "2026-12-15"), null))
    }

    @Test
    fun `la frecuencia pide su fecha y el abono es de cada mes`() {
        assertNull(problemaDeLaFrecuencia(null, null, esAbono = false))
        assertNull(problemaDeLaFrecuencia(FrecuenciaDelPago.MENSUAL, null, esAbono = true))
        assertEquals("Falta la fecha del próximo pago", problemaDeLaFrecuencia(FrecuenciaDelPago.SEMESTRAL, null, esAbono = false))
        assertEquals("Falta la fecha del pago", problemaDeLaFrecuencia(FrecuenciaDelPago.UNA_VEZ, "mañana", esAbono = false))
        assertNull(problemaDeLaFrecuencia(FrecuenciaDelPago.UNA_VEZ, "2027-01-20", esAbono = false))
        assertTrue(problemaDeLaFrecuencia(FrecuenciaDelPago.TRIMESTRAL, "2026-12-15", esAbono = true) != null)
        assertTrue(frecuenciaSirveEnDolares(FrecuenciaDelPago.ANUAL))
        assertFalse(frecuenciaSirveEnDolares(FrecuenciaDelPago.TRIMESTRAL))
    }

    @Test
    fun `ya pagaste se pregunta solo si la no mensual vence en este periodo y ya paso`() {
        val hoy = LocalDate(2026, 11, 20)
        // Vence el 10 de noviembre (este período, ya pasó): se pregunta.
        assertTrue(
            sePreguntaSiYaPagasteElDeEstePeriodo(
                TransactionType.EXPENSE, false, 10, hoy, calendario, FrecuenciaDelPago.TRIMESTRAL, "2026-11-10",
            ),
        )
        // Vence el 10 de diciembre: este período no lo trae.
        assertFalse(
            sePreguntaSiYaPagasteElDeEstePeriodo(
                TransactionType.EXPENSE, false, 10, hoy, calendario, FrecuenciaDelPago.TRIMESTRAL, "2026-12-10",
            ),
        )
        // Vence el 25 de noviembre: todavía no pasó.
        assertFalse(
            sePreguntaSiYaPagasteElDeEstePeriodo(
                TransactionType.EXPENSE, false, 25, hoy, calendario, FrecuenciaDelPago.UNA_VEZ, "2026-11-25",
            ),
        )
        // Mensual: lo de siempre (el día 10 de este período ya pasó).
        assertTrue(sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.EXPENSE, false, 10, hoy, calendario))
    }
}
