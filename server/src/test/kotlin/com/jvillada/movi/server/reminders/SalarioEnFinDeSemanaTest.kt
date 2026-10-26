package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **El salario del 25 que cae domingo se espera el viernes 23**, igual que el período que abre.
 *
 * Sin esto «Próximos» decía «Salario · 25 de octubre» con el período ya arrancado el 23, y el
 * checklist seguía preguntando por un salario que había llegado el viernes.
 */
class SalarioEnFinDeSemanaTest {

    private val viernesAntes = PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
    private val salario = RecurringRule("salario", "Salario", "Salario", 10_000_000, 25, TransactionType.INCOME)
    private val arriendo = RecurringRule("arriendo", "Arriendo", "Vivienda", 2_000_000, 25, TransactionType.EXPENSE)

    @Test
    fun `el salario del domingo 25 se espera el viernes 23`() {
        val hoy = LocalDate.of(2026, 10, 22)
        val pagos = upcomingPayments(listOf(salario, arriendo), hoy, leadDays = 3, settings = viernesAntes)
            .associateBy { it.rule.id }

        assertEquals("2026-10-23", pagos.getValue("salario").dueDate)
        assertEquals(1, pagos.getValue("salario").daysUntil)
        // Un gasto del mismo día no se mueve: la regla es del día en que pagan, no de todo el 25.
        assertEquals("2026-10-25", pagos.getValue("arriendo").dueDate)
    }

    @Test
    fun `sin la regla el salario sigue esperandose el 25`() {
        val pagos = upcomingPayments(listOf(salario), LocalDate.of(2026, 10, 22), leadDays = 3, settings = PeriodSettings(cutoffDay = 25))
        assertEquals("2026-10-25", pagos.single().dueDate)
    }

    @Test
    fun `el sello sigue siendo el mes del 25`() {
        // Lo que se muestra se mueve; la clave del sello, no: así un sello viejo sigue valiendo.
        assertEquals("2026-10", reminderKeyFor(salario, LocalDate.of(2026, 10, 22), settings = viernesAntes))
        assertEquals(LocalDate.of(2026, 10, 23), fechaEsperada(salario, LocalDate.of(2026, 10, 25), viernesAntes))
    }
}
