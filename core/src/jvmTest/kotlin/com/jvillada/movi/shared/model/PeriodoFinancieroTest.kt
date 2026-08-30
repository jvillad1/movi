package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeriodoFinancieroTest {

    private val calendario = PeriodSettings(cutoffDay = 1)
    private val delDueno = PeriodSettings(cutoffDay = 26)

    /** Un instante en la zona de la app, para no razonar en UTC. */
    private fun bogota(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime(y, m, d, h, min).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    // ── Compatibilidad: el default no cambia nada ──────────────────────────────

    /**
     * La garantía que hace seguro desplegar esto: con el corte por defecto, un período **es** el
     * mes de calendario. Nadie que no toque el ajuste ve una cifra distinta.
     */
    @Test
    fun con_el_corte_por_defecto_un_periodo_es_el_mes_de_calendario() {
        assertTrue(calendario.esMesDeCalendario)
        assertEquals(PeriodoFinanciero(2026, 8), periodoDe(bogota(2026, 8, 1), calendario))
        assertEquals(PeriodoFinanciero(2026, 8), periodoDe(bogota(2026, 8, 31, 23, 59), calendario))
        assertEquals(PeriodoFinanciero(2026, 9), periodoDe(bogota(2026, 9, 1), calendario))
        assertEquals("2026-08", periodoDe(bogota(2026, 8, 15), calendario).prefijo)
    }

    // ── El caso del dueño ──────────────────────────────────────────────────────

    /**
     * Su salario está registrado el 26 de agosto y se llama «Salario Septiembre 2026». Con corte
     * 26, ese día **es** septiembre — que es lo que él ya escribía a mano.
     */
    @Test
    fun el_26_de_agosto_ya_es_septiembre() {
        assertEquals(PeriodoFinanciero(2026, 9), periodoDe(bogota(2026, 8, 26), delDueno))
    }

    /** Y el 25 todavía es agosto: el corte incluye su propio día en el período que abre. */
    @Test
    fun el_25_todavia_es_agosto() {
        assertEquals(PeriodoFinanciero(2026, 8), periodoDe(bogota(2026, 8, 25, 23, 59), delDueno))
    }

    /** Sus gastos del 27 y 28 de agosto caen en septiembre, no en agosto. */
    @Test
    fun los_gastos_del_27_y_28_de_agosto_son_de_septiembre() {
        assertEquals(PeriodoFinanciero(2026, 9), periodoDe(bogota(2026, 8, 27, 18, 44), delDueno))
        assertEquals(PeriodoFinanciero(2026, 9), periodoDe(bogota(2026, 8, 28, 21, 21), delDueno))
    }

    // ── La ventana ────────────────────────────────────────────────────────────

    @Test
    fun la_ventana_del_dueno_va_del_26_al_25() {
        val v = ventanaDe(PeriodoFinanciero(2026, 9), delDueno)

        assertEquals(bogota(2026, 8, 26, 0, 0), v.first, "arranca el 26 de agosto a las 00:00")
        assertTrue(bogota(2026, 9, 25, 23, 59) in v, "el 25 de septiembre todavía está adentro")
        assertTrue(bogota(2026, 9, 26, 0, 0) !in v, "el 26 de septiembre ya es del siguiente")
    }

    /**
     * El fin es exclusivo: sin eso, el instante que abre el período siguiente cae en los dos y el
     * movimiento se cuenta dos veces.
     */
    @Test
    fun dos_periodos_seguidos_no_se_pisan() {
        val septiembre = ventanaDe(PeriodoFinanciero(2026, 9), delDueno)
        val octubre = ventanaDe(PeriodoFinanciero(2026, 10), delDueno)

        assertEquals(septiembre.last + 1, octubre.first, "pegados, sin hueco y sin solape")
    }

    @Test
    fun con_corte_1_la_ventana_es_el_mes_entero() {
        val v = ventanaDe(PeriodoFinanciero(2026, 2), calendario)

        assertEquals(bogota(2026, 2, 1, 0, 0), v.first)
        assertTrue(bogota(2026, 2, 28, 23, 59) in v)
        assertTrue(bogota(2026, 3, 1, 0, 0) !in v)
    }

    // ── Bordes de calendario ──────────────────────────────────────────────────

    /**
     * Un corte 31 no existe en febrero, abril, junio, septiembre ni noviembre. Sin recorte, el
     * dueño que elige 31 se queda sin período en cinco meses del año.
     */
    @Test
    fun un_corte_31_se_recorta_al_ultimo_dia_del_mes() {
        val corte31 = PeriodSettings(cutoffDay = 31)
        val v = ventanaDe(PeriodoFinanciero(2026, 3), corte31)

        assertEquals(bogota(2026, 2, 28, 0, 0), v.first, "febrero de 2026 termina el 28")
    }

    /** Y en bisiesto usa el 29, no el 28. */
    @Test
    fun en_ano_bisiesto_el_corte_31_cae_el_29_de_febrero() {
        val corte31 = PeriodSettings(cutoffDay = 31)
        val v = ventanaDe(PeriodoFinanciero(2024, 3), corte31)

        assertEquals(bogota(2024, 2, 29, 0, 0), v.first, "2024 es bisiesto")
    }

    @Test
    fun un_corte_30_cae_el_28_en_febrero_no_bisiesto() {
        val v = ventanaDe(PeriodoFinanciero(2026, 3), PeriodSettings(cutoffDay = 30))

        assertEquals(bogota(2026, 2, 28, 0, 0), v.first)
    }

    /** El cambio de año, en las dos direcciones. */
    @Test
    fun el_cambio_de_ano_no_se_rompe() {
        assertEquals(PeriodoFinanciero(2027, 1), periodoDe(bogota(2026, 12, 26), delDueno))
        assertEquals(PeriodoFinanciero(2026, 12), periodoDe(bogota(2026, 12, 25), delDueno))

        val enero = ventanaDe(PeriodoFinanciero(2027, 1), delDueno)
        assertEquals(bogota(2026, 12, 26, 0, 0), enero.first, "enero de 2027 arranca en diciembre de 2026")
    }

    // ── Zona horaria ──────────────────────────────────────────────────────────

    /**
     * El corte es a la medianoche **de Bogotá**, no de UTC. Un movimiento de las 22:00 del 25 en
     * Bogotá son las 03:00 del 26 en UTC: si el cálculo se hiciera en UTC, ese gasto saltaría al
     * período siguiente.
     */
    @Test
    fun el_corte_es_a_la_medianoche_de_bogota() {
        assertEquals(PeriodoFinanciero(2026, 8), periodoDe(bogota(2026, 8, 25, 22, 0), delDueno))
        assertEquals(PeriodoFinanciero(2026, 9), periodoDe(bogota(2026, 8, 26, 0, 1), delDueno))
    }

    // ── Nombres ───────────────────────────────────────────────────────────────

    @Test
    fun el_nombre_sale_del_mes_que_le_da_titulo() {
        assertEquals("septiembre de 2026", nombreDe(PeriodoFinanciero(2026, 9)))
    }

    /** Con un corte que no es el 1, el rango se explica; con el 1 no hay nada que aclarar. */
    @Test
    fun el_rango_legible_solo_aparece_cuando_hace_falta() {
        assertEquals(
            "Del 26 de agosto al 25 de septiembre",
            rangoLegibleDe(PeriodoFinanciero(2026, 9), delDueno),
        )
        assertNull(rangoLegibleDe(PeriodoFinanciero(2026, 9), calendario))
    }

    // ── Validación ────────────────────────────────────────────────────────────

    @Test
    fun un_corte_fuera_de_rango_se_rechaza() {
        assertFailsWith<IllegalArgumentException> { PeriodSettings(cutoffDay = 0) }
        assertFailsWith<IllegalArgumentException> { PeriodSettings(cutoffDay = 32) }
    }
}
