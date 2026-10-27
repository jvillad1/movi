package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio

/** Tag estable de un [Boton]. */
const val TAG_BOTON: String = "ds-boton"

/** Lo que un lector de pantalla dice de un botón que está cargando. */
const val ROTULO_CARGANDO: String = "Cargando"

/**
 * **Qué tan fuerte habla un botón.** Uno [Principal] por pantalla o por tarjeta, como mucho.
 */
enum class VarianteDeBoton {
    /** La acción principal: lleno de marca. */
    Principal,

    /** Una acción al lado de la principal: en la capa 2, con texto normal. */
    Secundario,

    /**
     * **El botón de texto**: una acción que casi no pesa, solo el texto en marca, sin fondo —tampoco
     * apagado—. «Saltar este paso» debajo de «Siguiente», «Ver» al lado de «Confirmar».
     */
    Fantasma,

    /** Borrar, anular, salir: el color de `sale` sobre su lavado. Nunca lleno de rojo. */
    Peligro,
}

/**
 * **El tamaño de un botón.** [M] (48 dp, el objetivo táctil) es el de siempre; [S] (36 dp) va solo
 * dentro de una tarjeta —una propuesta, una fila de acciones— y su área de toque igual llega a 48,
 * porque Compose la extiende sin ocupar lugar.
 */
enum class TamanoDeBoton(val alto: Dp) {
    M(Medida.objetivoTactil),
    S(Medida.altoChico),
}

/** El fondo y el texto de una variante, en la paleta del tema. Pura, para probarla en los dos. */
fun coloresDelBoton(variante: VarianteDeBoton, colores: ColoresDeMovi): Pair<Color, Color> = when (variante) {
    VarianteDeBoton.Principal -> colores.marca to colores.sobreMarca
    VarianteDeBoton.Secundario -> colores.capa2 to colores.texto
    VarianteDeBoton.Fantasma -> Color.Transparent to colores.marca
    VarianteDeBoton.Peligro -> colores.lavado(colores.sale) to colores.sale
}

/**
 * El fondo y el texto de una variante **apagada**: la capa 2 y el texto apagado, para que se siga
 * leyendo como botón pero no como tocable; el [VarianteDeBoton.Fantasma] sigue sin fondo, porque un
 * botón de texto que se apaga y se rellena de gris parece otro botón. Pura.
 */
fun coloresDelBotonApagado(variante: VarianteDeBoton, colores: ColoresDeMovi): Pair<Color, Color> = when (variante) {
    VarianteDeBoton.Fantasma -> Color.Transparent to colores.textoApagado
    else -> colores.capa2 to colores.textoApagado
}

/**
 * **El botón de Movi.** Reemplaza a los botones armados a mano —la auditoría encontró botones
 * principales de 46, 48, 50 y 54 dp de alto, con radios de 10, 12, 14 y píldora—.
 *
 * - Radio [Radio.m], alto fijo por [tamano], texto en `cuerpo` seminegrita.
 * - [icono] opcional a la izquierda, a [Icono.m].
 * - [cargando]: la ruedita reemplaza al ícono, el botón no se toca y el lector de pantalla dice
 *   «Cargando». El ancho no cambia (el texto se queda), así que nada salta.
 * - Apagado ([habilitado] en `false`): fondo de la capa 2 y texto apagado, para que se siga leyendo
 *   como botón pero no como tocable; el de texto ([VarianteDeBoton.Fantasma]) sigue sin fondo (ver
 *   [coloresDelBotonApagado]).
 *
 * ```
 * Boton("Guardar", onClick = ::guardar, anchoCompleto = true, cargando = guardando)
 * Boton("Ver", onClick = ::ver, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)
 * ```
 */
@Composable
fun Boton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variante: VarianteDeBoton = VarianteDeBoton.Principal,
    tamano: TamanoDeBoton = TamanoDeBoton.M,
    icono: ImageVector? = null,
    cargando: Boolean = false,
    habilitado: Boolean = true,
    anchoCompleto: Boolean = false,
) {
    val (fondo, color) = if (habilitado) {
        coloresDelBoton(variante, Movi.colores)
    } else {
        coloresDelBotonApagado(variante, Movi.colores)
    }
    val forma = RoundedCornerShape(Radio.m)
    Row(
        modifier = modifier
            .then(if (anchoCompleto) Modifier.fillMaxWidth() else Modifier)
            .height(tamano.alto)
            .clip(forma)
            .background(fondo)
            .clickable(enabled = habilitado && !cargando, role = Role.Button, onClick = onClick)
            .then(if (cargando) Modifier.semantics { stateDescription = ROTULO_CARGANDO } else Modifier)
            .testTag(TAG_BOTON)
            .padding(horizontal = if (tamano == TamanoDeBoton.M) Espacio.l else Espacio.s),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            cargando -> CircularProgressIndicator(
                modifier = Modifier.size(Icono.s),
                color = color,
                strokeWidth = Medida.trazoDeCarga,
            )
            icono != null -> Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(Icono.m))
        }
        Text(
            text = texto,
            style = Movi.textos.cuerpo,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}
