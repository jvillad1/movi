package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **La cola de lo pendiente** (vuelta de diseño 22): una sola lista, ordenada por urgencia y monto, que
 * leen la campana, «Movi propone hoy», el widget «Falta por pagar» y Plan.
 */
class ColaDePendientesTest {

    private fun pago(
        nombre: String,
        monto: Long,
        dias: Int,
        pagado: Boolean = false,
        ingreso: Boolean = false,
        esSaldo: Boolean = false,
        minimo: Long? = null,
    ) = PagoDelPeriodo(
        ruleId = "r-$nombre", nombre = nombre, monto = monto, pagado = pagado, diasParaVencer = dias,
        esIngreso = ingreso, montoEsSaldo = esSaldo, pagoMinimo = minimo,
    )

    private fun debito(nombre: String, monto: Long) = DebitoAutomaticoPorConfirmar(
        ruleId = "d-$nombre", periodo = "2026-10", origen = OrigenDelDebito.RECURRENTE, nombre = nombre,
        monto = monto, vence = "2026-10-20", cuentaId = "c1", cuentaNombre = "Ahorros", categoria = "Seguros",
        pataDelDineroId = "p-$nombre",
    )

    private fun nombres(cola: List<Pendiente>): List<String> = cola.map {
        when (it) {
            is Pendiente.Pago -> it.pago.nombre
            is Pendiente.Debito -> "débito ${it.debito.nombre}"
            is Pendiente.PorRevisar -> "por revisar ${it.cuantos}"
        }
    }

    @Test
    fun `lo vencido primero y dentro de cada grupo el monto mas grande`() {
        val cola = colaDePendientes(
            checklist = listOf(
                pago("Internet", 3_000, dias = -1),
                pago("Carro", 9_000, dias = -2),
                pago("Arriendo", 1_800_000, dias = -22),
                pago("Celular", 53_000, dias = 0),
                pago("Gimnasio", 90_000, dias = 0),
                pago("Colegio", 700_000, dias = 5),
                pago("Seguro", 120_000, dias = 1),
                pago("Lejano", 5_000_000, dias = 20),
                pago("Ya pagado", 2_000_000, dias = -3, pagado = true),
                pago("Salario", 9_000_000, dias = -1, ingreso = true),
            ),
            debitos = listOf(debito("Póliza", 80_000), debito("Cuota", 900_000)),
            porRevisar = 3,
        )
        assertEquals(
            listOf(
                // Vencidos, por monto: el Arriendo de $1,8M antes que Carro e Internet.
                "Arriendo", "Carro", "Internet",
                // Lo de hoy.
                "Gimnasio", "Celular",
                // Lo que el banco ya cobró solo, por monto.
                "débito Cuota", "débito Póliza",
                // Lo que espera en «Por revisar», una sola entrada.
                "por revisar 3",
                // Lo de la semana, por monto (no por fecha).
                "Colegio", "Seguro",
            ),
            nombres(cola),
        )
        // Fuera: lo pagado, el ingreso y lo que vence pasada la semana.
        assertTrue(cola.none { it.clave == "pago:r-Lejano" || it.clave == "pago:r-Ya pagado" || it.clave == "pago:r-Salario" })
    }

    @Test
    fun `la urgencia de cada pago`() {
        assertEquals(UrgenciaDelPendiente.VENCIDO, (colaDePendientes(listOf(pago("A", 1, -1))).single()).urgencia)
        assertEquals(UrgenciaDelPendiente.VENCE_HOY, (colaDePendientes(listOf(pago("A", 1, 0))).single()).urgencia)
        assertEquals(UrgenciaDelPendiente.POR_VENCER, (colaDePendientes(listOf(pago("A", 1, 7))).single()).urgencia)
        assertTrue(colaDePendientes(listOf(pago("A", 1, 8))).isEmpty(), "pasada la semana no entra a la cola")
        assertTrue(colaDePendientes(emptyList(), porRevisar = 0).isEmpty(), "sin nada por revisar no hay entrada")
    }

    @Test
    fun `una tarjeta se ordena y se dice por su minimo, no por su saldo`() {
        val tarjeta = pago("Master Black", 12_000_000, dias = -1, esSaldo = true, minimo = 400_000)
        val sinMinimo = pago("AMEX", 9_000_000, dias = -1, esSaldo = true)
        val cola = colaDePendientes(listOf(sinMinimo, pago("Arriendo", 1_800_000, -1), tarjeta))
        assertEquals(listOf("Arriendo", "Master Black", "AMEX"), nombres(cola))
        assertEquals(400_000L, (cola[1] as Pendiente.Pago).cifra)
        assertEquals(null, (cola[2] as Pendiente.Pago).cifra)
    }

    /**
     * **El Arriendo de la revisión**: vencido hace 22 días, su vencimiento vigente ya rodó al período
     * siguiente («vence en 9 días»). La campana leía ese vencimiento y lo dejaba afuera; la cola sale
     * del checklist, donde manda la ocurrencia, y lo pone primero.
     */
    @Test
    fun `el Arriendo vencido hace 22 dias entra primero aunque su vencimiento vigente ya rodo`() {
        val arriendo = RecurringRule(id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 1_800_000, dayOfMonth = 5, type = TransactionType.EXPENSE)
        val internet = RecurringRule(id = "rr_internet", name = "Internet", category = "Servicios", amount = 3_000, dayOfMonth = 26, type = TransactionType.EXPENSE)
        val upcoming = listOf(
            // Hoy es el 27-oct: el vencimiento vigente del arriendo ya es el 5-nov.
            UpcomingPayment(arriendo, dueDate = "2026-11-05", daysUntil = 9, status = PaymentStatus.UPCOMING),
            UpcomingPayment(internet, dueDate = "2026-10-26", daysUntil = -1, status = PaymentStatus.OVERDUE),
        )
        val ocurrencias = listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-10", dueDate = "2026-10-05", occurred = false))
        val checklist = checklistDelPeriodo(upcoming, ocurrencias, PeriodoFinanciero(2026, 10), PeriodSettings())

        val cola = colaDePendientes(checklist)
        val primero = cola.first() as Pendiente.Pago
        assertEquals("Arriendo", primero.pago.nombre)
        assertEquals(-22, primero.pago.diasParaVencer)
        assertEquals(UrgenciaDelPendiente.VENCIDO, primero.urgencia)
        assertEquals(listOf("Arriendo", "Internet"), nombres(cola))
    }

    @Test
    fun `las listas enteras usan el mismo orden y lo de mas adelante va por fecha`() {
        val ordenados = pagosEnOrdenDeUrgencia(
            listOf(
                pago("Lejano grande", 5_000_000, dias = 25),
                pago("Lejano chico", 10_000, dias = 12),
                pago("Semana", 50_000, dias = 3),
                pago("Vencido", 20_000, dias = -4),
                pago("Pagado", 1, dias = -9, pagado = true),
            ),
        )
        assertEquals(listOf("Vencido", "Semana", "Lejano chico", "Lejano grande"), ordenados.map { it.nombre })
    }

    @Test
    fun `los movimientos por confirmar se cuentan como renglones`() {
        val patas = listOf(
            PataPorConfirmar("a", null, TransactionType.EXPENSE),
            PataPorConfirmar("t-sale", "t1", TransactionType.EXPENSE),
            PataPorConfirmar("t-entra", "t1", TransactionType.INCOME),
            // Una pata suelta de otro traspaso (la otra ya se confirmó): cuenta sola.
            PataPorConfirmar("u-sale", "t2", TransactionType.EXPENSE),
        )
        assertEquals(3, renglonesPorConfirmar(patas))
        assertEquals(0, renglonesPorConfirmar(emptyList()))
    }

    /**
     * Vuelta 49: la cuota que el banco cobra sola, vencida, es un fijo pendiente del checklist Y la
     * pregunta de «Lo que el banco cobró solo». En la cola entra una vez: la pregunta.
     */
    @Test
    fun `una cuota que el banco cobra sola entra una vez, como la pregunta del debito`() {
        val cuota = pago("Cuota Vehículo 4083", 980_000, dias = -23).copy(ruleId = "d-Cuota Vehículo 4083", seCobraSola = true)
        val cola = colaDePendientes(
            checklist = listOf(cuota, pago("Arriendo", 1_800_000, dias = -2)),
            debitos = listOf(debito("Cuota Vehículo 4083", 980_000)),
        )
        assertEquals(listOf("Arriendo", "débito Cuota Vehículo 4083"), nombres(cola))
        // Sin la pregunta (la deuda en $0, o «No se cobró»), la cuota sigue en la cola como vencida.
        assertEquals(listOf("Arriendo", "Cuota Vehículo 4083"), nombres(colaDePendientes(listOf(cuota, pago("Arriendo", 1_800_000, dias = -2)))))
    }
}
