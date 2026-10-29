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
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja

/** El título de la fila del detalle de la cuenta y de esta hoja (vuelta de diseño 5). */
const val PARA_QUE_ES_ESTA_PLATA: String = "Para qué es esta plata"

/**
 * Lo que dice la fila de una cuenta sin condición: su plata se usa para lo que sea. Vuelta 64: era
 * «Libre: para cualquier gasto», y en la misma pantalla el renglón de los apartados decía «Libre» por
 * lo que queda sin apartar. Ahora cada palabra tiene un solo sentido: esta dice que la cuenta no tiene
 * condición, y lo que queda sin apartar es [DISPONIBLE_EN_LA_CUENTA].
 */
const val PLATA_LIBRE: String = "Sin condición: para cualquier gasto"

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
            // Qué hace, dicho con las dos consecuencias que tiene y sin ninguna más: es
            // exactamente lo que el campo cambia en la app, y decir de más acá sería
            // prometerle al dueño un efecto que no existe. Es el subtítulo del encabezado.
            EncabezadoDeHoja(
                PARA_QUE_ES_ESTA_PLATA,
                subtitulo = "Si esta plata no tiene condición, déjalo vacío. Si solo puedes usarla para algo " +
                    "—una pensión voluntaria, las cesantías, una cuenta AFC—, escribe para qué: " +
                    "esta cuenta deja de sumar en «Tu plata» y aparece en su propio renglón. " +
                    "Tu patrimonio no cambia: la plata sigue siendo tuya.",
                modifier = Modifier.padding(top = Espacio.xxs),
            )

            Spacer(Modifier.height(Espacio.m))

            Campo(
                texto,
                // El recorte va acá y no solo al guardar: un campo que acepta teclas que después se
                // tiran en silencio es peor que uno que deja de aceptarlas.
                { texto = it.take(MAX_ACCOUNT_CONDITION_LENGTH) },
                rotulo = "Solo se puede usar para",
                guia = "Vivienda",
                ayuda = if (account.condicionadaA != null) {
                    "Déjalo vacío para que vuelva a ser sin condición: para cualquier gasto."
                } else {
                    "Vacío es sin condición: para cualquier gasto."
                },
                teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                habilitado = !guardando,
            )

            Spacer(Modifier.height(Espacio.l))

            PieDeHoja(
                texto = "Guardar",
                onClick = { guardar() },
                habilitado = cambio,
                cargando = guardando,
                error = error,
                secundaria = "Cancelar",
                onSecundaria = onDismiss,
                secundariaHabilitada = !guardando,
            )

            Spacer(Modifier.height(Espacio.xl))
        }
    }
}
