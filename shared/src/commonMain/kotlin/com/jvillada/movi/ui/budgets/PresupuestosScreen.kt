package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.dashboard.currentMonthPrefixApp
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.dashboard.spentByCategoryForPeriod
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.ventanaDe
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Month
import kotlinx.datetime.toLocalDateTime
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.shared.model.FinancialEvent
import androidx.compose.runtime.rememberCoroutineScope

/** `internal` y no `private` para poder probar [estadoDelPresupuesto] — ver EstadoDelPresupuestoTest. */
internal data class BudgetProgress(
    val budget: Budget,
    val spent: Long,
) {
    val remaining: Long get() = budget.monthlyLimit - spent
    val pctRaw: Float get() = if (budget.monthlyLimit == 0L) 0f else spent.toFloat() / budget.monthlyLimit.toFloat()
    /**
     * El porcentaje se calcula con enteros, no con el [pctRaw] de arriba.
     *
     * `Float` tiene 24 bits de mantisa: a partir de 16.777.216 ya no puede representar todos los
     * enteros. Con los montos del dueño —una hipoteca de 767.800.000— la división pierde
     * precisión, así que un porcentaje sacado de ahí puede estar mal por más de un punto. En pesos
     * colombianos eso no es una hipótesis de laboratorio: es el orden de magnitud normal.
     */
    val pct: Int get() = if (budget.monthlyLimit == 0L) 0 else (spent * 100 / budget.monthlyLimit).toInt()
    val state: AlertState get() = estadoDelPresupuesto(spent, budget.monthlyLimit)
}

internal enum class AlertState { OK, WARN, AL_LIMITE, OVER }

/**
 * En qué estado está un presupuesto.
 *
 * ### «Sobrepasado · $0»
 *
 * El dueño, mirando su presupuesto de Mercado en $2.000.000 de $2.000.000: *«marca sobrepasado
 * Mercado pero está al 100%, eso es un error, ¿no?»*. Lo era, y el propio rótulo lo delataba —
 * si el exceso es cero, no hay exceso.
 *
 * La condición era `pctRaw >= 1f`, que mete el empate del lado equivocado. Gastar exactamente el
 * límite no es pasarse: es quedarse sin margen, que es otra cosa y merece decirse distinto. Por
 * eso [AL_LIMITE] existe en vez de mandar el 100 % a [WARN]: «cerca del límite» sería igual de
 * falso, al revés — no estás cerca, estás justo ahí.
 *
 * ### Por qué con enteros
 *
 * La comparación vieja iba contra un `Float`, y `Float` tiene 24 bits de mantisa: a partir de
 * 16.777.216 deja de representar todos los enteros. Con los montos de este dueño (hipotecas de
 * cientos de millones) `gastado.toFloat() / limite.toFloat()` puede dar exactamente `1.0` cuando
 * el gasto supera al límite por unos pesos, y también al revés. Comparar los `Long` es exacto y
 * cuesta lo mismo.
 *
 * Un límite en cero no es «sobrepasado» aunque haya gasto: es un presupuesto sin configurar, y
 * dividir por él tampoco tiene sentido.
 */
internal fun estadoDelPresupuesto(gastado: Long, limite: Long): AlertState = when {
    limite <= 0L -> AlertState.OK
    gastado > limite -> AlertState.OVER
    gastado == limite -> AlertState.AL_LIMITE
    // 80 % con enteros: `gastado * 100 >= limite * 80` es lo mismo que `gastado / limite >= 0.8`
    // sin pasar por punto flotante.
    gastado * 100 >= limite * 80 -> AlertState.WARN
    else -> AlertState.OK
}

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
    var budgets by remember { mutableStateOf<List<Budget>>(emptyList()) }
    var cutoffDay by remember { mutableStateOf(1) }
    var days by remember { mutableStateOf<List<EventDay>>(emptyList()) }
    // Se incrementa al asociar un gasto, para volver a leer con el movimiento ya movido.
    var refreshKeyLocal by remember { mutableStateOf(0) }
    // Gasto del mes por categoría según el server (la misma fuente que el Inicio); null hasta
    // que llegue o si no hay red — ver `progresses`.
    var serverSpent by remember { mutableStateOf<Map<String, Long>?>(null) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    // Error de guardar/renombrar que la hoja tiene que mostrar (409 del server, red).
    var sheetError by remember { mutableStateOf<String?>(null) }
    // Ola 2 #6: mismo guard que ya usaba Recurrentes — sin esto el botón ancho de "vacío"
    // parpadeaba un instante antes de que llegaran los presupuestos reales.
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        // F35: de paso, alimenta el caché de "categorías ya usadas" que lee CategoryField —
        // esta pantalla ya carga presupuestos y movimientos, no hace falta un fetch nuevo.
        runCatching { Repositories.wallets.getBudgets() }.onSuccess {
            budgets = it
            // Ola 9 · A3: un presupuesto es, por definición, un límite de GASTO — así que sus
            // categorías se anotan con ese tipo y no como "no se sabe".
            UsedCategoriesCache.recordAll(it.map { b -> b.category to TransactionType.EXPENSE })
        }
    }

    val refreshTick = LocalRefreshTick.current
    // `refreshTick` y no `Unit`: con Unit esta pantalla no recargaba NUNCA mientras estuviera
    // compuesta, y desde que Agregar es una modal se puede registrar un gasto parado acá y ver
    // la barra del presupuesto sin moverse. Ver [LocalRefreshTick].
    LaunchedEffect(refreshTick, refreshKeyLocal) {
        loading = true
        reload()
        runCatching { Repositories.wallets.getEventsByDay() }.onSuccess {
            days = it
            // Ola 9 · A3: con el tipo de cada movimiento, así una categoría propia se ofrece
            // del lado en que de verdad se usó.
            UsedCategoriesCache.recordAll(it.flatMap { d -> d.items }.map { ev -> ev.category to ev.type })
        }
        // Misma cifra que el Inicio: el server suma con TODO lo que sabe (todos los dispositivos,
        // SMS, importaciones; anulados fuera). En el teléfono `getEventsByDay` es local y solo
        // conoce lo de este aparato, así que el Inicio podía decir «Comida superado» y esta
        // pantalla no. Si falla (sin red) queda el cálculo local de abajo como fallback.
        runCatching { Repositories.wallets.getDashboardSummary(Scope.SELF) }.onSuccess { serverSpent = it.spentByCategory }
        // El corte del período: define qué ventana usa el cálculo local de respaldo. Si falla,
        // queda en 1 —mes de calendario— que es el comportamiento de siempre.
        runCatching { Repositories.wallets.getUserProfile() }.onSuccess { cutoffDay = it.periodCutoffDay }
        loading = false
    }

    // El gasto del período por categoría, una sola vez: lo usan las barras de progreso y también
    // el aviso de la hoja de crear (ver [avisoDeCategoria]). Calcularlo en dos lados abriría la
    // puerta a que la pantalla y su hoja dijeran cifras distintas.
    // El período del usuario, no el mes de calendario. `serverSpent` ya viene calculado con la
    // ventana correcta (el server usa `currentPeriodWindow`); el cálculo local es el respaldo y
    // tiene que usar la MISMA ventana o las dos mitades de la app dirían cifras distintas.
    val alcance = rememberCoroutineScope()
    // Mover un movimiento a la categoría del presupuesto. Va acá y no en la hoja porque después
    // hay que recargar la lista: el gasto recién asociado tiene que aparecer contado.
    fun asociarGasto(evento: FinancialEvent, categoria: String) {
        alcance.launch {
            runCatching { Repositories.wallets.updateEventCategory(evento.id, categoria) }
                .onSuccess { refreshKeyLocal++ }
                .onFailure { sheetError = it.toUserMessage() }
        }
    }

    val ventanaDelPeriodo = remember(cutoffDay) {
        val settings = PeriodSettings(cutoffDay = cutoffDay)
        ventanaDe(periodoDe(kotlinx.datetime.Clock.System.now().toEpochMilliseconds(), settings), settings)
    }
    val gastoPorCategoria = remember(days, serverSpent, ventanaDelPeriodo) {
        serverSpent ?: spentByCategoryForPeriod(days, ventanaDelPeriodo)
    }

    val progresses = remember(budgets, gastoPorCategoria) {
        // countsAsCashFlow deja fuera los movimientos de cuentas de deuda. Sin él, un ajuste de
        // saldo de un crédito caía en la categoría "Otros" y ponía en OVER al instante a un
        // presupuesto con ese nombre.
        // Misma regla que el acceso «Presupuestos» del Inicio y que la alerta de sobrepasado
        // (spentByCategoryForMonth): solo el mes en curso y solo COP. Antes esta pantalla sumaba
        // TODO el historial mientras el encabezado decía «Gastado en agosto» — el Inicio y
        // Presupuestos daban cifras distintas para el mismo presupuesto.
        budgets.map { b -> BudgetProgress(b, gastoPorCategoria[b.category] ?: 0L) }
            .sortedByDescending { it.pctRaw }
    }

    // F16: "Gastado del mes" no decía CUÁL mes — el nombre del mes en curso lo hace explícito.
    // Misma zona que el prefijo del mes (AppTimeZone): el título y la suma no pueden discrepar.
    val monthName = remember {
        Clock.System.now().toLocalDateTime(AppTimeZone.zone).month.spanishName()
    }

    val totalLimit = budgets.sumOf { it.monthlyLimit }
    val totalSpent = progresses.sumOf { it.spent }
    // «Al límite» cuenta como aviso, no como sobrepasado: el encabezado decía «2 Sobrepasados»
    // con uno de los dos exactamente en el límite.
    val warnCount = progresses.count { it.state == AlertState.WARN || it.state == AlertState.AL_LIMITE }
    val overCount = progresses.count { it.state == AlertState.OVER }

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // F60: encabezado único — avatar en ancho (Presupuestos está en el rail), flecha a
            // Más en el teléfono (se llega por Más). Con presupuestos ya creados, el alta
            // compacta a la derecha (F18).
            MinScreenHeader(
                title = "Presupuestos",
                leading = leadingFor(Screen.Budgets, onProfile = { onNavigate(Screen.Profile) }, fallback = Screen.Mas),
                action = if (budgets.isNotEmpty()) {
                    { NewItemButton(label = "Nuevo presupuesto", onClick = { sheet = Sheet.Add }) }
                } else null,
            )
            if (budgets.isEmpty() && !loading) {
                NewItemButton(
                    label = "Nuevo presupuesto",
                    onClick = { sheet = Sheet.Add },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(vertical = 14.dp),
                    full = true,
                )
            } else {
                Spacer(Modifier.height(14.dp))
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
                error = sheetError,
                title = "Editar presupuesto",
                gastoPorCategoria = gastoPorCategoria,
                dias = days,
                ventana = ventanaDelPeriodo,
                onAsociar = ::asociarGasto,
                initialCategory = s.current.category,
                // F17: la categoría dejó de ser de solo lectura — antes era una limitación
                // técnica filtrada a la pantalla (la categoría es la PK en el server), ahora
                // PUT /api/budgets/{category}/rename la resuelve del lado del servidor.
                categoryEditable = true,
                initialAmount = s.current.monthlyLimit,
                onDismiss = { sheet = null; sheetError = null },
                onDelete = {
                    scope.launch {
                        runCatching { Repositories.wallets.deleteBudget(s.current.category) }
                        reload()
                        sheet = null
                    }
                },
                onSave = { cat, amt ->
                    scope.launch {
                        val result = runCatching {
                            // F17: renombrar y cambiar el monto son dos llamadas separadas
                            // porque son dos endpoints separados — rename conserva el límite
                            // viejo, así que si además cambió el monto hay que pisarlo después.
                            val renamed = cat != s.current.category
                            val finalCategory = if (renamed) {
                                Repositories.wallets.renameBudget(s.current.category, cat).category
                            } else {
                                s.current.category
                            }
                            if (!renamed || amt != s.current.monthlyLimit) {
                                Repositories.wallets.updateBudget(finalCategory, Budget(finalCategory, amt))
                            }
                        }
                        // El 409 del server («Ya existe un presupuesto llamado…») tiene que
                        // llegarle a la persona: cerrar la hoja en silencio era decirle que se
                        // guardó cuando no. La hoja queda abierta con el mensaje; reintenta o cierra.
                        result.onSuccess { reload(); sheet = null }
                            .onFailure { sheetError = it.toUserMessage() }
                    }
                },
            )
            Sheet.Add -> BudgetSheet(
                error = sheetError,
                title = "Nuevo presupuesto",
                initialCategory = "",
                categoryEditable = true,
                initialAmount = 0,
                gastoPorCategoria = gastoPorCategoria,
                dias = days,
                ventana = ventanaDelPeriodo,
                onAsociar = ::asociarGasto,
                onDismiss = { sheet = null; sheetError = null },
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
        AlertState.AL_LIMITE -> MinWarn
        AlertState.WARN -> MinWarn
        AlertState.OK -> MinText.copy(alpha = 0.85f)
    }
    val pctColor = when (p.state) {
        AlertState.OVER -> MinExpense
        AlertState.AL_LIMITE -> MinWarn
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
                // Ni «sobrepasado» (no se pasó) ni «cerca» (no está cerca, está justo ahí).
                AlertState.AL_LIMITE -> "Sin margen · gastaste justo el límite"
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
    /** Gasto del período por categoría, para poder decir la verdad antes de guardar. */
    gastoPorCategoria: Map<String, Long>,
    /** Los días del período, para poder ofrecer los movimientos que se llaman como la categoría. */
    dias: List<EventDay>,
    ventana: LongRange,
    onAsociar: (FinancialEvent, String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, Long) -> Unit,
    error: String? = null,
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
                // Esta hoja NO tenía scroll, y con la lista de movimientos deja de alcanzar: bajo
                // ella van el teclado (192 dp) y «Guardar»/«Eliminar», así que con tres
                // movimientos los botones caían fuera de la pantalla, recortados por el `clip` de
                // la propia hoja — el presupuesto quedaba imposible de editar. Y la lista existe
                // justamente para las categorías con muchos movimientos. Lo midió la revisión
                // antes de que llegara a producción.
                .verticalScroll(rememberScrollState())
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
                // F35/F17: campo libre con sugerencias en vez de texto libre a ciegas — el
                // mismo campo sirve para crear (categoría nueva) y para editar (renombrar).
                // Solo EXPENSE: no tiene sentido presupuestar una categoría de ingreso.
                CategoryField(
                    value = category,
                    onValueChange = { category = it },
                    type = TransactionType.EXPENSE,
                    usedCategories = UsedCategoriesCache.used,
                    prefs = UsedCategoriesCache.prefs,
                    label = "Categoría",
                    placeholder = "Mercado, Salud, Restaurantes…",
                    // Desde que esta hoja se desplaza, el tope de 220 dp del panel sería un
                    // scroll adentro de otro scroll — el defecto que el dueño reportó con
                    // «cuando quiero ver las categorías, al hacer scroll desaparecen». Ver el
                    // KDoc de `maxSuggestionsHeight`.
                    maxSuggestionsHeight = null,
                )
                // F17: onDelete solo viene no-nulo al editar un presupuesto EXISTENTE (Sheet.Add
                // lo manda null) — ahí es donde "cambiar el nombre" significa renombrar una
                // categoría que ya tiene gasto acumulado, así que solo ahí hace falta la
                // advertencia. El cruce presupuesto↔gasto es por NOMBRE de categoría
                // (spentByCategoryForMonth), no por un id estable — renombrar corta ese cruce
                // para los movimientos viejos.
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "El gasto se cruza por nombre: si renombras \"$initialCategory\" a otra cosa, " +
                            "los movimientos que digan \"$initialCategory\" dejan de contar aquí.",
                        fontSize = 11.5.sp,
                        color = MinTextMute,
                        lineHeight = 15.sp,
                    )
                }
                // La verdad sobre la categoría escrita, ANTES de guardar. El dueño creó un
                // presupuesto en «Mercado» —que es la descripción de su gasto, no su categoría—
                // y la app lo dejó crear algo que no vigilaba nada, en silencio. Ver
                // [avisoDeCategoria].
                avisoDeCategoria(category, gastoPorCategoria, ::formatCOP)?.let { aviso ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = aviso.texto,
                        fontSize = 11.5.sp,
                        color = if (aviso.esAdvertencia) MinWarn else MinTextMute,
                        lineHeight = 15.sp,
                    )
                    // Los movimientos del período que se LLAMAN como la categoría pero están en
                    // otra. Es la otra mitad de lo que el dueño pidió: el aviso le dice qué
                    // categorías tienen gasto, y esto le deja traer el gasto a la categoría que
                    // eligió vigilar. Ver [gastosQueSuenanA].
                    val candidatos = gastosQueSuenanA(category, dias, ventana)
                    if (candidatos.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        // El texto dice CÓMO coincidió, no solo que coincidió: «llamado» cuando
                        // la descripción es el nombre de la categoría, «que mencionan» cuando
                        // apenas la nombra («Mercado Éxito»). Si el dueño no puede ver por qué se
                        // lo proponemos, no puede desconfiar de la propuesta.
                        val soloExactas = candidatos.all { it.coincidencia == Coincidencia.EXACTA }
                        Text(
                            text = when {
                                candidatos.size == 1 && soloExactas ->
                                    "Tienes un movimiento llamado \"${category.trim()}\" en otra categoría:"
                                candidatos.size == 1 ->
                                    "Tienes un movimiento que menciona \"${category.trim()}\" en otra categoría:"
                                soloExactas ->
                                    "Tienes ${candidatos.size} movimientos llamados \"${category.trim()}\" en otras categorías:"
                                else ->
                                    "Tienes ${candidatos.size} movimientos que mencionan \"${category.trim()}\" en otras categorías:"
                            },
                            fontSize = 11.5.sp,
                            color = MinTextMute,
                            lineHeight = 16.sp,
                        )
                        candidatos.take(3).forEach { (ev, _) ->
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onAsociar(ev, category.trim()) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ev.description, fontSize = 13.sp, color = MinText, fontWeight = FontWeight.Medium)
                                    Text("Hoy en \"${ev.category}\" · toca para moverlo aquí", fontSize = 11.sp, color = MinTextMute)
                                }
                                Text(formatCOP(ev.amount), fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MinText)
                            }
                        }
                    }
                    if (aviso.sugerencias.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            aviso.sugerencias.forEach { sugerida ->
                                Text(
                                    text = sugerida,
                                    fontSize = 12.sp,
                                    color = MinPrimary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { category = sugerida }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
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

            // El dueño: «En presupuestos yo debería ver cada uno de los movimientos asociados a
            // ese presupuesto». Había una barra y dos números, sin manera de contestar la
            // pregunta que sigue: ¿en qué? Un presupuesto excedido sin la lista es una acusación
            // sin pruebas — no se distingue un gasto mal archivado de uno real.
            //
            // Va FUERA del `if (categoryEditable)`. La primera versión de esto quedó dentro del
            // `else`, que **no se dibuja nunca**: los dos call sites (Sheet.Add y Sheet.Edit)
            // pasan `categoryEditable = true`. La feature compilaba, su test pasaba —prueba la
            // función pura, no la pantalla— y en la app no aparecía nada. Lo encontró la
            // revisión. Ese `else` es deuda anterior: la rama «al editar la categoría es su
            // clave» quedó inalcanzable cuando editar pasó a permitir renombrar.
            //
            // Sirve igual al crear: escribir «Mercado» y ver ahí mismo lo que ya se gastó es
            // justo lo que hace falta para elegir el monto.
            val movimientos = gastosDelPresupuesto(category, dias, ventana)
            // Se calcula FUERA del `if`: el caso en que este mensaje más falta hace es
            // justamente cuando no hay nada que listar —la barra dice \$2.000.000 y este
            // dispositivo no bajó ni un movimiento— y ahí un bloque vacío sin explicación es
            // peor que el problema que la lista vino a resolver.
            val faltante = faltanMovimientosPorVer(
                gastoPorCategoria[category.trim()] ?: 0L,
                movimientos.sumOf { it.amount },
            )
            if (faltante != 0L && movimientos.isEmpty() && category.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Hay ${formatCOP(faltante)} contados en esta categoría que este dispositivo todavía no bajó.",
                    fontSize = 11.5.sp,
                    color = MinTextMute,
                    lineHeight = 15.sp,
                )
            }
            if (movimientos.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (movimientos.size == 1) "1 movimiento" else "${movimientos.size} movimientos",
                        fontSize = 11.sp,
                        color = MinTextMute,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatCOP(movimientos.sumOf { it.amount }),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MinTextMute,
                    )
                }
                // Si el server contó plata que este aparato todavía no bajó, se dice — no se
                // deja que el dueño reste dos números y desconfíe de los dos.
                if (faltante != 0L) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (faltante > 0L)
                            "Hay ${formatCOP(faltante)} más contados en esta categoría que todavía no bajaron a este dispositivo."
                        else
                            "Este dispositivo tiene ${formatCOP(-faltante)} que el total de arriba todavía no cuenta.",
                        fontSize = 11.sp,
                        color = MinTextMute,
                        lineHeight = 15.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                movimientos.forEach { ev ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ev.description, fontSize = 13.5.sp, color = MinText)
                            Text(etiquetaDeFecha(fechaDeEpoch(ev.timestamp), hoyEnAppZone()), fontSize = 11.sp, color = MinTextMute)
                        }
                        Text(
                            text = formatCOP(ev.amount),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MinText,
                        )
                    }
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

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MinExpense)
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
