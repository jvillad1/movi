package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

/**
 * Crea o edita los términos de un crédito.
 * - [editing] != null → modo edición sobre ese crédito (cuenta fija, campos precargados, permite eliminar).
 * - [editing] == null → modo creación: elegir una cuenta LOAN sin términos de [candidates] o crear cuenta nueva.
 */
@Composable
fun CreditTermsSheet(
    editing: CreditSummary?,
    candidates: List<Account>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val existingTerms = editing?.terms

    var selectedAccountId by remember { mutableStateOf(editing?.account?.id ?: candidates.firstOrNull()?.id) }
    var newAccountMode by remember { mutableStateOf(editing == null && candidates.isEmpty()) }
    var newAccountName by remember { mutableStateOf("") }
    var newAccountDebt by remember { mutableStateOf("") }

    var bank by remember { mutableStateOf(existingTerms?.bank ?: "") }
    var principal by remember { mutableStateOf(existingTerms?.principal?.toString() ?: "") }
    var rateEa by remember { mutableStateOf(existingTerms?.rateEa?.toString() ?: "") }
    var termMonths by remember { mutableStateOf(existingTerms?.termMonths?.toString() ?: "") }
    var installment by remember { mutableStateOf(existingTerms?.installment?.toString() ?: "") }
    var dayOfMonth by remember { mutableStateOf(existingTerms?.dayOfMonth?.toString() ?: "") }
    var startDate by remember { mutableStateOf(existingTerms?.startDate ?: "") }
    var notes by remember { mutableStateOf(existingTerms?.notes ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val termsValid = bank.isNotBlank() &&
        (principal.toLongOrNull() ?: 0L) > 0L &&
        (rateEa.toDoubleOrNull() != null) &&
        (termMonths.toIntOrNull() ?: 0) > 0 &&
        (installment.toLongOrNull() ?: 0L) > 0L &&
        (dayOfMonth.toIntOrNull() in 1..31) &&
        startDate.isNotBlank()
    val accountValid = if (editing != null) true
        else if (newAccountMode) newAccountName.isNotBlank() && (newAccountDebt.toLongOrNull() ?: 0L) > 0L
        else selectedAccountId != null
    val canSave = termsValid && accountValid && !saving

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                val accountId = when {
                    editing != null -> editing.account.id
                    newAccountMode -> Repositories.wallets.createAccount(
                        Account(
                            id = "",
                            name = newAccountName.trim(),
                            type = AccountType.LOAN,
                            balance = newAccountDebt.toLongOrNull() ?: 0L,
                            currency = "COP",
                        )
                    ).id
                    else -> selectedAccountId!!
                }
                Repositories.wallets.putCreditTerms(
                    CreditTerms(
                        accountId = accountId,
                        bank = bank.trim(),
                        principal = principal.toLong(),
                        rateEa = rateEa.toDouble(),
                        termMonths = termMonths.toInt(),
                        installment = installment.toLong(),
                        dayOfMonth = dayOfMonth.toInt(),
                        startDate = startDate.trim(),
                        notes = notes.trim().ifBlank { null },
                    )
                )
            }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    fun deleteTerms() {
        if (editing == null || saving) return
        saving = true
        coroutine.launch {
            val result = runCatching { Repositories.wallets.deleteCreditTerms(editing.account.id) }
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
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .width(32.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinTextFaint),
            )

            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                if (editing != null) {
                    SectionLabel("CRÉDITO")
                    Spacer(Modifier.height(8.dp))
                    Text(editing.account.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
                    Spacer(Modifier.height(16.dp))
                } else {
                    SectionLabel("CUENTA DEL PRÉSTAMO")
                    Spacer(Modifier.height(8.dp))
                    candidates.forEach { acc ->
                        SelectRow(
                            label = acc.name,
                            selected = !newAccountMode && selectedAccountId == acc.id,
                            onClick = { newAccountMode = false; selectedAccountId = acc.id },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    SelectRow(
                        label = "+ Nueva cuenta de préstamo",
                        selected = newAccountMode,
                        onClick = { newAccountMode = true },
                    )
                    if (newAccountMode) {
                        Spacer(Modifier.height(10.dp))
                        FieldBox("Nombre (p.ej. Crédito Vehículo Santander)", newAccountName, { newAccountName = it })
                        Spacer(Modifier.height(8.dp))
                        FieldBox("Deuda actual (COP)", newAccountDebt, { newAccountDebt = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                SectionLabel("TÉRMINOS")
                Spacer(Modifier.height(8.dp))
                FieldBox("Banco", bank, { bank = it })
                Spacer(Modifier.height(8.dp))
                FieldBox("Capital original (COP)", principal, { principal = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { FieldBox("Tasa % EA", rateEa, { rateEa = it }, KeyboardType.Decimal) }
                    Box(Modifier.weight(1f)) { FieldBox("Plazo (meses)", termMonths, { termMonths = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { FieldBox("Cuota mensual (COP)", installment, { installment = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                    Box(Modifier.weight(1f)) { FieldBox("Día de pago", dayOfMonth, { dayOfMonth = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                }
                Spacer(Modifier.height(8.dp))
                FieldBox("Desembolso (AAAA-MM-DD)", startDate, { startDate = it })
                Spacer(Modifier.height(8.dp))
                FieldBox("Notas (opcional)", notes, { notes = it })

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = MinExpense)
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) MinText else MinTextFaint)
                    .clickable(enabled = canSave) { save() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (saving) "Guardando…" else "Guardar crédito", color = MinBg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            if (editing?.terms != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Eliminar términos",
                    fontSize = 13.sp,
                    color = MinExpense,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { deleteTerms() }.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MinTextMute,
        letterSpacing = 0.4.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun FieldBox(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = MinTextFaint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 14.sp, color = MinText),
            cursorBrush = SolidColor(MinText),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SelectRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
