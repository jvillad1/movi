package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.dashboard.DashboardData
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Vuelta 56 · `cuotaPrevistaParaElDetalle`: con lo que el Inicio ya leyó, ¿el detalle guarda el lugar de
 * «¿Es la cuota de X?» mientras pide los vencimientos? Es la misma cuenta pura de la sugerencia
 * (`cuotaQueSeParece`): solo un gasto que se le parece a una cuota reserva; sin nada leído, nada.
 */
class LaCuotaPrevistaTest {

    private val ahorros = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 5_000_000L, "COP")
    private val libre = Account("acc-libre", "Libre inversión 9695", AccountType.LOAN, 40_710_555L, "COP")
    private val cuentas = listOf(ahorros, libre)

    private fun gasto(monto: Long, categoria: String = "Otros", fecha: String = "2026-10-02T10:00:00") = FinancialEvent(
        id = "ev",
        accountId = ahorros.id,
        type = TransactionType.EXPENSE,
        amount = monto,
        category = categoria,
        description = "Transferencia",
        timestamp = LocalDateTime.parse(fecha).toInstant(AppTimeZone.zone).toEpochMilliseconds(),
    )

    private val regla = RecurringRule(
        id = "$CREDIT_RULE_PREFIX${libre.id}", name = "Cuota Libre inversión 9695", category = CUOTA_CATEGORY,
        amount = 980_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
    )

    private fun leido(vence: String, ocurrencias: List<OccurrenceState> = emptyList()) = DashboardData(
        upcoming = listOf(UpcomingPayment(rule = regla, dueDate = vence, daysUntil = 3, status = PaymentStatus.DUE_SOON)),
        ocurrencias = ocurrencias,
        ajustesDePeriodo = PeriodSettings(),
    )

    @Test
    fun sinNadaLeidoNoSeReserva() {
        assertNull(cuotaPrevistaParaElDetalle(gasto(980_000L), cuentas, null))
        assertNull(cuotaPrevistaParaElDetalle(gasto(980_000L), cuentas, DashboardData()))
    }

    @Test
    fun unGastoQueSePareceReservaConSuCredito() {
        assertEquals(libre.id, cuotaPrevistaParaElDetalle(gasto(980_000L), cuentas, leido("2026-10-05"))?.creditoId)
        // Dentro del 10 %.
        assertEquals(libre.id, cuotaPrevistaParaElDetalle(gasto(1_050_000L), cuentas, leido("2026-10-05"))?.creditoId)
    }

    @Test
    fun elMontoLaCategoriaYElPeriodoMandan() {
        assertNull(cuotaPrevistaParaElDetalle(gasto(50_000L), cuentas, leido("2026-10-05")), "otro monto")
        assertNull(cuotaPrevistaParaElDetalle(gasto(980_000L, TRANSFER_CATEGORY), cuentas, leido("2026-10-05")), "una categoría de Movi")
        assertNull(cuotaPrevistaParaElDetalle(gasto(980_000L), cuentas, leido("2026-11-05")), "la de octubre ya rodó a noviembre")
    }

    @Test
    fun laCuotaVencidaQueDiceOccurrencesTambienReserva() {
        // `upcoming` ya rodó a noviembre, pero la de octubre sigue pendiente en `occurrences`.
        val pendiente = OccurrenceState(ruleId = regla.id, period = "2026-10", dueDate = "2026-10-05", occurred = false)
        assertEquals(libre.id, cuotaPrevistaParaElDetalle(gasto(980_000L), cuentas, leido("2026-11-05", listOf(pendiente)))?.creditoId)
    }
}
