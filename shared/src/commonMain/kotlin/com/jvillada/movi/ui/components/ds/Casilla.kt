package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio

/** Tag estable de la caja que se ve de una [Casilla]. */
const val TAG_CASILLA: String = "ds-casilla"

/**
 * **Una casilla de marcar** (vuelta de diseño 14). La caja mide [Icono.l] con radio [Radio.s]:
 *
 * - **marcada**: rellena de la marca, con el visto en `sobreMarca`. Se distingue de lejos, no por un
 *   lavado de 16 % (la de antes, `CasillaDeSeleccion` —ya borrada—, era un cuadrado lavanda chico
 *   que marcada y desmarcada casi no cambiaba);
 * - **sin marcar**: vacía, con el borde en `textoMedio` (el `borde` de un campo no se veía sobre la
 *   capa 1).
 *
 * **El toque es de 48, siempre** ([Medida.objetivoTactil]):
 * - con [onCambiar], la casilla se toca sola: mide 48 × 48 con la caja en el centro, y dice
 *   «casilla, marcada» al lector de pantalla;
 * - sin [onCambiar] va dentro de algo que se marca entero —una [Fila] con `toggleable`, como cada
 *   propuesta de Presupuestos—: ese algo es el toque (de borde a borde y 56 o más de alto) y la
 *   casilla solo dice el estado, sin ocupar el ancho de un toque que no es suyo. Es lo que hace
 *   Material con su `Checkbox` sin `onCheckedChange`.
 *
 * ```
 * Casilla(marcada = aceptado, onCambiar = { aceptado = it })
 * Fila(titulo = "Mercado", final = { Casilla(marcada) },
 *     modifier = Modifier.toggleable(marcada, role = Role.Checkbox, onValueChange = ::alternar))
 * ```
 */
@Composable
fun Casilla(
    marcada: Boolean,
    modifier: Modifier = Modifier,
    onCambiar: ((Boolean) -> Unit)? = null,
    habilitada: Boolean = true,
) {
    val conToque = if (onCambiar != null) {
        modifier
            .size(Medida.objetivoTactil)
            .toggleable(value = marcada, enabled = habilitada, role = Role.Checkbox, onValueChange = onCambiar)
    } else {
        modifier
    }
    Box(modifier = conToque, contentAlignment = Alignment.Center) {
        CajaDeLaCasilla(marcada)
    }
}

@Composable
private fun CajaDeLaCasilla(marcada: Boolean) {
    val forma = RoundedCornerShape(Radio.s)
    Box(
        modifier = Modifier
            .testTag(TAG_CASILLA)
            .size(Icono.l)
            .clip(forma)
            .then(
                if (marcada) {
                    Modifier.background(Movi.colores.marca, forma)
                } else {
                    Modifier.border(Medida.trazo, Movi.colores.textoMedio, forma)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) {
            // Ícono y no «✓»: la fuente del canvas web no trae el carácter (se vio en la PWA, en «Nuevo recurrente»).
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.sobreMarca, modifier = Modifier.size(Icono.s))
        }
    }
}
