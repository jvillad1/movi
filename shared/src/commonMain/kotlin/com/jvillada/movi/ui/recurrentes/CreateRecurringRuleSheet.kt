package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CreateSubscriptionRequest
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.CategoryField
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch

/**
 * La ÚNICA hoja para anotar algo que se repite todos los meses (Ola 8).
 *
 * Antes había dos —«Nuevo recurrente» y «Nueva suscripción»— y para elegir entre ellas el dueño
 * tenía que saber una distinción que solo existe adentro de Movi. Ahora hay una sola, y la
 * pregunta que decide dónde se guarda es una del mundo real, no del modelo de datos: **en qué
 * moneda te lo cobran**.
 *
 * - **En pesos** → una regla recurrente: ingreso o gasto, con categoría y con recordatorio.
 * - **En dólares** → una suscripción: las reglas recurrentes son COP puro (`RecurringRule` no
 *   tiene campo de moneda) y agregarle uno tocaría el modelo, la tabla, el barrido de avisos y
 *   los totales. El modelo de suscripciones YA es multi-moneda y ya convierte con la TRM del
 *   día (ver `resultFor` en `SubscriptionRoutes.kt`), así que un cobro en dólares va ahí.
 *
 * Lo que el dueño pierde al elegir dólares (categoría y recordatorio) se dice en la hoja, en
 * lugar de que los campos desaparezcan sin explicación.
 *
 * Editar es siempre editar una [RecurringRule] (las suscripciones no tienen hoja de edición,
 * se quitan desde la lista), así que en modo edición la moneda ni se muestra.
 */
@Composable
fun CreateRecurringRuleSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    existing: RecurringRule? = null,
    /**
     * Ola 9 · B: el formulario llega lleno con lo que el dueño acaba de anotar (nombre, monto,
     * categoría, tipo, cuenta y el día del mes tomado de la fecha del movimiento). **Todo se
     * puede corregir antes de guardar**: es un formulario prellenado, no una confirmación.
     */
    prefill: RecurringPrefill? = null,
) {
    val coroutine = rememberCoroutineScope()

    // Prefill state from existing rule when in edit mode
    var name by remember { mutableStateOf(existing?.name ?: prefill?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.amount ?: prefill?.amount) }
    var dayOfMonth by remember {
        mutableStateOf(existing?.dayOfMonth?.toString() ?: prefill?.dayOfMonth?.toString() ?: "")
    }
    var selectedType by remember {
        mutableStateOf(existing?.type ?: prefill?.type ?: TransactionType.EXPENSE)
    }
    var category by remember { mutableStateOf(existing?.category ?: prefill?.category ?: "Otros") }
    // Ola 9 · D: ¿la categoría la eligió una persona? Con una regla que ya existe o con un
    // movimiento que la trae, sí — y entonces la sugerencia por nombre no la toca. Ver
    // [categoriaSugeridaPorNombre].
    var categoriaElegidaAMano by remember { mutableStateOf(existing != null || prefill != null) }
    // Ola 9 · D: a qué cuenta entra o de cuál sale. Opcional siempre (ver RecurringRule.accountId).
    var accountId by remember { mutableStateOf(existing?.accountId ?: prefill?.accountId) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var accountPickerOpen by remember { mutableStateOf(false) }
    // Marcada por defecto al crear; al editar refleja lo que está guardado.
    var remindMe by remember { mutableStateOf(existing?.remindMe ?: true) }
    var currency by remember { mutableStateOf("COP") }
    // ¿Elegir dólares le cambió al dueño un «Ingreso» que ya había marcado? (V11: la regla se
    // explica igual, pero si además le pisamos una elección suya, eso se avisa aparte.)
    var tipoCambiadoPorLaMoneda by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Una sola llamada, y solo cuando el dueño abrió esta hoja: no hay lista de cuentas
    // cacheada en `:shared` y el selector necesita los nombres. Si falla, el selector queda
    // vacío y la regla se guarda igual sin cuenta — que es un estado legítimo del modelo.
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { list ->
                accounts = list
                if (accountId != null && list.none { it.id == accountId }) accountId = null
            }
    }

    // Ola 9 · D: si el nombre coincide con una categoría del catálogo, se propone esa en vez de
    // dejar el genérico «Otros». Solo mientras nadie haya tocado la categoría a mano.
    LaunchedEffect(name, selectedType) {
        if (categoriaElegidaAMano) return@LaunchedEffect
        categoriaSugeridaPorNombre(name, selectedType)?.let { category = it }
    }

    val isEditMode = existing != null
    // Editar es editar una regla; solo al crear se puede elegir dólares (ver KDoc).
    val enDolares = !isEditMode && currency == "USD"
    val canSave = name.isNotBlank() && (amount ?: 0L) > 0L && dayOfMonth.toIntOrNull() in 1..31 && !saving
    // F24: mismo patrón que las demás hojas de crear — la primera cosa que falta, no un botón
    // gris sin explicación.
    val missingFieldMessage = when {
        name.isBlank() -> "Falta el nombre"
        (amount ?: 0L) <= 0L -> "Falta el monto"
        dayOfMonth.toIntOrNull() !in 1..31 -> "El día del mes tiene que estar entre 1 y 31"
        else -> null
    }

    fun save() {
        if (!canSave) return
        val day = dayOfMonth.toIntOrNull()?.coerceIn(1, 31) ?: return
        val amt = amount ?: return
        saving = true
        error = null
        coroutine.launch {
            val result = when {
                // Dólares → suscripción (el único modelo multi-moneda que hay). Nace CONFIRMED
                // del lado del server: la escribió el dueño, no hay nada que confirmar.
                enDolares -> runCatching {
                    Repositories.wallets.createSubscription(
                        CreateSubscriptionRequest(
                            displayName = name.trim(),
                            amount = amt,
                            currency = currency,
                            dayOfMonth = day,
                        )
                    )
                }
                else -> {
                    val rule = RecurringRule(
                        id = existing?.id ?: "",
                        name = name.trim(),
                        category = category.trim().ifBlank { "Otros" },
                        amount = amt,
                        dayOfMonth = day,
                        type = selectedType,
                        // Un INGRESO nunca entra al barrido de avisos (`selectDueForReminder`
                        // solo mira gastos) y la hoja ni siquiera ofrece la casilla, así que
                        // guardar `true` dejaba en la fila una intención que nadie expresó — y
                        // que se leía como «pedí que me avisaran» desde cualquier consulta.
                        //
                        // Cambiar de tipo DENTRO de la hoja no pierde nada: `remindMe` es estado
                        // de la hoja y nadie lo toca al mover el chip, así que ir a Ingreso y
                        // volver a Gasto deja la casilla como estaba. Lo único que se pierde es
                        // al GUARDAR como ingreso: ahí queda `false` en la fila, y si más
                        // adelante se reabre y se pasa a Gasto, la casilla arranca desmarcada.
                        // Es un camino angosto y el precio de que la base no mienta.
                        remindMe = selectedType == TransactionType.EXPENSE && remindMe,
                        // Ola 9 · D. Puede ser null: la cuenta nunca es obligatoria.
                        accountId = accountId,
                    )
                    if (isEditMode) {
                        runCatching { Repositories.wallets.updateRecurringRule(existing!!.id, rule) }
                    } else {
                        runCatching { Repositories.wallets.createRecurringRule(rule) }
                    }
                }
            }
            saving = false
            result.onSuccess { onSaved() }
                .onFailure { error = it.toUserMessage() }
        }
    }

    fun delete() {
        if (existing == null || saving) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.deleteRecurringRule(existing.id) }
            saving = false
            result.onSuccess { onSaved() }
                .onFailure { error = it.toUserMessage() }
        }
    }

    // La hoja crecía sin techo y el contenido que no entraba quedaba recortado, sin scroll: en
    // una laptop (1280×860) el caso más común —un GASTO, que suma las secciones de categoría y
    // recordatorio— dejaba «Crear recurrente» debajo del borde y el recurrente era, lisa y
    // llanamente, imposible de crear. Ahora la hoja se topa en el 92% de la altura disponible,
    // los campos scrollean, y la manija y el botón quedan FIJOS: el botón es siempre alcanzable
    // sin depender de que el usuario descubra que hay que rodar.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val altoMaximoDeLaHoja = maxHeight * 0.92f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(enabled = !saving, onClick = onDismiss),
        ) {
            Box(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = altoMaximoDeLaHoja)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MinSurfaceContainerHigh)
                    .padding(horizontal = 20.dp)
                    .clickable(enabled = false) {},
            ) {
                // F37: manija + X para cerrar, mismo componente en las 8 hojas de la app.
                SheetHandleWithClose(onClose = onDismiss, enabled = !saving)

                // Solo los CAMPOS scrollean; `fill = false` para que una hoja corta siga
                // midiendo lo que ocupa en vez de estirarse hasta el tope.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Title row: sheet title + optional delete action in edit mode
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isEditMode) "Editar recurrente" else "Nuevo recurrente",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinText,
                            modifier = Modifier.weight(1f),
                        )
                        if (isEditMode) {
                            Text(
                                text = if (saving) "…" else "Eliminar",
                                fontSize = 13.sp,
                                color = MinExpense,
                                modifier = Modifier.clickable(enabled = !saving) { delete() },
                            )
                        }
                    }

                    // --- NOMBRE ---
                    SheetSectionLabel("NOMBRE")
                    Spacer(Modifier.height(8.dp))
                    SheetInputBox {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            cursorBrush = SolidColor(MinText),
                            textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (name.isEmpty()) {
                                    Text("Ej: Arriendo, Netflix, Gym", fontSize = 14.sp, color = MinTextMute)
                                }
                                inner()
                            },
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // --- MONEDA ---
                    // La única pregunta que decide dónde se guarda esto, y es una pregunta del mundo
                    // real: cualquiera sabe si le cobran en pesos o en dólares. Al editar no aparece —
                    // editar es siempre editar una regla (ver KDoc).
                    if (!isEditMode) {
                        SheetSectionLabel("MONEDA")
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SheetChip(
                                label = "Pesos",
                                selected = currency == "COP",
                                onClick = { currency = "COP"; tipoCambiadoPorLaMoneda = false },
                            )
                            SheetChip(
                                label = "Dólares",
                                selected = currency == "USD",
                                onClick = {
                                    currency = "USD"
                                    // En dólares esto se guarda como suscripción, y una suscripción es
                                    // SIEMPRE un cobro. Forzar el tipo al tocar el chip (y no callarlo
                                    // al guardar) es lo que evita el peor caso: elegir Ingreso, después
                                    // Dólares, y que se guarde un gasto — el flujo libre se movía al
                                    // revés por el doble del monto, sin que nada lo dijera.
                                    // Si eso le pisó una elección al dueño, se le dice (ver la nota).
                                    tipoCambiadoPorLaMoneda = selectedType == TransactionType.INCOME
                                    selectedType = TransactionType.EXPENSE
                                },
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    // --- MONTO ---
                    SheetSectionLabel("MONTO")
                    Spacer(Modifier.height(8.dp))
                    // V12: en Colombia "$20" se lee veinte pesos. Si el cobro es en dólares, el campo
                    // lo dice mientras se escribe — igual que después lo dice la fila de la lista.
                    MoneyField(
                        value = amount,
                        onValueChange = { amount = it },
                        prefix = if (enDolares) "US$" else "$",
                        placeholder = if (enDolares) "US$ 0" else "$ 0",
                    )

                    Spacer(Modifier.height(18.dp))

                    // --- DÍA DEL MES ---
                    SheetSectionLabel("DÍA DEL MES (1–31)")
                    Spacer(Modifier.height(8.dp))
                    SheetInputBox {
                        BasicTextField(
                            value = dayOfMonth,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }.take(2)
                                dayOfMonth = digits
                            },
                            cursorBrush = SolidColor(MinText),
                            textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (dayOfMonth.isEmpty()) {
                                    Text("Ej: 5", fontSize = 14.sp, color = MinTextMute)
                                }
                                inner()
                            },
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // --- TIPO ---
                    // Siempre visible, también en dólares. Ahí «Ingreso» queda deshabilitado en vez de
                    // desaparecer: el dueño VE que la opción existe y que no aplica, en lugar de elegirla
                    // y que Movi la cambie por atrás. (Un ingreso recurrente en dólares no se puede
                    // registrar hoy — el modelo de suscripciones es de cobros. Ver la nota de abajo.)
                    SheetSectionLabel("TIPO")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SheetChip(
                            label = "Gasto",
                            selected = selectedType == TransactionType.EXPENSE,
                            onClick = { selectedType = TransactionType.EXPENSE },
                        )
                        SheetChip(
                            label = "Ingreso",
                            selected = selectedType == TransactionType.INCOME,
                            enabled = !enDolares,
                            onClick = { selectedType = TransactionType.INCOME },
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    if (enDolares) {
                        // No se ocultan los campos en silencio: en dólares esto se guarda como
                        // suscripción, y una suscripción es siempre un cobro, sin categoría y sin
                        // recordatorio. Decirlo es más honesto que hacer desaparecer tres secciones.
                        if (tipoCambiadoPorLaMoneda) {
                            Text(
                                text = "Cambiamos el tipo a Gasto: en dólares solo podemos anotar cobros.",
                                fontSize = 12.sp,
                                color = MinWarn,
                                lineHeight = 17.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = "En dólares solo podemos anotar cobros, y guardamos únicamente el " +
                                "nombre, el monto y el día: sin categoría y sin recordatorio. Para el " +
                                "total del mes lo convertimos a pesos con la tasa de cambio más " +
                                "reciente que pudimos consultar.",
                            fontSize = 12.sp,
                            color = MinTextMute,
                            lineHeight = 17.sp,
                        )
                    } else {
                        // --- CATEGORÍA ---
                        // F35: campo libre con sugerencias — antes arrancaba en "Otros" sin ninguna ayuda.
                        // Filtra por el tipo elegido arriba (Gasto/Ingreso) para no sugerir "Salario" en
                        // una regla de gasto.
                        CategoryField(
                            value = category,
                            onValueChange = { category = it; categoriaElegidaAMano = true },
                            type = selectedType,
                            usedCategories = UsedCategoriesCache.used,
                            label = "CATEGORÍA",
                            placeholder = "Ej: Vivienda, Suscripción, Salud",
                        )

                        Spacer(Modifier.height(18.dp))

                        // --- CUENTA (opcional) ---
                        AccountPickerField(
                            accounts = accounts,
                            selectedId = accountId,
                            open = accountPickerOpen,
                            enabled = !saving,
                            onToggle = { accountPickerOpen = !accountPickerOpen },
                            onPick = { accountId = it; accountPickerOpen = false },
                        )

                        Spacer(Modifier.height(18.dp))

                        // --- RECORDATORIO ---
                        // Una regla de INGRESO no genera recordatorio (el barrido solo mira gastos, ver
                        // selectDueForReminder), así que ofrecer la casilla ahí sería prometer un aviso que
                        // nunca sale. Se muestra solo en Gasto.
                        if (selectedType == TransactionType.EXPENSE) {
                            ReminderOptInField(
                                checked = remindMe,
                                onCheckedChange = { remindMe = it },
                                enabled = !saving,
                            )
                        } else {
                            // V10: antes la casilla simplemente se esfumaba al tocar «Ingreso». Que un
                            // control desaparezca sin decir nada deja al dueño preguntándose si lo
                            // imaginó; se explica, igual que se explica lo que se pierde en dólares.
                            Text(
                                text = "Los recordatorios son para lo que tienes que pagar, así que un " +
                                    "ingreso no lleva aviso.",
                                fontSize = 12.sp,
                                color = MinTextMute,
                                lineHeight = 17.sp,
                            )
                        }
                    }

                }

                Spacer(Modifier.height(20.dp))

                // El error del guardado va FIJO, junto al botón, y no dentro del área que
                // scrollea. En el caso Gasto —el que desborda— el contenido queda rodado hacia
                // arriba y el dueño nunca baja, porque el botón ya es fijo: si el POST fallaba,
                // el texto rojo se agregaba al fondo, fuera de vista, y el botón volvía a decir
                // «Crear recurrente». Tocaba, no pasaba nada, y volvía a tocar.
                if (error != null) {
                    Text(
                        text = error!!,
                        fontSize = 12.sp,
                        color = MinExpense,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                // --- CTA --- (fijo al pie, fuera del área que scrollea)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canSave) MinPrimaryContainer else MinSurfaceContainerLow)
                        .clickable(enabled = canSave) { save() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            saving       -> if (isEditMode) "Guardando…" else "Creando…"
                            isEditMode   -> "Guardar cambios"
                            else         -> "Crear recurrente"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (canSave) MinOnPrimaryContainer else MinTextFaint,
                    )
                }
                if (!canSave && !saving && missingFieldMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = missingFieldMessage,
                        fontSize = 12.sp,
                        color = MinTextMute,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

/**
 * Ola 9 · D — **de qué cuenta sale (o a cuál entra) esto todos los meses.**
 *
 * Un movimiento siempre tuvo cuenta y un recurrente no, así que Movi sabía que el salario entra
 * el 25 pero no dónde. El selector es **opcional a propósito**: «Sin cuenta» es una opción de
 * primera clase y no un error — las reglas que el dueño ya tiene nacieron sin cuenta y nadie
 * puede exigirle ahora un dato que nunca se le pidió.
 *
 * Se abre y se cierra en su lugar, sin hoja encima de la hoja: esta ya vive dentro de un
 * `verticalScroll` y una modal sobre otra modal, en el navegador angosto, es una trampa.
 */
@Composable
private fun AccountPickerField(
    accounts: List<Account>,
    selectedId: String?,
    open: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val selected = accounts.firstOrNull { it.id == selectedId }
    SheetSectionLabel("CUENTA (OPCIONAL)")
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && accounts.isNotEmpty(), onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    selected != null -> selected.name
                    accounts.isEmpty() -> "Sin cuentas todavía"
                    else -> "Sin cuenta"
                },
                fontSize = 14.sp,
                color = if (selected != null) MinText else MinTextMute,
                modifier = Modifier.weight(1f),
            )
            if (accounts.isNotEmpty()) {
                Text(if (open) "Cerrar" else "Elegir", fontSize = 12.sp, color = MinTextMute)
            }
        }
    }
    if (open && accounts.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MinSurfaceContainerHigh)
                .border(1.dp, MinBorder, RoundedCornerShape(12.dp)),
        ) {
            AccountPickerRow(label = "Sin cuenta", selected = selectedId == null) { onPick(null) }
            accounts.forEach { account ->
                AccountPickerRow(label = account.name, selected = account.id == selectedId) {
                    onPick(account.id)
                }
            }
        }
    }
}

@Composable
private fun AccountPickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (selected) MinOnPrimaryContainer else MinText,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MinPrimaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MinTextMute,
        letterSpacing = 0.4.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun SheetInputBox(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
private fun RowScope.SheetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MinPrimaryContainer else MinSurfaceContainerLow)
            .then(
                if (!selected) Modifier.border(1.dp, MinBorder, RoundedCornerShape(10.dp)) else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = when {
                !enabled -> MinTextFaint
                selected -> MinOnPrimaryContainer
                else     -> MinTextDim
            },
        )
    }
}
