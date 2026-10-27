package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi

/** Tag estable del texto de una respuesta del asistente. */
const val TAG_TEXTO_DEL_ASISTENTE: String = "ai-texto-del-asistente"

/** Tag estable de la viñeta o el número de un ítem de lista. */
const val TAG_MARCA_DE_LISTA: String = "ai-marca-de-lista"

/**
 * **Una respuesta de Movi AI, con formato** (vuelta de diseño 12). Los bloques salen de
 * [bloquesDeTexto]; acá se pintan con el sistema:
 *
 * - **Párrafo**: `cuerpo` en el color del texto; entre bloques, [Espacio.s].
 * - **Lista**: cada ítem en su renglón, con la viñeta («•») o el número a la izquierda en
 *   `textoMedio`, una sangría de [Espacio.xs] (y [Espacio.m] más para una sublista) y [Espacio.xxs]
 *   entre ítems. El texto de un ítem largo se alinea debajo de sí mismo, no debajo de la viñeta.
 * - **Negrita**: `SemiBold`, el peso de los títulos del sistema.
 * - **Cifras en pesos**: en la letra de las cifras (`monto`: Martian Mono, tabular), como en el
 *   resto de la app.
 *
 * Un texto que no trae formato se ve como antes: un párrafo.
 */
@Composable
internal fun TextoDelAsistente(texto: String, modifier: Modifier = Modifier) {
    val bloques = remember(texto) { bloquesDeTexto(texto) }
    val estilo = Movi.textos.cuerpoRegular.copy(color = Movi.colores.texto)
    val enfasis = Movi.textos.enfasis
    val monto = Movi.textos.monto
    val cifra = SpanStyle(
        fontFamily = monto.fontFamily,
        fontSize = monto.fontSize,
        letterSpacing = monto.letterSpacing,
        fontFeatureSettings = monto.fontFeatureSettings,
    )
    Column(
        modifier = modifier.testTag(TAG_TEXTO_DEL_ASISTENTE),
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        if (bloques.isEmpty()) {
            Text(text = texto, style = estilo)
        }
        bloques.forEach { bloque ->
            when (bloque) {
                is BloqueDeTexto.Parrafo -> Text(text = anotado(bloque.tramos, cifra, enfasis), style = estilo)
                is BloqueDeTexto.Lista -> Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                    bloque.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .padding(start = Espacio.xs + Espacio.m * item.nivel)
                                // El lector de pantalla lee el ítem entero: «1. Vivienda $1.500.000».
                                .semantics(mergeDescendants = true) {},
                            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                        ) {
                            Text(
                                text = item.marca,
                                style = estilo,
                                color = Movi.colores.textoMedio,
                                modifier = Modifier.widthIn(min = Espacio.s).testTag(TAG_MARCA_DE_LISTA),
                            )
                            Text(text = anotado(item.tramos, cifra, enfasis), style = estilo, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Los tramos como un texto con estilos: la negrita y las cifras. */
private fun anotado(tramos: List<Tramo>, cifra: SpanStyle, enfasis: SpanStyle): AnnotatedString = buildAnnotatedString {
    tramos.forEach { tramo ->
        val estilo = when {
            tramo.cifra && tramo.negrita -> cifra.merge(enfasis)
            tramo.cifra -> cifra
            tramo.negrita -> enfasis
            else -> null
        }
        if (estilo == null) append(tramo.texto) else withStyle(estilo) { append(tramo.texto) }
    }
}
