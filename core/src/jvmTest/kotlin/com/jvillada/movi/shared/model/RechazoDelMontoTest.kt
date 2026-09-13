package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **Un monto es plata, o no se guarda.**
 *
 * La regla estaba en cuatro lugares y faltaba en la puerta de entrada: se podía *crear* en $0 un
 * movimiento que no se podía *corregir* a $0.
 */
class RechazoDelMontoTest {

    @Test
    fun `un monto normal pasa`() {
        assertNull(rechazoDelMonto(18_500L))
        assertNull(rechazoDelMonto(1L))
        assertNull(rechazoDelMonto(MONTO_MAXIMO))
    }

    @Test
    fun `cero no es plata`() {
        assertEquals(MONTO_INVALIDO, rechazoDelMonto(0L))
    }

    /**
     * **El que hace daño sin que se vea.** La dirección la dice `type`: un gasto con monto negativo
     * sumaría al flujo y al saldo en vez de restar.
     */
    @Test
    fun `negativo no es plata`() {
        assertEquals(MONTO_INVALIDO, rechazoDelMonto(-18_500L))
    }

    @Test
    fun `un billon y uno es un dedo, no un gasto`() {
        assertEquals(MONTO_DEMASIADO_GRANDE, rechazoDelMonto(MONTO_MAXIMO + 1))
    }
}
