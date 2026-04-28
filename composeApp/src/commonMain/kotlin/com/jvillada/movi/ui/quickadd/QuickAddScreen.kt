package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun QuickAddScreen(onDismiss: () -> Unit, onNavigate: (Screen) -> Unit = {}) {
    var typeIndex by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }

    fun onKey(key: String) {
        amount = when (key) {
            "⌫" -> if (amount.isNotEmpty()) amount.dropLast(1) else amount
            "." -> if ("." !in amount) amount + key else amount
            else -> if (amount.length < 12) amount + key else amount
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinTextFaint)
            )

            // Type toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(MinSurfaceContainerLow)
                    .border(1.dp, MinBorder, RoundedCornerShape(999.dp))
                    .padding(3.dp),
            ) {
                listOf("Egreso", "Ingreso").forEachIndexed { i, label ->
                    val isActive = i == typeIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isActive) MinSurfaceContainerHigh else Color.Transparent)
                            .clickable { typeIndex = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isActive) MinText else MinTextDim,
                            letterSpacing = 0.1.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // Amount display
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$${amount.ifEmpty { "0" }}",
                    fontSize = 56.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    color = MinText,
                    letterSpacing = (-2.2).sp,
                    lineHeight = 56.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("COP", fontSize = 12.sp, color = MinTextMute, letterSpacing = 0.4.sp)
            }

            Spacer(Modifier.height(18.dp))

            // Detail card
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            ) {
                CardRow(
                    left = { Text("Categoría", fontSize = 14.5.sp, color = MinTextMute) },
                    right = { Text("Restaurantes", fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) },
                    showChevron = true,
                )
                CardRow(
                    left = { Text("Cuenta", fontSize = 14.5.sp, color = MinTextMute) },
                    right = { Text("Bancolombia ···· 4821", fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) },
                    showChevron = true,
                )
                CardRow(
                    left = { Text("Nota", fontSize = 14.5.sp, color = MinTextMute) },
                    right = { Text("Agregar nota…", fontSize = 14.5.sp, color = MinTextFaint) },
                    isLast = true,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Numpad
            Column {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(".", "0", "⌫"),
                ).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clickable { onKey(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = key,
                                    fontSize = 22.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Normal,
                                    color = MinText,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bottom buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MinBorderStrong, RoundedCornerShape(16.dp))
                        .clickable { onNavigate(Screen.OCRCapture) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📷", fontSize = 20.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MinPrimaryContainer)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Guardar movimiento",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinOnPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            NavPill()
        }
    }
}
