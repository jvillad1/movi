package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 57 · la fila de Pagos ofrece el gasto que se parece a la cuota
 *
 * La cuota pendiente del Vehículo 4083 solo decía «¿Se cobró?» aunque el detalle del gasto de $980.000
 * en «Otros» sugería «¿Es la cuota de Vehículo 4083?». [gastosParecidosALasCuotas] es la misma regla
 * ([cuotaQueSeParece]) mirada desde la cuota.
 */
class Vuelta57LaCuotaEnPagosTest {

    private fun ms(iso: String) = LocalDateTime.parse(iso).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private val vehiculo = Account("acc-vehiculo", "Vehículo 4083", AccountType.LOAN, 40_000_000L, "COP")
    private val libre = Account("acc-libre", "Libre inversión 9695", AccountType.LOAN, 40_000_000L, "COP")
    private val ahorros = Account("acc-ahorros", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 5_000_000L, "COP")

    private fun vence(cuenta: Account, cuota: Long, dueDate: String) = UpcomingPayment(
        rule = RecurringRule(
            id = "$CREDIT_RULE_PREFIX${cuenta.id}", name = "Cuota ${cuenta.name}", category = CUOTA_CATEGORY,
            amount = cuota, dayOfMonth = dueDate.takeLast(2).toInt(), type = TransactionType.EXPENSE,
        ),
        dueDate = dueDate,
        daysUntil = 0,
        status = PaymentStatus.DUE_SOON,
    )

    private fun gasto(id: String, monto: Long, fecha: String, categoria: String = "Otros") = FinancialEvent(
        id = id, accountId = ahorros.id, type = TransactionType.EXPENSE, amount = monto,
        category = categoria, description = "Banco de Occidente S A ATH", timestamp = ms("${fecha}T10:00:00"),
    )

    private val corte25 = PeriodSettings(cutoffDay = 25)

    @Test
    fun `la cuota pendiente encuentra el gasto parecido de su periodo, el mas cercano al monto`() {
        val cuotas = cuotasPorSaldar(
            listOf(vence(vehiculo, 980_000L, "2026-10-27"), vence(libre, 1_204_064L, "2026-10-05")),
            listOf(ahorros, vehiculo, libre),
        )
        val parecidos = gastosParecidosALasCuotas(
            listOf(
                gasto("ev-980", 980_000L, "2026-10-27"),
                gasto("ev-1000", 1_000_000L, "2026-10-20"),
                // El Carro de $260.000 no se parece a ninguna cuota.
                gasto("ev-260", 260_000L, "2026-10-26"),
                // Ya es una cuota: la regla del detalle no la sugiere.
                gasto("ev-cuota", 980_000L, "2026-10-27", categoria = CUOTA_CATEGORY),
            ),
            cuotas,
            corte25,
        )
        val delVehiculo = parecidos.getValue(claveDeLaCuotaPorSaldar(vehiculo.id, "2026-10-27"))
        assertEquals("ev-980", delVehiculo.gasto.id, "el de monto más cercano")
        assertEquals("Vehículo 4083", delVehiculo.cuota.nombre)
        assertEquals(1, parecidos.size, "nada para el Libre inversión: ningún gasto se le parece")
    }

    @Test
    fun `un gasto que se parece a dos cuotas no se ofrece en ninguna, como en el detalle`() {
        val cuotas = cuotasPorSaldar(
            listOf(vence(vehiculo, 980_000L, "2026-10-27"), vence(libre, 990_000L, "2026-10-28")),
            listOf(ahorros, vehiculo, libre),
        )
        assertTrue(gastosParecidosALasCuotas(listOf(gasto("ev-985", 985_000L, "2026-10-26")), cuotas, corte25).isEmpty())
    }
}
