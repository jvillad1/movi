package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.recurrentes.ReminderOptInField
import kotlinx.coroutines.launch

/**
 * Crea una tarjeta de crédito o edita los términos de una existente — la hoja hermana de
 * [CreditTermsSheet], con los campos que una tarjeta sí tiene: cupo, corte y día de pago
 * (nada de capital, tasa ni plazo, que son de préstamo).
 *
 * - [editing] != null → edición sobre esa tarjeta (cuenta fija, campos precargados). También es
 *   el camino por el que una tarjeta creada antes desde Cuentas gana corte y pago (PUT upsert).
 * - [editing] == null → creación atómica server-side (cuenta + deuda inicial + términos), mismo
 *   patrón que el alta de crédito. La deuda actual es opcional: una tarjeta recién sacada está
 *   en $0. Moneda COP/USD — las Mastercard en dólares existen.
 */
@Composable
fun CardTermsSheet(
    editing: CardSummary?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val existingTerms = editing?.terms

    var name by remember { mutableStateOf("") }
    var initialDebt by remember { mutableStateOf<Long?>(null) }
    var currency by remember { mutableStateOf(editing?.account?.currency ?: "COP") }
    var bank by remember { mutableStateOf(existingTerms?.bank ?: "") }
    var creditLimit by remember { mutableStateOf(existingTerms?.creditLimit) }
    var cutoffDay by remember { mutableStateOf(existingTerms?.cutoffDay?.toString() ?: "") }
    var paymentDay by remember { mutableStateOf(existingTerms?.paymentDay?.toString() ?: "") }
    // Marcada por defecto al crear; al editar refleja lo que está guardado.
    var remindMe by remember { mutableStateOf(existingTerms?.remindMe ?: true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // El corte es opcional: vacío vale; si se escribe, tiene que ser un día real.
    val cutoffOk = cutoffDay.isBlank() || cutoffDay.toIntOrNull() in 1..31
    val termsValid = bank.isNotBlank() && (paymentDay.toIntOrNull() in 1..31) && cutoffOk
    val accountValid = editing != null || name.isNotBlank()
    val canSave = termsValid && accountValid && !saving

    // F24 (mismo patrón que CreditTermsSheet): el botón nunca se apaga en silencio — debajo
    // dice la PRIMERA cosa que falta, en el orden de los campos.
    val missingFieldMessage = when {
        editing == null && name.isBlank() -> "Falta el nombre de la tarjeta"
        bank.isBlank() -> "Falta el banco"
        cutoffDay.isNotBlank() && cutoffDay.toIntOrNull() !in 1..31 -> "El día de corte tiene que estar entre 1 y 31"
        paymentDay.isBlank() -> "Falta el día de pago"
        paymentDay.toIntOrNull() !in 1..31 -> "El día de pago tiene que estar entre 1 y 31"
        else -> null
    }

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                val terms = CardTerms(
                    accountId = editing?.account?.id ?: "",
                    bank = bank.trim(),
                    creditLimit = creditLimit,
                    cutoffDay = cutoffDay.toIntOrNull(),
                    paymentDay = paymentDay.toInt(),
                    remindMe = remindMe,
                )
                if (editing == null) {
                    // Alta atómica server-side: cuenta + deuda inicial (si la hay) + términos
                    // en una sola operación — sin estados parciales si algo falla a mitad.
                    Repositories.wallets.createCard(
                        CreateCardRequest(
                            name = name.trim(),
                            initialDebt = initialDebt ?: 0L,
                            currency = currency,
                            terms = terms,
                        )
                    )
                } else {
                    Repositories.wallets.putCardTerms(terms)
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
            val result = runCatching { Repositories.wallets.deleteCardTerms(editing.account.id) }
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

            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                if (editing != null) {
                    SectionLabel("TARJETA")
                    Spacer(Modifier.height(8.dp))
                    Text(editing.account.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
                    Spacer(Modifier.height(16.dp))
                } else {
                    FieldBox("Nombre (p.ej. Visa Bancolombia)", name, { name = it })
                    Spacer(Modifier.height(8.dp))
                    // Opcional a propósito: una tarjeta recién sacada no debe nada.
                    MoneyField(initialDebt, { initialDebt = it }, placeholder = "Deuda actual ($currency, opcional)")
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("MONEDA")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (cur in listOf("COP", "USD")) {
                            CurrencyChip(
                                label = cur,
                                selected = currency == cur,
                                onClick = { currency = cur },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                SectionLabel("TÉRMINOS")
                Spacer(Modifier.height(8.dp))
                FieldBox("Banco", bank, { bank = it })
                Spacer(Modifier.height(8.dp))
                MoneyField(
                    creditLimit, { creditLimit = it },
                    placeholder = "Cupo total (${editing?.account?.currency ?: currency}, opcional)",
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        FieldBox("Corte (opcional)", cutoffDay, { cutoffDay = it.filter { ch -> ch.isDigit() }.take(2) }, KeyboardType.Number)
                    }
                    Box(Modifier.weight(1f)) {
                        FieldBox("Día de pago", paymentDay, { paymentDay = it.filter { ch -> ch.isDigit() }.take(2) }, KeyboardType.Number)
                    }
                }

                Spacer(Modifier.height(16.dp))
                // El pago de esta tarjeta entra al barrido de recordatorios salvo que el dueño
                // diga que no — mismo componente y mismo texto que crédito y recurrente.
                ReminderOptInField(
                    checked = remindMe,
                    onCheckedChange = { remindMe = it },
                    enabled = !saving,
                )

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
                Text(if (saving) "Guardando…" else "Guardar tarjeta", color = MinBg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
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

/** Chip de moneda, mismo dibujo que el selector de CreateAccountSheet (que es privado allá). */
@Composable
private fun RowScope.CurrencyChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
