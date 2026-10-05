package com.jvillada.movi.server.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **Lo que el correo de PSE no dice, completado con lo que el dueño ya tiene en Movi**: qué crédito o
 * tarjeta abona, a qué cuenta propia fue un depósito y de qué cuenta salió. Cada cosa solo si una
 * sola encaja.
 */
class LoQueMoviSabeDelPagoTest {

    private fun cuenta(id: String, nombre: String, tipo: AccountType, moneda: String = "COP") =
        Account(id = id, name = nombre, type = tipo, balance = 0, currency = moneda)

    private val ahorros = cuenta("ahorros", "Bancolombia Ahorros", AccountType.SAVINGS)
    private val ahorros2 = cuenta("ahorros2", "Bancolombia Ahorros 0031", AccountType.SAVINGS)
    private val nu = cuenta("nu", "Nu", AccountType.SAVINGS)
    private val nuTarjeta = cuenta("nu-tc", "Nu Tarjeta", AccountType.CREDIT_CARD)
    private val davivienda = cuenta("davi-tc", "Davivienda 9418", AccountType.CREDIT_CARD)
    private val vehiculo = cuenta("vehiculo", "Vehículo 8761", AccountType.LOAN)
    private val libreInversion = cuenta("libre", "Libre inversión 9695", AccountType.LOAN)
    private val efectivo = cuenta("efectivo", "Efectivo", AccountType.CASH)
    private val cuentas = listOf(ahorros, ahorros2, nu, nuTarjeta, davivienda, vehiculo, libreInversion, efectivo)

    private val deudas = listOf(
        DeudaDelDueno(vehiculo, banco = "Banco de Occidente", cuota = 4_178_163),
        DeudaDelDueno(libreInversion, banco = "Bancolombia", cuota = 1_204_064),
    )

    private fun pago(empresa: String, categoria: String, monto: Double = 4_178_163.0) =
        ParsedSms(monto, empresa, TransactionType.EXPENSE, categoria, nota = "nota")

    private fun evento(id: String, cuenta: Account, tipo: TransactionType, ts: Long, transfer: String? = null, comercio: String? = null, desc: String = "x") =
        FinancialEvent(id = id, accountId = cuenta.id, type = tipo, amount = 1, currency = "COP", category = "c", description = desc, merchant = comercio, timestamp = ts, transferId = transfer)

    @Test
    fun `la cuota de Occidente se empareja con el credito del banco de Occidente`() {
        val p = loQueMoviSabeDelPago(pago("Banco de Occidente", CUOTA_CATEGORY), cuentas, deudas, emptyList(), emptyList())
        assertEquals("vehiculo", p.deudaSugeridaId)
        assertNull(p.cuentaSugeridaId, "sin pagos anteriores, ni regla, ni memoria, no se inventa una cuenta")
    }

    @Test
    fun `la cuenta sale de los pagos anteriores a esa deuda`() {
        val eventos = listOf(
            evento("viejo-dinero", ahorros2, TransactionType.EXPENSE, 1_000, "tr-viejo"),
            evento("viejo-deuda", vehiculo, TransactionType.INCOME, 1_000, "tr-viejo"),
            evento("nuevo-dinero", ahorros, TransactionType.EXPENSE, 2_000, "tr-nuevo"),
            evento("nuevo-deuda", vehiculo, TransactionType.INCOME, 2_000, "tr-nuevo"),
        )
        val p = loQueMoviSabeDelPago(pago("Banco de Occidente", CUOTA_CATEGORY), cuentas, deudas, emptyList(), eventos)
        assertEquals("ahorros", p.cuentaSugeridaId, "la del pago más reciente")
        assertEquals("La de tus pagos a Vehículo 8761", p.cuentaSugeridaPor)
    }

    @Test
    fun `dos creditos del mismo banco desempatan por la cuota, y si no, ninguno`() {
        val dos = deudas + DeudaDelDueno(cuenta("vehiculo2", "Otro carro", AccountType.LOAN), banco = "Banco de Occidente", cuota = 900_000)
        assertEquals("vehiculo", loQueMoviSabeDelPago(pago("Banco de Occidente", CUOTA_CATEGORY), cuentas + dos.last().cuenta, dos, emptyList(), emptyList()).deudaSugeridaId)
        assertNull(loQueMoviSabeDelPago(pago("Banco de Occidente", CUOTA_CATEGORY, monto = 1.0), cuentas + dos.last().cuenta, dos, emptyList(), emptyList()).deudaSugeridaId)
    }

    @Test
    fun `una cuota que paga otro no se propone`() {
        val ajena = listOf(DeudaDelDueno(vehiculo, banco = "Banco de Occidente", cuota = 4_178_163, noLaPagaEl = true))
        assertNull(loQueMoviSabeDelPago(pago("Banco de Occidente", CUOTA_CATEGORY), cuentas, ajena, emptyList(), emptyList()).deudaSugeridaId)
    }

    @Test
    fun `el pago de la tarjeta Nu se empareja con Nu Tarjeta, no con la cuenta Nu`() {
        val p = loQueMoviSabeDelPago(pago("NU Compañía de Financiamiento", CARD_PAYMENT_CATEGORY), cuentas, deudas, emptyList(), emptyList())
        assertEquals("nu-tc", p.deudaSugeridaId)
        assertNull(p.traspasoHaciaId)
        assertEquals("davi-tc", loQueMoviSabeDelPago(pago("Banco Davivienda", CARD_PAYMENT_CATEGORY), cuentas, deudas, emptyList(), emptyList()).deudaSugeridaId)
    }

    @Test
    fun `el deposito a tu cuenta Nu va a la cuenta Nu, que no es la tarjeta`() {
        val p = loQueMoviSabeDelPago(pago("NU Compañía de Financiamiento", TRANSFER_CATEGORY), cuentas, deudas, emptyList(), emptyList())
        assertEquals("nu", p.traspasoHaciaId)
        assertNull(p.deudaSugeridaId)
    }

    @Test
    fun `la cuenta sale de la regla recurrente que nombra a la empresa`() {
        val reglas = listOf(
            ReglaDelDueno("Coomeva", "ahorros2", TransactionType.EXPENSE),
            ReglaDelDueno("Celular", "ahorros", TransactionType.EXPENSE),
        )
        val p = loQueMoviSabeDelPago(pago("Coomeva Medicina Prepagada", "Salud", 138_600.0), cuentas, deudas, reglas, emptyList())
        assertEquals("ahorros2", p.cuentaSugeridaId)
        assertEquals("La de tu recurrente «Coomeva»", p.cuentaSugeridaPor)
        assertNull(p.deudaSugeridaId, "un gasto no abona ninguna deuda")
    }

    @Test
    fun `sin regla, la cuenta sale de la ultima vez que le pago a esa empresa`() {
        val eventos = listOf(
            evento("a", ahorros, TransactionType.EXPENSE, 1_000, comercio = "Coomeva Medicina Prepagada S A"),
            evento("b", ahorros2, TransactionType.EXPENSE, 2_000, desc = "Coomeva plan familiar"),
            evento("c", efectivo, TransactionType.EXPENSE, 3_000, desc = "Coomeva en efectivo"),
        )
        val p = loQueMoviSabeDelPago(pago("Coomeva Medicina Prepagada", "Salud", 138_600.0), cuentas, deudas, emptyList(), eventos)
        assertEquals("ahorros", p.cuentaSugeridaId, "«Coomeva plan familiar» no nombra a toda la empresa: queda la de antes")
        val q = loQueMoviSabeDelPago(pago("Coomeva", "Salud", 138_600.0), cuentas, deudas, emptyList(), eventos)
        assertEquals("ahorros2", q.cuentaSugeridaId, "la más reciente, y nunca el efectivo")
        assertEquals("La que usaste la última vez para Coomeva", q.cuentaSugeridaPor)
    }

    @Test
    fun `una empresa sin nombre propio no empareja nada`() {
        val p = loQueMoviSabeDelPago(pago("Banco S.A.", CUOTA_CATEGORY), cuentas, deudas, emptyList(), emptyList())
        assertNull(p.deudaSugeridaId)
        assertNull(p.cuentaSugeridaId)
    }
}
