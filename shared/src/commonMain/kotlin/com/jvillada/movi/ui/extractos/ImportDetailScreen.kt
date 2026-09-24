package com.jvillada.movi.ui.extractos

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.LocalGoBack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ImportDetailScreen(onNavigate: (Screen) -> Unit, importId: String) {
    var detail by remember { mutableStateOf<StatementImportDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val goBack = LocalGoBack.current
    val coroutine = rememberCoroutineScope()
    var pidiendoDeshacer by remember { mutableStateOf(false) }
    var deshaciendo by remember { mutableStateOf(false) }
    var errorDeDeshacer by remember { mutableStateOf<String?>(null) }

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

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60 · F22: encabezado único; el detalle vuelve a Documentos si no hay historial de
        // navegación. Ola B, tarea 7: era Screen.Extractos — la sección «Importaciones» que
        // abre esta pantalla ahora vive en Documentos, no en Extractos (que salió de Más).
        MinScreenHeader(
            title = "Detalle de importación",
            leading = HeaderLeading.Back(fallback = Screen.Documentos),
        )
        Spacer(Modifier.height(12.dp))

        when {
            loading -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = Movi.colores.marca,
                trackColor = Movi.colores.tarjeta,
            )
            error != null -> Text(
                error!!,
                style = Movi.textos.cuerpo, color = Movi.colores.sale,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
            detail != null -> {
                errorDeDeshacer?.let {
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.padding(horizontal = 20.dp))
                }
                // **Deshacer el importe entero.** Antes un extracto importado en la cuenta equivocada
                // solo se arreglaba anulando fila por fila.
                Text(
                    "Deshacer este importe",
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.sale,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .clickable(enabled = !deshaciendo) { pidiendoDeshacer = true }
                        .padding(vertical = 8.dp),
                )
                ImportDetailContent(detail = detail!!)
            }
        }
    }
    if (pidiendoDeshacer) {
        ConfirmarEnHoja(
            pregunta = "¿Deshacer este importe?",
            detalle = "Se anulan los movimientos que creó este extracto. Lo que ya tenías anotado y solo se concilió se queda. No se puede deshacer.",
            textoConfirmar = "Deshacer",
            ocupado = deshaciendo,
            onConfirmar = {
                deshaciendo = true
                errorDeDeshacer = null
                coroutine.launch {
                    runCatching { Repositories.wallets.deleteStatementImport(importId) }
                        .onSuccess { pidiendoDeshacer = false; goBack(Screen.Documentos) }
                        .onFailure { errorDeDeshacer = it.toUserMessage(); pidiendoDeshacer = false }
                    deshaciendo = false
                }
            },
            onCancelar = { pidiendoDeshacer = false },
        )
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
                    style = Movi.textos.cuerpo, color = Movi.colores.textoMedio,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                )
            }
        } else {
            item(key = "section-label") {
                Text(
                    "MOVIMIENTOS",
                    style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
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
            .background(Movi.colores.tarjeta)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${imp.bankName.uppercase()} · ${imp.period.uppercase()}",
            style = Movi.textos.apoyo, color = Movi.colores.textoMedio, letterSpacing = 0.8.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${imp.importedCount} importadas · ${imp.reconciledCount} reconciliadas",
                style = Movi.textos.cuerpo, fontWeight = FontWeight.SemiBold, color = Movi.colores.texto,
            )
            Text(
                epochToShortDate(imp.importedAt),
                style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
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
                style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(event.category, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                StatusDot(Movi.colores.textoApagado, 2.dp)
                Text(
                    epochToShortDate(event.timestamp),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }
        }
        Text(
            text = "${if (isIncome) "+" else "−"}${formatMoney(event.amount, event.currency)}",
            style = Movi.textos.monto,
            fontWeight = FontWeight.Medium,
            color = if (isIncome) Movi.colores.entra else Movi.colores.sale,
        )
    }
}

private fun epochToShortDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
