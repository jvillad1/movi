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

    // ── enFormaDeTitulo (vuelta de diseño 32) ───────────────────────────────────────────────

    @Test
    fun `lo que llega en mayusculas pasa a forma de titulo, con las tildes de la marca`() {
        assertEquals("Éxito Suba", enFormaDeTitulo("EXITO SUBA"))
        assertEquals("Olímpica Calle 80", enFormaDeTitulo("OLIMPICA CALLE 80"))
        assertEquals("Compra en McDonald's Andino", enFormaDeTitulo("COMPRA EN MCDONALDS ANDINO"))
        assertEquals("Drogueria de La 80", enFormaDeTitulo("DROGUERIA DE LA 80"))
    }

    @Test
    fun `no quita nada de la descripcion, a diferencia de nombreParaMostrar`() {
        assertEquals("Payu Netflix", enFormaDeTitulo("PAYU*NETFLIX"))
        assertEquals("Uber Trip", enFormaDeTitulo("UBER *TRIP"))
        assertEquals("Tienda D1 Calle 80", enFormaDeTitulo("TIENDA D1 CALLE 80"))
    }

    @Test
    fun `las siglas cortas y los numeros se quedan como estan`() {
        assertEquals("D1", enFormaDeTitulo("D1"))
        assertEquals("EPM", enFormaDeTitulo("EPM"))
        assertEquals("DIAN", enFormaDeTitulo("DIAN"))
        assertEquals("Pago PSE EPM", enFormaDeTitulo("PAGO PSE EPM"))
        assertEquals("Impuesto DIAN 2026", enFormaDeTitulo("IMPUESTO DIAN 2026"))
        assertEquals("CRV Motors", enFormaDeTitulo("CRV MOTORS"))
        assertEquals("BBVA", enFormaDeTitulo("BBVA"))
        assertEquals("123456", enFormaDeTitulo("123456"))
    }

    @Test
    fun `lo que trae minusculas no se toca`() {
        assertEquals("Almuerzo con el equipo", enFormaDeTitulo("Almuerzo con el equipo"))
        assertEquals("mercado del sábado", enFormaDeTitulo("mercado del sábado"))
        assertEquals("Pago a EXITO", enFormaDeTitulo("Pago a EXITO"))
        assertEquals("iPhone", enFormaDeTitulo("iPhone"))
        assertEquals("", enFormaDeTitulo(""))
    }
}
