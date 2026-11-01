package com.jvillada.movi.ui.sms

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.GrupoQueSaldaElAviso
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.formatCOP

/*
 * # «¿Salda Casa con Andrés?» en la tarjeta del aviso (vuelta 85)
 *
 * Antes la pregunta aparecía solo después de confirmar, en otra tarjeta. Ahora la tarjeta del aviso la
 * dice mientras espera, con lo que se deben. **Solo informa**: confirmar anota el ingreso como siempre,
 * y saldar sigue siendo «Sí, saldar» sobre ese movimiento ya anotado (la propuesta de siempre, que
 * aparece en «Por revisar» al confirmar). Nada se salda solo.
 */

/** El tag del renglón. */
const val TAG_GRUPO_QUE_SALDA: String = "aviso:grupo-que-salda"

/**
 * «¿Salda Casa con Andrés? Andrés te debe $539.625. Al confirmarlo, Movi te lo pregunta.» o, si el
 * dueño es quien paga, «… Le debes $X a Andrés. …»; si ya marcó saldado y este es el giro que ese saldado
 * esperaba, «¿Es el saldado de Casa con Andrés? …». Pura.
 */
fun textoDelGrupoQueSalda(grupo: GrupoQueSaldaElAviso): String {
    val quien = grupo.quien.ifBlank { "la otra persona" }
    // Vuelta 66: ya se marcó «saldado» y esta es la plata que ese saldado esperaba.
    if (grupo.yaMarcadoSaldado) {
        return "¿Es el saldado de ${grupo.nombreDelEspacio}? Marcaste ${formatCOP(grupo.debe)} con $quien como saldado. " +
            "Al confirmarlo, Movi te lo pregunta."
    }
    val deuda = if (grupo.entra) "$quien te debe ${formatCOP(grupo.debe)}." else "Le debes ${formatCOP(grupo.debe)} a $quien."
    return "¿Salda ${grupo.nombreDelEspacio}? $deuda Al confirmarlo, Movi te lo pregunta."
}

/** El renglón de [textoDelGrupoQueSalda], en la letra de apoyo, entre la evidencia y los botones. */
@Composable
internal fun LineaDelGrupoQueSalda(grupo: GrupoQueSaldaElAviso) {
    Text(
        textoDelGrupoQueSalda(grupo),
        style = Movi.textos.apoyo,
        color = Movi.colores.texto,
        modifier = Modifier.testTag(TAG_GRUPO_QUE_SALDA),
    )
}
