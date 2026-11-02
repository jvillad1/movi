package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.ROTULO_TE_FALTAN
import com.jvillada.movi.shared.model.pesosCortos
import com.jvillada.movi.ui.components.formatMoneyCompactSinOcultar
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 108 · sin margen, «Cuánto puedes gastar» dice lo que sirve para decidir
 *
 * Y la cascada corta de «¿De dónde sale?», que suma la cifra del héroe al peso; y que las frases que arma
 * `:core` escriben las cifras como la app.
 */
class SinMargenParaElDiaADiaTest {

    private val hoy = LocalDate(2026, 11, 2)

    private fun pago(nombre: String, monto: Long, vence: String, pagado: Boolean = false) = PagoDelPeriodo(
        ruleId = nombre, nombre = nombre, monto = monto, pagado = pagado, diasParaVencer = 0, vence = vence,
    )

    private val checklist = listOf(
        pago("Colegio", 4_000_000L, "2026-10-25", pagado = true),
        pago("Cuota Hipotecario 2334", 2_613_714L, "2026-11-07"),
        pago("Gimnasio", 180_000L, "2026-11-04"),
        pago("Cuota Vehículo 8761", 4_101_123L, "2026-11-17"),
        pago("Cuota Cotrafa 5413", 1_931_488L, "2026-11-22"),
    )

    /** Período 23-oct → 24-nov, lo libre $1.362.010, gastado en el día a día $1.950.929 (nada esta semana). */
    private fun disponible() = assertNotNull(
        disponibleDelPeriodo(
            ingresosRecibidos = 20_000_000L,
            checklist = checklist,
            gastoVariablePorDia = mapOf("2026-10-28" to 1_950_929L),
            inicio = LocalDate(2026, 10, 23),
            finExclusivo = LocalDate(2026, 11, 25),
            hoy = hoy,
            plata = PlataDelDisponible(saldoAlInicio = 0L, entradas = 14_188_335L, guardado = 0L),
        ),
    )

    @Test
    fun `sin margen no hay metas en cero, sino lo gastado y los pagos que mas pesan`() {
        val d = disponible()
        assertEquals(1_362_010L, d.disponible)
        assertTrue(d.yaTePasaste)
        val s = sinMargenParaElDiaADia(d, checklist, hoy, conElPeriodoElegido = true) { "$" + it }
        assertEquals("No hay margen para el día a día hasta el 25 nov", s.titulo)
        assertEquals("$1950929 · lo libre era $1362010", s.diaADia)
        assertEquals("llevas $0", s.semana, "el lunes 2-nov no hay gasto en la semana: «llevas», nunca una meta")
        assertEquals("llevas $0", s.hoy)
        assertEquals(
            listOf("Cuota Vehículo 8761 · 17 nov" to 4_101_123L, "Cuota Hipotecario 2334 · 7 nov" to 2_613_714L, "Cuota Cotrafa 5413 · 22 nov" to 1_931_488L),
            s.pesan,
        )
        assertEquals("Ver los 4 pagos que faltan", s.verTodos)
        // Con el mes de calendario, el último día del período.
        assertEquals("No hay margen para el día a día hasta el 24 nov", sinMargenParaElDiaADia(d, checklist, hoy, false).titulo)
    }

    @Test
    fun `la cascada corta suma la cifra del heroe al peso`() {
        val d = disponible()
        val t = teQuedanDeHoy(d, checklist, gasto = null, hoy = hoy, conElPeriodoElegido = true)
        val c = cascadaHastaQueTePaguen(t)
        assertEquals(ROTULO_TE_FALTAN, c.rotuloDelTotal)
        assertEquals(t.teQuedan, c.total)
        assertEquals(c.total, c.renglones.sumOf { it.monto }, "lo libre + lo que entra − los pagos = la cifra")
        assertEquals(listOf(ROTULO_TIENES_LIBRE_HOY, ROTULO_FALTAN_EN_PAGOS), c.renglones.map { it.rotulo })
        assertEquals("4 pagos", c.renglones.last().detalle)
    }

    @Test
    fun `las frases de core escriben las cifras como la app`() {
        listOf(0L, 9_999L, 588_919L, 999_999L, 1_000_000L, 1_049_999L, 2_584_055L, 12_000_000L, -2_584_055L, 1_500_000_000L).forEach {
            assertEquals(formatMoneyCompactSinOcultar(it), pesosCortos(it), "$it")
        }
    }
}
