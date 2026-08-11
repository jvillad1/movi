package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.MAX_CREDIT_DEBT_COP
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

/**
 * Cuadra la deuda de un crédito con el saldo que reporta el banco.
 *
 * Va aparte de [CreditTermsSheet] a propósito: los términos son el contrato (tasa, cuota,
 * plazo) y casi nunca cambian; la deuda es estado y se mueve todos los días por intereses.
 * Mezclarlos haría creer que editar la tasa recalcula lo que se debe.
 *
 * No edita un número: manda el saldo objetivo y el server registra la diferencia como un
 * movimiento visible de la cuenta, que es de donde sale la deuda.
 */
@Composable
fun CreditBalanceSheet(
    credit: CreditSummary,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val current = credit.account.balance

    var target by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // El techo se espeja del server (MAX_CREDIT_DEBT_COP, en :core) para poder explicar el
    // dedazo acá mismo en vez de mandarlo y traducir un 400. `toLongOrNull` además devuelve
    // null si se pegan tantos dígitos que no caben en Long — ese caso cae en el mismo aviso.
    val parsed = target.toLongOrNull()
    val overCap = parsed != null && parsed > MAX_CREDIT_DEBT_COP
    val delta = parsed?.let { it - current }
    val canSave = parsed != null && !overCap && delta != null && delta != 0L && !saving

    fun adjust() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                Repositories.wallets.adjustCreditBalance(credit.account.id, parsed!!)
            }
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
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .width(32.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinTextFaint),
            )

            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                SectionLabel("AJUSTAR SALDO")
                Spacer(Modifier.height(8.dp))
                Text(credit.account.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Deuda en Movi", fontSize = 13.sp, color = MinTextMute)
                    Text(
                        formatCOP(current),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MinText,
                    )
                }
                Spacer(Modifier.height(14.dp))

                FieldBox(
                    "Deuda real según el banco (COP)",
                    target,
                    { target = it.filter { ch -> ch.isDigit() } },
                    KeyboardType.Number,
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        target.isNotEmpty() && parsed == null ->
                            "Ese número es demasiado grande — revisá los dígitos."
                        overCap        -> "Saldo fuera de rango — revisá el monto."
                        parsed == null -> "Copiá el saldo que muestra la banca en línea hoy."
                        delta == 0L    -> "Ya coincide con Movi — no hay nada que registrar."
                        delta!! > 0L   -> "Se registrará un cargo de ${formatCOP(delta)} para subir la deuda."
                        else           -> "Se registrará un abono de ${formatCOP(-delta)} para bajar la deuda."
                    },
                    fontSize = 12.sp,
                    color = MinTextMute,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Queda como un movimiento visible en la cuenta, no como un número editado a mano.",
                    fontSize = 11.5.sp,
                    color = MinTextFaint,
                    lineHeight = 16.sp,
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
                    .clickable(enabled = canSave) { adjust() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (saving) "Registrando…" else "Registrar ajuste",
                    color = MinBg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
