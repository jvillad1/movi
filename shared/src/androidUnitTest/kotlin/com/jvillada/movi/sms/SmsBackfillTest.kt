package com.jvillada.movi.sms

import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun sms(id: String, bank: String, text: String) =
    SmsMessage(id = id, time = "2026-08-01 10:00", bank = bank, text = text, state = SMS_STATE_PENDING, det = "")

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
    fun `un 401 que no cerro la sesion avisa sin mandar a loguearse de nuevo`() {
        // M1: con la racha de SessionManager, un 401 suelto ya no desloguea. El mensaje
        // no puede decir "vuelve a entrar" (la sesión sigue viva) ni culpar a la conexión.
        val text = backfillMessage(BackfillOutcome.AuthRetry)
        assertTrue("prueba de nuevo" in text, text)
        assertFalse("vuelve a entrar" in text, text)
        assertFalse("conexión" in text, text)
        assertTrue(isBackfillError(BackfillOutcome.AuthRetry))
    }

    @Test
    fun `el aviso de pausa dice desde cuando y como recuperar`() {
        // m2: quien fue deslogueado por el worker aterriza en un login genérico; al volver,
        // la sección le cuenta que la captura estuvo muda y cuál es el remedio.
        val text = captureOutageNotice(1755730860000L) // 2026-08-20 (hora local)
        assertTrue("pausada" in text, text)
        assertTrue(Regex("\\d{2}/\\d{2}/\\d{4}").containsMatchIn(text), text)
        assertTrue("historial" in text.lowercase(), text)
    }

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

class OutcomeForReadFailureTest {

    @Test
    fun `un SecurityException al leer el inbox se mapea a NoPermission, no a Failed`() {
        // Esto es justo lo que el auto-revoke por hibernación le hace a esta app: READ_SMS
        // se revoca entre el chequeo de la UI y la llamada real a contentResolver.query.
        // "Revisá la conexión" sería un consejo falso y sin salida.
        assertEquals(BackfillOutcome.NoPermission, outcomeForReadFailure(SecurityException("permiso revocado")))
    }

    @Test
    fun `cualquier otra falla de lectura se mapea a Failed`() {
        assertEquals(BackfillOutcome.Failed, outcomeForReadFailure(RuntimeException("boom")))
        assertEquals(BackfillOutcome.Failed, outcomeForReadFailure(IllegalStateException("cursor nulo")))
    }
}
