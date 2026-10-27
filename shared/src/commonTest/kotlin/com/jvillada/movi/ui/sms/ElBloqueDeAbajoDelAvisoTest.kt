package com.jvillada.movi.ui.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # La tarjeta de un aviso no deja huecos (vuelta de diseño 7)
 *
 * Debajo del texto del banco va «Se anota: …» o el detalle del server; si el detalle está en blanco
 * (el teléfono, el correo y los comprobantes lo suben vacío), no va nada.
 */
class ElBloqueDeAbajoDelAvisoTest {

    @Test
    fun `con lo entendido va Se anota`() {
        assertEquals(BloqueDeAbajo.LoEntendido("Comida · Nu · \$27.500"), bloqueDeAbajo(true, "Comida · Nu · \$27.500", ""))
    }

    @Test
    fun `sin lo entendido va el detalle solo si dice algo`() {
        assertEquals(BloqueDeAbajo.Detalle("Compra en Rappi"), bloqueDeAbajo(false, null, "Compra en Rappi"))
        assertNull(bloqueDeAbajo(false, null, ""))
        assertNull(bloqueDeAbajo(false, null, "   "))
        // Entendido pero no confirmable (ya no está pendiente): tampoco hay hueco.
        assertNull(bloqueDeAbajo(false, "Comida · Nu · \$27.500", ""))
    }
}
