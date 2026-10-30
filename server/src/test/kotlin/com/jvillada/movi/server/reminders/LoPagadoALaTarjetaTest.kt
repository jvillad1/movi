package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **Vuelta 72 · lo pagado a la tarjeta en el período**: la suma que `faltaDelMinimo` compara con el
 * mínimo. Dos abonos de $50.000 son $100.000 (el `montoDelPago` de la fila sigue siendo el último, como
 * siempre); un pago de otro período no entra; una regla que no es de tarjeta no tiene esta cifra.
 */
class LoPagadoALaTarjetaTest {

    private val nu = "acc_nu"
    private val tarjeta = RecurringRule(
        "$CARD_RULE_PREFIX$nu", "Pago tarjeta Nu", CARD_PAYMENT_CATEGORY, 1_583_000, 5, TransactionType.EXPENSE,
        montoEsSaldo = true, pagoMinimoCop = 180_000,
    )

    private fun pago(id: String, monto: Long, dia: LocalDate) = FinancialEvent(
        id = id, accountId = nu, type = TransactionType.INCOME, amount = monto,
        category = CARD_PAYMENT_CATEGORY, description = "Pago Nu",
        timestamp = appDateToEpochMillis(dia) + 12 * 3_600_000L,
    )

    @Test
    fun `dos abonos del periodo se suman`() {
        val a = pago("ev_a", 50_000, LocalDate.of(2026, 10, 28))
        val b = pago("ev_b", 50_000, LocalDate.of(2026, 10, 29))
        val periodo = periodoQueSalda(tarjeta, LocalDate.of(2026, 10, 28), settings = PeriodSettings())
        assertEquals(periodo, periodoQueSalda(tarjeta, LocalDate.of(2026, 10, 29), settings = PeriodSettings()))
        assertEquals(100_000L, pagadoALaTarjetaEnElPeriodo(tarjeta, listOf(a, b), periodo, "COP"))
    }

    @Test
    fun `un pago de otro periodo no entra, y una regla comun no tiene cifra`() {
        val viejo = pago("ev_viejo", 70_000, LocalDate.of(2026, 8, 1))
        val nuevo = pago("ev_nuevo", 100_000, LocalDate.of(2026, 10, 28))
        val periodo = periodoQueSalda(tarjeta, LocalDate.of(2026, 10, 28), settings = PeriodSettings())
        assertEquals(100_000L, pagadoALaTarjetaEnElPeriodo(tarjeta, listOf(viejo, nuevo), periodo, "COP"))
        val comun = tarjeta.copy(id = "rr_gym")
        assertNull(pagadoALaTarjetaEnElPeriodo(comun, listOf(nuevo), periodo, "COP"))
    }
}
