package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.NavTab
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun ExtractosScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var uploadingFileName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

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
                modifier = Modifier.size(20.dp).clickable { onNavigate(Screen.Mas) },
            )
            Spacer(Modifier.width(12.dp))
            Text("Extractos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MinText)
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
        ) {
            // Info banner
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

            if (uploading) {
                // Progress state
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
                // Upload zone
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

            // Error
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, color = MinExpense, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))

            // Supported banks
            Text(
                "Bancos soportados",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinTextDim,
                modifier = Modifier.padding(bottom = 10.dp),
            )
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
}
