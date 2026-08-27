package com.jvillada.movi.ui.fecha

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La fecha de un movimiento decide **en qué mes cuenta la plata**, así que todo lo que la
 * calcula se fija acá: las conversiones a epoch-ms, las etiquetas que el dueño lee, el aviso de
 * cambio de mes y la forma de la grilla del calendario.
 */
class FechaMovimientoTest {

    private val bogota = AppTimeZone.zone
    private val hoy = LocalDate(2026, 8, 27)

    // ── epoch ↔ fecha civil ──────────────────────────────────────────────────

    /**
     * El mediodía de Bogotá y no la medianoche. Mirado en UTC, «23 de agosto» tiene que seguir
     * siendo el 23 — con una medianoche UTC se vería como las 7 pm del 22 en Bogotá y el gasto
     * aparecería un día antes de cuando pasó.
     */
    @Test
    fun `el epoch de un dia elegido cae al mediodia de Bogota y no corre de dia en UTC`() {
        val millis = epochAlMediodia(LocalDate(2026, 8, 23), bogota)
        val enBogota = Instant.fromEpochMilliseconds(millis).toLocalDateTime(bogota)
        assertEquals(LocalDate(2026, 8, 23), enBogota.date)
        assertEquals(12, enBogota.hour)

        val enUtc = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC)
        assertEquals(LocalDate(2026, 8, 23), enUtc.date)
    }

    @Test
    fun `ida y vuelta - el epaco de una fecha vuelve a leerse como la misma fecha`() {
        listOf(
            LocalDate(2026, 1, 1), LocalDate(2026, 2, 28), LocalDate(2024, 2, 29),
            LocalDate(2026, 8, 31), LocalDate(2026, 12, 31),
        ).forEach { fecha ->
            assertEquals(fecha, fechaDeEpoch(epochAlMediodia(fecha, bogota), bogota))
        }
    }

    /**
     * Con «Hoy» —el valor por defecto— se sella la hora real, igual que antes de que esta
     * pantalla tuviera selector. Si se sellara el mediodía, los cinco gastos de una misma tarde
     * quedarían empatados y en un orden arbitrario dentro del día.
     */
    @Test
    fun `hoy conserva la hora real y cualquier otro dia va al mediodia`() {
        val ahora = Clock.System.now().toEpochMilliseconds()
        val relojFijo = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(ahora)
        }
        val hoyReal = hoyEnAppZone(relojFijo, bogota)
        assertEquals(ahora, timestampParaFecha(hoyReal, hoyReal, relojFijo, bogota))

        val otro = LocalDate(2026, 7, 4)
        assertEquals(epochAlMediodia(otro, bogota), timestampParaFecha(otro, hoyReal, relojFijo, bogota))
    }

    // ── etiquetas ────────────────────────────────────────────────────────────

    /**
     * Las mismas palabras que los encabezados de Movimientos (`formatDayHeading`): el dueño elige
     * «Ayer» y después busca su gasto bajo «AYER».
     */
    @Test
    fun `las etiquetas dicen Hoy, Ayer, el dia y el año solo cuando es otro`() {
        assertEquals("Hoy", etiquetaDeFecha(hoy, hoy))
        assertEquals("Ayer", etiquetaDeFecha(LocalDate(2026, 8, 26), hoy))
        assertEquals("23 de agosto", etiquetaDeFecha(LocalDate(2026, 8, 23), hoy))
        assertEquals("1 de enero", etiquetaDeFecha(LocalDate(2026, 1, 1), hoy))
        assertEquals("31 de diciembre de 2025", etiquetaDeFecha(LocalDate(2025, 12, 31), hoy))
    }

    /** «Ayer» cruzando el borde del mes sigue siendo «Ayer». */
    @Test
    fun `ayer cruza el fin de mes`() {
        assertEquals("Ayer", etiquetaDeFecha(LocalDate(2026, 7, 31), LocalDate(2026, 8, 1)))
    }

    @Test
    fun `el encabezado del calendario dice el mes y el año`() {
        assertEquals("agosto 2026", etiquetaDeMes(LocalDate(2026, 8, 27)))
    }

    // ── futuro ───────────────────────────────────────────────────────────────

    @Test
    fun `hoy no es futuro y mañana si`() {
        assertFalse(esFutura(hoy, hoy))
        assertFalse(esFutura(LocalDate(2026, 8, 26), hoy))
        assertTrue(esFutura(LocalDate(2026, 8, 28), hoy))
    }

    /** El mes en curso se puede mirar hasta el final; el siguiente, entero futuro, no se ofrece. */
    @Test
    fun `la flecha de avanzar mes se apaga al llegar al mes en curso`() {
        assertTrue(puedeAvanzarMes(LocalDate(2026, 7, 1), hoy))
        assertFalse(puedeAvanzarMes(LocalDate(2026, 8, 1), hoy))
        assertFalse(puedeAvanzarMes(LocalDate(2026, 9, 1), hoy))
    }

    @Test
    fun `las flechas de mes cruzan el año`() {
        assertEquals(LocalDate(2025, 12, 1), mesAnterior(LocalDate(2026, 1, 15)))
        assertEquals(LocalDate(2026, 1, 1), mesSiguiente(LocalDate(2025, 12, 15)))
    }

    // ── grilla ───────────────────────────────────────────────────────────────

    /**
     * 42 casillas SIEMPRE. La grilla es lo más alto del selector, y una que cambiara de alto al
     * pasar de mes correría todo lo de abajo bajo el dedo en una hoja anclada abajo.
     */
    @Test
    fun `la grilla siempre mide 42 casillas, con el 1 en su dia de la semana`() {
        // Agosto de 2026 arranca un sábado → cinco huecos adelante (L M M J V).
        val agosto = casillasDelMes(LocalDate(2026, 8, 1))
        assertEquals(42, agosto.size)
        assertEquals(List(5) { null }, agosto.take(5))
        assertEquals(LocalDate(2026, 8, 1), agosto[5])
        assertEquals(31, agosto.count { it != null })

        // Febrero de 2027 arranca lunes y tiene 28 días: cero huecos adelante, y aun así 42.
        val febrero = casillasDelMes(LocalDate(2027, 2, 10))
        assertEquals(42, febrero.size)
        assertEquals(LocalDate(2027, 2, 1), febrero[0])
        assertEquals(28, febrero.count { it != null })

        // Bisiesto.
        assertEquals(29, casillasDelMes(LocalDate(2024, 2, 1)).count { it != null })
    }

    @Test
    fun `la grilla no se sale del mes ni repite dias`() {
        val casillas = casillasDelMes(LocalDate(2026, 8, 1)).filterNotNull()
        assertTrue(casillas.all { it.monthNumber == 8 && it.year == 2026 })
        assertEquals(casillas.size, casillas.toSet().size)
        assertEquals(casillas.sorted(), casillas)
    }

    // ── el aviso ─────────────────────────────────────────────────────────────

    /** Dentro del mismo mes no hay nada que avisar: no se mueve plata de un mes a otro. */
    @Test
    fun `mover un dia dentro del mismo mes no avisa nada`() {
        assertNull(avisoDeCambioDeMes(LocalDate(2026, 8, 23), LocalDate(2026, 8, 27)))
        assertNull(avisoDeCambioDeMes(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31)))
    }

    /**
     * Cruzar el borde del mes SÍ avisa, y nombra los dos meses: es lo único que el dueño no puede
     * ver desde la pantalla donde lo hace — puede estar sacando plata de un mes ya cerrado.
     */
    @Test
    fun `cruzar el mes avisa y nombra los dos meses`() {
        val aviso = assertNotNull(avisoDeCambioDeMes(LocalDate(2026, 8, 2), LocalDate(2026, 7, 31)))
        assertTrue(aviso.contains("de agosto a julio"), aviso)
        assertTrue(aviso.contains("presupuesto"), aviso)
        // No se promete lo que no es: esto SÍ se puede deshacer volviendo a editar la fecha.
        assertFalse(aviso.contains("deshacer"), aviso)
    }

    /** Con años distintos el aviso dice el año, o «de diciembre a enero» no diría cuál. */
    @Test
    fun `cruzar el año pone el año en el aviso`() {
        val aviso = assertNotNull(avisoDeCambioDeMes(LocalDate(2026, 1, 3), LocalDate(2025, 12, 30)))
        assertTrue(aviso.contains("de enero de 2026 a diciembre de 2025"), aviso)
    }
}
