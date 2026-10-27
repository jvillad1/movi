package com.jvillada.movi.ui.destinos

import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.TipoDeIdentificador
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El renglón de una persona o comercio no empieza con un separador (vuelta de diseño 8)
 *
 * La fila de Caro decía «·0756»: la cola del número se escribía con un punto adelante, y al quedar
 * sola —o detrás de « · », «Persona · ·0756»— se leía como un separador colgando. Ahora el número va
 * nombrado («cuenta ·0756») y las partes se unen sin dejar ninguna vacía.
 */
class ElRenglonDelDestinoTest {

    private fun numero(valor: String) = IdentificadorDelDestino(TipoDeIdentificador.NUMERO, valor)
    private fun llave(valor: String) = IdentificadorDelDestino(TipoDeIdentificador.LLAVE, valor)

    @Test
    fun `solo con el numero el renglon nombra la cuenta`() {
        val caro = DestinoConocido(id = "d1", nombre = "Caro", numero = "55500000756")
        assertEquals("Cuenta ·0756", subtituloDelDestino(caro))
    }

    @Test
    fun `con numero y nota no queda ningun separador colgando`() {
        val caro = DestinoConocido(id = "d1", nombre = "Caro", numero = "55500000756", deQuien = "esposa")
        assertEquals("Cuenta ·0756 · esposa", subtituloDelDestino(caro))
    }

    @Test
    fun `sin identificadores ni nota el renglon queda vacio`() {
        assertEquals("", subtituloDelDestino(DestinoConocido(id = "d1", nombre = "Tienda", numero = "")))
    }

    @Test
    fun `solo la nota va sola y en frase`() {
        assertEquals("Esposa", subtituloDelDestino(DestinoConocido(id = "d1", nombre = "Caro", numero = "", deQuien = "esposa")))
    }

    @Test
    fun `la ficha dice el tipo y despues la cuenta, sin dos puntos seguidos`() {
        val caro = DestinoConocido(id = "d1", nombre = "Caro", numero = "55500000756", deQuien = "esposa")
        val linea = queEsYComoLoReconoce(caro)
        assertEquals("Persona · cuenta ·0756 · esposa", linea)
        assertFalse("· ·" in linea)
    }

    @Test
    fun `un identificador se nombra en la linea`() {
        assertEquals("cuenta ·0756", identificadorEnUnaLinea(numero("55500000756")))
        assertEquals("llave @caro", identificadorEnUnaLinea(llave("@caro")))
        assertNull(identificadorEnUnaLinea(numero("sin digitos")))
    }

    @Test
    fun `las partes vacias o nulas no dejan separadores`() {
        assertEquals("Uno · dos", enUnaLinea(listOf(null, " ", "uno", "", null, "dos", null)))
        assertEquals("", enUnaLinea(listOf(null, "")))
        val linea = enUnaLinea(listOf("", "a", null))
        assertFalse(linea.startsWith("·") || linea.endsWith("·"))
        assertTrue(linea == "A")
    }
}
