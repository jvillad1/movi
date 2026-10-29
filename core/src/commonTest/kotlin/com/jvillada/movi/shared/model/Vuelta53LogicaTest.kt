package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 53, lo puro de `:core`
 *
 * - «¿Es la cuota de Vehículo 4083?»: cuándo un gasto se parece a la cuota de un crédito sin saldar
 *   ([cuotaQueSeParece]).
 * - El 409 de la segunda cuota del mismo período ([CuotaYaPagada], [cuotaYaPagadaDe]).
 */
class Vuelta53LogicaTest {

    private fun ms(iso: String) = LocalDateTime.parse(iso).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private val vehiculo = Account("acc-vehiculo", "Vehículo 4083", AccountType.LOAN, 177_200_000L, "COP")
    private val libre = Account("acc-libre", "Libre inversión 9695", AccountType.LOAN, 40_710_555L, "COP")
    private val ahorros = Account("acc-ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 5_000_000L, "COP")

    private fun vence(cuenta: Account, cuota: Long, dueDate: String) = UpcomingPayment(
        rule = RecurringRule(
            id = "$CREDIT_RULE_PREFIX${cuenta.id}", name = "Cuota ${cuenta.name}", category = CUOTA_CATEGORY,
            amount = cuota, dayOfMonth = dueDate.takeLast(2).toInt(), type = TransactionType.EXPENSE,
        ),
        dueDate = dueDate,
        daysUntil = 0,
        status = PaymentStatus.DUE_SOON,
    )

    private fun gasto(monto: Long, fecha: String, categoria: String = "Otros", transferId: String? = null) = FinancialEvent(
        id = "ev-1", accountId = ahorros.id, type = TransactionType.EXPENSE, amount = monto,
        category = categoria, description = "Transferencia Bancolombia", timestamp = ms("${fecha}T10:00:00"),
        transferId = transferId,
    )

    private val corte25 = PeriodSettings(cutoffDay = 25)

    // ── 2 · La sugerencia ────────────────────────────────────────────────────────

    @Test
    fun `un gasto a mas o menos diez por ciento de la cuota en el periodo de su vencimiento se sugiere`() {
        val cuotas = cuotasPorSaldar(listOf(vence(vehiculo, 4_215_223L, "2026-10-05")), listOf(ahorros, vehiculo))
        // Corte 25: el 30 de septiembre ya es el período de octubre, el del vencimiento del 5.
        val sugerida = cuotaQueSeParece(gasto(4_215_223L, "2026-09-30"), cuotas, corte25)
        assertEquals("acc-vehiculo", sugerida?.creditoId)
        assertEquals("¿Es la cuota de Vehículo 4083?", preguntaDeLaCuotaQueSeParece(sugerida!!))
        // Los bordes del 10 %, de los dos lados.
        assertEquals("acc-vehiculo", cuotaQueSeParece(gasto(3_793_701L, "2026-10-02"), cuotas, corte25)?.creditoId)
        assertEquals("acc-vehiculo", cuotaQueSeParece(gasto(4_636_745L, "2026-10-02"), cuotas, corte25)?.creditoId)
        assertNull(cuotaQueSeParece(gasto(3_793_000L, "2026-10-02"), cuotas, corte25), "pasa del 10 % hacia abajo")
        assertNull(cuotaQueSeParece(gasto(4_637_000L, "2026-10-02"), cuotas, corte25), "pasa del 10 % hacia arriba")
    }

    @Test
    fun `fuera del periodo del vencimiento sin saldar no se sugiere`() {
        // La de octubre ya se saldó: el vigente rodó al 5 de noviembre (período de noviembre, del 25-oct).
        val cuotas = cuotasPorSaldar(listOf(vence(vehiculo, 4_215_223L, "2026-11-05")), listOf(vehiculo))
        assertNull(cuotaQueSeParece(gasto(4_215_223L, "2026-10-10"), cuotas, corte25))
        assertEquals("acc-vehiculo", cuotaQueSeParece(gasto(4_215_223L, "2026-10-26"), cuotas, corte25)?.creditoId)
    }

    @Test
    fun `si se parece a dos creditos no sugiere ninguno`() {
        val cuotas = cuotasPorSaldar(
            listOf(vence(vehiculo, 1_000_000L, "2026-10-05"), vence(libre, 980_000L, "2026-10-15")),
            listOf(vehiculo, libre),
        )
        assertNull(cuotaQueSeParece(gasto(990_000L, "2026-10-01"), cuotas, corte25))
        // Con uno solo cerca, ese.
        assertEquals("acc-libre", cuotaQueSeParece(gasto(885_000L, "2026-10-01"), cuotas, corte25)?.creditoId)
    }

    @Test
    fun `una cuota un pago de tarjeta o un traspaso no se sugieren y la palabra sola no alcanza`() {
        val cuotas = cuotasPorSaldar(listOf(vence(vehiculo, 4_215_223L, "2026-10-05")), listOf(vehiculo))
        assertNull(cuotaQueSeParece(gasto(4_215_223L, "2026-10-01", categoria = CUOTA_CATEGORY), cuotas, corte25))
        assertNull(cuotaQueSeParece(gasto(4_215_223L, "2026-10-01", categoria = CARD_PAYMENT_CATEGORY), cuotas, corte25))
        assertNull(cuotaQueSeParece(gasto(4_215_223L, "2026-10-01", transferId = "tr-1"), cuotas, corte25))
        // «Cuota de manejo» dice «cuota» y no se parece en el monto: nada.
        val manejo = gasto(32_000L, "2026-10-01").copy(description = "Cuota de manejo crédito")
        assertNull(cuotaQueSeParece(manejo, cuotas, corte25))
    }

    @Test
    fun `solo cuentan las reglas de credito de un credito que existe y en su moneda`() {
        val tarjeta = UpcomingPayment(
            rule = RecurringRule(id = "card_nu", name = "Nu", category = CARD_PAYMENT_CATEGORY, amount = 4_215_223L, dayOfMonth = 5, type = TransactionType.EXPENSE),
            dueDate = "2026-10-05", daysUntil = 0, status = PaymentStatus.DUE_SOON,
        )
        val deOtro = vence(libre, 4_215_223L, "2026-10-05")
        assertEquals(emptyList(), cuotasPorSaldar(listOf(tarjeta, deOtro), listOf(vehiculo)))
        val enDolares = cuotasPorSaldar(listOf(vence(vehiculo, 1_000L, "2026-10-05")), listOf(vehiculo.copy(currency = "USD")))
        assertNull(cuotaQueSeParece(gasto(1_000L, "2026-10-01"), enDolares, corte25), "un gasto en pesos no es una cuota en dólares")
    }

    // ── 3 · El 409 de la segunda cuota ───────────────────────────────────────────

    @Test
    fun `la frase dice el mes de la cuota y el credito`() {
        assertEquals("La cuota de octubre de Vehículo 4083 ya está pagada.", fraseDeLaCuotaYaPagada("Vehículo 4083", "2026-10"))
        assertEquals("La cuota de este mes de Vehículo 4083 ya está pagada.", fraseDeLaCuotaYaPagada("Vehículo 4083", "otra cosa"))
    }

    @Test
    fun `el cliente reconoce el 409 por su codigo y solo ese`() {
        val cuerpo = """{"codigo":"CUOTA_YA_PAGADA","periodo":"2026-10","credito":"Vehículo 4083","mensaje":"La cuota de octubre de Vehículo 4083 ya está pagada.","otro":1}"""
        assertEquals(
            CuotaYaPagada(periodo = "2026-10", credito = "Vehículo 4083", mensaje = "La cuota de octubre de Vehículo 4083 ya está pagada."),
            cuotaYaPagadaDe(409, cuerpo),
        )
        assertNull(cuotaYaPagadaDe(422, cuerpo), "otro estado")
        assertNull(cuotaYaPagadaDe(409, "Ese id ya existe"), "un 409 de texto")
        assertNull(cuotaYaPagadaDe(409, cuerpo.replace("CUOTA_YA_PAGADA", "OTRO")), "otro código")
    }
}
