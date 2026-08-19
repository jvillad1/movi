package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

/**
 * F20 — Créditos es «todo lo que debes»: préstamos (cuota, tasa, plazo) y tarjetas de crédito
 * (cupo, corte, día de pago), con **un solo total de deuda** arriba que suma ambos — la misma
 * suma que muestra el acceso «Créditos» del Inicio (ver [totalDebtCop]).
 */
@Composable
fun CreditosScreen(onNavigate: (Screen) -> Unit) {
    val goBack = LocalGoBack.current
    var credits by remember { mutableStateOf<List<CreditSummary>>(emptyList()) }
    var cards by remember { mutableStateOf<List<CardSummary>>(emptyList()) }
    var showTypeChooser by remember { mutableStateOf(false) }
    var showLoanSheet by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<CreditSummary?>(null) }
    var showCardSheet by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<CardSummary?>(null) }
    var adjusting by remember { mutableStateOf<CreditSummary?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    // Ola 2 #6: mismo guard que ya usaba Recurrentes — sin esto el botón ancho de "vacío"
    // parpadeaba un instante antes de que llegaran los créditos reales.
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(reloadKey) {
        loading = true
        val loans = launch { runCatching { Repositories.wallets.getCredits() }.onSuccess { credits = it } }
        val tarjetas = launch { runCatching { Repositories.wallets.getCards() }.onSuccess { cards = it } }
        loans.join()
        tarjetas.join()
        loading = false
    }
    val isEmpty = credits.isEmpty() && cards.isEmpty()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(MinBg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // F22: Créditos vive en Más — destino de reserva si no hay historial
                // (antes caía siempre en Inicio, aunque entraras desde Más).
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = MinText, modifier = Modifier.size(22.dp).clickableSimple { goBack(Screen.Mas) })
                Text("Créditos", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, modifier = Modifier.weight(1f))
                // F18: compacto arriba a la derecha cuando ya hay deudas; vacío, el botón de
                // abajo (ver el bloque bajo el header) es la acción principal.
                if (!isEmpty) {
                    NewItemButton(label = "Nuevo crédito", onClick = { showTypeChooser = true })
                }
            }
            if (isEmpty && !loading) {
                NewItemButton(
                    label = "Nuevo crédito",
                    onClick = { showTypeChooser = true },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp),
                    full = true,
                )
            }

            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text("Deuda total", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        // F20: préstamos + tarjetas — la MISMA función que usa el Inicio.
                        Text(formatCOP(totalDebtCop(credits, cards)), fontSize = 36.sp, fontFamily = FontFamily.Monospace, color = MinText, letterSpacing = (-1.4).sp, lineHeight = 36.sp)
                    }
                }

                if (isEmpty) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Mis créditos")
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                Text(
                                    "Sin créditos registrados",
                                    fontSize = 14.sp, color = MinTextMute,
                                )
                            }
                        }
                    }
                }

                if (credits.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Préstamos", count = credits.size)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                credits.forEach { c ->
                                    LoanCard(
                                        credit = c,
                                        onEdit = { editingLoan = c; showLoanSheet = true },
                                        onAdjust = { adjusting = c },
                                    )
                                }
                            }
                        }
                    }
                }

                if (cards.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Tarjetas", count = cards.size)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                cards.forEach { c ->
                                    CreditCardCard(card = c, onEdit = { editingCard = c; showCardSheet = true })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showTypeChooser) {
            DebtTypeChooserSheet(
                onDismiss = { showTypeChooser = false },
                onLoan = { showTypeChooser = false; editingLoan = null; showLoanSheet = true },
                onCard = { showTypeChooser = false; editingCard = null; showCardSheet = true },
            )
        }
        if (showLoanSheet) {
            CreditTermsSheet(
                editing = editingLoan,
                candidates = credits.filter { it.terms == null }.map { it.account },
                onDismiss = { showLoanSheet = false },
                onSaved = { showLoanSheet = false; reloadKey++ },
            )
        }
        if (showCardSheet) {
            CardTermsSheet(
                editing = editingCard,
                onDismiss = { showCardSheet = false },
                onSaved = { showCardSheet = false; reloadKey++ },
            )
        }
        adjusting?.let { credit ->
            CreditBalanceSheet(
                credit = credit,
                onDismiss = { adjusting = null },
                onSaved = { adjusting = null; reloadKey++ },
            )
        }
    }
}

/** Tarjeta de un préstamo: cuota, tasa, plazo y progreso — lo que Créditos mostraba desde siempre. */
@Composable
private fun LoanCard(
    credit: CreditSummary,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
) {
    val pct = (credit.paidPct ?: 0.0).toFloat()
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
        onClick = onEdit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(credit.account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.1).sp)
            Text(credit.terms?.let { "${it.rateEa}% EA" } ?: "", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
        }
        Text(credit.terms?.bank ?: "Sin términos registrados", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatCOP(credit.account.balance), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
            Text("${(pct * 100).toInt()}% pagado", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
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
                    .fillMaxWidth(pct)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MinText.copy(alpha = 0.9f))
            )
        }
        credit.terms?.let { t ->
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Cuota · día ${t.dayOfMonth}", fontSize = 12.sp, color = MinTextMute)
                Text(formatCOP(t.installment), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
            }
        }
        // La deuda es estado (se mueve a diario por intereses), no
        // contrato: por eso cuadrarla con el banco vive fuera de la
        // hoja de términos.
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "Ajustar saldo",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MinTextMute,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickableSimple(onAdjust)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Tarjeta de una tarjeta de crédito (F20): deuda actual, cupo disponible si hay, corte y día de
 * pago. La deuda se muestra en la moneda de la tarjeta ([formatMoney]) — una Mastercard en USD
 * debe dólares, y su componente COP sería $0, un número que miente.
 */
@Composable
private fun CreditCardCard(card: CardSummary, onEdit: () -> Unit) {
    val currency = card.account.currency
    val debt = card.account.balancesByCurrency[currency] ?: card.account.balance
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
        onClick = onEdit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(card.account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.1).sp)
            if (currency != "COP") {
                Text(currency, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
            }
        }
        Text(card.terms?.bank ?: "Sin corte ni pago — tócala para completarlos", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatMoney(debt, currency), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
            card.available?.let {
                Text("Disponible ${formatMoney(it, currency)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
            }
        }
        card.terms?.let { t ->
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(t.cutoffDay?.let { "Corte · día $it" } ?: "Sin día de corte", fontSize = 12.sp, color = MinTextMute)
                Text("Pago · día ${t.paymentDay}", fontSize = 12.sp, color = MinTextMute)
            }
        }
    }
}

/**
 * Selector previo al alta (F20): un préstamo y una tarjeta no se crean igual — el préstamo
 * tiene capital, tasa y plazo; la tarjeta, cupo, corte y día de pago. Preguntar primero evita
 * una hoja única llena de campos que no aplican.
 */
@Composable
private fun DebtTypeChooserSheet(
    onDismiss: () -> Unit,
    onLoan: () -> Unit,
    onCard: () -> Unit,
) {
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
            SheetHandleWithClose(onClose = onDismiss)
            Text("¿Qué deuda quieres registrar?", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
            Spacer(Modifier.height(14.dp))
            DebtTypeOption(
                title = "Préstamo",
                subtitle = "Cuota fija, tasa y plazo — libranza, libre inversión, vehículo",
                onClick = onLoan,
            )
            Spacer(Modifier.height(8.dp))
            DebtTypeOption(
                title = "Tarjeta de crédito",
                subtitle = "Cupo, día de corte y día de pago",
                onClick = onCard,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DebtTypeOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MinSurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = MinTextMute)
    }
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
