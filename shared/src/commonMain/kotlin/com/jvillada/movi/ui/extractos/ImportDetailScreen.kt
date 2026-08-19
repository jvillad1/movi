package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ImportDetailScreen(onNavigate: (Screen) -> Unit, importId: String) {
    val goBack = LocalGoBack.current
    var detail by remember { mutableStateOf<StatementImportDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(importId) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getStatementImportDetail(importId) }
            .onSuccess { detail = it; loading = false }
            .onFailure { t ->
                if (t is CancellationException) throw t
                loading = false
                error = "No pude cargar el detalle: ${t.message ?: "error"}"
            }
    }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Icon(
                Icons.Rounded.ArrowBackIosNew, "Volver",
                tint = MinTextDim,
                // F22: el detalle vuelve a la lista de Extractos si no hay historial.
                modifier = Modifier.clickable { goBack(Screen.Extractos) }.padding(12.dp).size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text("Detalle de importación", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MinText)
        }

        when {
            loading -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = MinPrimary,
                trackColor = MinSurfaceContainerHigh,
            )
            error != null -> Text(
                error!!,
                fontSize = 13.sp, color = MinExpense,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
            detail != null -> ImportDetailContent(detail = detail!!)
        }
    }
}

@Composable
private fun ImportDetailContent(detail: StatementImportDetail) {
    val imp = detail.import
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Spacer(Modifier.height(4.dp))
            ImportSummaryHeader(imp)
        }

        if (detail.events.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No se encontraron movimientos",
                    fontSize = 13.sp, color = MinTextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                )
            }
        } else {
            item(key = "section-label") {
                Text(
                    "MOVIMIENTOS",
                    fontSize = 11.sp, color = MinTextDim,
                    letterSpacing = 0.8.sp,
                )
            }
            item(key = "events-card") {
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    detail.events.forEachIndexed { i, event ->
                        ImportEventRow(event)
                        if (i < detail.events.size - 1) Hairline()
                    }
                }
            }
        }

        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ImportSummaryHeader(imp: StatementImport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${imp.bankName.uppercase()} · ${imp.period.uppercase()}",
            fontSize = 11.sp, color = MinTextDim, letterSpacing = 0.8.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${imp.importedCount} importadas · ${imp.reconciledCount} reconciliadas",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MinText,
            )
            Text(
                epochToShortDate(imp.importedAt),
                fontSize = 12.sp, color = MinTextMute,
            )
        }
    }
}

@Composable
private fun ImportEventRow(event: FinancialEvent) {
    val isIncome = event.type == TransactionType.INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.description.ifBlank { event.merchant ?: "Sin descripción" },
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(event.category, fontSize = 12.sp, color = MinTextMute)
                StatusDot(MinTextFaint, 2.dp)
                Text(
                    epochToShortDate(event.timestamp),
                    fontSize = 11.sp, color = MinTextMute,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        MonoText(
            text = "${if (isIncome) "+" else "−"}${formatCOP(event.amount)}",
            fontSize = 14f,
            color = if (isIncome) MinIncome else MinExpense,
        )
    }
}

private fun epochToShortDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
