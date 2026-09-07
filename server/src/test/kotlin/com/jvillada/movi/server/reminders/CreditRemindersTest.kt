package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreditRemindersTest {

    private val terms = CreditTerms(
        accountId = "acc-loan-1", bank = "Santander", principal = 160_000_000,
        rateEa = 21.56, termMonths = 72, installment = 4_550_030,
        dayOfMonth = 25, startDate = "2025-11-25",
    )

    @Test
    fun `virtual rule maps terms to an EXPENSE recurring rule`() {
        val rule = virtualRuleFor(terms, accountName = "Crédito Vehículo")
        assertEquals("credit_acc-loan-1", rule.id)
        assertEquals("Cuota Crédito Vehículo", rule.name)
        assertEquals("Créditos", rule.category)
        assertEquals(4_550_030, rule.amount)
        assertEquals(25, rule.dayOfMonth)
        assertEquals(TransactionType.EXPENSE, rule.type)
    }

    @Test
    fun `due virtual rule enters the reminder sweep`() {
        val rule = virtualRuleFor(terms, "Crédito Vehículo")
        val today = LocalDate.of(2026, 7, 24)  // un día antes del día 25
        val selected = selectDueForReminder(listOf(rule to null), today, leadDays = 3)
        assertEquals(listOf(rule), selected)
    }

    @Test
    fun `already-reminded virtual rule is excluded this period`() {
        val rule = virtualRuleFor(terms, "Crédito Vehículo")
        val today = LocalDate.of(2026, 7, 24)
        val selected = selectDueForReminder(listOf(rule to "2026-07"), today, leadDays = 3)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `manual rule with the same name coexists with the virtual one`() {
        val virtual = virtualRuleFor(terms, "Crédito Vehículo")
        val manual = virtual.copy(id = "rr_manual-dup")
        val today = LocalDate.of(2026, 7, 24)
        val selected = selectDueForReminder(listOf(virtual to null, manual to null), today, leadDays = 3)
        assertEquals(2, selected.size)  // conviven por diseño; la de-duplicación es manual (siembra)
    }
}

/**
 * **Un crédito a un mes no es un compromiso mensual, y la regla sintética tiene que decirlo.**
 *
 * El dueño tiene uno: el «Crédito Techo Gardenera», $10.000.000 a un plazo de 1 mes — su saldo
 * entero, que vence una sola vez el 1 de octubre. El plazo vive en `credit_terms` y **no viaja**
 * en la regla, así que sin esta marca el cliente —que es quien suma el «Flujo libre»— no tiene
 * forma de distinguirlo de la cuota del carro, y le diría al dueño que tiene $10.000.000 menos
 * todos los meses, para siempre.
 *
 * Lo que esta marca **no** hace es callar el aviso: la deuda existe y vence. Los dos últimos
 * tests fijan justamente eso.
 */
class PagoUnicoNoEsCuotaMensualTest {

    private val techoGardenera = CreditTerms(
        accountId = "acc-techo", bank = "Papá", principal = 10_000_000, rateEa = 0.0,
        termMonths = 1, installment = 10_000_000, dayOfMonth = 1, startDate = "2026-09-01",
    )

    private val vehiculo = CreditTerms(
        accountId = "acc-carro", bank = "Bancolombia", principal = 160_000_000, rateEa = 21.56,
        termMonths = 72, installment = 4_215_223, dayOfMonth = 1, startDate = "2024-01-01",
    )

    @Test
    fun `un credito a un mes viaja marcado como pago unico`() {
        assertTrue(virtualRuleFor(techoGardenera, "Crédito Techo Gardenera").esPagoUnico)
    }

    @Test
    fun `una cuota de verdad no lleva la marca`() {
        assertFalse(virtualRuleFor(vehiculo, "Vehículo 4083").esPagoUnico)
    }

    /** Un plazo en 0 tampoco describe algo que se repita: se marca igual. */
    @Test
    fun `un plazo en cero cuenta como pago unico`() {
        assertTrue(virtualRuleFor(techoGardenera.copy(termMonths = 0), "Crédito raro").esPagoUnico)
    }

    /** Y el aviso sigue saliendo: es una deuda real, con fecha, que hay que recordar. */
    @Test
    fun `el pago unico sigue entrando al barrido de avisos`() {
        assertTrue(entraAlBarridoDeAvisos(techoGardenera))

        val regla = virtualRuleFor(techoGardenera, "Crédito Techo Gardenera")
        // El 30 de septiembre, con la primera (y única) cuota el 1 de octubre.
        val seleccionadas = selectDueForReminder(
            listOf(regla to null), LocalDate.of(2026, 9, 30), leadDays = 3,
        )
        assertEquals(listOf(regla), seleccionadas)
    }
}
