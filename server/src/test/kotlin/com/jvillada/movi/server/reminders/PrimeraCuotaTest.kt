package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La primera cuota de un crédito va **después** del desembolso, no el mismo día.
 *
 * El dueño registró un préstamo desembolsado el 1 de septiembre con pago el día 1, y Movi le
 * anunció la primera cuota para ese mismo 1 de septiembre: *«no tiene mucho sentido eso,
 * normalmente un desembolso es 1 mes aproximadamente antes de la primera cuota»*.
 */
class PrimeraCuotaTest {

    private fun regla(activeFrom: String?) = RecurringRule(
        id = "rule-1",
        name = "Cuota Crédito",
        category = "Créditos",
        amount = 10_000_000L,
        dayOfMonth = 1,
        type = TransactionType.EXPENSE,
        activeFrom = activeFrom,
    )

    @Test
    fun el_dia_del_desembolso_no_hay_cuota() {
        assertFalse(
            ruleIsActiveOn(regla("2026-09-01"), LocalDate.of(2026, 9, 1)),
            "el desembolso y la primera cuota no pueden caer el mismo día",
        )
    }

    @Test
    fun la_primera_cuota_es_el_mes_siguiente() {
        assertTrue(ruleIsActiveOn(regla("2026-09-01"), LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun antes_del_desembolso_tampoco_hay_cuota() {
        assertFalse(ruleIsActiveOn(regla("2026-09-01"), LocalDate.of(2026, 8, 1)))
    }

    /** Un salario o un gimnasio no tienen desembolso: corren desde siempre, como hasta ahora. */
    @Test
    fun una_regla_escrita_a_mano_corre_desde_siempre() {
        assertTrue(ruleIsActiveOn(regla(null), LocalDate.of(2020, 1, 1)))
    }

    /** Una fecha ilegible no puede apagar un recordatorio: ante la duda, la regla corre. */
    @Test
    fun una_fecha_invalida_no_apaga_la_regla() {
        assertTrue(ruleIsActiveOn(regla("no-es-una-fecha"), LocalDate.of(2026, 9, 1)))
    }

    /** La regla sintética de un crédito hereda la fecha de desembolso de sus términos. */
    @Test
    fun la_regla_del_credito_toma_la_fecha_de_desembolso() {
        val terms = CreditTerms(
            accountId = "acc-1", bank = "Papá", principal = 10_000_000L, rateEa = 0.0,
            termMonths = 1, installment = 10_000_000L, dayOfMonth = 1, startDate = "2026-09-01",
        )

        val regla = virtualRuleFor(terms, "Crédito Techo Gardenera")

        assertFalse(ruleIsActiveOn(regla, LocalDate.of(2026, 9, 1)), "no el día del desembolso")
        assertTrue(ruleIsActiveOn(regla, LocalDate.of(2026, 10, 1)), "sí un mes después")
    }
}

/**
 * La primera cuota, ahora en **todos** los endpoints.
 *
 * Se arregló una vez con `ruleIsActiveOn`, un filtro suelto que solo llamaba
 * `/api/payments/occurrences`. «Próximos pagos» del Inicio y el barrido de avisos seguían
 * mostrando la cuota el día del desembolso — que es exactamente donde el dueño la vio:
 *
 *   «el pago sale como que es mañana pero realmente sería el 1° de octubre a más tardar»
 *
 * Su crédito del techo se desembolsa el 1 de septiembre y su única cuota es el día 1. Con el
 * arreglo a medias, el Inicio decía «Vence en 2 días» el 30 de agosto: el mismo día en que la
 * plata todavía no había entrado.
 *
 * Ahora lo sabe `dueDateFor`, así que lo saben los tres consumidores sin que ninguno se acuerde.
 */
class PrimeraCuotaEnTodosLosEndpointsTest {

    private fun reglaDelTecho(inicio: String) = RecurringRule(
        id = "cred_techo",
        name = "Cuota Crédito Techo Gardenera",
        amount = 10_000_000,
        dayOfMonth = 1,
        type = TransactionType.EXPENSE,
        category = "Cuota de crédito",
        activeFrom = inicio,
    )

    @Test
    fun `la cuota NO cae el mismo dia del desembolso`() {
        // El caso del dueño, con sus fechas: desembolso 1-sep, día de pago 1.
        val due = dueDateFor(reglaDelTecho("2026-09-01"), today = LocalDate.of(2026, 8, 30))

        assertEquals(LocalDate.of(2026, 10, 1), due, "la primera cuota es la del mes siguiente")
    }

    @Test
    fun `ni el mismo dia del desembolso mirado desde ese dia`() {
        // El 1 de septiembre, con la plata recién entrada, tampoco vence hoy.
        val due = dueDateFor(reglaDelTecho("2026-09-01"), today = LocalDate.of(2026, 9, 1))

        assertEquals(LocalDate.of(2026, 10, 1), due)
    }

    @Test
    fun `y aparece en Proximos pagos con la fecha correcta`() {
        // El endpoint que el dueño mira en el Inicio, y el que el arreglo anterior no tocaba.
        val pagos = upcomingPayments(
            rules = listOf(reglaDelTecho("2026-09-01")),
            today = LocalDate.of(2026, 8, 30),
            leadDays = 3,
        )

        assertEquals("2026-10-01", pagos.single().dueDate)
        assertEquals(32, pagos.single().daysUntil, "y no «vence en 2 días»")
    }

    @Test
    fun `un credito que ya venia pagandose no se corre`() {
        // La otra mitad: el arreglo no puede empujar las cuotas de un crédito viejo. La libranza
        // del dueño se desembolsó en 2024 y su cuota de este mes es de este mes.
        val libranza = reglaDelTecho("2024-07-22").copy(id = "cred_libranza", dayOfMonth = 25)

        val due = dueDateFor(libranza, today = LocalDate.of(2026, 8, 20))

        assertEquals(LocalDate.of(2026, 8, 25), due)
    }

    @Test
    fun `una fecha de inicio absurda no cuelga el server`() {
        // Un año mal tecleado —2400 en vez de 2026— daría un bucle infinito sin el tope. Devuelve
        // una fecha rara, que es un dato raro y no un server caído.
        val futuro = reglaDelTecho("2400-01-01")

        val due = dueDateFor(futuro, today = LocalDate.of(2026, 8, 30))

        assertTrue(due.isAfter(LocalDate.of(2026, 8, 30)))
    }

    @Test
    fun `sin fecha de inicio se comporta como siempre`() {
        // Los recurrentes normales (el colegio, el gimnasio) no tienen `activeFrom`.
        val gimnasio = reglaDelTecho("2026-09-01").copy(activeFrom = null, dayOfMonth = 5)

        assertEquals(LocalDate.of(2026, 9, 5), dueDateFor(gimnasio, today = LocalDate.of(2026, 9, 1)))
    }
}
