package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.origenDelAviso
import com.jvillada.movi.server.routes.conLosAvisosEnGrupo
import com.jvillada.movi.server.routes.gruposDelMismoPago
import com.jvillada.movi.server.routes.losQueAbrenUnPago
import com.jvillada.movi.server.routes.minutosParaElMismoPago
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.server.routes.propuestaDelGrupo
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.cuantosPagosPorRevisar
import com.jvillada.movi.shared.model.unoPorPago
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Los avisos de un mismo pago van juntos
 *
 * Un pago llega por varios canales —el SMS del banco, la notificación de su app, la de Google
 * Wallet, el correo— y cada aviso era una tarjeta en «Por revisar». El 25-sep el dueño aprobó dos
 * avisos del mismo pago con la Glim y quedaron dos movimientos. Estos textos son de ejemplo: tienen
 * la forma de los de cada canal, con comercios y montos inventados.
 */
class AvisosDelMismoPagoTest {

    /** Mucho después de todos los mensajes: `momentoDelSms` no recorta ninguno. */
    private val ahora = 1_900_000_000_000L

    private fun sms(
        id: String,
        bank: String,
        text: String,
        time: String = "2026-09-25 09:15",
        state: String = SMS_STATE_PENDING,
    ) = SmsMessage(id = id, time = time, bank = bank, text = text, state = state, det = "")

    private val wallet = sms("w", "Notificación · Google Wallet", "PANADERIA LA ESQUINA: COP12,300 with Debito Mastercard ••1111")
    private val app = sms(
        "n", "Notificación · Bancolombia",
        "Compraste \$12.300,00 en PANADERIA LA ESQUINA con tu T.Deb *1111.",
        time = "2026-09-25 09:16",
    )
    private val delSms = sms(
        "s", "85540",
        "Bancolombia: Compraste \$12.300,00 en PANADERIA LA ESQ con tu T.Deb *1111, el 25/09/2026 a las 09:15.",
    )

    private fun grupos(vararg mensajes: SmsMessage, sueltos: Set<String> = emptySet()): List<Set<String>> =
        gruposDelMismoPago(mensajes.toList(), ahora, sueltos).map { it.ids.toSet() }

    // ── Cómo se forma ────────────────────────────────────────────────────────────

    @Test
    fun `SMS, notificacion del banco y Google Wallet del mismo pago son un pago de tres`() {
        assertEquals(listOf(setOf("w", "n", "s")), grupos(wallet, app, delSms))
        val marcados = conLosAvisosEnGrupo(listOf(wallet, app, delSms), ahora).associateBy { it.id }
        // El id del pago es el del aviso más antiguo (a igual minuto, el menor id): estable.
        assertEquals(setOf("s"), marcados.values.map { it.grupoId }.toSet())
        assertEquals(listOf("s", "w", "n"), marcados.getValue("n").miembrosDelGrupo)
        assertEquals(1, cuantosPagosPorRevisar(marcados.values.toList()))
        assertEquals(1, unoPorPago(marcados.values.toList()).size)
    }

    @Test
    fun `dos compras iguales del mismo canal no son el mismo pago`() {
        val otroCafe = wallet.copy(id = "w2", time = "2026-09-25 09:17")
        assertEquals(emptyList(), grupos(wallet, otroCafe))
        assertEquals(2, cuantosPagosPorRevisar(conLosAvisosEnGrupo(listOf(wallet, otroCafe), ahora)))
    }

    @Test
    fun `dos compras iguales seguidas, cada una avisada por dos canales, son dos pagos y no uno de cuatro`() {
        val wallet2 = wallet.copy(id = "w2", time = "2026-09-25 09:19")
        val app2 = app.copy(id = "n2", time = "2026-09-25 09:19")
        assertEquals(setOf(setOf("w", "n"), setOf("w2", "n2")), grupos(wallet, app, wallet2, app2).toSet())
    }

    @Test
    fun `la cadena A, B, C se junta aunque A y C esten lejos`() {
        // A (Wallet, 9:00) ~ B (SMS, 9:08) ~ C (app del banco, 9:16): A y C a 16 minutos.
        val a = wallet.copy(time = "2026-09-25 09:00")
        val b = delSms.copy(time = "2026-09-25 09:08")
        val c = app.copy(time = "2026-09-25 09:16")
        assertEquals(listOf(setOf("w", "s", "n")), grupos(a, b, c))
    }

    @Test
    fun `los codigos cortos de SMS son un solo canal`() {
        // Bancolombia usa varios remitentes; dos SMS iguales de dos códigos son dos compras.
        val otroCodigo = delSms.copy(id = "s2", bank = "85784", time = "2026-09-25 09:16")
        assertEquals(emptyList(), grupos(delSms, otroCodigo))
        assertEquals(origenDelAviso("85540"), origenDelAviso("85784"))
        assertEquals(origenDelAviso(""), origenDelAviso("SMS"))
        // El espacio duro que trae Android en «Google Wallet» no hace otro canal.
        assertEquals(origenDelAviso("Notificación · Google Wallet"), origenDelAviso("Notificación · Google\u00A0Wallet"))
    }

    @Test
    fun `con otro monto, otra moneda o otro tipo no son el mismo pago`() {
        assertEquals(emptyList(), grupos(wallet, app.copy(text = app.text.replace("12.300,00", "12.400,00"))))
        assertEquals(emptyList(), grupos(wallet, sms("u", "Notificación · Nu", "Compra aprobada por USD12.300,00: Tu compra en X por USD12.300,00 con tu tarjeta terminada en 2222 ha sido APROBADA.")))
        assertEquals(emptyList(), grupos(wallet, sms("i", "Notificación · Nu", "Recibiste 12.300,00 en tu cuenta: Te llegó dinero de ALGUIEN con tu llave.")))
    }

    // ── La ventana ───────────────────────────────────────────────────────────────

    @Test
    fun `entre SMS y notificaciones, treinta minutos`() {
        assertEquals(30L, minutosParaElMismoPago("85540", "Notificación · Google Wallet"))
        assertEquals(listOf(setOf("w", "s")), grupos(wallet, delSms.copy(time = "2026-09-25 09:45")))
        assertEquals(emptyList(), grupos(wallet, delSms.copy(time = "2026-09-25 09:46")))
    }

    /** El caso real del 2-oct: Google Wallet avisó 21 minutos después que la app de la tarjeta. */
    @Test
    fun `Google Wallet que avisa tarde se junta con la app de la tarjeta`() {
        val tarjeta = sms("t", "Notificación · Glim", "¡Usaste tus beneficios!: Pagaste \$3.300,00 COP con tu tarjeta de beneficios Glim en MAQUINA DE EJEMPLO.", time = "2026-10-02 11:22")
        val tarde = sms("g", "Notificación · Google Wallet", "PWS*MAQUINA EJEMPLO: COP3,300 with Glim ••1111", time = "2026-10-02 11:43")
        assertEquals(listOf(setOf("t", "g")), grupos(tarjeta, tarde))
    }

    @Test
    fun `el correo que llega tarde entra en la ventana`() {
        val correo = sms(
            "c", "Correo · Bancolombia",
            "Bancolombia le informa compra por \$12.300,00 en PANADERIA LA ESQUINA con su T.Deb *1111.",
            time = "2026-09-25 10:00",
        )
        assertEquals(60L, minutosParaElMismoPago("Correo · Bancolombia", "85540"))
        // 45 minutos después del SMS: el mismo pago.
        assertEquals(listOf(setOf("s", "c")), grupos(delSms, correo))
        // A más de una hora, ya no.
        assertEquals(emptyList(), grupos(delSms, correo.copy(time = "2026-09-25 10:16")))
    }

    // ── Quién entra ──────────────────────────────────────────────────────────────

    @Test
    fun `lo que el duenno separo no vuelve a juntarse`() {
        assertEquals(listOf(setOf("w", "s")), grupos(wallet, app, delSms, sueltos = setOf("n")))
        assertEquals(emptyList(), grupos(wallet, app, delSms, sueltos = setOf("w", "n", "s")))
        // Ni con un aviso nuevo del mismo pago que llegue después.
        val correo = sms("c", "Correo · Bancolombia", "Compra por \$12.300,00 en PANADERIA LA ESQUINA *1111.", time = "2026-09-25 09:30")
        assertEquals(emptyList(), grupos(wallet, correo, sueltos = setOf("w")))
    }

    @Test
    fun `con un aviso ya confirmado, los demas se marcan ya anotados`() {
        val marcados = conLosAvisosEnGrupo(listOf(wallet.copy(state = SMS_STATE_CONFIRMED), app, delSms), ahora).associateBy { it.id }
        assertEquals("w", marcados.getValue("n").yaAnotadoCon)
        assertEquals("w", marcados.getValue("s").yaAnotadoCon)
        assertNull(marcados.getValue("w").yaAnotadoCon, "el confirmado no lleva la marca")
        assertEquals("s", marcados.getValue("w").grupoId)
        // Sigue siendo un pago por revisar: hay que cerrar los otros dos.
        assertEquals(1, cuantosPagosPorRevisar(marcados.values.toList()))
    }

    @Test
    fun `lo ignorado no entra, y un pago sin pendientes no se marca`() {
        assertEquals(emptyList(), grupos(wallet.copy(state = SMS_STATE_IGNORED), app))
        assertEquals(emptyList(), grupos(wallet.copy(state = SMS_STATE_CONFIRMED), app.copy(state = SMS_STATE_CONFIRMED)))
    }

    @Test
    fun `cada pendiente apunta al aviso mas cercano de su pago, para los APK viejos`() {
        val marcados = conLosAvisosEnGrupo(listOf(wallet, app, delSms), ahora).associateBy { it.id }
        assertEquals("s", marcados.getValue("w").parecidoA)
        assertTrue(marcados.getValue("n").parecidoA in setOf("w", "s"))
    }

    @Test
    fun `no cambia el orden ni los demas campos, y sin pares no marca nada`() {
        val entrada = listOf(app, wallet, delSms)
        val salida = conLosAvisosEnGrupo(entrada, ahora)
        assertEquals(entrada.map { it.id }, salida.map { it.id })
        assertEquals(entrada, salida.map { it.copy(grupoId = null, miembrosDelGrupo = emptyList(), parecidoA = null) })
        val solo = listOf(wallet)
        assertEquals(solo, conLosAvisosEnGrupo(solo, ahora))
    }

    @Test
    fun `un mensaje que no es un movimiento, o una hora ilegible o futura, no entra`() {
        val atajo = sms("a", "Notificación · Google Wallet", "Set up a shortcut to pay: double press the power button")
        assertEquals(emptyList(), grupos(atajo, delSms))
        assertEquals(emptyList(), grupos(wallet.copy(time = "ayer"), delSms.copy(time = "sin hora")))
        assertEquals(emptyList(), gruposDelMismoPago(listOf(wallet, delSms), ahora = 1_750_000_000_000L))
    }

    @Test
    fun `los comprobantes compartidos no se juntan`() {
        val comprobante = sms("cmp_doc1", "Comprobante · recibo.png", "Pago por \$12.300,00 en PANADERIA LA ESQUINA")
        assertEquals(emptyList(), grupos(wallet, comprobante))
    }

    /** El historial crece sin tope: solo se lee lo que está cerca de algún pendiente. */
    @Test
    fun `solo se leen los avisos cerca de un pendiente`() {
        val viejos = (1..50).map { i ->
            sms("v$i", "85540", "Bancolombia: Compraste \$12.300,00 en PANADERIA", time = "2026-08-%02d 09:15".format(1 + i % 28), state = SMS_STATE_CONFIRMED)
        }
        val leidos = mutableListOf<String>()
        val salida = gruposDelMismoPago(viejos + wallet + app, ahora, leer = { m -> leidos += m.id; parseSms(m.text, m.bank) })
        assertEquals(setOf("w", "n"), leidos.toSet())
        assertEquals(listOf(setOf("w", "n")), salida.map { it.ids.toSet() })
    }

    // ── De qué aviso sale la propuesta ──────────────────────────────────────────

    @Test
    fun `la propuesta sale del que trae comercio y cuenta, y a igualdad del SMS`() {
        // Los tres traen comercio y cuenta: gana el SMS, el registro del banco.
        assertEquals("s", propuestaDelGrupo(listOf(wallet, app, delSms)).id)
        // Un pago por QR: el SMS solo trae la llave; Google Wallet trae el comercio.
        val qr = sms("q", "85540", "Bancolombia: pagaste \$12,300.00 por codigo QR desde tu cuenta *2222 a la llave 0099887766 el 25/09/2026.")
        assertEquals("w", propuestaDelGrupo(listOf(qr, wallet)).id)
        // Sin cuenta escrita, el que la nombra gana.
        val sinCuenta = sms("x", "Notificación · Otra", "Compraste \$12.300,00 en PANADERIA LA ESQUINA.")
        assertEquals("w", propuestaDelGrupo(listOf(sinCuenta, wallet)).id)
    }

    // ── El aviso del teléfono ────────────────────────────────────────────────────

    @Test
    fun `el telefono avisa una vez por pago`() {
        // Subidos juntos: avisa el primero.
        val bandeja = conLosAvisosEnGrupo(listOf(wallet, delSms), ahora)
        assertEquals(listOf("w"), losQueAbrenUnPago(listOf(wallet, delSms), bandeja).map { it.id })
        // El SMS que llega después de una notificación que ya estaba: no se avisa.
        assertEquals(emptyList(), losQueAbrenUnPago(listOf(delSms), bandeja))
        // Lo que no se juntó con nada, sí.
        val solo = conLosAvisosEnGrupo(listOf(app), ahora)
        assertEquals(listOf("n"), losQueAbrenUnPago(listOf(app), solo).map { it.id })
    }
}
