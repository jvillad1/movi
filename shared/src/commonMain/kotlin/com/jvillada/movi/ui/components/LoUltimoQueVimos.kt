package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.Movi

/** El texto de [ActualizandoEnLaCabecera]; el mismo que dicen el Inicio y Plan. */
const val TEXTO_ACTUALIZANDO: String = "Actualizando…"

const val TEXTO_NO_PUDIMOS_ACTUALIZAR: String = "No pudimos actualizar; esto es lo último que vimos"

const val TAG_NO_PUDIMOS_ACTUALIZAR: String = "no-pudimos-actualizar"

/**
 * **«Actualizando…», en la cabecera**, mientras se ve algo que la lectura de esta visita todavía no
 * confirmó (lo recordado de la visita anterior, o lo de antes de un «Reintentar»).
 *
 * Mismo texto y mismo estilo que el Inicio y Plan: el dueño ya aprendió qué quiere decir, y una
 * señal nueva para lo mismo sería una cosa más que aprender. Va en la cabecera porque su alto lo
 * fija el avatar: aparecer y desaparecer no mueve nada de lo de abajo mientras se lee.
 */
@Composable
fun ActualizandoEnLaCabecera(modifier: Modifier = Modifier) {
    Text(
        TEXTO_ACTUALIZANDO,
        style = Movi.textos.apoyo,
        color = Movi.colores.textoApagado,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * **La lectura falló y lo que se ve es de antes**: se dice, con «Reintentar».
 *
 * Es el hermano de [NoSePudoLeer] para cuando SÍ hay algo a la vista. Borrar la lista porque la
 * red falló sería quitarle al dueño lo que ya sabía; dejarla sin decir nada sería hacerla pasar
 * por actual. Se queda la lista, y arriba esta línea, hasta que una lectura conteste.
 */
@Composable
fun NoSePudoActualizar(onReintentar: () -> Unit, modifier: Modifier = Modifier) {
    MinCard(
        modifier = modifier.fillMaxWidth().testTag(TAG_NO_PUDIMOS_ACTUALIZAR),
        variant = MinCardVariant.Default,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = TEXTO_NO_PUDIMOS_ACTUALIZAR,
                style = Movi.textos.cuerpo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.weight(1f),
            )
            BotonReintentar(onReintentar)
        }
    }
}
