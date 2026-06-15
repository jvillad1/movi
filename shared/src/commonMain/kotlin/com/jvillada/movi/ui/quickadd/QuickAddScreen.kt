package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private val EXPENSE_CATEGORIES = listOf(
    "Mercado", "Restaurantes", "Transporte", "Salud",
    "Suscripción", "Servicios", "Hogar", "Entretenimiento", "Otro",
)
private val INCOME_CATEGORIES = listOf("Nómina", "Transferencia", "Reembolso", "Otro")

private sealed class Picker {
    data object None : Picker()
    data object Category : Picker()
    data object Wallet : Picker()
    data object Note : Picker()
}

@Composable
fun QuickAddScreen(onDismiss: () -> Unit, onNavigate: (Screen) -> Unit = {}) {
    val coroutine = rememberCoroutineScope()
    var typeIndex by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EXPENSE_CATEGORIES.first()) }
    var accounts by remember { mutableStateOf<List<com.jvillada.movi.shared.model.Account>>(emptyList()) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<Picker>(Picker.None) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var accountsRefreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(accountsRefreshKey) {
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { list ->
                accounts = list
                if (selectedAccountId == null || list.none { it.id == selectedAccountId }) {
                    selectedAccountId = list.firstOrNull()?.id
                }
            }
    }

    LaunchedEffect(typeIndex) {
        val available = if (typeIndex == 0) EXPENSE_CATEGORIES else INCOME_CATEGORIES
        if (category !in available) category = available.first()
    }

    fun onKey(key: String) {
        amount = when (key) {
            "⌫" -> if (amount.isNotEmpty()) amount.dropLast(1) else amount
            "." -> if ("." !in amount) amount + key else amount
            else -> if (amount.length < 12) amount + key else amount
        }
    }

    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
    val canSave = parsedAmount > 0 && selectedAccountId != null && !saving
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val event = FinancialEvent(
                id = "",
                accountId = selectedAccountId ?: accounts.firstOrNull()?.id ?: "acc_1",
                type = if (typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME,
                amount = amount.toLongOrNull() ?: 0L,
                category = category,
                description = note.ifBlank { category },
                source = EventSource.MANUAL,
                timestamp = Clock.System.now().toEpochMilliseconds(),
            )
            val result = runCatching { Repositories.wallets.postEvent(event) }
            saving = false
            result.onSuccess { onDismiss() }
                .onFailure { error = it.toUserMessage() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp)
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MinTextFaint),
                )

                when (picker) {
                    Picker.Category -> CategoryPicker(
                        options = if (typeIndex == 0) EXPENSE_CATEGORIES else INCOME_CATEGORIES,
                        selected = category,
                        onPick = { category = it; picker = Picker.None },
                        onClose = { picker = Picker.None },
                    )
                    Picker.Wallet -> WalletPicker(
                        accounts = accounts,
                        selectedId = selectedAccountId,
                        onPick = { selectedAccountId = it; picker = Picker.None },
                        onClose = { picker = Picker.None },
                    )
                    Picker.Note -> NoteEditor(
                        initial = note,
                        onSave = { note = it; picker = Picker.None },
                        onClose = { picker = Picker.None },
                    )
                    Picker.None -> EditorBody(
                        typeIndex = typeIndex,
                        onTypeChange = { typeIndex = it },
                        amount = amount,
                        onKey = ::onKey,
                        category = category,
                        walletLabel = selectedAccount?.name ?: "Seleccionar cuenta",
                        note = note,
                        onPickCategory = { picker = Picker.Category },
                        onPickWallet = { picker = Picker.Wallet },
                        onEditNote = { picker = Picker.Note },
                        onOcr = { onNavigate(Screen.OCRCapture) },
                        canSave = canSave,
                        saving = saving,
                        error = error,
                        onSave = ::save,
                        hasNoAccounts = accounts.isEmpty(),
                        onCreateAccount = { showCreateSheet = true },
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
        }

        if (showCreateSheet) {
            CreateAccountSheet(
                onDismiss = { showCreateSheet = false },
                onAccountCreated = { showCreateSheet = false; accountsRefreshKey++ },
            )
        }
    }
}

@Composable
private fun EditorBody(
    typeIndex: Int,
    onTypeChange: (Int) -> Unit,
    amount: String,
    onKey: (String) -> Unit,
    category: String,
    walletLabel: String,
    note: String,
    onPickCategory: () -> Unit,
    onPickWallet: () -> Unit,
    onEditNote: () -> Unit,
    onOcr: () -> Unit,
    canSave: Boolean,
    saving: Boolean,
    error: String?,
    onSave: () -> Unit,
    hasNoAccounts: Boolean = false,
    onCreateAccount: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        listOf("Egreso", "Ingreso").forEachIndexed { i, label ->
            val isActive = i == typeIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) MinSurfaceContainerHigh else Color.Transparent)
                    .clickable { onTypeChange(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) MinText else MinTextDim,
                    letterSpacing = 0.1.sp,
                )
            }
        }
    }

    Spacer(Modifier.height(22.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$${amount.ifEmpty { "0" }}",
            fontSize = 56.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            color = MinText,
            letterSpacing = (-2.2).sp,
            lineHeight = 56.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("COP", fontSize = 12.sp, color = MinTextMute, letterSpacing = 0.4.sp)
    }

    Spacer(Modifier.height(18.dp))

    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    ) {
        CardRow(
            left = { Text("Categoría", fontSize = 14.5.sp, color = MinTextMute) },
            right = { Text(category, fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) },
            showChevron = true,
            onClick = onPickCategory,
        )
        CardRow(
            left = { Text("Cuenta", fontSize = 14.5.sp, color = MinTextMute) },
            right = { Text(walletLabel, fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) },
            showChevron = true,
            onClick = onPickWallet,
        )
        CardRow(
            left = { Text("Nota", fontSize = 14.5.sp, color = MinTextMute) },
            right = {
                Text(
                    text = note.ifBlank { "Agregar nota…" },
                    fontSize = 14.5.sp,
                    color = if (note.isBlank()) MinTextFaint else MinText,
                    fontWeight = if (note.isBlank()) FontWeight.Normal else FontWeight.Medium,
                )
            },
            isLast = true,
            onClick = onEditNote,
        )
    }

    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error, fontSize = 12.sp, color = MinExpense)
    }

    Spacer(Modifier.height(14.dp))

    Column {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫"),
        ).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            color = MinText,
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    if (hasNoAccounts) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Primero creá una cuenta",
                fontSize = 13.sp,
                color = MinTextMute,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MinPrimaryContainer)
                    .clickable { onCreateAccount() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+ Crear cuenta",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinOnPrimaryContainer,
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp, 54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MinBorderStrong, RoundedCornerShape(16.dp))
                    .clickable { onOcr() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = "Escanear recibo", tint = MinText, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (canSave) MinPrimaryContainer else MinSurfaceContainerLow)
                    .clickable(enabled = canSave) { onSave() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (saving) "Guardando…" else "Guardar movimiento",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canSave) MinOnPrimaryContainer else MinTextFaint,
                )
            }
        }
    }
}

@Composable
private fun PickerHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MinText)
        Box(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MinSurfaceContainerLow)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", fontSize = 18.sp, color = MinText)
        }
    }
}

@Composable
private fun CategoryPicker(
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PickerHeader("Categoría", onClose)
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            items(options) { opt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(opt) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        opt,
                        fontSize = 15.sp,
                        color = MinText,
                        fontWeight = if (opt == selected) FontWeight.Medium else FontWeight.Normal,
                    )
                    Box(modifier = Modifier.weight(1f))
                    if (opt == selected) Text("✓", fontSize = 14.sp, color = MinText)
                }
            }
        }
    }
}

@Composable
private fun WalletPicker(
    accounts: List<com.jvillada.movi.shared.model.Account>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PickerHeader("Cuenta", onClose)
        if (accounts.isEmpty()) {
            Text("Cargando cuentas…", fontSize = 14.sp, color = MinTextMute, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(accounts) { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(account.id) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                account.name,
                                fontSize = 15.sp,
                                color = MinText,
                                fontWeight = if (account.id == selectedId) FontWeight.Medium else FontWeight.Normal,
                            )
                            Text(
                                "$${account.balance}",
                                fontSize = 12.sp,
                                color = MinTextMute,
                            )
                        }
                        if (account.id == selectedId) Text("✓", fontSize = 14.sp, color = MinText)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        PickerHeader("Nota", onClose)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MinSurfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                cursorBrush = SolidColor(MinText),
                textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text("Concepto del movimiento", fontSize = 14.sp, color = MinTextMute)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MinPrimaryContainer)
                .clickable { onSave(text.trim()) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Guardar nota", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinOnPrimaryContainer)
        }
    }
}
