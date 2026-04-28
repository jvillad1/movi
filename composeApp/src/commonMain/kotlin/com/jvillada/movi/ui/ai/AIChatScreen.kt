package com.jvillada.movi.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun AIChatScreen(onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("‹", fontSize = 22.sp, color = MinText,
                modifier = Modifier.clickableSimple { onNavigate(Screen.Dashboard) })
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Movi AI", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.2).sp)
                    Text("BETA", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = MinTextMute,
                        fontFamily = FontFamily.Monospace, letterSpacing = 0.6.sp)
                }
                Text("Conoce tus finanzas", fontSize = 11.sp, color = MinTextMute)
            }
        }
        Hairline()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
        ) {
            item { AIMsgAI("¡Hola Camilo! Pregúntame lo que quieras sobre tu plata.") }
            item { Spacer(Modifier.height(0.dp)) }
            item { AIMsgUser("¿Puedo comprarme un iPhone 17 de \$5.800.000 sin reventarme?") }
            item {
                AIMsgAI {
                    Text(
                        text = "Mirando tu mes sí puedes, pero te lo plantearía así:",
                        fontSize = 13.5.sp,
                        color = MinText,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.High,
                        padding = PaddingValues(16.dp),
                    ) {
                        BreakdownRow("Flujo libre del mes", "+\$1.840.000", MinIncome)
                        Hairline()
                        BreakdownRow("Meta apartamento", "−\$800.000", MinText)
                        Hairline()
                        BreakdownRow("Disponible real", "\$1.040.000", MinText, bold = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Si lo pagas en 6 cuotas de \$966.000 sin interés, te queda margen. De contado tendrías que pausar 4 meses tu meta.",
                        fontSize = 13.sp,
                        color = MinText,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("Ver opciones de pago", "Cómo afecta mi meta", "Compáralo con Samsung").forEach { s ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .border(1.dp, MinBorderStrong, RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(s, fontSize = 12.sp, color = MinText)
                        }
                    }
                }
            }
        }

        // Input bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MinSurface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Pregúntale a Movi…",
                    fontSize = 13.5.sp,
                    color = MinTextMute,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MinText),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("›", fontSize = 20.sp, color = MinBg, fontWeight = FontWeight.Bold)
                }
            }
        }
        NavPill()
    }
}

@Composable
private fun AIMsgUser(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MinText)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text = text, fontSize = 13.5.sp, color = MinBg, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AIMsgAI(text: String) {
    AIMsgAI { Text(text = text, fontSize = 13.5.sp, color = MinText, lineHeight = 20.sp) }
}

@Composable
private fun AIMsgAI(content: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MinSurfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", fontSize = 11.sp, color = MinText)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            content = content,
        )
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (bold) MinText else MinTextMute,
            fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

// Simple clickable extension for non-interactive elements
private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(
        interactionSource = null,
        indication = null,
        onClick = onClick,
    )
)
