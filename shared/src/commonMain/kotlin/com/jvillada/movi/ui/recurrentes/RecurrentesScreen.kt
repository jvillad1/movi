package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

// Amber warning color reused from the existing MinWarn palette entry.
private val MinAmber = MinWarn

@Composable
fun RecurrentesScreen(onNavigate: (Screen) -> Unit) {
    var rules by remember { mutableStateOf<List<RecurringRule>>(emptyList()) }
    var upcoming by remember { mutableStateOf<List<UpcomingPayment>>(emptyList()) }
    // loadKey increments after every create/update/delete to trigger a reload.
    var loadKey by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    // Sheet state: null = closed; non-null = open with optional prefilled rule (edit) or null (create)
    var sheetRule by remember { mutableStateOf<RecurringRule?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    // Estado del opt-in de push, para el aviso de "tus recordatorios no te van a llegar".
    var pushStatus by remember { mutableStateOf(PushOptIn.status()) }
    var pushRefreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(loadKey) {
        loading = true
        runCatching { Repositories.wallets.getRecurringRules() }.onSuccess { rules = it }
        runCatching { Repositories.wallets.getUpcomingPayments() }.onSuccess { upcoming = it }
        loading = false
    }

    LaunchedEffect(pushRefreshTick) {
        // El flujo de permisos del navegador es async (moviPush.js): refrescar unas
        // veces tras cada acción para que el aviso desaparezca sin necesidad de reabrir la app.
        repeat(20) {
            kotlinx.coroutines.delay(600)
            pushStatus = PushOptIn.status()
        }
    }

    val ingresosFijos = rules.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val egresosFijos  = rules.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val flujoLibre    = ingresosFijos - egresosFijos

    // Rules sorted by dayOfMonth for the "Por día" list.
    val ordered = remember(rules) { rules.sortedBy { it.dayOfMonth } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "‹",
                    fontSize = 22.sp,
                    color = MinText,
                    modifier = Modifier.clickable { onNavigate(Screen.Analisis) },
                )
                Text(
                    text = "Recurrentes",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+",
                    fontSize = 22.sp,
                    color = MinPrimary,
                    modifier = Modifier.clickable {
                        sheetRule = null
                        sheetOpen = true
                    },
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                // ── Flujo libre card ────────────────────────────────────────────
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text("Flujo libre", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = formatCOP(flujoLibre),
                            fontSize = 36.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MinText,
                            letterSpacing = (-1.4).sp,
                            lineHeight = 36.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Ingresos fijos − egresos fijos",
                            fontSize = 12.sp,
                            color = MinTextMute,
                        )
                        Spacer(Modifier.height(18.dp))
                        Hairline()
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ingresos fijos", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = formatCOP(ingresosFijos),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = MinIncome,
                                    letterSpacing = (-0.3).sp,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Egresos fijos", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = formatCOP(egresosFijos),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = MinText,
                                    letterSpacing = (-0.3).sp,
                                )
                            }
                        }
                    }
                }

                // ── Aviso: recordatorios sin canal de entrega ───────────────────
                if (shouldShowReminderWarning(pushStatus, upcoming.isNotEmpty())) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            ReminderWarningBanner(
                                pushStatus = pushStatus,
                                onEnable = {
                                    PushOptIn.enable()
                                    pushRefreshTick++
                                },
                            )
                        }
                    }
                }

                // ── Próximos pagos section ──────────────────────────────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(
                            title = "Próximos pagos",
                            count = if (upcoming.isNotEmpty()) upcoming.size else null,
                        )
                        if (upcoming.isEmpty() && !loading) {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                Text("Sin pagos próximos", fontSize = 14.sp, color = MinTextMute)
                            }
                        } else {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            ) {
                                upcoming.forEachIndexed { i, payment ->
                                    UpcomingPaymentRow(
                                        payment = payment,
                                        onClick = {
                                            if (payment.rule.id.startsWith(CREDIT_RULE_PREFIX)) {
                                                onNavigate(Screen.Credits)
                                            } else {
                                                sheetRule = payment.rule
                                                sheetOpen = true
                                            }
                                        },
                                    )
                                    if (i < upcoming.size - 1) Hairline()
                                }
                            }
                        }
                    }
                }

                // ── Por día del mes section ─────────────────────────────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Por día del mes", count = if (rules.isNotEmpty()) rules.size else null)
                        if (rules.isEmpty() && !loading) {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                Text("Sin reglas recurrentes", fontSize = 14.sp, color = MinTextMute)
                            }
                        } else {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            ) {
                                ordered.forEachIndexed { i, r ->
                                    val isIncome = r.type == TransactionType.INCOME
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                sheetRule = r
                                                sheetOpen = true
                                            }
                                            .padding(vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MinSurfaceContainerHigh),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "${r.dayOfMonth}",
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium,
                                                color = MinText,
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = r.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MinText,
                                                letterSpacing = (-0.1).sp,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(r.category, fontSize = 12.sp, color = MinTextMute)
                                        }
                                        Text(
                                            text = "${if (isIncome) "+" else "−"}${formatCOP(r.amount)}",
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isIncome) MinIncome else MinText,
                                            letterSpacing = (-0.3).sp,
                                        )
                                    }
                                    if (i < ordered.size - 1) Hairline()
                                }
                            }
                        }
                    }
                }
            }

            MinBottomNav(active = NavTab.MORE) { tab ->
                when (tab) {
                    NavTab.HOME         -> onNavigate(Screen.Dashboard)
                    NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                    NavTab.ADD          -> onNavigate(Screen.QuickAdd)
                    NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                    NavTab.MORE         -> onNavigate(Screen.Mas)
                }
            }
        }

        // Bottom sheet overlay
        if (sheetOpen) {
            CreateRecurringRuleSheet(
                onDismiss = { sheetOpen = false },
                onSaved = {
                    sheetOpen = false
                    loadKey++
                },
                existing = sheetRule,
            )
        }
    }
}

// ── Status badge helpers ──────────────────────────────────────────────────────

private fun dueDateDay(dueDate: String): Int =
    runCatching { LocalDate.parse(dueDate).dayOfMonth }.getOrElse {
        dueDate.takeLast(2).toIntOrNull()
            ?: dueDate.substringAfterLast('-').toIntOrNull()
            ?: 0
    }

private fun statusColor(status: PaymentStatus): Color = when (status) {
    PaymentStatus.OVERDUE   -> MinExpense
    PaymentStatus.DUE_TODAY -> MinAmber
    PaymentStatus.DUE_SOON  -> MinAmber
    PaymentStatus.UPCOMING  -> MinTextMute
}

private fun statusText(payment: UpcomingPayment): String {
    val n = payment.daysUntil
    val day = dueDateDay(payment.dueDate)
    return when (payment.status) {
        PaymentStatus.OVERDUE   -> "Vencido hace ${-n} ${if (-n == 1) "día" else "días"}"
        PaymentStatus.DUE_TODAY -> "Vence hoy"
        PaymentStatus.DUE_SOON  -> "Vence el $day · en $n ${if (n == 1) "día" else "días"}"
        PaymentStatus.UPCOMING  -> "Vence el $day · en $n ${if (n == 1) "día" else "días"}"
    }
}

@Composable
private fun UpcomingPaymentRow(payment: UpcomingPayment, onClick: () -> Unit) {
    val rule = payment.rule
    val isIncome = rule.type == TransactionType.INCOME
    val color = statusColor(payment.status)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(2.dp))
            // Status pill badge + category
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Status label (colored)
                Text(
                    text = statusText(payment),
                    fontSize = 11.sp,
                    color = color,
                )
                // Separator dot
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(MinTextFaint),
                )
                Text(rule.category, fontSize = 11.sp, color = MinTextMute)
            }
        }

        // Amount
        Text(
            text = "${if (isIncome) "+" else "−"}${formatCOP(rule.amount)}",
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = if (isIncome) MinIncome else MinText,
            letterSpacing = (-0.3).sp,
        )
    }
}

// ── Aviso de recordatorios sin canal de entrega ────────────────────────────────

@Composable
private fun ReminderWarningBanner(pushStatus: String, onEnable: () -> Unit) {
    val denied = pushStatus == "denied"
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MinAmber),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tus recordatorios no te van a llegar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    letterSpacing = (-0.1).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (denied) {
                        "Bloqueaste las notificaciones de Movi en el navegador, así que no podemos avisarte de estos pagos. Reactívalas desde la configuración del sitio en tu navegador."
                    } else {
                        "Tienes pagos próximos, pero las notificaciones están apagadas y no hay otro canal activo para avisarte. Actívalas para no perderte un vencimiento."
                    },
                    fontSize = 12.5.sp,
                    color = MinTextDim,
                    lineHeight = 17.sp,
                )
                if (!denied) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Activar notificaciones",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinPrimary,
                        modifier = Modifier.clickable(onClick = onEnable),
                    )
                }
            }
        }
    }
}

