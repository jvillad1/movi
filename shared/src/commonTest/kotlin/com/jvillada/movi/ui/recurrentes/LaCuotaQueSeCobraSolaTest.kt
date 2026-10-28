package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.epochDeFecha
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.quickadd.creditoSinSaldoEnMovi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 49 · la cuota que el banco cobra sola, en la lista y en la hoja de Cuota
 *
 * Cuenta igual que cualquier pago; lo que cambia es lo que dice: «Se cobra sola el 5» antes de su día,
 * «¿Se cobró?» desde ese día. Y la hoja de Cuota nunca muestra una deuda negativa.
 */
class LaCuotaQueSeCobraSolaTest {

    private fun cuota(dias: Int, vence: String, seCobraSola: Boolean = true) = PagoDelPeriodo(
        ruleId = "credit_acc_carro_demo", nombre = "Cuota Vehículo 4083", monto = 980_000, pagado = false,
        diasParaVencer = dias, vence = vence, seCobraSola = seCobraSola,
    )

    @Test
    fun `antes de su dia se cobra sola, y desde ese dia pregunta`() {
        assertEquals("Se cobra sola el 5" to TonoDePildora.Neutra, pastillaDeVencimiento(cuota(8, "2026-11-05"), conPropuesta = false))
        assertEquals("Se cobra sola el 5" to TonoDePildora.Aviso, pastillaDeVencimiento(cuota(2, "2026-11-05"), conPropuesta = false))
        assertEquals("¿Se cobró?" to TonoDePildora.Aviso, pastillaDeVencimiento(cuota(0, "2026-10-05"), conPropuesta = false))
        assertEquals("¿Se cobró?" to TonoDePildora.Aviso, pastillaDeVencimiento(cuota(-23, "2026-10-05"), conPropuesta = false))
        assertEquals("¿Se cobró?" to TonoDePildora.Aviso, pildoraDeLaFila(cuota(-23, "2026-10-05"), conPropuesta = false))
        // Sin débito automático, lo de siempre.
        assertEquals("Venció hace 23 días" to TonoDePildora.Aviso, pastillaDeVencimiento(cuota(-23, "2026-10-05", seCobraSola = false), conPropuesta = false))
    }

    @Test
    fun `la frase de la fila sin movimiento dice que el banco no avisa`() {
        assertEquals(TEXTO_SIN_MOVIMIENTO_DEL_DEBITO, textoSinMovimiento(cuota(-23, "2026-10-05")))
        assertEquals(TEXTO_SIN_MOVIMIENTO, textoSinMovimiento(cuota(-23, "2026-10-05", seCobraSola = false)))
    }

    @Test
    fun `la fila sabe que se cobra sola por la regla, y solo un gasto`() {
        val ajustes = PeriodSettings()
        val periodo = periodoDe(epochDeFecha("2026-10-28"), ajustes)
        fun regla(seDebitaSolo: Boolean?, tipo: TransactionType = TransactionType.EXPENSE) = RecurringRule(
            id = "credit_acc_carro_demo", name = "Cuota Vehículo 4083", category = "Cuota de crédito", amount = 980_000,
            dayOfMonth = 5, type = tipo, seDebitaSolo = seDebitaSolo,
        )
        fun fila(rule: RecurringRule) = checklistDelPeriodo(
            upcoming = listOf(UpcomingPayment(rule = rule, dueDate = "2026-11-05", daysUntil = 8, status = PaymentStatus.UPCOMING)),
            ocurrencias = listOf(
                OccurrenceState(ruleId = rule.id, period = "2026-10", dueDate = "2026-10-05", occurred = false, periodoDelDueno = "2026-10"),
            ),
            periodo = periodo,
            settings = ajustes,
        ).single()
        assertTrue(fila(regla(true)).seCobraSola)
        assertFalse(fila(regla(null)).seCobraSola)
        assertFalse(fila(regla(true, TransactionType.INCOME)).seCobraSola)
        // Vencida y sin pago: pendiente, cuenta en lo que falta.
        assertEquals(-23, fila(regla(true)).diasParaVencer)
        assertFalse(fila(regla(true)).pagado)
    }

    @Test
    fun `la hoja de Cuota nunca muestra una deuda negativa`() {
        val sinMovimientos = Account("acc_carro_demo", "Vehículo 4083", AccountType.LOAN, 0)
        val conDeuda = Account("acc_carro_demo", "Vehículo 4083", AccountType.LOAN, 45_000_000)
        val pocaDeuda = Account("acc_carro_demo", "Vehículo 4083", AccountType.LOAN, 300_000)
        val tarjeta = Account("nu", "Nu", AccountType.CREDIT_CARD, 0)
        assertTrue(creditoSinSaldoEnMovi(sinMovimientos, bajaLaDeuda = null), "en $0, Movi no sabe cuánto se debe")
        assertTrue(creditoSinSaldoEnMovi(sinMovimientos, bajaLaDeuda = 980_000))
        assertTrue(creditoSinSaldoEnMovi(pocaDeuda, bajaLaDeuda = 500_000), "el pago la dejaría negativa")
        assertFalse(creditoSinSaldoEnMovi(pocaDeuda, bajaLaDeuda = 300_000), "la última cuota la deja en cero, no negativa")
        assertFalse(creditoSinSaldoEnMovi(conDeuda, bajaLaDeuda = 980_000))
        assertFalse(creditoSinSaldoEnMovi(tarjeta, bajaLaDeuda = 500_000), "una tarjeta a favor es un dato real")
        assertFalse(creditoSinSaldoEnMovi(null, bajaLaDeuda = 1))
    }
}
