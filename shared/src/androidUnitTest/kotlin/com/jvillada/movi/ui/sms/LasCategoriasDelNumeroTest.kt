package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.CategoriaDelNumero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LasCategoriasDelNumeroTest {
    private fun c(n: String, v: Int) = CategoriaDelNumero(n, v)

    @Test
    fun `dos categorias en una llave`() =
        assertEquals("A esta llave le pusiste Parqueadero 3 veces y Gasolina 2", lineaDelNumero(listOf(c("Parqueadero", 3), c("Gasolina", 2)), true))

    @Test
    fun `una cuenta dice cuenta`() =
        assertEquals("A esta cuenta le pusiste Parqueadero 3 veces y Gasolina 2", lineaDelNumero(listOf(c("Parqueadero", 3), c("Gasolina", 2)), false))

    @Test
    fun `una sola vez va en singular`() =
        assertEquals("A esta llave le pusiste Parqueadero 3 veces y Gasolina 1 vez", lineaDelNumero(listOf(c("Parqueadero", 3), c("Gasolina", 1)), true))

    @Test
    fun `tres categorias`() =
        assertEquals("A esta llave le pusiste A 3 veces, B 2 y C 1 vez", lineaDelNumero(listOf(c("A", 3), c("B", 2), c("C", 1)), true))

    @Test
    fun `mas de tres dicen cuantas faltan`() =
        assertEquals(
            "A esta llave le pusiste A 3 veces, B 2, C 2 y 2 más",
            lineaDelNumero(listOf(c("A", 3), c("B", 2), c("C", 2), c("D", 1), c("E", 1)), true),
        )

    @Test
    fun `con una sola no hay linea`() {
        assertNull(lineaDelNumero(listOf(c("A", 3)), true))
        assertNull(lineaDelNumero(emptyList(), true))
    }

    @Test
    fun `las del numero van primero y el resto sin repetir`() =
        assertEquals(
            listOf("Parqueadero", "Gasolina", "Comida", "Mercado"),
            primeroLasDelNumero(listOf(c("Parqueadero", 3), c("Gasolina", 2)), listOf("Comida", "Gasolina", "Mercado")),
        )

    @Test
    fun `las que ningun selector ofrece no entran ni a la linea ni a las pastillas`() {
        val todas = listOf(c("Avance de tarjeta", 4), c("Parqueadero", 3), c("Gasolina", 2))
        assertEquals("A esta llave le pusiste Parqueadero 3 veces y Gasolina 2", lineaDelNumero(todas, true))
        assertEquals(listOf("Parqueadero", "Gasolina", "Comida"), primeroLasDelNumero(todas, listOf("Comida")))
    }

    @Test
    fun `si al filtrar queda una sola no hay linea y no cambia nada`() {
        val todas = listOf(c("Descuento de nómina", 4), c("Parqueadero", 3))
        assertNull(lineaDelNumero(todas, true))
        assertEquals(listOf("Comida"), primeroLasDelNumero(todas, listOf("Comida")))
        assertEquals(emptyList(), lasDelNumero(todas))
    }
}
