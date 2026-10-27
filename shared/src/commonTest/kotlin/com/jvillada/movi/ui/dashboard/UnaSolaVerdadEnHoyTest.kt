package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.porrevisar.conteoPorRevisar
import com.jvillada.movi.ui.porrevisar.desgloseDePorRevisar
import com.jvillada.movi.ui.porrevisar.textoDePorRevisar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta de diseño 22: una sola verdad en Hoy
 *
 * El caso de la revisión, con la cuenta demo: el Arriendo de $1,8M vencido hace 22 días no estaba en la
 * campana (que sí mostraba Internet $3.000 y Carro $9.000), Hoy lo listaba dos veces —«Movi propone hoy»
 * y el widget «Falta por pagar»—, y los contadores no cuadraban: la insignia de «Avisos» decía 2,
 * «Llegaron 2 avisos del banco», y Movimientos «3 por revisar».
 */
class UnaSolaVerdadEnHoyTest {

    private val octubre = PeriodoFinanciero(2026, 10)

    private fun regla(id: String, nombre: String, monto: Long, dia: Int) =
        RecurringRule(id = id, name = nombre, category = "Servicios", amount = monto, dayOfMonth = dia, type = TransactionType.EXPENSE)

    /**
     * Hoy es el 27-oct. El vencimiento VIGENTE del arriendo ya rodó al 5-nov («vence en 9 días»): es lo
     * que contesta `/api/payments/upcoming` pasados los días de gracia. Su ocurrencia del 5-oct sigue
     * abierta. Internet y Carro vencieron hace un día y hace dos.
     */
    private val demo = DashboardData(
        upcoming = listOf(
            UpcomingPayment(regla("rr_arriendo", "Arriendo", 1_800_000, 5), "2026-11-05", daysUntil = 9, status = PaymentStatus.UPCOMING),
            UpcomingPayment(regla("rr_internet", "Internet", 3_000, 26), "2026-10-26", daysUntil = -1, status = PaymentStatus.OVERDUE),
            UpcomingPayment(regla("rr_carro", "Carro", 9_000, 25), "2026-10-25", daysUntil = -2, status = PaymentStatus.OVERDUE),
        ),
        ocurrencias = listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-10", dueDate = "2026-10-05", occurred = false)),
        periodoActual = octubre,
        ajustesDePeriodo = PeriodSettings(),
    )

    // ── 1. Una sola cola ─────────────────────────────────────────────────────

    @Test
    fun `la campana incluye el Arriendo vencido, primero`() {
        val filas = notificationRows(demo)
        assertEquals(
            listOf("Arriendo venció hace 22 días", "Carro venció hace 2 días", "Internet venció ayer"),
            filas.map { it.titulo },
        )
        assertEquals(TipoDeNotificacion.PagoVencido, filas.first().tipo)
        assertEquals(1_800_000L, filas.first().monto)
        assertEquals(Screen.Plan(SEGMENTO_PAGOS), filas.first().target)
    }

    @Test
    fun `la campana y Movi propone hoy priorizan igual`() {
        val propuesta = propuestasDelInicio(demo).first { it.tipo == TipoDePropuesta.PAGO }
        assertEquals("Arriendo venció hace 22 días", propuesta.titulo)
        assertEquals(1_800_000L, propuesta.cifra)
        assertEquals("y 2 pagos más por vencer", propuesta.cuerpo)
        // El primero de la campana es el de la propuesta.
        assertEquals(propuesta.titulo, notificationRows(demo).first().titulo)
    }

    @Test
    fun `Hoy no lista el mismo pago dos veces`() {
        val propuesto = pagoPropuestoHoy(demo)!!
        val widget = filasDelWidgetDeFalta(demo, conPropuestas = true)
        assertTrue(widget.filas.none { it.ruleId == propuesto.ruleId }, "el widget no repite el pago de la propuesta")
        // Lo demás, en el orden de la cola (el monto más grande primero).
        assertEquals(listOf("Carro", "Internet"), widget.filas.map { it.nombre })
        // Las filas más «y N más» dan lo que dice «Faltan 3 de 3 pagos».
        assertEquals(1, widget.masSinFila)
        assertEquals("y 1 más", yNMas(widget.masSinFila))
        // Sin «Movi propone hoy» en el Inicio no hay a quién dejárselo: lo lista.
        assertEquals(listOf("Arriendo", "Carro"), filasDelWidgetDeFalta(demo, conPropuestas = false).filas.map { it.nombre })
    }

    @Test
    fun `si el unico pendiente esta en la propuesta, el widget dice donde esta`() {
        val soloElArriendo = demo.copy(upcoming = demo.upcoming!!.take(1))
        val widget = filasDelWidgetDeFalta(soloElArriendo, conPropuestas = true)
        assertTrue(widget.filas.isEmpty())
        assertTrue(widget.soloElDeArriba)
    }

    @Test
    fun `una lista que se recorta dice y N mas`() {
        val cinco = demo.copy(
            upcoming = demo.upcoming!! + listOf(
                UpcomingPayment(regla("rr_gym", "Gimnasio", 90_000, 28), "2026-10-28", daysUntil = 1, status = PaymentStatus.DUE_SOON),
                UpcomingPayment(regla("rr_cel", "Celular", 53_000, 30), "2026-10-30", daysUntil = 3, status = PaymentStatus.DUE_SOON),
            ),
        )
        val widget = filasDelWidgetDeFalta(cinco, conPropuestas = true)
        assertEquals(2, widget.filas.size)
        assertEquals(3, widget.masSinFila, "5 pendientes − 2 filas")
        assertEquals(null, yNMas(0))
    }

    // ── 2. Los contadores cuadran ────────────────────────────────────────────

    private fun sms(id: String, texto: String, cuando: String) =
        SmsMessage(id = id, time = cuando, bank = "Bancolombia", text = texto, state = SMS_STATE_PENDING, det = "")

    private val movimientoQueEntroSolo = FinancialEvent(
        id = "ev_solo", accountId = "acc", type = TransactionType.EXPENSE, amount = 18_500L, category = "Comida",
        description = "Rappi", timestamp = 1_757_000_000_000L, reconciliationStatus = ReconciliationStatus.UNCONFIRMED,
    )

    @Test
    fun `con 2 avisos y 1 movimiento por confirmar, los tres lugares dicen lo mismo`() {
        // Lo que manda el resumen del server: 2 pagos avisados y 1 movimiento por confirmar.
        val inicio = DashboardData(pendingSms = 2, movimientosPorConfirmar = 1)
        // Lo que Movimientos cuenta sobre la bandeja que bajó.
        val movimientos = conteoPorRevisar(
            mensajes = listOf(
                sms("s1", "Compra \$28.500 en Uber", "2026-10-20 07:15"),
                sms("s2", "Compra \$45.000 en Rappi", "2026-10-25 19:40"),
            ),
            dias = listOf(EventDay(date = "2026-10-26", total = 0L, items = listOf(movimientoQueEntroSolo))),
            candidatos = emptyList(),
            debitos = emptyList(),
        )

        // La insignia de «Avisos» y el renglón de Movimientos: el mismo número.
        assertEquals(3, pendientesPorRevisar(inicio))
        assertEquals(3, movimientos.total)
        assertEquals(conteoPorRevisarDelInicio(inicio), movimientos)
        assertEquals("3 por revisar", textoDePorRevisar(movimientos.total))
        // Y cada uno dice de qué es.
        assertEquals("2 avisos · 1 movimiento", desgloseDePorRevisar(movimientos))
        assertEquals("Revisar los avisos (2 avisos · 1 movimiento)", descripcionDeLaInsignia(inicio))

        // «Movi propone hoy» dice el mismo número, con el mismo desglose.
        val propuesta = propuestasDelInicio(inicio).single { it.tipo == TipoDePropuesta.AVISOS }
        assertEquals("3 por revisar", propuesta.titulo)
        assertEquals("2 avisos · 1 movimiento", propuesta.cuerpo)

        // Y la campana, una sola fila con lo mismo.
        val campana = notificationRows(inicio.copy(periodoActual = octubre)).single { it.target == Screen.PorRevisar }
        assertEquals("3 por revisar", campana.titulo)
        assertEquals("2 avisos · 1 movimiento", campana.subtitulo)
    }

    @Test
    fun `de una sola fuente se dice con su nombre`() {
        assertEquals("Llegaron 2 avisos del banco", propuestasDelInicio(DashboardData(pendingSms = 2)).single().titulo)
        assertEquals("1 movimiento entró solo", propuestasDelInicio(DashboardData(movimientosPorConfirmar = 1)).single().titulo)
        assertEquals(
            "2 pagos de tarjeta por confirmar",
            propuestasDelInicio(DashboardData(cardCandidates = 2)).single().titulo,
        )
    }
}
