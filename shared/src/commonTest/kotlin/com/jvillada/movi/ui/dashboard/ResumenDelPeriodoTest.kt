package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.periodoDeLaFecha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Las tres respuestas del Inicio nuevo, probadas sin pantalla: en qué se fue la plata, qué falta
 * por pagar y qué conviene revisar.
 */
class ResumenDelPeriodoTest {

    private val corte25 = PeriodSettings(cutoffDay = 25)

    private fun regla(id: String, nombre: String, monto: Long, dia: Int, saldo: Boolean = false) =
        RecurringRule(
            id = id, name = nombre, category = "Vivienda", amount = monto,
            dayOfMonth = dia, type = TransactionType.EXPENSE, montoEsSaldo = saldo,
        )

    private fun pago(id: String, nombre: String, monto: Long, vence: String, dias: Int, saldo: Boolean = false) =
        UpcomingPayment(
            rule = regla(id, nombre, monto, vence.takeLast(2).toInt(), saldo),
            dueDate = vence,
            daysUntil = dias,
            status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
        )

    private fun sellada(ruleId: String) =
        OccurrenceState(ruleId = ruleId, period = "2026-09", dueDate = "2026-09-05", occurred = true)

    // ── En qué se fue la plata ───────────────────────────────────────────────

    @Test
    fun `las categorias salen de mayor a menor y su fraccion es sobre el gasto total`() {
        val cats = categoriasDelPeriodo(mapOf("Mercado" to 300_000, "Vivienda" to 700_000))

        assertEquals(listOf("Vivienda", "Mercado"), cats.map { it.nombre })
        assertEquals(0.7f, cats[0].fraccion)
        assertEquals(0.3f, cats[1].fraccion)
    }

    /**
     * La barra más larga NO es «la categoría más grande llena el ancho»: es cuánto pesa dentro del
     * período. Con dos categorías chicas, la mayor tiene que verse chica.
     */
    @Test
    fun `una categoria que pesa poco se ve poco, aunque sea la mayor`() {
        val cats = categoriasDelPeriodo(
            mapOf("A" to 100_000, "B" to 90_000, "C" to 90_000, "D" to 90_000, "E" to 90_000, "F" to 90_000),
        )
        assertTrue(cats.first().fraccion < 0.2f, "la mayor de seis parecidas no puede llenar la barra")
    }

    @Test
    fun `la cola se agrupa y las barras siguen sumando el gasto del periodo`() {
        val gasto = (1..8).associate { "Cat$it" to (it * 100_000).toLong() }

        val cats = categoriasDelPeriodo(gasto, cuantas = 5)

        assertEquals(6, cats.size)
        assertEquals("Otras 3 categorías", cats.last().nombre)
        assertEquals(gasto.values.sum(), cats.sumOf { it.gastado })
        assertTrue(kotlin.math.abs(cats.sumOf { it.fraccion.toDouble() } - 1.0) < 0.001)
    }

    /** Con una sola categoría en la cola se dice su nombre: «Otras 1 categorías» no lo dice nadie. */
    @Test
    fun `una sola categoria en la cola se nombra`() {
        val cats = categoriasDelPeriodo((1..6).associate { "Cat$it" to (it * 1000).toLong() }, cuantas = 5)
        assertEquals("Cat1", cats.last().nombre)
    }

    @Test
    fun `una categoria sin presupuesto no esta superada, y con presupuesto excedido si`() {
        val cats = categoriasDelPeriodo(
            mapOf("Mercado" to 900_000, "Ocio" to 100_000),
            presupuestos = listOf(Budget(category = "Mercado", monthlyLimit = 800_000)),
        )
        assertTrue(cats.first { it.nombre == "Mercado" }.superada)
        assertFalse(cats.first { it.nombre == "Ocio" }.superada)
    }

    @Test
    fun `sin gasto no hay categorias que mostrar`() {
        assertEquals(emptyList(), categoriasDelPeriodo(emptyMap()))
        assertEquals(emptyList(), categoriasDelPeriodo(mapOf("Mercado" to 0L)))
    }

    // ── El checklist ─────────────────────────────────────────────────────────

    @Test
    fun `el checklist es del periodo del dueno, no del mes de calendario`() {
        val periodo = periodoDeLaFecha("2026-09-10", corte25)!!

        val checklist = checklistDelPeriodo(
            upcoming = listOf(
                pago("r1", "Arriendo", 1_850_000, "2026-09-05", -5),
                // Vence pasado el corte del 25: ya es del período siguiente.
                pago("r2", "Colegio", 1_320_000, "2026-09-28", 18),
            ),
            ocurrencias = emptyList(),
            periodo = periodo,
            settings = corte25,
        )

        assertEquals(listOf("Arriendo"), checklist.map { it.nombre })
    }

    @Test
    fun `lo pagado queda tildado y al final, y lo vencido arriba`() {
        val periodo = periodoDeLaFecha("2026-09-10", corte25)!!

        val checklist = checklistDelPeriodo(
            upcoming = listOf(
                pago("r1", "Gimnasio", 139_900, "2026-09-20", 10),
                pago("r2", "Arriendo", 1_850_000, "2026-09-05", -5),
                pago("r3", "Celular", 53_000, "2026-09-12", 2),
            ),
            ocurrencias = listOf(sellada("r2")),
            periodo = periodo,
            settings = corte25,
        )

        assertEquals(listOf("Celular", "Gimnasio", "Arriendo"), checklist.map { it.nombre })
        assertTrue(checklist.last().pagado)
        assertFalse(checklist.last().vencido, "lo pagado no está vencido, aunque su fecha ya pasó")
        assertEquals(2 to 3, avanceDelChecklist(checklist).let { (a, b) -> a + 1 to b })
    }

    /**
     * **Un saldo de tarjeta no es lo que falta por pagar.** La regla sintética de una tarjeta trae
     * el SALDO; sumarlo a «te falta» diría que el dueño debe pagar veintisiete millones este mes.
     */
    @Test
    fun `lo que falta por pagar no cuenta los saldos de tarjeta`() {
        val periodo = periodoDeLaFecha("2026-09-10", corte25)!!

        val checklist = checklistDelPeriodo(
            upcoming = listOf(
                pago("r1", "Celular", 53_000, "2026-09-12", 2),
                pago("card_1", "Master Black", 27_501_150, "2026-09-15", 5, saldo = true),
            ),
            ocurrencias = emptyList(),
            periodo = periodo,
            settings = corte25,
        )

        assertEquals(53_000, faltaPorPagar(checklist))
        assertEquals(2, checklist.size, "el saldo se muestra igual, solo no se suma")
    }

    @Test
    fun `sin ocurrencia conocida un pago cuenta como pendiente`() {
        val periodo = periodoDeLaFecha("2026-09-10", corte25)!!
        val checklist = checklistDelPeriodo(
            listOf(pago("r1", "Celular", 53_000, "2026-09-12", 2)), emptyList(), periodo, corte25,
        )
        assertFalse(checklist.single().pagado)
    }

    // ── Qué revisar ──────────────────────────────────────────────────────────

    @Test
    fun `lo vencido sin marcar es lo primero que se recomienda revisar`() {
        val checklist = listOf(
            PagoDelPeriodo("r1", "Cuota Vehículo", 4_101_123, pagado = false, diasParaVencer = -3),
        )

        val revisar = cosasParaRevisar(
            checklist = checklist,
            categorias = emptyList(),
            flujoDelPeriodo = -5_200_000,
            smsPorConfirmar = 0,
            candidatosAPagoDeTarjeta = 0,
        )

        assertTrue(revisar.first().urgente)
        assertTrue(revisar.first().texto.contains("Cuota Vehículo"))
        assertEquals(DestinoDeRevision.RECURRENTES, revisar.first().destino)
    }

    @Test
    fun `cada sugerencia lleva a donde se resuelve, y nunca son mas de cuatro`() {
        val revisar = cosasParaRevisar(
            checklist = listOf(
                PagoDelPeriodo("r1", "Arriendo", 1_850_000, pagado = false, diasParaVencer = -1),
            ),
            categorias = categoriasDelPeriodo(
                mapOf("Mercado" to 900_000),
                listOf(Budget(category = "Mercado", monthlyLimit = 500_000)),
            ),
            flujoDelPeriodo = -1_000_000,
            smsPorConfirmar = 109,
            candidatosAPagoDeTarjeta = 2,
        )

        assertEquals(4, revisar.size)
        assertTrue(revisar.all { it.texto.isNotBlank() && it.detalle.isNotBlank() })
        assertEquals(revisar.sortedByDescending { it.urgente }, revisar, "lo urgente va primero")
    }

    @Test
    fun `sin nada que revisar la seccion no dice nada`() {
        val revisar = cosasParaRevisar(
            checklist = listOf(PagoDelPeriodo("r1", "Celular", 53_000, pagado = true, diasParaVencer = 3)),
            categorias = categoriasDelPeriodo(mapOf("Mercado" to 100_000)),
            flujoDelPeriodo = 500_000,
            smsPorConfirmar = 0,
            candidatosAPagoDeTarjeta = 0,
        )
        assertEquals(emptyList(), revisar)
    }

    @Test
    fun `un solo mensaje del banco no es urgente, y cien si`() {
        fun sms(n: Int) = cosasParaRevisar(emptyList(), emptyList(), 0, n, 0).single()
        assertFalse(sms(1).urgente)
        assertTrue(sms(109).urgente)
        assertTrue(sms(1).texto.startsWith("1 mensaje "), "singular: «1 mensaje», no «1 mensajes»")
    }
}
