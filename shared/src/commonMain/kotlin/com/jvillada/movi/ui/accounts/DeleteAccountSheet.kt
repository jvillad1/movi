package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.SheetHandleWithClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * F55: confirmación de "Eliminar cuenta" — mismo esqueleto de hoja que [VoidEventSheet], pero
 * acá no hay reintento posible desde la app: si `deleteAccount` falla se muestra un mensaje
 * fijo en vez de [com.jvillada.movi.ui.components.toUserMessage] (ver el porqué en
 * [com.jvillada.movi.shared.repository.LocalRepository.deleteAccount]) — la causa casi
 * siempre es de red, así que un mensaje genérico ("Algo salió mal") no ayudaría tanto como
 * decir directamente qué revisar.
 */
@Composable
fun DeleteAccountSheet(
    accountId: String,
    accountName: String,
    eventCount: Int,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doDelete() {
        if (deleting) return
        deleting = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.deleteAccount(accountId)
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se pudo eliminar — revisa tu conexión"
                deleting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !deleting, onClick = onDismiss),
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
            SheetHandleWithClose(onClose = onDismiss, enabled = !deleting)

            Text(
                text = "Eliminar cuenta",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )

            // F55: la consecuencia real, con el conteo real — no un genérico "¿estás seguro?".
            Text(
                text = "Se borra \"$accountName\" y ${eventCountLabel(eventCount)}. " +
                    "Esto no se puede deshacer.",
                fontSize = 14.sp,
                color = MinTextMute,
                lineHeight = 19.sp,
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = error!!, fontSize = 12.sp, color = MinExpense)
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MinSurfaceContainerLow)
                        .clickable(enabled = !deleting, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancelar", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText)
                }
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (!deleting) MinExpenseContainer else MinSurfaceContainerLow)
                        .clickable(enabled = !deleting) { doDelete() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (deleting) "Eliminando…" else "Eliminar cuenta",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!deleting) MinExpense else MinTextFaint,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

private fun eventCountLabel(count: Int): String =
    if (count == 1) "su 1 movimiento" else "sus $count movimientos"
