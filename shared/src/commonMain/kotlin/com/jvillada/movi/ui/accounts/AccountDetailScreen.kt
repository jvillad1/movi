package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.accountDayTotal
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.homeScreenFor
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun AccountDetailScreen(onNavigate: (Screen) -> Unit, accountId: String, group: AccountGroup) {
    val goBack = LocalGoBack.current
    var account by remember { mutableStateOf<Account?>(null) }
    var days by remember { mutableStateOf<List<EventDay>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var selectedEvent by remember { mutableStateOf<FinancialEvent?>(null) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        try {
            val acc = Repositories.wallets.getAccount(accountId)
            val events = Repositories.wallets.getEvents(accountId)
            val grouped = events
                .groupBy { epochToDate(it.timestamp) }
                .map { (date, items) ->
                    EventDay(
                        date  = date,
                        // accountDayTotal, no countsAsCashFlow (Hallazgo bloqueante 1 de la
                        // revisión de esta rama). Movimientos/`by-day` filtran por
                        // countsAsCashFlow porque agregan VARIAS cuentas para responder "¿cuánta
                        // plata entró o salió del bolsillo?" — acá ya se sabe de qué cuenta se
                        // trata (es esta pantalla), así que esa pregunta no aplica. Lo que no
                        // miente es "¿cuánto se movió el saldo de esta cuenta hoy?", con SU
                        // convención: en LOAN/CREDIT_CARD un INCOME (abono) resta. Con
                        // countsAsCashFlow el total habría dado $0 todos los días en una cuenta
                        // LOAN (la regla es "LOAN nunca es flujo de caja") — incluso el día de un
                        // ajuste de $60.000.000 — y en CREDIT_CARD el abono habría desaparecido
                        // del total aunque sí mueve la deuda. Ver el KDoc de accountDayTotal.
                        total = accountDayTotal(acc.type, items),
                        items = items.sortedByDescending { it.timestamp },
                    )
                }
                .sortedByDescending { it.date }
            account = acc
            days = grouped
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.toUserMessage()
        }
        loading = false
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    val totalEvents = days.sumOf { it.items.size }

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            val accountTypePair = account?.let { accountTypeIcon(it.type) }
            // F60 · F22: encabezado único; sin historial, el detalle vuelve a donde vive la
            // cuenta: Créditos si es deuda (Ola 7: tarjetas y préstamos ya no se listan en
            // Cuentas), Cuentas en cualquier otro caso. El grupo viene en la pantalla, no de la
            // cuenta cargada: así la reserva es la correcta desde el primer frame (y es el mismo
            // dato con el que `navTabFor` resalta la pestaña).
            MinScreenHeader(
                title = account?.name ?: "",
                leading = HeaderLeading.Back(fallback = homeScreenFor(group)),
                action = if (accountTypePair != null) {
                    {
                        Icon(
                            imageVector = accountTypePair.first,
                            contentDescription = accountTypePair.second,
                            tint = MinTextDim,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else null,
            )

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MinPrimaryContainer,
                    trackColor = MinSurfaceContainerHigh,
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp,
                ),
            ) {
                // Balance hero card
                account?.let { acc ->
                    item(key = "balance-card") {
                        val typeLabel = accountTypeIcon(acc.type).second
                        val isCard = isDebtAccount(acc.type)
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(20.dp),
                        ) {
                            Text(
                                text = if (isCard) "DEUDA ACTUAL" else "SALDO ACTUAL",
                                fontSize = 11.sp,
                                color = MinTextMute,
                                letterSpacing = 0.4.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(8.dp))
                            if (isCard) {
                                val (debt, isEstimate) = cardDebt(acc)
                                MonoText(
                                    // debt < 0 es saldo a favor: signo invertido a propósito, así que se
                                    // le pasa el valor absoluto — si no, formatCOP (F36) le pondría su
                                    // propio "−" encima del "+" de acá.
                                    text = "${if (isEstimate) "≈" else ""}${if (debt < 0) "+" else ""}${formatCOP(kotlin.math.abs(debt))}",
                                    fontSize = 28f,
                                    color = if (debt < 0) MinIncome else MinExpense,
                                    fontWeight = FontWeight.Medium,
                                )
                            } else {
                                MonoText(
                                    text = formatCOP(acc.balance), // formatCOP ya trae el signo (F36)
                                    fontSize = 28f,
                                    color = if (acc.balance >= 0) MinIncome else MinExpense,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (typeLabel.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isCard) typeLabel else "COP · $typeLabel",
                                    fontSize = 11.sp,
                                    color = MinTextMute,
                                )
                            }
                            if (hasForeignBalance(acc)) {
                                Spacer(Modifier.height(12.dp))
                                CurrencyBreakdown(acc)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }

                // Section header
                item(key = "section-header") {
                    MinSectionHeader(title = "Movimientos", count = totalEvents.takeIf { it > 0 })
                }

                // Empty state
                if (!loading && days.isEmpty()) {
                    item(key = "empty-state") {
                        // F10: la acción registra directo en esta cuenta — no en la primera de
                        // la lista, que es lo que pasaba antes de que QuickAdd aceptara un
                        // accountId preseleccionado.
                        Column(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(top = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Sin movimientos aún", fontSize = 14.sp, color = MinTextMute)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MinPrimaryContainer)
                                    .clickable { onNavigate(Screen.QuickAdd(presetAccountId = accountId)) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+ Registrar el primero",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MinOnPrimaryContainer,
                                )
                            }
                        }
                    }
                }

                // Day groups
                days.forEach { day ->
                    item(key = day.date) {
                        Column(modifier = Modifier.padding(top = 20.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = day.date.uppercase(),
                                    fontSize = 11.sp,
                                    color = MinTextMute,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                )
                                if (day.items.any { it.currency == "COP" }) {
                                    MonoText(
                                        // Se pasa el valor absoluto: el signo ya lo pone el if de acá
                                        // (siempre "+" o "−", incluso en 0) — pasarle el total con signo
                                        // a formatCOP (F36) duplicaría el "−" cuando el día cierra en rojo.
                                        text = "${if (day.total >= 0) "+" else "−"}${formatCOP(kotlin.math.abs(day.total))}",
                                        fontSize = 11f,
                                        color = MinTextMute,
                                    )
                                }
                            }
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            ) {
                                day.items.forEachIndexed { i, event ->
                                    val isIncome = event.type == TransactionType.INCOME
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedEvent = event }
                                                .padding(vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text(
                                                        text = event.description,
                                                        fontSize = 14.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MinText,
                                                        letterSpacing = (-0.1).sp,
                                                    )
                                                    if (event.reconciliationStatus == ReconciliationStatus.UNCONFIRMED) {
                                                        StatusDot(MinWarn)
                                                    }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text(event.category, fontSize = 12.sp, color = MinTextMute)
                                                    StatusDot(MinTextFaint, 2.dp)
                                                    Text(
                                                        text = event.source.name,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MinTextMute,
                                                        letterSpacing = 0.3.sp,
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${if (isIncome) "+" else "−"}${formatMoney(event.amount, event.currency)}",
                                                fontSize = 14.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isIncome) MinIncome else MinText,
                                                letterSpacing = (-0.3).sp,
                                            )
                                        }
                                        if (i < day.items.size - 1) Hairline()
                                    }
                                }
                            }
                        }
                    }
                }

                // F55: al final de la pantalla, en rojo — mismo lugar donde el dueño esperaba
                // encontrarlo cuando pidió "ayúdame a eliminarla" y no había nada acá. Solo
                // aparece una vez que la cuenta cargó (necesita accountName real para la hoja).
                account?.let { acc ->
                    item(key = "delete-account") {
                        Spacer(Modifier.height(28.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDeleteAccount = true }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Eliminar cuenta",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinExpense,
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // La barra inferior ya no vive dentro de esta pantalla (la pinta App.kt debajo),
                // así que el snackbar solo necesita separarse del borde.
                .padding(bottom = 16.dp),
        )

        selectedEvent?.let { event ->
            VoidEventSheet(
                event = event,
                onDismiss = { selectedEvent = null },
                onVoided = {
                    selectedEvent = null
                    refreshKey++
                },
            )
        }

        if (showDeleteAccount) {
            account?.let { acc ->
                DeleteAccountSheet(
                    accountId = acc.id,
                    accountName = acc.name,
                    eventCount = totalEvents,
                    onDismiss = { showDeleteAccount = false },
                    onDeleted = {
                        // F22: mismo destino de reserva que la flecha de volver de acá arriba —
                        // AccountsScreen recarga sola al re-entrar (LaunchedEffect(refreshKey)
                        // con estado fresco, ver App.kt: cada pantalla se descompone del todo al
                        // salir de su rama del `when`), así que no hace falta pedirle un refresh.
                        showDeleteAccount = false
                        goBack(Screen.Accounts)
                    },
                )
            }
        }
    }
}

private fun epochToDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()

private fun accountTypeIcon(type: AccountType): Pair<ImageVector, String> = when (type) {
    AccountType.CASH        -> Icons.Filled.Payments to "Efectivo"
    AccountType.SAVINGS     -> Icons.Filled.AccountBalance to "Ahorros"
    AccountType.CHECKING    -> Icons.Filled.AccountBalanceWallet to "Corriente"
    AccountType.INVESTMENT  -> Icons.AutoMirrored.Filled.TrendingUp to "Inversión"
    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard to "Crédito"
    AccountType.LOAN        -> Icons.Filled.RequestQuote to "Préstamo"
}
