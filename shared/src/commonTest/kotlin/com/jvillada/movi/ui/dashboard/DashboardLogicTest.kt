package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.group
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
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.assetsDebtsNet
import com.jvillada.movi.ui.credits.totalDebtCop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `la alerta del Inicio usa la MISMA regla que Presupuestos`() {
        // Este test fijaba la regla vieja —«superado = gastado AL MENOS el límite»— y por eso se
        // puso rojo cuando Presupuestos dejó de contar el empate como exceso. Esa divergencia es
        // justo lo que el dueño vio: «Presupuesto de Mercado superado» en el Inicio y «Sin margen ·
        // gastaste justo el límite» al entrar.
        //
        // Ahora las dos llaman a `estadoDePresupuesto` en `:core`. Este test comprueba que el
        // Inicio no vuelva a tener criterio propio.
        val budgets = listOf(
            Budget("Justo", 100_000),      // exactamente el límite: NO es superado
            Budget("Pasado", 100_000),     // un peso más: sí
            Budget("Cerca", 200_000),      // 75 %: no
            Budget("Raro", 0),             // sin configurar: nunca alerta
        )
        val spent = mapOf(
            "Justo" to 100_000L,
            "Pasado" to 100_001L,
            "Cerca" to 150_000L,
            "Raro" to 5L,
        )

        assertEquals(listOf("Pasado"), overBudgetCategories(budgets, spent))
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

    /**
     * El rótulo del hero NO sale de la definición SDUI. Si volviera a `section.title ?: …`, un
     * deploy que cambiara la fila titularía «Tu plata» el patrimonio en cualquier APK ya
     * instalado — la lectura exacta que esta rama vino a evitar, afirmada por el rótulo.
     */
    @Test
    fun `el rotulo del hero vive en el binario y no en la definicion guardada`() {
        assertEquals("Tu plata", HERO_BALANCE_TITLE)
        assertEquals("Tu plata", heroBalanceTitle(ScreenSection(type = "HERO_BALANCE", title = "Balance neto")))
        assertEquals("Tu plata", heroBalanceTitle(ScreenSection(type = "HERO_BALANCE", title = null)))
        assertEquals("Tu plata", heroBalanceTitle(ScreenSection(type = "HERO_BALANCE", title = "Lo que sea")))
    }

    @Test
    fun `la explicacion del patrimonio escribe la resta del dueño`() {
        assertEquals(
            "Tu plata menos $1.505,1M en deudas",
            patrimonioExplicacion(heroBalance(cuentasDelDueño)),
        )
    }

    @Test
    fun `una tarjeta sobrepagada no esconde el patrimonio ni le pone un menos delante`() {
        // Deuda NEGATIVA: pagaste de más y la tarjeta te debe a vos. El patrimonio queda por
        // ENCIMA de «tu plata», así que la línea tiene algo que decir (con `deudas > 0` se
        // escondía justo cuando dejaba de ser redundante) y la redacción cambia de signo —
        // «menos −$500.000 en deudas» sería una resta escrita al revés.
        val b = heroBalance(
            listOf(
                Account("a1", "Ahorros", AccountType.SAVINGS, 2_000_000),
                Account("c1", "Visa", AccountType.CREDIT_CARD, -500_000),
            ),
        )
        assertEquals(-500_000L, b.deudas)
        assertEquals(2_500_000L, b.patrimonio)
        assertTrue(b.hasDebt, "el patrimonio ya no es el mismo número que «tu plata»: hay que mostrarlo")
        assertEquals("Tu plata más $500.000 a favor en créditos", patrimonioExplicacion(b))
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
        // Explícito desde que `cards` distingue «no contestó» de «no tiene»: este dueño de
        // prueba NO tiene tarjetas, que es distinto de que la lectura no haya vuelto.
        cards = emptyList(),
        budgets = listOf(Budget("Mercado", 400_000)),
        spentByCategory = mapOf("Mercado" to 250_000L),
        goals = listOf(Goal(id = "g1", name = "Viaje", target = 5_000_000, accountId = "a1", targetDate = "2027-01-01", saved = 1_200_000)),
    )

    /**
     * El conteo de la fila «Cuentas» tiene que hablar del MISMO conjunto que su cifra —los
     * activos, o sea toda cuenta que no sea deuda— y que el de la pantalla a la que lleva, que
     * desde F61 solo lista los grupos Dinero e Inversión. Contar `accounts.size` decía
     * «6 cuentas» al lado de la cifra de una sola, y al tocar aparecía «DINERO · 1».
     */
    @Test
    fun `el conteo de Cuentas nombra el mismo conjunto que su cifra y que la pantalla de destino`() {
        val fig = quickLinkFigure("accounts", DashboardData(accounts = cuentasDelDueño))
        assertEquals("$12.383.363", fig.value)
        assertEquals("1 cuenta", fig.sub, "cinco de las seis son deuda y no entran en la cifra")
        // Lo que el dueño ve al llegar: la suma de los dos grupos que lista la pantalla.
        val enPantalla = cuentasDelDueño.count {
            it.type.group == AccountGroup.DINERO || it.type.group == AccountGroup.INVERSION
        }
        assertEquals("$enPantalla cuenta", fig.sub)
    }

    @Test
    fun `las cuentas de inversion cuentan y suman, las de deuda ni una cosa ni la otra`() {
        val d = DashboardData(
            accounts = listOf(
                Account("a1", "Ahorros", AccountType.SAVINGS, 12_383_363),
                Account("i1", "CDT Bancolombia", AccountType.INVESTMENT, 3_000_000),
                Account("i2", "Fondo Nu", AccountType.INVESTMENT, 700_000),
                Account("l1", "Libre inversión", AccountType.LOAN, 41_093_905),
            ),
        )
        val fig = quickLinkFigure("accounts", d)
        assertEquals("$16.083.363", fig.value)
        assertEquals("3 cuentas", fig.sub)
    }

    /**
     * El estado de quien arranca cargando sus deudas: la cifra es $0 porque no hay activos, y
     * «0 cuentas» al lado de un cero se lee como que algo se perdió. La fila dice lo mismo que
     * los dos grupos vacíos de la pantalla de destino, y las deudas siguen contadas y sumadas
     * en la fila «Créditos», justo debajo.
     */
    @Test
    fun `con solo creditos la fila Cuentas no dice 0 cuentas`() {
        val soloDeudas = DashboardData(
            accounts = cuentasDelDueño.filter { it.type == AccountType.LOAN },
            credits = cuentasDelDueño.filter { it.type == AccountType.LOAN }
                .map { CreditSummary(it, terms = null, paidPct = null) },
            cards = emptyList(),
        )
        val fig = quickLinkFigure("accounts", soloDeudas)
        assertNull(fig.value, "sin activos no hay cifra que mostrar — nunca un $0 grande")
        assertEquals("Sin cuentas aún", fig.sub)
        // Y las deudas no desaparecen del Inicio: la fila de abajo las cuenta y las suma.
        assertEquals("5 créditos", quickLinkFigure("credits", soloDeudas).sub)
        assertEquals("$1.505.093.905", quickLinkFigure("credits", soloDeudas).value)
    }

    @Test
    fun `una tarjeta sola tampoco cuenta como cuenta`() {
        val soloTarjeta = DashboardData(
            accounts = listOf(Account("c1", "Visa", AccountType.CREDIT_CARD, 500_000)),
        )
        assertNull(quickLinkFigure("accounts", soloTarjeta).value)
        assertEquals("Sin cuentas aún", quickLinkFigure("accounts", soloTarjeta).sub)
    }

    @Test
    fun `cada acceso muestra la cifra de su destino`() {
        assertEquals("$2.000.000", quickLinkFigure("accounts", data).value)   // activos, sin la tarjeta
        // La tarjeta tampoco cuenta: la cifra no la suma y la pantalla de Cuentas no la lista.
        assertEquals("1 cuenta", quickLinkFigure("accounts", data).sub)
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
        assertEquals(14_500_000L, totalDebtCop(withCards.credits.orEmpty(), withCards.cards.orEmpty()))
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
            accounts = data.accounts.orEmpty() + listOf(
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
        // `DashboardData()` dejó de significar «este usuario no tiene nada» y pasó a significar
        // «todavía no contestaron»: por eso el «Sin cuentas aún» ya no sale acá y se comprueba
        // más abajo con `accounts = emptyList()`, que es la respuesta vacía de verdad. El resto
        // de los accesos no cambia — nunca afirmaron nada, solo se quedaban sin cifra.
        val empty = DashboardData()
        assertNull(quickLinkFigure("accounts", empty).value)
        assertNull(quickLinkFigure("accounts", empty).sub)
        assertEquals(
            "Sin cuentas aún",
            quickLinkFigure("accounts", DashboardData(accounts = emptyList())).sub,
        )
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
    fun `el acceso a Presupuestos usa la misma regla que el resto de la app`() {
        // Este test fijaba la regla vieja —«400.000 de 400.000 es alerta»— y por eso se puso rojo.
        // Era la TERCERA copia de esa comparación, a 70 líneas de la segunda, y producía la misma
        // contradicción que el dueño reportó: el acceso en alerta, y adentro todo verde.
        val justo = data.copy(spentByCategory = mapOf("Mercado" to 400_000L))
        assertEquals(false, quickLinkFigure("budgets", justo).isAlert, "gastar el límite exacto no es superarlo")

        val pasado = data.copy(spentByCategory = mapOf("Mercado" to 400_001L))
        assertEquals(true, quickLinkFigure("budgets", pasado).isAlert)
    }

    @Test
    fun `un presupuesto excedido no se compensa con otro que sobro`() {
        // El otro defecto de la misma línea, y el que sumar totales escondía: comparaba la SUMA de
        // los límites contra la SUMA de los gastos. Con un presupuesto muy excedido y otro casi
        // sin usar, el total daba por debajo y el acceso se veía tranquilo mientras el panel de
        // alertas decía «Presupuesto de Mercado superado».
        //
        // Un presupuesto excedido no se compensa con otro que sobró: así no se vive la plata.
        val dos = data.copy(
            budgets = listOf(Budget("Mercado", 2_000_000), Budget("Salidas", 2_000_000)),
            spentByCategory = mapOf("Mercado" to 3_000_000L, "Salidas" to 100_000L),
        )

        assertEquals(true, quickLinkFigure("budgets", dos).isAlert)
        assertEquals(listOf("Mercado"), overBudgetCategories(dos.budgets.orEmpty(), dos.spentByCategory))
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

// ── El Inicio no afirma vacío antes de saber (fix/inicio-no-miente-vacio) ──────────
//
// El dueño mandó un pantallazo de la web recién cargada: «Tu plata $0», «Sin cuentas aún» y la
// guía de Primeros pasos con «Crea tu primera cuenta» sin tildar — mientras abajo, en la misma
// pantalla, ya se veían sus propias cuotas de crédito. En la web la caché del Inicio vive en
// memoria, así que recargar la página la borra y cada arranque en frío pasa por ese estado.

private fun summaryConEventos(n: Int) =
    FinanceSummary(scope = Scope.SELF, balance = 0L, ingresos = 0L, egresos = 0L, eventCount = n)

class InicioNoAfirmaVacioTest {

    @Test
    fun sin_respuesta_todavia_no_se_puede_afirmar_vacio() {
        assertFalse(DashboardData().puedeAfirmarVacio)
    }

    @Test
    fun con_cuentas_pero_sin_resumen_tampoco() {
        // Es justo el estado del pantallazo al revés: llegó una de las dos lecturas. La guía
        // pregunta por cuenta Y movimiento; con media respuesta seguiría mintiendo sobre la otra.
        assertFalse(DashboardData(accounts = emptyList()).puedeAfirmarVacio)
    }

    @Test
    fun cuando_todas_contestaron_vacio_si_se_puede_afirmar() {
        // Las CINCO, no dos: la tarjeta pinta cuatro pasos y cada uno sale de una lectura
        // distinta. Con solo cuentas y resumen, la tarjeta aparecía diciendo «2 de 4» a alguien
        // con 5 recurrentes y 1 crédito, hasta que llegaban las otras respuestas — la misma
        // queja que originó el arreglo, apenas más angosta.
        val data = DashboardData(
            accounts = emptyList(),
            summary = summaryConEventos(0),
            upcoming = emptyList(),
            credits = emptyList(),
            cards = emptyList(),
        )
        assertTrue(data.puedeAfirmarVacio)
        assertFalse(data.hasAccount)
    }

    @Test
    fun con_cuentas_y_resumen_pero_sin_recurrentes_todavia_no() {
        // El paso «Anota tus gastos recurrentes» sale de `upcoming`, que ya distinguía null de
        // vacío por su cuenta. Sin él, el contador miente sobre ese casillero.
        assertFalse(
            DashboardData(
                accounts = emptyList(),
                summary = summaryConEventos(0),
                credits = emptyList(),
                cards = emptyList(),
            ).puedeAfirmarVacio,
        )
    }

    @Test
    fun el_acceso_creditos_no_dice_sin_creditos_a_medias() {
        // El conteo suma préstamos + tarjetas: con una sola de las dos respuestas, un cero no
        // significa nada.
        assertNull(quickLinkFigure("credits", DashboardData(credits = emptyList())).sub)
        assertEquals(
            "Sin créditos",
            quickLinkFigure("credits", DashboardData(credits = emptyList(), cards = emptyList())).sub,
        )
    }

    @Test
    fun el_acceso_inversiones_tampoco_afirma_antes_de_saber() {
        assertNull(quickLinkFigure("investments", DashboardData()).sub)
        assertEquals(
            "Sin inversiones",
            quickLinkFigure("investments", DashboardData(accounts = emptyList())).sub,
        )
    }

    @Test
    fun una_lectura_fallida_no_habilita_la_afirmacion() {
        // `runCatching { … }.onSuccess { … }` deja `accounts` en null cuando la llamada falla, y
        // eso tiene que seguir contando como «no sé» y no como «no tiene». Un error de red no es
        // evidencia sobre la plata de nadie.
        val trasFallarCuentas = DashboardData(summary = summaryConEventos(23))
        assertFalse(trasFallarCuentas.puedeAfirmarVacio)
    }

    @Test
    fun la_cifra_de_cuentas_queda_en_blanco_hasta_que_contesten() {
        val enBlanco = quickLinkFigure("accounts", DashboardData())
        assertNull(enBlanco.value)
        assertNull(enBlanco.sub, "«Sin cuentas aún» es una afirmación: no puede salir antes de la respuesta")

        val contestoVacio = quickLinkFigure("accounts", DashboardData(accounts = emptyList()))
        assertEquals("Sin cuentas aún", contestoVacio.sub)
    }
}
