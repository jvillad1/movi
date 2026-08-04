package com.jvillada.movi.sms

import com.jvillada.movi.shared.model.SmsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun sms(id: String, bank: String, text: String) =
    SmsMessage(id = id, time = "2026-08-01 10:00", bank = bank, text = text, state = "new", det = "")

class SmsBackfillFilterTest {

    private val config = FilterConfig(senderCodes = listOf("85540"), bodyKeywords = listOf("bancolombia"))

    @Test
    fun `solo salen del telefono los SMS que matchean el filtro bancario`() {
        val inbox = listOf(
            sms("a", "85540", "Compra por \$50.000 en EXITO"),
            sms("b", "Mama", "¿A qué hora llegás?"),
            sms("c", "890000", "Bancolombia: retiro por \$100.000"),
            sms("d", "12345", "Tu código de verificación es 9981"),
        )
        assertEquals(listOf("a", "c"), filterBankMessages(inbox, config).map { it.id })
    }

    @Test
    fun `el mensaje personal jamas se serializa`() {
        val personal = sms("p", "Novia", "te amo")
        assertTrue(filterBankMessages(listOf(personal), config).isEmpty())
    }

    @Test
    fun `un cuerpo vacio no se sube aunque el remitente sea del banco`() {
        // Un row del inbox con body null llega como "" — subirlo solo ensucia la bandeja.
        assertTrue(filterBankMessages(listOf(sms("e", "85540", "")), config).isEmpty())
    }

    @Test
    fun `el inbox vacio devuelve vacio`() {
        assertTrue(filterBankMessages(emptyList(), config).isEmpty())
    }
}

class BackfillMessageTest {

    @Test
    fun `subida con novedades dice cuantos encontro y cuantos eran nuevos`() {
        val text = backfillMessage(BackfillOutcome.Uploaded(found = 12, synced = 3))
        assertTrue("12" in text && "3" in text, text)
        assertFalse(isBackfillError(BackfillOutcome.Uploaded(12, 3)))
    }

    @Test
    fun `subida sin novedades no se presenta como error`() {
        val outcome = BackfillOutcome.Uploaded(found = 12, synced = 0)
        assertTrue("ya los tenía" in backfillMessage(outcome))
        assertFalse(isBackfillError(outcome))
    }

    @Test
    fun `sin conteo del server igual se avisa que se enviaron`() {
        val text = backfillMessage(BackfillOutcome.Uploaded(found = 4, synced = null))
        assertTrue("4" in text, text)
    }

    @Test
    fun `cada final tiene su mensaje y ninguno es vacio`() {
        val outcomes = listOf(
            BackfillOutcome.Uploaded(1, 1),
            BackfillOutcome.NothingFound,
            BackfillOutcome.NoSession,
            BackfillOutcome.NoPermission,
            BackfillOutcome.SessionExpired,
            BackfillOutcome.Failed,
        )
        val texts = outcomes.map { backfillMessage(it) }
        assertTrue(texts.none { it.isBlank() })
        // Sin repetidos: un botón que siempre dice lo mismo es un botón que no informa.
        assertEquals(texts.size, texts.distinct().size)
    }

    @Test
    fun `los finales que impiden subir se marcan como error`() {
        assertTrue(isBackfillError(BackfillOutcome.NoSession))
        assertTrue(isBackfillError(BackfillOutcome.NoPermission))
        assertTrue(isBackfillError(BackfillOutcome.SessionExpired))
        assertTrue(isBackfillError(BackfillOutcome.Failed))
        // "No hay nada que subir" no es una falla: el sensor funcionó.
        assertFalse(isBackfillError(BackfillOutcome.NothingFound))
    }
}
