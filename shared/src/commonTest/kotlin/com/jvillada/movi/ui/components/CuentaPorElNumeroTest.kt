package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # El mensaje dice de qué cuenta salió la plata, y ahora Movi lo lee
 *
 * El dueño abrió la pantalla de reconciliar con este SMS:
 *
 * > *«Bancolombia: Retiraste \$3,500,000.00 de tu cuenta \*9586 Fiducuenta…»*
 *
 * Tiene una cuenta llamada **«Fiducuenta 9586»**, y Movi le proponía **«Bancolombia Ahorros»** con
 * el rótulo «La puso Movi». El dato estaba escrito en el mensaje y nadie lo leía: el nombre del
 * banco ganaba, y el primer producto que dice «Bancolombia» no es necesariamente el que el banco
 * nombró.
 *
 * Las cuentas de esta clase son **las suyas**, con los nombres tal como él las escribió — que es lo
 * que hace posible el emparejamiento, porque Movi no guarda el número por separado.
 */
class CuentaPorElNumeroTest {

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 824_538)
    private val fiducuenta = Account("a2", "Fiducuenta 9586", AccountType.SAVINGS, 10_045_809)
    private val nu = Account("a3", "Nu", AccountType.SAVINGS, 12_769_308)
    private val master = Account("c1", "Master Black 3684", AccountType.CREDIT_CARD, 27_501_150)
    private val masterUsd = Account("c2", "Master Black 3684 USD", AccountType.CREDIT_CARD, 0, currency = "USD")
    private val amex = Account("c3", "AMEX 9208", AccountType.CREDIT_CARD, 0)

    private val todas = listOf(ahorros, amex, fiducuenta, master, masterUsd, nu)

    private fun resolver(texto: String, banco: String = "Bancolombia") = resolverCuentaDelBanco(
        accounts = todas,
        uso = UsoDeCuenta.ORIGEN_DE_GASTO,
        banco = banco,
        textoDelMensaje = texto,
    )

    /** El SMS exacto que tenía en pantalla cuando lo reportó. */
    private val retiroDeLaFiducuenta =
        "Bancolombia: Retiraste \$3,500,000.00 de tu cuenta *9586 Fiducuenta el " +
            "2026/09/10 13:35:32, hacia la cuenta *25318624146. ¿Dudas? 6045109009"

    @Test
    fun `el numero del mensaje le gana al nombre del banco`() {
        val resuelta = resolver(retiroDeLaFiducuenta)

        assertEquals(fiducuenta, resuelta.cuenta, "el mensaje nombra *9586 y él tiene la Fiducuenta 9586")
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, resuelta.origen)
    }

    /**
     * **El acierto se dice igual.** El nombre de la cuenta no alcanza para saber que la eligió el
     * número y no una corazonada; decirlo es lo que le permite mirar el SMS de arriba y verificar
     * el mismo número.
     */
    @Test
    fun `la pantalla cuenta de donde salio`() {
        assertEquals(
            "Por el número que dice el mensaje",
            avisoDeLaCuentaDelBanco(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO),
        )
    }

    /**
     * **La cuenta de destino no se confunde con la de origen.** Un traspaso nombra dos números; el
     * primero es de dónde sale la plata, que es la cuenta que el movimiento afecta. Y el segundo,
     * acá, ni siquiera es suyo.
     */
    @Test
    fun `el numero ajeno del final no engancha nada`() {
        assertEquals(fiducuenta, resolver(retiroDeLaFiducuenta).cuenta)
    }

    /**
     * **Ante un empate no se elige.** Las dos caras de la Master Black llevan el mismo número en el
     * nombre. Adivinar una sería el mismo accidente que `resolverCuentaDelBanco` vino a cerrar: no
     * saber y equivocarse no pueden verse igual.
     */
    @Test
    fun `dos cuentas con el mismo numero no resuelven por numero`() {
        assertNull(cuentaPorElNumero("Compra en tu tarjeta *3684 por \$50.000", todas))
    }

    @Test
    fun `un numero que no es de ninguna cuenta suya no engancha`() {
        assertNull(cuentaPorElNumero("Transferencia a la cuenta *99887766", todas))
    }

    /**
     * Solo cuenta lo que el banco escribe con `*`. Sin esa marca, una fecha, un monto o un
     * teléfono engancharían cualquier cosa — el SMS del caso trae `6045109009` al final.
     */
    @Test
    fun `los numeros sueltos del texto no cuentan`() {
        assertNull(cuentaPorElNumero("Dudas al 6045109009 o el 9586 de la sucursal", todas))
    }

    @Test
    fun `sin numero en el mensaje manda el nombre del banco, como antes`() {
        val resuelta = resolver("Bancolombia: Compra por \$80.894 en EXITO", banco = "Bancolombia")

        assertEquals(ahorros, resuelta.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, resuelta.origen)
    }

    /** Y quien no pase el texto se comporta exactamente como antes de este cambio. */
    @Test
    fun `sin texto no cambia nada`() {
        val resuelta = resolverCuentaDelBanco(
            accounts = todas,
            uso = UsoDeCuenta.ORIGEN_DE_GASTO,
            banco = "Bancolombia",
        )
        assertEquals(ahorros, resuelta.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, resuelta.origen)
    }
}
