package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.COMISION_DEL_TRASLADO
import com.jvillada.movi.shared.model.DudaDelAviso
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.comoLectura
import com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.resolverCuentaDelBanco
import com.jvillada.movi.ui.porrevisar.loQueMoviEntendio
import com.jvillada.movi.ui.porrevisar.tituloDelTraspaso
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 91 · el traspaso de Bancolombia a Nu, del lado de la app
 *
 * Los textos del 31-oct-2026. Lo que la app resuelve sola: de qué cuenta salió (el «producto 8133» del
 * SMS, nunca la cuenta Nu del depósito), a cuál fue (la que propone el server, o a elegir con la tarjeta
 * de ese banco), qué dice la tarjeta de «Por revisar» y que un depósito sin leer no se anote como ingreso.
 */
class ElTraspasoPropioEnLaAppTest {
    private val b0031 = Account("b0031", "Bancolombia Ahorros 0031", AccountType.SAVINGS, 0L)
    private val b8133 = Account("b8133", "Bancolombia Ahorros 8133 · 4057", AccountType.SAVINGS, 0L)
    private val nu = Account("nu", "Nu", AccountType.SAVINGS, 0L)
    private val nuTarjeta = Account("nut", "Nu Tarjeta", AccountType.CREDIT_CARD, 0L)
    private val cuentas = listOf(b0031, b8133, nu, nuTarjeta)

    private val salidaTexto =
        "Bancolombia: Pagaste \$1,500,000.00 a NU Compania de Financiamiento desde tu producto 8133 el 31/10/2026 11:12:19. ¿Dudas? Llamanos al 6045109095. Estamos cerca"
    private val depositoTexto =
        "Recibimos tu depósito por 1.500.000,00: ¡No te preocupes! En unas horas podrás revisar la actualización en tu app."
    private val pagoNuTexto =
        "¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por \$1.306.937,91. En un rato podrás verlo en tu app."

    private fun sms(id: String, bank: String, text: String, grupo: String? = null, miembros: List<String> = emptyList()) =
        SmsMessage(id = id, time = "2026-10-31 11:12", bank = bank, text = text, state = SMS_STATE_PENDING, det = "", grupoId = grupo, miembrosDelGrupo = miembros)

    @Test
    fun `desde tu producto 8133 es la cuenta 8133, por el numero`() {
        val r = resolverCuentaDelBanco(cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, "85784", textoDelMensaje = salidaTexto)
        assertEquals(b8133, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, r.origen)
    }

    @Test
    fun `en el pago con el deposito, el deposito no vota por la cuenta de la que salio`() {
        val miembros = listOf(sms("s", "85784", salidaTexto), sms("d", "Notificación · Nu", depositoTexto))
        assertEquals(b8133, resolverCuentaDelPago(cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, miembros, elegidaAMano = null).cuenta)
        // Y si llegó solo el depósito, sí es la cuenta Nu: a ella entró.
        val solo = resolverCuentaDelBanco(cuentas, UsoDeCuenta.DESTINO_DE_INGRESO, "Notificación · Nu", textoDelMensaje = depositoTexto)
        assertEquals(nu, solo.cuenta)
    }

    @Test
    fun `el pago de la tarjeta Nu sale de la cuenta Nu, no de la tarjeta`() {
        val r = resolverCuentaDelBanco(cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, "Notificación · Nu", textoDelMensaje = pagoNuTexto)
        assertEquals(nu, r.cuenta, "anotado en la tarjeta le SUBÍA la deuda")
        // Con la tarjeta que propone el server, son las dos patas del pago.
        val leido = ParsedSms(1_306_937.91, "Pago de tarjeta", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, deudaSugeridaId = "nut")
        val patas = dosPatasPropuestas(leido, CARD_PAYMENT_CATEGORY, r.cuenta, deudaPropuestaDelAviso(r, leido.deudaSugeridaId, cuentas), null)
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, patas?.operacion)
        assertEquals(nu, patas?.origen)
        assertEquals(nuTarjeta, patas?.destino)
        // Una compra con la tarjeta Nu sigue cayendo en la tarjeta.
        val compra = resolverCuentaDelBanco(
            cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, "Notificación · Nu",
            textoDelMensaje = "Compra aprobada por \$79.000,00: Tu compra en DLO*GOOGLE Google One por \$79.000,00 con tu tarjeta terminada en 1336 ha sido APROBADA.",
        )
        assertEquals(nuTarjeta, compra.cuenta)
    }

    @Test
    fun `el destino a elegir ofrece primero las de Nu, con la tarjeta, y la tarjeta lo vuelve pago de tarjeta`() {
        val elegibles = destinosElegibles(cuentas, b8133, posibles = listOf("nu", "nut"))
        assertEquals(listOf(nu, nuTarjeta, b0031), elegibles)
        assertEquals(listOf(b0031, nu), destinosElegibles(cuentas, b8133), "sin posibles, las de siempre (sin tarjetas)")
        val leido = ParsedSms(1_500_000.0, "NU Compania de Financiamiento", TransactionType.EXPENSE, TRANSFER_CATEGORY, destinosPosibles = listOf("nu", "nut"))
        assertEquals(OperacionDelAviso.TRASPASO, dosPatasPropuestas(leido, TRANSFER_CATEGORY, b8133, null, nu)?.operacion)
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, dosPatasPropuestas(leido, TRANSFER_CATEGORY, b8133, null, nuTarjeta)?.operacion)
        // Revisión de la 91: «Pagaste $X a DAVIBANK» → el hipotecario de ese banco es su cuota.
        val hipoteca = Account("hip", "Hipoteca Papá (Davibank)", AccountType.LOAN, 0L)
        assertEquals(listOf(hipoteca, b0031, nu), destinosElegibles(cuentas + hipoteca, b8133, posibles = listOf("hip")))
        assertEquals(OperacionDelAviso.CUOTA, dosPatasPropuestas(leido, TRANSFER_CATEGORY, b8133, null, hipoteca)?.operacion)
    }

    @Test
    fun `la comision del traslado se dice en el traspaso de Bancolombia a Nu y en nada mas`() {
        val traspaso = DosPatasPropuestas(OperacionDelAviso.TRASPASO, b8133, nu)
        assertEquals(COMISION_DEL_TRASLADO, lineaDeLaComisionDelTraslado(traspaso, listOf(salidaTexto)))
        assertNull(lineaDeLaComisionDelTraslado(DosPatasPropuestas(OperacionDelAviso.TRASPASO, b8133, b0031), listOf(salidaTexto)))
        assertNull(lineaDeLaComisionDelTraslado(traspaso.copy(entrante = true), listOf(salidaTexto)))
        assertNull(lineaDeLaComisionDelTraslado(null, listOf(salidaTexto)))
    }

    @Test
    fun `Por revisar titula el pago como el traspaso que es, y no se confirma desde la fila`() {
        val miembros = listOf("s", "d", "c")
        val salida = sms("s", "85784", salidaTexto, grupo = "s", miembros = miembros)
        val deposito = sms("d", "Notificación · Nu", depositoTexto, grupo = "s", miembros = miembros)
        val correo = sms("c", "Correo · achcolombia.com.co", "PSE - Transacción Aprobada ✅ CUS 787758754", grupo = "s", miembros = miembros)
        val leido = ParsedSms(
            1_500_000.0, "NU Compañía de Financiamiento", TransactionType.EXPENSE, TRANSFER_CATEGORY,
            nota = "Depósito a tu cuenta NU", traspasoHaciaId = "nu",
        )
        val propuestas = PropuestasDeLaBandeja(listOf(PropuestaDelAviso("s", grupoId = "s", leido = leido)), categoriasDelDueno = listOf("Comida"))
        val entendido = loQueMoviEntendio(deposito, listOf(deposito, correo, salida), propuestas, cuentas, ahora = 0L)
        assertEquals("Traspaso de Bancolombia ·8133 a Nu", entendido.titulo)
        assertNull(entendido.advertencia, "el depósito que entró a Nu no es «el aviso es de Nu y la cuenta es de Bancolombia»")
        assertTrue(DudaDelAviso.DOS_PATAS in entendido.dudas, "un traspaso se confirma en Revisar aviso, con sus dos patas")
        assertNull(entendido.anotacion)
    }

    @Test
    fun `el titulo del traspaso con una sola punta`() {
        assertEquals("Traspaso a Nu", tituloDelTraspaso(null, nu))
        assertEquals("Traspaso de Bancolombia ·8133", tituloDelTraspaso(b8133, null))
        assertNull(tituloDelTraspaso(null, null))
    }

    @Test
    fun `un deposito sin leer se anota como traspaso, nunca como ingreso`() {
        val trae = AvisoSinLeer(monto = 250_000.0, tipo = TransactionType.INCOME, deposito = true)
        val lectura = trae.comoLectura()
        assertEquals(TRANSFER_CATEGORY, lectura.category)
        assertTrue(esUnTraspasoQueEntra(lectura, lectura.category, nu), "pide de qué cuenta vino")
        assertEquals(ANOTARLO_COMO_TRASPASO, botonDeAnotarloIgual(trae))
        assertEquals(ANOTARLO_IGUAL, botonDeAnotarloIgual(trae.copy(deposito = false)))
        assertEquals(
            "Movi no entendió este aviso, pero es un depósito de \$250.000 a tu cuenta. Anótalo como traspaso desde otra cuenta tuya.",
            textoDeAnotarloIgual(trae),
        )
        // La cabeza de la tarjeta: entre cuentas, sin el verde del ingreso.
        val cabeza = cabezaDelAviso(listOf(sms("d", "Notificación · Nu", "tu depósito")), null, LocalDate(2026, 10, 31), trae)
        assertEquals(TonoDeCifra.EntreCuentas, cabeza.tono)
        val leidoDeposito = ParsedSms(1_500_000.0, "Depósito a tu cuenta", TransactionType.INCOME, TRANSFER_CATEGORY)
        assertEquals(TonoDeCifra.EntreCuentas, cabezaDelAviso(listOf(sms("d", "Notificación · Nu", depositoTexto)), leidoDeposito, LocalDate(2026, 10, 31)).tono)
    }
}
