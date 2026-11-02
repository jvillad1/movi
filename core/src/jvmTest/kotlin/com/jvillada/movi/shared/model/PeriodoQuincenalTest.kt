package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 105 · el período quincenal
 *
 * Diseño: `docs/superpowers/specs/2026-11-02-periodo-quincenal-semanal-design.md` (§2 y las pruebas de
 * §4). Una quincena se identifica por su arranque nominal («2026-10-15»), arranca el día A o el B
 * recortado al largo del mes, se corre por fin de semana y festivo con la misma regla del mes, y una
 * excepción a mano vale a ±5 días.
 */
class PeriodoQuincenalTest {

    private val quinceYUltimo = PeriodSettings(cutoffDay = 15, frecuencia = FrecuenciaDelPeriodo.QUINCENAL, segundoDia = 31)
    private val quinceYUltimoHabil = quinceYUltimo.copy(siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)

    private fun millis(d: LocalDate): Long = d.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()
    private fun dia(iso: String) = LocalDate.parse(iso)

    @Test
    fun `el 15 y el ultimo dia - dos quincenas por mes, febrero incluido`() {
        assertTrue(quinceYUltimo.esQuincenal)
        assertFalse(quinceYUltimo.esMesDeCalendario)
        assertEquals(PeriodoFinanciero(2026, 10, 15), periodoDeLaFecha("2026-10-20", quinceYUltimo))
        assertEquals(PeriodoFinanciero(2026, 10, 15), periodoDeLaFecha("2026-10-30", quinceYUltimo))
        assertEquals(PeriodoFinanciero(2026, 10, 31), periodoDeLaFecha("2026-10-31", quinceYUltimo))
        assertEquals(PeriodoFinanciero(2026, 10, 31), periodoDeLaFecha("2026-11-14", quinceYUltimo))
        assertEquals(PeriodoFinanciero(2026, 11, 15), periodoDeLaFecha("2026-11-15", quinceYUltimo))
        // Noviembre tiene 30: la segunda arranca el 30. Febrero de 2027, el 28.
        assertEquals(PeriodoFinanciero(2026, 11, 30), periodoDeLaFecha("2026-11-30", quinceYUltimo))
        assertEquals(PeriodoFinanciero(2027, 2, 28), periodoDeLaFecha("2027-03-01", quinceYUltimo))
        assertEquals(PeriodoFinanciero(2027, 2, 15), periodoDeLaFecha("2027-02-27", quinceYUltimo))
        assertEquals("2026-10-15", PeriodoFinanciero(2026, 10, 15).prefijo)
        assertEquals("15 – 30 oct", nombreDelPeriodo(PeriodoFinanciero(2026, 10, 15), quinceYUltimo, anioDeHoy = 2026))
        assertEquals("31 oct – 14 nov", nombreDelPeriodo(PeriodoFinanciero(2026, 10, 31), quinceYUltimo, anioDeHoy = 2026))
        assertEquals("15 – 30 oct 2026", tituloDelPeriodo(PeriodoFinanciero(2026, 10, 15), quinceYUltimo))
    }

    @Test
    fun `anterior, siguiente y homologo`() {
        val q = PeriodoFinanciero(2026, 10, 15)
        assertEquals(PeriodoFinanciero(2026, 10, 31), periodoSiguiente(q, quinceYUltimo))
        assertEquals(PeriodoFinanciero(2026, 9, 30), periodoAnterior(q, quinceYUltimo))
        assertEquals(PeriodoFinanciero(2026, 9, 15), periodoHomologo(q, quinceYUltimo))
        assertEquals(PeriodoFinanciero(2026, 9, 30), periodoHomologo(PeriodoFinanciero(2026, 10, 31), quinceYUltimo))
        assertEquals(PeriodoFinanciero(2027, 1, 15), periodoSiguiente(PeriodoFinanciero(2026, 12, 31), quinceYUltimo))
        // Un id que no es de estos ajustes (un mensual, otro día) no revienta: se lleva a su quincena.
        assertEquals(LocalDate(2026, 10, 15), inicioDelPeriodo(PeriodoFinanciero(2026, 10), quinceYUltimo))
        assertEquals(LocalDate(2026, 10, 15), inicioDelPeriodo(PeriodoFinanciero(2026, 10, 20), quinceYUltimo))
        assertTrue(esPeriodoDe(PeriodoFinanciero(2026, 10, 15), quinceYUltimo))
        assertFalse(esPeriodoDe(PeriodoFinanciero(2026, 10, 20), quinceYUltimo))
        assertFalse(esPeriodoDe(PeriodoFinanciero(2026, 10), quinceYUltimo))
        assertFalse(esPeriodoDe(PeriodoFinanciero(2026, 10, 15), PeriodSettings()))
        assertEquals(PeriodoFinanciero(2026, 10, 15), periodoDelId("2026-10-15"))
        assertEquals(PeriodoFinanciero(2026, 10), periodoDelId("2026-10"))
        assertNull(periodoDelId("2026-02-30"))
        assertNull(periodoDelPrefijo("2026-10-15"))
    }

    @Test
    fun `fin de semana y festivos corren el arranque, como en el mes`() {
        // 15-nov-2026 es domingo: con «día hábil anterior» la quincena arranca el viernes 13.
        assertEquals(LocalDate(2026, 11, 13), inicioDelPeriodo(PeriodoFinanciero(2026, 11, 15), quinceYUltimoHabil))
        assertEquals(PeriodoFinanciero(2026, 11, 15), periodoDeLaFecha("2026-11-13", quinceYUltimoHabil))
        assertEquals(PeriodoFinanciero(2026, 10, 31), periodoDeLaFecha("2026-11-12", quinceYUltimoHabil))
        // 31-oct-2026 es sábado: arranca el viernes 30.
        assertEquals(LocalDate(2026, 10, 30), inicioDelPeriodo(PeriodoFinanciero(2026, 10, 31), quinceYUltimoHabil))
        // El id no se mueve con el festivo.
        assertEquals("2026-11-15", periodoDeLaFecha("2026-11-13", quinceYUltimoHabil)!!.prefijo)
    }

    @Test
    fun `una excepcion vale a 5 dias del arranque nominal y no mas`() {
        val conExcepcion = quinceYUltimo.copy(iniciosPropios = mapOf("2026-10-15" to "2026-10-12"))
        assertEquals(LocalDate(2026, 10, 12), inicioDelPeriodo(PeriodoFinanciero(2026, 10, 15), conExcepcion))
        assertEquals(PeriodoFinanciero(2026, 10, 15), periodoDeLaFecha("2026-10-12", conExcepcion))
        val fueraDeRango = quinceYUltimo.copy(iniciosPropios = mapOf("2026-10-15" to "2026-10-09"))
        assertEquals(LocalDate(2026, 10, 15), inicioDelPeriodo(PeriodoFinanciero(2026, 10, 15), fueraDeRango))
        // «Empezar el período nuevo hoy»: el 28-oct se puede empezar la quincena del 31.
        val nuevos = empezarElSiguienteHoy(PeriodoFinanciero(2026, 10, 15), dia("2026-10-28"), quinceYUltimo)
        assertEquals(mapOf("2026-10-31" to "2026-10-28"), nuevos?.iniciosPropios)
        // El 20 no: estaría a 11 días del 31.
        assertNull(empezarElSiguienteHoy(PeriodoFinanciero(2026, 10, 15), dia("2026-10-20"), quinceYUltimo))
    }

    @Test
    fun `las dos reglas del salario se esperan el dia que arranca su quincena`() {
        // 15-nov-2026 domingo → viernes 13; 31-oct-2026 sábado → viernes 30.
        assertEquals(dia("2026-11-13"), fechaEsperadaDelIngreso(TransactionType.INCOME, 15, dia("2026-11-15"), quinceYUltimoHabil))
        assertEquals(dia("2026-10-30"), fechaEsperadaDelIngreso(TransactionType.INCOME, 31, dia("2026-10-31"), quinceYUltimoHabil))
        // Febrero: la regla del 31 vence el 28 y abre la quincena del 28 (domingo 28-feb-2027 → viernes 26).
        assertEquals(dia("2027-02-26"), fechaEsperadaDelIngreso(TransactionType.INCOME, 31, dia("2027-02-28"), quinceYUltimoHabil))
        // Un ingreso de otro día, o un gasto, no se corre.
        assertEquals(dia("2026-11-20"), fechaEsperadaDelIngreso(TransactionType.INCOME, 20, dia("2026-11-20"), quinceYUltimoHabil))
        assertEquals(dia("2026-11-15"), fechaEsperadaDelIngreso(TransactionType.EXPENSE, 15, dia("2026-11-15"), quinceYUltimoHabil))
    }

    @Test
    fun `el mes de referencia es un mes con corte en el dia A`() {
        val ref = quinceYUltimoHabil.copy(iniciosPropios = mapOf("2026-10-15" to "2026-10-12")).mesDeReferencia()
        assertEquals(PeriodSettings(cutoffDay = 15, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR), ref)
        assertEquals(ref, ref.mesDeReferencia())
        // Cada quincena cae entera dentro de un mes de referencia (sin excepciones).
        var q = periodoDeLaFecha("2024-01-01", quinceYUltimoHabil)!!
        repeat(170) {
            val v = ventanaDe(q, quinceYUltimoHabil)
            val mes = periodoDe(v.first, ref)
            assertEquals(mes, periodoDe(v.last, ref), "la quincena $q cae en un solo mes de referencia")
            q = periodoSiguiente(q, quinceYUltimoHabil)
        }
    }

    @Test
    fun `presupuesto prorrateado - las dos quincenas suman exacto el mensual`() {
        val a = PeriodoFinanciero(2026, 10, 15)
        val b = PeriodoFinanciero(2026, 10, 31)
        assertEquals(400_001L, limiteDelPeriodo(800_001L, a, quinceYUltimo))
        assertEquals(400_000L, limiteDelPeriodo(800_001L, b, quinceYUltimo))
        assertEquals(800_000L, limiteDelPeriodo(800_000L, PeriodoFinanciero(2026, 10), PeriodSettings()))
    }

    @Test
    fun `que dias arman quincenas sanas`() {
        assertTrue(esQuincenaValida(15, 31))
        assertTrue(esQuincenaValida(1, 16))
        assertTrue(esQuincenaValida(1, 15))
        assertTrue(esQuincenaValida(5, 20))
        assertTrue(esQuincenaValida(10, 25))
        assertTrue(esQuincenaValida(14, 28))
        assertFalse(esQuincenaValida(16, 31), "en febrero quedan 12 días del 16 al 28")
        assertFalse(esQuincenaValida(1, 31))
        assertFalse(esQuincenaValida(15, 15))
        assertFalse(esQuincenaValida(20, 5))
        assertFalse(PeriodSettings(cutoffDay = 16, frecuencia = FrecuenciaDelPeriodo.QUINCENAL, segundoDia = 31).esQuincenal)
    }

    /**
     * La propiedad del diseño (§4, prueba 2): para toda quincena sana, las tres reglas de fin de semana y
     * excepciones en el borde del rango, cada día de 2024 a 2030 cae en exactamente un período, los
     * períodos son contiguos y ninguno queda vacío.
     */
    @Test
    fun `cada dia cae en exactamente una quincena, contiguas y ninguna vacia`() {
        val pares = (1..31).flatMap { a -> (1..31).map { b -> a to b } }.filter { (a, b) -> esQuincenaValida(a, b) }
        assertTrue(pares.size > 20)
        val diaPorDia = setOf(15 to 31, 1 to 16, 5 to 20, 10 to 25, 14 to 28)
        val desde = LocalDate(2024, 1, 1)
        val hasta = LocalDate(2030, 12, 31)
        for ((a, b) in pares) {
            for (regla in SiElCorteCaeEnFinDeSemana.entries) {
                val s = PeriodSettings(cutoffDay = a, frecuencia = FrecuenciaDelPeriodo.QUINCENAL, segundoDia = b, siCaeEnFinDeSemana = regla)
                // Excepciones al borde: cada tercera quincena de 2026 se corre +5, la siguiente −5.
                var q = periodoDeLaFecha("2026-01-01", s)!!
                val excepciones = mutableMapOf<String, String>()
                repeat(24) { i ->
                    val nominal = LocalDate(q.year, q.month, q.dia)
                    when (i % 3) {
                        0 -> excepciones[q.prefijo] = nominal.plus(5, DateTimeUnit.DAY).toString()
                        1 -> excepciones[q.prefijo] = nominal.plus(-5, DateTimeUnit.DAY).toString()
                    }
                    q = periodoSiguiente(q, s)
                }
                for (ajustes in listOf(s, s.copy(iniciosPropios = excepciones))) {
                    var p = periodoDeLaFecha(desde.toString(), ajustes)!!
                    // El primero puede arrancar antes del 1-ene-2024 (corrido por festivo): se parte de ahí.
                    var esperado = inicioDelPeriodo(p, ajustes)
                    while (esperado <= hasta) {
                        val v = ventanaDe(p, ajustes)
                        assertEquals(millis(esperado), v.first, "contiguas: $p con $ajustes")
                        assertTrue(v.last > v.first, "ninguna vacía: $p con $ajustes")
                        val siguiente = periodoSiguiente(p, ajustes)
                        assertEquals(p, periodoAnterior(siguiente, ajustes))
                        // Cada día de la ventana dice que es de este período (día por día solo en las
                        // quincenas comunes: con todas, la prueba tarda minutos; el resto se camina).
                        var d = esperado
                        while (millis(d) <= v.last) {
                            if ((a to b) in diaPorDia) {
                                assertEquals(p, periodoDe(millis(d) + 12 * 3_600_000L, ajustes), "el $d con $ajustes")
                            }
                            d = d.plus(1, DateTimeUnit.DAY)
                        }
                        if ((a to b) !in diaPorDia) {
                            assertEquals(p, periodoDe(v.first + 12 * 3_600_000L, ajustes))
                            assertEquals(p, periodoDe(v.last - 3_600_000L, ajustes))
                        }
                        esperado = d
                        p = siguiente
                    }
                }
            }
        }
    }
}
