package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.MAX_ACCOUNT_CONDITION_LENGTH
import com.jvillada.movi.shared.model.normalizarCondicion
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * «¿Esta plata solo se puede usar para algo?» — el único camino por el que el dueño puede marcar
 * una cuenta como condicionada.
 *
 * **Existe porque el campo nacía muerto.** `Account.condicionadaA` se podía guardar solo al crear
 * la cuenta, y la cuenta que motivó el campo —la pensión voluntaria en Skandia, $106.000.000 que
 * solo puede retirar para vivienda sin perder el beneficio tributario— ya existía desde antes.
 * Sin esta hoja, ni desplegando el cálculo el dueño podía marcarla: había que tocar la base de
 * datos a mano. Es la regla dura del proyecto — nada de ajustes que solo se cambien tocando
 * código, porque los demás usuarios no tienen a un desarrollador al lado.
 *
 * **Texto libre y no una lista**, por lo mismo que el campo del modelo: en Colombia el mismo caso
 * son las cesantías, una AFC, un fondo voluntario, y quien conoce la condición de su producto es
 * el dueño, no Movi. Vacío = sin condición, y esa plata vuelve a «Tu plata».
 */
@Composable
fun CondicionDeCuentaSheet(
    account: Account,
    onDismiss: () -> Unit,
    onGuardada: (Account) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var texto by remember { mutableStateOf(account.condicionadaA.orEmpty()) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val normalizada = normalizarCondicion(texto)
    val cambio = normalizada != account.condicionadaA

    fun guardar() {
        if (guardando) return
        guardando = true
        error = null
        coroutine.launch {
            try {
                onGuardada(Repositories.wallets.updateAccountCondition(account.id, normalizada))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUserMessage()
                guardando = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !guardando, onClick = onDismiss),
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
            SheetHandleWithClose(onClose = onDismiss, enabled = !guardando)

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
            ) {
                Text(
                    text = "¿Esta plata solo sirve para algo?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    letterSpacing = (-0.2).sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )

                // Qué hace, dicho con las dos consecuencias que tiene y sin ninguna más: es
                // exactamente lo que el campo cambia en la app, y decir de más acá sería
                // prometerle al dueño un efecto que no existe.
                Text(
                    text = "Hay plata tuya que no puedes usar para cualquier cosa: una pensión " +
                        "voluntaria, las cesantías, una cuenta AFC. Si escribes para qué sirve, " +
                        "esta cuenta deja de sumar en «Tu plata» y aparece en su propio renglón. " +
                        "Tu patrimonio no cambia: la plata sigue siendo tuya.",
                    fontSize = 13.5.sp,
                    color = MinTextMute,
                    lineHeight = 19.sp,
                )

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MinSurfaceContainerLow)
                        .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    if (texto.isEmpty()) {
                        Text("Vivienda", fontSize = 14.sp, color = MinTextFaint, maxLines = 1)
                    }
                    // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver
                    // [esAtajoDeSeleccionarTodo]. El recorte de abajo sigue siendo el de siempre:
                    // el campo avisa el texto, la pantalla decide con cuánto se queda.
                    val campo = rememberCampoConSeleccion(texto) {
                        // El recorte va acá y no solo al guardar: un campo que acepta teclas que
                        // después se tiran en silencio es peor que uno que deja de aceptarlas.
                        texto = it.take(MAX_ACCOUNT_CONDITION_LENGTH)
                    }
                    BasicTextField(
                        value = campo.valor,
                        onValueChange = campo::alCambiar,
                        textStyle = TextStyle(fontSize = 14.sp, color = MinText),
                        cursorBrush = SolidColor(MinText),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        singleLine = true,
                        enabled = !guardando,
                        modifier = Modifier.fillMaxWidth()
                            .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (account.condicionadaA != null) {
                        "Déjalo vacío para quitar la condición."
                    } else {
                        "Déjalo vacío si puedes usar esta plata para lo que quieras."
                    },
                    fontSize = 11.5.sp,
                    color = MinTextFaint,
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
                            .clickable(enabled = !guardando, onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancelar", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (cambio && !guardando) MinPrimaryContainer else MinSurfaceContainerLow)
                            .clickable(enabled = cambio && !guardando) { guardar() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (guardando) "Guardando…" else "Guardar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (cambio && !guardando) MinOnPrimaryContainer else MinTextFaint,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
