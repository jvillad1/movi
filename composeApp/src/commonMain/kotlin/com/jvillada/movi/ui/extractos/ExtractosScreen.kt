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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.NavTab

@Composable
fun ExtractosScreen(onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Volver",
                tint = MinTextDim,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onNavigate(Screen.Mas) },
            )
            Spacer(Modifier.width(12.dp))
            Text("Extractos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MinText)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
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
                    Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MinPrimary,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Fuente de verdad",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinText,
                    )
                    Text(
                        "Los extractos bancarios reconcilian automáticamente tus movimientos. Sube PDF, CSV o XLS de cualquier banco colombiano.",
                        fontSize = 12.sp,
                        color = MinTextMute,
                        lineHeight = 17.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Upload area (placeholder)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MinSurfaceContainerLow)
                    .border(1.dp, MinBorderStrong, RoundedCornerShape(16.dp)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.UploadFile,
                        contentDescription = null,
                        tint = MinPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        "Subir extracto",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinText,
                    )
                    Text(
                        "PDF · CSV · XLS",
                        fontSize = 12.sp,
                        color = MinTextMute,
                    )
                    Text(
                        "Próximamente",
                        fontSize = 11.sp,
                        color = MinPrimary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Bancos soportados
            Text(
                "Bancos soportados",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MinTextDim,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            val bancos = listOf("Bancolombia", "Nequi", "Davivienda", "BBVA", "Falabella", "Colpatria", "Banco de Bogotá")
            bancos.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    row.forEach { banco ->
                        Text(
                            text = banco,
                            fontSize = 11.sp,
                            color = MinTextMute,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MinSurfaceContainerHigh)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "El parsing automático llegará en la próxima versión.\nPor ahora podés registrar los movimientos manualmente o vía SMS.",
                fontSize = 12.sp,
                color = MinTextFaint,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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

