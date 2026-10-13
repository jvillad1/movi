package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **«vs. período pasado» compara contra los MISMOS días del anterior** (ver `anteriorAMismaAltura`).
 *
 * Es la cuenta que más se presta a errores de un día, así que se prueba en los bordes: el primer y
 * el último día del período, con el corte 25 del dueño, con un período que arrancó a mano antes del
 * corte, con un anterior más corto que los días transcurridos, y con instantes a un lado y otro de la
 * medianoche de **Bogotá** (que no es la del server, que corre en UTC).
 */
class ContraElPeriodoPasadoTest {

    private val corte25 = PeriodSettings(cutoffDay = 25)

    /** El período real del dueño: octubre arrancó el 24-sep porque el salario entró ese día. */
    private val delDueno = PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-10" to "2026-09-24"))

    private fun dia(iso: String): LocalDate = LocalDate.parse(iso)

    /** Un instante en UTC, escrito tal cual: `"2026-10-25T04:30:00Z"` son las 11:30 p. m. del 24 en Bogotá. */
    private fun utc(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

    private fun desde(r: AnteriorAMismaAltura): LocalDate = epochMillisToAppDate(r.ventana.first)

    /** El último día INCLUIDO de la franja. */
    private fun hasta(r: AnteriorAMismaAltura): LocalDate = epochMillisToAppDate(r.ventana.last)

    @Test
    fun `el primer dia del periodo se compara contra el primer dia del anterior`() {
        // 25-oct a las 9 a. m. de Bogotá: día 1 de noviembre (25-oct → 24-nov).
        val r = anteriorAMismaAltura(utc("2026-10-25T14:00:00Z"), corte25)
        assertEquals(1, r.dias)
        assertEquals(dia("2026-09-25"), desde(r))
        assertEquals(dia("2026-09-25"), hasta(r))
    }

    @Test
    fun `las 11 de la noche del 24 en Bogota todavia son el ultimo dia del periodo, aunque en UTC ya sea 25`() {
        // 04:30 UTC del 25 = 23:30 del 24 en Bogotá: sigue siendo octubre (25-sep → 24-oct), día 30.
        val r = anteriorAMismaAltura(utc("2026-10-25T04:30:00Z"), corte25)
        assertEquals(30, r.dias)
        // Septiembre (25-ago → 24-sep) tiene 31 días: se toman sus primeros 30, del 25-ago al 23-sep.
        assertEquals(dia("2026-08-25"), desde(r))
        assertEquals(dia("2026-09-23"), hasta(r))
    }

    @Test
    fun `media hora despues ya es el dia 1 del periodo siguiente`() {
        // 05:30 UTC del 25 = 00:30 del 25 en Bogotá: noviembre, día 1.
        val r = anteriorAMismaAltura(utc("2026-10-25T05:30:00Z"), corte25)
        assertEquals(1, r.dias)
        assertEquals(dia("2026-09-25"), desde(r))
    }

    @Test
    fun `el anterior se mide desde su propio arranque aunque lo hayan movido a mano`() {
        // Octubre arrancó el 24-sep. El 26-oct (día 2 de noviembre) se compara con 24 y 25 de sep.
        val r = anteriorAMismaAltura(utc("2026-10-26T17:00:00Z"), delDueno)
        assertEquals(2, r.dias)
        assertEquals(dia("2026-09-24"), desde(r))
        assertEquals(dia("2026-09-25"), hasta(r))
    }

    @Test
    fun `un periodo que arranco antes acorta al anterior, y la franja no se pasa de su fin`() {
        // El 24-oct es el día 31 de octubre (24-sep → 24-oct). Septiembre, con el 24-sep llevado a
        // octubre, va del 25-ago al 23-sep: 30 días, y se toma entero.
        val r = anteriorAMismaAltura(utc("2026-10-24T17:00:00Z"), delDueno)
        assertEquals(30, r.dias)
        assertEquals(dia("2026-08-25"), desde(r))
        assertEquals(dia("2026-09-23"), hasta(r))
        // El fin es exclusivo y es exactamente el arranque de octubre: ni un milisegundo de octubre.
        assertEquals(dia("2026-09-24").atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds(), r.ventana.last + 1)
    }

    @Test
    fun `con mes de calendario un marzo largo contra un febrero corto toma febrero entero`() {
        val r = anteriorAMismaAltura(utc("2027-03-31T17:00:00Z"), PeriodSettings())
        assertEquals(28, r.dias)
        assertEquals(dia("2027-02-01"), desde(r))
        assertEquals(dia("2027-02-28"), hasta(r))
    }

    // ── La flecha ────────────────────────────────────────────────────────────

    @Test
    fun `sin periodo pasado no hay flecha`() {
        assertEquals(null, contraElAnterior(150_000, null))
    }

    @Test
    fun `mas que el periodo pasado sube, menos baja, y la diferencia va sin signo`() {
        assertEquals(ContraElAnterior(Tendencia.SUBE, 120_000), contraElAnterior(300_000, 180_000))
        assertEquals(ContraElAnterior(Tendencia.BAJA, 45_000), contraElAnterior(135_000, 180_000))
    }

    @Test
    fun `una categoria nueva sube por todo lo gastado`() {
        assertEquals(ContraElAnterior(Tendencia.SUBE, 80_000), contraElAnterior(80_000, 0))
    }

    @Test
    fun `las diferencias chicas se dicen igual`() {
        // Menos de $1.000…
        assertEquals(Tendencia.IGUAL, contraElAnterior(10_500, 10_000)?.tendencia)
        // …o menos del 5 % de lo del período pasado.
        assertEquals(Tendencia.IGUAL, contraElAnterior(1_040_000, 1_000_000)?.tendencia)
        // El 5 % justo ya es una diferencia.
        assertEquals(Tendencia.SUBE, contraElAnterior(1_050_000, 1_000_000)?.tendencia)
    }
}
