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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.NavTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun ExtractosScreen(onNavigate: (Screen) -> Unit) {
    val goBack = LocalGoBack.current
    val coroutine = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var uploadingFileName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var imports by remember { mutableStateOf(emptyList<StatementImport>()) }
    var importsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getStatementImports() }
            .onSuccess { imports = it }
            .onFailure { t ->
                if (t is CancellationException) throw t
                importsError = "No pude cargar el historial: ${t.message ?: "error"}"
            }
    }

    val launchPicker = rememberFilePicker { fileName, bytes, mimeType ->
        uploading = true
        uploadingFileName = fileName
        error = null
        coroutine.launch {
            runCatching { Repositories.wallets.uploadStatement(fileName, bytes, mimeType) }
                .onSuccess { result: StatementParseResult ->
                    uploading = false
                    onNavigate(Screen.StatementReview(Json.encodeToString(result)))
                }
                .onFailure {
                    uploading = false
                    error = "No pude procesar el extracto: ${it.message ?: "error"}"
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
                // F22: Extractos vive en Más — destino de reserva si no hay historial.
                modifier = Modifier.size(20.dp).clickable { goBack(Screen.Mas) },
            )
            Spacer(Modifier.width(12.dp))
            Text("Extractos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MinText)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Info banner
            item(key = "info-banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MinSurfaceContainer)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Rounded.Description, null,
                        tint = MinPrimary,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Fuente de verdad", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinText)
                        Text(
                            "Los extractos bancarios reconcilian automáticamente tus movimientos. Sube PDF, CSV o XLS de cualquier banco colombiano.",
                            fontSize = 12.sp, color = MinTextMute, lineHeight = 17.sp,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Upload zone / progress
            item(key = "upload-zone") {
                if (uploading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MinSurfaceContainerLow)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(uploadingFileName, fontSize = 12.sp, color = MinText)
                            Text("Parseando…", fontSize = 11.sp, color = MinPrimary)
                        }
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MinPrimary,
                            trackColor = MinSurfaceContainerHigh,
                        )
                        Text("Claude está leyendo el extracto", fontSize = 11.sp, color = MinTextMute)
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MinSurfaceContainerLow)
                            .border(1.dp, MinBorderStrong, RoundedCornerShape(16.dp))
                            .clickable { launchPicker() },
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.UploadFile, null, tint = MinPrimary, modifier = Modifier.size(40.dp))
                            Text("Subir extracto", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MinText)
                            Text("PDF · CSV · XLS", fontSize = 12.sp, color = MinTextMute)
                        }
                    }
                }
            }

            // Upload error
            error?.let { msg ->
                item(key = "upload-error") {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        msg, fontSize = 12.sp, color = MinExpense,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Supported banks
            item(key = "banks-header") {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Bancos soportados",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinTextDim,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            item(key = "banks-list") {
                Column {
                    listOf("Bancolombia", "Nequi", "Davivienda", "BBVA", "Falabella", "Colpatria", "Banco de Bogotá").chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            row.forEach { banco ->
                                Text(
                                    banco, fontSize = 11.sp, color = MinTextMute,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MinSurfaceContainerHigh)
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Import history section
            item(key = "imports-header") {
                Spacer(Modifier.height(28.dp))
                Text(
                    "IMPORTACIONES ANTERIORES",
                    fontSize = 11.sp, color = MinTextDim, letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            when {
                importsError != null -> item(key = "imports-error") {
                    Text(
                        importsError!!,
                        fontSize = 12.sp, color = MinExpense,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                imports.isEmpty() -> item(key = "imports-empty") {
                    Text(
                        "Aún no hay importaciones",
                        fontSize = 13.sp, color = MinTextMute,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                else -> item(key = "imports-list") {
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        imports.forEachIndexed { i, imp ->
                            ImportCard(imp) { onNavigate(Screen.ImportDetail(imp.id)) }
                            if (i < imports.size - 1) Hairline()
                        }
                    }
                }
            }

            item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
        }

        MinBottomNav(active = NavTab.MORE) { tab ->
            when (tab) {
                NavTab.HOME         -> onNavigate(Screen.Dashboard)
                NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                NavTab.ADD          -> onNavigate(Screen.QuickAdd())
                NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                NavTab.MORE         -> onNavigate(Screen.Mas)
            }
        }
    }
}

@Composable
private fun ImportCard(imp: StatementImport, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${imp.bankName.uppercase()} · ${imp.period.uppercase()}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinText,
            )
            Text(
                "${imp.importedCount} importadas · ${imp.reconciledCount} reconciliadas",
                fontSize = 12.sp, color = MinTextMute,
            )
        }
        Text(
            importEpochToShortDate(imp.importedAt),
            fontSize = 12.sp, color = MinTextMute,
        )
    }
}

private fun importEpochToShortDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
