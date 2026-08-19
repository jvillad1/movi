package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
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
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private data class BudgetProgress(
    val budget: Budget,
    val spent: Long,
) {
    val remaining: Long get() = budget.monthlyLimit - spent
    val pctRaw: Float get() = if (budget.monthlyLimit == 0L) 0f else spent.toFloat() / budget.monthlyLimit.toFloat()
    val pct: Int get() = (pctRaw * 100).toInt()
    val state: AlertState get() = when {
        pctRaw >= 1f -> AlertState.OVER
        pctRaw >= 0.80f -> AlertState.WARN
        else -> AlertState.OK
    }
}

private enum class AlertState { OK, WARN, OVER }

/**
 * F16: el encabezado y cada tarjeta necesitan decir "de qué mes" es el gasto — kotlinx-datetime
 * da el [Month] del sistema, pero no en español, así que se mapea a mano. Minúscula porque así
 * va en el encabezado ("Gastado en agosto"), sin punto porque no es abreviatura.
 */
private fun Month.spanishName(): String = when (this) {
    Month.JANUARY -> "enero"
    Month.FEBRUARY -> "febrero"
    Month.MARCH -> "marzo"
    Month.APRIL -> "abril"
    Month.MAY -> "mayo"
    Month.JUNE -> "junio"
    Month.JULY -> "julio"
    Month.AUGUST -> "agosto"
    Month.SEPTEMBER -> "septiembre"
    Month.OCTOBER -> "octubre"
    Month.NOVEMBER -> "noviembre"
    Month.DECEMBER -> "diciembre"
}

private sealed class Sheet {
    data class Edit(val current: Budget) : Sheet()
    data object Add : Sheet()
}

@Composable
fun PresupuestosScreen(onNavigate: (Screen) -> Unit) {
    val goBack = LocalGoBack.current
    var budgets by remember { mutableStateOf<List<Budget>>(emptyList()) }
    var days by remember { mutableStateOf<List<EventDay>>(emptyList()) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    // Ola 2 #6: mismo guard que ya usaba Recurrentes — sin esto el botón ancho de "vacío"
    // parpadeaba un instante antes de que llegaran los presupuestos reales.
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        // F35: de paso, alimenta el caché de "categorías ya usadas" que lee CategoryField —
        // esta pantalla ya carga presupuestos y movimientos, no hace falta un fetch nuevo.
        runCatching { Repositories.wallets.getBudgets() }.onSuccess {
            budgets = it
            UsedCategoriesCache.record(it.map { b -> b.category })
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        reload()
        runCatching { Repositories.wallets.getEventsByDay() }.onSuccess {
            days = it
            UsedCategoriesCache.record(it.flatMap { d -> d.items }.map { ev -> ev.category })
        }
        loading = false
    }

    val progresses = remember(budgets, days) {
        // countsAsCashFlow deja fuera los movimientos de cuentas de deuda. Sin él, un ajuste de
        // saldo de un crédito caía en la categoría "Otros" y ponía en OVER al instante a un
        // presupuesto con ese nombre.
        val spentByCategory = days.flatMap { it.items }
            .filter { it.type == TransactionType.EXPENSE && it.countsAsCashFlow }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        budgets.map { b -> BudgetProgress(b, spentByCategory[b.category] ?: 0L) }
            .sortedByDescending { it.pctRaw }
    }

    // F16: "Gastado del mes" no decía CUÁL mes — el nombre del mes en curso lo hace explícito.
    val monthName = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).month.spanishName()
    }

    val totalLimit = budgets.sumOf { it.monthlyLimit }
    val totalSpent = progresses.sumOf { it.spent }
    val warnCount = progresses.count { it.state == AlertState.WARN }
    val overCount = progresses.count { it.state == AlertState.OVER }

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = MinText,
                    // F22: Presupuestos es una pestaña de primer nivel — Inicio como destino
                    // de reserva si no hay historial (antes caía siempre en Análisis).
                    modifier = Modifier.size(22.dp).clickable { goBack(Screen.Dashboard) },
                )
                // F41: mismo componente que Inicio y Movimientos — Perfil alcanzable desde acá.
                AvatarButton(onClick = { onNavigate(Screen.Profile) })
                Text(
                    text = "Presupuestos",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    modifier = Modifier.weight(1f),
                )
                // F18: compacto arriba a la derecha cuando ya hay presupuestos.
                if (budgets.isNotEmpty()) {
                    NewItemButton(label = "Nuevo presupuesto", onClick = { sheet = Sheet.Add })
                }
            }
            if (budgets.isEmpty() && !loading) {
                NewItemButton(
                    label = "Nuevo presupuesto",
                    onClick = { sheet = Sheet.Add },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp),
                    full = true,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text("Gastado en $monthName", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = formatCOP(totalSpent),
                            fontSize = 36.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MinText,
                            letterSpacing = (-1.4).sp,
                            lineHeight = 36.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "de ${formatCOP(totalLimit)}",
                            fontSize = 13.sp,
                            color = MinTextMute,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (warnCount + overCount > 0) {
                            Spacer(Modifier.height(14.dp))
                            Hairline()
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                if (overCount > 0) {
                                    AlertBadge("Sobrepasados", overCount, MinExpense)
                                }
                                if (warnCount > 0) {
                                    AlertBadge("Cerca del límite", warnCount, MinWarn)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Categorías", count = budgets.size)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            progresses.forEach { p ->
                                BudgetCard(p, onClick = { sheet = Sheet.Edit(p.budget) })
                            }
                        }
                    }
                }
            }

        }

        when (val s = sheet) {
            is Sheet.Edit -> BudgetSheet(
                title = "Editar presupuesto",
                initialCategory = s.current.category,
                categoryEditable = false,
                initialAmount = s.current.monthlyLimit,
                onDismiss = { sheet = null },
                onDelete = {
                    scope.launch {
                        runCatching { Repositories.wallets.deleteBudget(s.current.category) }
                        reload()
                        sheet = null
                    }
                },
                onSave = { _, amt ->
                    scope.launch {
                        runCatching {
                            Repositories.wallets.updateBudget(
                                s.current.category,
                                Budget(s.current.category, amt),
                            )
                        }
                        reload()
                        sheet = null
                    }
                },
            )
            Sheet.Add -> BudgetSheet(
                title = "Nuevo presupuesto",
                initialCategory = "",
                categoryEditable = true,
                initialAmount = 0,
                onDismiss = { sheet = null },
                onDelete = null,
                onSave = { cat, amt ->
                    if (cat.isBlank() || amt <= 0L) return@BudgetSheet
                    scope.launch {
                        runCatching { Repositories.wallets.createBudget(Budget(cat.trim(), amt)) }
                        reload()
                        sheet = null
                    }
                },
            )
            null -> {}
        }
    }
}

@Composable
private fun AlertBadge(label: String, count: Int, color: Color) {
    Column {
        Text(
            text = "$count",
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = color,
            letterSpacing = (-0.4).sp,
        )
        Text(label, fontSize = 11.sp, color = MinTextMute)
    }
}

@Composable
private fun BudgetCard(p: BudgetProgress, onClick: () -> Unit) {
    val barColor = when (p.state) {
        AlertState.OVER -> MinExpense
        AlertState.WARN -> MinWarn
        AlertState.OK -> MinText.copy(alpha = 0.85f)
    }
    val pctColor = when (p.state) {
        AlertState.OVER -> MinExpense
        AlertState.WARN -> MinWarn
        AlertState.OK -> MinTextMute
    }
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = p.budget.category,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.1).sp,
            )
            // F15: el chevron es lo que insinúa que la tarjeta se toca — mismo ícono que usa la
            // guía de primeros pasos del Inicio (ver ChevronRight).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${p.pct}%",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = pctColor,
                    fontWeight = FontWeight.Medium,
                )
                ChevronRight()
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Text(formatCOP(p.spent), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                // F16: "de $2.000.000 este mes" en vez de "/ $2.000.000" — deja explícito que el
                // límite es mensual sin depender solo del texto chico bajo el monto en la hoja.
                Text(" de ${formatCOP(p.budget.monthlyLimit)} este mes", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MinTextMute, letterSpacing = (-0.3).sp)
            }
            val tail = when (p.state) {
                AlertState.OVER -> "Sobrepasado · ${formatCOP(-p.remaining)}"
                AlertState.WARN -> "Cerca del límite"
                AlertState.OK -> "${formatCOP(p.remaining)} disponibles"
            }
            Text(tail, fontSize = 11.sp, color = pctColor)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MinHairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(p.pctRaw.coerceAtMost(1f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun BudgetSheet(
    title: String,
    initialCategory: String,
    categoryEditable: Boolean,
    initialAmount: Long,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, Long) -> Unit,
) {
    var category by remember { mutableStateOf(initialCategory) }
    var amount by remember { mutableStateOf(if (initialAmount > 0L) initialAmount.toString() else "") }

    fun onKey(key: String) {
        amount = when (key) {
            "⌫" -> if (amount.isNotEmpty()) amount.dropLast(1) else amount
            else -> if (amount.length < 12) amount + key else amount
        }
    }

    val parsedAmount = amount.toLongOrNull() ?: 0L
    val canSave = category.isNotBlank() && parsedAmount > 0L
    // F24: mismo patrón que las demás hojas de crear — la primera cosa que falta.
    val missingFieldMessage = when {
        category.isBlank() -> "Falta la categoría"
        parsedAmount <= 0L -> "Falta el monto"
        else -> null
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
            // F37: manija + X para cerrar, mismo componente en las 8 hojas de la app.
            SheetHandleWithClose(onClose = onDismiss)

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )

            // Category
            if (categoryEditable) {
                // F35: crear presupuesto — campo libre con sugerencias en vez de texto libre a
                // ciegas. Solo EXPENSE: no tiene sentido presupuestar una categoría de ingreso.
                CategoryField(
                    value = category,
                    onValueChange = { category = it },
                    type = TransactionType.EXPENSE,
                    usedCategories = UsedCategoriesCache.categories,
                    label = "Categoría",
                    placeholder = "Mercado, Salud, Restaurantes…",
                )
            } else {
                // Al editar un presupuesto existente la categoría es su clave — no se cambia acá.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Categoría", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = category,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinText,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Amount display
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    // F14: separador de miles mientras se escribe, no solo al guardar.
                    text = "$" + formatAmountKeypadDisplay(amount),
                    fontSize = 48.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    color = MinText,
                    letterSpacing = (-1.8).sp,
                    lineHeight = 48.sp,
                )
                Spacer(Modifier.height(6.dp))
                // F16: decía "Límite mensual · COP" — la moneda ya es obvia en toda la app, pero
                // que se reinicia cada mes no, y es la pregunta real ("¿por cuánto tiempo?").
                Text("Límite mensual · se reinicia cada mes", fontSize = 12.sp, color = MinTextMute, letterSpacing = 0.4.sp)
            }

            Spacer(Modifier.height(14.dp))

            // Numpad
            Column {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("000", "0", "⌫"),
                ).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clickable { onKey(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (key == "⌫") {
                                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Borrar", tint = MinText, modifier = Modifier.size(20.dp))
                                } else {
                                    Text(
                                        text = key,
                                        fontSize = 20.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Normal,
                                        color = MinText,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onDelete != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, MinBorder, RoundedCornerShape(999.dp))
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Eliminar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinExpense,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(if (onDelete != null) 1.4f else 1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canSave) MinPrimaryContainer else MinSurfaceContainerLow)
                        // Ola 2 #2: recorte al guardar — canSave ya exige no-vacío, pero
                        // "  Comida  " pasaba esa guarda y se guardaba con espacios.
                        .clickable(enabled = canSave) { onSave(category.trim(), parsedAmount) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Guardar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (canSave) MinOnPrimaryContainer else MinTextDim,
                    )
                }
            }
            if (!canSave && missingFieldMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = missingFieldMessage,
                    fontSize = 12.sp,
                    color = MinTextMute,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
