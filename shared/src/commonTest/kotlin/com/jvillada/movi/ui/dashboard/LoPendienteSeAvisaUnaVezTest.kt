package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.ui.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta de diseño 28: lo pendiente se avisa una vez en Hoy
 *
 * Con «3 por revisar», Hoy lo decía tres veces: el punto rojo de la campana, la insignia «3» de la
 * acción «Avisos» y la tarjeta «3 por revisar» de «Movi propone hoy». La insignia se queda (es el
 * contador); la tarjeta sale (una propuesta es una decisión con contenido, no un número); y el punto
 * de la campana se prende solo con algo vencido o urgente.
 */
class LoPendienteSeAvisaUnaVezTest {

    private val octubre = PeriodoFinanciero(2026, 10)

    private fun pago(nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        rule = RecurringRule(id = "rr_$nombre", name = nombre, category = "Servicios", amount = monto, dayOfMonth = 27 + dias, type = TransactionType.EXPENSE),
        dueDate = "2026-10-${(27 + dias).toString().padStart(2, '0')}",
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    /** Hoy, 27-oct: 2 avisos y 1 movimiento por revisar, y nada vencido. */
    private val tresPorRevisar = DashboardData(
        pendingSms = 2,
        movimientosPorConfirmar = 1,
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        periodoActual = octubre,
        ajustesDePeriodo = PeriodSettings(),
    )

    @Test
    fun `con 3 por revisar y nada vencido, la tarjeta no esta y la campana no tiene punto`() {
        // La insignia de «Avisos» sigue contando los tres.
        assertEquals(3, pendientesPorRevisar(tresPorRevisar))
        // «Movi propone hoy» no los repite como tarjeta.
        assertTrue(propuestasDelInicio(tresPorRevisar).isEmpty())
        // La campana los tiene en su bandeja, pero sin punto.
        val campana = notificationRows(tresPorRevisar)
        assertEquals(listOf(Screen.PorRevisar), campana.map { it.target })
        assertFalse(campanaConPunto(campana))
    }

    @Test
    fun `un pago de la semana tampoco prende el punto`() {
        val data = tresPorRevisar.copy(upcoming = listOf(pago("Celular", 53_000, 3)))
        assertFalse(campanaConPunto(notificationRows(data)))
    }

    @Test
    fun `un pago vencido prende el punto`() {
        val data = tresPorRevisar.copy(upcoming = listOf(pago("Arriendo", 1_800_000, -1)))
        assertTrue(campanaConPunto(notificationRows(data)))
    }

    @Test
    fun `un pago que vence hoy prende el punto`() {
        val data = tresPorRevisar.copy(upcoming = listOf(pago("Internet", 120_000, 0)))
        assertTrue(campanaConPunto(notificationRows(data)))
    }

    @Test
    fun `lo que el banco cobro solo prende el punto`() {
        val debito = DebitoAutomaticoPorConfirmar(
            ruleId = "cr_1", periodo = "2026-10", origen = OrigenDelDebito.CUOTA_DE_CREDITO, nombre = "Libre inversión 9695",
            monto = 1_204_064, vence = "2026-10-05", cuentaId = "a1", cuentaNombre = "Bancolombia Ahorros",
            categoria = "Cuota de crédito", pataDelDineroId = "p1",
        )
        assertTrue(campanaConPunto(notificationRows(tresPorRevisar.copy(debitosPorConfirmar = listOf(debito)))))
    }

    @Test
    fun `sin nada, ni punto ni bandeja`() {
        assertFalse(campanaConPunto(notificationRows(DashboardData())))
    }
}
