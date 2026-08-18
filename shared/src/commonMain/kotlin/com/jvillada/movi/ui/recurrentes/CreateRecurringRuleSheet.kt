package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch

@Composable
fun CreateRecurringRuleSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    existing: RecurringRule? = null,
) {
    val coroutine = rememberCoroutineScope()

    // Prefill state from existing rule when in edit mode
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.amount) }
    var dayOfMonth by remember { mutableStateOf(existing?.dayOfMonth?.toString() ?: "") }
    var selectedType by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var category by remember { mutableStateOf(existing?.category ?: "Otros") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isEditMode = existing != null
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
            val rule = RecurringRule(
                id = existing?.id ?: "",
                name = name.trim(),
                category = category.trim().ifBlank { "Otros" },
                amount = amt,
                dayOfMonth = day,
                type = selectedType,
            )
            val result = if (isEditMode) {
                runCatching { Repositories.wallets.updateRecurringRule(existing!!.id, rule) }
            } else {
                runCatching { Repositories.wallets.createRecurringRule(rule) }
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
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinTextFaint),
            )

            // Title row: sheet title + optional delete action in edit mode
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isEditMode) "Editar pago" else "Nuevo pago",
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

            // --- MONTO ---
            SheetSectionLabel("MONTO")
            Spacer(Modifier.height(8.dp))
            MoneyField(value = amount, onValueChange = { amount = it })

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
                    onClick = { selectedType = TransactionType.INCOME },
                )
            }

            Spacer(Modifier.height(18.dp))

            // --- CATEGORÍA ---
            SheetSectionLabel("CATEGORÍA")
            Spacer(Modifier.height(8.dp))
            SheetInputBox {
                BasicTextField(
                    value = category,
                    onValueChange = { category = it },
                    cursorBrush = SolidColor(MinText),
                    textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (category.isEmpty()) {
                            Text("Ej: Vivienda, Suscripción, Salud", fontSize = 14.sp, color = MinTextMute)
                        }
                        inner()
                    },
                )
            }

            // Inline error display
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error!!,
                    fontSize = 12.sp,
                    color = MinExpense,
                )
            }

            Spacer(Modifier.height(20.dp))

            // --- CTA ---
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
                        else         -> "Crear pago"
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
private fun RowScope.SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MinPrimaryContainer else MinSurfaceContainerLow)
            .then(
                if (!selected) Modifier.border(1.dp, MinBorder, RoundedCornerShape(10.dp)) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MinOnPrimaryContainer else MinTextDim,
        )
    }
}
