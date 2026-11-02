package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 105 · la prueba dorada: **con período mensual nada cambia**
 *
 * La quincena entró a las primitivas de `PeriodoFinanciero.kt`. La garantía del diseño
 * (`docs/superpowers/specs/2026-11-02-periodo-quincenal-semanal-design.md`, §2.5) es que con
 * `frecuencia = MENSUAL` —el default y el del dueño— **todas** devuelvan exactamente lo mismo que antes.
 *
 * Abajo está la implementación de ANTES, copiada tal cual (con el sufijo `Viejo`), y se compara contra
 * la nueva para cada día de 2024 a 2030 con varias configuraciones mensuales, incluida la del dueño
 * (corte 25, día hábil anterior, excepciones a mano, una inválida y una ilegible).
 */
class ElMensualNoCambiaTest {

    private val configuraciones = listOf(
        PeriodSettings(),
        PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR),
        PeriodSettings(cutoffDay = 31),
        PeriodSettings(cutoffDay = 31, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE),
        PeriodSettings(cutoffDay = 15, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE),
        PeriodSettings(cutoffDay = 1, iniciosPropios = mapOf("2026-03" to "2026-03-02")),
        // La del dueño: corte 25, viernes antes, y sus excepciones (más una fuera de rango y una basura).
        PeriodSettings(
            cutoffDay = 25,
            siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR,
            iniciosPropios = mapOf(
                "2026-10" to "2026-09-24",
                "2026-11" to "2026-10-23",
                "2026-12" to "2026-11-27",
                "2027-02" to "2027-01-22",
                "2026-05" to "2026-07-01",
                "2025-02" to "basura",
            ),
        ),
        // Una frecuencia quincenal incompleta se lee como mensual: también tiene que dar lo de antes.
        PeriodSettings(cutoffDay = 25, frecuencia = FrecuenciaDelPeriodo.QUINCENAL, segundoDia = null),
        PeriodSettings(cutoffDay = 16, frecuencia = FrecuenciaDelPeriodo.QUINCENAL, segundoDia = 31),
    )

    private val dias: List<LocalDate> = generateSequence(LocalDate(2024, 1, 1)) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it <= LocalDate(2030, 12, 31) }
        .toList()

    private fun millis(d: LocalDate): Long = d.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()

    @Test
    fun `cada dia de 2024 a 2030 cae en el mismo periodo, con la misma ventana y el mismo id`() {
        for (s in configuraciones) {
            for (d in dias) {
                val mediodia = millis(d) + 12 * 3_600_000L
                val nuevo = periodoDe(mediodia, s)
                val viejo = periodoDeViejo(mediodia, s)
                assertEquals(viejo, nuevo, "periodoDe($d) con $s")
                assertEquals(viejo.prefijo, nuevo.prefijo)
                assertEquals(0, nuevo.dia)
                assertEquals(ventanaDeVieja(viejo, s), ventanaDe(nuevo, s), "ventanaDe($nuevo) con $s")
                assertEquals(inicioDelPeriodoViejo(viejo, s), inicioDelPeriodo(nuevo, s))
                assertEquals(periodoAnteriorViejo(viejo), periodoAnterior(nuevo, s))
                assertEquals(periodoSiguienteViejo(viejo), periodoSiguiente(nuevo, s))
                assertEquals(periodoAnteriorViejo(viejo), periodoHomologo(nuevo, s), "el homologo del mensual es el anterior")
                assertEquals(desdeDondeRecuperarSmsViejo(mediodia, s), desdeDondeRecuperarSms(mediodia, s))
                assertEquals(viejo, periodoDeLaFecha(d.toString(), s))
                assertEquals(s, s.mesDeReferencia(), "el mes de referencia del mensual es el mismo")
                assertEquals(1_234_567L, limiteDelPeriodo(1_234_567L, nuevo, s))
            }
        }
    }

    @Test
    fun `el salario se espera el mismo dia que antes`() {
        for (s in configuraciones) {
            for (d in dias) {
                for (dia in listOf(s.cutoffDay, 1, 15, 28, 31)) {
                    assertEquals(
                        fechaEsperadaDelIngresoViejo(TransactionType.INCOME, dia, d, s),
                        fechaEsperadaDelIngreso(TransactionType.INCOME, dia, d, s),
                        "fechaEsperadaDelIngreso($dia, $d) con $s",
                    )
                }
            }
        }
    }

    @Test
    fun `empezar el siguiente hoy se ofrece los mismos dias que antes`() {
        for (s in configuraciones) {
            for (d in dias) {
                val enCurso = periodoDeViejo(millis(d) + 12 * 3_600_000L, s)
                assertEquals(
                    empezarElSiguienteHoyViejo(enCurso, d, s),
                    empezarElSiguienteHoy(enCurso, d, s),
                    "empezarElSiguienteHoy($enCurso, $d) con $s",
                )
            }
        }
    }

    @Test
    fun `los nombres y los ids mensuales no cambian`() {
        val octubre = PeriodoFinanciero(2026, 10)
        assertEquals("2026-10", octubre.prefijo)
        assertEquals(octubre, periodoDelPrefijo("2026-10"))
        assertEquals("octubre", nombreDelMesDe(octubre))
        assertEquals("octubre de 2026", nombreDe(octubre))
        assertEquals("Octubre 2026", tituloDelPeriodo(octubre))
        assertEquals("Octubre 2026", tituloDelPeriodo(octubre, PeriodSettings()))
        assertEquals(PeriodoFinanciero(2026, 9), mesAnterior(octubre))
        assertEquals(PeriodoFinanciero(2026, 11), mesSiguiente(octubre))
        // Un mes de calendario sigue siendo el mes de calendario; una quincena incompleta, también.
        assertEquals(true, PeriodSettings().esMesDeCalendario)
        assertEquals(true, PeriodSettings(frecuencia = FrecuenciaDelPeriodo.QUINCENAL).esMesDeCalendario)
        assertEquals(FrecuenciaDelPeriodo.MENSUAL, frecuenciaDelPeriodoDe(null))
        assertEquals(FrecuenciaDelPeriodo.MENSUAL, frecuenciaDelPeriodoDe("SEMANAL"))
        // El perfil por defecto arma los ajustes de siempre.
        val perfil = UserProfile(id = "u", email = "a@b.c", name = "A", avatarColor = "#000000")
        assertEquals(PeriodSettings(), perfil.ajustesDelPeriodo())
    }

    // ── La implementación de ANTES de la vuelta 105, copiada tal cual ─────────────────────────────

    private fun diasDelMesViejo(year: Int, month: Int): Int {
        val siguiente = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        return siguiente.toEpochDays() - LocalDate(year, month, 1).toEpochDays()
    }

    private fun inicioDeViejo(year: Int, month: Int, cutoffDay: Int): LocalDate =
        LocalDate(year, month, minOf(cutoffDay, diasDelMesViejo(year, month)))

    private fun esMesDeCalendarioViejo(s: PeriodSettings): Boolean =
        s.cutoffDay == 1 && s.iniciosPropios.isEmpty() && s.siCaeEnFinDeSemana == SiElCorteCaeEnFinDeSemana.ESE_DIA

    private fun inicioDelPeriodoViejo(periodo: PeriodoFinanciero, settings: PeriodSettings): LocalDate {
        val porCorte = inicioPorCorteViejo(periodo, settings.cutoffDay)
        val porLaRegla = corteCorrido(porCorte, settings.siCaeEnFinDeSemana)
        val declarado = settings.iniciosPropios[periodo.prefijo]
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return porLaRegla
        return if (declarado.year == porCorte.year && declarado.month == porCorte.month) declarado
        else porLaRegla
    }

    private fun inicioPorCorteViejo(periodo: PeriodoFinanciero, cutoffDay: Int): LocalDate {
        val (y, m) = if (cutoffDay == 1) {
            periodo.year to periodo.month
        } else {
            if (periodo.month == 1) (periodo.year - 1) to 12 else periodo.year to (periodo.month - 1)
        }
        return inicioDeViejo(y, m, cutoffDay)
    }

    private fun ventanaDeVieja(periodo: PeriodoFinanciero, settings: PeriodSettings): LongRange {
        val zona = AppTimeZone.zone
        val inicio = inicioDelPeriodoViejo(periodo, settings).atStartOfDayIn(zona)
        val fin = inicioDelPeriodoViejo(periodoSiguienteViejo(periodo), settings).atStartOfDayIn(zona)
        return inicio.toEpochMilliseconds() until fin.toEpochMilliseconds()
    }

    private fun periodoDeViejo(epochMillis: Long, settings: PeriodSettings): PeriodoFinanciero {
        val fecha = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(AppTimeZone.zone).date
        if (esMesDeCalendarioViejo(settings)) return PeriodoFinanciero(fecha.year, fecha.month.number)
        val porElCorte = periodoPorElCorteViejo(fecha, settings.cutoffDay)
        if (settings.iniciosPropios.isEmpty() && settings.siCaeEnFinDeSemana == SiElCorteCaeEnFinDeSemana.ESE_DIA) {
            return porElCorte
        }
        val millis = fecha.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()
        val candidatos = listOf(periodoAnteriorViejo(porElCorte), porElCorte, periodoSiguienteViejo(porElCorte))
        return candidatos.firstOrNull { millis in ventanaDeVieja(it, settings) } ?: porElCorte
    }

    private fun periodoPorElCorteViejo(fecha: LocalDate, cutoffDay: Int): PeriodoFinanciero {
        if (cutoffDay == 1) return PeriodoFinanciero(fecha.year, fecha.month.number)
        val corteDeEsteMes = inicioDeViejo(fecha.year, fecha.month.number, cutoffDay)
        return if (fecha < corteDeEsteMes) {
            PeriodoFinanciero(fecha.year, fecha.month.number)
        } else {
            if (fecha.month.number == 12) PeriodoFinanciero(fecha.year + 1, 1)
            else PeriodoFinanciero(fecha.year, fecha.month.number + 1)
        }
    }

    private fun periodoAnteriorViejo(periodo: PeriodoFinanciero): PeriodoFinanciero =
        if (periodo.month == 1) PeriodoFinanciero(periodo.year - 1, 12)
        else PeriodoFinanciero(periodo.year, periodo.month - 1)

    private fun periodoSiguienteViejo(periodo: PeriodoFinanciero): PeriodoFinanciero =
        if (periodo.month == 12) PeriodoFinanciero(periodo.year + 1, 1)
        else PeriodoFinanciero(periodo.year, periodo.month + 1)

    private fun fechaEsperadaDelIngresoViejo(
        tipo: TransactionType,
        diaDeLaRegla: Int,
        vencimiento: LocalDate,
        settings: PeriodSettings,
    ): LocalDate {
        if (tipo != TransactionType.INCOME || diaDeLaRegla != settings.cutoffDay) return vencimiento
        val periodo = periodoPorElCorteViejo(vencimiento, settings.cutoffDay)
        if (inicioPorCorteViejo(periodo, settings.cutoffDay) != vencimiento) return vencimiento
        return inicioDelPeriodoViejo(periodo, settings)
    }

    private fun desdeDondeRecuperarSmsViejo(ahoraMillis: Long, settings: PeriodSettings): Long =
        ventanaDeVieja(periodoAnteriorViejo(periodoDeViejo(ahoraMillis, settings)), settings).first

    private fun empezarElSiguienteHoyViejo(enCurso: PeriodoFinanciero, hoy: LocalDate, settings: PeriodSettings): PeriodSettings? {
        val millis = hoy.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()
        if (periodoDeViejo(millis, settings) != enCurso) return null
        val siguiente = periodoSiguienteViejo(enCurso)
        if (inicioDelPeriodoViejo(siguiente, settings) == hoy) return null
        val nuevos = settings.copy(iniciosPropios = settings.iniciosPropios + (siguiente.prefijo to hoy.toString()))
        if (inicioDelPeriodoViejo(siguiente, nuevos) != hoy) return null
        if (nuevos.iniciosPropios.size > MAX_INICIOS_PROPIOS) return null
        return nuevos
    }
}
