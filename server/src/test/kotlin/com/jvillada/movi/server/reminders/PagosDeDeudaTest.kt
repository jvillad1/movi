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
 * (día 16) pagada el 30 de agosto.
 *
 * Lo que se prueba no es que la función devuelva algo: es **qué NO cuenta como pago** y **a qué mes
 * NO se le atribuye**. Dar por pagada una cuota que nadie pagó apaga el aviso de una deuda real, y
 * eso cuesta plata — y atribuirle el pago a un vencimiento que todavía no llegó es la misma cosa
 * con otra forma: deja el mes que sí venció figurando abierto y apaga el aviso del que viene.
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
     * **El caso AMEX — y este test cambió de respuesta.** Antes exigía «septiembre»; ahora exige
     * **agosto**, que es el mes que de verdad venció antes del pago.
     *
     * Pagó el 30 de agosto una tarjeta que vence el 16. Para esa fecha `dueDateFor` ya había
     * rodado al 16 de SEPTIEMBRE (pasada la gracia rueda), así que atribuirle el pago a ese
     * vencimiento era darle por saldado un mes que todavía no había llegado. El mismo movimiento
     * tiene dos lecturas —adelantó el extracto de septiembre, o pagó el de agosto con dos semanas
     * de atraso— y movi no conoce el extracto para distinguirlas; lo que sí se puede comparar es el
     * costo de equivocarse, y **no es simétrico**: darlo por pagado de más apaga el aviso del 16 de
     * septiembre y se paga con mora, darlo por pagado de menos cuesta un recordatorio que se
     * descarta en dos segundos. Se elige el lado barato. (Todo el argumento, en `PagosDeDeuda.kt`.)
     */
    @Test fun `el pago hecho pasada la gracia salda el vencimiento que ya paso, no el que viene`() {
        val regla = reglaDeTarjeta(dia = 16)
        val pagos = listOf(
            pago(
                cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
                fecha = LocalDate.of(2026, 8, 30), monto = 1_008_902,
            ),
        )
        assertEquals(
            setOf("2026-08"), periodosSaldados(listOf(regla), pagos)[regla.id],
            "Un pago no puede saldar un vencimiento que todavía no llegó",
        )
    }

    /**
     * **El caso Nu, el que costaba plata.** Vence el 1, pagó el **7**: un día pasada la ventana de
     * gracia, así que `dueDateFor` ya apuntaba al 1 de octubre y el pago le saldaba OCTUBRE.
     * Resultado: septiembre seguía apareciendo «vencido» —el reclamo original, intacto— y el aviso
     * del 1 de octubre no salía. Los dos errores a la vez, y el segundo se paga con mora.
     */
    @Test fun `el pago hecho un dia tarde salda el mes que vencio, no el siguiente`() {
        val regla = reglaDeTarjeta(dia = 1)
        val pagos = listOf(
            pago(
                cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
                fecha = LocalDate.of(2026, 9, 7), monto = 115_113,
            ),
        )
        assertEquals(setOf("2026-09"), periodosSaldados(listOf(regla), pagos)[regla.id])
    }

    /**
     * Y la consecuencia que se paga en plata: **el vencimiento siguiente sigue avisando**. Con la
     * atribución vieja el pago tardío del 7 de septiembre saldaba octubre, así que el barrido del
     * 1 de octubre no mandaba nada por una cuota que sí se debía.
     */
    @Test fun `el pago tardio no apaga el aviso del mes siguiente`() {
        val regla = reglaDeTarjeta(dia = 1)
        val pagos = listOf(
            pago(
                cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
                fecha = LocalDate.of(2026, 9, 7), monto = 115_113,
            ),
        )
        val saldados = periodosSaldados(listOf(regla), pagos)
        assertEquals(
            listOf(regla.id),
            selectDueForReminder(listOf(regla to null), LocalDate.of(2026, 10, 1), leadDays = 3, occurredBy = saldados)
                .map { it.id },
            "El 1 de octubre vence de nuevo: el pago de septiembre no puede apagar ese aviso",
        )
    }

    /**
     * El caso Nu del reclamo: vence el 1, pagó el 5. Dentro de la gracia, así que salda
     * **septiembre** y no octubre — si saldara octubre, el pago de septiembre seguiría figurando
     * como pendiente y el de octubre no avisaría. Los dos errores a la vez.
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

    /**
     * Dos pagos dentro del mismo ciclo saldan un periodo, no dos — **y gana el último**.
     *
     * Lo segundo no es un detalle: la ruta publica `pago.id` y `pago.timestamp` de ese ganador, o
     * sea que de él salen el movimiento que la fila dice que la prueba, el monto que muestra y la
     * fecha. Sin fijarlo, sacar el `sortedBy { it.timestamp }` de [pagosDeDeudaPorPeriodo] dejaba
     * el test en verde y la fila pasaba a mostrar el abono que la base devolviera primero.
     */
    @Test fun `dos abonos en el mismo ciclo saldan un solo periodo, y gana el ultimo`() {
        val regla = reglaDeTarjeta(dia = 16)
        val primero = pago(
            id = "ev_a", cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
            fecha = LocalDate.of(2026, 9, 3), monto = 50_000,
        )
        val ultimo = pago(
            id = "ev_b", cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
            fecha = LocalDate.of(2026, 9, 10), monto = 900_000,
        )
        // En los dos órdenes de llegada, para que lo que decida sea la fecha y no la lista.
        listOf(listOf(primero, ultimo), listOf(ultimo, primero)).forEach { pagos ->
            assertEquals(setOf("2026-09"), periodosSaldados(listOf(regla), pagos)[regla.id])
            assertEquals(
                "ev_b", pagosDeDeudaPorPeriodo(listOf(regla), pagos)[regla.id]?.get("2026-09")?.id,
                "El movimiento que se publica es el último del ciclo, no el que llegó primero",
            )
        }
    }

    /**
     * **La plata que salió de la cuenta, no la que bajó la deuda.** En una cuota son distintas a
     * propósito: la deuda baja por el capital ($12.157) y de la cuenta salió la cuota entera
     * ($26.485). La fila de «Ya ocurrieron» muestra este número, así que tomar el equivocado le
     * diría al dueño que pagó la mitad de lo que pagó.
     */
    @Test fun `la plata que salio es la otra pata del traspaso`() {
        val pataDeLaDeuda = pago(
            id = "ev_deuda", cuenta = cuentaDelCredito, categoria = CUOTA_CATEGORY,
            fecha = LocalDate.of(2026, 9, 5), monto = 12_157,
        )
        val pataDelDinero = pago(
            id = "ev_dinero", cuenta = cuentaDeAhorros, categoria = CUOTA_CATEGORY,
            fecha = LocalDate.of(2026, 9, 5), tipo = TransactionType.EXPENSE, monto = 26_485,
        )
        assertEquals(26_485, plataQueSalio(pataDeLaDeuda, listOf(pataDeLaDeuda, pataDelDinero)).amount)
    }

    /** Sin traspaso —un pago suelto, o importado— se muestra lo único que se sabe. */
    @Test fun `sin la otra pata se cae al mismo movimiento`() {
        val suelto = pago(
            id = "ev_solo", cuenta = cuentaDeLaTarjeta, categoria = CARD_PAYMENT_CATEGORY,
            fecha = LocalDate.of(2026, 9, 5), monto = 115_113,
        ).copy(transferId = null)
        assertEquals(115_113, plataQueSalio(suelto, listOf(suelto)).amount)
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
