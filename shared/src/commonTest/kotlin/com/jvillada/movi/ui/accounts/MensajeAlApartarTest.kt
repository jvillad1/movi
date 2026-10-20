package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.repository.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals

/** El error de la hoja de apartar se decide por el tipo: contestó el server, o no llegó. */
class MensajeAlApartarTest {

    @Test
    fun `si el server contesto se dice su motivo`() {
        assertEquals("El monto debe ser mayor que cero", mensajeAlApartar(ApiException(400, "El monto debe ser mayor que cero")))
    }

    @Test
    fun `si el pedido no llego se dice que hace falta conexion`() {
        assertEquals(APARTAR_SIN_CONEXION, mensajeAlApartar(RuntimeException("Unable to resolve host api.movi")))
        assertEquals(APARTAR_SIN_CONEXION, mensajeAlApartar(IllegalStateException("Failed to fetch")))
    }
}
