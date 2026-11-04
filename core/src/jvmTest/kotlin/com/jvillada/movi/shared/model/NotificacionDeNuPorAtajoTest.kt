package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 127 · la notificación de Nu que manda el atajo del iPhone
 *
 * Las formas reales de producción (con los dígitos cambiados): un Android las sube como «título: cuerpo». No
 * se sabe qué manda Atajos, así que cada una se prueba solo con el cuerpo, con título y cuerpo (con «: », en
 * dos renglones y pegados) y con el nombre de la app delante («Nu», «Nu:», «Nu -» y «Nu» con un salto).
 */
class NotificacionDeNuPorAtajoTest {

    private class Forma(val titulo: String, val cuerpo: String) {
        /** Lo que sube un Android: `textoDeLaNotificacion(título, cuerpo)`. */
        val delAndroid: String = textoDeUnaNotificacion(titulo, cuerpo)

        /** Título y cuerpo como los podría mandar el atajo. */
        val conTitulo: List<String> = listOf("$titulo: $cuerpo", "$titulo\n$cuerpo", "$titulo $cuerpo", "  $titulo\r\n$cuerpo  ")

        /** Con el nombre de la app delante. */
        val conLaApp: List<String> = listOf(
            "Nu $titulo: $cuerpo", "Nu: $titulo: $cuerpo", "Nu - $titulo\n$cuerpo", "Nu\n$titulo\n$cuerpo", "NU:\n$titulo $cuerpo",
        )
    }

    private val compra = Forma(
        "Compra aprobada por \$27.500,00",
        "Tu compra en ANTHROPIC* CLAUDE SUB por \$27.500,00 con tu tarjeta terminada en 1336 ha sido APROBADA.",
    )
    private val pagoDeLaTarjeta = Forma(
        "¡Bravo! Pagaste tu tarjeta de crédito Nu",
        "Recibimos tu pago por \$1.306.937,91. En un rato podrás verlo en tu app.",
    )
    private val deposito = Forma(
        "Recibimos tu depósito por 1.500.000,00",
        "¡No te preocupes! En unas horas podrás revisar la actualización en tu app.",
    )
    private val rechazo = Forma(
        "¡Uy! Algo salió mal con tu pago",
        "Tu pago por \$45.000,00 no fue aprobado. Revisa el cupo de tu tarjeta.",
    )
    private val verificada = Forma(
        "Tarjeta verificada",
        "ANTHROPIC verificó tu tarjeta terminada en 1336 para tus pagos.",
    )
    private val recibiste = Forma(
        "Recibiste 300.000,00 en tu cuenta",
        "Te llegó dinero de CAROLINA PRUEBA con tu llave.",
    )

    private val todas = listOf(compra, pagoDeLaTarjeta, deposito, rechazo, verificada, recibiste)

    @Test
    fun `titulo y cuerpo, vengan como vengan, dan el texto del Android`() {
        for (forma in todas) {
            for (enviado in forma.conTitulo + forma.conLaApp) {
                assertEquals(forma.delAndroid, notificacionDeNuDelAtajo(enviado), "mandado así: «$enviado»")
            }
        }
    }

    @Test
    fun `el texto del Android tal cual tambien es de Nu, y no cambia`() {
        for (forma in todas) assertEquals(forma.delAndroid, notificacionDeNuDelAtajo(forma.delAndroid))
    }

    @Test
    fun `solo el cuerpo de la compra y del pago de la tarjeta recupera su titulo`() {
        assertEquals(compra.delAndroid, notificacionDeNuDelAtajo(compra.cuerpo))
        assertEquals(pagoDeLaTarjeta.delAndroid, notificacionDeNuDelAtajo(pagoDeLaTarjeta.cuerpo))
        assertEquals(compra.delAndroid, notificacionDeNuDelAtajo("Nu\n${compra.cuerpo}"))
    }

    @Test
    fun `solo el cuerpo del rechazo y de la tarjeta verificada se reconoce como llego`() {
        assertEquals(rechazo.cuerpo, notificacionDeNuDelAtajo(rechazo.cuerpo))
        assertEquals(verificada.cuerpo, notificacionDeNuDelAtajo(verificada.cuerpo))
        // Una compra que dice que no pasó no se vuelve «Compra aprobada».
        val rechazada = "Tu compra en RAPPI por \$45.000,00 con tu tarjeta terminada en 1336 fue rechazada."
        assertEquals(rechazada, notificacionDeNuDelAtajo(rechazada))
    }

    @Test
    fun `el cuerpo del deposito, sin titulo, no dice nada de Nu`() {
        // «¡No te preocupes! En unas horas…» no trae monto ni una forma de Nu: sin el título no se reconoce.
        assertNull(notificacionDeNuDelAtajo(deposito.cuerpo))
    }

    @Test
    fun `lo que manda Nu con su nombre de titulo va como el Android`() {
        val envio = "Acabas de enviar \$1.000,00 Se los enviaste a Carolina R."
        assertEquals("Nu: $envio", notificacionDeNuDelAtajo("Nu: $envio"))
        assertEquals("Nu: $envio", notificacionDeNuDelAtajo("Nu\n$envio"))
        // Sin el nombre de la app, esa forma sola no prueba que sea de Nu.
        assertNull(notificacionDeNuDelAtajo(envio))
    }

    @Test
    fun `lo que no es de Nu da null`() {
        listOf(
            "",
            "   ",
            "Nu",
            "Hola, ya te pagué los \$20.000 del almuerzo",
            "Nu: ¿vienes a comer?",
            "Nubank te espera: abre tu cuenta",
            "Nuevo: Compra aprobada en tu corazón",
            // Un SMS de otro banco que escribe parecido es de ese banco.
            "Bancolombia: Recibimos tu pago por \$1.000.000,00 a tu tarjeta *9208.",
            "Bancolombia: Compraste \$27.500,00 en RAPPI COLOMBIA con tu T.Cred *3684.",
            "DAVIVIENDA Descuento en internet, \$64,900,Cta de Ahorros *0136,Hora 16:49,Lugar PSE MERCADOPAGO COLOMBIA.",
            // Una promoción de Nu sin ninguna forma de un aviso.
            "Algo nuevo de Nu para 2026: mira lo que preparamos para ti.",
        ).forEach { assertNull(notificacionDeNuDelAtajo(it), "«$it» no es una notificación de Nu") }
    }

    @Test
    fun `el texto que se guarda tiene el tope del Android`() {
        val largo = "Compra aprobada por \$1.000,00: Tu compra en " + "X".repeat(900) + " con tu tarjeta terminada en 1336"
        assertEquals(MAX_TEXTO_DE_UNA_NOTIFICACION, notificacionDeNuDelAtajo(largo)?.length)
    }

    // ── El rótulo ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `el rotulo del atajo cuenta como la notificacion de Nu`() {
        assertEquals("Atajo · Notificación · Nu", ORIGEN_DE_NU_POR_ATAJO)
        assertTrue(esUnAtajo(ORIGEN_DE_NU_POR_ATAJO), "llegó por el iPhone")
        assertTrue(esUnaNotificacionPorAtajo(ORIGEN_DE_NU_POR_ATAJO))
        assertFalse(esUnaNotificacionPorAtajo("Atajo · Bancolombia"))
        assertFalse(esUnaNotificacionPorAtajo(ORIGEN_DE_APPLE_PAY))
        assertFalse(esUnaNotificacionPorAtajo("Notificación · Nu"), "la del Android no llegó por un atajo")
        assertEquals("Notificación · Nu", origenSinElAtajo(ORIGEN_DE_NU_POR_ATAJO))
        assertEquals("Atajo · Bancolombia", origenSinElAtajo("Atajo · Bancolombia"))

        assertEquals(CanalDeCaptura.NOTIFICACION, canalDeCaptura(ORIGEN_DE_NU_POR_ATAJO))
        assertEquals("Nu", nombreDelOrigenDeCaptura(ORIGEN_DE_NU_POR_ATAJO))
        assertEquals("nu", marcaDelRemitente(ORIGEN_DE_NU_POR_ATAJO, compra.delAndroid)?.id)
        assertTrue(esElPagoDeLaTarjetaNu(ORIGEN_DE_NU_POR_ATAJO, pagoDeLaTarjeta.delAndroid))
        // Lo de antes, igual.
        assertEquals(CanalDeCaptura.SMS, canalDeCaptura("Atajo · Bancolombia"))
        assertEquals("Bancolombia", nombreDelOrigenDeCaptura("Atajo · Bancolombia"))
        assertEquals(CanalDeCaptura.NOTIFICACION, canalDeCaptura(ORIGEN_DE_APPLE_PAY))
    }

    @Test
    fun `el banco mudo junta la notificacion de Nu del iPhone con la del Android`() {
        val dia = 86_400_000L
        val ahora = 40 * dia
        // Cinco notificaciones de Nu en tres días: dos del Android y tres del iPhone. Un origen solo.
        val capturas = listOf(
            Captura("Notificación · Nu", ahora - 20 * dia),
            Captura(ORIGEN_DE_NU_POR_ATAJO, ahora - 19 * dia),
            Captura("Notificación · Nu", ahora - 18 * dia),
            Captura(ORIGEN_DE_NU_POR_ATAJO, ahora - 18 * dia + 1_000),
            Captura(ORIGEN_DE_NU_POR_ATAJO, ahora - 17 * dia),
        )
        val mudos = origenesMudos(capturas, ahora, diasDeSilencio = 3)
        assertEquals(1, mudos.size, "$mudos")
        assertEquals(CanalDeCaptura.NOTIFICACION, mudos.single().canal)
        assertEquals("Nu", mudos.single().nombre)
    }
}
