package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.components.*

@Composable
fun TransactionsScreen(onNavigate: (Screen) -> Unit) {
    var activeFilter by remember { mutableStateOf(0) }
    // F12: "Pendientes" no decía qué es — son los movimientos que entraron solos (SMS, OCR,
    // extracto) y esperan que confirmes monto y categoría. "Por confirmar" sí lo dice.
    val filters = listOf("Todo", "Egresos", "Ingresos", "Por confirmar")

    var allDays by remember { mutableStateOf<List<EventDay>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // F10: el estado vacío necesita saber si hay cuentas para elegir entre "+ Registrar el
    // primero" y "Crear una cuenta primero" — sin esto no hay forma de saber si el problema es
    // "no hay movimientos" o "no hay dónde anotarlos".
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    // Solo se ofrece «Crear una cuenta primero» cuando la lista llegó y vino vacía; mientras
    // carga (o si falló) se ofrece registrar, que es la acción segura.
    var accountsLoaded by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey) {
        runCatching { Repositories.wallets.getAccounts() }.onSuccess { accounts = it; accountsLoaded = true }
    }

    // Candidatos a pago de tarjeta sin marcar (Task 2 del plan): entrada opcional, así que un
    // fallo al traerlos no debe tapar Movimientos con un snackbar.
    var candidates by remember { mutableStateOf<List<FinancialEvent>>(emptyList()) }
    // Los que el dueño ya resolvió en esta pantalla — confirmados con "Marcar" o descartados con
    // "No es", mismo tratamiento para los dos. Se descuentan de `candidates` porque el refetch
    // puede fallar y dejar la lista vieja: sin esto, un pago recién resuelto volvía a aparecer
    // con sus botones activos y el contador seguía diciendo el número de antes — o sea, la app le
    // decía que su acción no se había guardado, cuando sí se guardó. Para "No es" en particular,
    // sin este descuento el falso positivo revivía en cuanto el refetch fallaba.
    var resolvedIds by remember { mutableStateOf(emptySet<String>()) }
    val pendingCandidates = candidates.filterNot { it.id in resolvedIds }
    var selectedEvent by remember { mutableStateOf<FinancialEvent?>(null) }
    var showCandidatesSheet by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getEventsByDay() }
            .onSuccess {
                allDays = it
                // F35: de paso, alimenta el caché de "categorías ya usadas" que lee
                // CategoryField — esta pantalla ya carga los movimientos.
                UsedCategoriesCache.record(it.flatMap { d -> d.items }.map { ev -> ev.category })
            }
            .onFailure { e -> error = e.toUserMessage() }
        loading = false
    }

    LaunchedEffect(refreshKey) {
        runCatching { Repositories.wallets.getCardPaymentCandidates() }
            .onSuccess { candidates = it }
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    fun signedAmount(tx: FinancialEvent): Long =
        if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount

    val visibleDays = remember(activeFilter, allDays) {
        allDays.mapNotNull { day ->
            val filtered = when (activeFilter) {
                1 -> day.items.filter { it.type == TransactionType.EXPENSE && it.reconciliationStatus != ReconciliationStatus.UNCONFIRMED }
                2 -> day.items.filter { it.type == TransactionType.INCOME }
                3 -> day.items.filter { it.reconciliationStatus == ReconciliationStatus.UNCONFIRMED }
                else -> day.items
            }
            if (filtered.isEmpty()) null
            // El total recalculado sigue el mismo criterio que el del server (ver EventRoutes
            // /by-day): countsAsCashFlow deja fuera los movimientos de cuentas de deuda. Sin
            // esto, el encabezado del día decía $0 en "Todo" y +$60.000.000 en "Ingresos" —
            // el mismo número engañoso que esta rama vino a matar, una pestaña más allá.
            else day.copy(items = filtered, total = filtered.filter { it.countsAsCashFlow }.sumOf { signedAmount(it) })
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // F41: mismo componente que Inicio y Presupuestos.
                AvatarButton(onClick = { onNavigate(Screen.Profile) })
                Text(
                    text = "Movimientos",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    letterSpacing = (-0.8).sp,
                )
            }
            Icon(imageVector = Icons.Filled.Search, contentDescription = "Buscar", tint = MinTextDim, modifier = Modifier.size(22.dp))
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 0.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { i, f ->
                val isActive = i == activeFilter
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) MinSurfaceContainerHigh else Color.Transparent)
                        .then(
                            if (!isActive) Modifier.border(1.dp, MinBorderStrong, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { activeFilter = i }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isActive) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MinPrimary, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        text = f,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) MinText else MinTextDim,
                    )
                }
            }
        }

        // Entrada discreta a los candidatos de pago de tarjeta: solo aparece si hay algo que
        // proponer, y abre una lista donde cada uno se confirma por separado (ver
        // CardPaymentCandidatesSheet) — nunca se marcan todos de una.
        if (pendingCandidates.isNotEmpty()) {
            MinCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                variant = MinCardVariant.Default,
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                onClick = { showCandidatesSheet = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (pendingCandidates.size == 1) "1 pago de tarjeta sin marcar"
                               else "${pendingCandidates.size} pagos de tarjeta sin marcar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinText,
                    )
                    ChevronRight()
                }
            }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            if (!loading && visibleDays.isEmpty()) {
                item {
                    // F10: el estado vacío ofrece la acción — registrar si ya hay dónde anotar,
                    // crear una cuenta primero si no.
                    Column(
                        modifier = Modifier.fillParentMaxWidth().padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Sin movimientos aún", fontSize = 14.sp, color = MinTextMute)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MinPrimaryContainer)
                                .clickable {
                                    if (accounts.isNotEmpty() || !accountsLoaded) onNavigate(Screen.QuickAdd())
                                    else showCreateSheet = true
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (accounts.isNotEmpty() || !accountsLoaded) "+ Registrar el primero" else "Crear una cuenta primero",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinOnPrimaryContainer,
                            )
                        }
                    }
                }
            }

            visibleDays.forEach { day ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = day.date.uppercase(),
                                fontSize = 11.sp,
                                color = MinTextMute,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.4.sp,
                            )
                            Text(
                                text = "${if (day.total > 0) "+" else ""}${formatCOP(day.total)}",
                                fontSize = 11.sp,
                                color = MinTextMute,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            day.items.forEachIndexed { i, tx ->
                                val isIncome = tx.type == TransactionType.INCOME
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedEvent = tx }
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
                                                    text = tx.description,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MinText,
                                                    letterSpacing = (-0.1).sp,
                                                )
                                                if (tx.reconciliationStatus == ReconciliationStatus.UNCONFIRMED) {
                                                    StatusDot(MinWarn)
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(tx.category, fontSize = 12.sp, color = MinTextMute)
                                                StatusDot(MinTextFaint, 2.dp)
                                                Text(
                                                    text = tx.source.name,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MinTextMute,
                                                    letterSpacing = 0.3.sp,
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${if (isIncome) "+" else "−"}${formatCOP(tx.amount)}",
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
        }

        MinBottomNav(active = NavTab.TRANSACTIONS) { tab ->
            when (tab) {
                NavTab.HOME    -> onNavigate(Screen.Dashboard)
                NavTab.ADD     -> onNavigate(Screen.QuickAdd())
                NavTab.BUDGETS -> onNavigate(Screen.Budgets)
                NavTab.MORE    -> onNavigate(Screen.Mas)
                else -> {}
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
    )

    selectedEvent?.let { event ->
        ChangeCategorySheet(
            event = event,
            onDismiss = { selectedEvent = null },
            onCategoryChanged = { selectedEvent = null; refreshKey++ },
        )
    }

    if (showCandidatesSheet) {
        CardPaymentCandidatesSheet(
            candidates = pendingCandidates,
            onDismiss = { showCandidatesSheet = false },
            onConfirmed = { id -> resolvedIds = resolvedIds + id; refreshKey++ },
            onDismissedCandidate = { id -> resolvedIds = resolvedIds + id; refreshKey++ },
        )
    }

    if (showCreateSheet) {
        CreateAccountSheet(
            onDismiss = { showCreateSheet = false },
            onAccountCreated = { showCreateSheet = false; refreshKey++ },
        )
    }
    }
}
