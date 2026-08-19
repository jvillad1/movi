package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

/**
 * Mismo armazón visual que [com.jvillada.movi.ui.credits.CreditBalanceSheet]: fondo oscuro
 * clickeable para cerrar, panel con esquinas redondeadas arriba y [SheetHandleWithClose] (F37).
 * Se duplica acá en vez de importarse — mismo criterio que [com.jvillada.movi.ui.accounts.CreateAccountSheet],
 * cada pantalla trae sus propios helpers de hoja.
 */
@Composable
private fun BottomSheetScaffold(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = dismissEnabled, onClick = onDismiss),
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
            SheetHandleWithClose(onClose = onDismiss, enabled = dismissEnabled)
            content()
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MinTextMute,
        letterSpacing = 0.5.sp,
    )
}

@Composable
private fun CategoryRow(icon: String, name: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, fontSize = 18.sp)
        Text(name, fontSize = 14.5.sp, color = MinText, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MinPrimary, modifier = Modifier.size(16.dp))
    }
}

/**
 * Cambia la categoría de un movimiento ya registrado.
 *
 * Lista las [PREDEFINED_CATEGORIES] del mismo lado que el movimiento (gasto vs. ingreso):
 * cambiar un gasto a una categoría de ingreso no significa nada en Movi. Elegir una llama a
 * [com.jvillada.movi.shared.repository.WalletRepository.updateEventCategory] — el server
 * recalcula `countsAsCashFlow`, esta hoja nunca lo manda.
 *
 * F35: a propósito NO usa [com.jvillada.movi.ui.components.CategoryField] (texto libre con
 * sugerencias), aunque las otras tres hojas de categoría de la app sí — acá se elige entre el
 * catálogo para recategorizar UN movimiento que ya existe, no se escribe una categoría nueva.
 * Además ya resuelve un caso que el campo libre no cubre: la categoría actual del movimiento
 * cuando no está en el catálogo (viene de un extracto importado, ver `currentIsKnown` abajo) se
 * agrega como opción marcada, algo que no tendría sentido en un campo de texto.
 */
@Composable
fun ChangeCategorySheet(
    event: FinancialEvent,
    onDismiss: () -> Unit,
    onCategoryChanged: (FinancialEvent) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val options = remember(event.type) {
        PREDEFINED_CATEGORIES.filter { it.type == event.type.name || it.type == "BOTH" }
    }
    // Los extractos importados traen categorías libres del parser (ver ClaudeStatementParser)
    // que pueden no estar en el catálogo. Si la actual no aparece en `options`, se agrega igual
    // como la opción ya marcada — perderla acá sería más confuso que una entrada de más.
    val currentIsKnown = options.any { it.name == event.category }

    fun choose(category: String) {
        if (category == event.category || saving) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.updateEventCategory(event.id, category) }
            saving = false
            result.onSuccess { onCategoryChanged(it) }.onFailure { error = it.toUserMessage() }
        }
    }

    BottomSheetScaffold(onDismiss = onDismiss, dismissEnabled = !saving) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            SheetLabel("CAMBIAR CATEGORÍA")
            Spacer(Modifier.height(8.dp))
            Text(event.description, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
            Spacer(Modifier.height(16.dp))

            if (!currentIsKnown) {
                CategoryRow(icon = "🏷️", name = event.category, selected = true, enabled = false, onClick = {})
                Hairline()
            }
            options.forEachIndexed { i, cat ->
                CategoryRow(
                    icon = cat.icon,
                    name = cat.name,
                    selected = cat.name == event.category,
                    enabled = !saving,
                    onClick = { choose(cat.name) },
                )
                if (i < options.size - 1) Hairline()
            }

            if (saving) {
                Spacer(Modifier.height(10.dp))
                Text("Guardando…", fontSize = 12.sp, color = MinTextMute)
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, color = MinExpense)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Candidatos que el server propone como pago de extracto de tarjeta (`looksLikeCardPayment`
 * en el server) pero que todavía no están marcados con [CARD_PAYMENT_CATEGORY]. **Cada uno se
 * confirma por separado** — no hay botón de "marcar todos": es la promesa central de esta
 * feature (el dueño decide, Movi no adivina en bloque).
 *
 * Cada fila tiene dos botones: "Marcar" (confirma que sí es el pago, recategoriza) y "No es"
 * (descarta el candidato para siempre — ver [com.jvillada.movi.shared.repository.WalletRepository.dismissCardPaymentCandidate]
 * — **sin tocar la categoría**: el gasto sigue contando como flujo de caja del mes). Si "No es"
 * fue un error, no hay forma de deshacerlo acá: el movimiento sigue en Movimientos y se
 * recategoriza a mano desde ahí con [ChangeCategorySheet].
 */
@Composable
fun CardPaymentCandidatesSheet(
    candidates: List<FinancialEvent>,
    onDismiss: () -> Unit,
    /** Recibe el id confirmado: quien llama tiene que poder descartarlo aunque el refetch falle. */
    onConfirmed: (String) -> Unit,
    /** Recibe el id de un "No es": mismo motivo que [onConfirmed] — el refetch puede fallar. */
    onDismissedCandidate: (String) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var remaining by remember(candidates) { mutableStateOf(candidates) }
    var savingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun confirm(event: FinancialEvent) {
        if (savingId != null) return
        savingId = event.id
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.updateEventCategory(event.id, CARD_PAYMENT_CATEGORY) }
            savingId = null
            result.onSuccess {
                remaining = remaining.filterNot { it.id == event.id }
                onConfirmed(event.id)
            }.onFailure { error = it.toUserMessage() }
        }
    }

    fun dismiss(event: FinancialEvent) {
        if (savingId != null) return
        savingId = event.id
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.dismissCardPaymentCandidate(event.id) }
            savingId = null
            result.onSuccess {
                remaining = remaining.filterNot { it.id == event.id }
                onDismissedCandidate(event.id)
            }.onFailure { error = it.toUserMessage() }
        }
    }

    BottomSheetScaffold(onDismiss = onDismiss, dismissEnabled = savingId == null) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            SheetLabel("PAGOS DE TARJETA SIN MARCAR")
            Spacer(Modifier.height(8.dp))
            Text(
                "Marcarlos como \"$CARD_PAYMENT_CATEGORY\" evita contar esta plata dos veces: " +
                    "ya se contó como gasto el día que se compró con la tarjeta.",
                fontSize = 12.sp,
                color = MinTextMute,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(14.dp))

            if (remaining.isEmpty()) {
                Text("Ya no quedan pagos por confirmar.", fontSize = 13.sp, color = MinTextMute)
            }

            remaining.forEachIndexed { i, event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            event.description,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinText,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("hoy: ${event.category}", fontSize = 12.sp, color = MinTextMute)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatCOP(event.amount),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MinText,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    val isSavingThis = savingId == event.id
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MinBorderStrong, RoundedCornerShape(10.dp))
                                .clickable(enabled = savingId == null) { dismiss(event) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (isSavingThis) "…" else "No es",
                                color = MinTextMute,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSavingThis) MinTextFaint else MinText)
                                .clickable(enabled = savingId == null) { confirm(event) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (isSavingThis) "Marcando…" else "Marcar",
                                color = MinBg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (i < remaining.size - 1) Hairline()
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, color = MinExpense)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
