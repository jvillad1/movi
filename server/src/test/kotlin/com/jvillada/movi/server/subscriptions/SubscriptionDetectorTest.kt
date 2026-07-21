package com.jvillada.movi.server.subscriptions

import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionDetectorTest {

    private val today = LocalDate.of(2026, 7, 20)

    private fun at(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private var seq = 0
    private fun expense(desc: String, amount: Long, date: String, currency: String = "COP") = FinancialEvent(
        id = "ev-${seq++}", accountId = "acc-tc", type = TransactionType.EXPENSE,
        amount = amount, currency = currency, category = "Otros", description = desc,
        timestamp = at(date), source = EventSource.STATEMENT,
    )

    // ── normalizeMerchant ────────────────────────────────────────────────────

    @Test
    fun `known services match through gateway prefixes and suffixes`() {
        assertEquals("netflix", normalizeMerchant("PAYU*NETFLIX 110111")!!.key)
        assertEquals("youtube", normalizeMerchant("Google YOUTUBE Mmbrshp g.co")!!.key)
        assertEquals("anthropic_claude", normalizeMerchant("ANTHROPIC CLAUDE.AI SUBSCR")!!.key)
        assertEquals("directv", normalizeMerchant("DTV*DIRECTV COLOMBIA")!!.key)
        assertEquals("microsoft", normalizeMerchant("MICROSOFT*M365 FAMILIA")!!.key)
        assertTrue(normalizeMerchant("PAYU*NETFLIX 110111")!!.known)
    }

    @Test
    fun `unknown merchant falls back to a clean token`() {
        val m = normalizeMerchant("MERCPAGO*GIMNASIO BODYTECH")!!
        assertEquals(false, m.known)
        assertEquals("gimnasio_bodytech", m.key)
    }

    @Test
    fun `blank or too-short descriptions are rejected`() {
        assertNull(normalizeMerchant("  "))
        assertNull(normalizeMerchant("A1"))
    }

    // ── detectSubscriptions ──────────────────────────────────────────────────

    @Test
    fun `three stable months with regular cadence is HIGH`() {
        val events = listOf(
            expense("PAYU*NETFLIX", 44_900, "2026-04-14"),
            expense("PAYU*NETFLIX", 44_900, "2026-05-14"),
            expense("PAYU*NETFLIX", 44_900, "2026-06-14"),
        )
        val subs = detectSubscriptions(events, today)
        assertEquals(1, subs.size)
        val s = subs[0]
        assertEquals("netflix", s.merchantKey)
        assertEquals(44_900, s.amount)
        assertEquals(14, s.dayOfMonth)
        assertEquals(3, s.occurrences)
        assertEquals(SubConfidence.HIGH, s.confidence)
    }

    @Test
    fun `two months is MEDIUM (candidate)`() {
        val events = listOf(
            expense("Google YOUTUBE Mmbrshp", 26_900, "2026-05-10"),
            expense("Google YOUTUBE Mmbrshp", 26_900, "2026-06-10"),
        )
        val subs = detectSubscriptions(events, today)
        assertEquals(1, subs.size)
        assertEquals(SubConfidence.MEDIUM, subs[0].confidence)
    }

    @Test
    fun `multiple same-cycle charges aggregate into one monthly amount`() {
        // Claude ×3 cuentas: tres cargos de USD 20 el mismo día, tres meses seguidos
        val events = (4..6).flatMap { m ->
            (1..3).map { expense("ANTHROPIC CLAUDE.AI", 20, "2026-0$m-05", currency = "USD") }
        }
        val subs = detectSubscriptions(events, today)
        assertEquals(1, subs.size)
        val s = subs[0]
        assertEquals("anthropic_claude", s.merchantKey)
        assertEquals(60, s.amount)           // suma mensual, no cargo individual
        assertEquals("USD", s.currency)
        assertEquals(SubConfidence.HIGH, s.confidence)
    }

    @Test
    fun `irregular amounts are not a subscription`() {
        val events = listOf(
            expense("EXITO COUNTRY", 312_400, "2026-04-02"),
            expense("EXITO COUNTRY", 128_900, "2026-05-07"),
            expense("EXITO COUNTRY", 402_100, "2026-06-19"),
        )
        assertTrue(detectSubscriptions(events, today).isEmpty())
    }

    @Test
    fun `single month is not a subscription`() {
        val events = listOf(expense("MCDONALDS 73", 38_500, "2026-06-11"))
        assertTrue(detectSubscriptions(events, today).isEmpty())
    }

    @Test
    fun `a gap longer than 45 days breaks the cadence`() {
        val events = listOf(
            expense("UBER *TRIP", 25_000, "2026-02-01"),
            expense("UBER *TRIP", 25_000, "2026-06-01"),
        )
        assertTrue(detectSubscriptions(events, today).isEmpty())
    }

    @Test
    fun `INCOME events and future events are ignored`() {
        val income = expense("PAYU*NETFLIX", 44_900, "2026-05-14").copy(type = TransactionType.INCOME, id = "ev-i")
        val future = expense("PAYU*NETFLIX", 44_900, "2026-09-14")
        val events = listOf(income, future, expense("PAYU*NETFLIX", 44_900, "2026-06-14"))
        assertTrue(detectSubscriptions(events, today).isEmpty())
    }

    @Test
    fun `unknown merchant never reaches HIGH even with 3 stable regular months`() {
        val events = listOf(
            expense("MERCPAGO*GIMNASIO BODYTECH", 89_900, "2026-04-05"),
            expense("MERCPAGO*GIMNASIO BODYTECH", 89_900, "2026-05-05"),
            expense("MERCPAGO*GIMNASIO BODYTECH", 89_900, "2026-06-05"),
        )
        val subs = detectSubscriptions(events, today)
        assertEquals(1, subs.size)
        assertEquals("gimnasio_bodytech", subs[0].merchantKey)
        assertEquals(3, subs[0].occurrences)
        assertEquals(SubConfidence.MEDIUM, subs[0].confidence)
    }

    @Test
    fun `a long recurring description is clamped to fit merchant_key and display_name columns`() {
        // merchant_key varchar(80) / display_name varchar(100): una descripción de 200
        // caracteres no debe tronar la detección ni el insert/update posterior.
        val longDesc = "SUSCRIPCION SERVICIO RECURRENTE " + "X".repeat(170)
        val events = listOf(
            expense(longDesc, 15_000, "2026-05-08"),
            expense(longDesc, 15_000, "2026-06-08"),
        )
        val subs = detectSubscriptions(events, today)
        assertEquals(1, subs.size)
        assertTrue(subs[0].merchantKey.length <= 80)
        assertTrue(subs[0].displayName.length <= 100)
    }

    @Test
    fun `same merchant in different currencies stays separate`() {
        val events = listOf(
            expense("ANTHROPIC CLAUDE.AI", 20, "2026-05-05", currency = "USD"),
            expense("ANTHROPIC CLAUDE.AI", 20, "2026-06-05", currency = "USD"),
            expense("ANTHROPIC CLAUDE.AI", 90_000, "2026-05-06"),
            expense("ANTHROPIC CLAUDE.AI", 90_000, "2026-06-06"),
        )
        assertEquals(2, detectSubscriptions(events, today).size)
    }
}
