package com.jvillada.movi.ui.ocr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun OCRCaptureScreen(onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))
    ) {
        // Status pill at top
        Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusDot(MinIncome)
                Text("Detectando recibo…", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }

        // Receipt mock
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF5F2EA))
                    .padding(14.dp),
            ) {
                Column {
                    Text("ÉXITO COUNTRY", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222), fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Cra 15 # 82-12 · Bogotá", fontSize = 9.sp, color = Color(0xFF666666), fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF999999).copy(alpha = 0.5f)))
                    Spacer(Modifier.height(8.dp))
                    listOf("Leche x2" to "\$8.400", "Pan integral" to "\$6.200", "Pollo 1kg" to "\$22.900", "Verduras" to "\$18.300", "Detergente" to "\$15.600").forEach { (item, price) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF222222))
                            Text(price, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF222222))
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333333)).padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF222222))
                        Text("\$312.400", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF222222))
                    }
                }
            }

            // Corner brackets
            listOf(true to true, false to true, true to false, false to false).forEach { (isLeft, isTop) ->
                val xAlign = if (isLeft) Alignment.Start else Alignment.End
                val yAlign = if (isTop) Alignment.Top else Alignment.Bottom
                Box(
                    modifier = Modifier
                        .align(if (isTop && isLeft) Alignment.TopStart else if (isTop) Alignment.TopEnd else if (isLeft) Alignment.BottomStart else Alignment.BottomEnd)
                        .size(24.dp)
                        .border(
                            width = 1.5.dp,
                            color = MinText,
                            shape = when {
                                isLeft && isTop  -> RoundedCornerShape(topStart = 2.dp)
                                !isLeft && isTop  -> RoundedCornerShape(topEnd = 2.dp)
                                isLeft            -> RoundedCornerShape(bottomStart = 2.dp)
                                else              -> RoundedCornerShape(bottomEnd = 2.dp)
                            }
                        )
                )
            }
        }

        // Capture controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.GridOn, contentDescription = "Cuadrícula", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .padding(7.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onNavigate(Screen.OCRConfirm) }
            )
            Icon(imageVector = Icons.Filled.WbSunny, contentDescription = "Brillo", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun OCRConfirmScreen(onNavigate: (Screen) -> Unit) {
    val goBack = LocalGoBack.current
    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        // F60 · F22: normalmente vuelve a la cámara (paso anterior real); si entraste directo a
        // confirmar (poco probable, pero por si acaso) cae a Inicio.
        MinScreenHeader(
            title = "Confirma el recibo",
            leading = HeaderLeading.Back(fallback = Screen.Dashboard),
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MinIncome, modifier = Modifier.size(16.dp))
                        Row {
                            Text("Recibo leído. ", fontSize = 13.sp, color = MinText)
                            Text("Revisa antes de guardar.", fontSize = 13.sp, color = MinTextMute)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(22.dp),
                ) {
                    Text("Total a registrar", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Text("−\$312.400", fontSize = 38.sp, fontFamily = FontFamily.Monospace, color = MinExpense, letterSpacing = (-1.4).sp, lineHeight = 38.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("OCR · 5 ítems detectados", fontSize = 12.sp, color = MinTextMute, fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp)
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Campos detectados")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        CardRow(left = { Text("Comercio", fontSize = 14.5.sp, color = MinTextMute) }, right = { Text("Éxito Country", fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) }, showChevron = true)
                        CardRow(left = { Text("Fecha", fontSize = 14.5.sp, color = MinTextMute) }, right = { Text("27 abr · 18:42", fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) }, showChevron = true)
                        CardRow(left = { Text("Categoría", fontSize = 14.5.sp, color = MinTextMute) }, right = { Text("Mercado", fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) }, showChevron = true)
                        CardRow(left = { Text("Cuenta", fontSize = 14.5.sp, color = MinTextMute) }, right = { Text("Bancolombia ···· 4821", fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) }, showChevron = true, isLast = true)
                    }
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Ítems · puedes editar", count = 5)
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        listOf("Leche x2" to 8_400L, "Pan integral" to 6_200L, "Pollo 1kg" to 22_900L, "Verduras" to 18_300L, "Detergente" to 15_600L).forEachIndexed { i, (name, value) ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(name, fontSize = 13.5.sp, color = MinText)
                                    Text(formatCOP(value), fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MinTextDim, letterSpacing = (-0.3).sp)
                                }
                                if (i < 4) Hairline()
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("+ Agregar ítem", fontSize = 13.sp, color = MinText, fontWeight = FontWeight.Medium)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).border(0.dp, Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, MinBorderStrong, RoundedCornerShape(14.dp)).clickable { onNavigate(Screen.OCRCapture) },
                contentAlignment = Alignment.Center,
            ) { Text("Reescanear", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText) }
            Box(
                // Ola 2 #1: pop, no push — coherente con SMS/Extractos (evita que un segundo
                // tap desde Inicio vuelva a este "guardado" y lo repita).
                modifier = Modifier.weight(1.7f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(MinText).clickable { goBack(Screen.Dashboard) },
                contentAlignment = Alignment.Center,
            ) { Text("Guardar movimiento", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinBg) }
        }
    }
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
