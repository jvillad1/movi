package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.PLATA_OCULTA
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El cierre semanal de la pareja** (vuelta 68), decidido sin red: el domingo, la semana de lunes a
 * domingo; el total es el gasto entero del grupo y el saldo, la frase de la tarjeta del grupo.
 *
 * Semana del lunes 19 al domingo 25 de octubre de 2026.
 */
class ResumenSemanalTest {

    private fun a(dia: Int, hora: Int = 12): Long =
        LocalDateTime(2026, 10, dia, hora, 0).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private val domingo = a(25, 19)

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private val viaje = Espacio(
        id = "esp_viaje",
        nombre = "Viaje a Cartagena",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_ana", "Ana")),
    )

    private fun gasto(espacio: Espacio, id: String, monto: Long, fecha: Long, pagadoPor: String = MIEMBRO_YO) = GastoDelEspacio(
        id = id, espacioId = espacio.id, pagadoPor = pagadoPor, monto = monto, categoria = "Mercado",
        descripcion = "Mercado", fecha = fecha, partes = partesDelGasto(monto, espacio, pagadoPor),
    )

    private fun detalle(espacio: Espacio, gastos: List<GastoDelEspacio>): DetalleDelEspacio {
        val saldo = saldoDelEspacio(espacio, gastos, emptyList())
        return DetalleDelEspacio(espacio, saldo, saldo, gastos)
    }

    /** Pagué $400.000 el martes y $80.000 el domingo; $50.000 del domingo anterior no son de esta semana. */
    private val casaConGastos = detalle(
        casa,
        listOf(
            gasto(casa, "g1", 400_000, a(20)),
            gasto(casa, "g2", 80_000, a(25, 10)),
            gasto(casa, "g0", 50_000, a(18)),
        ),
    )

    @Test
    fun `un grupo dice lo que gastaron juntos y como quedan`() {
        val r = assertNotNull(resumenSemanal(listOf(casaConGastos), domingo, yaAvisado = null, ocultos = false))
        // El total es el gasto entero (no mi mitad), y solo de lunes a domingo.
        assertEquals("Esta semana gastaron \$480.000 juntos en Casa con Caro", r.texto.titulo)
        // El saldo es el de siempre (todo el tiempo): pagué $530.000, Caro me debe la mitad.
        assertEquals("Caro te debe \$265.000", r.texto.texto)
        assertEquals("grupo:esp_casa", r.abrir)
        assertEquals(Screen.Espacio("esp_casa"), destinoDeAviso(r.abrir))
        assertEquals("semana:2026-10-19", r.huella)
    }

    @Test
    fun `si le debes, o estan a mano, lo dice`() {
        val pagoCaro = detalle(casa, listOf(gasto(casa, "g1", 100_000, a(21), pagadoPor = "dst_caro")))
        assertEquals("Le debes a Caro \$50.000", resumenSemanal(listOf(pagoCaro), domingo, null, false)?.texto?.texto)
        val parejo = detalle(
            casa,
            listOf(gasto(casa, "g1", 100_000, a(21)), gasto(casa, "g2", 100_000, a(22), pagadoPor = "dst_caro")),
        )
        assertEquals("Están a mano", resumenSemanal(listOf(parejo), domingo, null, false)?.texto?.texto)
    }

    @Test
    fun `varios grupos van juntos y tocarlo abre la lista`() {
        val conViaje = detalle(viaje, listOf(gasto(viaje, "v1", 600_000, a(23))))
        val r = assertNotNull(resumenSemanal(listOf(casaConGastos, conViaje), domingo, null, false))
        assertEquals("Esta semana gastaron \$1.080.000 juntos en 2 grupos", r.texto.titulo)
        // El que más gastó primero.
        assertEquals("Viaje a Cartagena · \$600.000 · Ana te debe \$300.000", r.texto.lineas.first())
        assertEquals(2, r.texto.lineas.size)
        assertEquals(ABRIR_GRUPOS, r.abrir)
        assertEquals(Screen.Espacios, destinoDeAviso(r.abrir))
    }

    @Test
    fun `sin grupo con gastos esa semana no sale`() {
        assertNull(resumenSemanal(emptyList(), domingo, null, false), "sin grupos")
        val soloViejos = detalle(casa, listOf(gasto(casa, "g0", 50_000, a(18))))
        assertNull(resumenSemanal(listOf(soloViejos), domingo, null, false), "un grupo sin gastos de esta semana")
    }

    @Test
    fun `solo el domingo, o el lunes de rezago`() {
        assertNull(resumenSemanal(listOf(casaConGastos), a(23, 19), null, false), "viernes")
        val lunes = assertNotNull(resumenSemanal(listOf(casaConGastos), a(26, 9), null, false))
        assertEquals("La semana pasada gastaron \$480.000 juntos en Casa con Caro", lunes.texto.titulo)
        assertEquals("semana:2026-10-19", lunes.huella)
        assertEquals(null, semanaDelResumen(LocalDate(2026, 10, 21)))
    }

    @Test
    fun `la misma semana no suena dos veces`() {
        assertNull(resumenSemanal(listOf(casaConGastos), domingo, yaAvisado = "semana:2026-10-19", ocultos = false))
        // El lunes de rezago tampoco, si el domingo ya sonó.
        assertNull(resumenSemanal(listOf(casaConGastos), a(26, 9), yaAvisado = "semana:2026-10-19", ocultos = false))
        assertEquals("semana:2026-10-19", huellaDeLaSemanaDe(domingo))
        assertNull(huellaDeLaSemanaDe(a(22)))
    }

    @Test
    fun `con los saldos ocultos ninguna cifra sale en claro`() {
        val r = assertNotNull(resumenSemanal(listOf(casaConGastos), domingo, null, ocultos = true))
        assertEquals("Esta semana gastaron $PLATA_OCULTA juntos en Casa con Caro", r.texto.titulo)
        assertEquals("Caro te debe $PLATA_OCULTA", r.texto.texto)
        assertTrue("265" !in r.texto.texto)
    }
}
