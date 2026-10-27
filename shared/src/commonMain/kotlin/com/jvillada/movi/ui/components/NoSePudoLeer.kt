package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio

/**
 * **«No pudimos cargar X» no es lo mismo que «no tienes X»**, y esta tarjeta es la que lo dice.
 *
 * Nació en Cuentas: una lectura fallida dejaba abajo el estado vacío invitando a «crear tu primera
 * cuenta» a alguien que ya tenía tres. Las demás listas (Créditos, Metas, Presupuestos…) repetían
 * el mismo error —«Deuda total $0 · Sin créditos registrados» con el botón de crear uno—, y un
 * botón de crear sobre datos que no se leyeron es como se fabrican los duplicados. Por eso la
 * única acción que ofrece es reintentar.
 *
 * Quien la usa lleva un `leido` que solo se prende cuando una lectura DE VERDAD contestó, y la
 * muestra en lugar del estado vacío mientras `leido` sea falso. Vive en un solo lugar para que el
 * rediseño la cambie una vez y valga para todas.
 */
@Composable
fun NoSePudoLeer(
    texto: String,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MinCard(
        modifier = modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(Espacio.xxl),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Text(
                text = texto,
                style = Movi.textos.titulo,
                color = Movi.colores.textoMedio,
            )
            BotonReintentar(onReintentar)
        }
    }
}

/**
 * El «Reintentar» de [NoSePudoLeer], suelto para un aviso que va adentro de otra tarjeta (las
 * propuestas de Presupuestos que no se pudieron crear): una tarjeta dentro de otra no se lee como
 * aviso, pero el botón tiene que ser el mismo.
 */
@Composable
fun BotonReintentar(onReintentar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // Vuelta de diseño 20: el lavado y los tamaños del sistema (eran `copy(alpha = 0.16f)`,
            // radio 999 y rellenos de 20 y 10 dp): alto de un botón chico, relleno de un botón.
            .heightIn(min = Medida.altoChico)
            .clip(RoundedCornerShape(Radio.pill))
            .background(Movi.colores.lavado(Movi.colores.marca))
            .clickable(onClick = onReintentar)
            .padding(horizontal = Espacio.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Reintentar",
            style = Movi.textos.cuerpo,
            color = Movi.colores.marca,
        )
    }
}
