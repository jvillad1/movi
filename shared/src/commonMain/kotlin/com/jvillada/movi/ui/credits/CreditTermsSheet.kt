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
import com.jvillada.movi.shared.model.CreateCreditRequest
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
    var newAccountDebt by remember { mutableStateOf<Long?>(null) }

    var bank by remember { mutableStateOf(existingTerms?.bank ?: "") }
    var principal by remember { mutableStateOf(existingTerms?.principal) }
    // F23: la tasa aceptaba "12%" y no se leía como número — el filtro de abajo (solo dígitos y
    // un único punto) hace que el "%" nunca llegue a este estado; el campo lo pinta aparte.
    var rateEa by remember { mutableStateOf(existingTerms?.rateEa?.toString() ?: "") }
    var termMonths by remember { mutableStateOf(existingTerms?.termMonths?.toString() ?: "") }
    var installment by remember { mutableStateOf(existingTerms?.installment) }
    var dayOfMonth by remember { mutableStateOf(existingTerms?.dayOfMonth?.toString() ?: "") }
    // F23: aceptaba cualquier cosa como fecha — el filtro de abajo (solo dígitos y guiones) más
    // isValidCreditDate son la validación real hasta que exista un selector de calendario
    // (pendiente, anotado en el KDoc de más abajo).
    var startDate by remember { mutableStateOf(existingTerms?.startDate ?: "") }
    var notes by remember { mutableStateOf(existingTerms?.notes ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val termsValid = bank.isNotBlank() &&
        (principal ?: 0L) > 0L &&
        (rateEa.toDoubleOrNull() != null) &&
        (termMonths.toIntOrNull() ?: 0) > 0 &&
        (installment ?: 0L) > 0L &&
        (dayOfMonth.toIntOrNull() in 1..31) &&
        isValidCreditDate(startDate)
    val accountValid = if (editing != null) true
        else if (newAccountMode) newAccountName.isNotBlank() && (newAccountDebt ?: 0L) > 0L
        else selectedAccountId != null
    val canSave = termsValid && accountValid && !saving

    // F24: el botón se ponía gris sin decir por qué. Debajo, la PRIMERA cosa que falta, en el
    // mismo orden en que aparecen los campos en la hoja.
    val missingFieldMessage = when {
        editing == null && newAccountMode && newAccountName.isBlank() -> "Falta el nombre de la cuenta"
        editing == null && newAccountMode && (newAccountDebt ?: 0L) <= 0L -> "Falta la deuda actual"
        editing == null && !newAccountMode && selectedAccountId == null -> "Elige una cuenta"
        bank.isBlank() -> "Falta el banco"
        (principal ?: 0L) <= 0L -> "Falta el capital original"
        rateEa.toDoubleOrNull() == null -> "Falta la tasa"
        (termMonths.toIntOrNull() ?: 0) <= 0 -> "Falta el plazo en meses"
        (installment ?: 0L) <= 0L -> "Falta la cuota mensual"
        dayOfMonth.toIntOrNull() !in 1..31 -> "El día de pago tiene que estar entre 1 y 31"
        startDate.isBlank() -> "Falta la fecha de desembolso"
        !isValidCreditDate(startDate) -> "La fecha de desembolso tiene que ser AAAA-MM-DD"
        else -> null
    }

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                val terms = CreditTerms(
                    accountId = "",
                    bank = bank.trim(),
                    principal = principal!!,
                    rateEa = rateEa.toDouble(),
                    termMonths = termMonths.toInt(),
                    installment = installment!!,
                    dayOfMonth = dayOfMonth.toInt(),
                    startDate = startDate.trim(),
                    notes = notes.trim().ifBlank { null },
                )
                if (editing == null && newAccountMode) {
                    // Alta atómica server-side: cuenta + deuda inicial + términos en una
                    // sola operación — sin estados parciales si algo falla a mitad.
                    Repositories.wallets.createCredit(
                        CreateCreditRequest(
                            name = newAccountName.trim(),
                            initialDebt = newAccountDebt!!,
                            terms = terms,
                        )
                    )
                } else {
                    val accountId = editing?.account?.id ?: selectedAccountId!!
                    Repositories.wallets.putCreditTerms(terms.copy(accountId = accountId))
                }
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
                        MoneyField(newAccountDebt, { newAccountDebt = it }, placeholder = "Deuda actual (COP)")
                    }
                    Spacer(Modifier.height(16.dp))
                }

                SectionLabel("TÉRMINOS")
                Spacer(Modifier.height(8.dp))
                FieldBox("Banco", bank, { bank = it })
                Spacer(Modifier.height(8.dp))
                MoneyField(principal, { principal = it }, placeholder = "Capital original (COP)")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        // F23/F24: solo dígitos y un único punto — el "%" lo pinta RateFieldBox,
                        // nunca lo escribe la persona.
                        RateFieldBox("Tasa % EA", rateEa, { rateEa = filterRateInput(it) })
                    }
                    Box(Modifier.weight(1f)) { FieldBox("Plazo (meses)", termMonths, { termMonths = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { MoneyField(installment, { installment = it }, placeholder = "Cuota mensual (COP)") }
                    Box(Modifier.weight(1f)) { FieldBox("Día de pago", dayOfMonth, { dayOfMonth = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                }
                Spacer(Modifier.height(8.dp))
                // F23/F24: solo dígitos y guiones — sin selector de calendario todavía
                // (pendiente, ver KDoc de isValidCreditDate más abajo).
                FieldBox("Desembolso (AAAA-MM-DD)", startDate, { startDate = filterDateInput(it) })
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
            // F24: antes el botón se apagaba en silencio. Ahora dice la primera cosa que falta.
            if (!canSave && !saving && missingFieldMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = missingFieldMessage,
                    fontSize = 12.sp,
                    color = MinTextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
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
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MinTextMute,
        letterSpacing = 0.4.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun FieldBox(
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

/**
 * Como [FieldBox], pero para la tasa: pinta un "%" fijo después del número — la persona nunca
 * lo escribe, así que nunca puede terminar en el estado ("12%") que rompía el parseo (F23/F24).
 */
@Composable
private fun RateFieldBox(placeholder: String, value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = MinTextFaint)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(fontSize = 14.sp, color = MinText),
                    cursorBrush = SolidColor(MinText),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) Text("%", fontSize = 14.sp, color = MinTextMute)
        }
    }
}

/**
 * F23: la tasa aceptaba "12%" y no se leía como número. Filtra todo lo que no sea dígito o un
 * único punto decimal — el "%" queda a cargo de [RateFieldBox], nunca del texto escrito.
 */
fun filterRateInput(input: String): String {
    var sawDot = false
    // La coma cuenta como punto decimal: en Colombia se escribe «12,5», y el teclado decimal de
    // Android en español muestra «,». Descartarla en silencio convertía «12,5» en «125» — una
    // tasa diez veces mayor, guardada sin aviso. Justo el número que miente que esta ola vino a matar.
    return input.replace(',', '.').filter { ch ->
        when {
            ch.isDigit() -> true
            ch == '.' && !sawDot -> { sawDot = true; true }
            else -> false
        }
    }
}

/** F23: la fecha de desembolso aceptaba cualquier texto. Deja pasar solo dígitos y guiones. */
// La barra cuenta como guion: «2026/06/17» era el caso exacto que dejaba el botón en gris (F24).
fun filterDateInput(input: String): String = input.replace('/', '-').filter { it.isDigit() || it == '-' }

/**
 * F23/F24: AAAA-MM-DD con año/mes/día en rango razonable — la validación real hasta que exista
 * un selector de calendario (pendiente; anotado en el ítem F23 del plan, no se agregó acá).
 * No valida días por mes (el 31 de febrero pasa) a propósito: es un chequeo de forma para que
 * el botón no se quede en gris sin explicar por qué, no una validación de calendario completa.
 */
fun isValidCreditDate(input: String): Boolean {
    val parts = input.split("-")
    if (parts.size != 3) return false
    val (y, m, d) = parts
    // Mes y día de dos dígitos exactos: el server guarda el texto tal cual (varchar), así que
    // «2026-6-7» quedaría almacenado en un formato que después nadie parsea igual.
    if (y.length != 4 || m.length != 2 || d.length != 2) return false
    val year = y.toIntOrNull() ?: return false
    val month = m.toIntOrNull() ?: return false
    val day = d.toIntOrNull() ?: return false
    return year in 1900..2100 && month in 1..12 && day in 1..31
}
