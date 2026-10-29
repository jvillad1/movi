package com.jvillada.movi.server.routes

import com.jvillada.movi.server.ai.lineaDeLasDeudasSinSaldo
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PAYROLL_DEDUCTION_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Vuelta 59.** `GET /api/credits` manda las cuotas ya pagadas de cada crédito, para que la pantalla
 * cuente las que faltan desde la primera sin pagar («la última en noviembre de 2030», no octubre); y
 * Movi AI sabe de las deudas sin saldo para no contradecir la deuda total de Créditos.
 */
class LasCuotasPagadasDelCreditoTest {

    private val hoy = LocalDate.of(2026, 10, 29)

    private fun pago(fecha: LocalDate, categoria: String = CUOTA_CATEGORY, tipo: TransactionType = TransactionType.INCOME) =
        FinancialEvent(
            id = "ev_$fecha$categoria", accountId = "acc_v", type = tipo, amount = 500_000L,
            category = categoria, description = "Cuota", timestamp = appDateToEpochMillis(fecha) + 12 * 3_600_000L,
        )

    @Test
    fun `cada pago de una cuota sella la del vencimiento mas cercano`() {
        val eventos = listOf(
            pago(LocalDate.of(2026, 9, 4)),
            // La de octubre pagada en dos partes: una sola cuota.
            pago(LocalDate.of(2026, 10, 5)),
            pago(LocalDate.of(2026, 10, 6)),
            // La gasta la pata del dinero: no salda nada en la cuenta del crédito.
            pago(LocalDate.of(2026, 11, 5), tipo = TransactionType.EXPENSE),
            // Un ajuste de saldo no paga una cuota.
            pago(LocalDate.of(2026, 8, 5), categoria = "Ajuste de saldo"),
        )
        assertEquals(listOf("2026-09", "2026-10"), cuotasPagadasAlrededorDe(eventos, diaDePago = 5, hoy = hoy))
    }

    @Test
    fun `el descuento de nomina tambien paga la cuota y lo muy viejo no viaja`() {
        val eventos = listOf(
            pago(LocalDate.of(2026, 10, 30), categoria = PAYROLL_DEDUCTION_CATEGORY),
            pago(LocalDate.of(2024, 1, 30)),
        )
        assertEquals(listOf("2026-10"), cuotasPagadasAlrededorDe(eventos, diaDePago = 30, hoy = hoy))
    }

    @Test
    fun `sin pagos la lista va vacia, no nula`() {
        assertTrue(cuotasPagadasAlrededorDe(emptyList(), diaDePago = 5, hoy = hoy).isEmpty())
    }

    @Test
    fun `Movi AI nombra la deuda sin saldo y la deuda total que no la resta`() {
        val cuentas = listOf(
            Account("acc_nu", "Nu", AccountType.CREDIT_CARD, 1_583_000L, "COP"),
            Account("acc_v", "Vehículo 4083", AccountType.LOAN, -980_000L, "COP"),
        )
        val linea = lineaDeLasDeudasSinSaldo(cuentas) { "\$$it" }!!
        assertTrue("«Vehículo 4083» (\$980000 a favor por sus movimientos)" in linea, linea)
        assertTrue("La deuda total de Créditos no la resta (es \$1583000)" in linea, linea)
        assertNull(lineaDeLasDeudasSinSaldo(cuentas.take(1)) { "\$$it" })
    }
}
