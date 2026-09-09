package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        val rule = virtualRuleForCard(terms, accountName = "Visa Bancolombia", currentDebt = 3_450_000, accountCurrency = "COP", usdToCop = 0.0)
        assertEquals("card_acc-card-1", rule.id)
        assertEquals("Pago tarjeta Visa Bancolombia", rule.name)
        assertEquals("Créditos", rule.category)
        assertEquals(3_450_000, rule.amount)
        assertEquals(25, rule.dayOfMonth)
        assertEquals(TransactionType.EXPENSE, rule.type)
    }

    @Test
    fun `due card rule enters the reminder sweep`() {
        val rule = virtualRuleForCard(terms, "Visa Bancolombia", currentDebt = 3_450_000, accountCurrency = "COP", usdToCop = 0.0)
        val today = LocalDate.of(2026, 7, 24)  // un día antes del día 25
        val selected = selectDueForReminder(listOf(rule to null), today, leadDays = 3)
        assertEquals(listOf(rule), selected)
    }

    @Test
    fun `already-reminded card rule is excluded this period`() {
        val rule = virtualRuleForCard(terms, "Visa Bancolombia", currentDebt = 3_450_000, accountCurrency = "COP", usdToCop = 0.0)
        val today = LocalDate.of(2026, 7, 24)
        val selected = selectDueForReminder(listOf(rule to "2026-07"), today, leadDays = 3)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `card and loan rule prefixes never collide`() {
        // El scheduler decide a qué tabla sellar por prefijo del id: si un prefijo fuera
        // prefijo del otro, un sello iría a la tabla equivocada.
        val cardRule = virtualRuleForCard(terms, "Visa", currentDebt = 1, accountCurrency = "COP", usdToCop = 0.0)
        assertTrue(cardRule.id.startsWith("card_"))
        assertTrue(!cardRule.id.startsWith("credit_"))
    }

    // ───────────────────── lo que de la tarjeta SÍ hay que pagar este mes ─────────────────

    /**
     * **El mínimo viaja aparte del saldo, y en su propio campo.** `RecurringRule.amount` sigue
     * siendo la deuda —marcada con `montoEsSaldo`, para que nadie la pinte como un pago— y
     * `pagoMinimoCop` es lo único que el «Flujo libre» puede descontar sin mentir.
     */
    @Test
    fun `el minimo del extracto viaja en la regla, sin tocar el monto`() {
        val conMinimo = terms.copy(pagoMinimo = 1_843_014)
        val rule = virtualRuleForCard(conMinimo, "Master Black", currentDebt = 27_647_837, accountCurrency = "COP", usdToCop = 0.0)

        assertEquals(1_843_014L, rule.pagoMinimoCop)
        assertEquals(27_647_837L, rule.amount, "el monto sigue siendo el saldo")
        assertTrue(rule.montoEsSaldo)
    }

    /** Sin mínimo cargado el campo llega en `null` — no en 0, que diría «no debes nada». */
    @Test
    fun `sin minimo cargado la regla no inventa un cero`() {
        val rule = virtualRuleForCard(terms, "Master Black", currentDebt = 27_647_837, accountCurrency = "COP", usdToCop = 0.0)
        assertNull(rule.pagoMinimoCop)
    }

    /**
     * Una tarjeta en dólares: el mínimo está en dólares (misma convención que el cupo) y llega
     * convertido, porque el total al que se resta es en pesos. Sin TRM no llega nada — es
     * exactamente lo que hace `copDeSuscripcion` del lado del cliente.
     */
    @Test
    fun `el minimo de una tarjeta en dolares llega convertido, o no llega`() {
        val enDolares = terms.copy(pagoMinimo = 200)

        assertEquals(
            800_000L,
            virtualRuleForCard(enDolares, "Master USD", currentDebt = 1_721, accountCurrency = "USD", usdToCop = 4000.0).pagoMinimoCop,
        )
        assertNull(
            virtualRuleForCard(enDolares, "Master USD", currentDebt = 1_721, accountCurrency = "USD", usdToCop = 0.0).pagoMinimoCop,
            "sin tasa no se inventa una conversión",
        )
    }

    /** Un mínimo en 0 es «no lo cargué» escrito raro: no entra como cero al total. */
    @Test
    fun `un minimo en cero se trata como sin cargar`() {
        val rule = virtualRuleForCard(terms.copy(pagoMinimo = 0), "Master Black", currentDebt = 1, accountCurrency = "COP", usdToCop = 0.0)
        assertNull(rule.pagoMinimoCop)
    }
}
