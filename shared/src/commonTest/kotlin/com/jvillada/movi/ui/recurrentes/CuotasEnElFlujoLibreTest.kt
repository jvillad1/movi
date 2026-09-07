package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Las cuotas de los créditos entran al «Flujo libre» — y solo las que de verdad salen todos los
 * meses del bolsillo del dueño.**
 *
 * El pedido: *«en recurrentes no estoy viendo los pagos de cuota realizados para mis créditos,
 * considero que esto es importante verlo porque me permite entender mi flujo de caja mensual»*.
 * El PR anterior las hizo visibles en la lista; este las mete al total.
 *
 * La parte cara no es sumarlas, es **cuáles**. Con los ocho créditos reales del dueño hay tres
 * respuestas posibles y dos son falsas:
 *
 * - **$25.470.538** — sumar todas las cuotas de sus ocho créditos.
 * - **$15.470.538** — sumar todo menos el pago único (o todo menos alguna otra).
 * - **$5.445.772** — la verdad: Vehículo + Libre inversión + Crediágil.
 *
 * Los datos de este archivo son los suyos, con los montos reales, justamente para que una
 * regresión se lea como una cifra que él reconoce y no como un número de laboratorio.
 */
class CuotasEnElFlujoLibreTest {

    // ── Los créditos del dueño, tal como llegan al cliente ────────────────────

    /**
     * Una cuota de crédito **como la fabrica el server** (`virtualRuleFor`): id con prefijo,
     * categoría «Créditos», gasto.
     *
     * Las de libranza y las que paga un tercero **no están en esta lista a propósito**: nunca
     * llegan al cliente, porque `loadCreditRulePairs` las filtra con `entraAlBarridoDeAvisos`
     * (su salario ya viene neto de la libranza, así que contarlas restaría dos veces). Que este
     * archivo no las tenga es la forma de fijar de qué lado vive esa decisión — si algún día
     * empezaran a viajar, `cuentaComoCompromisoMensual` tendría que ampliarse y este test se
     * quedaría corto en vez de tapar el cambio.
     */
    private fun cuota(cuenta: String, monto: Long, dia: Int = 1, pagoUnico: Boolean = false) =
        RecurringRule(
            id = "$CREDIT_RULE_PREFIX$cuenta",
            name = "Cuota $cuenta",
            category = "Créditos",
            amount = monto,
            dayOfMonth = dia,
            type = TransactionType.EXPENSE,
            esPagoUnico = pagoUnico,
        )

    /** La regla sintética de una tarjeta: su monto es el SALDO. Ver `RecurringRule.montoEsSaldo`. */
    private fun tarjeta(cuenta: String, saldo: Long) = RecurringRule(
        id = "$CARD_RULE_PREFIX$cuenta",
        name = "Pago tarjeta $cuenta",
        category = "Créditos",
        amount = saldo,
        dayOfMonth = 15,
        type = TransactionType.EXPENSE,
        montoEsSaldo = true,
    )

    private fun regla(nombre: String, monto: Long, tipo: TransactionType = TransactionType.EXPENSE) =
        RecurringRule(
            id = "rr_$nombre", name = nombre, category = "Otros", amount = monto,
            dayOfMonth = 5, type = tipo,
        )

    private fun sub(nombre: String, monto: Long) = Subscription(
        id = "s_$nombre", merchantKey = nombre.lowercase(), displayName = nombre, amount = monto,
        currency = "COP", dayOfMonth = 12, status = SubStatus.CONFIRMED,
        confidence = SubConfidence.HIGH, firstSeen = 0, lastSeen = 0, occurrences = 3,
    )

    private val vehiculo = cuota("Vehículo 4083", 4_215_223L)
    private val libreInversion = cuota("Libre inversión 9695", 1_204_064L)
    private val crediagil = cuota("Crediágil 3090", 26_485L)

    /** Plazo de 1 mes: $10.000.000 que vencen UNA vez, no todos los meses. */
    private val techoGardenera = cuota("Techo Gardenera", 10_000_000L, pagoUnico = true)

    private val amex = tarjeta("AMEX 9208", 27_501_150L)

    /** Lo que él tiene de su lado: las tres cuotas que paga él, el pago único y una tarjeta. */
    private val cuotasQueLeLlegan =
        listOf(vehiculo, libreInversion, crediagil, techoGardenera, amex)

    /** Vehículo + Libre inversión + Crediágil. */
    private val CUOTAS_REALES = 5_445_772L

    private val sinSuscripciones = SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)

    // ── La cifra ─────────────────────────────────────────────────────────────

    @Test
    fun `las cuotas de sus creditos entran al total, y son 5445772 al mes`() {
        val r = resumenRecurrentes(cuotasQueLeLlegan, sinSuscripciones)

        assertEquals(CUOTAS_REALES, r.gastos)
        assertEquals(CUOTAS_REALES, r.cuotasDeCredito)
        assertEquals(-CUOTAS_REALES, r.flujoLibre)
        // Y no las dos cifras equivocadas que están a un `filter` de distancia.
        assertFalse(r.gastos == 25_470_538L, "sumó también el saldo de la tarjeta")
        assertFalse(r.gastos == 15_445_772L, "sumó el crédito de pago único")
    }

    @Test
    fun `una cuota normal cuenta`() {
        assertTrue(cuentaComoCompromisoMensual(vehiculo))
        val r = resumenRecurrentes(listOf(vehiculo), sinSuscripciones)
        assertEquals(4_215_223L, r.gastos)
        assertEquals(4_215_223L, r.cuotasDeCredito)
    }

    /**
     * **El saldo de una tarjeta no es un pago.** Movi ya anunció una vez $27.501.150 como el
     * próximo pago de esa tarjeta, cuando su mínimo ronda el 5 %; meterlo a un total mensual
     * sería el mismo error, esta vez escondido adentro de una suma.
     */
    @Test
    fun `la regla de una tarjeta no entra al total ni al inventario`() {
        assertFalse(cuentaComoCompromisoMensual(amex))

        val r = resumenRecurrentes(listOf(amex), sinSuscripciones)
        assertEquals(0L, r.gastos)
        assertEquals(0L, r.cuotasDeCredito)
        // Tampoco cuenta como «recurrente» en el rótulo del Inicio: el conteo y la cifra que van
        // uno al lado del otro tienen que hablar del mismo conjunto.
        assertTrue(r.items.isEmpty())
    }

    /**
     * Y si algún día un server mandara la regla de una tarjeta **sin** la marca, el prefijo del
     * id la ataja igual: un total sobre la plata del dueño no se sostiene en un `Boolean` con
     * default `false` que tiene que acordarse de viajar.
     */
    @Test
    fun `una regla de tarjeta sin la marca tampoco entra`() {
        val sinMarca = amex.copy(montoEsSaldo = false)

        assertFalse(cuentaComoCompromisoMensual(sinMarca))
        assertEquals(0L, resumenRecurrentes(listOf(sinMarca), sinSuscripciones).gastos)
    }

    /**
     * **Un crédito a un mes no es un compromiso mensual.** Contarlo le diría que tiene
     * $10.000.000 menos todos los meses, para siempre, cuando vence una sola vez.
     */
    @Test
    fun `un credito de pago unico no entra al total, y se dice`() {
        assertFalse(cuentaComoCompromisoMensual(techoGardenera))

        val r = resumenRecurrentes(listOf(vehiculo, techoGardenera), sinSuscripciones)
        assertEquals(4_215_223L, r.gastos)
        assertEquals(1, r.pagosUnicosFuera)
        assertEquals(1, r.items.size)
    }

    /** Sin pagos únicos no hay nada que explicar: el card no dice nada. */
    @Test
    fun `sin creditos de pago unico no se cuenta ninguno`() {
        val r = resumenRecurrentes(listOf(vehiculo, libreInversion), sinSuscripciones)
        assertEquals(0, r.pagosUnicosFuera)
    }

    /** Sin créditos, `cuotasDeCredito` es 0 y la línea del card tampoco aparece. */
    @Test
    fun `sin creditos el total no menciona cuotas`() {
        val r = resumenRecurrentes(listOf(regla("Arriendo", 1_800_000L)), sinSuscripciones)
        assertEquals(0L, r.cuotasDeCredito)
        assertEquals(1_800_000L, r.gastos)
    }

    // ── Que lo de antes siga funcionando igual ───────────────────────────────

    /**
     * El mes del dueño completo: su sueldo, su arriendo, un cobro que se auto-descubrió, y las
     * cuotas. Las reglas y las suscripciones se comportan exactamente como antes — lo único que
     * cambió es que las cuotas se sumaron.
     */
    @Test
    fun `reglas y suscripciones se comportan igual que antes, con cuotas adentro`() {
        val reglas = listOf(
            regla("Salario", 12_000_000L, TransactionType.INCOME),
            regla("Arriendo", 1_800_000L),
        ) + cuotasQueLeLlegan
        val subs = SubscriptionsResult(listOf(sub("Netflix", 44_900L)), monthlyTotalCop = 44_900L)

        val r = resumenRecurrentes(reglas, subs)

        assertEquals(12_000_000L, r.ingresos)
        assertEquals(1_800_000L + 44_900L + CUOTAS_REALES, r.gastos)
        assertEquals(12_000_000L - 1_800_000L - 44_900L - CUOTAS_REALES, r.flujoLibre)
        // 2 reglas + 3 cuotas + 1 suscripción. La tarjeta y el pago único no están.
        assertEquals(6, r.items.size)
    }

    /**
     * El reparto uno-a-uno contra las suscripciones sigue intacto, y **se hace contra las reglas
     * que cuentan**: una regla excluida no puede tapar una suscripción que sí suma, porque eso
     * borraría un gasto real del total en vez de evitar un duplicado.
     */
    @Test
    fun `una regla que no cuenta no tapa una suscripcion con el mismo nombre`() {
        val cobroDeTarjeta = tarjeta("Netflix", 500_000L) // mismo nombre normalizado que la sub
        val subs = SubscriptionsResult(listOf(sub("Pago tarjeta Netflix", 44_900L)), 44_900L)

        val r = resumenRecurrentes(listOf(cobroDeTarjeta), subs)

        assertEquals(44_900L, r.gastos)
        assertEquals(0, r.items.filterIsInstance<Recurrente.Suscripcion>().count { it.yaEsRegla })
    }

    // ── De dónde salen las cuotas: `reglasSinteticas` ────────────────────────

    /**
     * Las reglas de créditos y tarjetas solo llegan por `/api/payments/upcoming`, mezcladas con
     * las que el dueño escribió. Esta función es la que las separa por el prefijo del id, que es
     * lo único que las distingue.
     */
    @Test
    fun `reglasSinteticas separa lo que fabrica el server de lo que escribio el dueno`() {
        val arriendo = regla("Arriendo", 1_800_000L)
        val upcoming = listOf(arriendo, vehiculo, amex).map {
            UpcomingPayment(rule = it, dueDate = "2026-09-01", daysUntil = 3, status = PaymentStatus.DUE_SOON)
        }

        assertEquals(listOf(vehiculo.id, amex.id), reglasSinteticas(upcoming).map { it.id })
    }

    /**
     * Y el camino entero, tal como lo arma Movimientos: reglas del dueño + sintéticas de
     * «Próximos». La del Inicio manda `upcoming` completo y tiene que dar lo mismo — que las dos
     * pantallas no puedan discrepar es el motivo por el que el filtro vive adentro de
     * [resumenRecurrentes] y no en cada llamado.
     */
    @Test
    fun `Movimientos e Inicio calculan el mismo flujo libre`() {
        val arriendo = regla("Arriendo", 1_800_000L)
        val upcoming = (listOf(arriendo) + cuotasQueLeLlegan).map {
            UpcomingPayment(rule = it, dueDate = "2026-09-01", daysUntil = 3, status = PaymentStatus.DUE_SOON)
        }

        val movimientos = resumenRecurrentes(
            listOf(arriendo) + reglasSinteticas(upcoming),
            sinSuscripciones,
        )
        val inicio = resumenRecurrentes(upcoming.map { it.rule }, sinSuscripciones)

        assertEquals(inicio.flujoLibre, movimientos.flujoLibre)
        assertEquals(inicio.items.size, movimientos.items.size)
        assertEquals(-(1_800_000L + CUOTAS_REALES), inicio.flujoLibre)
    }
}
