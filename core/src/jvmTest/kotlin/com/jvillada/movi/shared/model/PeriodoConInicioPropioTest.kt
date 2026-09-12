package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Un período puede empezar antes o después del día de corte.**
 *
 * El dueño: *«puede indicar el comienzo de un nuevo periodo que implícitamente indica el cierre del
 * anterior en cualquier momento, esto porque no siempre los pagos suceden misma fecha y puede que un
 * mes dure más o menos el periodo»*.
 *
 * Su corte es 25 porque ahí le suele caer el salario. Pero un 25 que cae domingo se paga el 24 o el
 * 26, y con un día fijo el salario queda del lado equivocado del corte — justo el error que el corte
 * vino a evitar.
 *
 * Lo que hay que garantizar, y es lo que hace peligroso a este cambio: que mover un borde **no deje
 * días sin período ni días en dos**.
 */
class PeriodoConInicioPropioTest {

    private val corte25 = PeriodSettings(cutoffDay = 25)

    /** Septiembre arrancó el 24 porque el 25 cayó domingo y el salario entró antes. */
    private val septiembreArrancoAntes = PeriodSettings(
        cutoffDay = 25,
        iniciosPropios = mapOf("2026-09" to "2026-08-24"),
    )

    /** Y el caso simétrico: entró el 27 y el período empezó más tarde. */
    private val septiembreArrancoDespues = PeriodSettings(
        cutoffDay = 25,
        iniciosPropios = mapOf("2026-09" to "2026-08-27"),
    )

    private fun periodoDelDia(iso: String, s: PeriodSettings) = periodoDeLaFecha(iso, s)

    @Test
    fun `sin inicios propios todo sigue saliendo del corte`() {
        assertEquals(PeriodoFinanciero(2026, 9), periodoDelDia("2026-08-25", corte25))
        assertEquals(PeriodoFinanciero(2026, 8), periodoDelDia("2026-08-24", corte25))
    }

    @Test
    fun `un periodo que arranca antes se lleva los dias del anterior`() {
        // El 24 de agosto pasa de ser el último día de agosto a ser el primero de septiembre.
        assertEquals(PeriodoFinanciero(2026, 9), periodoDelDia("2026-08-24", septiembreArrancoAntes))
        // Y el 23 sigue siendo de agosto: se movió el borde, no se borró.
        assertEquals(PeriodoFinanciero(2026, 8), periodoDelDia("2026-08-23", septiembreArrancoAntes))
    }

    @Test
    fun `un periodo que arranca despues se los devuelve`() {
        assertEquals(PeriodoFinanciero(2026, 8), periodoDelDia("2026-08-25", septiembreArrancoDespues))
        assertEquals(PeriodoFinanciero(2026, 8), periodoDelDia("2026-08-26", septiembreArrancoDespues))
        assertEquals(PeriodoFinanciero(2026, 9), periodoDelDia("2026-08-27", septiembreArrancoDespues))
    }

    /**
     * **El cierre del anterior es el arranque del nuevo, y por eso no hay huecos.** Esta es la
     * garantía que hace que el resto sea seguro: se recorren dos meses de días, uno por uno, y cada
     * uno tiene que caer en exactamente una ventana.
     */
    @Test
    fun `ningun dia queda sin periodo ni en dos periodos`() {
        for (ajustes in listOf(corte25, septiembreArrancoAntes, septiembreArrancoDespues)) {
            var dia = LocalDate(2026, 8, 1)
            while (dia < LocalDate(2026, 10, 1)) {
                val millis = dia.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()
                val cuantos = listOf(
                    PeriodoFinanciero(2026, 8),
                    PeriodoFinanciero(2026, 9),
                    PeriodoFinanciero(2026, 10),
                ).count { millis in ventanaDe(it, ajustes) }
                assertEquals(1, cuantos, "el $dia cayó en $cuantos ventanas")
                dia = LocalDate.fromEpochDays(dia.toEpochDays() + 1)
            }
        }
    }

    @Test
    fun `el periodo movido dice su rango real en pantalla`() {
        // Es lo único que le explica al dueño por qué este mes duró distinto.
        assertEquals(
            "Del 24 de agosto al 24 de septiembre",
            rangoLegibleDe(PeriodoFinanciero(2026, 9), septiembreArrancoAntes),
        )
        assertEquals(
            "Del 27 de agosto al 24 de septiembre",
            rangoLegibleDe(PeriodoFinanciero(2026, 9), septiembreArrancoDespues),
        )
    }

    @Test
    fun `mover un periodo tambien mueve el final del anterior`() {
        // «El comienzo de un nuevo periodo implícitamente indica el cierre del anterior», dicho por
        // él. Agosto se acorta solo, sin que nadie lo declare.
        assertEquals(
            "Del 25 de julio al 23 de agosto",
            rangoLegibleDe(PeriodoFinanciero(2026, 8), septiembreArrancoAntes),
        )
    }

    @Test
    fun `una fecha que no se entiende se ignora y manda el corte`() {
        // Un dato roto no puede hacer desaparecer movimientos ni partir la línea de tiempo.
        val basura = PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-09" to "vaya a saber"))
        assertEquals(PeriodoFinanciero(2026, 9), periodoDelDia("2026-08-25", basura))
    }

    @Test
    fun `un inicio fuera del mes que le corresponde se ignora`() {
        // Con corte 25, «septiembre» arranca en AGOSTO. Un inicio en octubre no sería correr un
        // borde: dejaría días sin período y días en dos.
        val imposible = PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-09" to "2026-10-05"))
        assertEquals(LocalDate(2026, 8, 25), inicioDelPeriodo(PeriodoFinanciero(2026, 9), imposible))
    }

    @Test
    fun `con un inicio propio deja de comportarse como mes de calendario`() {
        // Quien lea solo `cutoffDay` para decidir «esto es el mes de siempre» se saltearía la
        // excepción, y la ventana ya no sería la del mes civil.
        val mesCivilConExcepcion = PeriodSettings(
            cutoffDay = 1,
            iniciosPropios = mapOf("2026-09" to "2026-09-03"),
        )
        assertTrue(PeriodSettings(cutoffDay = 1).esMesDeCalendario)
        assertTrue(!mesCivilConExcepcion.esMesDeCalendario)
        assertEquals(PeriodoFinanciero(2026, 8), periodoDelDia("2026-09-02", mesCivilConExcepcion))
        assertEquals(PeriodoFinanciero(2026, 9), periodoDelDia("2026-09-03", mesCivilConExcepcion))
    }
}
