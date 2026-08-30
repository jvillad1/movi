package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
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
