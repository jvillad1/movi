package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vuelta 129 · lo que el nombre de un archivo dice: sus palabras, sus números y su producto. */
class LoQueSeSabeDelPapelTest {

    @Test
    fun `las palabras del nombre se parten tambien donde cambia a mayuscula`() {
        assertEquals("Cuenta Nu CRS119 2026 09", palabrasDelNombreDelArchivo("CuentaNu_CRS119_2026-09.pdf"))
        assertEquals("Nu 2026 09 17", palabrasDelNombreDelArchivo("Nu_2026-09-17.pdf"))
    }

    @Test
    fun `los numeros del nombre son los cuatro digitos que no son un anio`() {
        assertEquals(listOf("0756"), numerosDeCuentaDelExtracto("Extracto_0756_09_2026.pdf"))
        assertEquals(emptyList(), numerosDeCuentaDelExtracto("Nu_2026-09-17.pdf"))
        assertEquals(emptyList(), numerosDeCuentaDelExtracto("CuentaNu_CRS119_2026-09.pdf"))
    }

    @Test
    fun `el producto que nombra el archivo`() {
        assertEquals(ProductoDelPapel.CUENTA, productoQueNombraElArchivo("CuentaNu_CRS119_2026-09.pdf"))
        assertEquals(ProductoDelPapel.TARJETA, productoQueNombraElArchivo("TC_Master_3684_09_2026.pdf"))
        // «Tarjeta de crédito» es una tarjeta, no un crédito.
        assertEquals(ProductoDelPapel.TARJETA, productoQueNombraElArchivo("Extracto tarjeta de crédito Nu.pdf"))
        assertEquals(ProductoDelPapel.CREDITO, productoQueNombraElArchivo("Extracto_credito_vehiculo.pdf"))
        // «Nu» solo no dice si es la tarjeta o la cuenta.
        assertNull(productoQueNombraElArchivo("Nu_2026-09-17.pdf"))
        // Dos que se contradicen: no se sabe.
        assertNull(productoQueNombraElArchivo("Tarjeta_y_cuenta.pdf"))
    }
}
