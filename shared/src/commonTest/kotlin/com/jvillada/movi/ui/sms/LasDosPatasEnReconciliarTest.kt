package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AVANCE_DE_TARJETA_CATEGORY
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.ui.components.resolverCuentaDelBanco
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Lo que Reconciliar propone crear** con los avisos reales de la auditoría: el pago a la Master
 * Black, el retiro de la Fiducuenta hacia Ahorros, el avance de la AMEX y la cuota del carro.
 */
class LasDosPatasEnReconciliarTest {

    private val ahorros = Account("a", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 0)
    private val fidu = Account("f", "Fiducuenta 9586", AccountType.INVESTMENT, 0)
    private val masterBlack = Account("m", "Master Black 3684", AccountType.CREDIT_CARD, 0)
    private val amex = Account("x", "AMEX 9208", AccountType.CREDIT_CARD, 0)
    private val carro = Account("c", "Vehículo 8761", AccountType.LOAN, 0)
    private val carroEnDolares = Account("cu", "Crédito USD", AccountType.LOAN, 0, currency = "USD")
    private val todas = listOf(ahorros, fidu, masterBlack, amex, carro)

    private fun leido(tipo: TransactionType, categoria: String, monto: Double = 386_902.0) =
        ParsedSms(monto, "Pago de tarjeta", tipo, categoria)

    @Test
    fun `el pago a la tarjeta que nombra el mensaje sale de Ahorros y entra a la tarjeta`() {
        val texto = "Bancolombia: Pagaste \$386.902 en la tarjeta de credito *3684 desde la cuenta *8133"
        val resuelta = resolverCuentaDelBanco(todas, UsoDeCuenta.ORIGEN_DE_GASTO, "85784", textoDelMensaje = texto)
        assertEquals(ahorros, resuelta.cuenta)
        val deuda = assertNotNull(deudaPropuestaDelAviso(resuelta, null, todas))
        val propuestas = assertNotNull(dosPatasPropuestas(leido(TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY), CARD_PAYMENT_CATEGORY, ahorros, deuda, null))
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, propuestas.operacion)
        assertEquals("Sale de Bancolombia Ahorros 8133 · entra a Master Black 3684 como pago", propuestas.resumen)
    }

    @Test
    fun `la cuota con su credito, y lo decide el tipo de la deuda`() {
        val cuota = leido(TransactionType.EXPENSE, CUOTA_CATEGORY, 4_178_163.0)
        assertEquals(OperacionDelAviso.CUOTA, dosPatasPropuestas(cuota, CUOTA_CATEGORY, ahorros, carro, null)?.operacion)
        // «Cuota de crédito» con una tarjeta elegida es un pago de tarjeta, como en la Ola Y.
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, dosPatasPropuestas(cuota, CUOTA_CATEGORY, ahorros, amex, null)?.operacion)
        assertNull(dosPatasPropuestas(cuota, CUOTA_CATEGORY, ahorros, carroEnDolares, null), "una cuota entre monedas no se reparte inventando")
        assertNull(dosPatasPropuestas(cuota, CUOTA_CATEGORY, ahorros, null, null), "sin deuda elegida, un gasto suelto como siempre")
        assertNull(dosPatasPropuestas(cuota, "Comida", ahorros, carro, null), "otra categoría, un gasto suelto")
    }

    @Test
    fun `el retiro de la Fiducuenta hacia la cuenta propia es un traspaso`() {
        val texto = "Bancolombia: Retiraste \$4.200.000 de tu cuenta *9586 Fiducuenta hacia la cuenta *02955068133"
        val resuelta = resolverCuentaDelBanco(todas, UsoDeCuenta.ORIGEN_DE_GASTO, "85540", textoDelMensaje = texto)
        assertEquals(fidu, resuelta.cuenta)
        val leidoDelRetiro = leido(TransactionType.EXPENSE, "Otros", 4_200_000.0)
        val hacia = assertNotNull(destinoPropuestoDelAviso(leidoDelRetiro, resuelta, todas))
        assertEquals(ahorros, hacia)
        assertTrue(pideLaCuentaDeDestino(leidoDelRetiro, TRANSFER_CATEGORY, fidu))
        val propuestas = assertNotNull(dosPatasPropuestas(leidoDelRetiro, TRANSFER_CATEGORY, fidu, null, hacia))
        assertEquals("Sale de Fiducuenta 9586 · entra a Bancolombia Ahorros 8133 como traspaso", propuestas.resumen)
        assertNull(dosPatasPropuestas(leidoDelRetiro, TRANSFER_CATEGORY, fidu, null, null), "sin destino no hay traspaso")
    }

    @Test
    fun `el avance cae en la tarjeta y pide la cuenta a la que entro`() {
        val texto = "Bancolombia: Hiciste un avance de \$6,200,000 en tu SUC VIRTUAL desde tu T.Credito *9208 a la cuenta *8133."
        val avance = leido(TransactionType.INCOME, AVANCE_DE_TARJETA_CATEGORY, 6_200_000.0)
        val resuelta = resolverCuentaDelBanco(todas, UsoDeCuenta.DESTINO_DE_INGRESO, "Correo · Bancolombia", textoDelMensaje = texto)
        assertEquals(amex, resuelta.cuenta, "por el número, la tarjeta de donde salió")
        assertTrue(esUnAvanceDeLaTarjeta(avance, AVANCE_DE_TARJETA_CATEGORY, amex))
        assertTrue(pideLaCuentaDeDestino(avance, AVANCE_DE_TARJETA_CATEGORY, amex))
        val hacia = assertNotNull(destinoPropuestoDelAviso(avance, resuelta, todas))
        assertEquals(ahorros, hacia)
        val propuestas = assertNotNull(dosPatasPropuestas(avance, AVANCE_DE_TARJETA_CATEGORY, amex, null, hacia))
        assertEquals(OperacionDelAviso.AVANCE, propuestas.operacion)
        assertEquals("Sale de AMEX 9208 como avance · entra a Bancolombia Ahorros 8133", propuestas.resumen)
        assertNull(dosPatasPropuestas(avance, AVANCE_DE_TARJETA_CATEGORY, amex, null, null), "sin la cuenta no se confirma")
        // En una cuenta de dinero (la tarjeta sin número en el nombre) es el ingreso suelto de siempre.
        assertFalse(esUnAvanceDeLaTarjeta(avance, AVANCE_DE_TARJETA_CATEGORY, ahorros))
    }

    @Test
    fun `como destino solo cuentas suyas de dinero o inversion, distintas del origen`() {
        assertEquals(listOf(ahorros), destinosElegibles(todas, fidu))
    }
}
