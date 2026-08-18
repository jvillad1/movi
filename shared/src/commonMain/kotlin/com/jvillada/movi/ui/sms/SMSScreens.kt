package com.jvillada.movi.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.platform.rememberSmsSync
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
fun SMSInboxScreen(onNavigate: (Screen) -> Unit) {
    var smsItems by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var syncWorking by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    val coroutine = rememberCoroutineScope()

    val smsSync = rememberSmsSync { messages ->
        coroutine.launch {
            syncWorking = true
            syncError = null
            runCatching { Repositories.wallets.syncSms(messages) }
                .onSuccess { refreshKey++ }
                .onFailure { syncError = it.toUserMessage() }
            syncWorking = false
        }
    }

    LaunchedEffect(refreshKey) {
        runCatching { Repositories.wallets.getSmsMessages() }
            .onSuccess { smsItems = it }
    }
    val pendingCount = smsItems.count { it.state == "pending" }
    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("‹", fontSize = 22.sp, color = MinText, modifier = Modifier.clickableSimple { onNavigate(Screen.Dashboard) })
            Column(modifier = Modifier.weight(1f)) {
                Text("Mensajes del banco", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                Text("$pendingCount por confirmar", fontSize = 12.sp, color = MinTextMute)
            }
            Text(
                "↻",
                fontSize = 18.sp,
                color = MinTextDim,
                modifier = Modifier.clickableSimple { refreshKey++ },
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
        ) {
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(18.dp),
                ) {
                    // La lectura automática solo existe en Android (permiso READ_SMS + bandeja
                    // del sistema). En web/iOS mostrar "AUTO-LECTURA ACTIVA" sería mentir: acá
                    // no hay ninguna lectura pasando, solo la revisión de lo que el teléfono ya
                    // subió.
                    if (isAndroid) {
                        Text("AUTO-LECTURA ACTIVA", fontSize = 11.sp, color = MinTextMute, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Movi lee tus SMS bancarios automáticamente. Revisa los pendientes para confirmar comercios o categoría.",
                            fontSize = 13.5.sp,
                            color = MinText,
                            lineHeight = 19.sp,
                        )
                    } else {
                        Text(
                            "Los mensajes del banco los lee tu teléfono con Movi instalado. Aquí los revisas antes de que cuenten.",
                            fontSize = 13.5.sp,
                            color = MinText,
                            lineHeight = 19.sp,
                        )
                    }
                }
                if (smsSync.available) {
                    Spacer(Modifier.height(10.dp))
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Default,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        onClick = if (!syncWorking) smsSync.requestAndRead else null,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (syncWorking) "Sincronizando…" else "Sincronizar SMS del teléfono",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (syncWorking) MinTextMute else MinText,
                                    letterSpacing = (-0.1).sp,
                                )
                                Text(
                                    "Últimos 30 días · solo lectura",
                                    fontSize = 11.5.sp,
                                    color = MinTextMute,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Text(
                                if (syncWorking) "…" else "↑",
                                fontSize = 18.sp,
                                color = if (syncWorking) MinTextFaint else MinPrimary,
                            )
                        }
                        if (syncError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(syncError!!, fontSize = 11.5.sp, color = MinExpense)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Bandeja", count = smsItems.size)
            }

            smsItems.forEach { sms ->
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(sms.bank, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinText)
                            StatusDot(MinTextFaint, 2.dp)
                            Text(sms.time, fontSize = 11.5.sp, color = MinTextMute)
                            Spacer(Modifier.weight(1f))
                            val (label, color) = when (sms.state) {
                                "pending" -> "PENDIENTE" to MinWarn
                                "auto" -> "AUTO" to MinIncome
                                "ignored" -> "IGNORADO" to MinTextMute
                                else -> sms.state.uppercase() to MinTextMute
                            }
                            Text(label, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = color, letterSpacing = 0.4.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MinHairline))
                            Text(sms.text, fontSize = 12.sp, color = MinTextDim, fontFamily = FontFamily.Monospace, lineHeight = 17.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Hairline()
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sms.det, fontSize = 13.sp, color = MinText, letterSpacing = (-0.1).sp, modifier = Modifier.weight(1f))
                            if (sms.state == "pending") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .border(1.dp, MinBorderStrong, RoundedCornerShape(999.dp))
                                        .clickable { onNavigate(Screen.SMSReconcile(sms.id)) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("Revisar", fontSize = 12.5.sp, color = MinText, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SMSReconcileScreen(onNavigate: (Screen) -> Unit, smsId: String) {
    val coroutine = rememberCoroutineScope()
    var sms by remember { mutableStateOf<SmsMessage?>(null) }
    var parsed by remember { mutableStateOf<ParsedSms?>(null) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(smsId) {
        runCatching { Repositories.wallets.getSms(smsId) }.onSuccess { sms = it }
            .onFailure { error = "No pude cargar el SMS" }
        runCatching { Repositories.wallets.parseSms(smsId) }
            .onSuccess { parsed = it; selectedCategory = it.category }
            .onFailure { error = "No pude parsear el SMS" }
        runCatching { Repositories.wallets.getAccounts() }.onSuccess { accounts = it }
    }

    val currentSms = sms
    val resolvedAccount = accounts.firstOrNull { currentSms != null && it.name.contains(currentSms.bank, ignoreCase = true) }
        ?: accounts.firstOrNull { it.type != AccountType.CASH }
        ?: accounts.firstOrNull()

    val categoryOptions: List<String> = run {
        val base = parsed?.category
        val alts = if (parsed?.type == TransactionType.INCOME) {
            listOf("Nómina", "Transferencia", "Reembolso")
        } else {
            listOf("Restaurantes", "Mercado", "Transporte", "Salud", "Suscripción", "Servicios", "Hogar", "Otro")
        }
        (listOfNotNull(base) + alts).distinct().take(4)
    }

    fun confirm() {
        val cat = selectedCategory ?: parsed?.category ?: return
        val acct = resolvedAccount ?: return
        val p = parsed ?: return
        working = true
        error = null
        coroutine.launch {
            runCatching {
                val event = FinancialEvent(
                    // Mismo motivo que en QuickAddScreen — ver newId().
                    id = newId("ev"),
                    accountId = acct.id,
                    type = p.type,
                    amount = p.amount.toLong(),
                    category = cat,
                    description = p.merchant,
                    merchant = p.merchant,
                    source = EventSource.SMS,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
                Repositories.wallets.postEvent(event)
                Repositories.wallets.confirmSms(smsId)
            }.onSuccess {
                working = false
                onNavigate(Screen.SMSInbox)
            }.onFailure {
                working = false
                error = it.toUserMessage()
            }
        }
    }

    fun ignore() {
        working = true
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.ignoreSms(smsId) }
            working = false
            result.onSuccess { onNavigate(Screen.SMSInbox) }
                .onFailure { error = it.toUserMessage() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("‹", fontSize = 22.sp, color = MinText, modifier = Modifier.clickableSimple { onNavigate(Screen.SMSInbox) })
            Text("Reconciliar movimiento", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            item {
                MinSectionHeader(title = "SMS recibido")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Default,
                    padding = PaddingValues(18.dp),
                ) {
                    if (sms == null) {
                        Text("Cargando…", fontSize = 13.sp, color = MinTextMute)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(sms!!.bank, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinText)
                            StatusDot(MinTextFaint, 2.dp)
                            Text("SMS", fontSize = 11.sp, color = MinTextMute, fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp)
                            StatusDot(MinTextFaint, 2.dp)
                            Text(sms!!.time, fontSize = 11.sp, color = MinTextMute)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MinHairline))
                            Text(
                                "\"${sms!!.text}\"",
                                fontSize = 13.sp,
                                color = MinTextDim,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Movi sugiere")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(20.dp),
                ) {
                    if (parsed == null) {
                        Text("Parseando…", fontSize = 13.sp, color = MinTextMute)
                    } else {
                        val p = parsed!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.merchant, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.2).sp)
                                Text(
                                    "${selectedCategory ?: p.category} · ${resolvedAccount?.name ?: "Sin cuenta"}",
                                    fontSize = 12.sp,
                                    color = MinTextMute,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            val sign = if (p.type == TransactionType.EXPENSE) "−" else "+"
                            val color = if (p.type == TransactionType.EXPENSE) MinExpense else MinIncome
                            Text(
                                "$sign\$${formatThousands(p.amount.toLong())}",
                                fontSize = 17.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = color,
                                letterSpacing = (-0.4).sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Confirma o ajusta")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    Detail(
                        ok = parsed != null,
                        label = "Comercio",
                        value = parsed?.merchant ?: "—",
                    )
                    Hairline()
                    Detail(
                        ok = resolvedAccount != null,
                        label = "Cuenta",
                        value = resolvedAccount?.name ?: "Sin cuenta",
                    )
                    Hairline()
                    Detail(
                        ok = selectedCategory != null,
                        label = "Categoría",
                        value = selectedCategory ?: "—",
                        isLast = true,
                    )
                }

                if (categoryOptions.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categoryOptions.forEach { opt ->
                            val on = opt == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (on) MinText else Color.Transparent)
                                    .then(if (!on) Modifier.border(1.dp, MinBorderStrong, RoundedCornerShape(999.dp)) else Modifier)
                                    .clickable { selectedCategory = opt }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(opt, fontSize = 12.5.sp, color = if (on) MinBg else MinText, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error!!, fontSize = 12.sp, color = MinExpense)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MinBorderStrong, RoundedCornerShape(14.dp))
                    .clickable(enabled = !working) { ignore() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Ignorar", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText)
            }
            val canConfirm = parsed != null && resolvedAccount != null && !working
            Box(
                modifier = Modifier
                    .weight(1.7f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canConfirm) MinText else MinSurfaceContainerLow)
                    .clickable(enabled = canConfirm) { confirm() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (working) "Guardando…" else "Confirmar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canConfirm) MinBg else MinTextFaint,
                )
            }
        }
    }
}

@Composable
private fun Detail(ok: Boolean, label: String, value: String, isLast: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (ok) MinIncome.copy(alpha = 0.16f) else MinWarn.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (ok) "✓" else "?", fontSize = 10.sp, color = if (ok) MinIncome else MinWarn, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(), fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
            Text(value, fontSize = 13.5.sp, color = MinText, letterSpacing = (-0.1).sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun formatThousands(amount: Long): String {
    val digits = amount.toString()
    val sb = StringBuilder()
    for ((i, c) in digits.reversed().withIndex()) {
        if (i > 0 && i % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.reverse().toString()
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick),
)
