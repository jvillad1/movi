package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.CapturaDeSms
import com.jvillada.movi.shared.model.CodigoDeCaptura
import com.jvillada.movi.shared.model.avisoDeCaptura
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # «Mensajes del banco» se lee de un vistazo (vuelta de diseño 8)
 *
 * La primera tarjeta eran dos párrafos (≈8 renglones). Ahora una fila («Último mensaje · hoy, …»)
 * y una línea de ayuda; lo largo se abre con «¿Qué es esto?». Lo que se fija acá: que nada de lo
 * que decía se pierda, que solo se pliega.
 */
class ElUltimoMensajeTest {

    private val hoy = LocalDate(2026, 10, 27)

    @Test
    fun `con mensajes la fila dice cuando llego el ultimo y cuantos van`() {
        val captura = CapturaDeSms(total = 120, ultimo = "2026-10-27 11:02")
        val fila = elUltimoMensaje(captura, hoy, enAndroid = false)
        assertEquals("Último mensaje · hoy, 11:02 a. m.", fila.titulo)
        assertEquals("120 mensajes de tu banco", fila.subtitulo)
        assertFalse(fila.esAlerta)
    }

    @Test
    fun `en la web y en iOS la linea dice que este aparato no lee mensajes, y lo demas se pliega`() {
        val captura = CapturaDeSms(total = 3, ultimo = "2026-09-03 07:15")
        val fila = elUltimoMensaje(captura, hoy, enAndroid = false)
        assertEquals(ESTE_APARATO_NO_LEE_MENSAJES, fila.ayuda)
        // Nada se pierde: el detalle de siempre y quién lee los mensajes, enteros.
        assertTrue(fila.masAyuda.startsWith(avisoDeCaptura(captura).detalle), fila.masAyuda)
        assertTrue("teléfono Android con Movi instalado" in fila.masAyuda, fila.masAyuda)
        assertTrue("«Por revisar»" in fila.masAyuda, fila.masAyuda)
    }

    @Test
    fun `en Android la linea dice que la fecha es lo que llego`() {
        val captura = CapturaDeSms(total = 1, ultimo = "2026-10-26 08:10")
        val fila = elUltimoMensaje(captura, hoy, enAndroid = true)
        assertEquals("Último mensaje · ayer, 8:10 a. m.", fila.titulo)
        assertEquals("El único que ha llegado de tu banco", fila.subtitulo)
        assertEquals(LA_FECHA_DICE_LO_QUE_LLEGO, fila.ayuda)
        assertEquals(avisoDeCaptura(captura).detalle, fila.masAyuda)
    }

    @Test
    fun `sin un solo mensaje la fila lo dice como alerta`() {
        val fila = elUltimoMensaje(CapturaDeSms(), hoy, enAndroid = false)
        assertEquals(NUNCA_HA_LLEGADO_UN_MENSAJE, fila.titulo)
        assertTrue(fila.esAlerta)
        assertTrue("Movi todavía no ha recibido ningún mensaje de tu banco" in fila.masAyuda)
    }

    @Test
    fun `la ayuda es de una linea corta en los dos casos`() {
        listOf(true, false).forEach { android ->
            val fila = elUltimoMensaje(CapturaDeSms(total = 2, ultimo = "2026-10-27 11:02"), hoy, android)
            assertTrue(fila.ayuda.length <= 60, "«${fila.ayuda}» no es una línea")
        }
    }

    @Test
    fun `el banco que se calla dice una linea y lo demas detras de Que es esto`() {
        val (encendido, explicacion) = ayudaDelBancoQueSeCalla(3)
        assertEquals("Te avisa en Inicio tras 3 días sin nada.", encendido)
        assertTrue("3 días sin mandar nada" in explicacion && "Movi te lo dice en Inicio" in explicacion, explicacion)
        val (apagado, porQue) = ayudaDelBancoQueSeCalla(0)
        assertEquals("Apagado: Movi no te avisa.", apagado)
        assertTrue("mensajes, avisos o correos" in porQue, porQue)
        assertEquals("Te avisa en Inicio tras 1 día sin nada.", ayudaDelBancoQueSeCalla(1).first)
    }

    /**
     * Vuelta 114 · en el iPhone de Caro, con el atajo mandando avisos, la tarjeta decía «Este aparato no lee los
     * mensajes del banco». Ahora la línea dice lo cierto, y como el código es de la cuenta (no del aparato), no
     * dice «este iPhone»: vale en cualquier aparato de la cuenta.
     */
    @Test
    fun `con el atajo mandando avisos la linea lo dice, en cualquier aparato de la cuenta`() {
        val captura = CapturaDeSms(total = 2, ultimo = "2026-11-02 08:30")
        val ahora = 1_793_628_000_000L
        val usado = CodigoDeCaptura(vigente = true, creadoEn = ahora - 86_400_000L, ultimoUso = ahora - 24 * 60_000L)
        val esperado = "Tus mensajes del banco llegan por un atajo de Atajos · último aviso hace 24 min"
        val enIphone = elUltimoMensaje(captura, hoy, enAndroid = false, enIphone = true, codigoDelAtajo = usado, ahora = ahora)
        assertEquals(esperado, enIphone.ayuda)
        assertFalse("este iPhone" in enIphone.ayuda, enIphone.ayuda)
        assertFalse("teléfono Android" in enIphone.masAyuda, enIphone.masAyuda)
        assertTrue("«Desde tu iPhone»" in enIphone.masAyuda && "«Por revisar»" in enIphone.masAyuda, enIphone.masAyuda)
        // En una computadora con la misma cuenta, también.
        assertEquals(esperado, elUltimoMensaje(captura, hoy, enAndroid = false, codigoDelAtajo = usado, ahora = ahora).ayuda)
        // En Android, la de siempre: ahí Movi lee el teléfono.
        assertEquals(LA_FECHA_DICE_LO_QUE_LLEGO, elUltimoMensaje(captura, hoy, enAndroid = true, codigoDelAtajo = usado, ahora = ahora).ayuda)
    }

    @Test
    fun `sin un atajo que haya mandado algo, el iPhone dice donde configurarlo y nada falso mientras no se sabe`() {
        val captura = CapturaDeSms(total = 2, ultimo = "2026-11-02 08:30")
        val sinUsar = CodigoDeCaptura(vigente = true, creadoEn = 1)
        val revocado = CodigoDeCaptura(vigente = false, ultimoUso = 2)
        listOf(sinUsar, revocado, CodigoDeCaptura()).forEach { codigo ->
            assertEquals(
                "Este iPhone no los lee solo: configúralo en «Desde tu iPhone».",
                elUltimoMensaje(captura, hoy, enAndroid = false, enIphone = true, codigoDelAtajo = codigo).ayuda,
            )
            assertEquals(ESTE_APARATO_NO_LEE_MENSAJES, elUltimoMensaje(captura, hoy, enAndroid = false, codigoDelAtajo = codigo).ayuda)
        }
        // Mientras no se sabe, nada que pueda ser falso.
        assertEquals(EN_UN_IPHONE_LLEGAN_CON_UN_ATAJO, elUltimoMensaje(captura, hoy, enAndroid = false, enIphone = true).ayuda)
        assertEquals(ESTE_APARATO_NO_LEE_MENSAJES, elUltimoMensaje(captura, hoy, enAndroid = false).ayuda)
    }
}
