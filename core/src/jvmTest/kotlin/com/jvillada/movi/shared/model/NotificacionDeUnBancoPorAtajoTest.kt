package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 137 · el texto y el rótulo de una notificación de cualquier banco que mandó el atajo de notificaciones.
 * Los textos de bancos distintos de Nu son **inventados** (formas plausibles).
 */
class NotificacionDeUnBancoPorAtajoTest {

    @Test
    fun `dos renglones son titulo y cuerpo, como los sube un Android, y uno solo queda igual`() {
        assertEquals(
            "Davivienda: Compra por \$85.400 en EXITO.",
            textoDeLaNotificacionDelAtajo("Davivienda\r\nCompra por \$85.400 en EXITO."),
        )
        assertEquals(
            textoDeUnaNotificacion("Nequi", "¡Te llegó plata! JUAN te envió \$50.000."),
            textoDeLaNotificacionDelAtajo("  Nequi \n\n¡Te llegó plata! JUAN te envió \$50.000.  "),
        )
        assertEquals("Recibiste 999.999,99 en tu cuenta: Te llegó dinero.", textoDeLaNotificacionDelAtajo("Recibiste 999.999,99 en tu cuenta: Te llegó dinero."))
        assertNull(textoDeLaNotificacionDelAtajo("  \n "))
        assertTrue(textoDeLaNotificacionDelAtajo("x".repeat(900))!!.length <= MAX_TEXTO_DE_UNA_NOTIFICACION)
    }

    @Test
    fun `el banco sale del titulo o de la primera palabra, solo si es un banco o una billetera`() {
        assertEquals("Atajo · Notificación · Davivienda", origenDeLaNotificacionPorAtajo("Davivienda: Compra por \$85.400 en EXITO."))
        assertEquals("Atajo · Notificación · Nequi", origenDeLaNotificacionPorAtajo("Nequi: ¡Te llegó plata! \$50.000"))
        assertEquals("Atajo · Notificación · Bancolombia", origenDeLaNotificacionPorAtajo("Bancolombia: Pagaste \$45.900 en RAPPI"))
        // Un comercio delante no es un banco, y el banco del cuerpo tampoco cuenta.
        assertEquals("Atajo · Notificación · tu banco", origenDeLaNotificacionPorAtajo("Rappi: Tu pedido de \$45.900 va en camino"))
        assertEquals(
            "Atajo · Notificación · tu banco",
            origenDeLaNotificacionPorAtajo("Pago aprobado por \$99.999,99: Pagaste en Banco Davivienda SA con tu cuenta de ahorros."),
        )
        assertEquals("$ORIGEN_DE_UNA_NOTIFICACION_POR_ATAJO · $BANCO_SIN_NOMBRE_DEL_ATAJO", origenDeLaNotificacionPorAtajo("Compraste \$32.000"))
        assertEquals("Atajo · Notificación · Nu", ORIGEN_DE_NU_POR_ATAJO)
    }

    @Test
    fun `el rotulo se lee como la notificacion de un Android en el canal, el nombre y el banco mudo`() {
        val rotulo = origenDeLaNotificacionPorAtajo("Davivienda: Compra por \$85.400 en EXITO.")
        assertTrue(esUnAtajo(rotulo))
        assertTrue(esUnaNotificacionPorAtajo(rotulo))
        assertEquals("Notificación · Davivienda", origenSinElAtajo(rotulo))
        assertEquals(CanalDeCaptura.NOTIFICACION, canalDeCaptura(rotulo))
        assertEquals("Davivienda", nombreDelOrigenDeCaptura(rotulo))
        assertEquals("davivienda", marcaDelRemitente(rotulo, "Compra por \$85.400")?.id)
        val sinBanco = origenDeLaNotificacionPorAtajo("Compraste \$32.000")
        assertEquals(CanalDeCaptura.NOTIFICACION, canalDeCaptura(sinBanco))
        assertEquals("tu banco", nombreDelOrigenDeCaptura(sinBanco))
    }
}
