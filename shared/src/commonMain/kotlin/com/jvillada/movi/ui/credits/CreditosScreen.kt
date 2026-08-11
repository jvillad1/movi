package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun CreditosScreen(onNavigate: (Screen) -> Unit) {
    var credits by remember { mutableStateOf<List<CreditSummary>>(emptyList()) }
    var showSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CreditSummary?>(null) }
    var adjusting by remember { mutableStateOf<CreditSummary?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        runCatching { Repositories.wallets.getCredits() }
            .onSuccess { credits = it }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(MinBg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("‹", fontSize = 22.sp, color = MinText, modifier = Modifier.clickableSimple { onNavigate(Screen.Dashboard) })
                Text("Créditos", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, modifier = Modifier.weight(1f))
                Text("+", fontSize = 22.sp, color = MinText, modifier = Modifier.clickableSimple { editing = null; showSheet = true })
            }

            val totalDebt = credits.sumOf { it.account.balance }

            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text("Deuda total", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        Text(formatCOP(totalDebt), fontSize = 36.sp, fontFamily = FontFamily.Monospace, color = MinText, letterSpacing = (-1.4).sp, lineHeight = 36.sp)
                    }
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Mis créditos", count = if (credits.isNotEmpty()) credits.size else null)
                        if (credits.isEmpty()) {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                Text(
                                    "Sin créditos registrados — toca + para agregar el primero",
                                    fontSize = 14.sp, color = MinTextMute,
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            credits.forEach { c ->
                                val pct = (c.paidPct ?: 0.0).toFloat()
                                MinCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    variant = MinCardVariant.Elevated,
                                    padding = PaddingValues(18.dp),
                                    onClick = { editing = c; showSheet = true },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(c.account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.1).sp)
                                        Text(c.terms?.let { "${it.rateEa}% EA" } ?: "", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
                                    }
                                    Text(c.terms?.bank ?: "Sin términos registrados", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(formatCOP(c.account.balance), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
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
                                    c.terms?.let { t ->
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
                                                .clickableSimple { adjusting = c }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MinBottomNav(active = NavTab.MORE) { tab ->
                when (tab) {
                    NavTab.HOME         -> onNavigate(Screen.Dashboard)
                    NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                    NavTab.ADD          -> onNavigate(Screen.QuickAdd)
                    NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                    NavTab.MORE         -> onNavigate(Screen.Mas)
                }
            }
        }
        if (showSheet) {
            CreditTermsSheet(
                editing = editing,
                candidates = credits.filter { it.terms == null }.map { it.account },
                onDismiss = { showSheet = false },
                onSaved = { showSheet = false; reloadKey++ },
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

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
