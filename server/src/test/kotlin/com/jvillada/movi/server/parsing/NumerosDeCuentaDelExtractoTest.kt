package com.jvillada.movi.server.parsing

import kotlin.test.Test
import kotlin.test.assertEquals

/** El número que nombra un extracto, para elegir la cuenta de destino por él y no por el banco. */
class NumerosDeCuentaDelExtractoTest {

    @Test
    fun `el nombre del archivo con la convencion del dueno trae el numero, sin el ano`() {
        assertEquals(listOf("3684"), StatementParser.numerosDeCuenta("TC_Master_3684_09_2026.pdf"))
        assertEquals(listOf("9586"), StatementParser.numerosDeCuenta("Fondo_9586_09_2026.pdf"))
        assertEquals(emptyList(), StatementParser.numerosDeCuenta("Salario_09_2026.pdf"))
    }

    @Test
    fun `del texto solo cuentan los numeros enmascarados`() {
        val texto = "Tarjeta **** **** **** 3684\nPago mínimo 1.843\nLínea 6045109009\nCuenta terminada en 8133"
        assertEquals(listOf("3684", "8133"), StatementParser.numerosDeCuenta("extracto.pdf", texto))
    }

    @Test
    fun `el archivo va primero y no se repite`() {
        assertEquals(listOf("3684", "9208"), StatementParser.numerosDeCuenta("TC_3684.pdf", "XXXX3684 y XXXX9208"))
    }
}
