package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun VoidEventSheet(
    event: FinancialEvent,
    onDismiss: () -> Unit,
    onVoided: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var voiding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doVoid() {
        if (voiding) return
        voiding = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.voidEvent(event.id, reason.trim().ifBlank { null })
                onVoided()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUserMessage()
                voiding = false
            }
        }
    }

    val isIncome = event.type == TransactionType.INCOME
    val signedAmount = "${if (isIncome) "+" else "−"}${formatMoney(event.amount, event.currency)}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !voiding, onClick = onDismiss),
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
            SheetHandleWithClose(onClose = onDismiss, enabled = !voiding)
            // El contenido de la hoja se desplaza.
            //
            // Estas hojas nacieron sin `verticalScroll` y funcionaban de casualidad: con el teclado
            // abierto en un teléfono chico, o con la lista un poco más larga, el contenido se salía por
            // abajo y el botón de guardar quedaba fuera de la pantalla, recortado por el `clip` de la
            // propia hoja. Sin manera de llegar a él.
            //
            // `weight(1f, fill = false)` es lo que hace que la hoja **crezca con su contenido** hasta el
            // borde de la pantalla y recién ahí desplace, en vez de ocupar siempre todo el alto. Mismo
            // patrón que las hojas de `CategorySheets.kt`, que ya lo tenían.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
            ) {

                // Event summary card
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.description,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinText,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${event.category} · ${event.source.name}",
                                fontSize = 11.sp,
                                color = MinTextMute,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        MonoText(
                            text = signedAmount,
                            fontSize = 14f,
                            color = if (isIncome) MinIncome else MinText,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Reason label
                Text(
                    text = "MOTIVO (OPCIONAL)",
                    fontSize = 11.sp,
                    color = MinTextMute,
                    letterSpacing = 0.4.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))

                // Reason input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MinSurfaceContainerLow)
                        .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    BasicTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        cursorBrush = SolidColor(MinText),
                        textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (reason.isEmpty()) {
                                Text("Ej: Movimiento duplicado", fontSize = 14.sp, color = MinTextMute)
                            }
                            inner()
                        },
                    )
                }

                // Inline error
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = error!!, fontSize = 12.sp, color = MinExpense)
                }

                Spacer(Modifier.height(20.dp))

                // CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (!voiding) MinExpenseContainer else MinSurfaceContainerLow)
                        .clickable(enabled = !voiding) { doVoid() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (voiding) "Anulando…" else "Anular movimiento",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!voiding) MinExpense else MinTextFaint,
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}
