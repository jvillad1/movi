package com.jvillada.movi.ui.goals

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun MetasScreen(onNavigate: (Screen) -> Unit) {
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getGoals() }
            .onSuccess { goals = it }
    }
    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = MinText, modifier = Modifier.size(22.dp).clickableSimple { onNavigate(Screen.Dashboard) })
            Text("Metas de ahorro", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, modifier = Modifier.weight(1f))
            Text("+", fontSize = 22.sp, color = MinTextDim)
        }

        val totalSaved  = goals.sumOf { it.saved }
        val totalTarget = goals.sumOf { it.target }
        val overallPct  = if (totalTarget > 0) (totalSaved.toFloat() / totalTarget.toFloat()).coerceAtMost(1f) else 0f
        val pctLabel    = "${(overallPct * 100).toInt()}%"

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        // Donut
                        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(72.dp)) {
                                val r = size.minDimension / 2 - 6
                                val cx = size.width / 2
                                val cy = size.height / 2
                                drawArc(
                                    color = MinHairline,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                                    topLeft = Offset(cx - r, cy - r),
                                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                                )
                                drawArc(
                                    color = MinText,
                                    startAngle = -90f,
                                    sweepAngle = overallPct * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                                    topLeft = Offset(cx - r, cy - r),
                                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                                )
                            }
                            Text(pctLabel, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total ahorrado", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text(formatCOP(totalSaved), fontSize = 22.sp, fontFamily = FontFamily.Monospace, color = MinText, letterSpacing = (-0.7).sp)
                            Text("de ${formatCOP(totalTarget)} · ${goals.size} metas", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Metas activas", count = if (goals.isNotEmpty()) goals.size else null)
                    if (goals.isEmpty()) {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                        ) {
                            Text("Sin metas de ahorro aún", fontSize = 14.sp, color = MinTextMute)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        goals.forEach { g ->
                            val pct = (g.saved.toFloat() / g.target.toFloat()).coerceAtMost(1f)
                            val done = g.saved >= g.target
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(18.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(g.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.1).sp)
                                    if (done) {
                                        Text("COMPLETADA", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MinIncome, letterSpacing = 0.4.sp)
                                    }
                                }
                                Text(g.deadline, fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row {
                                        Text(formatCOP(g.saved), fontSize = 13.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                                        Text(" / ${formatCOP(g.target)}", fontSize = 13.5.sp, fontFamily = FontFamily.Monospace, color = MinTextMute, letterSpacing = (-0.3).sp)
                                    }
                                    Text("${(pct * 100).toInt()}%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(MinHairline)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pct)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(if (done) MinIncome else MinText.copy(alpha = 0.9f))
                                    )
                                }
                                if (g.monthly > 0) {
                                    Spacer(Modifier.height(14.dp))
                                    Hairline()
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Aporta ${formatCOP(g.monthly)}/mes para llegar a tiempo",
                                        fontSize = 12.sp,
                                        color = MinTextMute,
                                    )
                                }
                            }
                        }
                    }
                }
            }
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

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
