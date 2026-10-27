package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.CirculoEsqueleto
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.VacioQueEnsena

/** Tag estable de un [EsqueletoDeFila]. */
const val TAG_ESQUELETO_DE_FILA: String = "ds-esqueleto-de-fila"

/**
 * **Un vacío que enseña**, desde el sistema: es `VacioQueEnsena` (la regla de la ola D: un vacío
 * dice qué va a aparecer y trae el botón que lo llena), con el mismo contrato. Está acá para que
 * una pantalla nueva lo encuentre junto a todo lo demás.
 *
 * ```
 * Vacio(
 *     titulo = "Todavía no hay movimientos",
 *     detalle = "Lo que anotes o llegue del banco aparece aquí.",
 *     accion = "Anotar el primero", onAccion = ::anotar,
 * )
 * ```
 */
@Composable
fun Vacio(
    titulo: String,
    detalle: String?,
    modifier: Modifier = Modifier,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
    icono: ImageVector? = null,
) {
    VacioQueEnsena(
        titulo = titulo,
        detalle = detalle,
        accion = accion,
        onAccion = onAccion,
        modifier = modifier,
        icono = icono,
    )
}

/**
 * **Una [Fila] que todavía no llegó**: mide lo mismo que la fila real (56, o 64 con subtítulo), con
 * el círculo del inicio, los renglones y el monto en su lugar, para que nada salte cuando llega el
 * dato. Reusa los bloques de `Esqueleto.kt` (mismo pulso y mismo color).
 */
@Composable
fun EsqueletoDeFila(
    modifier: Modifier = Modifier,
    conSubtitulo: Boolean = true,
    conInicio: Boolean = true,
    conFinal: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (conSubtitulo) Medida.altoDeFilaDoble else Medida.altoDeFila)
            .testTag(TAG_ESQUELETO_DE_FILA)
            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Los mismos aires que la [Fila]: 12 después del inicio, 8 antes del final (vuelta 15).
        if (conInicio) {
            CirculoEsqueleto(diametro = Medida.avatar)
            Spacer(Modifier.width(ESPACIO_ANTES_DEL_TEXTO_DE_FILA))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
            LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.cuerpo)
            if (conSubtitulo) LineaEsqueleto(fraccionDelAncho = 0.4f, estilo = Movi.textos.apoyo)
        }
        if (conFinal) {
            Spacer(Modifier.width(ESPACIO_ANTES_DEL_FINAL_DE_FILA))
            Box(modifier = Modifier.weight(0.3f)) {
                LineaEsqueleto(fraccionDelAncho = 1f, estilo = Movi.textos.monto)
            }
        }
    }
}
