package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.MAX_ACCOUNT_CONDITION_LENGTH
import com.jvillada.movi.shared.model.normalizarCondicion
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** El título de la fila del detalle de la cuenta y de esta hoja (vuelta de diseño 5). */
const val PARA_QUE_ES_ESTA_PLATA: String = "Para qué es esta plata"

/** Lo que dice la fila de una cuenta sin condición: su plata se usa para lo que sea. */
const val PLATA_LIBRE: String = "Libre: para cualquier gasto"

/**
 * **El subtítulo de «Para qué es esta plata»**: el estado, en palabras. Sin condición,
 * [PLATA_LIBRE]; con una, para qué es y la consecuencia que tiene en la app («Solo para vivienda ·
 * no suma en «Tu plata»»). Misma regla que [esDeUsoCondicionado]: una condición en blanco es libre.
 */
fun subtituloDeParaQueEs(account: Account): String =
    if (esDeUsoCondicionado(account)) "Solo para ${account.condicionadaA!!.trim()} · no suma en «Tu plata»"
    else PLATA_LIBRE

/**
 * «Para qué es esta plata» — el único camino por el que el dueño puede marcar una cuenta como
 * condicionada (de uso condicionado, ver [esDeUsoCondicionado]).
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
            } catch (e: Throwable) {
                error = e.toUserMessage()
                guardando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !guardando) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
        ) {
            Text(
                text = PARA_QUE_ES_ESTA_PLATA,
                style = Movi.textos.titulo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                modifier = Modifier.padding(top = Espacio.xxs, bottom = Espacio.s),
            )

            // Qué hace, dicho con las dos consecuencias que tiene y sin ninguna más: es
            // exactamente lo que el campo cambia en la app, y decir de más acá sería
            // prometerle al dueño un efecto que no existe.
            Text(
                text = "Si esta plata es libre, déjalo vacío. Si solo puedes usarla para algo " +
                    "—una pensión voluntaria, las cesantías, una cuenta AFC—, escribe para qué: " +
                    "esta cuenta deja de sumar en «Tu plata» y aparece en su propio renglón. " +
                    "Tu patrimonio no cambia: la plata sigue siendo tuya.",
                style = Movi.textos.cuerpo,
                // Un párrafo de explicación: con el peso de una fila se leería pesado.
                fontWeight = FontWeight.Normal,
                color = Movi.colores.textoMedio,
            )

            Spacer(Modifier.height(Espacio.m))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radio.m))
                    .background(Movi.colores.tarjeta)
                    .border(Medida.trazo, Movi.colores.borde, RoundedCornerShape(Radio.m))
                    .padding(horizontal = Espacio.s, vertical = Espacio.s),
            ) {
                if (texto.isEmpty()) {
                    Text("Vivienda", style = Movi.textos.cuerpo, color = Movi.colores.textoApagado, maxLines = 1)
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
                    textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                    cursorBrush = SolidColor(Movi.colores.texto),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    enabled = !guardando,
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                )
            }

            Spacer(Modifier.height(Espacio.xs))
            Text(
                text = if (account.condicionadaA != null) {
                    "Déjalo vacío para que vuelva a ser libre: para cualquier gasto."
                } else {
                    "Vacío es libre: para cualquier gasto."
                },
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
            )

            if (error != null) {
                Spacer(Modifier.height(Espacio.s))
                Text(text = error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }

            Spacer(Modifier.height(Espacio.l))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                Boton(
                    "Cancelar",
                    onClick = onDismiss,
                    variante = VarianteDeBoton.Secundario,
                    habilitado = !guardando,
                    modifier = Modifier.weight(1f),
                )
                Boton(
                    "Guardar",
                    onClick = { guardar() },
                    habilitado = cambio,
                    cargando = guardando,
                    modifier = Modifier.weight(1.4f),
                )
            }

            Spacer(Modifier.height(Espacio.xl))
        }
    }
}
