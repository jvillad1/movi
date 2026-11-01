package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.DEPOSITO_A_TU_CUENTA
import com.jvillada.movi.server.routes.ElTraspasoANuDePuntaAPuntaTest
import com.jvillada.movi.server.routes.gruposDelMismoPago
import com.jvillada.movi.server.routes.loQueTraeUnAvisoSinLeer
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.server.routes.propuestaDelGrupo
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.comoLectura
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 91 · el traspaso propio, sin la base: cómo se lee, cómo se junta y qué se propone
 *
 * Con los textos reales del 31-oct-2026 (`ElTraspasoANuDePuntaAPuntaTest`) y variantes de PSE a otros
 * bancos con montos inventados.
 */
class ElTraspasoPropioTest {
    private val ahora = 1_900_000_000_000L

    private fun sms(id: String, bank: String, text: String, time: String = "2026-10-31 11:12") =
        SmsMessage(id = id, time = time, bank = bank, text = text, state = SMS_STATE_PENDING, det = "")

    private val salida = sms("s", "85784", ElTraspasoANuDePuntaAPuntaTest.SMS_DE_SALIDA)
    private val deposito = sms("d", "Notificación · Nu", ElTraspasoANuDePuntaAPuntaTest.DEPOSITO_DE_NU)
    private val correo = sms("c", "Correo · achcolombia.com.co", ElTraspasoANuDePuntaAPuntaTest.CORREO_DE_PSE)

    private fun cuenta(id: String, nombre: String, tipo: AccountType) = Account(id = id, name = nombre, type = tipo, balance = 0L)
    private val ahorros8133 = cuenta("b8133", "Bancolombia Ahorros 8133 · 4057", AccountType.SAVINGS)
    private val ahorros0031 = cuenta("b0031", "Bancolombia Ahorros 0031", AccountType.SAVINGS)
    private val nu = cuenta("nu", "Nu", AccountType.SAVINGS)
    private val nuTarjeta = cuenta("nut", "Nu Tarjeta", AccountType.CREDIT_CARD)
    private val davivienda = cuenta("davi", "Davivienda Ahorros", AccountType.SAVINGS)
    private val cuentas = listOf(ahorros0031, ahorros8133, nu, nuTarjeta)

    // ── Cómo se lee ──────────────────────────────────────────────────────────────

    @Test
    fun `el deposito de Nu se lee como el lado que entra de un traspaso, con la coma decimal`() {
        val p = assertNotNull(parseSms(deposito.text, deposito.bank), "ya no es «Movi no entendió este aviso»")
        assertEquals(1_500_000.0, p.amount)
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals(TRANSFER_CATEGORY, p.category)
        assertEquals(DEPOSITO_A_TU_CUENTA, p.merchant)
        assertNull(p.identificadorDelDestino)
    }

    @Test
    fun `el SMS de salida sigue leyendose igual, sin las cuentas del dueno`() {
        val p = assertNotNull(parseSms(salida.text, salida.bank))
        assertEquals(1_500_000.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("NU Compania de Financiamiento", p.merchant)
    }

    @Test
    fun `un aviso sin leer que habla de tu deposito se marca para no anotarlo como ingreso`() {
        // Una forma que Movi no lee (sin «Recibimos»), pero que habla del depósito del dueño.
        val raro = sms("x", "Notificación · Lulo Bank", "Listo: tu depósito quedó en camino. Valor \$250.000,00")
        val trae = assertNotNull(loQueTraeUnAvisoSinLeer(raro))
        assertTrue(trae.deposito)
        assertEquals(TransactionType.INCOME, trae.tipo)
        assertEquals(TRANSFER_CATEGORY, trae.comoLectura().category, "se propone como traspaso: la app pide de qué cuenta vino")
        // Y lo de siempre sigue igual: un envío sin leer no es un depósito.
        val envio = assertNotNull(loQueTraeUnAvisoSinLeer(sms("y", "Notificación · Lulo Bank", "Enviamos \$40.000,00 a Pedro")))
        assertFalse(envio.deposito)
        assertEquals("", envio.comoLectura().category)
    }

    // ── Cómo se junta ────────────────────────────────────────────────────────────

    @Test
    fun `el SMS, el correo de PSE y el deposito de Nu son un solo pago, y propone el SMS`() {
        val grupos = gruposDelMismoPago(listOf(salida, deposito, correo), ahora)
        assertEquals(listOf(setOf("s", "d", "c")), grupos.map { it.ids.toSet() })
        assertEquals("s", propuestaDelGrupo(grupos.single().miembros).id)
    }

    @Test
    fun `el correo de PSE y el deposito, sin el SMS, se juntan y propone el correo`() {
        val grupos = gruposDelMismoPago(listOf(deposito, correo), ahora)
        assertEquals(listOf(setOf("d", "c")), grupos.map { it.ids.toSet() })
        assertEquals("c", propuestaDelGrupo(grupos.single().miembros).id, "el depósito no dice de qué cuenta salió")
    }

    @Test
    fun `un deposito a mas de 30 minutos, de otro monto o de otro banco no se junta`() {
        val tarde = deposito.copy(id = "tarde", time = "2026-10-31 11:50")
        assertTrue(gruposDelMismoPago(listOf(salida, tarde), ahora).isEmpty(), "38 minutos: no")
        val otroMonto = deposito.copy(id = "otro", text = deposito.text.replace("1.500.000,00", "1.400.000,00"))
        assertTrue(gruposDelMismoPago(listOf(salida, otroMonto), ahora).isEmpty())
        val aOtroBanco = salida.copy(id = "davi", text = salida.text.replace("NU Compania de Financiamiento", "DAVIVIENDA"))
        assertTrue(gruposDelMismoPago(listOf(aOtroBanco, deposito), ahora).isEmpty(), "el depósito es de Nu, el pago fue a Davivienda")
        // Un gasto cualquiera del mismo monto (no a un banco) tampoco.
        val gasto = sms("g", "85540", "Bancolombia: Compraste \$1,500,000.00 en ALKOSTO con tu T.Deb *4057, el 31/10/2026 a las 11:12.")
        assertTrue(gruposDelMismoPago(listOf(gasto, deposito), ahora).isEmpty())
    }

    // ── Qué se propone ───────────────────────────────────────────────────────────

    private fun leido(s: SmsMessage) = assertNotNull(parseSms(s.text, s.bank))

    @Test
    fun `con el deposito en el mismo pago, el destino es la cuenta que lo aviso`() {
        val p = conElTraspasoPropio(leido(salida), salida, listOf(deposito), cuentas)
        assertEquals(TRANSFER_CATEGORY, p.category)
        assertEquals("nu", p.traspasoHaciaId)
    }

    @Test
    fun `solo el SMS, con la cuenta Nu y la Nu Tarjeta, deja el destino a elegir con las dos`() {
        val p = conElTraspasoPropio(leido(salida), salida, emptyList(), cuentas)
        assertEquals(TRANSFER_CATEGORY, p.category)
        assertNull(p.traspasoHaciaId)
        assertEquals(listOf("nu", "nut"), p.destinosPosibles)
    }

    @Test
    fun `PSE a otros bancos con una cuenta ahi es un traspaso con esa cuenta a elegir`() {
        val aDavivienda = sms("dv", "85784", "Bancolombia: Pagaste \$300,000.00 a DAVIVIENDA desde tu producto 8133 el 31/10/2026 09:00:00.")
        val p = conElTraspasoPropio(leido(aDavivienda), aDavivienda, emptyList(), cuentas + davivienda)
        assertEquals(TRANSFER_CATEGORY, p.category)
        assertNull(p.traspasoHaciaId, "el nombre solo no elige el destino")
        assertEquals(listOf("davi"), p.destinosPosibles)
        // Sin cuenta en ese banco, sigue siendo lo que era (un pago a alguien más).
        val aBogota = sms("bb", "85784", "Bancolombia: Pagaste \$300,000.00 a BANCO DE BOGOTA desde tu producto 8133 el 31/10/2026 09:00:00.")
        val sinCuenta = conElTraspasoPropio(leido(aBogota), aBogota, emptyList(), cuentas)
        assertEquals(leido(aBogota), sinCuenta)
    }

    @Test
    fun `un pago a su propio banco, a un credito o a una empresa no se vuelve traspaso`() {
        val aBancolombia = sms("bc", "85784", "Bancolombia: Pagaste \$80,000.00 a BANCOLOMBIA desde tu producto 8133 el 31/10/2026 09:00:00.")
        assertEquals(leido(aBancolombia), conElTraspasoPropio(leido(aBancolombia), aBancolombia, emptyList(), cuentas))
        val coomeva = sms("co", "85784", "Bancolombia: Pagaste \$138,600.00 a Coomeva Medicina Prepagada S A desde tu producto 8133 el 03/10/2026 09:00:00.")
        assertEquals(leido(coomeva), conElTraspasoPropio(leido(coomeva), coomeva, emptyList(), cuentas))
        // Lo que el correo de PSE ya dijo (una cuota, el pago de una tarjeta) no se cambia por el nombre.
        val cuota = leido(salida).copy(category = CUOTA_CATEGORY)
        assertEquals(cuota, conElTraspasoPropio(cuota, salida, emptyList(), cuentas))
    }

    @Test
    fun `el pago de la tarjeta Nu se propone desde la cuenta Nu hacia la Nu Tarjeta`() {
        val pago = sms("p", "Notificación · Nu", ElTraspasoANuDePuntaAPuntaTest.PAGO_TARJETA_NU, time = "2026-10-31 12:13")
        val l = leido(pago)
        assertEquals(CARD_PAYMENT_CATEGORY, l.category)
        val p = conElTraspasoPropio(l, pago, emptyList(), cuentas)
        assertEquals("nut", p.deudaSugeridaId)
        assertEquals("nu", p.cuentaSugeridaId)
        // Con dos cuentas Nu de plata, de cuál salió lo elige el dueño.
        val dosNu = conElTraspasoPropio(l, pago, emptyList(), cuentas + cuenta("nu2", "Nu Cajita", AccountType.SAVINGS))
        assertNull(dosNu.cuentaSugeridaId)
        assertEquals("nut", dosNu.deudaSugeridaId)
    }

    @Test
    fun `el pago por PSE avisado tambien por Nu como pago de la tarjeta es el pago de la tarjeta`() {
        val pago = sms("p", "Notificación · Nu", ElTraspasoANuDePuntaAPuntaTest.PAGO_TARJETA_NU.replace("1.306.937,91", "1.500.000,00"))
        val p: ParsedSms = conElTraspasoPropio(leido(salida), salida, listOf(pago), cuentas)
        assertEquals(CARD_PAYMENT_CATEGORY, p.category)
        assertEquals("nut", p.deudaSugeridaId)
        assertNull(p.traspasoHaciaId)
    }
}
