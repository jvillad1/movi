package com.jvillada.movi.server.credits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreditSummariesTest {

    @Test
    fun `paid pct is 1 minus debt over principal`() {
        assertEquals(0.6, paidPctFor(principal = 100_000_000, debt = 40_000_000)!!, 1e-9)
    }

    @Test
    fun `zero or negative principal yields null`() {
        assertNull(paidPctFor(principal = 0, debt = 10))
        assertNull(paidPctFor(principal = -5, debt = 10))
    }

    @Test
    fun `debt above principal clamps to zero pct`() {
        assertEquals(0.0, paidPctFor(principal = 100, debt = 150)!!, 1e-9)
    }

    @Test
    fun `overpaid credit (negative debt) clamps to one hundred pct`() {
        assertEquals(1.0, paidPctFor(principal = 100, debt = -20)!!, 1e-9)
    }
}
