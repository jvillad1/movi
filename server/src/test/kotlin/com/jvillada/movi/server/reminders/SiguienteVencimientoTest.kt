package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 70 (revisión) · el vencimiento que viene después
 *
 * El día del salario, mientras no está emparejado, su vencimiento vigente se queda en hoy y el período
 * siguiente no lo tenía: la caja caía a 30 días. `/api/payments/upcoming` manda ahora
 * `siguienteVencimiento`, calculado con `dueDateFor` (como si el vigente estuviera saldado) y
 * `fechaEsperada`. Corte del dueño: 25, y si cae en fin de semana o festivo, el día hábil anterior.
 */
class SiguienteVencimientoTest {

    private val delDueno = PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
    private val salario = RecurringRule("salario", "Salario", "Salario", 9_000_000, 25, TransactionType.INCOME)
    private val carro = RecurringRule("carro", "Carro", "Créditos", 980_000, 26, TransactionType.EXPENSE)
    private val techo = RecurringRule("techo", "Techo", "Créditos", 10_000_000, 1, TransactionType.EXPENSE, esPagoUnico = true)

    /** El viernes 23-oct-2026 arranca el período (el 25 es domingo) y el salario no se ha emparejado. */
    private val diaDelSalario = LocalDate.of(2026, 10, 23)

    @Test
    fun `el dia del salario sin emparejar, el siguiente es el arranque del periodo que viene`() {
        val pagos = upcomingPayments(listOf(salario, carro), diaDelSalario, leadDays = 3, settings = delDueno).associateBy { it.rule.id }
        assertEquals("2026-10-23", pagos.getValue("salario").dueDate, "el vigente no rueda: sigue pendiente")
        // El 25-nov es miércoles: el período siguiente arranca ese día, y ahí se espera el salario.
        assertEquals("2026-11-25", pagos.getValue("salario").siguienteVencimiento)
        assertEquals("2026-10-26", pagos.getValue("carro").dueDate)
        assertEquals("2026-11-26", pagos.getValue("carro").siguienteVencimiento)
    }

    @Test
    fun `con el corte en festivo el siguiente salario se espera el dia habil anterior`() {
        // El 25-dic es viernes festivo: el período de diciembre arranca, y el salario se espera, el 24.
        val pagos = upcomingPayments(listOf(salario), LocalDate.of(2026, 11, 25), leadDays = 3, settings = delDueno)
        assertEquals("2026-11-25", pagos.single().dueDate)
        assertEquals("2026-12-24", pagos.single().siguienteVencimiento)
    }

    @Test
    fun `con el vigente ya saldado, el siguiente es el del mes despues`() {
        val pagos = upcomingPayments(
            listOf(carro), diaDelSalario, leadDays = 3, occurredBy = mapOf("carro" to setOf("2026-10")), settings = delDueno,
        )
        assertEquals("2026-11-26", pagos.single().dueDate)
        assertEquals("2026-12-26", pagos.single().siguienteVencimiento)
    }

    @Test
    fun `un pago unico no tiene siguiente`() {
        assertNull(upcomingPayments(listOf(techo), diaDelSalario, leadDays = 3, settings = delDueno).single().siguienteVencimiento)
    }

    /** Un APK viejo no conoce el campo; uno nuevo con un server viejo lo recibe en `null`. */
    @Test
    fun `el campo es opcional en el wire`() {
        val json = Json { ignoreUnknownKeys = true }
        val viejo = """{"rule":{"id":"carro","name":"Carro","category":"Créditos","amount":980000,"dayOfMonth":26,"type":"EXPENSE"},""" +
            """"dueDate":"2026-10-26","daysUntil":3,"status":"UPCOMING"}"""
        assertNull(json.decodeFromString(UpcomingPayment.serializer(), viejo).siguienteVencimiento)
    }
}
