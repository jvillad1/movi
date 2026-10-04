package com.jvillada.movi.server.sms

import com.jvillada.movi.server.correo.textoDelCorreo
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.MotivoDeApartado
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Glim y Nu por correo**, listos para el día que el dueño amplíe el reenvío de Gmail. Textos
 * sintéticos con la forma de las notificaciones que ya se leen; los montos son los de siempre.
 */
class GlimYNuPorCorreoTest {

    private val glim = "Correo · Glim"
    private val nu = "Correo · Nu"

    @Test
    fun `la compra con la tarjeta de beneficios de Glim`() {
        val texto = textoDelCorreo(
            "Transacción aprobada con tu tarjeta de beneficios",
            "Pagaste \$15.100,00 COP con tu tarjeta de beneficios Glim el 02/10/2026 a las 19:25 en TOSTAO CAFE Y PAN.",
        )
        val p = assertNotNull(parseSms(texto, glim))
        assertEquals(15_100.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("TOSTAO CAFE Y PAN", p.merchant)
        assertNull(motivoParaApartar(texto, glim))
    }

    @Test
    fun `el nuevo saldo de Glim es la recarga del empleador`() {
        val texto = textoDelCorreo(
            "Nuevo saldo disponible",
            "¡Tienes nuevo saldo!: Mercado Libre Colombia Ltda recargó 640.000,00 COP en tu tarjeta de beneficios.",
        )
        val p = assertNotNull(parseSms(texto, glim))
        assertEquals(640_000.0, p.amount)
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals("Recarga de beneficios · Mercado Libre Colombia Ltda", p.merchant)
        assertEquals("Salario", p.category)
        assertNull(motivoParaApartar(texto, glim))
    }

    @Test
    fun `el pago desde la Cuenta Nu, como lo dice el correo`() {
        val texto = textoDelCorreo(
            "Pagaste en Coomeva Medicina Prepagada S.A. con Cuenta Nu",
            "Hola, Persona: tu pago por \$138.600,00 fue aprobado. Pagaste en Coomeva Medicina Prepagada S.A. con Cuenta Nu el 03/10/2026.",
        )
        val p = assertNotNull(parseSms(texto, nu))
        assertEquals(138_600.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("Coomeva Medicina Prepagada S.A.", p.merchant)
        assertEquals("Salud", p.category)
        assertNull(motivoParaApartar(texto, nu))
    }

    @Test
    fun `el extracto de la Cuenta Nu se aparta con su propio motivo`() {
        val texto = textoDelCorreo("El extracto de tu Cuenta Nu ya está aquí", "Hola, Persona: ya puedes ver el extracto de septiembre en el archivo adjunto.")
        assertNull(parseSms(texto, nu))
        assertEquals(MotivoDeApartado.EXTRACTO_DISPONIBLE, motivoParaApartar(texto, nu))
    }

    @Test
    fun `lo promocional de Nu por correo se aparta como promocion`() {
        listOf(
            textoDelCorreo("Haz crecer tu plata", "Hola, Persona: con las Cajitas de Nu tu plata crece todos los días. Conoce más en la app."),
            textoDelCorreo("Juan, tu árbol sigue sin adornos", "Invita a alguien a Nu y empieza a adornarlo. Participa por viajes y premios."),
        ).forEach { texto ->
            assertNull(parseSms(texto, nu), texto)
            assertEquals(MotivoDeApartado.PROMOCION, motivoParaApartar(texto, nu), texto)
        }
    }

    @Test
    fun `la confirmacion de PSE de Nu no se esconde`() {
        // Duplica el pago, pero ante la duda es movimiento: queda en la bandeja para que él decida.
        val texto = textoDelCorreo("Transacción exitosa y nuevo comercio guardado por PSE", "Tu pago por \$138.600,00 a Coomeva Medicina Prepagada S.A. fue exitoso.")
        assertNull(motivoParaApartar(texto, nu))
    }

    @Test
    fun `la regla de promocion de Nu es solo para el correo, no para las notificaciones`() {
        // Una notificación de Nu sin forma de movimiento y sin ninguna marca sigue en la bandeja.
        assertNull(motivoParaApartar("Algo nuevo de Nu para 2026: mira lo que preparamos para ti.", "Notificación · Nu"))
    }
}
