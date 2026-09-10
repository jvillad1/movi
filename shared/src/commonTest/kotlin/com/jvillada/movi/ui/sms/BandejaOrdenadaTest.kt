package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **El mensaje más nuevo va primero.**
 *
 * El dueño, con 96 mensajes en la bandeja: *«al importar SMS del teléfono el último mensaje
 * recibido queda de último en la lista, debe ser el primero»*. En su pantalla el orden no era ni
 * siquiera ascendente: dos del 11 de agosto arriba y uno del 10 de septiembre abajo.
 *
 * La causa estaba en el server —`GET /api/sms` no tenía `ORDER BY`, así que Postgres devolvía las
 * filas en el orden que le conviniera— y se arregló allá. Esto prueba la mitad del cliente, que no
 * sobra: el orden de una lista que se lee de arriba abajo no debería depender de que un endpoint
 * se acuerde.
 */
class BandejaOrdenadaTest {

    private fun sms(id: String, time: String) = SmsMessage(
        id = id,
        time = time,
        bank = "Bancolombia",
        text = "Bancolombia: transferiste $25.910",
        state = SMS_STATE_PENDING,
        det = "",
    )

    @Test
    fun `el mas nuevo queda primero, aunque llegue ultimo`() {
        // Exactamente el desorden de la pantalla del dueño.
        val comoVinieron = listOf(
            sms("a", "2026-08-11 13:38"),
            sms("b", "2026-08-11 12:24"),
            sms("c", "2026-09-10 08:50"),
        )

        assertEquals(
            listOf("c", "a", "b"),
            mensajesMasRecientesPrimero(comoVinieron).map { it.id },
        )
    }

    @Test
    fun `dos del mismo minuto conservan el orden en que llegaron`() {
        // `sortedByDescending` es estable: sin eso, dos mensajes del mismo minuto bailarían entre
        // lecturas y la lista se vería distinta cada vez que se toca «Actualizar».
        val mismoMinuto = listOf(
            sms("primero", "2026-09-10 08:50"),
            sms("segundo", "2026-09-10 08:50"),
            sms("viejo", "2026-08-11 12:24"),
        )

        assertEquals(
            listOf("primero", "segundo", "viejo"),
            mensajesMasRecientesPrimero(mismoMinuto).map { it.id },
        )
    }

    @Test
    fun `una bandeja vacia sigue vacia`() {
        assertEquals(emptyList(), mensajesMasRecientesPrimero(emptyList()))
    }
}
