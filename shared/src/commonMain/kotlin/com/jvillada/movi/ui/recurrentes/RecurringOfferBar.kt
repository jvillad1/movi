package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Movi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Ola 9 · B — la barra que ofrece convertir en recurrente el movimiento recién guardado.
 *
 * **Es un ofrecimiento, no una pregunta**, y la forma lo dice: aparece junto al «guardado», no
 * tiene botón de «No», no tapa nada que el dueño estuviera usando y se va sola a los pocos
 * segundos (ver App.kt). No contestar ES la respuesta, y no cuesta ni un toque — que es lo que
 * hace que se pueda dejar prendida aunque el dueño anote comida cuatro veces por semana. La ✕
 * está para el impaciente, no para que el diseño funcione.
 *
 * El nombre del movimiento va entre comillas para que se entienda de qué se está hablando: la
 * barra aparece un instante después de cerrarse la hoja, y sin el nombre habría que adivinar a
 * cuál de los dos gastos que acaba de anotar se refiere.
 */
@Composable
fun RecurringOfferBar(
    prefill: RecurringPrefill,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = Espacio.m, vertical = Espacio.s)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radio.l))
                .background(Movi.colores.tarjeta)
                .border(Medida.trazo, Movi.colores.borde, RoundedCornerShape(Radio.l))
                .padding(horizontal = Espacio.m, vertical = Espacio.s),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Guardado",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                    Spacer(Modifier.height(Espacio.xxs))
                    Text(
                        // Una sola línea de pregunta, corta: si necesita dos renglones para
                        // entenderse, ya es una interrupción y no un ofrecimiento.
                        text = "¿\"${prefill.name}\" se repite todos los meses?",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.texto,
                    )
                }
                // Vuelta de diseño 20: `BotonDeIcono` (el toque de 48 y la descripción). Ícono, no el
                // carácter "✕": la fuente del canvas no trae ese glifo y salía un cuadradito.
                BotonDeIcono(Icons.Rounded.Close, descripcion = "Cerrar", onClick = onDismiss)
            }
            Spacer(Modifier.height(Espacio.xs))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                // Vuelta de diseño 20: un `Boton` chico (era una píldora armada a mano).
                Boton("Sí, anótalo", onClick = onAccept, tamano = TamanoDeBoton.S)
            }
        }
    }
}
