package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # El pago con la Glim cae en la Glim
 *
 * El 25-sep el dueño pagó $15.100 en TOSTAO CAFE Y PAN con su tarjeta Glim y le llegaron dos avisos
 * del mismo pago: el de Google Wallet (`… with Glim ••3037`) y el de la app de Glim (sin número).
 * Los dos se anotaron en «Bancolombia Ahorros» con el rótulo «La puso Movi»: el número venía con
 * viñetas y no con asterisco, y el rótulo «Notificación · Glim» no es el nombre de ninguna cuenta.
 *
 * Las cuentas son las suyas, con sus nombres.
 */
class LaMarcaDeLaCuentaTest {

    private val glim = Account("g1", "Glim Alimentación 3037", AccountType.SAVINGS, 180_000)
    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 824_538)
    private val ahorros0031 = Account("a2", "Bancolombia Ahorros 0031", AccountType.SAVINGS, 1_000)
    private val nu = Account("a3", "Nu", AccountType.SAVINGS, 12_769_308)
    private val nuTarjeta = Account("c4", "Nu Tarjeta", AccountType.CREDIT_CARD, 0)
    private val master = Account("c1", "Master Black 3684", AccountType.CREDIT_CARD, 27_501_150)
    private val masterUsd = Account("c2", "Master Black 3684 USD", AccountType.CREDIT_CARD, 0, currency = "USD")

    /** Ordenadas por nombre, como las devuelve `GET /api/accounts`. */
    private val todas = listOf(ahorros, ahorros0031, glim, master, masterUsd, nu, nuTarjeta)

    private val deWallet = "TOSTAO CAFE Y PAN VISC: COP15,100 with Glim ••3037"
    private val deGlim = "¡Usaste tus beneficios!: Pagaste \$15.100,00 COP con tu tarjeta de beneficios Glim " +
        "el 25/09/2026 a las 14:15 en TOSTAO CAFE Y PAN."
    private val nuPorWallet = "CREPES Y WAFFLES LEMON: COP130,200 with Nu Mastercard Gold ••1336"
    private val debitoPorWallet = "WOMPI SAS: COP14,641 with Debito Mastercard ••4057"

    private fun resolver(texto: String, banco: String) = resolverCuentaDelBanco(
        accounts = todas,
        uso = UsoDeCuenta.ORIGEN_DE_GASTO,
        banco = banco,
        textoDelMensaje = texto,
    )

    // ── El número con viñetas ──────────────────────────────────────────────────

    @Test
    fun `el numero con vinetas de Google Wallet se lee como numero`() {
        val r = resolver(deWallet, banco = "Notificación · Google Wallet")
        assertEquals(glim, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, r.origen)
    }

    @Test
    fun `una sola vineta o puntos medios tambien son numero`() {
        assertEquals(glim, cuentaPorElNumero("PAN: COP1,000 with Glim •3037", todas))
        assertEquals(glim, cuentaPorElNumero("PAN: COP1,000 with Glim ····3037", todas))
    }

    @Test
    fun `con vinetas el empate por numero sigue sin elegir`() {
        assertNull(cuentaPorElNumero("UBER: \$20.000 with Master Black ••3684", todas))
    }

    // ── La marca ───────────────────────────────────────────────────────────────

    @Test
    fun `el aviso de la app de Glim cae en la cuenta Glim`() {
        val r = resolver(deGlim, banco = "Notificación · Glim")
        assertEquals(glim, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, r.origen)
    }

    @Test
    fun `los dos avisos del mismo pago caen en la misma cuenta`() {
        assertEquals(
            resolver(deWallet, banco = "Notificación · Google Wallet").cuenta,
            resolver(deGlim, banco = "Notificación · Glim").cuenta,
        )
    }

    @Test
    fun `la marca de la etiqueta de Wallet sin numero en el nombre`() {
        // Sin el número en el nombre de la cuenta, la etiqueta «Glim» es lo que queda.
        val glimSinNumero = Account("g1", "Glim Alimentación", AccountType.SAVINGS, 180_000)
        val cuentas = todas - glim + glimSinNumero
        val r = resolverCuentaDelBanco(cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, "Notificación · Google Wallet", textoDelMensaje = deWallet)
        assertEquals(glimSinNumero, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, r.origen)
    }

    @Test
    fun `la tarjeta de Nu por Wallet cae en Nu Tarjeta y no en la cuenta Nu`() {
        val r = resolver(nuPorWallet, banco = "Notificación · Google Wallet")
        assertEquals(nuTarjeta, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, r.origen)
    }

    @Test
    fun `una debito sin marca de cuenta sigue como hoy`() {
        assertNull(cuentaPorLaMarca("Notificación · Google Wallet", debitoPorWallet, todas))
        val r = resolver(debitoPorWallet, banco = "Notificación · Google Wallet")
        assertEquals(ahorros, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, r.origen)
    }

    @Test
    fun `una etiqueta de debito no cae en una tarjeta de credito`() {
        assertEquals(nu, cuentaPorLaMarca("Notificación · Google Wallet", "RAPPI: COP9,000 with Nu Débito ••1111", todas))
    }

    @Test
    fun `Google Wallet no es una marca de cuenta`() {
        val wallet = Account("w1", "Google Wallet", AccountType.SAVINGS, 0)
        assertNull(cuentaPorLaMarca("Notificación · Google Wallet", "Set up a shortcut to pay: 2 toques", todas + wallet))
    }

    @Test
    fun `con dos cuentas de la marca no se elige ninguna`() {
        // «Notificación · Bancolombia» nombra dos cuentas suyas: no se adivina.
        assertNull(cuentaPorLaMarca("Notificación · Bancolombia", "Compraste \$1.000 en EXITO", todas))
    }

    @Test
    fun `la marca es una palabra y no un pedazo`() {
        val glimmer = Account("x1", "Glimmer", AccountType.SAVINGS, 0)
        assertNull(cuentaPorLaMarca("Notificación · Glim", deGlim, listOf(ahorros, glimmer)))
    }

    @Test
    fun `la marca no distingue mayusculas ni tildes`() {
        val cuenta = Account("x2", "Crédito Fácil Codensa", AccountType.CREDIT_CARD, 0)
        assertEquals(cuenta, cuentaPorLaMarca("Correo · codensa", "Compra por \$10.000", listOf(ahorros, cuenta)))
    }

    @Test
    fun `un origen que no es notificacion ni correo no pasa por la marca`() {
        assertNull(cuentaPorLaMarca("85540", "Glim", todas))
    }

    // ── Lo que no cambia ──────────────────────────────────────────────────────

    @Test
    fun `un SMS de Bancolombia desde tu cuenta 8133 no cambia`() {
        val texto = "Bancolombia: Transferiste \$50,000.00 desde tu cuenta *8133 a la cuenta *31973270756 el 10/09/26."
        val r = resolver(texto, banco = "85540")
        assertEquals(ahorros, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, r.origen)
    }

    @Test
    fun `la compra de Nu por su app sigue cayendo en Nu Tarjeta`() {
        val crepes = "Compra aprobada por \$130.200,00: Tu compra en CREPES Y WAFFLES LEMON por \$130.200,00 " +
            "con tu tarjeta terminada en 1336 ha sido APROBADA."
        assertEquals(nuTarjeta, resolver(crepes, banco = "Notificación · Nu").cuenta)
    }
}
