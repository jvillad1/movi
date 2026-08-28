package com.jvillada.movi.ui.fecha

import com.jvillada.movi.shared.model.EventOccurrenceMark
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

    /** Sin piso, la flecha de mes anterior navegaba hasta años que el server rechaza. */
    @Test
    fun `el calendario no deja retroceder mas alla del ano 2000`() {
        assertTrue(puedeRetrocederMes(LocalDate(2000, 2, 1)))
        assertFalse(puedeRetrocederMes(LocalDate(2000, 1, 1)))
        assertFalse(puedeRetrocederMes(LocalDate(1999, 12, 1)))
    }

    // ── el aviso del cambio de mes ───────────────────────────────────────────

    /** Dentro del mismo mes no hay nada que avisar: no se mueve plata de un mes a otro. */
    @Test
    fun `mover un dia dentro del mismo mes no avisa nada`() {
        assertNull(avisoDeCambioDeMes(LocalDate(2026, 8, 23), LocalDate(2026, 8, 27), hoy))
        assertNull(avisoDeCambioDeMes(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31), hoy))
    }

    /**
     * Salir del mes en curso: la plata desaparece de todo lo que el dueño puede mirar. El aviso
     * **no** promete que vaya a poder verla sumada en julio — Movi no tiene pantalla de un mes
     * pasado, así que esa promesa sería cierta en la base e invisible en el producto.
     */
    @Test
    fun `salir del mes en curso avisa que deja de contar en lo que se ve`() {
        val aviso = assertNotNull(avisoDeCambioDeMes(LocalDate(2026, 8, 2), LocalDate(2026, 7, 31), hoy))
        assertTrue(aviso.contains("de agosto a julio"), aviso)
        assertTrue(aviso.contains("presupuesto"), aviso)
        assertTrue(aviso.contains("el mes que ves en la app"), aviso)
        // Lo que NO puede decir: que empiece a contar en algún lado observable.
        assertFalse(aviso.contains("empieza a contar"), aviso)
        // Y tampoco puede prometer irreversibilidad: esto se deshace volviendo a editar la fecha.
        assertFalse(aviso.contains("deshacer"), aviso)
    }

    /** Traer algo hacia el mes en curso SÍ es observable, y ahí el aviso lo dice literalmente. */
    @Test
    fun `entrar al mes en curso avisa que empieza a contar`() {
        val aviso = assertNotNull(avisoDeCambioDeMes(LocalDate(2026, 7, 20), LocalDate(2026, 8, 3), hoy))
        assertTrue(aviso.contains("de julio a agosto"), aviso)
        assertTrue(aviso.contains("empieza a contar"), aviso)
    }

    /** Entre dos meses pasados no cambia nada de lo que el dueño ve, y el aviso lo dice. */
    @Test
    fun `entre dos meses pasados avisa que las cifras de la pantalla no cambian`() {
        val aviso = assertNotNull(avisoDeCambioDeMes(LocalDate(2026, 6, 10), LocalDate(2026, 5, 3), hoy))
        assertTrue(aviso.contains("de junio a mayo"), aviso)
        assertTrue(aviso.contains("no cambian"), aviso)
    }

    /** Con años distintos el aviso dice el año, o «de diciembre a enero» no diría cuál. */
    @Test
    fun `cruzar el ano pone el ano en el aviso`() {
        val aviso = assertNotNull(
            avisoDeCambioDeMes(LocalDate(2026, 1, 3), LocalDate(2025, 12, 30), LocalDate(2026, 1, 15)),
        )
        assertTrue(aviso.contains("de enero de 2026 a diciembre de 2025"), aviso)
    }

    // ── el aviso del sello de recurrente ─────────────────────────────────────

    private fun marca(desde: String, hasta: String) = EventOccurrenceMark(
        ruleId = "rule-arriendo",
        ruleName = "Arriendo",
        period = "2026-08",
        validFrom = desde,
        validTo = hasta,
    )

    @Test
    fun `sin sello no hay aviso`() {
        assertNull(avisoDeSelloSuelto(null, LocalDate(2026, 3, 1)))
    }

    /**
     * Adentro de la ventana no hay nada que avisar, **bordes incluidos**. La ventana la calcula el
     * server (vencimiento el 5 de agosto → `[1 de agosto .. 15 de agosto]`, con el piso en el
     * primer día del mes); acá solo se verifica que la comparación sea inclusiva en los dos
     * extremos, que es donde un `<` en vez de un `<=` avisaría de más.
     */
    @Test
    fun `una fecha adentro de la ventana no avisa nada, bordes incluidos`() {
        val m = marca("2026-08-01", "2026-08-15")
        assertNull(avisoDeSelloSuelto(m, LocalDate(2026, 8, 5)))
        assertNull(avisoDeSelloSuelto(m, LocalDate(2026, 8, 1)))
        assertNull(avisoDeSelloSuelto(m, LocalDate(2026, 8, 15)))
    }

    /**
     * Y una ventana que cruza al mes siguiente —el arriendo del 31, que se puede pagar tarde—
     * tampoco avisa por el solo hecho de cambiar de mes: **la regla es la ventana, no el mes**.
     */
    @Test
    fun `una ventana que cruza de mes no avisa por cambiar de mes`() {
        val m = marca("2026-08-01", "2026-09-10")
        assertNull(avisoDeSelloSuelto(m, LocalDate(2026, 9, 3)))
    }

    /**
     * El caso que cuesta plata: el movimiento era de julio 15, se corrige la fecha, y agosto
     * quedaría sellado con una evidencia que el emparejador nunca habría propuesto. El aviso lo
     * dice antes, con el nombre del recurrente y el mes.
     */
    @Test
    fun `una fecha fuera de la ventana avisa que la marca se suelta`() {
        val aviso = assertNotNull(
            avisoDeSelloSuelto(marca("2026-08-01", "2026-08-15"), LocalDate(2026, 7, 15)),
        )
        assertTrue(aviso.contains("Arriendo"), aviso)
        assertTrue(aviso.contains("agosto de 2026"), aviso)
        assertTrue(aviso.contains("vuelve a recordar"), aviso)
    }

    @Test
    fun `un periodo ilegible no inventa un aviso`() {
        val m = EventOccurrenceMark("r", "Arriendo", "no-es-un-mes", "2026-08-01", "2026-08-15")
        assertNull(avisoDeSelloSuelto(m, LocalDate(2026, 7, 15)))
        val fechas = EventOccurrenceMark("r", "Arriendo", "2026-08", "ayer", "2026-08-15")
        assertNull(avisoDeSelloSuelto(fechas, LocalDate(2026, 7, 15)))
    }

    @Test
    fun `el periodo se lee como mes y ano`() {
        assertEquals("agosto de 2026", etiquetaDePeriodo("2026-08"))
        assertEquals("enero de 2025", etiquetaDePeriodo("2025-01"))
        assertNull(etiquetaDePeriodo("2026-13"))
        assertNull(etiquetaDePeriodo("2026"))
    }
}
