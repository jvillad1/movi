package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.COLORES_OSCUROS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **El desembolso de un crédito se ve como lo que es: plata que entró a tu cuenta.** Es un par
 * (la pata de salida en la cuenta del crédito, la de entrada en Bancolombia) que no cuenta como
 * ingreso del mes, pero pinta en verde y con «+», con una nota que dice por qué no suma. Ningún
 * otro par cambia.
 */
class DesembolsoEnMovimientosTest {

    private val tipos = mapOf(
        "acc_credito" to AccountType.LOAN,
        "acc_ahorros" to AccountType.SAVINGS,
        "acc_cdt" to AccountType.INVESTMENT,
        "acc_tarjeta" to AccountType.CREDIT_CARD,
    )
    private val nombres = mapOf(
        "acc_credito" to "Crédito Techo Gardenera",
        "acc_ahorros" to "Bancolombia Ahorros",
        "acc_cdt" to "CDT",
    )

    private fun pata(id: String, cuenta: String, tipo: TransactionType, categoria: String, monto: Long) = FinancialEvent(
        id = id,
        accountId = cuenta,
        type = tipo,
        amount = monto,
        category = categoria,
        description = id,
        timestamp = 0L,
        transferId = "tr",
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = false,
    )

    private fun par(desde: String, hacia: String, categoria: String = TRANSFER_CATEGORY, monto: Long = 10_000_000L) =
        MovementRow.Transfer(
            out = pata("out", desde, TransactionType.EXPENSE, categoria, monto),
            into = pata("into", hacia, TransactionType.INCOME, categoria, monto),
        )

    private val desembolso = par("acc_credito", "acc_ahorros")

    @Test
    fun `un desembolso va en verde, con signo mas y su nota`() {
        assertEquals("Desembolso", transferRowTitle(desembolso, tipos))
        assertEquals(TonoDelMonto.INGRESO, tonoDelRenglon(desembolso, tipos))
        assertEquals(COLORES_CLAROS.entra, colorDelTono(tonoDelRenglon(desembolso, tipos), COLORES_CLAROS))
        assertEquals(COLORES_OSCUROS.entra, colorDelTono(tonoDelRenglon(desembolso, tipos), COLORES_OSCUROS))
        assertTrue(textoDelMontoDeTraspaso(desembolso, tipos).startsWith("+"), textoDelMontoDeTraspaso(desembolso, tipos))
        assertTrue(textoDelMontoDeTraspaso(desembolso, tipos).contains("10.000.000"))
        assertEquals("Crédito · plata prestada, no cuenta como ingreso", NOTA_DE_DESEMBOLSO)
        assertTrue(esDesembolso(desembolso, tipos))
        // El nombre del crédito sigue a la vista en el «De X a Y».
        assertEquals("De Crédito Techo Gardenera a Bancolombia Ahorros", transferRowSubtitle(desembolso, nombres))
    }

    @Test
    fun `sin los tipos de cuenta todavia, el desembolso se queda neutro como siempre`() {
        assertEquals("Traspaso", transferRowTitle(desembolso, emptyMap()))
        assertEquals(TonoDelMonto.ENTRE_CUENTAS, tonoDelRenglon(desembolso))
        assertEquals(TonoDelMonto.ENTRE_CUENTAS, tonoDelRenglon(desembolso, emptyMap()))
        assertNotEquals('+', textoDelMontoDeTraspaso(desembolso, emptyMap()).first())
        assertTrue(!esDesembolso(desembolso, emptyMap()))
    }

    @Test
    fun `la cuota, el pago de tarjeta, el abono extraordinario y el traspaso entre cuentas siguen neutros`() {
        val cuota = par("acc_ahorros", "acc_credito", CUOTA_CATEGORY, 4_215_223L)
        val tarjeta = par("acc_ahorros", "acc_tarjeta", CARD_PAYMENT_CATEGORY, 1_000_000L)
        val abono = par("acc_ahorros", "acc_credito", monto = 2_000_000L)
        val entreAhorros = par("acc_ahorros", "acc_cdt", monto = 5_000_000L)
        // La pata de salida en un crédito pero con la categoría de cuota: no es un desembolso.
        val cuotaDesdeElCredito = par("acc_credito", "acc_ahorros", CUOTA_CATEGORY)

        for (fila in listOf(cuota, tarjeta, abono, entreAhorros, cuotaDesdeElCredito)) {
            assertEquals(TonoDelMonto.ENTRE_CUENTAS, tonoDelRenglon(fila, tipos), transferRowTitle(fila, tipos))
            assertNotEquals('+', textoDelMontoDeTraspaso(fila, tipos).first())
            assertTrue(!esDesembolso(fila, tipos))
        }
        assertEquals("Cuota de crédito", transferRowTitle(cuota, tipos))
        assertEquals("Pago de tarjeta", transferRowTitle(tarjeta, tipos))
        assertEquals("Abono extraordinario", transferRowTitle(abono, tipos))
        assertEquals("Traspaso", transferRowTitle(entreAhorros, tipos))
    }
}
