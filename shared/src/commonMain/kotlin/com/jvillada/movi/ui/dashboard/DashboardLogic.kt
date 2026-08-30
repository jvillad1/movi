package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.ui.recurrentes.resumenRecurrentes
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.renderableSections
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.assetsDebtsNet
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.components.isDebtAccount
import com.jvillada.movi.ui.credits.totalDebtCop
import com.jvillada.movi.shared.time.currentMonthPrefix

/**
 * Todo lo que el Inicio carga del server, junto, para que el renderer SDUI reciba un solo
 * valor y no doce parámetros. Cada campo arranca vacío y se va llenando a medida que llega
 * cada respuesta; una sección que no tiene todavía sus datos simplemente no se pinta (ver
 * [visibleSections]) o muestra la cifra en blanco — nunca un número inventado.
 */
data class DashboardData(
    val summary: FinanceSummary? = null,
    val accounts: List<Account> = emptyList(),
    val credits: List<CreditSummary> = emptyList(),
    /** F20: las tarjetas también son deuda — el acceso «Créditos» las suma junto a los préstamos. */
    val cards: List<CardSummary> = emptyList(),
    /**
     * `null` = todavía no llegó (o su carga falló); lista vacía = llegó y no hay nada.
     *
     * La distinción no era necesaria mientras cada cifra salía de UNA sola fuente: si algo no
     * llegaba, su acceso no se pintaba y listo. Dejó de serlo cuando el acceso «Recurrentes»
     * pasó a COMPONER dos fuentes (reglas + suscripciones): con `emptyList()` por defecto, que
     * se cayera `/api/payments/upcoming` era indistinguible de «este usuario no tiene reglas», y
     * el Inicio mostraba un flujo libre calculado solo con las suscripciones — un número
     * plausible, equivocado y sin nada que avisara. Ver `quickLinkFigure("recurrentes")`.
     */
    val upcoming: List<UpcomingPayment>? = null,
    val budgets: List<Budget> = emptyList(),
    /** Gasto del mes en curso por categoría (ver [spentByCategoryForMonth]). */
    val spentByCategory: Map<String, Long> = emptyMap(),
    val cardCandidates: Int = 0,
    val pendingSms: Int = 0,
    val goals: List<Goal> = emptyList(),
    val subscriptions: SubscriptionsResult? = null,
) {
    val hasAccount: Boolean get() = accounts.isNotEmpty()
    /** F20: una tarjeta registrada también tilda el paso de Créditos — es una deuda como cualquier préstamo. */
    val hasCredit: Boolean get() = credits.isNotEmpty() || cards.isNotEmpty()
    /**
     * La apertura de cuenta no cuenta (F54) y un traspaso cuenta una sola vez aunque sean dos
     * eventos — ver `FinanceSummary.eventCount` y `movementCount` en `:core`.
     */
    val hasMovement: Boolean get() = (summary?.eventCount ?: 0) > 0
    /**
     * F6: "Anota tus gastos recurrentes" se tilda con una regla recurrente REAL. `getUpcomingPayments`
     * también trae las cuotas sintéticas de los créditos (id con [CREDIT_RULE_PREFIX]) y, desde
     * F20, los pagos de tarjeta (id con [CARD_RULE_PREFIX]); esas tildan el paso de Créditos,
     * no este.
     */
    val hasRecurringRule: Boolean get() = upcoming.orEmpty().any {
        !it.rule.id.startsWith(CREDIT_RULE_PREFIX) && !it.rule.id.startsWith(CARD_RULE_PREFIX)
    }
}

// ── Las dos cifras del Inicio ──────────────────────────────────────────────────────

/**
 * Lo que TIENES y lo que VALES, separadas y con nombre propio.
 *
 * Nacen de un reporte del dueño: cargó su primer crédito y dijo «lo que hizo fue descontarme
 * de la cuenta todo el saldo del crédito». La cuenta no se tocó —la deuda vive en su propia
 * cuenta `LOAN` y nunca entró al flujo de caja— pero el número grande del Inicio pasó de
 * +$20.308.659 a −$28.710.542 de un día para el otro, sin nada que lo explicara. Que un dato
 * sea correcto no lo hace legible: el Inicio mostraba el **patrimonio** bajo el rótulo
 * «Balance neto», y con cinco créditos por cargar (~$1.505 millones) la primera cifra de cada
 * mañana iba a ser −$1.493 millones.
 *
 * Por eso el Inicio muestra ahora [tuPlata] arriba y [patrimonio] debajo, rotulados distinto.
 *
 * **Qué cuenta como «tu plata»: Dinero + Inversión, o sea toda cuenta que no sea deuda.**
 * Tres razones, en orden de peso:
 * 1. Es plata suya. Un CDT o un fondo es plata guardada, no plata ajena; esconderla del número
 *    grande obligaría a sumar dos cifras de dos pantallas para saber cuánto tiene.
 * 2. Es exactamente lo que ya muestra la fila «Cuentas» de EXPLORA y el renglón «Activos» de
 *    la pantalla de Cuentas (ambos, `assetsDebtsNet(...).first`). Dejar el hero en solo-Dinero
 *    crearía un tercer número que no coincide con ninguno de los dos — el desacuerdo que la
 *    Ola 4 tuvo que arreglar entre Créditos y el Inicio.
 * 3. La distinción que de verdad hizo daño acá no es líquido vs. invertido, es **tuyo vs.
 *    debido**. Esa es la que separan estas dos cifras.
 *
 * Lo invertido no se pierde de vista: cuando hay algo en Inversión, el hero lo desglosa
 * ([disponible] y [invertido]) en una línea secundaria.
 */
data class HeroBalance(
    /** Lo que tienes: saldo COP de toda cuenta que no sea deuda (Dinero + Inversión). */
    val tuPlata: Long,
    /** La parte de [tuPlata] en el grupo Dinero — efectivo, corriente, ahorros. */
    val disponible: Long,
    /** La parte de [tuPlata] en el grupo Inversión. */
    val invertido: Long,
    /** Lo que debes: tarjetas y préstamos, en COP (estimado cuando hay saldo en otra moneda). */
    val deudas: Long,
    /** [tuPlata] − [deudas]. Puede ser negativo, y con cinco créditos hipotecarios lo será. */
    val patrimonio: Long,
) {
    /**
     * ¿Hay algo que decir sobre el patrimonio? Con el grupo Deuda en cero, [patrimonio] y
     * [tuPlata] son el MISMO número y el hero no repite la cifra.
     *
     * Es `!= 0`, no `> 0`: una tarjeta **sobrepagada** deja [deudas] en negativo, y ahí el
     * patrimonio es MAYOR que «tu plata» — un dato que vale la pena mostrar y que un `> 0`
     * escondía justo cuando la línea dejaba de ser redundante. (Ver [patrimonioExplicacion],
     * que cambia «menos … en deudas» por «más … a favor en créditos» en ese caso.)
     */
    val hasDebt: Boolean get() = deudas != 0L
    /** Sin nada invertido no hay nada que desglosar: el hero no pinta la línea del desglose. */
    val hasInvestments: Boolean get() = invertido != 0L
}

/**
 * El rótulo de la cifra grande del Inicio, **fijo en el binario y no leído de la definición SDUI**.
 *
 * Es lo único de la tarjeta que no se puede editar desde el Editor de pantallas, y la razón es
 * la asimetría del despliegue: la fila de `screen_definitions` llega a TODOS los clientes en el
 * instante del deploy, pero el renderer viaja en el binario. La PWA está a salvo (el mismo
 * deploy sirve el wasm y la fila); un APK ya instalado no.
 *
 * Si el rótulo viajara en el schema, cambiar la semilla a «Tu plata» habría hecho que el APK 1.7
 * —que sigue pintando el patrimonio— titulara **«Tu plata −$1.492.710.542»**: exactamente la
 * lectura que esta rama existe para evitar, ahora afirmada por el rótulo. Con el rótulo en el
 * binario no hay ventana en ninguna de las dos direcciones: el cliente viejo dice «Balance neto»
 * sobre el patrimonio y el nuevo dice «Tu plata» sobre los activos, y **cada binario rotula lo
 * que él mismo calcula**.
 *
 * Es el mismo trato que ya tenían los tres sub-rótulos de abajo («Ingresos», «Gastos», «Flujo
 * del mes»): están cableados en el renderer, y por eso cambiar «Egresos» por «Gastos» en la Ola
 * 8 no necesitó subir la generación del seed.
 *
 * El costo asumido: el hero no se puede renombrar desde el Editor. Se verificó contra producción
 * (2026-08-28) que el dueño nunca editó el Inicio, y el resto de la definición —orden de
 * secciones, accesos, títulos de las demás— sigue siendo editable como antes.
 */
const val HERO_BALANCE_TITLE = "Tu plata"

/**
 * [HERO_BALANCE_TITLE], ignorando a propósito `section.title`.
 *
 * Existe como función —en vez de usar la constante directo en el renderer— para que el test
 * pueda fijar la decisión: alguien que "arregle" esto de vuelta a `section.title ?: …` reabre
 * la ventana de desalineación descrita arriba, y eso tiene que romper una prueba, no descubrirse
 * en el teléfono del dueño.
 */
fun heroBalanceTitle(@Suppress("UNUSED_PARAMETER") section: ScreenSection): String = HERO_BALANCE_TITLE

/**
 * La línea que explica de dónde sale el patrimonio — la resta escrita, que es lo que faltaba el
 * día del reporte del dueño.
 *
 * Dos redacciones porque [HeroBalance.deudas] puede ser negativo (una tarjeta sobrepagada):
 * «menos … en deudas» mentiría con un signo menos delante del monto.
 */
fun patrimonioExplicacion(balance: HeroBalance): String =
    if (balance.deudas >= 0L) "Tu plata menos ${formatMoneyCompact(balance.deudas)} en deudas"
    else "Tu plata más ${formatMoneyCompact(-balance.deudas)} a favor en créditos"

/**
 * Deriva [HeroBalance] de las cuentas. Se apoya en [assetsDebtsNet] a propósito —no
 * reimplementa la suma— para que el hero, la fila «Cuentas» del Inicio y el «Patrimonio neto»
 * de la pantalla de Cuentas no puedan dar tres números distintos.
 */
fun heroBalance(accounts: List<Account>): HeroBalance {
    val (activos, deudas, neto) = assetsDebtsNet(accounts)
    val invertido = accounts.filter { it.type.group == AccountGroup.INVERSION }.sumOf { it.balance }
    return HeroBalance(
        tuPlata = activos,
        disponible = activos - invertido,
        invertido = invertido,
        deudas = deudas,
        patrimonio = neto,
    )
}

// ── Próximos pagos ─────────────────────────────────────────────────────────────────

/**
 * Pagos (egresos) que vencen dentro de [days] días, incluidos los ya vencidos que el server
 * todavía considera de este periodo (daysUntil negativo): son los más urgentes y van primero.
 * Los ingresos recurrentes (sueldo) no son "pagos" y quedan fuera.
 */
fun upcomingPaymentsWithin(payments: List<UpcomingPayment>, days: Int = 7, max: Int = 3): List<UpcomingPayment> =
    payments
        .filter { it.rule.type == TransactionType.EXPENSE && it.daysUntil <= days }
        .sortedBy { it.daysUntil }
        .take(max)

fun dueLabel(daysUntil: Int): String = when {
    daysUntil < -1 -> "Vencido hace ${-daysUntil} días"
    daysUntil == -1 -> "Vencido ayer"
    daysUntil == 0 -> "Vence hoy"
    daysUntil == 1 -> "Vence mañana"
    else -> "Vence en $daysUntil días"
}

// ── Gasto del mes ──────────────────────────────────────────────────────────────────

/**
 * Gasto por categoría del mes [monthPrefix] ("2026-08"). Misma regla que el resumen del
 * server: solo egresos en COP que cuentan como flujo de caja (un ajuste de deuda o un pago
 * de tarjeta no es gasto del mes).
 */
fun spentByCategoryForMonth(days: List<EventDay>, monthPrefix: String): Map<String, Long> =
    days.filter { it.date.startsWith(monthPrefix) }
        .flatMap { it.items }
        .filter { it.type == TransactionType.EXPENSE && it.countsAsCashFlow && it.currency == "COP" }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }

/**
 * "2026-08" del día de hoy en la zona de la app (Bogotá, ver [com.jvillada.movi.shared.time.AppTimeZone])
 * — la misma zona con la que el server fecha `EventDay.date`. Antes era UTC de los dos lados:
 * entre las 7 pm y la medianoche del último día del mes, Inicio y Presupuestos ya mostraban
 * el mes siguiente (vacío) mientras el dueño seguía en el mes viejo.
 */
fun currentMonthPrefixApp(): String = currentMonthPrefix()

// ── Alertas ────────────────────────────────────────────────────────────────────────

data class DashboardAlert(val text: String, val target: Screen)

/** Misma regla que Presupuestos: OVER = gastado ≥ límite; un límite 0 no puede "superarse". */
fun overBudgetCategories(budgets: List<Budget>, spentByCategory: Map<String, Long>): List<String> =
    budgets.filter { it.monthlyLimit > 0 && (spentByCategory[it.category] ?: 0L) >= it.monthlyLimit }
        .map { it.category }

/**
 * Cada alerta es una fila tocable que lleva a donde se resuelve. Sin nada pendiente devuelve
 * vacío y la sección entera no se pinta — nada de "Sin alertas por ahora".
 */
fun dashboardAlerts(overBudget: List<String>, cardCandidates: Int, pendingSms: Int): List<DashboardAlert> = buildList {
    when (overBudget.size) {
        0 -> Unit
        1 -> add(DashboardAlert("Presupuesto de ${overBudget[0]} superado", Screen.Budgets))
        else -> add(DashboardAlert("${overBudget.size} presupuestos superados", Screen.Budgets))
    }
    if (cardCandidates > 0) {
        add(DashboardAlert(plural(cardCandidates, "pago de tarjeta", "pagos de tarjeta") + " por confirmar", Screen.Transactions))
    }
    if (pendingSms > 0) {
        add(DashboardAlert(plural(pendingSms, "mensaje del banco", "mensajes del banco") + " por confirmar", Screen.SMSInbox))
    }
}

// ── Accesos con cifra ──────────────────────────────────────────────────────────────

/** Cifra (y línea secundaria) de un acceso; `value` null = no hay nada que mostrar todavía. */
data class LinkFigure(val value: String? = null, val sub: String? = null, val isAlert: Boolean = false)

/**
 * La cifra que acompaña a cada acceso según su destino de navegación — lo que antes mostraba
 * "Análisis" (F40). Un destino sin cifra conocida (Movi AI, Perfil…) devuelve todo en null
 * y la fila se pinta solo con el título.
 */
fun quickLinkFigure(target: String, data: DashboardData): LinkFigure = when (target) {
    "accounts" -> {
        // El conteo nombra EXACTAMENTE el conjunto que suma la cifra: las cuentas que no son
        // deuda. Mismo predicado que usa [assetsDebtsNet] para sus «activos» —no una copia que
        // pueda separarse— y mismo conjunto que lista la pantalla de destino, que desde F61
        // muestra solo los grupos Dinero e Inversión (las deudas viven en Créditos).
        //
        // Contar `data.accounts.size` era correcto en la Ola 4, cuando Cuentas listaba TODO.
        // F61 (Ola 7) dejó las deudas afuera de esa pantalla y el conteo quedó hablando de otra
        // cosa que la cifra: con un ahorro y cinco créditos, la fila decía «$12.383.363 ·
        // 6 cuentas» y al tocarla aparecía «DINERO · 1 / INVERSIÓN · 0». Las deudas no se
        // pierden de vista: la fila «Créditos», justo debajo, las cuenta y las suma.
        val (activos, _, _) = assetsDebtsNet(data.accounts)
        val propias = data.accounts.count { !isDebtAccount(it.type) }
        // Solo deudas cargadas (el estado de quien arranca por sus créditos) es «sin cuentas»,
        // no «0 cuentas» al lado de un $0: es lo que dicen los dos grupos vacíos de la pantalla
        // de destino, y un cero grande en el Inicio se lee como que algo se perdió. Sigue la
        // regla de toda esta función: sin nada que contar, no se pinta cifra.
        if (propias == 0) LinkFigure(sub = "Sin cuentas aún")
        else LinkFigure(formatCOP(activos), plural(propias, "cuenta", "cuentas"))
    }
    "credits" -> {
        // F20: préstamos + tarjetas, con la MISMA función que usa la pantalla de Créditos para
        // su «Deuda total» — hallazgo de la Ola 4: la pantalla y el Inicio daban números
        // distintos (acá se sumaba solo LOAN).
        val count = data.credits.size + data.cards.size
        if (count == 0) LinkFigure(sub = "Sin créditos")
        else LinkFigure(formatCOP(totalDebtCop(data.credits, data.cards)), plural(count, "crédito", "créditos"))
    }
    "budgets" -> {
        if (data.budgets.isEmpty()) LinkFigure(sub = "Sin presupuestos")
        else {
            val limit = data.budgets.sumOf { it.monthlyLimit }
            val spent = data.budgets.sumOf { data.spentByCategory[it.category] ?: 0L }
            LinkFigure(formatCOP(spent), "de ${formatCOP(limit)} este mes", isAlert = limit > 0 && spent >= limit)
        }
    }
    "goals" -> {
        if (data.goals.isEmpty()) LinkFigure(sub = "Sin metas")
        else LinkFigure(formatCOP(data.goals.sumOf { it.saved }), plural(data.goals.size, "meta", "metas"))
    }
    "investments" -> {
        // F50: cuentas tipo INVESTMENT, no el modelo de "posiciones" (holdings) que el server
        // siempre devolvía vacío. F61: el target abre Cuentas (grupo Inversión).
        val investmentAccounts = data.accounts.filter { it.type == AccountType.INVESTMENT }
        if (investmentAccounts.isEmpty()) LinkFigure(sub = "Sin inversiones")
        else LinkFigure(formatCOP(investmentAccounts.sumOf { it.balance }), plural(investmentAccounts.size, "cuenta", "cuentas"))
    }
    "recurrentes" -> {
        // Ola 8: el acceso muestra exactamente el número grande de la pantalla de destino —
        // «Flujo libre»— calculado con la MISMA función pura que usa Recurrentes, para que
        // tocar la tarjeta no lleve a una cifra distinta de la que se tocó. (La Ola 4 ya había
        // encontrado ese desacuerdo entre Créditos y el Inicio.)
        //
        // `upcoming` trae UNA entrada por regla (ver `upcomingPayments`: mapea 1:1), así que
        // sirve de lista de reglas sin pedir nada nuevo — el Inicio sigue liviano. Las cuotas
        // sintéticas de créditos y tarjetas se descartan: no son reglas que el dueño escribió y
        // tampoco salen en la lista de Recurrentes.
        // Esta cifra COMPONE dos fuentes, así que exige las dos. Con una sola —la otra se cayó,
        // o todavía no llegó— el número saldría plausible y equivocado: sin las reglas, un
        // sueldo de +$5.000.000 desaparece y «libre al mes» queda en −$44.900, en rojo, contra
        // los $2.955.100 que muestra la pantalla de destino. Sin las dos no se pinta cifra
        // (título solo), que es la regla de toda esta función: nunca un número inventado.
        val reglas = data.upcoming?.filterNot {
            it.rule.id.startsWith(CREDIT_RULE_PREFIX) || it.rule.id.startsWith(CARD_RULE_PREFIX)
        }?.map { it.rule }
        val subs = data.subscriptions
        if (reglas == null || subs == null) LinkFigure()
        else {
            val resumen = resumenRecurrentes(reglas, subs)
            if (resumen.items.isEmpty()) LinkFigure(sub = "Sin recurrentes")
            else LinkFigure(
                formatCOP(resumen.flujoLibre),
                "libre al mes · " + plural(resumen.items.size, "recurrente", "recurrentes"),
                isAlert = resumen.flujoLibre < 0,
            )
        }
    }
    // Se queda para los Inicios ya guardados que todavía traen el acceso viejo: el target sigue
    // siendo válido y abre Recurrentes (ver SduiRenderer.screenForTarget).
    "subscriptions" -> {
        val subs = data.subscriptions
        // Solo las activas (AUTO/CONFIRMED) — es lo que suma monthlyTotalCop y lo que la pantalla
        // Recurrentes lista como activas. Las candidatas y las descartadas no son suscripciones
        // todavía (o ya no): contarlas acá daba «$0 · 4 suscripciones al mes» con cero activas.
        val active = subs?.subscriptions?.count { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED } ?: 0
        val candidates = subs?.subscriptions?.count { it.status == SubStatus.CANDIDATE } ?: 0
        when {
            subs == null || (active == 0 && candidates == 0) -> LinkFigure(sub = "Sin suscripciones")
            active == 0 -> LinkFigure(sub = plural(candidates, "por confirmar", "por confirmar"))
            else -> LinkFigure(formatCOP(subs.monthlyTotalCop), plural(active, "suscripción", "suscripciones") + " al mes")
        }
    }
    else -> LinkFigure()
}

private fun plural(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"

// ── F5: panel de notificaciones ────────────────────────────────────────────────────

/** Una fila del panel de la campana: el texto y a dónde lleva tocarla. */
data class NotificationRow(val text: String, val target: Screen)

/**
 * Todo lo que la campana muestra — una vista DERIVADA de los datos que el Inicio ya carga,
 * sin modelo de notificaciones persistente ni "marcar leído". Combina los pagos que vencen
 * pronto (cada uno a su propio destino, Recurrentes o Créditos según de qué regla venga) con
 * las mismas alertas que ya calcula [dashboardAlerts] — candidatos de pago de tarjeta, SMS
 * pendientes y presupuestos superados —, así el punto rojo y el panel nunca dicen algo que la
 * campana no pueda resolver.
 */
fun notificationRows(data: DashboardData): List<NotificationRow> = buildList {
    upcomingPaymentsWithin(data.upcoming.orEmpty()).forEach { p ->
        // F20: las cuotas de crédito y los pagos de tarjeta son sintéticos (UpcomingPayment
        // generado por el server, no una regla que viva en Recurrentes) — se resuelven en
        // Créditos, no en Recurrentes.
        val target = if (p.rule.id.startsWith(CREDIT_RULE_PREFIX) || p.rule.id.startsWith(CARD_RULE_PREFIX)) {
            Screen.Credits
        } else {
            Screen.Recurrentes
        }
        add(NotificationRow("${p.rule.name} · ${dueLabel(p.daysUntil)}", target))
    }
    dashboardAlerts(
        overBudgetCategories(data.budgets, data.spentByCategory), data.cardCandidates, data.pendingSms,
    ).forEach { add(NotificationRow(it.text, it.target)) }
}

// ── Secciones visibles ─────────────────────────────────────────────────────────────

/**
 * Secciones de la definición que tienen algo que mostrar con estos datos. Próximos pagos y
 * Alertas desaparecen del todo cuando están vacías (así pidió el dueño: nada de cajas vacías
 * ocupando lugar); un bloque de accesos sin tarjetas tampoco se pinta.
 */
fun visibleSections(def: ScreenDefinition, data: DashboardData): List<ScreenSection> =
    renderableSections(def).filter { section ->
        when (section.type) {
            "UPCOMING_PAYMENTS" -> upcomingPaymentsWithin(data.upcoming.orEmpty()).isNotEmpty()
            "ALERTS" -> dashboardAlerts(
                overBudgetCategories(data.budgets, data.spentByCategory), data.cardCandidates, data.pendingSms,
            ).isNotEmpty()
            "QUICK_LINKS_WITH_TOTALS", "LINK_LIST", "CARD_ROW", "CARD_LIST" -> section.cards.isNotEmpty()
            else -> true
        }
    }


/**
 * Gasto por categoría del **período** [ventana], en vez del mes de calendario.
 *
 * Es la misma regla que [spentByCategoryForMonth] —solo egresos en COP que cuentan como flujo—
 * cambiando el criterio de pertenencia: el prefijo de fecha («2026-08») no sirve cuando el
 * período cruza dos meses de calendario, que es justo lo que pasa con cualquier corte que no sea
 * el día 1.
 *
 * El filtro va por `timestamp` contra la ventana, que es exactamente lo que hace el server
 * (`currentPeriodWindow`). Las dos mitades tienen que coincidir o Inicio y Presupuestos vuelven a
 * decir cifras distintas del mismo presupuesto.
 */
fun spentByCategoryForPeriod(days: List<EventDay>, ventana: LongRange): Map<String, Long> =
    days.flatMap { it.items }
        .filter { it.timestamp in ventana }
        .filter { it.type == TransactionType.EXPENSE && it.countsAsCashFlow && it.currency == "COP" }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
