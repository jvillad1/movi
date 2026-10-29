package com.jvillada.movi.server.routes

import com.jvillada.movi.server.ai.lineaDeLasDeudasSinSaldo
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.server.reminders.periodosSaldados
import com.jvillada.movi.server.reminders.virtualRuleFor
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.PeriodSettings
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
            id = "ev_$fecha$categoria$tipo", accountId = "acc_v", type = tipo, amount = 500_000L,
            category = categoria, description = "Cuota", timestamp = appDateToEpochMillis(fecha) + 12 * 3_600_000L,
        )

    private fun terminos(dia: Int, desembolso: String = "2026-01-10", porNomina: Boolean = false) = CreditTerms(
        accountId = "acc_v", bank = "Banco", principal = 40_000_000L, rateEa = 18.0, termMonths = 60,
        installment = 980_000L, dayOfMonth = dia, startDate = desembolso, payrollDeduction = porNomina,
    )

    private fun pagadas(eventos: List<FinancialEvent>, dia: Int = 5, periodo: PeriodSettings = PeriodSettings(), porNomina: Boolean = false) =
        cuotasPagadasAlrededorDe(eventos, terminos(dia, porNomina = porNomina), "Vehículo 4083", hoy, periodo)

    /**
     * **El caso de la revisión de la vuelta 59.** El pago del 28 de octubre (día 5) salda **octubre**
     * —es lo que dicen `/api/payments/occurrences` y Pagos: un pago salda la cuota del período en que se
     * hizo (#416)—. La primera versión lo sellaba «noviembre» con `cuotaMasCercana` y la última cuota
     * del Vehículo 4083 salía en diciembre de 2030 en vez de enero de 2031.
     */
    @Test
    fun `el pago del 28 de octubre salda octubre como en las ocurrencias`() {
        val eventos = listOf(pago(LocalDate.of(2026, 9, 5)), pago(LocalDate.of(2026, 10, 28)))
        assertEquals(listOf("2026-09", "2026-10"), pagadas(eventos))
        val regla = virtualRuleFor(terminos(5), "Vehículo 4083")
        assertEquals(
            periodosSaldados(listOf(regla), eventos)[regla.id].orEmpty().sorted(),
            pagadas(eventos),
            "la misma fuente que las ocurrencias",
        )
    }

    @Test
    fun `solo la pata que baja la deuda con la categoria de la cuota salda`() {
        val eventos = listOf(
            pago(LocalDate.of(2026, 9, 4)),
            // La pata del dinero no salda nada en la cuenta del crédito.
            pago(LocalDate.of(2026, 10, 5), tipo = TransactionType.EXPENSE),
            // Un ajuste de saldo no paga una cuota.
            pago(LocalDate.of(2026, 8, 5), categoria = ADJUSTMENT_CATEGORY),
        )
        assertEquals(listOf("2026-09"), pagadas(eventos))
    }

    @Test
    fun `el descuento de nomina tambien paga la cuota y lo muy viejo no viaja`() {
        val eventos = listOf(
            pago(LocalDate.of(2026, 10, 30), categoria = PAYROLL_DEDUCTION_CATEGORY),
            pago(LocalDate.of(2024, 1, 30), categoria = PAYROLL_DEDUCTION_CATEGORY),
        )
        assertEquals(listOf("2026-10"), pagadas(eventos, dia = 30, porNomina = true))
    }

    @Test
    fun `sin pagos la lista va vacia, no nula`() {
        assertTrue(pagadas(emptyList()).isEmpty())
    }

    @Test
    fun `el saldo se fija con la apertura o el ultimo ajuste`() {
        val apertura = pago(LocalDate.of(2026, 10, 29), categoria = OPENING_CATEGORY)
        val ajuste = pago(LocalDate.of(2026, 10, 20), categoria = ADJUSTMENT_CATEGORY)
        assertEquals(apertura.timestamp, saldoFijadoEn(listOf(ajuste, apertura, pago(LocalDate.of(2026, 10, 30)))))
        assertNull(saldoFijadoEn(listOf(pago(LocalDate.of(2026, 10, 5)))))
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
