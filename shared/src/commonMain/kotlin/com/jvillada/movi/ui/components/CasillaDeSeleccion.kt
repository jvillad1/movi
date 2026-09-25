package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.Movi

/**
 * **La casilla que el dueño marca y desmarca**, dibujada a mano para no arrastrar el `Checkbox` de
 * Material a estas hojas. Solo el dibujo: el toque y la semántica de casilla (`toggleable` con
 * `Role.Checkbox`) los pone la fila entera que la contiene, que es lo que se toca.
 *
 * Vivía privada en `ReminderWarning.kt` (la casilla «Avisarme»); salió acá cuando Presupuestos
 * vacío necesitó la misma para elegir qué propuestas crear. No confundir con `CasillaDeChecklist`,
 * que es un reflejo y no un control.
 */
@Composable
fun CasillaDeSeleccion(marcada: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (marcada) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.tarjeta)
            .then(if (marcada) Modifier else Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(6.dp))),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) {
            // Ícono, no el carácter "✓": la fuente del canvas no lo trae y en el navegador la
            // casilla marcada se veía como un cuadradito vacío — o sea, justo lo contrario de
            // lo que quiere decir. (Se vio en la PWA, en «Nuevo recurrente».)
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Movi.colores.marca,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
