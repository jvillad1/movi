package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El caso que motivó todo esto es el primer test: el «Salario» del 25 del dueño aparecía «Vencido
 * hace 1 día» mientras el ingreso ya estaba anotado, por un monto **parecido pero no igual** (una
 * retención). Si este test empieza a exigir monto exacto, la función dejó de servir para lo que
 * fue pedida.
 */
class OccurrenceMatchingTest {

    private val due = LocalDate.of(2026, 8, 25)

    private fun regla(
        name: String = "Salario",
        category: String = "Salario",
        amount: Long = 5_000_000,
        type: TransactionType = TransactionType.INCOME,
        accountId: String? = null,
    ) = RecurringRule("rr_1", name, category, amount, 25, type, accountId = accountId)

    private fun evento(
        id: String = "ev_1",
        day: Int = 25,
        month: Int = 8,
        amount: Long = 5_000_000,
        category: String = "Salario",
        description: String = "Salario",
        type: TransactionType = TransactionType.INCOME,
        accountId: String = "acc_1",
        transferId: String? = null,
        merchant: String? = null,
    ) = FinancialEvent(
        id = id,
        accountId = accountId,
        type = type,
        amount = amount,
        category = category,
        description = description,
        merchant = merchant,
        timestamp = appDateToEpochMillis(LocalDate.of(2026, month, day)),
        transferId = transferId,
    )

    // ── El caso del dueño ─────────────────────────────────────────────────────

    @Test fun `el salario con retencion sigue siendo candidato`() {
        // Anotó 5.000.000 como recurrente y este mes le entraron 4.780.000.
        val candidatos = occurrenceCandidatesFor(regla(), due, listOf(evento(amount = 4_780_000)))
        assertEquals(listOf("ev_1"), candidatos.map { it.id })
    }

    @Test fun `entre varios candidatos, el mas cercano al monto esperado va primero`() {
        val lejos = evento(id = "ev_lejos", amount = 1_000_000)
        val cerca = evento(id = "ev_cerca", amount = 4_900_000)
        val candidatos = occurrenceCandidatesFor(regla(), due, listOf(lejos, cerca))
        assertEquals(listOf("ev_cerca", "ev_lejos"), candidatos.map { it.id })
    }

    @Test fun `el nombre identifica mas que la categoria`() {
        // El de categoría suelta tiene el monto exacto; el que se llama igual, no. Gana el nombre:
        // el monto es lo variable, la identidad es lo estable.
        val soloCategoria = evento(id = "ev_cat", description = "Reintegro", amount = 5_000_000)
        val nombreIgual = evento(id = "ev_nom", category = "Otros ingresos", amount = 4_500_000)
        val candidatos = occurrenceCandidatesFor(regla(), due, listOf(soloCategoria, nombreIgual))
        assertEquals(listOf("ev_nom", "ev_cat"), candidatos.map { it.id })
    }

    // ── Las puertas cerradas ──────────────────────────────────────────────────

    @Test fun `un gasto nunca es la ocurrencia de un ingreso`() {
        val gasto = evento(type = TransactionType.EXPENSE)
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(gasto)).isEmpty())
    }

    @Test fun `una pata de traspaso no es una ocurrencia`() {
        val pata = evento(transferId = "tr_1", category = TRANSFER_CATEGORY, description = "Traspaso")
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(pata)).isEmpty())
    }

    @Test fun `las categorias reservadas nunca son candidatas`() {
        val apertura = evento(id = "ev_ap", category = OPENING_CATEGORY, description = "Salario")
        val pagoTarjeta = evento(
            id = "ev_tj",
            category = CARD_PAYMENT_CATEGORY,
            description = "Salario",
            type = TransactionType.EXPENSE,
        )
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(apertura)).isEmpty())
        assertTrue(
            occurrenceCandidatesFor(
                regla(type = TransactionType.EXPENSE),
                due,
                listOf(pagoTarjeta),
            ).isEmpty(),
        )
    }

    @Test fun `un movimiento ya usado como ocurrencia no se vuelve a proponer`() {
        val candidatos = occurrenceCandidatesFor(regla(), due, listOf(evento()), usedEventIds = setOf("ev_1"))
        assertTrue(candidatos.isEmpty())
    }

    @Test fun `fuera de la ventana no hay propuesta`() {
        // El 5 de agosto está a 20 días del vencimiento del 25: es el sueldo de otro mes.
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(evento(day = 5))).isEmpty())
        // Y el borde exacto sí entra.
        assertEquals(1, occurrenceCandidatesFor(regla(), due, listOf(evento(day = 15))).size)
    }

    @Test fun `sin ninguna sena de identidad no se propone nada`() {
        // Mismo tipo, misma ventana, pero ni el nombre ni la categoría pegan y la regla no dice
        // cuenta. Proponer esto sería invitar a cerrar el mes con cualquier ingreso.
        val ajeno = evento(description = "Venta de la moto", category = "Otros ingresos")
        assertTrue(occurrenceCandidatesFor(regla(), due, listOf(ajeno)).isEmpty())
    }

    /**
     * **Regresión del hallazgo ALTA-2.** La cuenta no mira el movimiento: decir «esta regla tiene
     * cuenta» no dice NADA sobre si un gasto de esa cuenta es el arriendo. Con la cuenta como seña
     * suficiente, la lista de gastos que tiene una cuenta de verdad entraba entera.
     *
     * El test de antes probaba la cuenta con UN movimiento, que es exactamente el escenario donde
     * el bug no se ve.
     */
    @Test fun `una cuenta poblada no convierte cualquier gasto en el arriendo`() {
        val arriendo = RecurringRule(
            "rr_arriendo", "Arriendo", "Vivienda", 1_800_000, 25,
            TransactionType.EXPENSE, accountId = "acc_1",
        )
        val gastosDeLaCuenta = listOf(
            evento(id = "ev_exito", amount = 1_750_000, category = "Mercado", description = "Compra Exito", type = TransactionType.EXPENSE),
            evento(id = "ev_monitor", amount = 2_100_000, category = "Tecnologia", description = "Monitor", type = TransactionType.EXPENSE),
            evento(id = "ev_energia", amount = 260_000, category = "Servicios", description = "Energia", type = TransactionType.EXPENSE),
        )
        assertTrue(
            occurrenceCandidatesFor(arriendo, due, gastosDeLaCuenta).isEmpty(),
            "el mercado del Éxito no puede proponerse como el arriendo solo por estar en la misma cuenta",
        )
        // Y el arriendo de verdad, que sí comparte la categoría, sí se propone.
        val elArriendo = evento(id = "ev_arriendo", amount = 1_800_000, category = "Vivienda", description = "Pago arriendo", type = TransactionType.EXPENSE)
        assertEquals(
            listOf("ev_arriendo"),
            occurrenceCandidatesFor(arriendo, due, gastosDeLaCuenta + elArriendo).map { it.id },
        )
    }

    @Test fun `la cuenta suma como sena pero ya no filtra`() {
        val reglaConCuenta = regla(accountId = "acc_1")
        // Mismo nombre pero en OTRA cuenta: se propone igual. Antes desaparecía, y un nombre
        // idéntico pesando menos que la cuenta es raro.
        val enOtra = evento(id = "ev_nequi", accountId = "acc_2")
        assertEquals(listOf("ev_nequi"), occurrenceCandidatesFor(reglaConCuenta, due, listOf(enOtra)).map { it.id })
        // Y con dos iguales, el que está en la cuenta de la regla va primero.
        val enLaCuenta = evento(id = "ev_banco", accountId = "acc_1")
        assertEquals(
            listOf("ev_banco", "ev_nequi"),
            occurrenceCandidatesFor(reglaConCuenta, due, listOf(enOtra, enLaCuenta)).map { it.id },
        )
    }

    /**
     * **Regresión del hallazgo ALTA-1.** La ventana de ±10 días también iba hacia atrás, así que
     * para una regla de día bajo proponía el pago del mes ANTERIOR para cerrar este. Con el monto
     * exacto, además, así que ni siquiera salía el aviso de «no es el monto que anotaste».
     */
    @Test fun `nunca se propone un movimiento anterior al mes del vencimiento`() {
        val arriendo = RecurringRule(
            "rr_arriendo", "Arriendo", "Vivienda", 1_800_000, 1, TransactionType.EXPENSE,
        )
        val vencimientoDeSeptiembre = LocalDate.of(2026, 9, 1)
        val pagoDeAgosto = evento(
            id = "ev_agosto", day = 25, month = 8, amount = 1_800_000,
            category = "Vivienda", description = "Arriendo", type = TransactionType.EXPENSE,
        )
        assertTrue(
            occurrenceCandidatesFor(arriendo, vencimientoDeSeptiembre, listOf(pagoDeAgosto)).isEmpty(),
            "el arriendo de agosto no puede cerrar el vencimiento de septiembre",
        )
        // El de septiembre sí, aunque llegue tarde.
        val pagoDeSeptiembre = pagoDeAgosto.copy(
            id = "ev_septiembre",
            timestamp = appDateToEpochMillis(LocalDate.of(2026, 9, 3)),
        )
        assertEquals(
            listOf("ev_septiembre"),
            occurrenceCandidatesFor(arriendo, vencimientoDeSeptiembre, listOf(pagoDeSeptiembre)).map { it.id },
        )
    }

    @Test fun `se muestran como mucho tres propuestas`() {
        val muchos = (1..6).map { evento(id = "ev_$it", amount = 5_000_000L + it) }
        assertEquals(3, occurrenceCandidatesFor(regla(), due, muchos).size)
    }

    // ── El rodado del vencimiento y el barrido ────────────────────────────────

    @Test fun `un periodo dado por ocurrido deja de leerse como vencido`() {
        val rule = regla(type = TransactionType.EXPENSE)
        val hoy = LocalDate.of(2026, 8, 26)
        // Sin cerrar: vencido ayer.
        assertEquals(PaymentStatus.OVERDUE, statusFor(dueDateFor(rule, hoy), hoy, 3))
        // Cerrado agosto: el vencimiento vigente pasa a ser el 25 de septiembre.
        val con = dueDateFor(rule, hoy, DEFAULT_GRACE_DAYS, setOf("2026-08"))
        assertEquals(LocalDate.of(2026, 9, 25), con)
        assertEquals(PaymentStatus.UPCOMING, statusFor(con, hoy, 3))
    }

    @Test fun `al mes siguiente vuelve a estar pendiente`() {
        val rule = regla(type = TransactionType.EXPENSE)
        val enSeptiembre = LocalDate.of(2026, 9, 26)
        val due = dueDateFor(rule, enSeptiembre, DEFAULT_GRACE_DAYS, setOf("2026-08"))
        assertEquals(LocalDate.of(2026, 9, 25), due)
        assertEquals(PaymentStatus.OVERDUE, statusFor(due, enSeptiembre, 3))
    }

    @Test fun `el barrido no avisa de lo ya ocurrido, y si del mes siguiente`() {
        val rule = regla(name = "Arriendo", category = "Vivienda", type = TransactionType.EXPENSE)
        val hoy = LocalDate.of(2026, 8, 26)
        val pares = listOf(rule to null)
        assertEquals(listOf("rr_1"), selectDueForReminder(pares, hoy, 3).map { it.id })
        assertTrue(selectDueForReminder(pares, hoy, 3, mapOf("rr_1" to setOf("2026-08"))).isEmpty())
        // Septiembre sí, aunque agosto siga cerrado.
        val enSeptiembre = LocalDate.of(2026, 9, 25)
        assertEquals(
            listOf("rr_1"),
            selectDueForReminder(pares, enSeptiembre, 3, mapOf("rr_1" to setOf("2026-08"))).map { it.id },
        )
    }

    @Test fun `el sello del aviso y el filtro miran el MISMO periodo`() {
        // Si divergieran, el mismo vencimiento se notificaría dos veces — el bug que
        // `reminderKeyFor` ya arregló una vez. Con un periodo cerrado la clave tiene que rodar
        // igual que la fecha.
        val rule = regla(type = TransactionType.EXPENSE)
        val hoy = LocalDate.of(2026, 8, 26)
        val ocurridos = setOf("2026-08")
        assertEquals("2026-09", reminderKeyFor(rule, hoy, DEFAULT_GRACE_DAYS, ocurridos))
        assertEquals("2026-08", reminderKeyFor(rule, hoy))
    }

    @Test fun `un ingreso nunca entra al barrido, ocurrido o no`() {
        // Regresión: el barrido solo mira gastos. Cerrar el salario no debe cambiar eso.
        val pares = listOf(regla() to null)
        assertFalse(selectDueForReminder(pares, LocalDate.of(2026, 8, 26), 3).any { it.id == "rr_1" })
    }
}
