package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `nombreParaMostrar` con los comercios como los escribe el banco (vuelta de diseño 4): la tarjeta de
 * un aviso decía «RAPPI» y «EXITO SUBA», gritando.
 */
class NombreParaMostrarTest {

    @Test
    fun `una marca conocida se dice con su nombre`() {
        assertEquals("Rappi", nombreParaMostrar("RAPPI"))
        assertEquals("D1", nombreParaMostrar("D1"))
    }

    @Test
    fun `lo que sigue a la marca se conserva si aporta`() {
        assertEquals("Éxito Suba", nombreParaMostrar("EXITO SUBA"))
        assertEquals("D1 Calle 80", nombreParaMostrar("TIENDA D1 CALLE 80"))
        assertEquals("Rappi Las Doce", nombreParaMostrar("RAPPI*LAS DOCE"))
    }

    @Test
    fun `la pasarela de antes y lo que no dice nada de despues se caen`() {
        assertEquals("Uber", nombreParaMostrar("UBER *TRIP"))
        assertEquals("Netflix", nombreParaMostrar("PAYU*NETFLIX"))
        assertEquals("Netflix", nombreParaMostrar("NETFLIX.COM"))
        assertEquals("Spotify", nombreParaMostrar("SPOTIFY P1A2B3C4"))
    }

    @Test
    fun `sin marca va en tipo titulo con siglas y numeros`() {
        assertEquals("Panaderia El Trigal", nombreParaMostrar("PANADERIA EL TRIGAL"))
        assertEquals("CRV Motors", nombreParaMostrar("CRV MOTORS"))
        assertEquals("Drogueria de La 80", nombreParaMostrar("DROGUERIA DE LA 80"))
        assertEquals("Parqueadero Calle 93 S.A.", nombreParaMostrar("PARQUEADERO CALLE 93 S.A."))
        assertEquals("Cafe Pergamino", nombreParaMostrar("cafe pergamino"))
    }

    @Test
    fun `lo que ya viene escrito por alguien no se toca`() {
        assertEquals("Transferencia a Caro", nombreParaMostrar("Transferencia a Caro"))
        assertEquals("Rappi · Las Doce", nombreParaMostrar("  Rappi · Las Doce "))
        assertEquals("", nombreParaMostrar("   "))
    }
}
