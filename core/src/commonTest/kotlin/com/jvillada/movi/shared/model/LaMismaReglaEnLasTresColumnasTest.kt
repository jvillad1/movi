package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Una sola regla en las columnas de «Cuánto puedes gastar» (vuelta 60)
 *
 * «Meta de hoy $50.203» salía al lado de «Te pasaste por $549.050»: la meta del día era el ritmo fijo
 * (el disponible entre los días del período) y la semana medía lo que quedaba. Ahora las tres columnas
 * usan la regla de «Te quedan» y «$X por día» (`porDiaParaLoQueQueda`): lo que quedaba al empezar la
 * ventana, entre los días que quedaban. Con el disponible ya pasado no hay meta positiva.
 */
class LaMismaReglaEnLasTresColumnasTest {

    // 25 de septiembre al 24 de octubre; hoy martes 20 de octubre (la semana empezó el lunes 19).
    private val inicio = LocalDate(2026, 9, 25)
    private val fin = LocalDate(2026, 10, 25)
    private val hoy = LocalDate(2026, 10, 20)

    private fun disponible(gasto: Map<String, Long>) = assertNotNull(
        disponibleDelPeriodo(
            ingresosRecibidos = 3_000_000,
            checklist = emptyList(),
            gastoVariablePorDia = gasto,
            inicio = inicio,
            finExclusivo = fin,
            hoy = hoy,
        ),
    )

    @Test
    fun `con el disponible pasado, la semana y hoy no ofrecen meta`() {
        // $3M de disponible y $3.549.050 gastados: «Te pasaste por $549.050».
        val d = disponible(mapOf("2026-10-01" to 3_400_000L, "2026-10-19" to 100_000L, "2026-10-20" to 49_050L))
        assertEquals(-549_050L, d.loQueQueda)
        assertTrue(d.yaTePasaste)
        assertEquals(0L, d.hoy.meta, "hoy dice «$0 · ya te pasaste», no la meta fija de $100.000")
        assertEquals(0L, d.semana.meta)
        assertEquals(3_000_000L / 30, d.metaPorDia, "el ritmo fijo sigue existiendo, pero ninguna columna lo pinta")
    }

    @Test
    fun `con margen, la meta de hoy es lo que quedaba al empezar el dia entre los dias que quedan`() {
        val temprano = disponible(mapOf("2026-10-01" to 600_000L))
        assertFalse(temprano.yaTePasaste)
        assertEquals(5, temprano.diasQueQuedan)
        // A primera hora, la meta de hoy ES el «$X por día» del héroe.
        assertEquals(temprano.porDiaParaLoQueQueda, temprano.hoy.meta)
        assertEquals(480_000L, temprano.hoy.meta)

        // Gastar hoy no achica la meta de hoy (sí lo que le queda).
        val despues = disponible(mapOf("2026-10-01" to 600_000L, "2026-10-20" to 50_000L))
        assertEquals(480_000L, despues.hoy.meta)
        assertEquals(430_000L, despues.hoy.teQuedan)
        assertEquals(470_000L, despues.porDiaParaLoQueQueda, "el héroe reparte lo que queda ahora")

        // La semana (lunes 19 al sábado 24, el último del período): lo que quedaba el lunes, entre los
        // seis días que quedaban, por sus seis días.
        assertEquals(6, despues.diasDeLaSemana)
        assertEquals(2_400_000L, despues.semana.meta)
        assertEquals(2_350_000L, despues.semana.teQuedan)
    }

    @Test
    fun `lo gastado antes de la semana achica la semana, como achica lo que queda`() {
        val d = disponible(mapOf("2026-10-01" to 600_000L, "2026-10-18" to 1_200_000L))
        // El lunes quedaban $1.200.000 para seis días: $200.000 por día.
        assertEquals(1_200_000L, d.semana.meta)
        assertEquals(240_000L, d.hoy.meta, "el martes quedaban los mismos $1.200.000 para cinco días")
    }
}
