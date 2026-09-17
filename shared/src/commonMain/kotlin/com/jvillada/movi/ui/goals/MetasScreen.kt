package com.jvillada.movi.ui.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun MetasScreen(onNavigate: (Screen) -> Unit) {
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    // loadKey incrementa tras cada crear/editar/borrar para forzar la recarga.
    var loadKey by remember { mutableStateOf(0) }

    // Estado de la hoja: null = cerrada; no-null = abierta, con la meta a editar (o null = alta).
    var sheetGoal by remember { mutableStateOf<Goal?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    // Ver [NoSePudoLeer]: «Aún no hay metas» solo si la lectura contestó.
    var metasLeidas by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    // `refreshTick`: la hoja de Agregar es una modal que se abre encima de Metas, y un ingreso a
    // la cuenta de una meta mueve su «ahorrado». Sin esto la cifra quedaba vieja hasta salir y
    // volver. Mismo mecanismo que Cuentas, Créditos y Presupuestos. Ver [LocalRefreshTick].
    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(loadKey, refreshTick) {
        cargando = true
        runCatching { Repositories.wallets.getGoals() }
            .onSuccess { goals = it; metasLeidas = true }
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it }
        cargando = false
    }
    val noSeLeyo = !cargando && !metasLeidas
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60: encabezado único — Metas se abre desde Más (flecha, F22) y lleva el mismo
        // rótulo que su acceso («Metas»); el alta compacta a la derecha cuando ya hay (F26).
        MinScreenHeader(
            title = "Metas",
            leading = HeaderLeading.Back(fallback = Screen.Mas),
            action = if (goals.isNotEmpty() && !noSeLeyo) {
                { NewItemButton(label = "Nueva meta", onClick = { sheetGoal = null; sheetOpen = true }) }
            } else null,
        )
        if (noSeLeyo) {
            Spacer(Modifier.height(14.dp))
            NoSePudoLeer(
                "No pudimos cargar tus metas",
                onReintentar = { loadKey++ },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else if (goals.isEmpty() && !cargando) {
            NewItemButton(
                label = "Nueva meta",
                onClick = { sheetGoal = null; sheetOpen = true },
                modifier = Modifier.padding(horizontal = 20.dp).padding(vertical = 14.dp),
                full = true,
            )
        }

        // Ver [ResumenDeMetas]: dos metas sobre la misma cuenta veían el mismo saldo, y el
        // encabezado lo sumaba dos veces. Acá cada cuenta cuenta una vez.
        val resumen    = resumenDeMetas(goals)
        val overallPct = resumen.porcentaje
        val pctLabel   = "${(overallPct * 100).toInt()}%"

        if (!noSeLeyo) LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        // Donut
                        // El `Canvas` de abajo DIBUJA, no compone: su lambda no es contexto
                        // componible y no puede leer los tokens. Se leen acá, una vez por
                        // composición en vez de una por cuadro.
                        val colorDelRiel = Movi.colores.hilo
                        val colorDelArco = Movi.colores.texto
                        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(72.dp)) {
                                val r = size.minDimension / 2 - 6
                                val cx = size.width / 2
                                val cy = size.height / 2
                                drawArc(
                                    color = colorDelRiel,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                                    topLeft = Offset(cx - r, cy - r),
                                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                                )
                                drawArc(
                                    color = colorDelArco,
                                    startAngle = -90f,
                                    sweepAngle = overallPct * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                                    topLeft = Offset(cx - r, cy - r),
                                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                                )
                            }
                            Text(pctLabel, style = Movi.textos.monto, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total ahorrado", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                            Spacer(Modifier.height(6.dp))
                            Text(formatCOP(resumen.ahorrado), fontSize = 22.sp, style = Movi.textos.monto, color = Movi.colores.texto, letterSpacing = (-0.7).sp)
                            Text("de ${formatCOP(resumen.objetivo)} · ${resumen.rotuloDeCantidad}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.padding(top = 4.dp))
                            if (resumen.hayCuentasCompartidas) {
                                // Sin esta línea el total parece más chico de lo que el dueño
                                // esperaría al sumar las metas de a una, y no se entiende por qué.
                                Text(
                                    "Hay metas que comparten cuenta: esa plata se cuenta una sola vez.",
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoMedio,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
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
                            Text("Aún no hay metas de ahorro", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        goals.forEach { g ->
                            val pct = if (g.target > 0) (g.saved.toFloat() / g.target.toFloat()).coerceIn(0f, 1f) else 0f
                            // El anillo sigue midiendo el saldo de la cuenta contra ESTA meta —
                            // es lo que la tarjeta quiere decir. Lo que no se puede es cantar
                            // «completada» con plata que otra meta ya reclamó: si dos metas
                            // están sobre la misma cuenta, la tarjeta dice que la comparten.
                            val comparte = resumen.comparteCuenta(g)
                            val done = g.target > 0 && g.saved >= g.target && !comparte
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
                                            Text(g.name, style = Movi.textos.titulo, color = Movi.colores.texto)
                                            if (done) {
                                                Text("COMPLETADA", style = Movi.textos.rotulo, color = Movi.colores.entra)
                                            } else if (comparte) {
                                                Text("COMPARTE CUENTA", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                                            }
                                        }
                                        // F26: la fecha objetivo es opcional — sin ella no se
                                        // inventa un texto de relleno.
                                        Text(
                                            g.targetDate?.let { "Meta para el $it" } ?: "Sin fecha objetivo",
                                            style = Movi.textos.apoyo,
                                            color = Movi.colores.textoMedio,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row {
                                            Text(formatCOP(g.saved), style = Movi.textos.monto, fontWeight = FontWeight.Medium, color = Movi.colores.texto, letterSpacing = (-0.3).sp)
                                            Text(" / ${formatCOP(g.target)}", style = Movi.textos.monto, color = Movi.colores.textoMedio, letterSpacing = (-0.3).sp)
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
    val ringColor = if (done) Movi.colores.entra else Movi.colores.texto
    val colorDelRiel = Movi.colores.hilo
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.minDimension / 2 - 4
            val cx = this.size.width / 2
            val cy = this.size.height / 2
            drawArc(
                color = colorDelRiel,
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
        Text("${(pct * 100).toInt()}%", style = Movi.textos.monto, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
    }
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
