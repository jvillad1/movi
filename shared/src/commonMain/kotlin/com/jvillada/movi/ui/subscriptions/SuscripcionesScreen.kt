package com.jvillada.movi.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun SuscripcionesScreen(onNavigate: (Screen) -> Unit) {
    val goBack = LocalGoBack.current
    val coroutine = rememberCoroutineScope()
    var result by remember { mutableStateOf(SubscriptionsResult(emptyList(), 0)) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        runCatching { Repositories.wallets.getSubscriptions() }
            .onSuccess { result = it; error = null }
            .onFailure { error = it.toUserMessage() }
    }

    fun rescan() {
        if (scanning) return
        scanning = true
        error = null
        coroutine.launch {
            runCatching { Repositories.wallets.detectSubscriptions() }
                .onSuccess { result = it }
                .onFailure { error = it.toUserMessage() }
            scanning = false
        }
    }

    fun setStatus(sub: Subscription, status: SubStatus) {
        coroutine.launch {
            runCatching { Repositories.wallets.updateSubscription(sub.id, sub.copy(status = status)) }
                .onSuccess { reloadKey++ }
                .onFailure { error = it.toUserMessage() }
        }
    }

    val candidates = result.subscriptions.filter { it.status == SubStatus.CANDIDATE }
    val active = result.subscriptions
        .filter { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED }
        .sortedBy { it.dayOfMonth }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // F22: ya volvía a Más a mano (era la única correcta) — ahora usa la pila real igual.
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = MinText, modifier = Modifier.size(22.dp).clickable { goBack(Screen.Mas) })
            Text("Suscripciones", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, modifier = Modifier.weight(1f))
            Text(
                if (scanning) "Escaneando…" else "Re-escanear",
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (scanning) MinTextMute else MinText,
                modifier = Modifier.clickable(enabled = !scanning) { rescan() },
            )
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(22.dp),
                ) {
                    Text("Total mensual", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        formatCOP(result.monthlyTotalCop),
                        fontSize = 36.sp, fontFamily = FontFamily.Monospace, color = MinText,
                        letterSpacing = (-1.4).sp, lineHeight = 36.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${active.size} activas", fontSize = 12.sp, color = MinTextMute)
                }
            }

            error?.let { msg ->
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(msg, fontSize = 12.sp, color = MinExpense, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }

            if (candidates.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        // F39: nada nace activo — todo lo que el escaneo detecta (sin importar
                        // la confianza) cae acá primero. El dueño confirma o dice "No es" de a
                        // una; lo que se descarta no vuelve a proponerse (ver SubscriptionSync,
                        // que respeta DISMISSED en cada re-scan).
                        MinSectionHeader(title = "Detectadas · por confirmar", count = candidates.size)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            candidates.forEach { s ->
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
                                        Text(s.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                        Text(formatMoney(s.amount, s.currency), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
                                    }
                                    Text("Visto ${s.occurrences} ${if (s.occurrences == 1) "mes" else "meses"} · día ${s.dayOfMonth}", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ActionChip("Confirmar", primary = true) { setStatus(s, SubStatus.CONFIRMED) }
                                        ActionChip("No es", primary = false) { setStatus(s, SubStatus.DISMISSED) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Activas", count = if (active.isNotEmpty()) active.size else null)
                    if (active.isEmpty()) {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                        ) {
                            Text(
                                "Sin suscripciones aún — importa 2-3 meses de extractos de tarjeta y toca Re-escanear",
                                fontSize = 14.sp, color = MinTextMute,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        active.forEach { s ->
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
                                    Text(s.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                    Text(formatMoney(s.amount, s.currency), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Cobro el día ${s.dayOfMonth}${if (s.status == SubStatus.AUTO) " · auto" else ""}", fontSize = 12.sp, color = MinTextMute)
                                    Text("Quitar", fontSize = 12.sp, color = MinExpense, modifier = Modifier.clickable { setStatus(s, SubStatus.DISMISSED) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) MinText else MinSurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (primary) MinBg else MinText)
    }
}
