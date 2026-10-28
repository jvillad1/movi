package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Vuelta 45 · el emparejamiento pregunta más, sin tildar nada.** Pruebas puras del emparejador (sin
 * base): la regla «Arriendo · Otros · $1.800.000» **sin cuenta**, que vence el 5 de octubre, y el mes de
 * calendario como período.
 *
 * - Con regla sin cuenta, la misma categoría y el monto exacto, **único** en la ventana: la fila pregunta
 *   (es candidato) y Movi no lo tilda solo (no es concluyente: falta la cuenta).
 * - Pasada la ventana de 10 días, se sigue proponiendo hasta el fin del período; nunca concluyente.
 * - Con dos, los propone los dos, como ya hacía la fila (hasta tres).
 * - Fuera del período o con otra categoría, nada.
 */
class ElEmparejamientoPreguntaMasTest {

    private val vence = LocalDate.of(2026, 10, 5)
    private val periodo = PeriodSettings()
    private val arriendo = RecurringRule("rr_arriendo", "Arriendo", "Otros", 1_800_000, 5, TransactionType.EXPENSE, accountId = null)

    private fun pago(id: String, dia: LocalDate, monto: Long = 1_800_000, categoria: String = "Otros") = FinancialEvent(
        id = id,
        accountId = "acc_1",
        type = TransactionType.EXPENSE,
        amount = monto,
        category = categoria,
        description = "Transferencia",
        timestamp = appDateToEpochMillis(dia),
    )

    private fun candidatos(vararg eventos: FinancialEvent) =
        occurrenceCandidatesFor(arriendo, vence, eventos.toList(), settings = periodo).map { it.id }

    private fun concluyente(vararg eventos: FinancialEvent) =
        ocurrenciaConcluyente(arriendo, vence, eventos.toList(), settings = periodo)

    @Test
    fun `regla sin cuenta, misma categoria y monto exacto, unico en la ventana - pregunta sin tildar`() {
        val elPago = pago("ev_1", LocalDate.of(2026, 10, 6))
        assertEquals(listOf("ev_1"), candidatos(elPago))
        assertNull(concluyente(elPago), "sin la cuenta no hay tres circunstancias: Movi pregunta, no tilda")
    }

    @Test
    fun `pasada la ventana se sigue proponiendo hasta el fin del periodo, nunca solo`() {
        // 23 días tarde, como el arriendo del dueño.
        val tarde = pago("ev_tarde", LocalDate.of(2026, 10, 28))
        assertEquals(listOf("ev_tarde"), candidatos(tarde))
        assertNull(concluyente(tarde))
        val puntuado = candidatosPuntuados(arriendo, vence, listOf(tarde), settings = periodo).single()
        assertFalse(puntuado.enLaVentana)
        assertFalse(puntuado.concluyente)
        assertFalse(puntuado.identidadFuerte, "no absorbe el fijo en el Disponible")
    }

    @Test
    fun `tarde y con el nombre, tampoco se tilda solo`() {
        val conNombre = pago("ev_nombre", LocalDate.of(2026, 10, 25)).copy(description = "Arriendo")
        assertEquals(listOf("ev_nombre"), candidatos(conNombre))
        assertNull(concluyente(conNombre), "fuera de la ventana angosta, ni el nombre decide")
        // Dentro de la ventana, el nombre sí decide, como siempre.
        val aTiempo = conNombre.copy(id = "ev_atiempo", timestamp = appDateToEpochMillis(LocalDate.of(2026, 10, 6)))
        assertEquals("ev_atiempo", concluyente(aTiempo)?.id)
    }

    @Test
    fun `con dos candidatos los propone los dos y no tilda ninguno`() {
        val uno = pago("ev_1", LocalDate.of(2026, 10, 6))
        val otro = pago("ev_2", LocalDate.of(2026, 10, 27))
        assertEquals(listOf("ev_1", "ev_2"), candidatos(uno, otro), "el más cercano al vencimiento, primero")
        assertNull(concluyente(uno, otro))
    }

    @Test
    fun `fuera del periodo, o con otra categoria, no se propone`() {
        assertEquals(emptyList(), candidatos(pago("ev_nov", LocalDate.of(2026, 11, 20))), "el período cerró el 31")
        assertEquals(emptyList(), candidatos(pago("ev_sep", LocalDate.of(2026, 9, 30))), "antes del período")
        assertEquals(emptyList(), candidatos(pago("ev_cat", LocalDate.of(2026, 10, 6), categoria = "Mercado")))
    }

    @Test
    fun `la ventana para proponer llega al cierre y la que decide sigue en 10 dias`() {
        val amplia = ventanaParaProponer(vence, settings = periodo)
        val angosta = occurrenceWindow(vence, settings = periodo)
        assertEquals(LocalDate.of(2026, 10, 31), amplia.endInclusive)
        assertEquals(LocalDate.of(2026, 10, 15), angosta.endInclusive)
        assertEquals(angosta.start, amplia.start)
        assertTrue(sostieneLaOcurrencia(LocalDate.of(2026, 10, 15), vence, settings = periodo))
    }
}
