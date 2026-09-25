package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DueDatesTest {
    private fun rule(day: Int) = RecurringRule("r$day", "Pago", "Otros", 1000, day, TransactionType.EXPENSE)

    /**
     * Espejo deliberado del default de producción (`DEFAULT_GRACE_DAYS`), no una referencia:
     * si alguien cambia el default, estos tests deben fallar y revisarse a conciencia.
     */
    private val grace = 5

    @Test fun `due date is the day of the current month`() {
        // hoy 3 y la regla vence el 5: el día sigue adelante → mes en curso (comportamiento intacto)
        assertEquals(LocalDate.of(2026, 6, 5), dueDateFor(rule(5), LocalDate.of(2026, 6, 3)))
    }

    @Test fun `day past month length clamps to last day`() {
        assertEquals(LocalDate.of(2026, 2, 28), dueDateFor(rule(31), LocalDate.of(2026, 2, 10)))
    }

    // ── Ventana de gracia ─────────────────────────────────────────────────────

    @Test fun `day already passed but still within grace stays in the current month`() {
        // día 5, hoy 10 → 5 días de atraso = exactamente la gracia → sigue siendo junio
        val due = dueDateFor(rule(5), LocalDate.of(2026, 6, 10))
        assertEquals(LocalDate.of(2026, 6, 5), due)
        assertEquals(PaymentStatus.OVERDUE, statusFor(due, LocalDate.of(2026, 6, 10), 3))
    }

    @Test fun `day passed beyond grace rolls to next month and is not overdue`() {
        // día 5, hoy 13 → 8 días de atraso > gracia → rueda a julio, ya no se afirma vencido
        val due = dueDateFor(rule(5), LocalDate.of(2026, 6, 13))
        assertEquals(LocalDate.of(2026, 7, 5), due)
        assertEquals(PaymentStatus.UPCOMING, statusFor(due, LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `day 31 clamps to the length of the month it lands in`() {
        assertEquals(LocalDate.of(2026, 1, 31), occurrenceInMonth(YearMonth.of(2026, 1), 31))
        assertEquals(LocalDate.of(2026, 4, 30), occurrenceInMonth(YearMonth.of(2026, 4), 31))  // 31 → mes de 30
        assertEquals(LocalDate.of(2026, 2, 28), occurrenceInMonth(YearMonth.of(2026, 2), 31))  // 31 → febrero
        assertEquals(LocalDate.of(2028, 2, 29), occurrenceInMonth(YearMonth.of(2028, 2), 31))  // febrero bisiesto
    }

    @Test fun `rolling forward clamps to the length of the NEXT month, not the current one`() {
        // con gracia 0, el día 30 de enero rueda a "30 de febrero" → debe recortar a 28, no reventar
        assertEquals(LocalDate.of(2026, 2, 28), dueDateFor(rule(30), LocalDate.of(2026, 1, 31), graceDays = 0))
        // y desde un mes de 31 hacia uno de 30: día 29 de marzo con gracia 0 → 29 de abril
        assertEquals(LocalDate.of(2026, 4, 29), dueDateFor(rule(29), LocalDate.of(2026, 3, 31), graceDays = 0))
    }

    @Test fun `a rule pinned to the end of the month never rolls forward`() {
        // el día 31 (o el recorte al último día) nunca puede quedar atrasado: hoy no pasa del fin de mes
        assertEquals(LocalDate.of(2026, 1, 31), dueDateFor(rule(31), LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2026, 2, 28), dueDateFor(rule(31), LocalDate.of(2026, 2, 28)))
    }

    @Test fun `rolling forward in December increments the year`() {
        assertEquals(LocalDate.of(2027, 1, 5), dueDateFor(rule(5), LocalDate.of(2026, 12, 20)))
    }

    @Test fun `no due date is ever more than the grace window in the past`() {
        // barrido: ningún día de regla contra ningún día de ningún mes puede afirmar un atraso
        // ilimitado. Cubre los 12 meses de un año bisiesto (2028) y de uno no bisiesto (2026) —
        // el mismo loop que ya cubría por separado los casos de febrero y de año bisiesto.
        for (year in listOf(2026, 2028)) {
            for (month in 1..12) {
                for (day in 1..31) {
                    for (dom in 1..YearMonth.of(year, month).lengthOfMonth()) {
                        val today = LocalDate.of(year, month, dom)
                        val due = dueDateFor(rule(day), today)
                        val daysLate = ChronoUnit.DAYS.between(due, today)
                        assertTrue(
                            daysLate <= grace,
                            "regla día=$day, hoy=$today → $due son $daysLate días de atraso",
                        )
                    }
                }
            }
        }
    }

    @Test fun `status overdue when due before today`() {
        assertEquals(PaymentStatus.OVERDUE, statusFor(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `status due today`() {
        assertEquals(PaymentStatus.DUE_TODAY, statusFor(LocalDate.of(2026, 6, 13), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `status due soon within lead`() {
        assertEquals(PaymentStatus.DUE_SOON, statusFor(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `status upcoming beyond lead`() {
        assertEquals(PaymentStatus.UPCOMING, statusFor(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 13), 3))
    }

    @Test fun `upcomingPayments sorts by due date and computes fields`() {
        // día 10: 3 días de atraso, dentro de la gracia → sigue en junio y sigue siendo OVERDUE
        val rules = listOf(rule(25), rule(10), rule(13))
        val out = upcomingPayments(rules, LocalDate.of(2026, 6, 13), 3)
        assertEquals(listOf(10, 13, 25), out.map { LocalDate.parse(it.dueDate).dayOfMonth })
        assertEquals(PaymentStatus.OVERDUE, out[0].status)   // day 10 < 13
        assertEquals(-3, out[0].daysUntil)
        assertEquals(PaymentStatus.DUE_TODAY, out[1].status) // day 13
        assertEquals(0, out[1].daysUntil)
    }

    @Test fun `upcomingPayments shows a payment past its grace as next month's, at the end`() {
        val out = upcomingPayments(listOf(rule(5), rule(25)), LocalDate.of(2026, 6, 13), 3)
        assertEquals(listOf("2026-06-25", "2026-07-05"), out.map { it.dueDate })
        assertEquals(PaymentStatus.UPCOMING, out[1].status)
        assertEquals(22, out[1].daysUntil)
    }

    // ── El período del dueño (corte 25) ───────────────────────────────────────

    private val corte25 = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 25)

    @Test fun `con corte 25 la ocurrencia en juego es la del periodo, no la del mes`() {
        // «Octubre» va del 25-sep al 24-oct: un pago del 28 es el 28-sep, uno del 10 es el 10-oct.
        assertEquals(LocalDate.of(2026, 9, 28), ocurrenciaEnJuego(LocalDate.of(2026, 9, 26), 28, corte25))
        assertEquals(LocalDate.of(2026, 10, 10), ocurrenciaEnJuego(LocalDate.of(2026, 9, 26), 10, corte25))
        // Y el 20-sep todavía es «septiembre» (25-ago a 24-sep): el del 28 en juego es el 28-ago.
        assertEquals(LocalDate.of(2026, 8, 28), ocurrenciaEnJuego(LocalDate.of(2026, 9, 20), 28, corte25))
        assertEquals("2026-10", periodoDelDueno(LocalDate.of(2026, 9, 28), corte25))
    }

    @Test fun `con corte 25 un pago del 28 sin registrar sigue vencido dentro de la gracia al cambiar de mes`() {
        // Por calendario, el 2-sep saltaba al 28-sep y el 28-ago se daba por hecho en silencio.
        // Desde `ocurrenciaEnGracia` tampoco pasa por calendario: los dos dicen 28-ago.
        val hoy = LocalDate.of(2026, 9, 2)
        assertEquals(LocalDate.of(2026, 8, 28), dueDateFor(rule(28), hoy))
        val due = dueDateFor(rule(28), hoy, settings = corte25)
        assertEquals(LocalDate.of(2026, 8, 28), due)
        assertEquals(PaymentStatus.OVERDUE, statusFor(due, hoy, 3))
    }

    @Test fun `con corte 25 pasada la gracia rueda a la siguiente ocurrencia`() {
        assertEquals(LocalDate.of(2026, 9, 28), dueDateFor(rule(28), LocalDate.of(2026, 9, 20), settings = corte25))
        assertEquals(LocalDate.of(2026, 10, 10), dueDateFor(rule(10), LocalDate.of(2026, 9, 26), settings = corte25))
    }

    @Test fun `el sello sigue siendo el mes del vencimiento y rueda igual con corte 25`() {
        // El 28-sep ya marcado como ocurrido (clave «2026-09», su mes de calendario): sigue el 28-oct.
        val hoy = LocalDate.of(2026, 9, 29)
        assertEquals(
            LocalDate.of(2026, 10, 28),
            dueDateFor(rule(28), hoy, occurredPeriods = setOf("2026-09"), settings = corte25),
        )
        assertEquals("2026-10", reminderKeyFor(rule(28), hoy, occurredPeriods = setOf("2026-09"), settings = corte25))
    }

    @Test fun `un periodo que arranco antes mueve la ocurrencia en juego`() {
        // Octubre arrancó el 22-sep: el 23-sep ya es octubre, y el pago del 23 en juego es el 23-sep.
        val settings = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-10" to "2026-09-22"))
        assertEquals(LocalDate.of(2026, 9, 23), ocurrenciaEnJuego(LocalDate.of(2026, 9, 23), 23, settings))
    }

    // ── La ocurrencia del período anterior sigue en gracia ────────────────────

    @Test fun `con corte 25 un pago del 24 sin registrar sigue vencido del 25 al 29`() {
        // El 25-sep arranca «octubre» (25-sep a 24-oct), pero el 24-sep todavía está en gracia.
        for (dia in 25..29) {
            val hoy = LocalDate.of(2026, 9, dia)
            val due = dueDateFor(rule(24), hoy, settings = corte25)
            assertEquals(LocalDate.of(2026, 9, 24), due, "hoy $hoy")
            assertEquals(PaymentStatus.OVERDUE, statusFor(due, hoy, 3), "hoy $hoy")
            assertEquals("2026-09", reminderKeyFor(rule(24), hoy, settings = corte25), "hoy $hoy")
        }
        // El 30 ya son 6 días: rueda al 24-oct.
        val hoy = LocalDate.of(2026, 9, 30)
        val due = dueDateFor(rule(24), hoy, settings = corte25)
        assertEquals(LocalDate.of(2026, 10, 24), due)
        assertEquals(PaymentStatus.UPCOMING, statusFor(due, hoy, 3))
    }

    @Test fun `con corte 25 un pago del 24 ya sellado rueda enseguida`() {
        val hoy = LocalDate.of(2026, 9, 25)
        assertEquals(
            LocalDate.of(2026, 10, 24),
            dueDateFor(rule(24), hoy, occurredPeriods = setOf("2026-09"), settings = corte25),
        )
        assertTrue(
            selectDueForReminder(listOf(rule(24) to null), hoy, 3, mapOf(rule(24).id to setOf("2026-09")), corte25).isEmpty(),
        )
    }

    @Test fun `el vencido en gracia del periodo anterior entra al barrido de avisos`() {
        val hoy = LocalDate.of(2026, 9, 26)
        assertEquals(listOf(rule(24).id), selectDueForReminder(listOf(rule(24) to null), hoy, 3, settings = corte25).map { it.id })
        // Ya avisado por ese vencimiento (clave «2026-09»): no se repite.
        assertTrue(selectDueForReminder(listOf(rule(24) to "2026-09"), hoy, 3, settings = corte25).isEmpty())
    }

    @Test fun `por calendario un pago del 30 sigue vencido el 2 del mes siguiente`() {
        val hoy = LocalDate.of(2026, 10, 2)
        val due = dueDateFor(rule(30), hoy)
        assertEquals(LocalDate.of(2026, 9, 30), due)
        assertEquals(PaymentStatus.OVERDUE, statusFor(due, hoy, 3))
        val out = upcomingPayments(listOf(rule(30)), hoy, 3).single()
        assertEquals("2026-09-30", out.dueDate)
        assertEquals(-2, out.daysUntil)
        // Sellado, rueda al 30-oct como siempre.
        assertEquals(LocalDate.of(2026, 10, 30), dueDateFor(rule(30), hoy, occurredPeriods = setOf("2026-09")))
    }

    @Test fun `una regla que arranco despues del vencimiento en gracia no lo trae de vuelta`() {
        val desdeEl25 = rule(24).copy(activeFrom = "2026-09-25")
        assertEquals(LocalDate.of(2026, 10, 24), dueDateFor(desdeEl25, LocalDate.of(2026, 9, 26), settings = corte25))
        assertEquals(LocalDate.of(2026, 10, 24), ocurrenciaPorPreguntar(LocalDate.of(2026, 9, 26), desdeEl25, corte25))
    }

    @Test fun `el endpoint de ocurrencias sigue preguntando por el vencido en gracia`() {
        // Corte 25, día 24: del 25 al 29 se pregunta por el 24-sep (con «Ya lo pagué»).
        for (dia in 25..29) {
            assertEquals(LocalDate.of(2026, 9, 24), ocurrenciaPorPreguntar(LocalDate.of(2026, 9, dia), rule(24), corte25))
        }
        // El 30 ya es la del período en curso, que todavía no llegó (el endpoint no pregunta).
        assertEquals(LocalDate.of(2026, 10, 24), ocurrenciaPorPreguntar(LocalDate.of(2026, 9, 30), rule(24), corte25))
        // Por calendario, día 30: el 2-oct sigue siendo el 30-sep.
        val cal = com.jvillada.movi.shared.model.PeriodSettings()
        assertEquals(LocalDate.of(2026, 9, 30), ocurrenciaPorPreguntar(LocalDate.of(2026, 10, 2), rule(30), cal))
        // Y cuando no hay nada en gracia, es exactamente la ocurrencia en juego de siempre.
        assertEquals(ocurrenciaEnJuego(LocalDate.of(2026, 9, 20), 28, corte25), ocurrenciaPorPreguntar(LocalDate.of(2026, 9, 20), rule(28), corte25))
        assertEquals(ocurrenciaEnJuego(LocalDate.of(2026, 9, 2), 28, corte25), ocurrenciaPorPreguntar(LocalDate.of(2026, 9, 2), rule(28), corte25))
    }

    @Test fun `los candidatos del vencido en gracia salen de su propia ventana`() {
        // El pago del 24-sep anotado el 23-sep (antes del corte) se sigue proponiendo el 26-sep.
        val evento = com.jvillada.movi.shared.model.FinancialEvent(
            id = "ev_1", accountId = "acc", type = TransactionType.EXPENSE, amount = 1000,
            currency = "COP", category = "Otros", description = "Pago",
            timestamp = com.jvillada.movi.server.time.appDateToEpochMillis(LocalDate.of(2026, 9, 23)) + 12 * 3_600_000L,
        )
        val due = ocurrenciaPorPreguntar(LocalDate.of(2026, 9, 26), rule(24), corte25)!!
        assertEquals(listOf("ev_1"), occurrenceCandidatesFor(rule(24), due, listOf(evento), settings = corte25).map { it.id })
    }

    @Test fun `con corte 1 nada cambia`() {
        val cal = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 1)
        for (dia in listOf(1, 5, 15, 28, 31)) for (hoy in listOf(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 29), LocalDate.of(2026, 12, 31))) {
            assertEquals(dueDateFor(rule(dia), hoy), dueDateFor(rule(dia), hoy, settings = cal), "día $dia hoy $hoy")
        }
    }

    @Test fun `la ocurrencia anterior sale solo si su ventana pisa el periodo`() {
        val hoy = LocalDate.of(2026, 9, 30) // período 25-sep..24-oct
        // Día 23: su ventana llega hasta el 3-oct, así que un pago tardío puede caer en el período.
        assertEquals(LocalDate.of(2026, 9, 23), ocurrenciaAnteriorQuePisaElPeriodo(hoy, rule(23), corte25))
        // Día 15: la ventana del 15-sep termina el 25-sep, justo el arranque — todavía lo pisa.
        assertEquals(LocalDate.of(2026, 9, 15), ocurrenciaAnteriorQuePisaElPeriodo(hoy, rule(15), corte25))
        // Día 14: termina el 24-sep, un día antes. Ningún pago suyo puede estar en este período.
        assertEquals(null, ocurrenciaAnteriorQuePisaElPeriodo(hoy, rule(14), corte25))
        // Día 28: la anterior al 25-sep es la del 28-ago, no la del 28-sep (que es de este período).
        assertEquals(null, ocurrenciaAnteriorQuePisaElPeriodo(hoy, rule(28), corte25))
    }
}
