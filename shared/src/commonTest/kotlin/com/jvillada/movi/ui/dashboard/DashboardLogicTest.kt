package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.assetsDebtsNet
import com.jvillada.movi.ui.credits.totalDebtCop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ola 4 (F9/F40): la lógica del Inicio de alto nivel sin Compose — qué pagos entran en
 * "Próximos pagos", qué alertas existen, qué cifra lleva cada acceso y qué secciones de la
 * definición SDUI se pintan con los datos que hay.
 */
class DashboardLogicTest {

    private fun rule(id: String, name: String, amount: Long, type: TransactionType = TransactionType.EXPENSE) =
        RecurringRule(id = id, name = name, category = "Hogar", amount = amount, dayOfMonth = 10, type = type)

    private fun upcoming(id: String, name: String, amount: Long, daysUntil: Int, type: TransactionType = TransactionType.EXPENSE) =
        UpcomingPayment(rule(id, name, amount, type), dueDate = "2026-08-2${daysUntil.coerceIn(0, 9)}", daysUntil = daysUntil, status = PaymentStatus.UPCOMING)

    private fun event(category: String, amount: Long, type: TransactionType = TransactionType.EXPENSE, cashFlow: Boolean = true, currency: String = "COP") =
        FinancialEvent(id = "e-$category-$amount", accountId = "a", type = type, amount = amount, currency = currency,
            category = category, description = "", timestamp = 0L, countsAsCashFlow = cashFlow)

    // ── Próximos pagos ─────────────────────────────────────────────────────────

    @Test
    fun `proximos pagos toma solo egresos dentro de la ventana, ordenados por urgencia y recortados`() {
        val all = listOf(
            upcoming("r1", "Gimnasio", 90_000, daysUntil = 12),   // fuera de la ventana de 7 días
            upcoming("r2", "Arriendo", 1_500_000, daysUntil = 3),
            upcoming("r3", "Sueldo", 5_000_000, daysUntil = 1, type = TransactionType.INCOME), // un ingreso no es un pago
            upcoming("${CREDIT_RULE_PREFIX}x", "Cuota Carro", 800_000, daysUntil = -1),        // vencido: va primero
            upcoming("r5", "Colegio", 700_000, daysUntil = 0),
            upcoming("r6", "Internet", 80_000, daysUntil = 7),
        )
        val rows = upcomingPaymentsWithin(all, days = 7, max = 3)
        assertEquals(listOf("Cuota Carro", "Colegio", "Arriendo"), rows.map { it.rule.name })
    }

    @Test
    fun `etiqueta de vencimiento en espanol neutro`() {
        assertEquals("Vencido hace 3 días", dueLabel(-3))
        assertEquals("Vencido ayer", dueLabel(-1))
        assertEquals("Vence hoy", dueLabel(0))
        assertEquals("Vence mañana", dueLabel(1))
        assertEquals("Vence en 5 días", dueLabel(5))
    }

    // ── Gasto del mes por categoría ────────────────────────────────────────────

    @Test
    fun `gasto del mes por categoria filtra mes, tipo, flujo de caja y moneda`() {
        val days = listOf(
            EventDay("2026-08-03", 0, listOf(event("Mercado", 100_000), event("Mercado", 50_000), event("Sueldo", 3_000_000, TransactionType.INCOME))),
            EventDay("2026-08-10", 0, listOf(event("Créditos", 900_000, cashFlow = false), event("Viajes", 200, currency = "USD"))),
            EventDay("2026-07-28", 0, listOf(event("Mercado", 999_999))), // mes anterior
        )
        val spent = spentByCategoryForMonth(days, monthPrefix = "2026-08")
        assertEquals(mapOf("Mercado" to 150_000L), spent)
    }

    // ── Alertas ────────────────────────────────────────────────────────────────

    @Test
    fun `sin nada pendiente no hay alertas`() {
        assertTrue(dashboardAlerts(overBudget = emptyList(), cardCandidates = 0, pendingSms = 0).isEmpty())
    }

    @Test
    fun `cada alerta lleva a donde se resuelve y pluraliza bien`() {
        val alerts = dashboardAlerts(overBudget = listOf("Mercado"), cardCandidates = 2, pendingSms = 1)
        assertEquals(
            listOf(
                "Presupuesto de Mercado superado" to Screen.Budgets,
                "2 pagos de tarjeta por confirmar" to Screen.Transactions,
                "1 mensaje del banco por confirmar" to Screen.SMSInbox,
            ),
            alerts.map { it.text to it.target },
        )
        val many = dashboardAlerts(overBudget = listOf("Mercado", "Salidas"), cardCandidates = 1, pendingSms = 0)
        assertEquals(listOf("2 presupuestos superados", "1 pago de tarjeta por confirmar"), many.map { it.text })
    }

    @Test
    fun `presupuesto superado = gastado al menos el limite, y un limite 0 nunca alerta`() {
        val budgets = listOf(Budget("Mercado", 100_000), Budget("Salidas", 200_000), Budget("Raro", 0))
        val spent = mapOf("Mercado" to 100_000L, "Salidas" to 150_000L, "Raro" to 5L)
        assertEquals(listOf("Mercado"), overBudgetCategories(budgets, spent))
    }

    // ── Las dos cifras del Inicio: lo que tienes vs. lo que vales ──────────────

    /**
     * La foto real del dueño el día del reporte, más los cuatro créditos que le faltaba cargar:
     * una cuenta de ahorros con $12.383.363 y cinco créditos por $1.505.093.905. Es el caso que
     * originó la rama — «agregué un crédito y lo que hizo fue descontarme de la cuenta todo el
     * saldo del crédito» — y el que tiene que leerse bien.
     */
    private val cuentasDelDueño = listOf(
        Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 12_383_363),
        Account("l1", "Hipotecario Casa", AccountType.LOAN, 768_000_000),
        Account("l2", "Hipotecario Apto", AccountType.LOAN, 203_000_000),
        Account("l3", "Libranza Uno", AccountType.LOAN, 262_000_000),
        Account("l4", "Libranza Dos", AccountType.LOAN, 231_000_000),
        Account("l5", "Libre inversión", AccountType.LOAN, 41_093_905),
    )

    @Test
    fun `con los cinco creditos del dueño la plata no se toca y el patrimonio queda aparte`() {
        val b = heroBalance(cuentasDelDueño)
        // Lo que el dueño creyó que se le había descontado: no se movió ni un peso.
        assertEquals(12_383_363L, b.tuPlata)
        assertEquals(12_383_363L, b.disponible)
        assertEquals(0L, b.invertido)
        assertEquals(1_505_093_905L, b.deudas)
        assertEquals(12_383_363L - 1_505_093_905L, b.patrimonio)
        assertEquals(-1_492_710_542L, b.patrimonio)
        assertTrue(b.hasDebt, "con deudas el Inicio pinta la línea del patrimonio")
        assertEquals(false, b.hasInvestments)
    }

    @Test
    fun `agregar un credito no cambia lo que tienes, solo el patrimonio`() {
        // El movimiento exacto del reporte: antes del crédito y después, con la MISMA cuenta.
        val antes = listOf(cuentasDelDueño[0])
        val despues = listOf(cuentasDelDueño[0], cuentasDelDueño[5])  // + libre inversión $41M
        assertEquals(heroBalance(antes).tuPlata, heroBalance(despues).tuPlata)
        assertEquals(12_383_363L, heroBalance(antes).patrimonio)      // sin deudas: patrimonio = tu plata
        assertEquals(-28_710_542L, heroBalance(despues).patrimonio)   // la cifra que asustó
    }

    @Test
    fun `sin cuentas todo es cero y no hay nada secundario que pintar`() {
        val b = heroBalance(emptyList())
        assertEquals(0L, b.tuPlata)
        assertEquals(0L, b.deudas)
        assertEquals(0L, b.patrimonio)
        assertEquals(false, b.hasDebt, "sin deudas el patrimonio repetiría el número grande")
        assertEquals(false, b.hasInvestments)
    }

    @Test
    fun `solo deudas deja tu plata en cero y el patrimonio en negativo`() {
        val b = heroBalance(listOf(Account("l1", "Hipotecario", AccountType.LOAN, 768_000_000)))
        assertEquals(0L, b.tuPlata)
        assertEquals(768_000_000L, b.deudas)
        assertEquals(-768_000_000L, b.patrimonio)
        assertTrue(b.hasDebt)
    }

    @Test
    fun `solo efectivo no muestra patrimonio porque seria el mismo numero`() {
        val b = heroBalance(listOf(Account("a1", "Efectivo", AccountType.CASH, 350_000)))
        assertEquals(350_000L, b.tuPlata)
        assertEquals(0L, b.deudas)
        assertEquals(b.tuPlata, b.patrimonio)
        assertEquals(false, b.hasDebt)
    }

    @Test
    fun `las inversiones entran en tu plata y se desglosan aparte`() {
        val b = heroBalance(
            listOf(
                Account("a1", "Ahorros", AccountType.SAVINGS, 12_383_363),
                Account("i1", "CDT Bancolombia", AccountType.INVESTMENT, 3_000_000),
                Account("i2", "Fondo Nu", AccountType.INVESTMENT, 700_000),
                Account("l1", "Libre inversión", AccountType.LOAN, 41_093_905),
            ),
        )
        assertEquals(16_083_363L, b.tuPlata, "Dinero + Inversión: lo invertido sigue siendo plata suya")
        assertEquals(12_383_363L, b.disponible)
        assertEquals(3_700_000L, b.invertido)
        assertTrue(b.hasInvestments, "con algo invertido el hero muestra el desglose")
        assertEquals(16_083_363L - 41_093_905L, b.patrimonio)
    }

    @Test
    fun `una tarjeta en otra moneda entra por su estimado en COP, igual que en Cuentas`() {
        val b = heroBalance(
            listOf(
                Account("a1", "Ahorros", AccountType.SAVINGS, 2_000_000),
                Account("c1", "Mastercard USD", AccountType.CREDIT_CARD, 0, currency = "USD", estimatedTotalCop = 4_000_000),
            ),
        )
        assertEquals(4_000_000L, b.deudas)
        assertEquals(-2_000_000L, b.patrimonio)
    }

    @Test
    fun `las dos cifras del hero no pueden desalinearse de la fila Cuentas ni de Cuentas`() {
        // El hero, el acceso «Cuentas» del Inicio y el «Patrimonio neto» de la pantalla de
        // Cuentas salen todos de assetsDebtsNet — el desacuerdo entre pantallas que la Ola 4
        // tuvo que arreglar entre Créditos y el Inicio no puede repetirse acá.
        val (activos, deudas, neto) = assetsDebtsNet(cuentasDelDueño)
        val b = heroBalance(cuentasDelDueño)
        assertEquals(activos, b.tuPlata)
        assertEquals(deudas, b.deudas)
        assertEquals(neto, b.patrimonio)
        assertEquals(b.tuPlata, b.disponible + b.invertido)
        assertEquals(b.patrimonio, b.tuPlata - b.deudas)
        // Y lo mismo que muestra la fila «Cuentas» de EXPLORA.
        assertEquals("$12.383.363", quickLinkFigure("accounts", DashboardData(accounts = cuentasDelDueño)).value)
    }

    // ── Accesos con cifra ──────────────────────────────────────────────────────

    private val data = DashboardData(
        accounts = listOf(
            Account("a1", "Ahorros", AccountType.SAVINGS, 2_000_000),
            Account("a2", "Visa", AccountType.CREDIT_CARD, 500_000),
        ),
        credits = listOf(CreditSummary(Account("l1", "Carro", AccountType.LOAN, 10_000_000), terms = null, paidPct = null)),
        budgets = listOf(Budget("Mercado", 400_000)),
        spentByCategory = mapOf("Mercado" to 250_000L),
        goals = listOf(Goal(id = "g1", name = "Viaje", target = 5_000_000, accountId = "a1", targetDate = "2027-01-01", saved = 1_200_000)),
    )

    @Test
    fun `cada acceso muestra la cifra de su destino`() {
        assertEquals("$2.000.000", quickLinkFigure("accounts", data).value)   // activos, sin la tarjeta
        assertEquals("2 cuentas", quickLinkFigure("accounts", data).sub)
        assertEquals("$10.000.000", quickLinkFigure("credits", data).value)
        assertEquals("1 crédito", quickLinkFigure("credits", data).sub)
        val b = quickLinkFigure("budgets", data)
        assertEquals("$250.000", b.value)
        assertEquals("de $400.000 este mes", b.sub)
        assertEquals(false, b.isAlert)
        assertEquals("$1.200.000", quickLinkFigure("goals", data).value)
        assertEquals("1 meta", quickLinkFigure("goals", data).sub)
    }

    @Test
    fun `credits suma prestamos y tarjetas con la misma funcion que la pantalla de Creditos`() {
        // F20 (hallazgo Ola 4): el acceso del Inicio y la «Deuda total» de la pantalla tienen
        // que dar el mismo número — acá se verifica que ambos salen de totalDebtCop.
        val withCards = data.copy(
            cards = listOf(
                CardSummary(Account("c1", "Visa", AccountType.CREDIT_CARD, 500_000), terms = null),
                // Tarjeta USD: entra por su estimado en COP, no por su componente COP (que es $0).
                CardSummary(
                    Account("c2", "Mastercard USD", AccountType.CREDIT_CARD, 0, currency = "USD", estimatedTotalCop = 4_000_000),
                    terms = null,
                ),
            ),
        )
        val fig = quickLinkFigure("credits", withCards)
        assertEquals("$14.500.000", fig.value)  // 10M préstamo + 500K Visa + 4M estimado USD
        assertEquals("3 créditos", fig.sub)
        assertEquals(14_500_000L, totalDebtCop(withCards.credits, withCards.cards))
    }

    @Test
    fun `hasCredit se tilda tambien con una tarjeta sola`() {
        val onlyCard = DashboardData(
            cards = listOf(CardSummary(Account("c1", "Visa", AccountType.CREDIT_CARD, 500_000), terms = null)),
        )
        assertTrue(onlyCard.hasCredit)
    }

    @Test
    fun `hasRecurringRule ignora las reglas sinteticas de credito y de tarjeta`() {
        val synthetic = DashboardData(
            upcoming = listOf(
                upcoming("${CREDIT_RULE_PREFIX}acc-1", "Cuota Carro", 1_000_000, daysUntil = 3),
                upcoming("${CARD_RULE_PREFIX}acc-2", "Pago tarjeta Visa", 500_000, daysUntil = 5),
            ),
        )
        assertEquals(false, synthetic.hasRecurringRule)
        val real = synthetic.copy(upcoming = synthetic.upcoming.orEmpty() + upcoming("rr_1", "Arriendo", 2_000_000, daysUntil = 8))
        assertTrue(real.hasRecurringRule)
    }

    @Test
    fun `investments toma cuentas tipo INVESTMENT, no el modelo de posiciones retirado`() {
        // F50: Inversiones (y su acceso con cifra en el Inicio) dejaron de leer holdings —
        // ese modelo se retiró — y pasaron a leer cuentas de tipo INVESTMENT, igual que
        // Cuentas. Una cuenta de Dinero (SAVINGS) no debe sumar acá.
        val withInvestments = data.copy(
            accounts = data.accounts + listOf(
                Account("i1", "CDT Bancolombia", AccountType.INVESTMENT, 3_000_000),
                Account("i2", "Fondo Nu", AccountType.INVESTMENT, 700_000),
            ),
        )
        val figure = quickLinkFigure("investments", withInvestments)
        assertEquals("$3.700.000", figure.value)
        assertEquals("2 cuentas", figure.sub)
    }

    @Test
    fun `sin datos el acceso no inventa una cifra`() {
        val empty = DashboardData()
        assertNull(quickLinkFigure("accounts", empty).value)
        assertEquals("Sin cuentas aún", quickLinkFigure("accounts", empty).sub)
        assertNull(quickLinkFigure("investments", empty).value)
        assertNull(quickLinkFigure("subscriptions", empty).value)
        assertNull(quickLinkFigure("recurrentes", empty).value)
        assertNull(quickLinkFigure("aichat", empty).value)   // destino sin cifra
        assertNull(quickLinkFigure("aichat", empty).sub)
    }

    // ── El acceso «Recurrentes» (Ola 8) ────────────────────────────────────────

    @Test
    fun `el acceso Recurrentes muestra el mismo flujo libre que la pantalla`() {
        val d = DashboardData(
            upcoming = listOf(
                upcoming("rr_1", "Sueldo", 5_000_000, daysUntil = 3, type = TransactionType.INCOME),
                upcoming("rr_2", "Arriendo", 2_000_000, daysUntil = 5),
            ),
            subscriptions = SubscriptionsResult(
                subscriptions = listOf(
                    Subscription(
                        id = "s1", merchantKey = "netflix", displayName = "Netflix", amount = 44_900,
                        currency = "COP", dayOfMonth = 5, status = SubStatus.CONFIRMED,
                        confidence = SubConfidence.HIGH, firstSeen = 0, lastSeen = 0, occurrences = 3,
                    ),
                ),
                monthlyTotalCop = 44_900,
            ),
        )
        val f = quickLinkFigure("recurrentes", d)
        assertEquals("$2.955.100", f.value)
        assertEquals("libre al mes · 3 recurrentes", f.sub)
        assertEquals(false, f.isAlert)
    }

    /**
     * A1 — la regresión que motivó este test. La cifra COMPONE dos fuentes; con una sola sale
     * plausible y equivocada. Si se cae `/api/payments/upcoming` y responde `/api/subscriptions`,
     * el sueldo desaparece y «libre al mes» quedaría en −$44.900, en rojo, contra los $2.955.100
     * que muestra la pantalla de destino. Sin las dos fuentes no se pinta cifra.
     */
    @Test
    fun `con una sola de sus dos fuentes el acceso Recurrentes no inventa nada`() {
        val subs = SubscriptionsResult(
            subscriptions = listOf(
                Subscription(
                    id = "s1", merchantKey = "netflix", displayName = "Netflix", amount = 44_900,
                    currency = "COP", dayOfMonth = 5, status = SubStatus.CONFIRMED,
                    confidence = SubConfidence.HIGH, firstSeen = 0, lastSeen = 0, occurrences = 3,
                ),
            ),
            monthlyTotalCop = 44_900,
        )
        // Solo suscripciones: se cayeron las reglas.
        val soloSubs = DashboardData(subscriptions = subs)
        assertNull(quickLinkFigure("recurrentes", soloSubs).value)
        assertNull(quickLinkFigure("recurrentes", soloSubs).sub)

        // Solo reglas: se cayeron las suscripciones.
        val soloReglas = DashboardData(upcoming = listOf(upcoming("rr_1", "Arriendo", 2_000_000, daysUntil = 5)))
        assertNull(quickLinkFigure("recurrentes", soloReglas).value)

        // Las dos llegaron y de verdad no hay nada: ahí sí se puede afirmar el vacío.
        val vacioReal = DashboardData(upcoming = emptyList(), subscriptions = SubscriptionsResult(emptyList(), 0))
        assertNull(quickLinkFigure("recurrentes", vacioReal).value)
        assertEquals("Sin recurrentes", quickLinkFigure("recurrentes", vacioReal).sub)
    }

    @Test
    fun `el acceso Recurrentes ignora las cuotas sinteticas de creditos y tarjetas`() {
        val d = DashboardData(
            upcoming = listOf(
                upcoming("${CREDIT_RULE_PREFIX}l1", "Cuota del carro", 900_000, daysUntil = 4),
                upcoming("${CARD_RULE_PREFIX}c1", "Pago tarjeta", 500_000, daysUntil = 6),
                upcoming("rr_1", "Arriendo", 2_000_000, daysUntil = 5),
            ),
            subscriptions = SubscriptionsResult(emptyList(), 0),
        )
        val f = quickLinkFigure("recurrentes", d)
        assertEquals("−$2.000.000", f.value)
        assertEquals("libre al mes · 1 recurrente", f.sub)
        assertEquals(true, f.isAlert, "un flujo libre negativo se marca")
    }

    @Test
    fun `presupuesto superado se marca como alerta en el acceso`() {
        val over = data.copy(spentByCategory = mapOf("Mercado" to 400_000L))
        assertEquals(true, quickLinkFigure("budgets", over).isAlert)
    }

    // ── Secciones visibles ─────────────────────────────────────────────────────

    @Test
    fun `con la base vacia solo se pintan balance, accesos y Movi AI`() {
        val visible = visibleSections(defaultDashboardDefinition(), DashboardData())
        assertEquals(listOf("HERO_BALANCE", "QUICK_LINKS_WITH_TOTALS", "BANNER"), visible.map { it.type })
    }

    @Test
    fun `proximos pagos y alertas aparecen solo cuando hay algo`() {
        val withStuff = DashboardData(
            upcoming = listOf(upcoming("r1", "Arriendo", 1_000, daysUntil = 2)),
            cardCandidates = 1,
        )
        val visible = visibleSections(defaultDashboardDefinition(), withStuff)
        assertEquals(listOf("HERO_BALANCE", "UPCOMING_PAYMENTS", "ALERTS", "QUICK_LINKS_WITH_TOTALS", "BANNER"), visible.map { it.type })
    }

    @Test
    fun `un acceso sin tarjetas no se pinta`() {
        val def = defaultDashboardDefinition().copy(sections = listOf(ScreenSection(type = "QUICK_LINKS_WITH_TOTALS", title = "Vacío")))
        assertTrue(visibleSections(def, DashboardData()).isEmpty())
    }

    // ── Guía de primeros pasos ─────────────────────────────────────────────────

    @Test
    fun `la guia distingue reglas recurrentes reales de las cuotas sinteticas de creditos`() {
        val onlyCredit = DashboardData(upcoming = listOf(upcoming("${CREDIT_RULE_PREFIX}l1", "Cuota", 1, 3)))
        assertEquals(false, onlyCredit.hasRecurringRule)
        val real = DashboardData(upcoming = listOf(upcoming("rr_1", "Colegio", 1, 3)))
        assertEquals(true, real.hasRecurringRule)
    }

    // ── F5: panel de notificaciones ─────────────────────────────────────────────

    @Test
    fun `sin nada pendiente el panel de notificaciones queda vacio`() {
        assertTrue(notificationRows(DashboardData()).isEmpty())
    }

    @Test
    fun `el panel combina pagos proximos y alertas, cada uno a su destino`() {
        val d = DashboardData(
            upcoming = listOf(
                upcoming("rr_1", "Colegio", 700_000, daysUntil = 2),
                upcoming("${CREDIT_RULE_PREFIX}l1", "Cuota Carro", 800_000, daysUntil = -1),
                upcoming("${CARD_RULE_PREFIX}c1", "Pago Visa", 500_000, daysUntil = 0),
                upcoming("r-lejos", "Gimnasio", 90_000, daysUntil = 20), // fuera de la ventana de 7 días
            ),
            budgets = listOf(Budget("Mercado", 100_000)),
            spentByCategory = mapOf("Mercado" to 150_000L),
            cardCandidates = 1,
            pendingSms = 2,
        )
        val rows = notificationRows(d)
        assertEquals(
            listOf(
                "Cuota Carro · Vencido ayer" to Screen.Credits,       // synthetic de crédito -> Créditos
                "Pago Visa · Vence hoy" to Screen.Credits,            // synthetic de tarjeta -> Créditos
                "Colegio · Vence en 2 días" to Screen.Recurrentes,    // regla real -> Recurrentes
                "Presupuesto de Mercado superado" to Screen.Budgets,
                "1 pago de tarjeta por confirmar" to Screen.Transactions,
                "2 mensajes del banco por confirmar" to Screen.SMSInbox,
            ),
            rows.map { it.text to it.target },
        )
    }
}
