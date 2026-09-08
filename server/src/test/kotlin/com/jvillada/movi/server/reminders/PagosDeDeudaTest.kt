package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Los cuatro pagos que la app ignoraba.** Palabras del dueño sobre su propia base:
 * *«"Ya ocurrieron · 1" esto es falso, de hecho todos los que registran movimientos ocurrieron»*.
 *
 * Los casos de acá son literalmente los suyos, con los días reales de sus créditos y tarjetas:
 * Crediágil y Libre inversión (día 15) pagados el 5 de septiembre, Nu (día 1) pagada el 5, y AMEX
 * (día 16) pagada el 30 de agosto — la que ninguna ventana centrada en el vencimiento atrapa.
 *
 * Lo que se prueba no es que la función devuelva algo: es **qué NO cuenta como pago**. Dar por
 * pagada una cuota que nadie pagó apaga el aviso de una deuda real, y eso cuesta plata.
 */
class PagosDeDeudaTest {

    private val cuentaDelCredito = "acc_crediagil"
    private val cuentaDeLaTarjeta = "acc_amex"
    private val cuentaDeAhorros = "acc_bancolombia"

    private fun reglaDeCredito(dia: Int = 15, desde: String? = null) = RecurringRule(
        id = "$CREDIT_RULE_PREFIX$cuentaDelCredito",
        name = "Cuota Crediágil 3090",
        category = "Créditos",
        amount = 26_485,
        dayOfMonth = dia,
        type = TransactionType.EXPENSE,
        activeFrom = desde,
    )

    private fun reglaDeTarjeta(dia: Int = 16) = RecurringRule(
        id = "$CARD_RULE_PREFIX$cuentaDeLaTarjeta",
        name = "Pago tarjeta AMEX 9208",
        category = "Créditos",
        amount = 19_818_701,
        montoEsSaldo = true,
        dayOfMonth = dia,
        type = TransactionType.EXPENSE,
    )

    private fun pago(
        id: String = "ev_1",
        cuenta: String,
        categoria: String,
        fecha: LocalDate,
        tipo: TransactionType = TransactionType.INCOME,
        monto: Long = 26_485,
    ) = FinancialEvent(
        id = id,
        accountId = cuenta,
        type = tipo,
        amount = monto,
        category = categoria,
        description = "Pago desde Bancolombia Ahorros",
        timestamp = appDateToEpochMillis(fecha),
        transferId = "tr_1",
    )

    // ── Los cuatro casos del dueño ────────────────────────────────────────────

    @Test fun `la cuota pagada el 5 salda el vencimiento del 15 de ese mes`() {
        val regla = reglaDeCredito(dia = 15)
        val pagos = listOf(
            pago(cuenta = cuentaDelCredito, categoria = CUOTA_CATEGORY, fecha = LocalDate.of(2026, 9, 5)),
        )
        assertEquals(setOf("2026-09"), periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    /**
     * **El caso AMEX, y el que decide la forma de esta función.** Pagó el 30 de agosto una tarjeta
     * que vence el 16. Para el 30 de agosto la app ya no hablaba del 16 de agosto —pasada la
     * ventana de gracia el vencimiento vigente rueda— sino del **16 de septiembre**: eso es lo que
     * el dueño estaba pagando y eso es lo que se salda.
     *
     * Una ventana centrada en el vencimiento (±10 días, como la de `occurrenceCandidatesFor`)
     * habría dejado este pago sin dueño: el 30 de agosto no cae ni cerca del 16 de agosto ni del
     * 16 de septiembre. Si alguien "simplifica" [periodoQueSalda] a una ventana, este test cae.
     */
    @Test fun `el pago hecho pasada la gracia salda el vencimiento siguiente`() {
        val regla = reglaDeTarjeta(dia = 16)
        val pagos = listOf(
            pago(
                cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
                fecha = LocalDate.of(2026, 8, 30), monto = 1_008_902,
            ),
        )
        assertEquals(setOf("2026-09"), periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    /**
     * El caso Nu: vence el 1, pagó el 5. Dentro de la gracia, así que salda **septiembre** y no
     * octubre — si saldara octubre, el pago de septiembre seguiría figurando como pendiente y el
     * de octubre no avisaría. Los dos errores a la vez.
     */
    @Test fun `el pago dentro de la gracia salda el vencimiento que acaba de pasar`() {
        val regla = reglaDeTarjeta(dia = 1)
        val pagos = listOf(
            pago(
                cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
                fecha = LocalDate.of(2026, 9, 5), monto = 115_113,
            ),
        )
        assertEquals(setOf("2026-09"), periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    // ── Lo que NO cuenta como pago ────────────────────────────────────────────

    /**
     * **La otra pata del traspaso lleva la MISMA categoría**, y no salda nada: la plata que sale
     * de la cuenta de ahorros no bajó ninguna deuda.
     *
     * Este caso lo detienen DOS puertas a la vez —la cuenta y el tipo— porque la pata de ahorros
     * es EXPENSE por naturaleza, así que **no aísla el filtro por cuenta**; quien lo aísla es
     * `la cuota de otro credito no salda esta`, donde todo lo demás coincide. Se deja igual
     * porque es el escenario REAL que se quiere fijar (así escribe `pagoDeCuotaLegs`), pero
     * conviene decir qué prueba y qué no: comprobado mutando los tres filtros uno por uno.
     */
    @Test fun `la pata que sale de la cuenta de ahorros no salda nada`() {
        val regla = reglaDeCredito()
        val pagos = listOf(
            pago(
                cuenta = cuentaDeAhorros, categoria = CUOTA_CATEGORY,
                fecha = LocalDate.of(2026, 9, 5), tipo = TransactionType.EXPENSE,
            ),
        )
        assertNull(periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    /**
     * **La devolución de una compra tampoco es el pago del extracto.** Vive en la misma cuenta y
     * es INCOME —baja la deuda, igual que un pago— así que la categoría es la única puerta que la
     * detiene. Si la tarjeta se diera por pagada porque le devolvieron un mercado, el extracto de
     * verdad dejaría de avisar.
     */
    @Test fun `una devolucion en la tarjeta no salda el pago`() {
        val regla = reglaDeTarjeta()
        val pagos = listOf(
            pago(
                cuenta = cuentaDeLaTarjeta, categoria = "Mercado",
                fecha = LocalDate.of(2026, 9, 5), tipo = TransactionType.INCOME,
            ),
        )
        assertNull(periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    /**
     * **La cuota de OTRO crédito no salda este.** Las dos patas buenas se ven igual —misma
     * categoría, mismo tipo, misma fecha— y solo la cuenta las distingue. Sin ese filtro, pagar
     * Crediágil daría por pagada también la Libre inversión: una sola cuota apagando dos avisos.
     */
    @Test fun `la cuota de otro credito no salda esta`() {
        val regla = reglaDeCredito()
        val pagos = listOf(
            pago(
                cuenta = "acc_otro_credito", categoria = CUOTA_CATEGORY,
                fecha = LocalDate.of(2026, 9, 5),
            ),
        )
        assertNull(periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    /**
     * Un cargo sobre la cuenta de la deuda (un interés, una cuota de manejo) **sube** la deuda. Con
     * la categoría puesta a mano pasa las otras dos puertas, así que el tipo es la que queda: en
     * una cuenta de deuda, lo que la baja es INCOME.
     */
    @Test fun `un cargo sobre la cuenta de la deuda no salda nada`() {
        val regla = reglaDeTarjeta()
        val pagos = listOf(
            pago(
                cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
                fecha = LocalDate.of(2026, 9, 5), tipo = TransactionType.EXPENSE,
            ),
        )
        assertNull(periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    /**
     * Una regla REAL (el arriendo, el gimnasio) no deriva nada de acá: su «ya ocurrió» se sella en
     * `recurring_occurrences`, con el dueño confirmando. Derivarle un periodo por parecido de
     * categoría sería exactamente el segundo mecanismo que este diseño evita.
     */
    @Test fun `una regla real no deriva ningun periodo`() {
        val regla = RecurringRule(
            id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
            amount = 1_800_000, dayOfMonth = 5, type = TransactionType.EXPENSE,
        )
        val pagos = listOf(
            pago(cuenta = "rr_arriendo", categoria = CUOTA_CATEGORY, fecha = LocalDate.of(2026, 9, 5)),
        )
        assertTrue(periodosSaldados(listOf(regla), pagos).isEmpty())
    }

    /** Dos pagos dentro del mismo ciclo saldan un periodo, no dos. */
    @Test fun `dos abonos en el mismo ciclo saldan un solo periodo`() {
        val regla = reglaDeTarjeta(dia = 16)
        val pagos = listOf(
            pago(
                id = "ev_a", cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
                fecha = LocalDate.of(2026, 9, 3),
            ),
            pago(
                id = "ev_b", cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
                fecha = LocalDate.of(2026, 9, 10),
            ),
        )
        assertEquals(setOf("2026-09"), periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    /**
     * Meses distintos, periodos distintos: saldar septiembre no puede saldar octubre. Es la misma
     * garantía que tiene el sello a mano —cerrar agosto no dice nada de septiembre— y sin ella un
     * solo pago apagaría el aviso de todos los meses que vienen.
     */
    @Test fun `cada ciclo se salda por separado`() {
        val regla = reglaDeCredito(dia = 15)
        val pagos = listOf(
            pago(
                id = "ev_a", cuenta = cuentaDelCredito, categoria = CUOTA_CATEGORY,
                fecha = LocalDate.of(2026, 9, 5),
            ),
            pago(
                id = "ev_b", cuenta = cuentaDelCredito, categoria = CUOTA_CATEGORY,
                fecha = LocalDate.of(2026, 10, 14),
            ),
        )
        assertEquals(setOf("2026-09", "2026-10"), periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    // ── Cómo se enchufa con el resto ──────────────────────────────────────────

    /**
     * El efecto que el dueño reclamó, extremo a extremo sobre la función que pinta «Próximos»: con
     * la cuota registrada, el vencimiento vigente pasa a ser el del mes que viene y el estado deja
     * de ser «vencido». **Y sin ningún valor nuevo en `PaymentStatus`**: es la misma fecha rodada
     * de siempre, la que un cliente viejo entiende sin saber que esta función existe.
     */
    @Test fun `una cuota registrada deja de leerse como vencida`() {
        val regla = reglaDeCredito(dia = 1)
        val hoy = LocalDate.of(2026, 9, 3)
        val sinPago = upcomingPayments(listOf(regla), hoy, leadDays = 3).single()
        assertEquals("2026-09-01", sinPago.dueDate)

        val pagos = listOf(
            pago(cuenta = cuentaDelCredito, categoria = CUOTA_CATEGORY, fecha = LocalDate.of(2026, 9, 2)),
        )
        val conPago = upcomingPayments(
            listOf(regla), hoy, leadDays = 3, occurredBy = periodosSaldados(listOf(regla), pagos),
        ).single()
        assertEquals("2026-10-01", conPago.dueDate)
    }

    /**
     * **Y tampoco avisa por correo.** Mandarle «tu cuota vence» a alguien que ya pagó es la misma
     * mentira que decirle «Vencido hace 5 días» en la pantalla, y salía del mismo agujero. No hace
     * falta ningún filtro nuevo en el barrido: el vencimiento vigente de una cuota pagada ya es el
     * del mes que viene, así que cae en UPCOMING y sale por el criterio que ya estaba.
     */
    @Test fun `una cuota registrada deja de avisar ese mes`() {
        val regla = reglaDeCredito(dia = 1)
        val hoy = LocalDate.of(2026, 9, 3)
        val pares = listOf(regla to null)
        assertEquals(
            listOf(regla.id), selectDueForReminder(pares, hoy, leadDays = 3).map { it.id },
            "Sin el pago registrado, la cuota sí entra al barrido",
        )
        val pagos = listOf(
            pago(cuenta = cuentaDelCredito, categoria = CUOTA_CATEGORY, fecha = LocalDate.of(2026, 9, 2)),
        )
        assertTrue(
            selectDueForReminder(pares, hoy, leadDays = 3, occurredBy = periodosSaldados(listOf(regla), pagos))
                .isEmpty(),
            "Con la cuota registrada, el barrido no la selecciona",
        )
    }

    /** Lo sellado a mano y lo derivado conviven: unir no puede perder ninguno de los dos. */
    @Test fun `unirOcurridos no pierde periodos`() {
        val unido = unirOcurridos(
            selladas = mapOf("rr_1" to setOf("2026-08")),
            derivadas = mapOf("credit_x" to setOf("2026-09"), "rr_1" to setOf("2026-09")),
        )
        assertEquals(setOf("2026-08", "2026-09"), unido["rr_1"])
        assertEquals(setOf("2026-09"), unido["credit_x"])
    }
}
