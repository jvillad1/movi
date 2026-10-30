package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi

/** El tag de un selector de día del mes: lo usan las pruebas que exigen UNA sola forma de elegirlo. */
const val TAG_SELECTOR_DE_DIA_DEL_MES: String = "ds:selector-de-dia-del-mes"

/** El tag de un día del selector: `"ds:dia-del-mes:5"`. */
fun tagDelDiaDelMes(dia: Int): String = "ds:dia-del-mes:$dia"

/** Una semana por fila. */
private const val DIAS_POR_FILA: Int = 7

/**
 * **El selector del día del mes** (vuelta 72): la única forma de elegir un día del mes en la app. Lo usan
 * «Inicio del período», «¿Cuándo empezó…?» y la hoja de un pago fijo; antes la hoja del pago fijo tenía
 * su propia cuadrícula de cuadrados y las del período círculos (era `CalendarioDeDias`, vuelta 41).
 *
 * Filas de siete **círculos** de [Medida.avatar], el elegido en tinta (`coloresDeLoElegido`) y los demás en
 * la capa 2 (sobre la hoja blanca se ven); cada día es un botón de radio con su estado para el lector de
 * pantalla. El toque es la celda entera —un séptimo del ancho y [Medida.objetivoTactil] de alto—, no solo
 * el círculo: a 360 dp siete círculos de 40 con su aire no caben, y el círculo se queda en su tamaño
 * mientras la celda se reparte el ancho. Las celdas que sobran de la última fila son huecos del mismo
 * ancho: la cuadrícula no cambia de paso.
 *
 * @param dias cuántos días ofrece (31; menos cuando el mes del período es más corto).
 * @param elegido el día elegido, o `null` sin ninguno.
 */
@Composable
fun SelectorDeDiaDelMes(
    elegido: Int?,
    onElegir: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dias: Int = 31,
    habilitado: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(TAG_SELECTOR_DE_DIA_DEL_MES),
        verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
    ) {
        (1..dias).chunked(DIAS_POR_FILA).forEach { fila ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                fila.forEach { d ->
                    val esElElegido = d == elegido
                    val (fondo, tinta) = coloresDeLoElegido(Movi.colores)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(Medida.objetivoTactil)
                            // El foco del teclado se ve en el círculo del día (vuelta 55).
                            .anilloDeFoco(CircleShape)
                            .alPresionar(onClick = { onElegir(d) }, habilitado = habilitado, rol = Role.RadioButton, velo = !esElElegido, forma = CircleShape)
                            .semantics { selected = esElElegido }
                            .testTag(tagDelDiaDelMes(d)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(Medida.avatar)
                                .clip(CircleShape)
                                .background(if (esElElegido) fondo else Movi.colores.capa2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = d.toString(),
                                style = if (esElElegido) Movi.textos.cuerpoFuerte else Movi.textos.cuerpoRegular,
                                color = when {
                                    esElElegido -> tinta
                                    !habilitado -> Movi.colores.textoApagado
                                    else -> Movi.colores.texto
                                },
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                // Vuelta 75: a lo ancho del círculo, centrado por el párrafo y no por la caja,
                                // que a la letra agrandada medía el número con un pelo menos de lo que ocupa
                                // (lo mismo que el día del período de la guía).
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                repeat(DIAS_POR_FILA - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
