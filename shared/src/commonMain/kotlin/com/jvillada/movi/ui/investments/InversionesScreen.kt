package com.jvillada.movi.ui.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun InversionesScreen(onNavigate: (Screen) -> Unit) {
    val goBack = LocalGoBack.current
    var holdings by remember { mutableStateOf<List<Holding>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getHoldings() }
            .onSuccess { holdings = it }
    }
    val total = holdings.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // F22: Inversiones vive en Más — destino de reserva si no hay historial
            // (antes caía siempre en Inicio, aunque entraras desde Más).
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = MinText, modifier = Modifier.size(22.dp).clickableSimple { goBack(Screen.Mas) })
            Text("Inversiones", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp, modifier = Modifier.weight(1f))
            // F21: acá había un "+" sin acción — no existe ni la hoja de alta ni el endpoint
            // para crear una inversión, solo el de listar. Se saca en vez de prometer un alta
            // que no hay; vuelve cuando se construya (anotado en el plan, Ola posterior).
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            if (holdings.isEmpty()) {
                // F21: sin posiciones, la tarjeta "Patrimonio invertido" solo mostraba un
                // gráfico vacío (una línea plana) y "$0" — nada de eso es información, es
                // decorado que simula haber algo. Un único estado vacío honesto en su lugar,
                // sin la tarjeta de sugerencias de diversificación (esa también prometía algo
                // — "recibe sugerencias" — que no hay detrás sin datos que analizar).
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                    ) {
                        Text("Aún no hay inversiones registradas", fontSize = 14.sp, color = MinTextMute)
                    }
                }
            } else {
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text("Patrimonio invertido", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = formatCOP(total),
                            fontSize = 38.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            color = MinText,
                            letterSpacing = (-1.4).sp,
                            lineHeight = 38.sp,
                        )
                        Spacer(Modifier.height(18.dp))
                        InvestmentSparkline(modifier = Modifier.fillMaxWidth().height(56.dp), hasData = true)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            listOf("1M", "3M", "6M", "1A", "Todo").forEachIndexed { i, p ->
                                val active = i == 3
                                Column {
                                    Text(
                                        text = p,
                                        fontSize = 12.sp,
                                        color = if (active) MinText else MinTextMute,
                                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    if (active) {
                                        Box(modifier = Modifier.width(20.dp).height(1.5.dp).clip(RoundedCornerShape(1.dp)).background(MinText))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Mis posiciones", count = holdings.size)
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            holdings.forEachIndexed { i, h ->
                                CardRow(
                                    left = { Text(h.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                    sub = h.sub,
                                    right = {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(formatCOP(h.amount), fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                                            Text(
                                                text = "${if (h.change > 0) "+" else ""}${formatOneDecimal(h.change)}%",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = when {
                                                    h.change > 0 -> MinIncome
                                                    h.change < 0 -> MinExpense
                                                    else -> MinTextMute
                                                },
                                            )
                                        }
                                    },
                                    isLast = i == holdings.size - 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatOneDecimal(v: Double): String {
    val intPart = v.toLong()
    val frac = kotlin.math.abs((v - intPart) * 10).toLong()
    return "$intPart.$frac"
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
