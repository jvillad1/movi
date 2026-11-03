package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Casilla

/*
 * # Vuelta 124 · «¿Ya pagaste el de este período?» fuera de la hoja
 *
 * La vuelta 121 la puso en la hoja de un pago fijo nuevo (`CreateRecurringRuleSheet`), como una [Fila] en
 * su superficie. La guía de primer uso (el formulario de «Lo que pagas cada mes») y la tarjeta de lo que
 * propone Movi AI la necesitan **dentro** de un bloque que ya tiene su margen, donde una [Fila] sumaría el
 * suyo: por eso acá va como un renglón sin margen propio, con las MISMAS palabras de la hoja y la misma
 * [Casilla] («Sí» por defecto). Cuándo se pregunta lo decide siempre `sePreguntaSiYaPagasteElDeEstePeriodo`
 * (`:core`), y la respuesta viaja en `RecurringRule.yaPagoElDeEstePeriodo`.
 */

/**
 * El renglón de la pregunta: el título y, debajo, qué hace Movi con la respuesta; la [Casilla] al final es
 * el toque (48 × 48, con su estado para el lector). [tag] va en la casilla.
 */
@Composable
fun PreguntaDeYaPagaste(
    marcada: Boolean,
    onCambiar: (Boolean) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    habilitada: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(YA_PAGASTE_EL_DE_ESTE_PERIODO, style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Text(
                if (marcada) LO_ESPERA_DESDE_EL_PROXIMO_PERIODO else LO_ESPERA_EN_ESTE_PERIODO,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }
        Spacer(Modifier.width(Espacio.xs))
        Casilla(marcada = marcada, onCambiar = onCambiar, habilitada = habilitada, modifier = Modifier.testTag(tag))
    }
}
