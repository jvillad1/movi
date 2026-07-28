package com.jvillada.movi.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SmsFilterConfigStoreTest {
    @Test
    fun `valid json parses`() {
        val c = SmsFilterConfigStore.parseConfigJson("""{"senderCodes":["85540","123"],"bodyKeywords":["bancolombia","nequi"]}""")!!
        assertEquals(listOf("85540", "123"), c.senderCodes)
        assertEquals(listOf("bancolombia", "nequi"), c.bodyKeywords)
    }

    @Test
    fun `corrupt or empty json yields null (caller falls back to defaults)`() {
        assertNull(SmsFilterConfigStore.parseConfigJson("not json"))
        assertNull(SmsFilterConfigStore.parseConfigJson("""{"senderCodes":[]}"""))   // sin keywords
        assertNull(SmsFilterConfigStore.parseConfigJson("""{"senderCodes":[],"bodyKeywords":[]}"""))  // vacía = inválida (fail-open)
    }

    @Test
    fun `staleness honors the 24h ttl`() {
        val now = 1_700_000_000_000
        assertFalse(SmsFilterConfigStore.isStale(fetchedAt = now - 23 * 3_600_000L, now = now))
        assertTrue(SmsFilterConfigStore.isStale(fetchedAt = now - 25 * 3_600_000L, now = now))
        assertTrue(SmsFilterConfigStore.isStale(fetchedAt = 0, now = now))
    }
}
