package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.CanalDelAviso
import com.jvillada.movi.server.routes.canalDelAviso
import com.jvillada.movi.server.routes.conLaTarjetaDeApplePay
import com.jvillada.movi.server.routes.origenDelAviso
import com.jvillada.movi.server.routes.origenDelSmsDelAtajo
import com.jvillada.movi.server.routes.pasaElFiltroDeBancos
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ORIGEN_DE_APPLE_PAY
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 111 · el pago con Apple Pay del atajo del iPhone: el monto sin adivinar, el texto y la tarjeta. */
class ElPagoConApplePayTest {

    @Test
    fun `los formatos del iPhone en pesos se leen igual`() {
        listOf("\$27.500,00", "27,500.00", "COP 27.500", "\$27.500", "27500", "\$ 27 500", "COL\$27.500,00", "27.500 COP").forEach {
            assertEquals(MontoDelAtajo(27_500.0, "COP"), montoDelAtajo(it), "«$it»")
        }
        assertEquals(MontoDelAtajo(1_500_000.0, "COP"), montoDelAtajo("$1.500.000"))
        assertEquals(MontoDelAtajo(1_500_000.5, "COP"), montoDelAtajo("1,500,000.50"))
    }

    @Test
    fun `los dolares se leen como dolares`() {
        assertEquals(MontoDelAtajo(12.5, "USD"), montoDelAtajo("US\$12.50"))
        assertEquals(MontoDelAtajo(12.5, "USD"), montoDelAtajo("USD 12,50"))
        assertEquals(MontoDelAtajo(1_200.0, "USD"), montoDelAtajo("USD 1,200.00"))
    }

    @Test
    fun `lo ambiguo o lo que no es pesos ni dolares no se adivina`() {
        listOf(
            "", "   ", "€ 12,00", "12,00 EUR", "1.50.000", "27.5000", "12.345.67", "1,2,3", "0", "\$0,00", "abc", "27.500,", "1.234,567",
            "0.500",
        ).forEach { assertNull(montoDelAtajo(it), "«$it» no se puede leer con seguridad") }
        assertNull(montoDelAtajo(null))
    }

    @Test
    fun `el texto del pago es legible y se vuelve a leer igual`() {
        val texto = textoDelPagoConApplePay("Rappi", "\$27.500,00", "Nu", null)
        assertEquals("Apple Pay · Rappi · \$27.500 · Nu", texto)
        val leido = leerElPagoConApplePay(texto!!)
        assertEquals(PagoConApplePay("Rappi", MontoDelAtajo(27_500.0, "COP"), "Nu"), leido)

        assertEquals("Apple Pay · Starbucks · USD 4,75", textoDelPagoConApplePay("Starbucks", "US$4.75", null, null))
        assertEquals("Apple Pay · Exito · \$12.300,50 · Visa", textoDelPagoConApplePay("Exito", "12.300,50", "Visa", null))
    }

    @Test
    fun `lo que falta se dice y un punto medio del comercio no rompe el texto`() {
        assertEquals("Apple Pay · Compra · \$5.000", textoDelPagoConApplePay(null, "5000", null, null))
        assertEquals("Apple Pay · Pago en tienda · sin monto · Nu", textoDelPagoConApplePay(null, "??", "Nu", "Pago en tienda"))
        assertEquals("Apple Pay · Tienda - Centro · \$5.000", textoDelPagoConApplePay("Tienda · Centro", "5000", null, null))
        assertNull(textoDelPagoConApplePay(null, null, null, null))
        val sinMonto = leerElPagoConApplePay("Apple Pay · Amazon · sin monto · Nu")
        assertEquals(PagoConApplePay("Amazon", null, "Nu"), sinMonto)
    }

    @Test
    fun `parseSms lee el pago como un gasto con su comercio y su moneda`() {
        val leido = parseSms("Apple Pay · Rappi · \$27.500 · Nu", ORIGEN_DE_APPLE_PAY)
        assertNotNull(leido)
        assertEquals(27_500.0, leido.amount)
        assertEquals("Rappi", leido.merchant)
        assertEquals(TransactionType.EXPENSE, leido.type)
        assertEquals("COP", leido.currency)
        assertEquals("USD", parseSms("Apple Pay · Uber · USD 9,99", ORIGEN_DE_APPLE_PAY)?.currency)
    }

    @Test
    fun `un pago con Apple Pay nunca se aparta`() {
        // Ni con palabras que en un SMS harían pensar en una promoción.
        assertNull(motivoParaApartar("Apple Pay · Descuentos y Promociones SAS · \$10.000 · Nu", ORIGEN_DE_APPLE_PAY))
    }

    @Test
    fun `Apple Pay es el aviso de la billetera y no se confunde con los SMS`() {
        assertEquals(CanalDelAviso.NOTIFICACION, canalDelAviso(ORIGEN_DE_APPLE_PAY))
        assertEquals(CanalDelAviso.SMS, canalDelAviso("Atajo · Bancolombia"))
        assertEquals("sms", origenDelAviso("Atajo · Bancolombia"))
        assertFalse(origenDelAviso(ORIGEN_DE_APPLE_PAY) == "sms")
    }

    private fun cuenta(id: String, nombre: String, tipo: AccountType) = Account(id = id, name = nombre, type = tipo, balance = 0)

    @Test
    fun `la cuenta de la tarjeta se propone solo si calza una sola`() {
        val cuentas = listOf(
            cuenta("mb", "Master Black 3684", AccountType.CREDIT_CARD),
            cuenta("amex", "AMEX 1004", AccountType.CREDIT_CARD),
            cuenta("nu", "Nu", AccountType.SAVINGS),
            cuenta("nut", "Nu Tarjeta", AccountType.CREDIT_CARD),
            cuenta("ah", "Bancolombia Ahorros 8133", AccountType.SAVINGS),
            cuenta("cred", "Vehículo 3684", AccountType.LOAN),
        )
        assertEquals("mb", cuentaDeLaTarjeta("Mastercard Black", cuentas)?.id)
        assertEquals("mb", cuentaDeLaTarjeta("Visa ••3684", cuentas)?.id, "el crédito con los mismos cuatro no es una tarjeta")
        assertEquals("ah", cuentaDeLaTarjeta("Débito 8133", cuentas)?.id)
        assertNull(cuentaDeLaTarjeta("Nu", cuentas), "la cuenta Nu y la tarjeta Nu calzan las dos: se pide en Revisar")
        assertNull(cuentaDeLaTarjeta("Visa", cuentas), "«Visa» no dice cuál")
        assertNull(cuentaDeLaTarjeta(null, cuentas))
    }

    @Test
    fun `la propuesta lleva la cuenta de la tarjeta si nadie propuso otra`() {
        val cuentas = listOf(cuenta("mb", "Master Black 3684", AccountType.CREDIT_CARD))
        val base = ParsedSms(27_500.0, "Rappi", TransactionType.EXPENSE, "Domicilios")
        val conCuenta = conLaTarjetaDeApplePay(base, "Apple Pay · Rappi · \$27.500 · Master Black", cuentas)
        assertEquals("mb", conCuenta.cuentaSugeridaId)
        assertTrue(conCuenta.cuentaSugeridaPor!!.contains("Apple Pay"))
        val yaPropuesta = base.copy(cuentaSugeridaId = "otra")
        assertEquals("otra", conLaTarjetaDeApplePay(yaPropuesta, "Apple Pay · Rappi · \$27.500 · Master Black", cuentas).cuentaSugeridaId)
        assertNull(conLaTarjetaDeApplePay(base, "Bancolombia: Compraste \$27.500", cuentas).cuentaSugeridaId)
    }

    @Test
    fun `el filtro del server es el del telefono`() {
        assertTrue(pasaElFiltroDeBancos("85540", "Transferiste \$50.000"))
        assertTrue(pasaElFiltroDeBancos(null, "Bancolombia: Compraste \$27.500"))
        assertTrue(pasaElFiltroDeBancos("Mamá", "DAVIVIENDA Descuento en internet, \$64,900"))
        assertFalse(pasaElFiltroDeBancos("Mamá", "Te mandé por Nequi lo del almuerzo"))
        assertFalse(pasaElFiltroDeBancos(null, "Tu código de Uber es 1234"))
    }

    @Test
    fun `el origen de un SMS del atajo nombra al banco y nunca al remitente`() {
        assertEquals("Atajo · Bancolombia", origenDelSmsDelAtajo("85540", "Compraste \$1"))
        assertEquals("Atajo · Bancolombia", origenDelSmsDelAtajo("Bancolombia", "Compraste \$1"))
        assertEquals("Atajo · tu banco", origenDelSmsDelAtajo("Mi jefe", "algo del banco bbva \$1"))
    }
}
