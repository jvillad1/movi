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
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun MetasScreen(onNavigate: (Screen) -> Unit) {
    val goBack = LocalGoBack.current
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    // loadKey incrementa tras cada crear/editar/borrar para forzar la recarga.
    var loadKey by remember { mutableStateOf(0) }

    // Estado de la hoja: null = cerrada; no-null = abierta, con la meta a editar (o null = alta).
    var sheetGoal by remember { mutableStateOf<Goal?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(loadKey) {
        runCatching { Repositories.wallets.getGoals() }
            .onSuccess { goals = it }
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // F22: vuelve a donde estabas de verdad (Inicio o Más); Metas vive en
            // Más, así que ese es el destino de reserva si no hay historial.
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = MinText, modifier = Modifier.size(22.dp).clickableSimple { goBack(Screen.Mas) })
            Text("Metas de ahorro", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, modifier = Modifier.weight(1f))
            // F26: el "+" decorativo se reemplaza por el alta real — mismo componente que
            // Recurrentes/Presupuestos/Créditos.
            if (goals.isNotEmpty()) {
                NewItemButton(
                    label = "Nueva meta",
                    onClick = { sheetGoal = null; sheetOpen = true },
                )
            }
        }
        if (goals.isEmpty()) {
            NewItemButton(
                label = "Nueva meta",
                onClick = { sheetGoal = null; sheetOpen = true },
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp),
                full = true,
            )
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
                            Text("Aún no hay metas de ahorro", fontSize = 14.sp, color = MinTextMute)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        goals.forEach { g ->
                            val pct = if (g.target > 0) (g.saved.toFloat() / g.target.toFloat()).coerceAtMost(1f) else 0f
                            val done = g.target > 0 && g.saved >= g.target
                            MinCard(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    // F26: tocar una meta la abre para editar o eliminar —
                                    // mismo patrón que Recurrentes/Créditos.
                                    sheetGoal = g
                                    sheetOpen = true
                                },
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(18.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    GoalRing(pct = pct, done = done, size = 46.dp)
                                    Column(modifier = Modifier.weight(1f)) {
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
                                        // F26: la fecha objetivo es opcional — sin ella no se
                                        // inventa un texto de relleno.
                                        Text(
                                            g.targetDate?.let { "Meta para el $it" } ?: "Sin fecha objetivo",
                                            fontSize = 12.sp,
                                            color = MinTextMute,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row {
                                            Text(formatCOP(g.saved), fontSize = 13.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                                            Text(" / ${formatCOP(g.target)}", fontSize = 13.5.sp, fontFamily = FontFamily.Monospace, color = MinTextMute, letterSpacing = (-0.3).sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        GoalSheet(
            accounts = accounts,
            onDismiss = { sheetOpen = false },
            onSaved = { sheetOpen = false; loadKey++ },
            existing = sheetGoal,
        )
    }
    }
}

/** Anillo de progreso (saved/target) por tarjeta — versión chica del donut del encabezado. */
@Composable
private fun GoalRing(pct: Float, done: Boolean, size: androidx.compose.ui.unit.Dp) {
    val ringColor = if (done) MinIncome else MinText
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.minDimension / 2 - 4
            val cx = this.size.width / 2
            val cy = this.size.height / 2
            drawArc(
                color = MinHairline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 4f, cap = StrokeCap.Round),
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = pct * 360f,
                useCenter = false,
                style = Stroke(width = 4f, cap = StrokeCap.Round),
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            )
        }
        Text("${(pct * 100).toInt()}%", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
    }
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
