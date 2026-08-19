package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.MinTextFaint
import com.jvillada.movi.theme.MinTextMute

/**
 * F37: manija (32×4.dp, centrada) + X para cerrar (44.dp de área tocable, arriba a la derecha),
 * en el mismo renglón. Reemplaza la manija suelta que traía cada una de las 8 hojas — antes solo
 * se cerraban tocando afuera o arrastrando la manija, sin ninguna pista visual, y en escritorio
 * con mouse ese gesto no es obvio.
 *
 * [enabled] debe ir atado al mismo `!saving` (o equivalente) que ya deshabilita el tap-afuera de
 * cada hoja — no tiene sentido dejar cerrar por la X mientras un guardado está en vuelo.
 */
@Composable
fun SheetHandleWithClose(
    onClose: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MinTextFaint),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Cerrar",
                tint = MinTextMute,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
