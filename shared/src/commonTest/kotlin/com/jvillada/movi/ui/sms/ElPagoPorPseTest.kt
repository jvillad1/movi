package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.components.CuentaDelBanco
import com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco
import com.jvillada.movi.ui.components.avisoDeLaCuentaDelBanco
import com.jvillada.movi.ui.components.conLaCuentaQueSugiereMovi
import com.jvillada.movi.ui.components.resolverCuentaDelBanco
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Confirmar un pago por PSE**: la cuenta que sabe Movi cuando el aviso no dice ninguna, el día de
 * la transacción, la nota, y el depósito a una cuenta propia como traspaso.
 */
class ElPagoPorPseTest {

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 0)
    private val afc = Account("a2", "AFC 9497", AccountType.SAVINGS, 0)
    private val nu = Account("a3", "Nu", AccountType.SAVINGS, 0)
    private val nuTarjeta = Account("c1", "Nu Tarjeta", AccountType.CREDIT_CARD, 0)
    private val todas = listOf(afc, ahorros, nu, nuTarjeta)

    private val pse = "PSE - Transacción Aprobada CUS 700000001: Valor: \$ 138.600,00 Empresa: Coomeva Medicina Prepagada S.A."

    @Test
    fun `la cuenta que sabe Movi le gana a la de por defecto`() {
        val porDefecto = resolverCuentaDelBanco(todas, UsoDeCuenta.ORIGEN_DE_GASTO, "Correo · PSE", textoDelMensaje = pse)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, porDefecto.origen, "el correo de PSE no nombra ninguna cuenta")
        val conMovi = conLaCuentaQueSugiereMovi(porDefecto, todas, sugeridaId = "a1")
        assertEquals(ahorros, conMovi.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.SUGERIDA_POR_MOVI, conMovi.origen)
        assertEquals("Por lo que Movi sabe de este pago", avisoDeLaCuentaDelBanco(conMovi.origen))
    }

    @Test
    fun `pero no le gana al numero del aviso ni a lo que eligio el duenio`() {
        val porNumero = CuentaDelBanco(afc, OrigenDeLaCuentaDelBanco.POR_EL_NUMERO)
        assertEquals(porNumero, conLaCuentaQueSugiereMovi(porNumero, todas, "a1"))
        val aMano = CuentaDelBanco(afc, OrigenDeLaCuentaDelBanco.A_MANO)
        assertEquals(aMano, conLaCuentaQueSugiereMovi(aMano, todas, "a1"))
        val ninguna = CuentaDelBanco(null, OrigenDeLaCuentaDelBanco.NINGUNA)
        assertEquals(ninguna, conLaCuentaQueSugiereMovi(ninguna, todas, "borrada"), "una sugerida que ya no existe no cuenta")
    }

    @Test
    fun `si el aviso dice otro dia, el movimiento va ese dia a la misma hora`() {
        val zona = AppTimeZone.fixedBogota
        val llegada = LocalDateTime(2026, 10, 4, 9, 30).toInstant(zona).toEpochMilliseconds()
        val esperado = LocalDateTime(2026, 10, 3, 9, 30).toInstant(zona).toEpochMilliseconds()
        assertEquals(esperado, momentoConLaFechaDelAviso(llegada, "2026-10-03", zona))
        assertEquals(llegada, momentoConLaFechaDelAviso(llegada, "2026-10-04", zona))
        assertEquals(llegada, momentoConLaFechaDelAviso(llegada, null, zona))
        assertEquals(llegada, momentoConLaFechaDelAviso(llegada, "03/10/2026", zona), "una fecha ilegible no mueve nada")
    }

    @Test
    fun `la nota va en la descripcion`() {
        assertEquals("Coomeva Medicina Prepagada · Pago de saldo plan familiar", descripcionConLaNota("Coomeva Medicina Prepagada", "Pago de saldo plan familiar"))
        assertEquals("Coomeva", descripcionConLaNota("Coomeva", null))
        assertEquals("Coomeva", descripcionConLaNota("Coomeva", "  "))
    }

    private val deposito = ParsedSms(500_000.0, "NU Compañía de Financiamiento", TransactionType.EXPENSE, TRANSFER_CATEGORY, traspasoHaciaId = "a3", nota = "Depósito a tu cuenta NU")

    @Test
    fun `el deposito a tu cuenta Nu se anota como traspaso de dos patas desde la cuenta de origen`() {
        val resuelta = CuentaDelBanco(ahorros, OrigenDeLaCuentaDelBanco.SUGERIDA_POR_MOVI)
        val hacia = assertNotNull(destinoPropuestoDelAviso(deposito, resuelta, todas))
        assertEquals(nu, hacia)
        val propuestas = assertNotNull(dosPatasPropuestas(deposito, TRANSFER_CATEGORY, ahorros, deudaElegida = null, destinoElegido = hacia))
        assertEquals(OperacionDelAviso.TRASPASO, propuestas.operacion)
        assertEquals("Sale de Bancolombia Ahorros · entra a Nu como traspaso", propuestas.resumen)
        var n = 0
        val pedido = pedidoDeDosPatas(propuestas, deposito, momento = 1_000L) { prefijo -> "${prefijo}_${n++}" }
        assertEquals("a1", pedido.origenId)
        assertEquals("a3", pedido.destinoId)
        assertEquals(500_000L, pedido.monto)
        assertEquals("Depósito a tu cuenta NU", pedido.nota)
        assertEquals(setOf(pedido.transferId, pedido.origenEventId, pedido.destinoEventId).size, 3)
    }

    @Test
    fun `sin destino, con otra categoria o entre la misma cuenta no hay traspaso`() {
        val resuelta = CuentaDelBanco(ahorros, OrigenDeLaCuentaDelBanco.SUGERIDA_POR_MOVI)
        assertNull(destinoPropuestoDelAviso(deposito.copy(traspasoHaciaId = null), resuelta, todas))
        assertNull(dosPatasPropuestas(deposito, CUOTA_CATEGORY, ahorros, null, nu), "si el dueño eligió otra categoría, es lo que eligió")
        assertNull(destinoPropuestoDelAviso(deposito, CuentaDelBanco(nu, OrigenDeLaCuentaDelBanco.POR_EL_BANCO), todas))
        assertNull(destinoPropuestoDelAviso(deposito.copy(traspasoHaciaId = "c1"), resuelta, todas), "un traspaso no toca una tarjeta")
        assertNull(destinoPropuestoDelAviso(deposito, CuentaDelBanco(null, OrigenDeLaCuentaDelBanco.NINGUNA), todas))
        assertEquals("Un traspaso necesita la cuenta tuya a la que fue la plata. Si no está en Movi, créala, o elige otra categoría.", avisoDelTraspaso(null, ahorros))
    }

    private val amex = Account("c2", "AMEX 9208", AccountType.CREDIT_CARD, 0)
    private val carro = Account("l1", "Vehículo 8761", AccountType.LOAN, 0)

    @Test
    fun `el numero del mensaje le gana a la cuenta y a la deuda que sugiere Movi`() {
        val conAmex = todas + amex + carro
        val texto = "Bancolombia: Pagaste \$974,550 en la tarjeta de credito *9208 desde la cuenta *9497, el 15/08/2026 19:37."
        val porNumero = resolverCuentaDelBanco(conAmex, UsoDeCuenta.ORIGEN_DE_GASTO, "85784", textoDelMensaje = texto)
        val resuelta = conLaCuentaQueSugiereMovi(porNumero, conAmex, sugeridaId = "a1")
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, resuelta.origen)
        assertEquals(afc, resuelta.cuenta, "la cuenta que escribió el banco, no la sugerida")
        assertEquals(amex, deudaPropuestaDelAviso(resuelta, deudaSugeridaId = "l1", accounts = conAmex), "la tarjeta que nombra el mensaje")
    }

    @Test
    fun `sin numero en el mensaje, vale lo que sugiere Movi, y el destino leido se conserva`() {
        val conCarro = todas + carro
        val porDefecto = resolverCuentaDelBanco(conCarro, UsoDeCuenta.ORIGEN_DE_GASTO, "Correo · PSE", textoDelMensaje = pse)
        val resuelta = conLaCuentaQueSugiereMovi(porDefecto, conCarro, sugeridaId = "a1")
        assertEquals(ahorros, resuelta.cuenta)
        assertEquals(carro, deudaPropuestaDelAviso(resuelta, deudaSugeridaId = "l1", accounts = conCarro))
        assertNull(deudaPropuestaDelAviso(resuelta, deudaSugeridaId = null, accounts = conCarro))

        val conDestino = CuentaDelBanco(null, OrigenDeLaCuentaDelBanco.NINGUNA, destino = amex)
        assertEquals(amex, conLaCuentaQueSugiereMovi(conDestino, todas + amex, "a1").destino)
    }
}
