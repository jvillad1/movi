package com.jvillada.movi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.coloresDeLoElegido

/**
 * **Los días de un mes para elegir uno** (vuelta 41): la grilla que «Inicio del período» y «¿Cuándo
 * empezó…?» escribían cada una a mano. Filas de siete círculos de [Medida.avatar], el elegido en tinta
 * (`coloresDeLoElegido`), los demás en la capa 2 —el blanco de la hoja no los dejaba ver—; cada día es
 * un botón de radio con su estado para el lector de pantalla.
 *
 * Filas normales, no un `LazyVerticalGrid`: el grid de antes tenía un alto fijo de 196 dp para un
 * contenido de 224 y cortaba el 29, el 30 y el 31 —justo los que la hoja explica—, y dos áreas
 * desplazables anidadas se peleaban el gesto. 31 celdas de tamaño conocido no ganan nada con ser lazy.
 */
@Composable
internal fun CalendarioDeDias(
    dias: Int,
    elegido: Int,
    habilitado: Boolean,
    onElegir: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        (1..dias).chunked(DIAS_POR_FILA).forEach { fila ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                fila.forEach { d ->
                    val esElElegido = d == elegido
                    Box(
                        modifier = Modifier
                            .size(Medida.avatar)
                            .clip(CircleShape)
                            .background(if (esElElegido) coloresDeLoElegido(Movi.colores).first else Movi.colores.capa2)
                            .clickable(enabled = habilitado, role = Role.RadioButton) { onElegir(d) }
                            .semantics { selected = esElElegido },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = d.toString(),
                            style = if (esElElegido) Movi.textos.cuerpoFuerte else Movi.textos.cuerpoRegular,
                            color = if (esElElegido) coloresDeLoElegido(Movi.colores).second else Movi.colores.texto,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Una semana por fila. */
private const val DIAS_POR_FILA: Int = 7
