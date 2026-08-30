package com.jvillada.movi.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.credits.FieldBox
import com.jvillada.movi.ui.credits.SectionLabel
import com.jvillada.movi.ui.credits.filterDateInput
import com.jvillada.movi.ui.credits.isValidCreditDate
import kotlinx.coroutines.launch

/**
 * F26: alta/edición de una meta de ahorro — nombre, monto objetivo, cuenta donde se ahorra
 * (solo Dinero o Inversión: el server rechaza una de deuda con 422) y fecha objetivo opcional.
 * No hay campo de "aporte manual" — el ahorrado siempre sale del saldo real de [accounts]
 * (ver KDoc de [Goal.saved]).
 *
 * Reusa [isValidCreditDate]/[filterDateInput] de `CreditTermsSheet.kt` (Ola 1): mismo formato
 * AAAA-MM-DD, mismo chequeo de forma — no tiene sentido reimplementarlo acá.
 */
@Composable
fun GoalSheet(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    existing: Goal? = null,
) {
    val coroutine = rememberCoroutineScope()
    // F26: solo cuentas de Dinero o Inversión — una meta no se ahorra en una deuda.
    val eligibleAccounts = remember(accounts) { accounts.filter { it.type.group != AccountGroup.DEUDA } }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var target by remember { mutableStateOf(existing?.target) }
    var selectedAccountId by remember { mutableStateOf(existing?.accountId) }
    var targetDate by remember { mutableStateOf(existing?.targetDate ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isEditMode = existing != null
    val dateValid = targetDate.isBlank() || isValidCreditDate(targetDate)
    val canSave = name.isNotBlank() && (target ?: 0L) > 0L && selectedAccountId != null && dateValid && !saving
    // F24: mismo patrón que las demás hojas — la primera cosa que falta, no un botón gris.
    val missingFieldMessage = when {
        name.isBlank() -> "Falta el nombre"
        (target ?: 0L) <= 0L -> "Falta el monto objetivo"
        selectedAccountId == null -> "Elige una cuenta"
        !dateValid -> "La fecha objetivo tiene que ser AAAA-MM-DD"
        else -> null
    }

    fun save() {
        if (!canSave) return
        val accountId = selectedAccountId ?: return
        val amt = target ?: return
        saving = true
        error = null
        coroutine.launch {
            val goal = Goal(
                id = existing?.id ?: "",
                name = name.trim(),
                target = amt,
                accountId = accountId,
                targetDate = targetDate.trim().ifBlank { null },
            )
            val result = if (isEditMode) {
                runCatching { Repositories.wallets.updateGoal(existing!!.id, goal) }
            } else {
                runCatching { Repositories.wallets.createGoal(goal) }
            }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    fun delete() {
        if (existing == null || saving) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.deleteGoal(existing.id) }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
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
            SheetHandleWithClose(onClose = onDismiss, enabled = !saving)

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isEditMode) "Editar meta" else "Nueva meta",
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

            SectionLabel("NOMBRE")
            Spacer(Modifier.height(8.dp))
            FieldBox("Ej: Viaje, Colchón de emergencia", name, { name = it })

            Spacer(Modifier.height(18.dp))

            SectionLabel("MONTO OBJETIVO")
            Spacer(Modifier.height(8.dp))
            MoneyField(value = target, onValueChange = { target = it })

            Spacer(Modifier.height(18.dp))

            SectionLabel("CUENTA DONDE SE AHORRA")
            Spacer(Modifier.height(8.dp))
            if (eligibleAccounts.isEmpty()) {
                Text(
                    "No tienes cuentas de Dinero o Inversión — crea una en Cuentas primero",
                    fontSize = 12.5.sp,
                    color = MinTextMute,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    eligibleAccounts.forEach { acc ->
                        GoalAccountRow(
                            label = acc.name,
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionLabel("FECHA OBJETIVO (AAAA-MM-DD, OPCIONAL)")
            Spacer(Modifier.height(8.dp))
            FieldBox("Ej: 2027-06-01", targetDate, { targetDate = filterDateInput(it) })

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = error!!, fontSize = 12.sp, color = MinExpense)
            }

            Spacer(Modifier.height(20.dp))

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
                        saving     -> if (isEditMode) "Guardando…" else "Creando…"
                        isEditMode -> "Guardar cambios"
                        else       -> "Crear meta"
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
private fun GoalAccountRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MinSurfaceContainerLow else Color.Transparent)
            .border(1.dp, if (selected) MinText else MinBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.5.sp, color = MinText, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}
