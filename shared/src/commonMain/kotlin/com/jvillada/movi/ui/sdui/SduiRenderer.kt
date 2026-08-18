package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.renderableSections
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.CardRow
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.MonoText
import com.jvillada.movi.ui.components.Sparkline
import com.jvillada.movi.ui.components.assetsDebtsNet
import com.jvillada.movi.ui.components.cardDebt
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMillions
import com.jvillada.movi.ui.components.isDebtAccount

/**
 * Renders a server-provided [ScreenDefinition] for the dashboard. Consumes
 * `renderableSections(definition)` — unknown section types and invalid actions have
 * already been filtered/stripped there (Task 1), so this renderer never re-validates.
 *
 * Kept fully independent from `DashboardFallback` (no shared composables) per the
 * SDUI-movi brief: the fallback is the insurance policy and must not be touched.
 *
 * `isFamily` only affects the HERO_BALANCE sparkline tint — the "Patrimonio
 * familiar"/"Patrimonio" title branching from the old hardcoded Dashboard no longer
 * applies here: that section is now the server-titled LINK_LIST "Explora".
 */
@Composable
fun SduiRenderer(
    definition: ScreenDefinition,
    summary: FinanceSummary?,
    accounts: List<Account>,
    isFamily: Boolean,
    modifier: Modifier = Modifier,
    onNavigate: (Screen) -> Unit,
    onShowCreateSheet: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        val sections = renderableSections(definition)
        sections.forEachIndexed { index, section ->
            item {
                if (index > 0) Spacer(Modifier.height(20.dp))
                SduiSection(
                    section = section,
                    summary = summary,
                    accounts = accounts,
                    isFamily = isFamily,
                    onNavigate = onNavigate,
                    onShowCreateSheet = onShowCreateSheet,
                    uriHandler = uriHandler,
                )
            }
        }
    }
}

@Composable
private fun SduiSection(
    section: ScreenSection,
    summary: FinanceSummary?,
    accounts: List<Account>,
    isFamily: Boolean,
    onNavigate: (Screen) -> Unit,
    onShowCreateSheet: () -> Unit,
    uriHandler: UriHandler,
) {
    when (section.type) {
        "HERO_BALANCE" -> HeroBalanceSection(summary, accounts, isFamily)
        "ACCOUNTS_SUMMARY" -> AccountsSummarySection(accounts, onNavigate, onShowCreateSheet)
        "CARD_ROW" -> CardRowSection(section, onNavigate, uriHandler)
        "CARD_LIST" -> CardListSection(section, onNavigate, uriHandler)
        "LINK_LIST" -> LinkListSection(section, onNavigate, uriHandler)
        "BANNER" -> BannerSection(section, onNavigate, uriHandler)
    }
}

// ── Action dispatch ─────────────────────────────────────────────────────────────────

/**
 * NAVIGATE target → [Screen], covering all 15 whitelisted targets from
 * `ScreenTaxonomy.NAVIGATE_TARGETS`. `renderableSections` has already stripped any
 * NAVIGATE action whose target isn't in that whitelist, so the `else` branch below is
 * defensive only (unreachable in practice) — `target` is a plain String, not a sealed
 * type, so the `when` cannot be exhaustive at the compiler level.
 */
private fun screenForTarget(target: String): Screen? = when (target) {
    "dashboard" -> Screen.Dashboard
    "transactions" -> Screen.Transactions
    "quickadd" -> Screen.QuickAdd
    "budgets" -> Screen.Budgets
    "mas" -> Screen.Mas
    "accounts" -> Screen.Accounts
    "credits" -> Screen.Credits
    "goals" -> Screen.Goals
    "investments" -> Screen.Investments
    "subscriptions" -> Screen.Subscriptions
    "recurrentes" -> Screen.Recurrentes
    "analisis" -> Screen.Analisis
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

// ── HERO_BALANCE — independent re-implementation of the current Balance card, driven
//    by `summary`/`accounts` directly instead of full DashboardScreen state (kept out
//    of DashboardScreen.kt on purpose so DashboardFallback's Hero card stays untouched).

@Composable
private fun HeroBalanceSection(summary: FinanceSummary?, accounts: List<Account>, isFamily: Boolean) {
    val (_, _, totalBalance) = assetsDebtsNet(accounts)
    val ingresos = summary?.ingresos ?: 0L
    val egresos = summary?.egresos ?: 0L
    val flujo = ingresos - egresos

    MinCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(22.dp),
    ) {
        Text(text = "Balance", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MinTextMute)
        Spacer(Modifier.height(10.dp))
        Text(
            text = formatCOP(totalBalance), // formatCOP ya trae el signo (F36) — no duplicarlo acá
            fontSize = 44.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            color = if (totalBalance < 0) MinExpense else MinText,
            letterSpacing = (-1.6).sp,
            lineHeight = 44.sp,
        )
        Spacer(Modifier.height(18.dp))
        Sparkline(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            family = isFamily,
            hasData = totalBalance != 0L || ingresos != 0L || egresos != 0L,
        )
        Spacer(Modifier.height(20.dp))
        Hairline()
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                Pair("Ingresos", formatMillions(ingresos)),
                Pair("Egresos", formatMillions(egresos)),
                Pair("Flujo", formatMillions(flujo)),
            ).forEach { (label, value) ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(value, fontSize = 14.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                }
            }
        }
    }
}

// ── ACCOUNTS_SUMMARY — independent re-implementation of the current "Mis cuentas"
//    section, driven by `accounts` directly. The empty-state CTA opens `CreateAccountSheet`
//    (via `onShowCreateSheet`, threaded in from DashboardScreen) — same behavior as
//    `DashboardFallback`'s empty state, so the two paths stay in parity.

@Composable
private fun AccountsSummarySection(
    accounts: List<Account>,
    onNavigate: (Screen) -> Unit,
    onShowCreateSheet: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MinSectionHeader(
            title = "Mis cuentas",
            count = if (accounts.isNotEmpty()) accounts.size else null,
            action = if (accounts.isNotEmpty()) "Ver todas +" else null,
            onAction = if (accounts.isNotEmpty()) { { onNavigate(Screen.Accounts) } } else null,
        )
        if (accounts.isEmpty()) {
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(18.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Sin cuentas aún", fontSize = 14.sp, color = MinTextMute)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MinPrimaryContainer)
                            .clickable { onShowCreateSheet() },
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text("+ Crear primera cuenta", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinOnPrimaryContainer)
                    }
                }
            }
        } else {
            val typeLabel: (AccountType) -> String = { type ->
                when (type) {
                    AccountType.CASH -> "Efectivo"
                    AccountType.SAVINGS -> "Ahorros"
                    AccountType.CHECKING -> "Corriente"
                    AccountType.INVESTMENT -> "Inversión"
                    AccountType.CREDIT_CARD -> "Crédito"
                    AccountType.LOAN -> "Préstamo"
                }
            }
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
            ) {
                accounts.take(3).forEachIndexed { i, account ->
                    CardRow(
                        left = { Text(account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                        sub = typeLabel(account.type),
                        right = {
                            if (isDebtAccount(account.type)) {
                                val (debt, isEstimate) = cardDebt(account)
                                MonoText(
                                    // debt < 0 es saldo a favor: signo invertido a propósito, así que
                                    // se le pasa el valor absoluto — si no, formatCOP (F36) le pondría
                                    // su propio "−" encima del "+" de acá.
                                    "${if (debt < 0) "+" else "−"}${if (isEstimate) "≈" else ""}${formatCOP(kotlin.math.abs(debt))}",
                                    14.5f,
                                    color = if (debt < 0) MinIncome else MinExpense,
                                )
                            } else {
                                MonoText(formatCOP(account.balance), 14.5f)
                            }
                        },
                        isLast = i == minOf(accounts.size, 3) - 1,
                        onClick = { onNavigate(Screen.Accounts) },
                    )
                }
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
 * CRITICAL: `ScreenSection` has no `action` field. The seed (Task 2) puts the AI
 * banner's NAVIGATE action in `cards[0].action` (see ScreenSeed.kt), so a BANNER is
 * clickable exactly when it has a card carrying an action — read from there, not from
 * any section-level field.
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
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
