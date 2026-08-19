package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardRemindersTest {

    private val terms = CardTerms(
        accountId = "acc-card-1", bank = "Bancolombia",
        creditLimit = 20_000_000, cutoffDay = 10, paymentDay = 25,
    )

    @Test
    fun `virtual rule maps card terms to an EXPENSE rule with the current debt as amount`() {
        // La cuota «esperada» de una tarjeta no es fija: el monto es la deuda actual de la
        // cuenta, que es lo que habría que pagar para quedar al día.
        val rule = virtualRuleForCard(terms, accountName = "Visa Bancolombia", currentDebt = 3_450_000)
        assertEquals("card_acc-card-1", rule.id)
        assertEquals("Pago tarjeta Visa Bancolombia", rule.name)
        assertEquals("Créditos", rule.category)
        assertEquals(3_450_000, rule.amount)
        assertEquals(25, rule.dayOfMonth)
        assertEquals(TransactionType.EXPENSE, rule.type)
    }

    @Test
    fun `due card rule enters the reminder sweep`() {
        val rule = virtualRuleForCard(terms, "Visa Bancolombia", currentDebt = 3_450_000)
        val today = LocalDate.of(2026, 7, 24)  // un día antes del día 25
        val selected = selectDueForReminder(listOf(rule to null), today, leadDays = 3)
        assertEquals(listOf(rule), selected)
    }

    @Test
    fun `already-reminded card rule is excluded this period`() {
        val rule = virtualRuleForCard(terms, "Visa Bancolombia", currentDebt = 3_450_000)
        val today = LocalDate.of(2026, 7, 24)
        val selected = selectDueForReminder(listOf(rule to "2026-07"), today, leadDays = 3)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `card and loan rule prefixes never collide`() {
        // El scheduler decide a qué tabla sellar por prefijo del id: si un prefijo fuera
        // prefijo del otro, un sello iría a la tabla equivocada.
        val cardRule = virtualRuleForCard(terms, "Visa", currentDebt = 1)
        assertTrue(cardRule.id.startsWith("card_"))
        assertTrue(!cardRule.id.startsWith("credit_"))
    }
}
