package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CuentaDelDisponible
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.pagosDeDeudaFueraDelChecklist
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **La cuota pagada después del corte del dueño salda la del período en que se pagó.**
 *
 * Descubierto el 27-sep-2026 con los pagos reales del dueño (corte 25): Crediágil 3090 (día 15),
 * pagada el 5 de septiembre y otra vez el 27. Las dos patas del traspaso estaban bien —la deuda
 * bajó— pero el Plan seguía mostrando la cuota del 15 de octubre en «Falta por pagar», y
 * «Disponible» restaba el pago dos veces (como fijo y como «otro pago de deuda»).
 *
 * La causa: el tope de [periodoQueSalda] («un pago no salda un vencimiento que todavía no llegó»)
 * usaba el MES DE CALENDARIO del pago. El 27 de septiembre es septiembre por calendario, pero es el
 * período de OCTUBRE del dueño (25-sep a 24-oct), y el vencimiento de septiembre ya lo había saldado
 * el pago del 5. Los dos pagos caían en la misma clave y el del 15 de octubre no lo saldaba nadie.
 *
 * Todos los casos con corte 25 salvo donde se dice.
 */
class PagoDespuesDelCorteTest {

    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val calendario = PeriodSettings()

    private val cuentaDelCredito = "acc_crediagil"
    private val cuentaDeLaTarjeta = "acc_amex"
    private val cuentaDeAhorros = "acc_bancolombia"

    private val crediagil = RecurringRule(
        id = "$CREDIT_RULE_PREFIX$cuentaDelCredito",
        name = "Cuota Crediágil 3090",
        category = CUOTA_CATEGORY,
        amount = 26_485,
        dayOfMonth = 15,
        type = TransactionType.EXPENSE,
    )

    private fun tarjeta(dia: Int) = RecurringRule(
        id = "$CARD_RULE_PREFIX$cuentaDeLaTarjeta",
        name = "Pago tarjeta AMEX 9208",
        category = CUOTA_CATEGORY,
        amount = 19_818_701,
        montoEsSaldo = true,
        dayOfMonth = dia,
        type = TransactionType.EXPENSE,
    )

    /** La pata que baja la deuda (INCOME en la cuenta del crédito), a las 3 p. m. de [fecha]. */
    private fun abonoAlCredito(id: String, fecha: LocalDate, monto: Long = 12_157) = FinancialEvent(
        id = id,
        accountId = cuentaDelCredito,
        type = TransactionType.INCOME,
        amount = monto,
        category = CUOTA_CATEGORY,
        description = "Pago desde Bancolombia Ahorros",
        timestamp = appDateToEpochMillis(fecha) + 15 * 3_600_000L,
        transferId = "tr_$id",
        // Fuera de «Por confirmar»: si no, `pagosDeDeudaFueraDelChecklist` lo ignora y la prueba no prueba nada.
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    /** La otra pata: la plata que salió de la cuenta de ahorros. */
    private fun salidaDeAhorros(id: String, fecha: LocalDate, monto: Long = 26_485) = FinancialEvent(
        id = "${id}_sale",
        accountId = cuentaDeAhorros,
        type = TransactionType.EXPENSE,
        amount = monto,
        category = CUOTA_CATEGORY,
        description = "Pago cuota Crediágil",
        timestamp = appDateToEpochMillis(fecha) + 15 * 3_600_000L,
        transferId = "tr_$id",
        // Fuera de «Por confirmar»: si no, `pagosDeDeudaFueraDelChecklist` lo ignora y la prueba no prueba nada.
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private fun pagoDeTarjeta(id: String, fecha: LocalDate, monto: Long = 1_008_902) = FinancialEvent(
        id = id,
        accountId = cuentaDeLaTarjeta,
        type = TransactionType.INCOME,
        amount = monto,
        category = CARD_PAYMENT_CATEGORY,
        description = "Pago tarjeta",
        timestamp = appDateToEpochMillis(fecha) + 15 * 3_600_000L,
        transferId = "tr_$id",
        // Fuera de «Por confirmar»: si no, `pagosDeDeudaFueraDelChecklist` lo ignora y la prueba no prueba nada.
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private val cinco = LocalDate.of(2026, 9, 5)
    private val veintisiete = LocalDate.of(2026, 9, 27)

    // ── 1. El caso del dueño, exacto ──────────────────────────────────────────

    @Test fun `el pago del 27 salda la cuota de octubre cuando la de septiembre ya estaba pagada`() {
        val pagos = listOf(abonoAlCredito("ev_0905", cinco), abonoAlCredito("ev_0927", veintisiete))
        // En los dos órdenes de llegada: decide la fecha, no la lista.
        listOf(pagos, pagos.reversed()).forEach { lista ->
            val porPeriodo = pagosDeDeudaPorPeriodo(listOf(crediagil), lista, settings = corte25)[crediagil.id]!!
            assertEquals(setOf("2026-09", "2026-10"), porPeriodo.keys)
            assertEquals("ev_0905", porPeriodo["2026-09"]?.id)
            assertEquals("ev_0927", porPeriodo["2026-10"]?.id, "El pago del 27-sep es la cuota del 15-oct")
        }
    }

    @Test fun `despues del segundo pago el 15 de octubre ya no esta pendiente`() {
        val pagos = listOf(abonoAlCredito("ev_0905", cinco), abonoAlCredito("ev_0927", veintisiete))
        val saldados = periodosSaldados(listOf(crediagil), pagos, settings = corte25)

        val vigente = dueDateFor(crediagil, veintisiete, occurredPeriods = saldados[crediagil.id].orEmpty(), settings = corte25)
        assertNotEquals(LocalDate.of(2026, 10, 15), vigente, "El 15-oct ya está pagado")
        assertEquals(LocalDate.of(2026, 11, 15), vigente)

        // Y la fila de «Ya ocurrieron» que arma `/api/payments/occurrences` sale con el pago del 27.
        val porPreguntar = ocurrenciaPorPreguntar(veintisiete, crediagil, corte25)!!
        assertEquals(LocalDate.of(2026, 10, 15), porPreguntar)
        assertEquals(
            "ev_0927",
            pagosDeDeudaPorPeriodo(listOf(crediagil), pagos, settings = corte25)[crediagil.id]?.get(periodOf(porPreguntar))?.id,
        )

        // Y el barrido de avisos no manda «tu cuota vence» por una cuota que ya se pagó.
        assertTrue(
            selectDueForReminder(listOf(crediagil to null), LocalDate.of(2026, 10, 12), 3, saldados, corte25).isEmpty(),
        )
    }

    /**
     * **El Disponible no resta el pago dos veces.** El Dashboard le pasa a [cuotasDelChecklistPagadas]
     * los movimientos del PERÍODO (25-sep a 24-oct), donde el pago del 5 no está: sin el historial de
     * pagos de deuda no hay forma de saber que septiembre ya estaba saldado.
     */
    @Test fun `el pago del 27 no se cuenta como otro pago de deuda`() {
        val delPeriodo = listOf(abonoAlCredito("ev_0927", veintisiete), salidaDeAhorros("ev_0927", veintisiete))
        val historial = listOf(
            abonoAlCredito("ev_0905", cinco), salidaDeAhorros("ev_0905", cinco),
        ) + delPeriodo
        val cuentas = mapOf(
            cuentaDeAhorros to CuentaDelDisponible(AccountType.SAVINGS, null),
            cuentaDelCredito to CuentaDelDisponible(AccountType.LOAN, null),
        )

        val enLosFijos = cuotasDelChecklistPagadas(
            listOf(crediagil), delPeriodo, veintisiete, corte25, historialDePagosDeDeuda = historial,
        )
        // Control: sin nada en los fijos, el pago sí cuenta como otro pago de deuda. Si esto diera 0,
        // la aserción de abajo no probaría nada.
        assertEquals(26_485L, pagosDeDeudaFueraDelChecklist(delPeriodo, cuentas, emptyMap()))
        assertEquals(mapOf("ev_0927_sale" to 26_485L), enLosFijos, "La cuota del 15-oct la pagó el 27-sep")
        assertEquals(0L, pagosDeDeudaFueraDelChecklist(delPeriodo, cuentas, enLosFijos))
    }

    // ── 2. El caso AMEX no se rompe ───────────────────────────────────────────

    /**
     * El ejemplo del KDoc de `PagosDeDeuda.kt`: AMEX (día 16) pagada el 30 de agosto, sin ningún
     * pago anterior. Es ambiguo —adelantó septiembre o pagó agosto tarde— y se elige el lado barato:
     * agosto. Con calendario y con corte 25.
     */
    @Test fun `AMEX pagada el 30 de agosto sigue saldando agosto`() {
        val amex = tarjeta(dia = 16)
        val pagos = listOf(pagoDeTarjeta("ev_0830", LocalDate.of(2026, 8, 30)))
        listOf(calendario, corte25).forEach { settings ->
            assertEquals(setOf("2026-08"), periodosSaldados(listOf(amex), pagos, settings = settings)[amex.id], "$settings")
        }
    }

    /** Y un pago anterior que NO cae en el período del vencimiento de agosto no cambia nada. */
    @Test fun `un pago de hace dos periodos no convierte el de AMEX en adelanto`() {
        val amex = tarjeta(dia = 16)
        val pagos = listOf(
            pagoDeTarjeta("ev_0720", LocalDate.of(2026, 7, 20)), // período 25-jun a 24-jul
            pagoDeTarjeta("ev_0830", LocalDate.of(2026, 8, 30)),
        )
        assertEquals(setOf("2026-07", "2026-08"), periodosSaldados(listOf(amex), pagos, settings = corte25)[amex.id])
    }

    // ── 3. Un solo pago, sin pago anterior ────────────────────────────────────

    @Test fun `un solo pago en el periodo salda su propio vencimiento`() {
        listOf(calendario, corte25).forEach { settings ->
            assertEquals(
                setOf("2026-09"),
                periodosSaldados(listOf(crediagil), listOf(abonoAlCredito("ev_0905", cinco)), settings = settings)[crediagil.id],
                "$settings",
            )
        }
        // Y el pago solo del 27-sep, sin el del 5: ambiguo como el de AMEX, y se queda en septiembre.
        assertEquals(
            setOf("2026-09"),
            periodosSaldados(listOf(crediagil), listOf(abonoAlCredito("ev_0927", veintisiete)), settings = corte25)[crediagil.id],
        )
    }

    // ── 4. Dos pagos parciales del mismo vencimiento ──────────────────────────

    /**
     * La libranza de `desglosarCuota`: $3.000.000 + $3.040.259 de una cuota de $6.040.259. Las dos
     * partes son el MISMO vencimiento, antes del día, en el vencimiento, en la gracia, o las dos
     * después del corte.
     */
    @Test fun `dos pagos parciales del mismo vencimiento caen en el mismo periodo`() {
        fun periodos(a: LocalDate, b: LocalDate, antes: List<FinancialEvent> = emptyList()) = periodosSaldados(
            listOf(crediagil),
            antes + listOf(abonoAlCredito("ev_a", a, 3_000_000), abonoAlCredito("ev_b", b, 3_040_259)),
            settings = corte25,
        )[crediagil.id]

        assertEquals(setOf("2026-09"), periodos(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 10)))
        assertEquals(setOf("2026-09"), periodos(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15)))
        assertEquals(setOf("2026-09"), periodos(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)), "la segunda, en la gracia")
        // Las dos partes después del corte, con septiembre ya pagado: las dos son octubre.
        assertEquals(
            setOf("2026-09", "2026-10"),
            periodos(veintisiete, LocalDate.of(2026, 9, 29), antes = listOf(abonoAlCredito("ev_0905", cinco))),
        )
        // …y sin septiembre pagado, las dos son septiembre (lado barato, como AMEX): nunca una y una.
        assertEquals(setOf("2026-09"), periodos(veintisiete, LocalDate.of(2026, 9, 29)))
    }

    // ── Lo que el arreglo no puede mover ──────────────────────────────────────

    /**
     * **Con mes de calendario no cambia nada**: el período del pago y su mes de calendario son el
     * mismo, así que el vencimiento «del período» es el del mes. Mismos dos pagos del dueño, con
     * corte 1: los dos saldan septiembre, como siempre.
     */
    @Test fun `con mes de calendario los dos pagos siguen en septiembre`() {
        val pagos = listOf(abonoAlCredito("ev_0905", cinco), abonoAlCredito("ev_0927", veintisiete))
        assertEquals(setOf("2026-09"), periodosSaldados(listOf(crediagil), pagos, settings = calendario)[crediagil.id])
    }

    /**
     * **El segundo abono en la gracia no adelanta el mes siguiente** (el «Lo que queda» del KDoc):
     * corte 25, pago del día 24 hecho el 24 y otro abono el 26. Mientras el 24-sep siga en gracia, el
     * calendario todavía apunta a él: el 26 no es ambiguo y se queda en septiembre.
     */
    @Test fun `el segundo abono dentro de la gracia no adelanta octubre`() {
        val del24 = tarjeta(dia = 24)
        val pagos = listOf(
            pagoDeTarjeta("ev_0924", LocalDate.of(2026, 9, 24)),
            pagoDeTarjeta("ev_0926", LocalDate.of(2026, 9, 26)),
        )
        assertEquals(setOf("2026-09"), periodosSaldados(listOf(del24), pagos, settings = corte25)[del24.id])
    }

    /**
     * **Nu (día 1) pagada el 1 y otro abono el 7**, con corte 25: los dos caen en el mismo período
     * (25-ago a 24-sep), cuyo vencimiento es el 1-sep. El del 7 no adelanta octubre — el aviso del
     * 1 de octubre tiene que salir.
     */
    @Test fun `un segundo abono en el mismo periodo no apaga el aviso del siguiente`() {
        val nu = tarjeta(dia = 1)
        val pagos = listOf(
            pagoDeTarjeta("ev_0901", LocalDate.of(2026, 9, 1), 50_000),
            pagoDeTarjeta("ev_0907", LocalDate.of(2026, 9, 7), 900_000),
        )
        val saldados = periodosSaldados(listOf(nu), pagos, settings = corte25)
        assertEquals(setOf("2026-09"), saldados[nu.id])
        assertEquals(
            listOf(nu.id),
            selectDueForReminder(listOf(nu to null), LocalDate.of(2026, 10, 1), 3, saldados, corte25).map { it.id },
        )
    }

    /**
     * **Quien paga siempre después del corte**: cada pago salda el vencimiento de su propio período,
     * y la respuesta de hoy no depende de hasta dónde hacia atrás se cargaron los pagos. Si la
     * atribución se encadenara pago tras pago, el día que el primero saliera de la franja de
     * `cargarPagosDeDeuda` todos correrían un mes y la cuota de hoy volvería a figurar pendiente.
     */
    @Test fun `quien paga siempre despues del corte salda cada periodo, con cualquier franja`() {
        val julio = abonoAlCredito("ev_0727", LocalDate.of(2026, 7, 27))
        val agosto = abonoAlCredito("ev_0827", LocalDate.of(2026, 8, 27))
        val septiembre = abonoAlCredito("ev_0927", veintisiete)

        val completo = pagosDeDeudaPorPeriodo(listOf(crediagil), listOf(julio, agosto, septiembre), settings = corte25)[crediagil.id]!!
        assertEquals("ev_0927", completo["2026-10"]?.id)
        assertEquals("ev_0827", completo["2026-09"]?.id)

        val sinJulio = pagosDeDeudaPorPeriodo(listOf(crediagil), listOf(agosto, septiembre), settings = corte25)[crediagil.id]!!
        assertEquals("ev_0927", sinJulio["2026-10"]?.id, "Sacar julio de la franja no mueve el pago de hoy")
    }
}
