package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.conTuParteDeOtros
import com.jvillada.movi.shared.model.esTuParteDeOtro
import com.jvillada.movi.shared.model.movimientosDelAnalisis
import com.jvillada.movi.shared.model.tuParteComoEventos
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 66 · «Salió» de Actividad cuadra con la lista
 *
 * La revisión del 29-oct: «Salió» decía $4.978.850 y los días sumaban $4.858.850. Los $120.000 eran tu
 * parte del «Mercado del sábado» que pagó Caro: entraban al total (`/api/espacios/tu-parte`) y no
 * aparecían en ninguna fila; el lunes 26, con solo eso, no aparecía. Ahora esa parte es una fila en su
 * día ([conTuParteDeOtros]) y el resumen sale de los mismos días que la lista.
 */
class ActividadCuadraConSusFilasTest {

    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val noviembre = PeriodoFinanciero(2026, 11)
    private val hoy = LocalDate.parse("2026-10-29")
    private val tipos = mapOf("ahorros" to AccountType.SAVINGS)

    private fun alMediodia(dia: String): Long =
        LocalDate.parse(dia).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun mov(id: String, dia: String, monto: Long, categoria: String = "Comida", tipo: TransactionType = TransactionType.EXPENSE) =
        FinancialEvent(
            id = id,
            accountId = "ahorros",
            type = tipo,
            amount = monto,
            category = categoria,
            description = categoria,
            timestamp = alMediodia(dia),
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        )

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    /** El mercado que pagó Caro el lunes 26: $240.000, tu parte $120.000. */
    private val tuParte = tuParteComoEventos(
        listOf(
            GastoDelEspacio(
                id = "g_mercado",
                espacioId = "esp_casa",
                pagadoPor = "dst_caro",
                monto = 240_000L,
                categoria = "Mercado",
                descripcion = "Mercado del sábado",
                fecha = alMediodia("2026-10-26"),
                partes = mapOf(MIEMBRO_YO to 120_000L, "dst_caro" to 120_000L),
            ),
        ),
    )

    /** La historia como la manda `/by-day`, sin el 26 (ese día no tienes movimientos propios). */
    private val historia = listOf(
        mov("e_almuerzo", "2026-10-28", 30_000L),
        mov("e_salario", "2026-10-27", 850_000L, "Salario", TransactionType.INCOME),
        mov("e_cafe", "2026-10-25", 12_300L, "Restaurantes"),
    ).groupBy { epochMillisToAppDate(it.timestamp).toString() }
        .map { (dia, del) -> EventDay(dia, 0L, del) }
        .sortedByDescending { it.date }

    private fun gastadoEnLosDias(dias: List<EventDay>): Long =
        diasDelPeriodo(diasVisibles(dias, CHIP_TODO, "", tipos), noviembre, corte25)
            .sumOf { resumenDelDia(it.items, tipos).gastaste }

    @Test
    fun `la suma de los dias es Salio, con el gasto que pago Caro`() {
        val dias = conTuParteDeOtros(historia, tuParte)
        val resumen = resumenDelPanel(dias, noviembre, corte25, hoy, tipos)

        assertEquals(30_000L + 12_300L + 120_000L, resumen.salio)
        assertEquals(resumen.salio, gastadoEnLosDias(dias), "los días suman lo mismo que «Salió»")
        // Sin la fila, la lista se quedaba corta exactamente en tu parte: era el bug.
        assertEquals(resumen.salio - 120_000L, gastadoEnLosDias(historia))
    }

    @Test
    fun `el lunes 26 aparece, con la fila de tu parte, en Todo, Gastos y Compartidos`() {
        val dias = conTuParteDeOtros(historia, tuParte)
        assertEquals(listOf("2026-10-28", "2026-10-27", "2026-10-26", "2026-10-25"), dias.map { it.date })
        for (chip in listOf(CHIP_TODO, CHIP_GASTOS, CHIP_PERSONAS)) {
            val visibles = diasVisibles(dias, chip, "", tipos)
            val delLunes = visibles.single { it.date == "2026-10-26" }.items.single()
            assertTrue(esTuParteDeOtro(delLunes), "chip $chip")
            assertEquals(-120_000L, visibles.single { it.date == "2026-10-26" }.total)
        }
        assertTrue(diasVisibles(dias, CHIP_INGRESOS, "", tipos).none { it.date == "2026-10-26" })
    }

    @Test
    fun `pasarla aparte ademas de en los dias no la cuenta dos veces`() {
        val dias = conTuParteDeOtros(historia, tuParte)
        assertEquals(movimientosDelAnalisis(dias, emptyList()), movimientosDelAnalisis(dias, tuParte))
        assertEquals(resumenDelPanel(dias, noviembre, corte25, hoy, tipos), resumenDelPanel(dias, noviembre, corte25, hoy, tipos, tuParteDeOtros = tuParte))
        // Y unirla dos veces tampoco la repite.
        assertEquals(dias, conTuParteDeOtros(dias, tuParte))
    }

    @Test
    fun `la fila dice quien pago, y la cifra es tu parte`() {
        val fila = tuParte.single()
        assertEquals("dst_caro", fila.pagadoPor)
        assertEquals(TextosDeLaFila("Mercado del sábado", "Pagó Caro · Mercado"), textosDeTuParteDeOtro(fila, casa))
        assertEquals("Pagó otra persona", quienPagoTuParte(fila, null))
        assertEquals(120_000L, montoPrincipalDelMovimiento(fila))
    }
}
