package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

/**
 * **El caso que abrió la ola «una sola lista»**, con los datos del dueño (27-sep-2026, período de
 * octubre: del 24 de septiembre al 24 de octubre).
 *
 * Lo que vio: en la pantalla de OCTUBRE, «Ya ocurrieron» decía «Celular — Ya ocurrió en septiembre»
 * y «Cotrafa — Ya ocurrió en septiembre». Leyó que Movi daba por pagados Celular y Cotrafa de este
 * período, que todavía no pagó. Los datos estaban bien —septiembre sí se pagó, octubre no—; la
 * pantalla lo mezclaba.
 *
 * Lo comparten la prueba pura (`UnaSolaListaDelPeriodoTest`) y la que monta el tablero
 * (`UnaSolaListaEnElTableroTest`), para que las dos miren exactamente lo mismo. Hoy es el 27 de
 * septiembre: los `daysUntil` están contados desde ahí.
 */
object CasoDeOctubreDelDueno {

    /** Corte 25, con el período de octubre adelantado al 24 (su sueldo cayó un día antes). */
    val ajustes = PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-10" to "2026-09-24"))
    val octubre: PeriodoFinanciero = periodoDeLaFecha("2026-09-27", ajustes)!!

    private fun regla(id: String, nombre: String, monto: Long, dia: Int, categoria: String = "Servicios") =
        RecurringRule(
            id = id, name = nombre, category = categoria, amount = monto, dayOfMonth = dia,
            type = TransactionType.EXPENSE, accountId = "acc-banco",
        )

    val celular = regla("rr_celular", "Celular", 53_000, 22)
    val cotrafa = regla("rr_cotrafa", "Cotrafa", 410_000, 22, "Créditos")
    val crediagil = regla("${CREDIT_RULE_PREFIX}acc-crediagil", "Crediágil", 1_204_064, 5, "Créditos")
    val gimnasio = regla("rr_gym_caro", "Gimnasio Caro", 180_000, 25, "Salud")
    val mercado = regla("rr_mercado", "Mercado", 2_000_000, 26, "Comida")
    val internet = regla("rr_internet", "Internet", 120_000, 25)

    /** Mediodía de Bogotá de ese día, en epoch ms: lo que el server manda en `confirmedAt`. */
    fun mediodia(anio: Int, mes: Int, dia: Int): Long =
        LocalDateTime(anio, mes, dia, 12, 0).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    /**
     * Lo que manda `/api/payments/upcoming`: una entrada por regla, con el vencimiento vigente. Lo
     * ya pagado RODÓ al siguiente (Celular y Cotrafa, pagados en septiembre, dicen 22 de octubre).
     */
    val upcoming: List<UpcomingPayment> = listOf(
        UpcomingPayment(celular, "2026-10-22", daysUntil = 25, status = PaymentStatus.UPCOMING),
        UpcomingPayment(cotrafa, "2026-10-22", daysUntil = 25, status = PaymentStatus.UPCOMING),
        UpcomingPayment(crediagil, "2026-11-05", daysUntil = 39, status = PaymentStatus.UPCOMING),
        UpcomingPayment(gimnasio, "2026-09-25", daysUntil = -2, status = PaymentStatus.OVERDUE),
        UpcomingPayment(mercado, "2026-10-26", daysUntil = 29, status = PaymentStatus.UPCOMING),
        UpcomingPayment(internet, "2026-10-25", daysUntil = 28, status = PaymentStatus.UPCOMING),
    )

    /**
     * Lo que manda `/api/payments/occurrences`. Celular y Cotrafa traen la de SEPTIEMBRE, ya pagada:
     * es exactamente lo que «Ya ocurrieron» pintaba en la pantalla de octubre.
     */
    val ocurrencias: List<OccurrenceState> = listOf(
        OccurrenceState(
            ruleId = celular.id, period = "2026-09", dueDate = "2026-09-22", occurred = true,
            eventId = "ev_celular_sep", derivadaDeUnMovimiento = true, automatica = true,
            confirmedAt = mediodia(2026, 9, 21), montoDelPago = 53_000, monedaDelPago = "COP",
            periodoDelDueno = "2026-09",
        ),
        OccurrenceState(
            ruleId = cotrafa.id, period = "2026-09", dueDate = "2026-09-22", occurred = true,
            eventId = "ev_cotrafa_sep", confirmedAt = mediodia(2026, 9, 23), periodoDelDueno = "2026-09",
        ),
        // Crediágil: la cuota de octubre, pagada el 27 de septiembre — la prueba el movimiento.
        OccurrenceState(
            ruleId = crediagil.id, period = "2026-10", dueDate = "2026-10-05", occurred = true,
            eventId = "ev_crediagil", derivadaDeUnMovimiento = true,
            confirmedAt = mediodia(2026, 9, 27), montoDelPago = 1_204_064, monedaDelPago = "COP",
            periodoDelDueno = "2026-10",
        ),
        // Vencido hace 2 días y sin movimiento: la fila ofrece anotarlo.
        OccurrenceState(
            ruleId = gimnasio.id, period = "2026-09", dueDate = "2026-09-25", occurred = false,
            periodoDelDueno = "2026-10",
        ),
        // Lo emparejó Movi: se puede decir «No fue este».
        OccurrenceState(
            ruleId = mercado.id, period = "2026-09", dueDate = "2026-09-26", occurred = true,
            eventId = "ev_mercado", derivadaDeUnMovimiento = true, automatica = true,
            confirmedAt = mediodia(2026, 9, 26), montoDelPago = 2_000_000, monedaDelPago = "COP",
            periodoDelDueno = "2026-10",
        ),
        // Un sello viejo hecho a mano: se puede quitar.
        OccurrenceState(
            ruleId = internet.id, period = "2026-09", dueDate = "2026-09-25", occurred = true,
            eventId = null, confirmedAt = mediodia(2026, 9, 25), periodoDelDueno = "2026-10",
        ),
    )

    /** Coomeva: la de septiembre quedó abierta, con un candidato. No es de este período. */
    val coomeva = regla("rr_coomeva", "Coomeva", 350_000, 20, "Salud")
    val pagoDeCoomeva = FinancialEvent(
        id = "ev_coomeva", accountId = "acc-banco", type = TransactionType.EXPENSE, amount = 350_000,
        category = "Salud", description = "Coomeva", source = EventSource.MANUAL,
        timestamp = mediodia(2026, 9, 21),
    )
    val coomevaDeOctubre = UpcomingPayment(coomeva, "2026-10-20", daysUntil = 23, status = PaymentStatus.UPCOMING)
    val coomevaDeSeptiembre = OccurrenceState(
        ruleId = coomeva.id, period = "2026-09", dueDate = "2026-09-20", occurred = false,
        candidates = listOf(pagoDeCoomeva), periodoDelDueno = "2026-09",
    )
}
