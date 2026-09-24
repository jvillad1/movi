package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * # Una compra de Nu cae en «Nu Tarjeta», que no lleva el número en el nombre
 *
 * Nu avisa «… con tu tarjeta terminada en 1336 …» y la cuenta del dueño se llama «Nu Tarjeta», sin
 * dígitos: el emparejamiento por número no tiene con qué. Lo que sí dice de dónde vino es el rótulo
 * de origen, «Notificación · Nu». Las cuentas de acá son las suyas, con sus nombres.
 */
class LaTarjetaDeNuTest {

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 824_538)
    private val nuAhorros = Account("a3", "Nu", AccountType.SAVINGS, 12_769_308)
    private val nuTarjeta = Account("c4", "Nu Tarjeta", AccountType.CREDIT_CARD, 0)
    private val master = Account("c1", "Master Black 3684", AccountType.CREDIT_CARD, 27_501_150)
    private val amex = Account("c3", "AMEX 9208", AccountType.CREDIT_CARD, 0)

    private val todas = listOf(amex, ahorros, master, nuAhorros, nuTarjeta)

    /** Las dos notificaciones reales, ya juntadas por el teléfono. */
    private val crepes =
        "Compra aprobada por \$130.200,00: Tu compra en CREPES Y WAFFLES LEMON por \$130.200,00 " +
            "con tu tarjeta terminada en 1336 ha sido APROBADA."
    private val google =
        "Compra aprobada por \$39.920,00: Tu compra en GOOGLE *MINTROCKET por \$39.920,00 " +
            "con tu tarjeta terminada en 1336 ha sido APROBADA."

    private fun resolver(texto: String, banco: String = "Notificación · Nu", cuentas: List<Account> = todas) =
        resolverCuentaDelBanco(accounts = cuentas, uso = UsoDeCuenta.ORIGEN_DE_GASTO, banco = banco, textoDelMensaje = texto)

    @Test
    fun `las dos compras de Nu caen en Nu Tarjeta`() {
        listOf(crepes, google).forEach { texto ->
            val resuelta = resolver(texto)
            assertEquals(nuTarjeta, resuelta.cuenta, texto)
            assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, resuelta.origen)
        }
    }

    @Test
    fun `la cuenta de ahorros Nu no se lleva una compra con tarjeta`() {
        assertNotEquals(nuAhorros, resolver(crepes).cuenta)
    }

    /** Dos tarjetas que dicen Nu: no se adivina, baja a lo de siempre y la pantalla dice «La puso Movi». */
    @Test
    fun `con dos tarjetas Nu no se elige ninguna por Nu`() {
        val otraNu = Account("c5", "Nu Tarjeta vieja", AccountType.CREDIT_CARD, 0)
        assertNull(tarjetaDeNu("Notificación · Nu", crepes, todas + otraNu))
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, resolver(crepes, cuentas = todas + otraNu).origen)
    }

    @Test
    fun `sin tarjeta Nu cae a lo de siempre`() {
        val resuelta = resolver(crepes, cuentas = todas - nuTarjeta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, resuelta.origen)
    }

    /** «Nu» tiene que ser una palabra: «Notificación», «Número» o «Nuevo» no son Nu. */
    @Test
    fun `solo un origen que nombra a Nu activa la regla`() {
        assertNull(tarjetaDeNu("Notificación · Bancolombia", crepes, todas))
        assertNull(tarjetaDeNu("85540", "Nuevo cobro con tu tarjeta por \$1.000", todas))
        assertEquals(nuTarjeta, tarjetaDeNu("Notificación · Nubank", crepes, todas))
    }

    /** Si algún día le pone los dígitos al nombre, manda el número, como en el resto de los bancos. */
    @Test
    fun `terminada en 1336 se lee como numero de la tarjeta`() {
        val conNumero = Account("c4", "Nu Tarjeta 1336", AccountType.CREDIT_CARD, 0)
        val resuelta = resolver(crepes, cuentas = listOf(amex, ahorros, master, nuAhorros, conNumero))
        assertEquals(conNumero, resuelta.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, resuelta.origen)
    }

    @Test
    fun `Bancolombia sigue resolviendo como antes`() {
        val resuelta = resolver("Bancolombia: Compra por \$80.894 en EXITO", banco = "Bancolombia")
        assertEquals(ahorros, resuelta.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, resuelta.origen)
    }

    /** La del 23-sep: «Reconciliar movimiento» la ponía en Bancolombia Ahorros («La puso Movi»). */
    private val llegoANu =
        "Recibiste 300.000,00 en tu cuenta: Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave."

    @Test
    fun `la plata que llega a Nu cae en la cuenta Nu`() {
        val resuelta = resolverCuentaDelBanco(
            accounts = todas,
            uso = UsoDeCuenta.DESTINO_DE_INGRESO,
            banco = "Notificación · Nu",
            textoDelMensaje = llegoANu,
        )
        assertEquals(nuAhorros, resuelta.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, resuelta.origen)
    }

    @Test
    fun `con dos cuentas Nu que no son tarjeta no se elige ninguna por Nu`() {
        val otraNu = Account("a9", "Nu Cajita", AccountType.SAVINGS, 0)
        assertNull(cuentaDeNu("Notificación · Nu", llegoANu, todas + otraNu))
    }

    @Test
    fun `un SMS que no viene de Nu no cae en la cuenta Nu`() {
        assertNull(cuentaDeNu("85540", llegoANu, todas))
    }
}
