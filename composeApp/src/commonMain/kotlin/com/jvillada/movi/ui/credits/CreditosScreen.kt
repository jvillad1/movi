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
import com.jvillada.movi.shared.model.Credit
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

@Composable
fun CreditosScreen(onNavigate: (Screen) -> Unit) {
    var credits by remember { mutableStateOf<List<Credit>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getCredits() }
            .onSuccess { credits = it }
    }
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
                    Text("\$160.040.000", fontSize = 36.sp, fontFamily = FontFamily.Monospace, color = MinText, letterSpacing = (-1.4).sp, lineHeight = 36.sp)
                    Spacer(Modifier.height(20.dp))
                    Hairline()
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Próximo pago", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text("\$1.860.000", fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
                            Text("en 2 días", fontSize = 11.5.sp, color = MinWarn)
                        }
                        Box(modifier = Modifier.width(1.dp).height(48.dp).background(MinHairline))
                        Spacer(Modifier.width(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pagado YTD", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text("\$96.280.000", fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinIncome)
                            Text("−37,5% deuda", fontSize = 11.5.sp, color = MinTextMute)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Mis créditos", count = 3)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        credits.forEach { c ->
                            val pct = c.paid.toFloat() / c.total.toFloat()
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(18.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(c.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.1).sp)
                                    Text(c.rate, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
                                }
                                Text(c.bank, fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row {
                                        Text(formatCOP(c.paid), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                                        Text(" / ${formatCOP(c.total)}", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MinTextMute, letterSpacing = (-0.3).sp)
                                    }
                                    Text("${(pct * 100).toInt()}%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
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
                                Spacer(Modifier.height(14.dp))
                                Hairline()
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Próxima cuota · ${c.nextDate}", fontSize = 12.sp, color = MinTextMute)
                                    Text(c.nextAmt, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
                                }
                            }
                        }
                    }
                }
            }
        }

        MinBottomNav(active = NavTab.ANALYSIS, onTabSelected = { tab ->
            when (tab) {
                NavTab.HOME -> onNavigate(Screen.Dashboard)
                NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                NavTab.ANALYSIS -> onNavigate(Screen.Analisis)
                NavTab.PROFILE -> onNavigate(Screen.Profile)
            }
        })
        NavPill()
    }
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
