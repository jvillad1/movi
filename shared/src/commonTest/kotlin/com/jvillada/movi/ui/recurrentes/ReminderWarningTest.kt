package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.ReminderChannels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderWarningTest {

    /** Lo que contesta un server SIN canal de correo: es el único estado que permite el aviso. */
    private val sinCorreo = ReminderChannels(email = false, push = true)

    /** Producción: `RESEND_API_KEY` puesta y el remitente de pruebas de Resend. */
    private val conCorreoDePrueba = ReminderChannels(
        email = true,
        emailTo = "jvillad1@gmail.com",
        emailSandbox = true,
        push = true,
    )

    /** Un server con dominio verificado: el correo sale para cualquiera. */
    private val conCorreoPropio = ReminderChannels(
        email = true,
        emailTo = "jvillad1@gmail.com",
        emailSandbox = false,
        push = true,
    )

    @Test
    fun `push already enabled means nothing to warn about`() {
        assertFalse(shouldShowReminderWarning("enabled", hayRecordatoriosPedidos = true, canales = sinCorreo))
    }

    @Test
    fun `no upcoming payments means the warning would not be actionable`() {
        assertFalse(shouldShowReminderWarning("disabled", hayRecordatoriosPedidos = false, canales = sinCorreo))
        assertFalse(shouldShowReminderWarning("denied", hayRecordatoriosPedidos = false, canales = sinCorreo))
    }

    @Test
    fun `unsupported platform has no fix to offer so we stay quiet`() {
        assertFalse(shouldShowReminderWarning("unsupported", hayRecordatoriosPedidos = true, canales = sinCorreo))
    }

    @Test
    fun `disabled push with payments due is exactly the actionable case`() {
        assertTrue(shouldShowReminderWarning("disabled", hayRecordatoriosPedidos = true, canales = sinCorreo))
    }

    @Test
    fun `denied push with payments due still warns, just with a different fix`() {
        assertTrue(shouldShowReminderWarning("denied", hayRecordatoriosPedidos = true, canales = sinCorreo))
    }

    // ── El arreglo: el canal de correo entra en la decisión ────────────────────

    /**
     * **El bug, en una línea.** El server manda los correos y la app decía que no iba a llegar
     * nada. Con canal de correo no hay promesa rota, así que no hay nada que advertir.
     */
    @Test
    fun `con canal de correo el aviso no aparece, aunque el push este apagado`() {
        assertFalse(shouldShowReminderWarning("disabled", hayRecordatoriosPedidos = true, canales = conCorreoDePrueba))
        assertFalse(shouldShowReminderWarning("denied", hayRecordatoriosPedidos = true, canales = conCorreoDePrueba))
        assertFalse(shouldShowReminderWarning("disabled", hayRecordatoriosPedidos = true, canales = conCorreoPropio))
    }

    /**
     * «Todavía no sé» no es «no hay nada». Mientras la respuesta del server no llegue (o si falló),
     * el aviso se calla: afirmar que un recordatorio no va a llegar sin haber preguntado es
     * exactamente lo que había que sacar.
     */
    @Test
    fun `sin respuesta del server no se afirma que no hay canal`() {
        assertFalse(shouldShowReminderWarning("disabled", hayRecordatoriosPedidos = true, canales = null))
        assertFalse(shouldShowReminderWarning("denied", hayRecordatoriosPedidos = true, canales = null))
    }

    // ── La casilla «Recordarme unos días antes» en las hojas de crear/editar ──

    @Test
    fun `la casilla desmarcada no promete nada, asi que no hay nada que advertir`() {
        assertFalse(shouldShowReminderOptInWarning("disabled", remindMe = false, canales = sinCorreo))
        assertFalse(shouldShowReminderOptInWarning("denied", remindMe = false, canales = sinCorreo))
    }

    @Test
    fun `la casilla marcada sin canal activo tiene que decir la verdad`() {
        assertTrue(shouldShowReminderOptInWarning("disabled", remindMe = true, canales = sinCorreo))
        assertTrue(shouldShowReminderOptInWarning("denied", remindMe = true, canales = sinCorreo))
    }

    @Test
    fun `la casilla marcada con push activo no advierte nada`() {
        assertFalse(shouldShowReminderOptInWarning("enabled", remindMe = true, canales = sinCorreo))
    }

    @Test
    fun `sin soporte de push no hay instruccion que dar, asi que no se advierte`() {
        assertFalse(shouldShowReminderOptInWarning("unsupported", remindMe = true, canales = sinCorreo))
    }

    /** El caso del dueño: correo configurado, notificaciones apagadas, y la casilla marcada. */
    @Test
    fun `la casilla marcada con correo configurado no advierte nada`() {
        assertFalse(shouldShowReminderOptInWarning("disabled", remindMe = true, canales = conCorreoDePrueba))
    }

    @Test
    fun `la linea chica dice cuantos dias antes avisa, en singular y en plural`() {
        assertEquals("Te avisamos 3 días antes del vencimiento.", reminderLeadHint(3))
        assertEquals("Te avisamos 1 día antes del vencimiento.", reminderLeadHint(1))
    }

    @Test
    fun `con cero dias de anticipacion la linea no miente — avisa el mismo dia`() {
        assertEquals("Te avisamos el día del vencimiento.", reminderLeadHint(0))
    }

    // ── La línea chica, con el canal adentro ──────────────────────────────────

    @Test
    fun `sin saber nada del server la linea solo dice cuando, no por donde`() {
        assertEquals(listOf("Te avisamos 3 días antes del vencimiento."), reminderDeliveryLines(null, 3))
    }

    @Test
    fun `sin canal de correo la linea tampoco nombra ninguno`() {
        assertEquals(listOf("Te avisamos 3 días antes del vencimiento."), reminderDeliveryLines(sinCorreo, 3))
    }

    @Test
    fun `con correo la linea dice a que direccion sale`() {
        assertEquals(
            listOf("Te avisamos 3 días antes del vencimiento, por correo a jvillad1@gmail.com."),
            reminderDeliveryLines(conCorreoPropio, 3),
        )
    }

    /**
     * Con el remitente de pruebas el correo SÍ sale —para el dueño de la cuenta de Resend—, así
     * que la primera línea no cambia; lo que se agrega es a quién alcanza, que es lo único que
     * el server puede afirmar con certeza.
     */
    @Test
    fun `con el remitente de prueba se dice a quien alcanza, sin negar la entrega`() {
        val lineas = reminderDeliveryLines(conCorreoDePrueba, 3)
        assertEquals(2, lineas.size)
        assertEquals("Te avisamos 3 días antes del vencimiento, por correo a jvillad1@gmail.com.", lineas[0])
        assertTrue(lineas[1].startsWith("Por ahora el correo solo llega"))
        assertFalse(lineas.any { it.contains("no te va a llegar") })
    }

    /** Un server con correo pero sin dirección (no debería pasar) no inventa un destinatario. */
    @Test
    fun `con correo y sin direccion no se inventa a quien`() {
        assertEquals(
            listOf("Te avisamos 1 día antes del vencimiento, por correo."),
            reminderDeliveryLines(ReminderChannels(email = true, emailTo = null), 1),
        )
    }

    /** Los días los manda el server: con 0 la frase entera cambia, también con canal de correo. */
    @Test
    fun `con cero dias y correo la frase sigue siendo cierta`() {
        assertEquals(
            listOf("Te avisamos el día del vencimiento, por correo a jvillad1@gmail.com."),
            reminderDeliveryLines(conCorreoPropio, 0),
        )
    }
}
