package com.jvillada.movi.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun SMSInboxScreen(onNavigate: (Screen) -> Unit) {
    var smsItems by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getSmsMessages() }
            .onSuccess { smsItems = it }
    }
    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("‹", fontSize = 22.sp, color = MinText, modifier = Modifier.clickableSimple { onNavigate(Screen.Dashboard) })
            Column(modifier = Modifier.weight(1f)) {
                Text("SMS bancarios", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                Text("Conectado a 2 bancos · 2 pendientes", fontSize = 12.sp, color = MinTextMute)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp).let {
            PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
        }) {
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(18.dp),
                ) {
                    Text("AUTO-LECTURA ACTIVA", fontSize = 11.sp, color = MinTextMute, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Text("Movi lee tus SMS bancarios automáticamente. ", fontSize = 13.5.sp, color = MinText, lineHeight = 19.sp)
                        Text("Revisa los pendientes para confirmar comercios o categoría.", fontSize = 13.5.sp, color = MinTextMute, lineHeight = 19.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Bandeja", count = smsItems.size)
            }

            smsItems.forEach { it ->
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(it.bank, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinText)
                            StatusDot(MinTextFaint, 2.dp)
                            Text(it.time, fontSize = 11.5.sp, color = MinTextMute)
                            Spacer(Modifier.weight(1f))
                            if (it.state == "pending") {
                                Text("PENDIENTE", fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = MinWarn, letterSpacing = 0.4.sp)
                            } else {
                                Text("AUTO", fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = MinIncome, letterSpacing = 0.4.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MinHairline))
                            Text(it.text, fontSize = 12.sp, color = MinTextDim, fontFamily = FontFamily.Monospace, lineHeight = 17.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Hairline()
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(it.det, fontSize = 13.sp, color = MinText, letterSpacing = (-0.1).sp, modifier = Modifier.weight(1f))
                            if (it.state == "pending") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .border(1.dp, MinBorderStrong, RoundedCornerShape(999.dp))
                                        .clickable { onNavigate(Screen.SMSReconcile) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("Revisar", fontSize = 12.5.sp, color = MinText, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
        NavPill()
    }
}

@Composable
fun SMSReconcileScreen(onNavigate: (Screen) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("‹", fontSize = 22.sp, color = MinText, modifier = Modifier.clickableSimple { onNavigate(Screen.SMSInbox) })
            Text("Reconciliar movimiento", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, modifier = Modifier.weight(1f))
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp).let {
            PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
        }) {
            item {
                MinSectionHeader(title = "SMS recibido")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Default,
                    padding = PaddingValues(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Bancolombia", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinText)
                        StatusDot(MinTextFaint, 2.dp)
                        Text("SMS", fontSize = 11.sp, color = MinTextMute, fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MinHairline))
                        Text(
                            "\"Compra aprobada por \$42.300 en CREPES & WAFFLES el 28/04 a las 13:24.\"",
                            fontSize = 13.sp,
                            color = MinTextDim,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Movi sugiere")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Crepes & Waffles", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.2).sp)
                            Text("Restaurantes · Bancolombia ···· 4821", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 3.dp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("−\$42.300", fontSize = 17.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinExpense, letterSpacing = (-0.4).sp)
                            Text("13:24", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Confirma o ajusta")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    listOf(
                        Triple(true, "Comercio", "Crepes & Waffles · Detectado"),
                        Triple(true, "Cuenta", "Bancolombia ···· 4821"),
                        Triple(false, "Categoría", "¿Restaurantes o Comida rápida?"),
                    ).forEachIndexed { i, (ok, label, value) ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(18.dp).clip(CircleShape)
                                        .background(if (ok) MinIncome.copy(alpha = 0.16f) else MinWarn.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(if (ok) "✓" else "?", fontSize = 10.sp, color = if (ok) MinIncome else MinWarn, fontWeight = FontWeight.Bold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label.uppercase(), fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
                                    Text(value, fontSize = 13.5.sp, color = MinText, letterSpacing = (-0.1).sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                if (!ok) ChevronRight()
                            }
                            if (i < 2) Hairline()
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Restaurantes" to true, "Comida rápida" to false, "Otra…" to false).forEach { (label, on) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) MinText else Color.Transparent)
                                .then(if (!on) Modifier.border(1.dp, MinBorderStrong, RoundedCornerShape(999.dp)) else Modifier)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(label, fontSize = 12.5.sp, color = if (on) MinBg else MinText, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(18.dp),
                ) {
                    Text("¿Es un gasto que se repite mensualmente?", fontSize = 13.5.sp, color = MinText, letterSpacing = (-0.1).sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("No" to false, "A veces" to false, "Sí" to true).forEach { (label, on) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) MinText else Color.Transparent)
                                    .then(if (!on) Modifier.border(1.dp, MinBorderStrong, RoundedCornerShape(12.dp)) else Modifier)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, fontSize = 13.sp, color = if (on) MinBg else MinText, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, MinBorderStrong, RoundedCornerShape(14.dp)).clickable { onNavigate(Screen.SMSInbox) },
                contentAlignment = Alignment.Center,
            ) { Text("Ignorar", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText) }
            Box(
                modifier = Modifier.weight(1.7f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(MinText).clickable { onNavigate(Screen.SMSInbox) },
                contentAlignment = Alignment.Center,
            ) { Text("Confirmar", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinBg) }
        }
        NavPill()
    }
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
