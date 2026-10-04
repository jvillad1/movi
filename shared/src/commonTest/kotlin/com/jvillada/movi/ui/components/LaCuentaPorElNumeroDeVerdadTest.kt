package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # La cuenta, por el número de verdad (auditoría de la ingesta, 4-oct-2026, arreglo 2)
 *
 * Con los datos reales, 163 de 225 avisos caían en la cuenta «por defecto» —la primera por orden
 * alfabético— y en tres formas fallaban siempre:
 *
 * - los ingresos a la \*8133 se proponían en la AFC (plata condicionada a vivienda);
 * - los retiros de la Fiducuenta \*9586 se proponían en Bancolombia Ahorros;
 * - los pagos a la AMEX \*9208 salían como un gasto EN la tarjeta.
 *
 * Los textos tienen la forma exacta de los avisos reales; las cuentas, los nombres y tipos de las
 * suyas (sin saldos).
 */
class LaCuentaPorElNumeroDeVerdadTest {

    private val afc = Account("afc", "AFC Davibank 9497", AccountType.SAVINGS, 0, condicionadaA = "vivienda")
    private val ahorros = Account("aho", "Bancolombia Ahorros", AccountType.SAVINGS, 0)
    private val ahorros0031 = Account("aho0031", "Bancolombia Ahorros 0031", AccountType.SAVINGS, 0)
    private val fiducuenta = Account("fidu", "Fiducuenta 9586", AccountType.INVESTMENT, 0)
    private val glim = Account("glim", "Glim Alimentación 3037", AccountType.SAVINGS, 0)
    private val nu = Account("nu", "Nu", AccountType.SAVINGS, 0)
    private val skandia = Account("sk", "Skandia pensión voluntaria", AccountType.INVESTMENT, 0, condicionadaA = "pensión voluntaria")
    private val amex = Account("amex", "AMEX 9208", AccountType.CREDIT_CARD, 0)
    private val master = Account("mb", "Master Black 3684", AccountType.CREDIT_CARD, 0)
    private val masterUsd = Account("mbu", "Master Black 3684 USD", AccountType.CREDIT_CARD, 0, currency = "USD")
    private val libranza = Account("lib", "Libranza 4608", AccountType.LOAN, 0)

    /** En el orden en que llegan: por nombre. */
    private fun cuentas(conAhorros: Account = ahorros) =
        listOf(afc, amex, conAhorros, ahorros0031, fiducuenta, glim, libranza, master, masterUsd, nu, skandia)
            .sortedBy { it.name }

    private val ahorrosConNumero = Account("aho", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 0)

    private fun resolver(texto: String, uso: UsoDeCuenta, todas: List<Account> = cuentas()) =
        resolverCuentaDelBanco(accounts = todas, uso = uso, banco = "85540", textoDelMensaje = texto)

    // ── Los ingresos a la *8133 ──────────────────────────────────────────────

    private val ingresoA8133 = "Bancolombia: JUAN, recibiste una transferencia de PERSONA DE PRUEBA por \$1,000,000.00 " +
        "en tu cuenta *8133 conectada a la llave @LLAVE01 el 31/08/26 a las 10:04. Con llaves es de una y gratis."

    @Test
    fun `un ingreso a la 8133 cae en la cuenta de ahorros, no en la AFC`() {
        val r = resolver(ingresoA8133, UsoDeCuenta.DESTINO_DE_INGRESO)
        assertEquals(ahorros, r.cuenta, "la AFC es la primera por nombre, pero es plata condicionada")
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, r.origen)
    }

    @Test
    fun `con el numero en el nombre, el ingreso cae por el numero`() {
        val r = resolver(ingresoA8133, UsoDeCuenta.DESTINO_DE_INGRESO, cuentas(ahorrosConNumero))
        assertEquals(ahorrosConNumero, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, r.origen)
    }

    @Test
    fun `el defecto de un ingreso nunca es una cuenta condicionada, aunque sea la unica`() {
        val r = resolver(ingresoA8133, UsoDeCuenta.DESTINO_DE_INGRESO, listOf(afc, skandia))
        assertNull(r.cuenta, "mejor que la elija él que proponerle plata condicionada")
        assertEquals(OrigenDeLaCuentaDelBanco.NINGUNA, r.origen)
    }

    @Test
    fun `si el banco escribe el numero de la AFC, la AFC si se encuentra`() {
        val texto = "Recibiste \$500.000 en tu cuenta *9497."
        assertEquals(afc, resolver(texto, UsoDeCuenta.DESTINO_DE_INGRESO).cuenta)
    }

    // ── El retiro de la Fiducuenta ───────────────────────────────────────────

    private val retiroDeLaFiducuenta = "Bancolombia: Retiraste \$4,200,000.00 de tu cuenta *9586 Fiducuenta el " +
        "2026/09/18 19:38:31, hacia la cuenta *02955068133. ¿Dudas? 6045109009"

    @Test
    fun `un retiro de la Fiducuenta hacia la 8133 sale de la Fiducuenta`() {
        val r = resolver(retiroDeLaFiducuenta, UsoDeCuenta.ORIGEN_DE_GASTO)
        assertEquals(fiducuenta, r.cuenta, "una inversión no es origen de un gasto, pero el banco nombró su número")
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, r.origen)
        assertNull(r.destino, "«Bancolombia Ahorros» no lleva el número: el destino no se sabe")
    }

    @Test
    fun `con el numero en el nombre, el destino del retiro es la cuenta de ahorros`() {
        val r = resolver(retiroDeLaFiducuenta, UsoDeCuenta.ORIGEN_DE_GASTO, cuentas(ahorrosConNumero))
        assertEquals(fiducuenta, r.cuenta)
        assertEquals(ahorrosConNumero, r.destino, "los últimos cuatro del número completo 02955068133")
        assertNull(deudaQueNombraElMensaje(r), "un traspaso entre cuentas suyas no es el pago de una deuda")
    }

    // ── El pago a la AMEX ────────────────────────────────────────────────────

    private val pagoALaAmex = "Bancolombia: Pagaste \$1,008,902 en la tarjeta de credito *9208 desde la cuenta *8133, " +
        "el 30/08/2026 20:25. ¿Dudas? Llamanos al 018000912345. Estamos cerca."

    @Test
    fun `el pago a la AMEX desde la 8133 sale de Ahorros y va a la AMEX, como pago de deuda`() {
        val r = resolver(pagoALaAmex, UsoDeCuenta.ORIGEN_DE_GASTO)
        assertEquals(ahorros, r.cuenta, "la AMEX es el destino, no la cuenta de donde sale la plata")
        assertEquals(amex, r.destino)
        assertEquals(amex, deudaQueNombraElMensaje(r), "la pantalla deja elegido «¿A cuál crédito o tarjeta corresponde?»")
    }

    @Test
    fun `con el numero en el nombre, el origen del pago sale por el numero`() {
        val r = resolver(pagoALaAmex, UsoDeCuenta.ORIGEN_DE_GASTO, cuentas(ahorrosConNumero))
        assertEquals(ahorrosConNumero, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, r.origen)
        assertEquals(amex, deudaQueNombraElMensaje(r))
    }

    @Test
    fun `el pago a la Master Black no adivina entre pesos y dolares`() {
        val texto = "Bancolombia: Pagaste \$386,902 en la tarjeta de credito *3684 desde la cuenta *8133, el 27/09/2026 09:17."
        val r = resolver(texto, UsoDeCuenta.ORIGEN_DE_GASTO)
        assertEquals(ahorros, r.cuenta)
        assertNull(r.destino, "dos tarjetas con el mismo número y un «\$» a secas: la elige él")
    }

    @Test
    fun `el abono de un tercero a la tarjeta es de la tarjeta`() {
        val texto = "Bancolombia: Recibimos pago por \$9,000,000.00 a tu tarjeta de credito **9208 desde Wompi-PSE, el 04/09/2026 08:11."
        val r = resolver(texto, UsoDeCuenta.DESTINO_DE_INGRESO)
        assertEquals(amex, r.cuenta, "es la única cuenta suya que nombra: nadie le sacó plata de otra")
        assertNull(deudaQueNombraElMensaje(r))
    }

    @Test
    fun `el avance sale de la tarjeta`() {
        val texto = "Bancolombia: Hiciste un avance de \$6.200.000 en tu SUC VIRTUAL el 17:43 03/10/2026 " +
            "desde tu T.Credito *9208 a la cuenta *8133."
        val r = resolver(texto, UsoDeCuenta.ORIGEN_DE_GASTO, cuentas(ahorrosConNumero))
        assertEquals(amex, r.cuenta)
        assertEquals(ahorrosConNumero, r.destino)
        assertNull(deudaQueNombraElMensaje(r), "la plata va de la deuda a la cuenta, no al revés")
    }

    @Test
    fun `una transferencia a un tercero no tiene destino propio`() {
        val texto = "Bancolombia: Transferiste \$50,000.00 desde tu cuenta *8133 a la cuenta *31973270756 el 10/09/26."
        val r = resolver(texto, UsoDeCuenta.ORIGEN_DE_GASTO)
        assertEquals(ahorros, r.cuenta)
        assertNull(r.destino)
    }

    @Test
    fun `la tarjeta debito en el nombre de la cuenta tambien la encuentra`() {
        val conLaDebito = Account("aho", "Bancolombia Ahorros 8133 · 4057", AccountType.SAVINGS, 0)
        val texto = "Bancolombia: Compraste \$15.100,00 en TOSTAO CAFE Y PAN con tu T.Deb *4057, el 25/09/2026 a las 09:15."
        val r = resolver(texto, UsoDeCuenta.ORIGEN_DE_GASTO, cuentas(conLaDebito))
        assertEquals(conLaDebito, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, r.origen)
    }
}
