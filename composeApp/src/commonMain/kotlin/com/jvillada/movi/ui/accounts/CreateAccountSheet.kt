package com.jvillada.movi.ui.accounts

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
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.theme.*
import kotlinx.coroutines.launch

private data class TypeOption(val type: AccountType, val label: String)

private val TYPE_OPTIONS = listOf(
    TypeOption(AccountType.CASH, "💵 Efectivo"),
    TypeOption(AccountType.SAVINGS, "🏦 Ahorros"),
    TypeOption(AccountType.CHECKING, "💳 Corriente"),
    TypeOption(AccountType.INVESTMENT, "📈 Inversión"),
)

@Composable
fun CreateAccountSheet(onDismiss: () -> Unit, onAccountCreated: () -> Unit) {
    val coroutine = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.CASH) }
    var initialBalance by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSave = name.isNotBlank() && !saving

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val account = Account(
                id = "",
                name = name.trim(),
                type = selectedType,
                balance = initialBalance.toLongOrNull() ?: 0L,
            )
            val result = runCatching { Repositories.wallets.createAccount(account) }
            saving = false
            result.onSuccess { onAccountCreated() }
                .onFailure { error = "No pude guardar: ${it.message ?: "error desconocido"}" }
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

            // --- NOMBRE ---
            SectionLabel("NOMBRE")
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerLow)
                    .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    cursorBrush = SolidColor(MinText),
                    textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text("Ej: Bancolombia Ahorros", fontSize = 14.sp, color = MinTextMute)
                        }
                        inner()
                    },
                )
            }

            Spacer(Modifier.height(18.dp))

            // --- TIPO ---
            SectionLabel("TIPO")
            Spacer(Modifier.height(8.dp))
            // 2×2 chip grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (row in TYPE_OPTIONS.chunked(2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { option ->
                            val isSelected = selectedType == option.type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MinPrimaryContainer else MinSurfaceContainerLow,
                                    )
                                    .then(
                                        if (!isSelected) Modifier.border(
                                            1.dp, MinBorder, RoundedCornerShape(10.dp),
                                        ) else Modifier,
                                    )
                                    .clickable { selectedType = option.type }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = option.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isSelected) MinOnPrimaryContainer else MinTextDim,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // --- SALDO INICIAL ---
            SectionLabel("SALDO INICIAL")
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerLow)
                    .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                BasicTextField(
                    value = initialBalance,
                    onValueChange = { input ->
                        // Only allow digit characters, max 12 chars
                        val filtered = input.filter { it.isDigit() }.take(12)
                        initialBalance = filtered
                    },
                    cursorBrush = SolidColor(MinText),
                    textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (initialBalance.isEmpty()) {
                            Text("$ 0", fontSize = 14.sp, color = MinTextMute)
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
                    text = if (saving) "Creando…" else "Crear cuenta",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canSave) MinOnPrimaryContainer else MinTextFaint,
                )
            }

            Spacer(Modifier.height(14.dp))
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
