package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **Vuelta 72 · el emparejamiento con sentido.** Pruebas puras del emparejador: la regla «Carro · Carro ·
 * $9.000» que vence el 20 de octubre, y el mes de calendario como período.
 *
 * - El tanqueo en Terpel de $260.000 (29 veces el monto) comparte la categoría, pero **no se propone**.
 * - El doble y la mitad exactos todavía se proponen; un peso más allá, no.
 * - Lo que Movi decide sola no pasa por el filtro: un movimiento que dice el nombre de la regla se
 *   empareja igual aunque el monto esté lejos (ninguna fila tildada se destilda).
 * - Una tarjeta (su monto es el saldo) no se compara.
 */
class ElEmparejamientoConSentidoTest {

    private val vence = LocalDate.of(2026, 10, 20)
    private val periodo = PeriodSettings()
    private val carro = RecurringRule("rr_carro", "Carro", "Carro", 9_000, 20, TransactionType.EXPENSE, accountId = null)

    private fun gasto(id: String, monto: Long, descripcion: String = "Terpel", categoria: String = "Carro") = FinancialEvent(
        id = id,
        accountId = "acc_nu",
        type = TransactionType.EXPENSE,
        amount = monto,
        category = categoria,
        description = descripcion,
        timestamp = appDateToEpochMillis(LocalDate.of(2026, 10, 21)),
    )

    private fun candidatos(regla: RecurringRule, vararg eventos: FinancialEvent) =
        occurrenceCandidatesFor(regla, vence, eventos.toList(), settings = periodo).map { it.id }

    @Test
    fun `el tanqueo de 260000 no se propone para el Carro de 9000`() {
        assertEquals(emptyList(), candidatos(carro, gasto("ev_terpel", 260_000)))
    }

    @Test
    fun `el doble y la mitad exactos se proponen, un peso mas alla no`() {
        assertEquals(listOf("ev_doble"), candidatos(carro, gasto("ev_doble", 18_000)))
        assertEquals(listOf("ev_mitad"), candidatos(carro, gasto("ev_mitad", 4_500)))
        assertEquals(emptyList(), candidatos(carro, gasto("ev_mas", 18_001)))
        assertEquals(emptyList(), candidatos(carro, gasto("ev_menos", 4_499)))
    }

    @Test
    fun `lo que Movi decide sola no cambia aunque el monto este lejos`() {
        // El movimiento dice «Carro»: el nombre pega, así que se empareja sola como siempre.
        val conNombre = gasto("ev_carro", 260_000, descripcion = "Carro")
        assertEquals(listOf("ev_carro"), candidatos(carro, conNombre))
        assertNotNull(ocurrenciaConcluyente(carro, vence, listOf(conNombre), settings = periodo))
    }

    @Test
    fun `una tarjeta no se compara por monto`() {
        val tarjeta = carro.copy(id = "card_nu", amount = 1_583_000, montoEsSaldo = true)
        assertEquals(listOf("ev_pago"), candidatos(tarjeta, gasto("ev_pago", 100_000)))
    }

    @Test
    fun `el salario que varia con la retencion se sigue proponiendo`() {
        val salario = RecurringRule("rr_sal", "Salario", "Salario", 7_000_000, 20, TransactionType.INCOME, accountId = null)
        val llego = gasto("ev_sal", 6_400_000, descripcion = "Nomina", categoria = "Salario").copy(type = TransactionType.INCOME)
        assertTrue("ev_sal" in candidatos(salario, llego))
    }
}
