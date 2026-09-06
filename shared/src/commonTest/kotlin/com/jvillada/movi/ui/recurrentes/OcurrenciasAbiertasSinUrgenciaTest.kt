package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PR 3 del rediseño de Recurrentes (2026-09): la puerta que faltaba para cerrar un periodo que ya
 * dejó de urgir.
 *
 * `GET /api/payments/upcoming` corre el vencimiento al mes siguiente apenas pasan los días de
 * gracia **aunque nadie haya confirmado el periodo en curso**, así que la regla sale de
 * «Próximos». Pero `GET /api/payments/occurrences` no corre nada y su estado sigue abierto. En la
 * pantalla vieja la propuesta seguía estando en el inventario «Por día del mes», que no se mudó a
 * Movimientos: sin esta lista, un recurrente de principio de mes (el gimnasio del día 5) quedaba
 * sin forma de confirmarse desde el día ~10 hasta fin de mes.
 */
class OcurrenciasAbiertasSinUrgenciaTest {

    private fun regla(id: String, nombre: String, dia: Int = 5) = RecurringRule(
        id = id, name = nombre, category = "Vivienda",
        amount = 1_000_000L, dayOfMonth = dia, type = TransactionType.EXPENSE,
    )

    private fun vencimiento(rule: RecurringRule, status: PaymentStatus = PaymentStatus.UPCOMING) =
        UpcomingPayment(rule = rule, dueDate = "2026-10-05", daysUntil = 29, status = status)

    private fun estado(ruleId: String, occurred: Boolean) = OccurrenceState(
        ruleId = ruleId, period = "2026-09", dueDate = "2026-09-05",
        occurred = occurred, eventId = null,
    )

    /** El caso que motivó la función: ya no urge, nadie lo confirmó, y tiene que poder cerrarse. */
    @Test fun `un periodo abierto que ya no urge sigue teniendo donde confirmarse`() {
        val gimnasio = regla("rr_gym", "Gimnasio Cami")
        val abiertas = ocurrenciasAbiertasSinUrgencia(
            upcoming = listOf(vencimiento(gimnasio)),
            estados = listOf(estado("rr_gym", occurred = false)),
            proximos = emptyList(),
        )
        assertEquals(listOf("Gimnasio Cami"), abiertas.map { it.first.name })
        assertEquals("2026-09", abiertas.single().second.period)
    }

    /** Lo que ya se pregunta arriba no se vuelve a preguntar acá: sería la misma pregunta dos veces. */
    @Test fun `lo que ya esta en Proximos no se repite`() {
        val arriendo = regla("rr_arriendo", "Arriendo")
        val vence = vencimiento(arriendo, PaymentStatus.DUE_SOON)
        val abiertas = ocurrenciasAbiertasSinUrgencia(
            upcoming = listOf(vence),
            estados = listOf(estado("rr_arriendo", occurred = false)),
            proximos = listOf(vence),
        )
        assertTrue(abiertas.isEmpty())
    }

    /** Lo sellado es trabajo de «Ya ocurrieron», con su «Deshacer». */
    @Test fun `lo ya sellado no entra`() {
        val gimnasio = regla("rr_gym", "Gimnasio Cami")
        val abiertas = ocurrenciasAbiertasSinUrgencia(
            upcoming = listOf(vencimiento(gimnasio)),
            estados = listOf(estado("rr_gym", occurred = true)),
            proximos = emptyList(),
        )
        assertTrue(abiertas.isEmpty())
    }

    /** Sin el nombre de la regla no hay fila que mostrar — mismo criterio que `ocurrenciasSelladas`. */
    @Test fun `una ocurrencia sin regla conocida se descarta en silencio`() {
        val abiertas = ocurrenciasAbiertasSinUrgencia(
            upcoming = emptyList(),
            estados = listOf(estado("rr_fantasma", occurred = false)),
            proximos = emptyList(),
        )
        assertTrue(abiertas.isEmpty())
    }

    /** Orden estable por nombre: las filas no pueden bailar entre recargas. */
    @Test fun `se ordena por nombre`() {
        val gimnasio = regla("rr_gym", "Gimnasio Cami")
        val agua = regla("rr_agua", "Agua")
        val abiertas = ocurrenciasAbiertasSinUrgencia(
            upcoming = listOf(vencimiento(gimnasio), vencimiento(agua)),
            estados = listOf(estado("rr_gym", occurred = false), estado("rr_agua", occurred = false)),
            proximos = emptyList(),
        )
        assertEquals(listOf("Agua", "Gimnasio Cami"), abiertas.map { it.first.name })
    }
}
