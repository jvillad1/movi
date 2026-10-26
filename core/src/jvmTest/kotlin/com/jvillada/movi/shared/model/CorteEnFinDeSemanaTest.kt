package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * **El período arranca el día que le pagan, también cuando el corte cae en fin de semana.**
 *
 * El dueño (25-oct-2026): *«El pago siempre me lo hacen el 25 de cada mes, pero cuando el 25 cae
 * en fin de semana me lo hacen el viernes antes del 25»*. En octubre de 2026 el 25 fue domingo y el
 * arranque de noviembre se tuvo que declarar a mano el 23; con la regla, el período lo sabe solo.
 */
class CorteEnFinDeSemanaTest {

    private val viernesAntes = PeriodSettings(
        cutoffDay = 25,
        siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR,
    )

    @Test
    fun `octubre de 2026 - el 25 es domingo y el periodo arranca el viernes 23`() {
        assertEquals(LocalDate(2026, 10, 23), inicioDelPeriodo(PeriodoFinanciero(2026, 11), viernesAntes))
        assertEquals(PeriodoFinanciero(2026, 11), periodoDeLaFecha("2026-10-23", viernesAntes))
        // Y el período anterior termina el jueves 22: el final de uno es el arranque del otro.
        assertEquals(PeriodoFinanciero(2026, 10), periodoDeLaFecha("2026-10-22", viernesAntes))
        assertEquals("Del 25 de septiembre al 22 de octubre", rangoLegibleDe(PeriodoFinanciero(2026, 10), viernesAntes))
    }

    @Test
    fun `julio de 2026 - el 25 es sabado y el periodo arranca el viernes 24`() {
        assertEquals(LocalDate(2026, 7, 24), inicioDelPeriodo(PeriodoFinanciero(2026, 8), viernesAntes))
        assertEquals(PeriodoFinanciero(2026, 8), periodoDeLaFecha("2026-07-24", viernesAntes))
        assertEquals(PeriodoFinanciero(2026, 7), periodoDeLaFecha("2026-07-23", viernesAntes))
    }

    @Test
    fun `un 25 entre semana no se mueve`() {
        // 25-nov-2026 es miércoles.
        assertEquals(LocalDate(2026, 11, 25), inicioDelPeriodo(PeriodoFinanciero(2026, 12), viernesAntes))
    }

    @Test
    fun `el lunes despues corre hacia adelante`() {
        val lunesDespues = viernesAntes.copy(siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE)
        assertEquals(LocalDate(2026, 10, 26), inicioDelPeriodo(PeriodoFinanciero(2026, 11), lunesDespues))
        assertEquals(LocalDate(2026, 7, 27), inicioDelPeriodo(PeriodoFinanciero(2026, 8), lunesDespues))
        assertEquals(PeriodoFinanciero(2026, 10), periodoDeLaFecha("2026-10-25", lunesDespues))
    }

    @Test
    fun `por defecto nada cambia`() {
        val corte25 = PeriodSettings(cutoffDay = 25)
        assertEquals(LocalDate(2026, 10, 25), inicioDelPeriodo(PeriodoFinanciero(2026, 11), corte25))
        assertEquals(PeriodSettings(), PeriodSettings(siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.ESE_DIA))
    }

    @Test
    fun `la excepcion a mano gana sobre la regla`() {
        // Septiembre 2026: el 25 fue viernes, pero el salario entró el jueves 24.
        val conExcepciones = viernesAntes.copy(
            iniciosPropios = mapOf("2026-10" to "2026-09-24", "2026-11" to "2026-10-23"),
        )
        assertEquals(LocalDate(2026, 9, 24), inicioDelPeriodo(PeriodoFinanciero(2026, 10), conExcepciones))
        assertEquals(LocalDate(2026, 10, 23), inicioDelPeriodo(PeriodoFinanciero(2026, 11), conExcepciones))
        // Sin la excepción, septiembre sale del corte: el 25 era viernes y no se corre.
        assertEquals(LocalDate(2026, 9, 25), inicioDelPeriodo(PeriodoFinanciero(2026, 10), viernesAntes))
        // Una excepción que cae el lunes después también vale: la regla no cambia qué es válido.
        val despues = viernesAntes.copy(iniciosPropios = mapOf("2026-11" to "2026-10-26"))
        assertEquals(LocalDate(2026, 10, 26), inicioDelPeriodo(PeriodoFinanciero(2026, 11), despues))
    }

    @Test
    fun `con corte 1 y la regla puesta ya no es el mes de calendario`() {
        // 1-ago-2026 es sábado: agosto arranca el viernes 31 de julio.
        val corte1 = PeriodSettings(cutoffDay = 1, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
        assertFalse(corte1.esMesDeCalendario)
        assertEquals(LocalDate(2026, 7, 31), inicioDelPeriodo(PeriodoFinanciero(2026, 8), corte1))
        assertEquals(PeriodoFinanciero(2026, 8), periodoDeLaFecha("2026-07-31", corte1))
        assertEquals(PeriodoFinanciero(2026, 7), periodoDeLaFecha("2026-07-30", corte1))
    }

    @Test
    fun `ningun dia queda sin periodo ni en dos durante dos anos`() {
        for (regla in SiElCorteCaeEnFinDeSemana.entries) for (corte in listOf(1, 2, 25, 30, 31)) {
            val s = PeriodSettings(cutoffDay = corte, siCaeEnFinDeSemana = regla)
            var periodo = PeriodoFinanciero(2026, 1)
            repeat(24) {
                val siguiente = periodoSiguiente(periodo)
                val v = ventanaDe(periodo, s)
                assertEquals(ventanaDe(siguiente, s).first, v.last + 1, "$regla corte $corte en ${periodo.prefijo}")
                periodo = siguiente
            }
        }
    }

    @Test
    fun `el salario del dia de corte se espera el dia que arranca el periodo`() {
        val domingo = LocalDate(2026, 10, 25)
        assertEquals(LocalDate(2026, 10, 23), fechaEsperadaDelIngreso(TransactionType.INCOME, 25, domingo, viernesAntes))
        // Un gasto del 25, o un ingreso de otro día, no se mueven.
        assertEquals(domingo, fechaEsperadaDelIngreso(TransactionType.EXPENSE, 25, domingo, viernesAntes))
        assertEquals(LocalDate(2026, 10, 18), fechaEsperadaDelIngreso(TransactionType.INCOME, 18, LocalDate(2026, 10, 18), viernesAntes))
        // La excepción a mano también: en septiembre el salario se esperaba el 24.
        val conSeptiembre = viernesAntes.copy(iniciosPropios = mapOf("2026-10" to "2026-09-24"))
        assertEquals(LocalDate(2026, 9, 24), fechaEsperadaDelIngreso(TransactionType.INCOME, 25, LocalDate(2026, 9, 25), conSeptiembre))
        // Y sin la regla, el 25 sigue siendo el 25.
        assertEquals(domingo, fechaEsperadaDelIngreso(TransactionType.INCOME, 25, domingo, PeriodSettings(cutoffDay = 25)))
    }

    @Test
    fun `el perfil lleva la regla y un valor desconocido se lee como ese dia`() {
        val perfil = UserProfile(
            id = "u", email = "e", name = "n", avatarColor = "c",
            periodCutoffDay = 25, periodWeekendRule = "DIA_HABIL_ANTERIOR",
        )
        assertEquals(SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR, perfil.ajustesDelPeriodo().siCaeEnFinDeSemana)
        assertEquals(SiElCorteCaeEnFinDeSemana.ESE_DIA, siElCorteCaeEnFinDeSemanaDe("FERIADO"))
        assertEquals(SiElCorteCaeEnFinDeSemana.ESE_DIA, siElCorteCaeEnFinDeSemanaDe(null))
    }
}

/**
 * **Un festivo corre el pago igual que un fin de semana** (el dueño, 25-oct-2026): el 25 de
 * diciembre se paga el día hábil anterior.
 */
class FestivosDeColombiaTest {

    @Test
    fun `los festivos de 2026 son los del calendario oficial`() {
        val esperados = listOf(
            "2026-01-01", "2026-01-12", "2026-03-23", "2026-04-02", "2026-04-03", "2026-05-01",
            "2026-05-18", "2026-06-08", "2026-06-15", "2026-06-29", "2026-07-20", "2026-08-07",
            "2026-08-17", "2026-10-12", "2026-11-02", "2026-11-16", "2026-12-08", "2026-12-25",
        ).map { LocalDate.parse(it) }.toSet()
        assertEquals(esperados, festivosDe(2026))
    }

    @Test
    fun `la pascua sale bien en varios anos`() {
        assertEquals(LocalDate(2026, 4, 5), domingoDePascua(2026))
        assertEquals(LocalDate(2027, 3, 28), domingoDePascua(2027))
        assertEquals(LocalDate(2025, 4, 20), domingoDePascua(2025))
        assertEquals(LocalDate(2019, 4, 21), domingoDePascua(2019))
    }

    private val diaHabilAnterior = PeriodSettings(
        cutoffDay = 25,
        siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR,
    )

    @Test
    fun `el 25 de diciembre de 2026 es viernes festivo y el periodo arranca el jueves 24`() {
        assertEquals(LocalDate(2026, 12, 24), inicioDelPeriodo(PeriodoFinanciero(2027, 1), diaHabilAnterior))
        assertEquals(PeriodoFinanciero(2027, 1), periodoDeLaFecha("2026-12-24", diaHabilAnterior))
        assertEquals(PeriodoFinanciero(2026, 12), periodoDeLaFecha("2026-12-23", diaHabilAnterior))
    }

    @Test
    fun `un lunes festivo sigue retrocediendo hasta el viernes`() {
        // 12-oct-2026: lunes festivo (Día de la Raza) → domingo, sábado → viernes 9.
        assertEquals(LocalDate(2026, 10, 9), corteCorrido(LocalDate(2026, 10, 12), SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR))
        // Y hacia adelante, el jueves santo salta hasta el lunes de pascua.
        assertEquals(LocalDate(2026, 4, 6), corteCorrido(LocalDate(2026, 4, 2), SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE))
    }

    @Test
    fun `ningun dia queda sin periodo ni en dos con los festivos, en diez anos`() {
        for (regla in SiElCorteCaeEnFinDeSemana.entries) for (corte in 1..31) {
            val s = PeriodSettings(cutoffDay = corte, siCaeEnFinDeSemana = regla)
            var periodo = PeriodoFinanciero(2026, 1)
            repeat(120) {
                val siguiente = periodoSiguiente(periodo)
                assertEquals(ventanaDe(siguiente, s).first, ventanaDe(periodo, s).last + 1, "$regla corte $corte en ${periodo.prefijo}")
                periodo = siguiente
            }
        }
    }
}
