package com.jvillada.movi.server.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Issue #27 — el dedupe de `POST /api/sms/sync` compara texto + tiempo dentro de una
 * ventana de tolerancia. Estos tests fijan las dos mitades del contrato:
 *
 *  - el MISMO SMS físico subido por los dos caminos (broadcast vs inbox) colapsa aunque
 *    sus timestamps difieran por el truncado a minutos;
 *  - dos transacciones REALES con texto byte-idéntico separadas en el tiempo no colapsan.
 */
class SmsDedupeTest {

    private val uber = "Compra aprobada \$28.500 en Uber BV."

    // ── ventana de tolerancia ─────────────────────────────────────────────────

    @Test
    fun `same text one minute apart is a duplicate`() {
        // El skew cross-esquema: PDU timestamp vs Telephony.Sms.DATE truncados a minutos.
        assertTrue(
            isDuplicateSms(
                SmsKey(uber, "2026-08-01 07:16"),
                listOf(SmsKey(uber, "2026-08-01 07:15")),
            )
        )
    }

    @Test
    fun `same text one minute apart is a duplicate in either direction`() {
        assertTrue(
            isDuplicateSms(
                SmsKey(uber, "2026-08-01 07:15"),
                listOf(SmsKey(uber, "2026-08-01 07:16")),
            ),
            "la comparación tiene que ser simétrica: el orden de llegada no decide",
        )
    }

    @Test
    fun `same text exactly at the tolerance boundary is a duplicate`() {
        assertEquals(1L, SMS_DEDUPE_TOLERANCE.toMinutes(), "el borde probado es el real")
        assertTrue(
            isDuplicateSms(
                SmsKey(uber, "2026-08-01 07:16"),
                listOf(SmsKey(uber, "2026-08-01 07:15")),
            ),
            "la ventana es inclusiva en el borde",
        )
    }

    @Test
    fun `same text just past the tolerance is not a duplicate`() {
        assertFalse(
            isDuplicateSms(
                SmsKey(uber, "2026-08-01 07:17"),
                listOf(SmsKey(uber, "2026-08-01 07:15")),
            )
        )
    }

    @Test
    fun `same text just past the tolerance at second granularity is not a duplicate`() {
        // parseSmsTime también acepta segundos, así que el lado exclusivo de la ventana
        // hay que pinnearlo más fino que el minuto: un segundo de más ya no es duplicado.
        assertFalse(
            isDuplicateSms(
                SmsKey(uber, "2026-08-01 07:16:01"),
                listOf(SmsKey(uber, "2026-08-01 07:15:00")),
            )
        )
    }

    @Test
    fun `same text on different days is not a duplicate`() {
        // Dos viajes de $28.500 en días distintos: el caso que se perdía en silencio.
        assertFalse(
            isDuplicateSms(
                SmsKey(uber, "2026-08-03 19:40"),
                listOf(SmsKey(uber, "2026-08-01 07:15")),
            )
        )
    }

    @Test
    fun `same text same timestamp is a duplicate`() {
        assertTrue(
            isDuplicateSms(
                SmsKey(uber, "2026-08-01 07:15"),
                listOf(SmsKey(uber, "2026-08-01 07:15")),
            )
        )
    }

    @Test
    fun `different text same timestamp is not a duplicate`() {
        assertFalse(
            isDuplicateSms(
                SmsKey("Compra aprobada \$12.000 en Rappi.", "2026-08-01 07:15"),
                listOf(SmsKey(uber, "2026-08-01 07:15")),
            )
        )
    }

    @Test
    fun `no existing rows means never a duplicate`() {
        assertFalse(isDuplicateSms(SmsKey(uber, "2026-08-01 07:15"), emptyList()))
    }

    @Test
    fun `only the matching text is compared`() {
        assertTrue(
            isDuplicateSms(
                SmsKey(uber, "2026-08-01 07:15"),
                listOf(
                    SmsKey("otro texto", "2026-08-01 07:15"),
                    SmsKey(uber, "2026-08-04 12:00"),
                    SmsKey(uber, "2026-08-01 07:15"),
                ),
            )
        )
    }

    // ── formatos de tiempo ────────────────────────────────────────────────────

    @Test
    fun `wire format parses`() {
        assertEquals("2026-08-01T07:15", parseSmsTime("2026-08-01 07:15").toString())
    }

    @Test
    fun `iso local and seconds variants parse`() {
        assertEquals("2026-08-01T07:15", parseSmsTime("2026-08-01T07:15:00").toString())
        assertEquals("2026-08-01T07:15:30", parseSmsTime("2026-08-01 07:15:30").toString())
    }

    @Test
    fun `garbage time does not parse`() {
        assertNull(parseSmsTime(""))
        assertNull(parseSmsTime("ayer"))
        assertNull(parseSmsTime("01/08/2026 07:15"))
    }

    // ── fallback ante tiempo ilegible ─────────────────────────────────────────

    @Test
    fun `unparseable candidate time is treated as distinct`() {
        assertFalse(
            isDuplicateSms(
                SmsKey(uber, "ayer"),
                listOf(SmsKey(uber, "2026-08-01 07:15")),
            ),
            "sin tiempo comparable insertamos: un duplicado se ve y se corrige, una pérdida no",
        )
    }

    @Test
    fun `unparseable existing time is treated as distinct`() {
        assertFalse(
            isDuplicateSms(
                SmsKey(uber, "2026-08-01 07:15"),
                listOf(SmsKey(uber, "")),
            )
        )
    }

    @Test
    fun `two unparseable times are treated as distinct`() {
        assertFalse(
            isDuplicateSms(
                SmsKey(uber, "ayer"),
                listOf(SmsKey(uber, "ayer")),
            ),
            "aun con textos y basura idénticos preferimos insertar; el chequeo por id " +
                "es el que mantiene idempotente el re-envío de la misma fila",
        )
    }

    // ── índice (dedupe intra-lote, mismo criterio que la función pura) ─────────

    @Test
    fun `index dedupes within a batch and keeps distinct ones`() {
        val index = SmsDedupeIndex(emptyList())
        val batch = listOf(
            SmsKey(uber, "2026-08-01 07:15"),
            SmsKey(uber, "2026-08-01 07:16"),  // mismo SMS por el otro camino
            SmsKey(uber, "2026-08-03 19:40"),  // otro viaje, otro día
        )
        // Mismo bucle que la ruta: consultar, insertar, y recién ahí sembrar el índice.
        val accepted = mutableListOf<SmsKey>()
        for (key in batch) {
            if (index.isDuplicate(key)) continue
            accepted += key
            index.add(key)
        }
        assertEquals(2, accepted.size, "solo el skew de un minuto colapsa")
        assertEquals(listOf("2026-08-01 07:15", "2026-08-03 19:40"), accepted.map { it.time })
    }

    @Test
    fun `index seeded with existing rows rejects a re-upload`() {
        val index = SmsDedupeIndex(listOf(SmsKey(uber, "2026-08-01 07:15")))
        assertTrue(index.isDuplicate(SmsKey(uber, "2026-08-01 07:15")))
        assertFalse(index.isDuplicate(SmsKey(uber, "2026-08-02 07:15")))
    }
}
