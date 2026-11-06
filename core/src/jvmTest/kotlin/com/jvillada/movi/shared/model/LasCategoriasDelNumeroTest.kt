package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LasCategoriasDelNumeroTest {

    private val llave = "Pago QR · llave 0099887766"
    private val cuenta = "Transferencia a la cuenta *41279033068"
    private val sms = "Bancolombia: Transferiste \$50,000 desde tu cuenta *8133 a la cuenta *41279033068 el 03/10/2026 a las 17:59. ¿Dudas? Llamanos al 018000000000."

    private fun a(
        comoLlego: String,
        categoria: String,
        cuando: Long = 0,
        tipo: TransactionType = TransactionType.EXPENSE,
        delBanco: String? = null,
    ) = AnotacionPasada(comoLlego, comoLlego, categoria, cuando, tipo, delBanco)

    private fun veces(n: Int, texto: String, cat: String, tipo: TransactionType = TransactionType.EXPENSE) =
        List(n) { a(texto, cat, it.toLong(), tipo) }

    private fun pares(l: List<CategoriaDelNumero>) = l.map { it.categoria to it.veces }

    @Test
    fun `dos categorias a la misma llave vuelven en orden de veces`() {
        val m = MemoriaDeCategorias.de(veces(3, llave, "Parqueadero") + veces(2, llave, "Gasolina"))
        assertEquals(listOf("Parqueadero" to 3, "Gasolina" to 2), pares(m.categoriasDelNumero(llave, TransactionType.EXPENSE)))
    }

    @Test
    fun `con una sola categoria no vuelve nada`() {
        val m = MemoriaDeCategorias.de(veces(4, llave, "Parqueadero"))
        assertEquals(emptyList(), m.categoriasDelNumero(llave, TransactionType.EXPENSE))
    }

    @Test
    fun `a igual numero de veces gana la mas reciente`() {
        val m = MemoriaDeCategorias.de(
            listOf(a(llave, "Gasolina", 10), a(llave, "Gasolina", 20), a(llave, "Parqueadero", 5), a(llave, "Parqueadero", 30)),
        )
        assertEquals(listOf("Parqueadero" to 2, "Gasolina" to 2), pares(m.categoriasDelNumero(llave, TransactionType.EXPENSE)))
    }

    @Test
    fun `las categorias que la memoria no aprende no cuentan`() {
        val m = MemoriaDeCategorias.de(
            veces(2, llave, "Parqueadero") + veces(1, llave, "Otros") + veces(1, llave, CARD_PAYMENT_CATEGORY),
        )
        assertEquals(emptyList(), m.categoriasDelNumero(llave, TransactionType.EXPENSE))
    }

    @Test
    fun `lo que entra va aparte`() {
        val m = MemoriaDeCategorias.de(
            veces(2, cuenta, "Parqueadero") + veces(1, cuenta, "Gasolina") +
                veces(2, cuenta, "Salario", TransactionType.INCOME) + veces(1, cuenta, "Reembolso", TransactionType.INCOME),
        )
        assertEquals(listOf("Parqueadero" to 2, "Gasolina" to 1), pares(m.categoriasDelNumero(cuenta, TransactionType.EXPENSE)))
        assertEquals(listOf("Salario" to 2, "Reembolso" to 1), pares(m.categoriasDelNumero(cuenta, TransactionType.INCOME)))
    }

    @Test
    fun `el numero del texto del banco cuenta aunque el comercio ya tenga nombre`() {
        val m = MemoriaDeCategorias.de(
            listOf(a("Caro", "Mercado", delBanco = sms)) + veces(2, cuenta, "Arriendo"),
        )
        assertEquals(listOf("Arriendo" to 2, "Mercado" to 1), pares(m.categoriasDelNumero(cuenta, TransactionType.EXPENSE)))
    }

    @Test
    fun `la cuenta propia del texto del banco no cuenta`() {
        val m = MemoriaDeCategorias.de(
            listOf(a("Caro", "Mercado", delBanco = sms)) + veces(2, "Transferencia a la cuenta *8133", "Arriendo"),
        )
        // Solo hay una categoría para *8133 (Arriendo): el SMS habla de la cuenta de destino, no de la propia.
        assertEquals(emptyList(), m.categoriasDelNumero("Transferencia a la cuenta *8133", TransactionType.EXPENSE))
    }

    @Test
    fun `un movimiento nombrado por los dos cuenta una vez`() {
        val m = MemoriaDeCategorias.de(
            listOf(a(cuenta, "Mercado", delBanco = sms)) + veces(1, cuenta, "Arriendo"),
        )
        assertEquals(listOf("Mercado" to 1, "Arriendo" to 1).sortedBy { it.first }, pares(m.categoriasDelNumero(cuenta, TransactionType.EXPENSE)).sortedBy { it.first })
    }

    @Test
    fun `el cajero no es un numero para esto`() {
        val t = "Retiro en cajero SUCVIVAPAL1"
        val m = MemoriaDeCategorias.de(veces(2, t, "Efectivo") + veces(2, t, "Otra"))
        assertEquals(emptyList(), m.categoriasDelNumero(t, TransactionType.EXPENSE))
    }

    @Test
    fun `el identificador sirve cuando el texto no trae el numero`() {
        val m = MemoriaDeCategorias.de(veces(2, llave, "Parqueadero") + veces(1, llave, "Gasolina"))
        val id = IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "0099887766")
        assertEquals(2, m.categoriasDelNumero("Pago QR", TransactionType.EXPENSE, id).size)
    }

    @Test
    fun `huellaDelIdentificador coincide con la huella del comercio`() {
        val texto = "Bancolombia: pagaste \$9,000.00 por codigo QR desde tu cuenta *8133 a la llave 0099887766 el 05/11/2026 a las 10:00."
        assertEquals(huellaDeUnMovimiento("Pago QR · llave 0099887766"), huellaDelIdentificador(identificadorDelDestinoEn(texto)!!))
    }

    @Test
    fun `nombreSinLaNota`() {
        assertEquals(llave, nombreSinLaNota(llave, "$llave · parqueadero del centro"))
        assertEquals("Coomeva", nombreSinLaNota("Coomeva", "Coomeva"))
        assertEquals("Panadería", nombreSinLaNota(null, "Panadería"))
        assertEquals("Mercado de Rappi", nombreSinLaNota("Rappi", "Mercado de Rappi"))
    }

    @Test
    fun `entraEnElLote`() {
        val dos = listOf(CategoriaDelNumero("A", 2), CategoriaDelNumero("B", 1))
        val base = ParsedSms(1.0, "x", TransactionType.EXPENSE, "A")
        assertFalse(entraEnElLote(base.copy(categoriasDelNumero = dos)))
        assertTrue(entraEnElLote(base.copy(categoriasDelNumero = dos, porElMismoMonto = true)))
        assertTrue(entraEnElLote(base))
        assertFalse(entraEnElLote(base.copy(type = TransactionType.INCOME)))
    }
}
