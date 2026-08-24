package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

// Amber warning color reused from the existing MinWarn palette entry.
private val MinAmber = MinWarn

/**
 * Todo lo que se repite mes a mes, en una sola pantalla (Ola 8).
 *
 * Suscripciones dejó de ser una pantalla hermana: una suscripción ES un recurrente —el mismo
 * cobro, el mismo día del mes— y tenerlas separadas obligaba al dueño a saber en cuál de las
 * dos anotar Netflix. Ahora hay una sola lista, con dos grupos que sí significan algo para él:
 *
 * 1. **Por día del mes** — lo confirmado: sus reglas recurrentes MÁS las suscripciones activas.
 *    Las que salieron del detector llevan la marca «la encontró Movi».
 * 2. **Detectadas · por confirmar** — lo que el detector propuso y nadie aceptó todavía, aparte
 *    y con Confirmar / No es. Nunca mezclado con lo confirmado.
 *
 * Es el mismo movimiento que la Ola 7 hizo con Inversiones dentro de Cuentas (F61).
 */
@Composable
fun RecurrentesScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<RecurringRule>>(emptyList()) }
    var upcoming by remember { mutableStateOf<List<UpcomingPayment>>(emptyList()) }
    var subs by remember { mutableStateOf(SubscriptionsResult(emptyList(), 0)) }
    var scanning by remember { mutableStateOf(false) }
    var subsError by remember { mutableStateOf<String?>(null) }
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
        // F35: de paso, alimenta el caché de "categorías ya usadas" que lee CategoryField —
        // esta pantalla ya carga las reglas, no hace falta un fetch nuevo.
        runCatching { Repositories.wallets.getRecurringRules() }.onSuccess {
            rules = it
            UsedCategoriesCache.record(it.map { r -> r.category })
        }
        runCatching { Repositories.wallets.getUpcomingPayments() }.onSuccess { upcoming = it }
        runCatching { Repositories.wallets.getSubscriptions() }
            .onSuccess { subs = it; subsError = null }
            .onFailure { subsError = it.toUserMessage() }
        loading = false
    }

    // Volver a barrer los movimientos buscando cobros que se repiten. El detector deja fuera la
    // categoría «Traspaso» (SubscriptionDetector.kt) y respeta lo ya descartado: lo que el dueño
    // dijo que no era, no vuelve a proponerse.
    fun rescan() {
        if (scanning) return
        scanning = true
        subsError = null
        coroutine.launch {
            runCatching { Repositories.wallets.detectSubscriptions() }
                .onSuccess { subs = it }
                .onFailure { subsError = it.toUserMessage() }
            scanning = false
        }
    }

    fun setStatus(sub: Subscription, status: SubStatus) {
        coroutine.launch {
            runCatching { Repositories.wallets.updateSubscription(sub.id, sub.copy(status = status)) }
                .onSuccess { loadKey++ }
                .onFailure { subsError = it.toUserMessage() }
        }
    }

    if (PushOptIn.supported) {
        LaunchedEffect(pushRefreshTick) {
            // El flujo de permisos del navegador es async (moviPush.js): refrescar unas
            // veces tras cada acción para que el aviso desaparezca sin necesidad de reabrir la app.
            // Solo donde el push existe: en Android/iOS status() es una constante y esto
            // sería un bucle inútil (mismo gate que usa PerfilScreen).
            repeat(20) {
                kotlinx.coroutines.delay(600)
                pushStatus = PushOptIn.status()
            }
        }
    }

    val candidatas = subs.subscriptions.filter { it.status == SubStatus.CANDIDATE }
    val suscripciones = subs.subscriptions.filter {
        it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED
    }
    // ¿Hay algo cobrado en otra moneda? De eso depende la nota al pie del total (ver abajo).
    val hayMonedaExtranjera = suscripciones.any { it.currency != "COP" }

    val ingresosRecurrentes = rules.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    // El total NO suma peras con manzanas: `RecurringRule.amount` es COP puro (el modelo no
    // tiene moneda) y `subs.monthlyTotalCop` ya viene convertido a COP por el server con la TRM
    // del día (ver `resultFor` en SubscriptionRoutes.kt) — nunca se suman los `amount` crudos de
    // las suscripciones, que están en su moneda nativa. Cada fila de la lista sí muestra su
    // moneda tal cual (formatMoney), así que el dueño ve el dólar donde el dólar está.
    val gastosRecurrentes   = rules.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } +
        subs.monthlyTotalCop
    val flujoLibre          = ingresosRecurrentes - gastosRecurrentes

    // La lista única, ordenada por día del mes: reglas y suscripciones activas, mezcladas por
    // fecha porque para el dueño son lo mismo — algo que le cobran (o le entra) tal día.
    val ordered: List<Recurrente> = remember(rules, suscripciones) {
        (rules.map { Recurrente.Regla(it) } + suscripciones.map { Recurrente.Suscripcion(it) })
            .sortedBy { it.dayOfMonth }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
            // F60: encabezado único; con reglas ya creadas el alta pasa a botón compacto a la
            // derecha (F18), vacío va a todo el ancho debajo. Recurrentes ya es pestaña propia:
            // el leading lo decide el layout (avatar en el rail, flecha en el teléfono, donde se
            // sigue llegando por Más).
            MinScreenHeader(
                title = "Recurrentes",
                leading = leadingFor(Screen.Recurrentes, onProfile = { onNavigate(Screen.Profile) }, fallback = Screen.Mas),
                action = {
                    // Ola 8: el alta es UNA sola («Nuevo recurrente», ver CreateRecurringRuleSheet)
                    // y el re-escaneo se mudó del encabezado de Suscripciones a este, que es el
                    // único que queda.
                    if (ordered.isNotEmpty()) {
                        NewItemButton(label = "Nuevo recurrente", onClick = { sheetRule = null; sheetOpen = true })
                    }
                    Text(
                        text = if (scanning) "Buscando…" else "Buscar cobros",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (scanning) MinTextMute else MinText,
                        modifier = Modifier.clickable(enabled = !scanning) { rescan() },
                    )
                },
            )
            if (ordered.isEmpty() && !loading) {
                NewItemButton(
                    label = "Nuevo recurrente",
                    onClick = { sheetRule = null; sheetOpen = true },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(vertical = 14.dp),
                    full = true,
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
                            text = "Ingresos recurrentes − Gastos recurrentes",
                            fontSize = 12.sp,
                            color = MinTextMute,
                        )
                        Spacer(Modifier.height(18.dp))
                        Hairline()
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ingresos recurrentes", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = formatCOP(ingresosRecurrentes),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = MinIncome,
                                    letterSpacing = (-0.3).sp,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gastos recurrentes", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = formatCOP(gastosRecurrentes),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = MinText,
                                    letterSpacing = (-0.3).sp,
                                )
                            }
                        }
                        // Solo cuando de verdad hay algo en otra moneda: si todo está en pesos,
                        // la nota sobraría y ensuciaría la tarjeta.
                        if (hayMonedaExtranjera) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Lo que te cobran en dólares entra al total convertido a " +
                                    "pesos con la tasa del día.",
                                fontSize = 11.sp,
                                color = MinTextMute,
                                lineHeight = 15.sp,
                            )
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

                // ── Error del lado de las suscripciones ─────────────────────────
                subsError?.let { msg ->
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text(msg, fontSize = 12.sp, color = MinExpense, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }

                // ── Detectadas · por confirmar ──────────────────────────────────
                // F39: nada nace activo. Lo que el detector encuentra cae acá primero, en su
                // propio grupo — NUNCA mezclado con lo confirmado— y el dueño lo acepta o lo
                // descarta de a uno. Lo descartado no se vuelve a proponer (SubscriptionSync
                // respeta DISMISSED en cada barrido).
                if (candidatas.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Detectadas · por confirmar", count = candidatas.size)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                candidatas.forEach { s ->
                                    MinCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        variant = MinCardVariant.Elevated,
                                        padding = PaddingValues(18.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(s.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                            // Cada fila en SU moneda — el dólar se muestra como dólar.
                                            Text(
                                                text = formatMoney(s.amount, s.currency),
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium,
                                                color = MinText,
                                            )
                                        }
                                        Text(
                                            text = "Visto ${s.occurrences} ${if (s.occurrences == 1) "mes" else "meses"} · día ${s.dayOfMonth}",
                                            fontSize = 12.sp,
                                            color = MinTextMute,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ActionChip("Confirmar", primary = true) { setStatus(s, SubStatus.CONFIRMED) }
                                            ActionChip("No es", primary = false) { setStatus(s, SubStatus.DISMISSED) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Próximos section ────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(
                            title = "Próximos",
                            count = if (upcoming.isNotEmpty()) upcoming.size else null,
                        )
                        if (upcoming.isEmpty() && !loading) {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                Text("Sin recurrentes aún", fontSize = 14.sp, color = MinTextMute)
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
                                            // F20: el pago de una tarjeta (card_) no es una regla
                                            // editable acá — se gestiona en Créditos, como las cuotas.
                                            if (payment.rule.id.startsWith(CREDIT_RULE_PREFIX) ||
                                                payment.rule.id.startsWith(CARD_RULE_PREFIX)
                                            ) {
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
                        MinSectionHeader(title = "Por día del mes", count = if (ordered.isNotEmpty()) ordered.size else null)
                        if (ordered.isEmpty() && !loading) {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                Text("Sin recurrentes aún", fontSize = 14.sp, color = MinTextMute)
                            }
                        } else {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            ) {
                                ordered.forEachIndexed { i, item ->
                                    RecurrenteRow(
                                        item = item,
                                        onEditRule = { sheetRule = it; sheetOpen = true },
                                        onRemoveSub = { setStatus(it, SubStatus.DISMISSED) },
                                    )
                                    if (i < ordered.size - 1) Hairline()
                                }
                            }
                        }
                    }
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

// ── La lista única ────────────────────────────────────────────────────────────

/**
 * Una fila de «Por día del mes». Las dos formas que puede tener algo que se repite: una regla
 * que escribió el dueño y una suscripción (detectada o dada de alta en dólares). Se unifican
 * en la UI y NO en el modelo a propósito — son dos tablas, dos endpoints y dos ciclos de vida
 * distintos del lado del server; lo único que comparten es que caen tal día del mes, y eso es
 * justo lo que esta lista ordena.
 */
private sealed class Recurrente {
    abstract val dayOfMonth: Int

    data class Regla(val rule: RecurringRule) : Recurrente() {
        override val dayOfMonth get() = rule.dayOfMonth
    }

    data class Suscripcion(val sub: Subscription) : Recurrente() {
        override val dayOfMonth get() = sub.dayOfMonth
        /**
         * ¿La encontró Movi sola? [SubStatus.CONFIRMED] no alcanza para saberlo: cubre por
         * igual «la detectó y la confirmé» y «la escribí yo». La señal de origen es la clave
         * del comercio — ver [MANUAL_SUB_PREFIX].
         */
        val laEncontroMovi get() = !sub.merchantKey.startsWith(MANUAL_SUB_PREFIX)
    }
}

@Composable
private fun RecurrenteRow(
    item: Recurrente,
    onEditRule: (RecurringRule) -> Unit,
    onRemoveSub: (Subscription) -> Unit,
) {
    // Misma anatomía para las dos formas: día en un círculo, nombre + una línea de contexto, y
    // el monto a la derecha. Lo que cambia es el contexto y qué pasa al tocar.
    val nombre: String
    val contexto: String
    val monto: String
    val esIngreso: Boolean
    val onClick: (() -> Unit)?
    when (item) {
        is Recurrente.Regla -> {
            nombre = item.rule.name
            contexto = item.rule.category
            esIngreso = item.rule.type == TransactionType.INCOME
            monto = "${if (esIngreso) "+" else "−"}${formatCOP(item.rule.amount)}"
            onClick = { onEditRule(item.rule) }
        }
        is Recurrente.Suscripcion -> {
            nombre = item.sub.displayName
            // La marca discreta que pidió el dueño: que se note cuáles no anotó él.
            contexto = if (item.laEncontroMovi) "Suscripción · la encontró Movi" else "Suscripción"
            esIngreso = false
            // En SU moneda, sin convertir: una suscripción en dólares se lee "−US$12". Solo el
            // total de arriba pasa por la TRM, y lo dice.
            monto = "−" + formatMoney(item.sub.amount, item.sub.currency)
            // Una suscripción no tiene hoja de edición (el detector es su dueño); lo único que
            // se puede hacer con ella es quitarla, y para eso está el enlace de la derecha.
            onClick = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
                text = "${item.dayOfMonth}",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MinText,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nombre,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(contexto, fontSize = 12.sp, color = MinTextMute)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = monto,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = if (esIngreso) MinIncome else MinText,
                letterSpacing = (-0.3).sp,
            )
            if (item is Recurrente.Suscripcion) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Quitar",
                    fontSize = 12.sp,
                    color = MinExpense,
                    modifier = Modifier.clickable { onRemoveSub(item.sub) },
                )
            }
        }
    }
}

/** Los botones de «Confirmar» / «No es» del grupo de detectadas. */
@Composable
private fun ActionChip(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) MinText else MinSurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (primary) MinBg else MinText)
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


