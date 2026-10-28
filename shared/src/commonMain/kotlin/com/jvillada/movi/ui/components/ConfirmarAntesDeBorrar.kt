package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton

/**
 * **Borrar pregunta antes, en todas partes.**
 *
 * Cuentas, movimientos y documentos pedían confirmación; metas, recurrentes, los términos de una
 * tarjeta o un crédito y las suscripciones se borraban con un toque — y en metas y recurrentes el
 * «Eliminar» está al lado del título, donde un dedo que va a cerrar la hoja lo toca sin querer.
 * Una sola pieza para decirlo, con **qué** se borra y **qué no** se toca, que es lo que uno quiere
 * leer antes del botón rojo.
 *
 * [ConfirmacionEnLinea] va dentro de una hoja (reemplaza la fila del botón mientras se decide);
 * [ConfirmarEnHoja] se monta encima de una pantalla, igual que la de Documentos.
 */
@Composable
fun ConfirmacionEnLinea(
    pregunta: String,
    detalle: String,
    textoConfirmar: String,
    ocupado: Boolean,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radio.l))
            .background(Movi.colores.fondo)
            .padding(Medida.rellenoDeSuperficie),
    ) {
        Text(pregunta, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        Spacer(Modifier.height(Espacio.xxs))
        Text(detalle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Espacio.s))
        // Dentro de un bloque de la hoja: los botones chicos, como los de una tarjeta.
        BotonesDeConfirmar(textoConfirmar, ocupado, onConfirmar, onCancelar, TamanoDeBoton.S)
    }
}

/**
 * La confirmación como hoja propia. La pregunta y su detalle son el [EncabezadoDeHoja] (vuelta de
 * diseño 18): «¿Borrar el enlace?» en `titulo` y lo que se borra y lo que no, debajo, en `cuerpo`.
 *
 * [varianteConfirmar] es `Peligro` para lo que se borra. Vuelta de diseño 26: lo que se confirma sin
 * destruir nada («¿Caro ya te pagó $76.050?» antes de saldar un espacio) va en `Principal`: el rojo es
 * para lo que no tiene vuelta, no para un pago.
 */
@Composable
fun ConfirmarEnHoja(
    pregunta: String,
    detalle: String,
    textoConfirmar: String,
    ocupado: Boolean,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    varianteConfirmar: VarianteDeBoton = VarianteDeBoton.Peligro,
) {
    MarcoDeHoja(
        onDismiss = onCancelar,
        dismissEnabled = !ocupado,
        relleno = PaddingValues(horizontal = Medida.margenDePantalla, vertical = Espacio.xl),
        conCierre = false,
    ) {
        EncabezadoDeHoja(pregunta, subtitulo = detalle)
        Spacer(Modifier.height(Medida.entreSecciones))
        BotonesDeConfirmar(textoConfirmar, ocupado, onConfirmar, onCancelar, TamanoDeBoton.M, varianteConfirmar)
    }
}

/** El `testTag` de la fila de botones de una confirmación. */
const val TAG_BOTONES_DE_CONFIRMAR: String = "botones-de-confirmar"

/**
 * **«Cancelar · Borrar», con los botones del sistema** (vuelta de diseño 18). Eran dos `Text`
 * tocables de 10/14 dp a mano, con «Borrar» en `sale` sin fondo: el botón que no tiene vuelta se
 * veía igual de liviano que el que cierra. Ahora «Cancelar» es un [Boton] `Fantasma` y la acción un
 * [Boton] `Peligro` (el `sale` sobre su lavado), al final de la fila. Mientras se borra, la ruedita
 * del `Peligro` (`cargando`) y «Cancelar» apagado: no se cierra a medias.
 */
@Composable
private fun BotonesDeConfirmar(
    textoConfirmar: String,
    ocupado: Boolean,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    tamano: TamanoDeBoton,
    variante: VarianteDeBoton = VarianteDeBoton.Peligro,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().testTag(TAG_BOTONES_DE_CONFIRMAR),
    ) {
        Boton(
            "Cancelar",
            onClick = onCancelar,
            variante = VarianteDeBoton.Fantasma,
            tamano = tamano,
            habilitado = !ocupado,
        )
        Boton(
            textoConfirmar,
            onClick = onConfirmar,
            variante = variante,
            tamano = tamano,
            cargando = ocupado,
        )
    }
}
