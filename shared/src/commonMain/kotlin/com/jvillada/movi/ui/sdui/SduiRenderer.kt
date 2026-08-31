package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.CardRow
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.MonoText
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.LinkFigure
import com.jvillada.movi.ui.dashboard.dashboardAlerts
import com.jvillada.movi.ui.dashboard.dueLabel
import com.jvillada.movi.ui.dashboard.heroBalance
import com.jvillada.movi.ui.dashboard.heroBalanceTitle
import com.jvillada.movi.ui.dashboard.patrimonioExplicacion
import com.jvillada.movi.ui.dashboard.overBudgetCategories
import com.jvillada.movi.ui.dashboard.quickLinkFigure
import com.jvillada.movi.ui.dashboard.upcomingPaymentsWithin
import com.jvillada.movi.ui.dashboard.visibleSections

/**
 * Pinta una [ScreenDefinition] del Inicio. Consume `visibleSections(definition, data)`:
 * los tipos desconocidos y las acciones inválidas ya vienen filtrados de
 * `renderableSections` (core), y las secciones de datos que no tienen nada que mostrar
 * (Próximos pagos, Alertas) ya vienen descartadas — acá no se vuelve a validar nada.
 *
 * [header] es el chrome nativo que va arriba del todo y scrollea con el resto (la guía de
 * primeros pasos); no viaja en el schema a propósito — así existe siempre, sin depender de
 * `screen_definitions`.
 */
@Composable
fun SduiRenderer(
    definition: ScreenDefinition,
    data: DashboardData,
    modifier: Modifier = Modifier,
    onNavigate: (Screen) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val sections = visibleSections(definition, data)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (header != null) {
            item {
                header()
                Spacer(Modifier.height(20.dp))
            }
        }
        sections.forEachIndexed { index, section ->
            item {
                if (index > 0) Spacer(Modifier.height(20.dp))
                SduiSection(section, data, onNavigate, uriHandler)
            }
        }
    }
}

@Composable
private fun SduiSection(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    uriHandler: UriHandler,
) {
    when (section.type) {
        "HERO_BALANCE" -> HeroBalanceSection(section, data, onNavigate)
        "UPCOMING_PAYMENTS" -> UpcomingPaymentsSection(section, data, onNavigate)
        "ALERTS" -> AlertsSection(section, data, onNavigate)
        "QUICK_LINKS_WITH_TOTALS" -> QuickLinksSection(section, data, onNavigate, uriHandler)
        "CARD_ROW" -> CardRowSection(section, onNavigate, uriHandler)
        "CARD_LIST" -> CardListSection(section, onNavigate, uriHandler)
        "LINK_LIST" -> LinkListSection(section, onNavigate, uriHandler)
        "BANNER" -> BannerSection(section, onNavigate, uriHandler)
    }
}

// ── Action dispatch ─────────────────────────────────────────────────────────────────

/**
 * NAVIGATE target → [Screen], cubriendo los targets de `ScreenTaxonomy.NAVIGATE_TARGETS`.
 * `renderableSections` ya strippeó cualquier NAVIGATE fuera de esa lista, así que el `else`
 * es defensivo — `target` es un String, no un sealed type, y el `when` no puede ser exhaustivo.
 */
private fun screenForTarget(target: String): Screen? = when (target) {
    "dashboard" -> Screen.Dashboard
    "transactions" -> Screen.Transactions
    "quickadd" -> Screen.QuickAdd()
    "budgets" -> Screen.Budgets
    "mas" -> Screen.Mas
    "accounts" -> Screen.Accounts
    "credits" -> Screen.Credits
    "goals" -> Screen.Goals
    // F61: Inversiones dejó de ser pantalla. Una definición guardada (o el Editor) puede seguir
    // trayendo este target — se manda a Cuentas, que es donde ahora viven las cuentas de
    // inversión. Nunca un crash por destino desconocido.
    "investments" -> Screen.Accounts
    // Ola 8: Suscripciones dejó de ser pantalla, igual que Inversiones. Mismo trato: el target
    // sobrevive (el acceso «Suscripciones» del Inicio ya está guardado en la DB de cada
    // instalación) y se manda a Recurrentes, que es donde ahora viven las suscripciones.
    "subscriptions" -> Screen.Recurrentes
    "recurrentes" -> Screen.Recurrentes
    "categorias" -> Screen.Categorias
    "extractos" -> Screen.Extractos
    "aichat" -> Screen.AIChat
    "profile" -> Screen.Profile
    else -> null
}

private fun performAction(action: ScreenAction, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    when (action.type) {
        "NAVIGATE" -> screenForTarget(action.target)?.let(onNavigate)
        "OPEN_URL" -> uriHandler.openUri(action.target)
    }
}

private fun clickHandler(
    action: ScreenAction?,
    onNavigate: (Screen) -> Unit,
    uriHandler: UriHandler,
): (() -> Unit)? = action?.let { { performAction(it, onNavigate, uriHandler) } }

// ── HERO_BALANCE — tu plata arriba, el patrimonio debajo, y el flujo del mes ───────

/**
 * El número grande es **lo que tienes** ([HeroBalance.tuPlata]); el **patrimonio neto** queda
 * debajo, secundario pero visible.
 *
 * Antes el número grande era el patrimonio bajo el rótulo «Balance neto». El dueño cargó su
 * primer crédito y reportó «me descontó de la cuenta todo el saldo del crédito»: no había tal
 * descuento —la deuda vive en su propia cuenta y no entra al flujo de caja—, pero la cifra de
 * portada saltó de +$20,3M a −$28,7M sin nada que lo explicara. El dato era correcto y aun así
 * ilegible. Ver [heroBalance] para qué cuenta como «tu plata» y por qué.
 *
 * El patrimonio **no se esconde**: con los cinco créditos del dueño (~$1.505M) es la foto
 * honesta de su situación. Se muestra con tres cuidados para que se entienda en vez de asustar:
 * - solo cuando el grupo Deuda no está en cero (en cero repetiría el número de arriba, y esta
 *   tarjeta ya compite con las filas «Cuentas» y «Créditos» de EXPLORA);
 * - **en gris, no en rojo** — el rojo de esta tarjeta está reservado al «Flujo del mes», que es
 *   el resultado del mes y algo sobre lo que se puede actuar hoy; un patrimonio negativo por
 *   hipotecas es una estructura de largo plazo, no una pérdida de este mes. La pantalla de
 *   Cuentas pinta ESTE MISMO número y sigue la misma regla, porque tocar la línea lleva ahí:
 *   ver gris acá y rojo a 28 sp un toque después se leería como que algo empeoró en el camino;
 * - con la resta escrita debajo («Tu plata menos $1.505,1M en deudas»), que es justamente lo
 *   que faltaba el día del reporte.
 *
 * Tocar esa línea abre Cuentas, cuyo hero es el mismo «PATRIMONIO NETO» desglosado en Activos
 * y Deudas.
 */
@Composable
private fun HeroBalanceSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit) {
    val balance = heroBalance(data.accounts.orEmpty())
    val ingresos = data.summary?.ingresos ?: 0L
    val egresos = data.summary?.egresos ?: 0L
    val flujo = ingresos - egresos

    MinCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(22.dp),
    ) {
        // `section.title` NO se lee acá: el rótulo del hero es [HERO_BALANCE_TITLE], que viaja
        // en el binario. Ver su KDoc — es la única forma de que cada cliente rotule lo que él
        // mismo calcula, sin ventana de desalineación con la fila del server.
        Text(text = heroBalanceTitle(section), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MinTextMute)
        Spacer(Modifier.height(10.dp))
        // Antes de que las cuentas contesten, un «$0» de 44 sp es la afirmación más fuerte que
        // hace esta pantalla, y es falsa mientras carga: en la web (sin caché que sobreviva a
        // recargar) el dueño veía «Tu plata $0» durante segundos. Un guion no miente.
        Text(
            text = if (data.accounts == null) "—" else formatCOP(balance.tuPlata), // formatCOP ya trae el signo (F36) — no duplicarlo acá
            fontSize = 44.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            // Una cuenta en descubierto SÍ es una alarma del día: eso se queda en rojo.
            color = if (balance.tuPlata < 0) MinExpense else MinText,
            letterSpacing = (-1.6).sp,
            lineHeight = 44.sp,
        )
        if (balance.hasInvestments) {
            // Las cuentas de Inversión entran en «tu plata», así que la línea dice cuánto de ese
            // total está guardado. Usa el vocabulario de la pantalla de Cuentas ("Dinero" e
            // "Inversión" son sus dos grupos con subtotal), no dos palabras nuevas.
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Dinero ${formatMoneyCompact(balance.disponible)} · Inversión ${formatMoneyCompact(balance.invertido)}",
                fontSize = 11.5.sp,
                color = MinTextMute,
            )
        }
        if (balance.hasDebt) {
            Spacer(Modifier.height(16.dp))
            Hairline()
            // La explicación va DEBAJO de la fila, a ancho completo, y no como sub-línea de la
            // etiqueta: en un teléfono de 375 px compartir el renglón con la cifra la partía en
            // «Tu plata menos $1.505,1M en / deudas», y la resta —que es todo el punto de esta
            // línea— dejaba de leerse de un vistazo. Verificado a ojo en 375×812.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.Accounts) }
                    .padding(top = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Patrimonio neto",
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = MinTextMute,
                        fontWeight = FontWeight.Medium,
                    )
                    MonoText(formatMoneyCompact(balance.patrimonio), 15f, color = MinTextDim, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = patrimonioExplicacion(balance),
                    fontSize = 11.sp,
                    color = MinTextMute,
                )
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Spacer(Modifier.height(18.dp))
        }
        Hairline()
        Spacer(Modifier.height(16.dp))
        // Las tres cifras chicas salen del MISMO resumen que el número grande de arriba, así que
        // siguen su misma regla: sin respuesta, un guion. Antes decían «$0 / $0 / $0» al lado del
        // «—» recién puesto arriba, que es la peor combinación posible — parece que la app sabe.
        val sinResumen = data.summary == null
        fun cifra(v: Long) = if (sinResumen) "—" else formatMoneyCompact(v)
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                Triple("Ingresos", cifra(ingresos), MinText),
                Triple("Gastos", cifra(egresos), MinText),
                // F36: un mes en rojo se ve en rojo.
                Triple("Flujo del mes", cifra(flujo), if (!sinResumen && flujo < 0) MinExpense else MinText),
            ).forEach { (label, value, color) ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(value, fontSize = 14.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = color, letterSpacing = (-0.3).sp)
                }
            }
        }
    }
}

// ── UPCOMING_PAYMENTS — lo que vence en los próximos 7 días (reglas + cuotas) ──────

@Composable
private fun UpcomingPaymentsSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit) {
    val rows = upcomingPaymentsWithin(data.upcoming.orEmpty())
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MinSectionHeader(
            title = section.title ?: "Próximos pagos",
            action = "Ver todos",
            onAction = { onNavigate(Screen.Recurrentes) },
        )
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            rows.forEachIndexed { i, p ->
                // F20: la regla sintética de una tarjeta (card_) también se gestiona en Créditos.
                val isCredit = p.rule.id.startsWith(CREDIT_RULE_PREFIX) || p.rule.id.startsWith(CARD_RULE_PREFIX)
                val urgent = p.daysUntil <= 0
                CardRow(
                    left = { Text(p.rule.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                    sub = dueLabel(p.daysUntil),
                    // Una tarjeta no tiene cuota: su monto es el SALDO. Mostrarlo bajo «Próximos
                    // pagos» anunciaba $27.501.150 como el próximo pago del dueño cuando el mínimo
                    // ronda el 5 %. Se muestra el saldo, dicho con su nombre y en gris — no como
                    // la cifra que va a salir de su cuenta. Ver `RecurringRule.montoEsSaldo`.
                    right = {
                        if (p.rule.montoEsSaldo) {
                            MonoText("saldo ${formatCOP(p.rule.amount)}", 12.5f, color = MinTextMute)
                        } else {
                            MonoText(formatCOP(p.rule.amount), 14.5f, color = if (urgent) MinExpense else MinText)
                        }
                    },
                    isLast = i == rows.lastIndex,
                    // Una cuota de crédito se gestiona en Créditos; una regla, en Recurrentes.
                    onClick = { onNavigate(if (isCredit) Screen.Credits else Screen.Recurrentes) },
                )
            }
        }
    }
}

// ── ALERTS — solo cuando hay algo; cada fila lleva a donde se resuelve ─────────────

@Composable
private fun AlertsSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit) {
    val alerts = dashboardAlerts(overBudgetCategories(data.budgets, data.spentByCategory), data.cardCandidates, data.pendingSms)
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MinSectionHeader(title = section.title ?: "Alertas", count = alerts.size)
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            alerts.forEachIndexed { i, alert ->
                CardRow(
                    left = { Text(alert.text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinExpense) },
                    showChevron = true,
                    isLast = i == alerts.lastIndex,
                    onClick = { onNavigate(alert.target) },
                )
            }
        }
    }
}

// ── QUICK_LINKS_WITH_TOTALS — accesos con la cifra de su destino (lo que era Análisis) ─

@Composable
private fun QuickLinksSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        section.title?.let { MinSectionHeader(title = it) }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            section.cards.forEachIndexed { i, card ->
                val target = card.action?.takeIf { it.type == "NAVIGATE" }?.target
                val figure = target?.let { quickLinkFigure(it, data) } ?: LinkFigure()
                CardRow(
                    left = { Text(card.title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                    // El subtítulo escrito en el Editor manda sobre el calculado.
                    sub = card.subtitle ?: figure.sub,
                    right = figure.value?.let { value ->
                        { MonoText(value, 14.5f, color = if (figure.isAlert) MinExpense else MinText) }
                    },
                    showChevron = card.action != null,
                    isLast = i == section.cards.lastIndex,
                    onClick = clickHandler(card.action, onNavigate, uriHandler),
                )
            }
        }
    }
}

// ── Generic sections: CARD_ROW / CARD_LIST / LINK_LIST / BANNER ────────────────────
// `imageUrl` is intentionally ignored in v1 — movi has no coil/kamel image-loader
// dependency (verified via grep), so cards render title/subtitle/badge only.

@Composable
private fun CardRowSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column {
        section.title?.let {
            Box(Modifier.padding(horizontal = 16.dp)) { MinSectionHeader(title = it) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.cards) { card -> SduiCardTile(card, clickHandler(card.action, onNavigate, uriHandler)) }
        }
    }
}

@Composable
private fun CardListSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        section.title?.let { MinSectionHeader(title = it) }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            section.cards.forEachIndexed { i, card ->
                CardRow(
                    left = { Text(card.title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                    sub = card.subtitle,
                    right = card.badge?.let { badge -> { Text(badge, fontSize = 12.sp, color = MinTextMute) } },
                    showChevron = card.action != null,
                    isLast = i == section.cards.size - 1,
                    onClick = clickHandler(card.action, onNavigate, uriHandler),
                )
            }
        }
    }
}

@Composable
private fun LinkListSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        section.title?.let {
            MinSectionHeader(title = it)
        }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            section.cards.forEachIndexed { i, card ->
                CardRow(
                    left = { Text(card.title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                    showChevron = true,
                    isLast = i == section.cards.size - 1,
                    onClick = clickHandler(card.action, onNavigate, uriHandler),
                )
            }
        }
    }
}

/**
 * CRITICAL: `ScreenSection` has no `action` field. The seed puts the AI banner's NAVIGATE
 * action in `cards[0].action` (see DashboardDefaults.kt), so a BANNER is clickable exactly
 * when it has a card carrying an action — read from there, not from any section-level field.
 */
@Composable
private fun BannerSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    val action = section.cards.firstOrNull()?.action
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        section.title?.let { MinSectionHeader(title = it) }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            onClick = clickHandler(action, onNavigate, uriHandler),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    section.text?.let { Text(it, fontSize = 14.sp, color = MinTextMute) }
                }
                if (action != null) ChevronRight()
            }
        }
    }
}

@Composable
private fun SduiCardTile(card: ScreenCard, onClick: (() -> Unit)?) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    MinCard(
        modifier = Modifier.width(150.dp).then(clickModifier),
        variant = MinCardVariant.Default,
        padding = PaddingValues(14.dp),
    ) {
        Text(card.title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = MinText, maxLines = 2)
        card.subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 11.5.sp, color = MinTextMute, maxLines = 2)
        }
        card.badge?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MinPrimary)
        }
    }
}
