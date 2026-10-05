package com.jvillada.movi.server.sms

import com.jvillada.movi.server.correo.limpiarCuerpoDelCorreo
import com.jvillada.movi.server.correo.textoDelCorreo
import com.jvillada.movi.server.routes.AVANCE_CATEGORY
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Los correos de Bancolombia que ya se reenvían**, como llegan: asunto + cuerpo con el pie de
 * seguridad que trae todo correo del banco. Textos sintéticos con la forma de los reales (los
 * números de producto cambiados). Se exige que el pie no confunda ni al parser ni a
 * `queEsEsteMensaje`: un correo que trae un movimiento no puede apartarse como «aviso de seguridad».
 */
class LosCorreosDeBancolombiaSeLeenTest {

    private val origen = "Correo · Bancolombia"

    private val pie = """
        Tu seguridad es nuestra prioridad. Por tu seguridad, desde Bancolombia nunca te solicitaremos
        claves, códigos ni datos personales por correo electrónico, llamadas o mensajes de texto. Nunca
        compartas tu clave dinámica. Si no reconoces esta transacción, comunícate con nosotros al
        6045109095 o al 018000931987.
        Este es un mensaje automático, por favor no lo respondas.
    """.trimIndent()

    private fun correo(asunto: String, aviso: String) =
        textoDelCorreo(asunto, limpiarCuerpoDelCorreo("Hola, Persona de Prueba:\n\n$aviso\n\n$pie"))

    @Test
    fun `el avance de la tarjeta, con el pie de seguridad`() {
        val texto = correo(
            "Alertas y Notificaciones",
            "Bancolombia: Hiciste un avance de \$6,200,000 en tu SUC VIRTUAL el 17:43 03/10/2026 desde tu T.Credito *2222 a la cuenta *3333.",
        )
        val p = assertNotNull(parseSms(texto, origen))
        assertEquals(6_200_000.0, p.amount)
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals("Avance de la tarjeta *2222", p.merchant)
        assertEquals(AVANCE_CATEGORY, p.category)
        assertNull(p.identificadorDelDestino)
        assertNull(motivoParaApartar(texto, origen), "un movimiento no se aparta por el pie de seguridad")
    }

    @Test
    fun `el pago de la tarjeta con el monto pegado`() {
        val texto = correo(
            "Notificación Transaccional",
            "Notificación Transaccional Bancolombia: Pagaste \$386902 en la tarjeta de credito *4444 desde la cuenta *3333, el 27/09/2026 09:17.",
        )
        val p = assertNotNull(parseSms(texto, origen))
        assertEquals(386_902.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals(CARD_PAYMENT_CATEGORY, p.category)
        assertEquals("Pago de tarjeta", p.merchant)
        assertNull(motivoParaApartar(texto, origen))
    }

    @Test
    fun `la factura programada sale desde la cuenta de ahorros a nombre de quien cobra`() {
        val texto = correo(
            "Notificación Informativa",
            "Notificación Informativa Bancolombia informa pago Factura Programada IGS MULTIASISTE Ref 12019266 por \$36.890,00 desde Aho*3333. 15/09/2026.",
        )
        val p = assertNotNull(parseSms(texto, origen))
        assertEquals(36_890.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("IGS MULTIASISTE", p.merchant)
        assertNull(p.identificadorDelDestino, "Aho*3333 es de donde salió, no a quién")
        assertNull(motivoParaApartar(texto, origen))
    }

    @Test
    fun `un correo de solo seguridad si se aparta`() {
        val texto = correo("Inicio de sesión", "Bancolombia: Iniciaste sesión en la Sucursal Virtual Personas desde un nuevo dispositivo.")
        assertEquals(com.jvillada.movi.shared.model.MotivoDeApartado.AVISO_DE_SEGURIDAD, motivoParaApartar(texto, origen))
    }
}
