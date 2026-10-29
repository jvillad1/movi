package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * # El detalle de una pata dice lo que es (vuelta 57)
 *
 * La revisión de la vuelta 56 abrió la cuota del Vehículo 4083 y leyó «Un traspaso no se puede
 * recategorizar: … no un gasto ni un ingreso», cuando la cuota cuenta como gasto desde la vuelta 50. El
 * texto va por tipo: cuota, desembolso, y el de traspaso solo para traspasos, pagos de tarjeta y abonos.
 */
class LaPataQueNoSeRecategorizaTest {

    private val ahorros = Account("a4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 0)
    private val vehiculo = Account("v4083", "Vehículo 4083", AccountType.LOAN, 0)
    private val vehiculoViejo = Account("v40", "Vehículo 40", AccountType.LOAN, 0)
    private val masterBlack = Account("m", "Master Black 3684", AccountType.CREDIT_CARD, 0)
    private val cuentas = listOf(ahorros, vehiculo, vehiculoViejo, masterBlack)

    private fun pata(cuenta: Account, categoria: String, descripcion: String, tipo: TransactionType = TransactionType.EXPENSE) =
        FinancialEvent(
            id = "ev", accountId = cuenta.id, type = tipo, amount = 980_000L, category = categoria,
            description = descripcion, timestamp = 0L, transferId = "tr",
        )

    @Test
    fun `la cuota dice que es la cuota de su credito y que cuenta como gasto`() {
        val dinero = pata(ahorros, CUOTA_CATEGORY, "Cuota de Vehículo 4083 · Pago Occidente")
        assertEquals(
            "Es la cuota de Vehículo 4083: cuenta como gasto. Para cambiarla, anúlala y vuelve a anotarla.",
            porQueLaPataNoSeRecategoriza(dinero, cuentas),
        )
        // La pata del crédito dice lo mismo, con su cuenta.
        val deuda = pata(vehiculo, CUOTA_CATEGORY, "Abono a capital desde Bancolombia Ahorros 4057", TransactionType.INCOME)
        assertEquals(
            "Es la cuota de Vehículo 4083: cuenta como gasto. Para cambiarla, anúlala y vuelve a anotarla.",
            porQueLaPataNoSeRecategoriza(deuda, cuentas),
        )
        // Con el concepto cambiado, sin nombre.
        assertEquals(
            "Es la cuota de un crédito: cuenta como gasto. Para cambiarla, anúlala y vuelve a anotarla.",
            porQueLaPataNoSeRecategoriza(pata(ahorros, CUOTA_CATEGORY, "Carro octubre"), cuentas),
        )
        assertFalse(porQueLaPataNoSeRecategoriza(dinero, cuentas).contains("traspaso"))
    }

    @Test
    fun `el traspaso, el pago de tarjeta y el abono siguen con el texto del traspaso`() {
        assertEquals(TRANSFER_RECATEGORIZE_BLOCKED, porQueLaPataNoSeRecategoriza(pata(ahorros, TRANSFER_CATEGORY, "A Nequi"), cuentas))
        assertEquals(
            TRANSFER_RECATEGORIZE_BLOCKED,
            porQueLaPataNoSeRecategoriza(pata(ahorros, CARD_PAYMENT_CATEGORY, "Pago de Master Black 3684"), cuentas),
        )
        // El abono extra a un crédito es un traspaso puro.
        assertEquals(
            TRANSFER_RECATEGORIZE_BLOCKED,
            porQueLaPataNoSeRecategoriza(pata(ahorros, TRANSFER_CATEGORY, "Abono a Vehículo 4083"), cuentas),
        )
    }

    @Test
    fun `el desembolso dice lo suyo`() {
        assertEquals(
            DESEMBOLSO_RECATEGORIZE_BLOCKED,
            porQueLaPataNoSeRecategoriza(pata(ahorros, DESEMBOLSO_CATEGORY, "Desembolso", TransactionType.INCOME), cuentas),
        )
    }
}
