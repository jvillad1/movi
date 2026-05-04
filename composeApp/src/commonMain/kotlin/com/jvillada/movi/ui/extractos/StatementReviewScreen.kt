package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.*
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import kotlinx.coroutines.launch

private val MinAmber = Color(0xFFE8A85C)

@Composable
fun StatementReviewScreen(
    onNavigate: (Screen) -> Unit,
    result: StatementParseResult,
) {
    val coroutine = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(emptyList<Account>()) }
    var selectedIds by remember { mutableStateOf(result.newTransactions.map { it.id }.toSet()) }
    val reconciliations = remember { mutableStateMapOf<String, ReconciliationDecision>() }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load accounts to determine destination
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it }
    }

    val destinationAccount = remember(accounts, result.bankName) {
        accounts.firstOrNull { it.name.contains(result.bankName, ignoreCase = true) }
            ?: accounts.firstOrNull { it.type != AccountType.CASH }
            ?: accounts.firstOrNull()
    }

    val confirmedCount = reconciliations.values.count { it.confirm }
    val importCount = selectedIds.size + confirmedCount
    val canImport = importCount > 0 && !working && destinationAccount != null

    fun import() {
        val acct = destinationAccount ?: return
        working = true; error = null
        coroutine.launch {
            runCatching {
                val decision = ImportDecision(
                    statementId = result.statementId,
                    accountId = acct.id,
                    imports = result.newTransactions.filter { it.id in selectedIds },
                    reconciliations = reconciliations.values.toList(),
                    skipped = result.newTransactions.map { it.id }.filter { it !in selectedIds },
                )
                Repositories.wallets.importStatement(decision)
            }.onSuccess {
                working = false
                onNavigate(Screen.Transactions)
            }.onFailure {
                working = false
                error = "No pude importar: ${it.message ?: "error"}"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Icon(
                Icons.Rounded.ArrowBackIosNew, "Volver",
                tint = MinTextDim,
                modifier = Modifier.size(20.dp).clickable { onNavigate(Screen.Extractos) },
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "${result.bankName.uppercase()} · ${result.period.uppercase()}",
                    fontSize = 10.sp, color = MinTextMute, letterSpacing = 1.sp,
                )
                val newCount = result.newTransactions.size
                val matchCount = result.matches.size
                Text(
                    text = "$newCount nuevas · $matchCount coincidencias",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MinText,
                )
            }
        }

        // Account destination chip
        destinationAccount?.let { acct ->
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Destino:", fontSize = 11.sp, color = MinTextMute)
                Text(
                    acct.name,
                    fontSize = 11.sp, color = MinPrimary, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MinPrimary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            // Matches section
            if (result.matches.isNotEmpty()) {
                item {
                    Text(
                        "POSIBLES DUPLICADOS",
                        fontSize = 10.sp, color = MinAmber, letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(result.matches, key = { it.parsed.id }) { match ->
                    ReconciliationCard(
                        match = match,
                        decision = reconciliations[match.parsed.id],
                        onConfirm = { dec -> reconciliations[match.parsed.id] = dec },
                        onReject = {
                            reconciliations[match.parsed.id] = ReconciliationDecision(
                                parsedId = match.parsed.id,
                                existingEventId = match.existingEventId,
                                confirm = false,
                                categorySource = FieldSource.STATEMENT,
                                descriptionSource = FieldSource.STATEMENT,
                                merchantSource = FieldSource.STATEMENT,
                                parsed = match.parsed,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // New transactions section
            if (result.newTransactions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "NUEVAS TRANSACCIONES",
                            fontSize = 10.sp, color = MinTextDim, letterSpacing = 1.sp,
                        )
                        val allSelected = selectedIds.size == result.newTransactions.size
                        Text(
                            if (allSelected) "Deseleccionar todas" else "Seleccionar todas",
                            fontSize = 11.sp, color = MinPrimary,
                            modifier = Modifier.clickable {
                                selectedIds = if (allSelected) emptySet()
                                    else result.newTransactions.map { it.id }.toSet()
                            },
                        )
                    }
                }
                items(result.newTransactions, key = { it.id }) { tx ->
                    NewTransactionRow(
                        tx = tx,
                        checked = tx.id in selectedIds,
                        onToggle = {
                            selectedIds = if (tx.id in selectedIds)
                                selectedIds - tx.id else selectedIds + tx.id
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // Error message
        error?.let {
            Text(
                it, fontSize = 12.sp, color = MinExpense,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Sticky bottom bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MinSurfaceContainer)
                .padding(16.dp),
        ) {
            Button(
                onClick = ::import,
                enabled = canImport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MinPrimary),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (working) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "Importar $importCount seleccionada${if (importCount != 1) "s" else ""}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewTransactionRow(
    tx: ParsedTransaction,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MinSurface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = if (checked) "Seleccionado" else "No seleccionado",
            tint = if (checked) MinPrimary else MinTextMute,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.merchant, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinText)
            Text("${tx.category} · ${tx.date}", fontSize = 11.sp, color = MinTextMute)
        }
        val amountColor = if (tx.type == TransactionType.INCOME) MinIncome else MinExpense
        val prefix = if (tx.type == TransactionType.INCOME) "+" else "−"
        Text(
            "$prefix$${"%,d".format(tx.amount)}",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = amountColor,
        )
    }
}

@Composable
private fun ReconciliationCard(
    match: ReconciliationMatch,
    decision: ReconciliationDecision?,
    onConfirm: (ReconciliationDecision) -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var categorySource by remember { mutableStateOf(FieldSource.STATEMENT) }
    var descriptionSource by remember { mutableStateOf(FieldSource.STATEMENT) }
    var merchantSource by remember { mutableStateOf(FieldSource.MANUAL) }

    val isDecided = decision != null
    val borderColor = if (isDecided && decision!!.confirm) MinIncome else MinAmber

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Badge + amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "⚠ POSIBLE DUPLICADO",
                fontSize = 9.sp, color = MinAmber, letterSpacing = 0.5.sp,
            )
            val amtColor = if (match.parsed.type == TransactionType.INCOME) MinIncome else MinExpense
            val prefix = if (match.parsed.type == TransactionType.INCOME) "+" else "−"
            Text(
                "$prefix$${"%,d".format(match.parsed.amount)}",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = amtColor,
            )
        }

        // Column headers
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(80.dp))
            Text(
                "MANUAL", fontSize = 9.sp, color = MinPrimary, letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            Text(
                "EXTRACTO", fontSize = 9.sp, color = Color(0xFF5CB8E8), letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
        }

        // Merchant row (differs always since bank names are messy)
        FieldRow(
            label = "Comercio",
            manualValue = match.existingEvent.merchant ?: match.existingEvent.description,
            statementValue = match.parsed.merchant,
            selected = merchantSource,
            onToggle = { merchantSource = if (merchantSource == FieldSource.MANUAL) FieldSource.STATEMENT else FieldSource.MANUAL },
        )

        // Category row (only if different)
        if (match.parsed.category != match.existingEvent.category) {
            FieldRow(
                label = "Categoría",
                manualValue = match.existingEvent.category,
                statementValue = match.parsed.category,
                selected = categorySource,
                onToggle = { categorySource = if (categorySource == FieldSource.STATEMENT) FieldSource.MANUAL else FieldSource.STATEMENT },
            )
        }

        // Description row (only if extracto has one and differs)
        val existDesc = match.existingEvent.description
        val parsedDesc = match.parsed.description
        if (parsedDesc.isNotBlank() && parsedDesc != existDesc) {
            FieldRow(
                label = "Descripción",
                manualValue = existDesc.ifBlank { "—" },
                statementValue = parsedDesc,
                selected = descriptionSource,
                onToggle = { descriptionSource = if (descriptionSource == FieldSource.STATEMENT) FieldSource.MANUAL else FieldSource.STATEMENT },
            )
        }

        if (!isDecided) {
            Text(
                "Toca cada campo para cambiar la fuente",
                fontSize = 9.sp, color = MinTextFaint,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Action buttons
        if (!isDecided) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("No son el mismo", fontSize = 11.sp, color = MinTextDim)
                }
                Button(
                    onClick = {
                        onConfirm(
                            ReconciliationDecision(
                                parsedId = match.parsed.id,
                                existingEventId = match.existingEventId,
                                confirm = true,
                                categorySource = categorySource,
                                descriptionSource = descriptionSource,
                                merchantSource = merchantSource,
                                parsed = match.parsed,
                            )
                        )
                    },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinPrimary),
                ) {
                    Text("Confirmar reconciliación", fontSize = 11.sp, color = Color.White)
                }
            }
        } else {
            Text(
                if (decision!!.confirm) "✓ Reconciliado" else "→ Se importará como nuevo",
                fontSize = 11.sp,
                color = if (decision.confirm) MinIncome else MinTextMute,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    manualValue: String,
    statementValue: String,
    selected: FieldSource,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp, color = MinTextMute, modifier = Modifier.width(80.dp))
        FieldCell(
            value = manualValue,
            active = selected == FieldSource.MANUAL,
            onClick = onToggle,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
        )
        FieldCell(
            value = statementValue,
            active = selected == FieldSource.STATEMENT,
            onClick = onToggle,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
    }
}

@Composable
private fun FieldCell(
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (active) MinPrimary else Color.Transparent
    val bgColor = if (active) MinPrimary.copy(alpha = 0.08f) else MinSurfaceContainerHigh
    Text(
        value,
        fontSize = 10.sp,
        color = if (active) MinText else MinTextMute,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
