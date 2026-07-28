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
    fun `corrupt or malformed json yields null (caller falls back to defaults)`() {
        assertNull(SmsFilterConfigStore.parseConfigJson("not json"))
        assertNull(SmsFilterConfigStore.parseConfigJson("""{"senderCodes":[]}"""))   // sin keywords: falta la clave
    }

    @Test
    fun `both-lists-empty json now parses fine — withDefaults, not rejection, is what keeps it fail-open`() {
        val c = SmsFilterConfigStore.parseConfigJson("""{"senderCodes":[],"bodyKeywords":[]}""")!!
        assertEquals(emptyList(), c.senderCodes)
        assertEquals(emptyList(), c.bodyKeywords)
    }

    @Test
    fun `withDefaults unions remote additions with compiled defaults, remote first`() {
        val remote = FilterConfig(senderCodes = listOf("85540", "999999"), bodyKeywords = listOf("bancolombia", "nequi"))
        val result = SmsFilterConfigStore.withDefaults(remote)
        assertEquals(listOf("85540", "999999", "891333", "87400"), result.senderCodes)
        assertEquals(listOf("bancolombia", "nequi"), result.bodyKeywords)
    }

    @Test
    fun `withDefaults keeps compiled defaults even when remote is a strict subset`() {
        // Assertion is about coverage, not order: matches() is order-agnostic, so pinning
        // exact list order here would incidentally pass only because the remote subset
        // ("85540") happens to be the first compiled default — swap it for "87400" and an
        // order-sensitive assertEquals would fail despite identical, correct behavior.
        val remote = FilterConfig(senderCodes = listOf("85540"), bodyKeywords = emptyList())
        val result = SmsFilterConfigStore.withDefaults(remote)
        assertTrue(result.senderCodes.containsAll(BankSenderFilter.DEFAULTS.senderCodes))
        assertTrue(result.bodyKeywords.containsAll(BankSenderFilter.DEFAULTS.bodyKeywords))
    }

    @Test
    fun `withDefaults on an empty-ish remote falls back to exactly the compiled defaults`() {
        val result = SmsFilterConfigStore.withDefaults(FilterConfig(emptyList(), emptyList()))
        assertEquals(BankSenderFilter.DEFAULTS, result)
    }

    @Test
    fun `withDefaults never duplicates entries present in both remote and defaults`() {
        val remote = FilterConfig(
            senderCodes = listOf("85540", "891333", "87400"),
            bodyKeywords = listOf("bancolombia"),
        )
        val result = SmsFilterConfigStore.withDefaults(remote)
        assertEquals(listOf("85540", "891333", "87400"), result.senderCodes)
        assertEquals(listOf("bancolombia"), result.bodyKeywords)
    }

    @Test
    fun `addsNothing is true only when both lists are empty — the store-level write guard`() {
        // Covers what the old parseConfigJson-both-empty-returns-null test used to pin
        // indirectly: a valid-but-empty response must not be worth writing over a good
        // cache. That guard now lives here (see fetchAndStore), separate from parsing.
        assertTrue(FilterConfig(emptyList(), emptyList()).addsNothing())
        assertFalse(FilterConfig(listOf("85540"), emptyList()).addsNothing())
        assertFalse(FilterConfig(emptyList(), listOf("bancolombia")).addsNothing())
        assertFalse(FilterConfig(listOf("85540"), listOf("bancolombia")).addsNothing())
    }

    @Test
    fun `session counts as expired only with a 401 mark and no active session`() {
        // Tras el 401 el Worker limpia la sesión: aviso + formulario de login.
        assertTrue(SmsFilterConfigStore.isSessionExpired(authErrorAt = 1_700_000_000_000, loggedIn = false))
        // Volver a entrar borra la marca; mientras tanto, sesión viva no muestra aviso.
        assertFalse(SmsFilterConfigStore.isSessionExpired(authErrorAt = 1_700_000_000_000, loggedIn = true))
        // Deslogueo normal (nunca hubo 401) no es "sesión vencida".
        assertFalse(SmsFilterConfigStore.isSessionExpired(authErrorAt = 0L, loggedIn = false))
    }

    @Test
    fun `staleness honors the 24h ttl`() {
        val now = 1_700_000_000_000
        assertFalse(SmsFilterConfigStore.isStale(fetchedAt = now - 23 * 3_600_000L, now = now))
        assertTrue(SmsFilterConfigStore.isStale(fetchedAt = now - 25 * 3_600_000L, now = now))
        assertTrue(SmsFilterConfigStore.isStale(fetchedAt = 0, now = now))
    }
}
