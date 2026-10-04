package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
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
    fun `el deposito a tu cuenta Nu se anota como traspaso desde la cuenta de origen`() {
        val hacia = assertNotNull(destinoDelTraspaso(deposito, TRANSFER_CATEGORY, ahorros, todas))
        assertEquals(nu, hacia)
        var n = 0
        val traspaso = traspasoDelAviso(deposito, ahorros, hacia, momento = 1_000L) { prefijo -> "${prefijo}_${n++}" }
        assertEquals("a1", traspaso.fromAccountId)
        assertEquals("a3", traspaso.toAccountId)
        assertEquals(500_000L, traspaso.amount)
        assertEquals("Depósito a tu cuenta NU", traspaso.note)
        assertEquals(setOf(traspaso.transferId, traspaso.fromEventId, traspaso.toEventId).size, 3)
        assertEquals("Se anota como traspaso de Bancolombia Ahorros a Nu: no cuenta como gasto.", avisoDelTraspaso(hacia, ahorros))
    }

    @Test
    fun `sin destino, con otra categoria o entre la misma cuenta no hay traspaso`() {
        assertNull(destinoDelTraspaso(deposito.copy(traspasoHaciaId = null), TRANSFER_CATEGORY, ahorros, todas))
        assertNull(destinoDelTraspaso(deposito, CUOTA_CATEGORY, ahorros, todas), "si el dueño eligió otra categoría, es lo que eligió")
        assertNull(destinoDelTraspaso(deposito, TRANSFER_CATEGORY, nu, todas))
        assertNull(destinoDelTraspaso(deposito.copy(traspasoHaciaId = "c1"), TRANSFER_CATEGORY, ahorros, todas), "un traspaso no toca una tarjeta")
        assertNull(destinoDelTraspaso(deposito, TRANSFER_CATEGORY, null, todas))
    }
}
