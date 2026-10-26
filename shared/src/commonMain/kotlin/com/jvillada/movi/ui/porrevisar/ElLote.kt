package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.sms.PastillaDelPago

/*
 * # «Confirmar los N que Movi entendió»
 *
 * El lote de la bandeja que se vacía sola: arriba de «Por revisar», la lista de lo que Movi va a
 * anotar —solo lo que pasa el criterio de la fila ([loQueMoviEntendio])—, un botón, **una**
 * pregunta, y al final qué se anotó y qué quedó. Cada aviso va por el mismo camino que la fila y que
 * Reconciliar ([confirmarLoEntendido]); si uno falla, los demás siguen ([confirmarElLote]).
 */

/** Con uno solo, el «Confirmar» de su fila basta: el lote aparece desde dos. */
const val MINIMO_PARA_EL_LOTE: Int = 2

/** Lo que ocupa `pagoEnCurso` mientras corre el lote: ninguna fila confirma ni ignora a la vez. */
internal const val CLAVE_DEL_LOTE: String = "lote-de-la-bandeja"

/** El tag de la tarjeta del lote. */
const val TAG_LOTE_DE_LA_BANDEJA: String = "por-revisar:lote"

/** «Confirmar los 3 que Movi entendió». */
fun tituloDelLote(cuantos: Int): String =
    if (cuantos == 1) "Confirmar el que Movi entendió" else "Confirmar los $cuantos que Movi entendió"

/** El botón que pide confirmar: «Confirmar los 3». */
fun botonDelLote(cuantos: Int): String = if (cuantos == 1) "Confirmar 1" else "Confirmar los $cuantos"

/** La única pregunta del lote. */
fun preguntaDelLote(cuantos: Int): String =
    if (cuantos == 1) "¿Anotar este movimiento tal como se ve?" else "¿Anotar estos $cuantos movimientos tal como se ven?"

/** Dónde va el lote: cerrado (solo la lista y el botón), preguntando, anotando o terminado. */
sealed interface EstadoDelLote {
    data object Cerrado : EstadoDelLote
    data object Preguntando : EstadoDelLote
    data class Anotando(val hechos: Int, val de: Int) : EstadoDelLote
    data class Terminado(val texto: String, val quedaron: Boolean = false) : EstadoDelLote
}

/** «Sí, anotarlos»: el botón de la pregunta. */
const val SI_ANOTAR_EL_LOTE: String = "Sí, anotarlos"

@Composable
internal fun TarjetaDelLote(
    lote: List<AnotacionEntendida>,
    estado: EstadoDelLote,
    habilitado: Boolean,
    onPedir: () -> Unit,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit,
    onCerrar: () -> Unit,
) {
    // Lo que Movi propone hacer de una vez: la superficie de la propuesta (ola R4).
    Superficie(
        modifier = Modifier.fillMaxWidth().padding(bottom = Medida.entreSecciones).testTag(TAG_LOTE_DE_LA_BANDEJA),
        nivel = NivelDeSuperficie.Propuesta,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            when (estado) {
                is EstadoDelLote.Terminado -> {
                    Text(
                        estado.texto,
                        style = Movi.textos.cuerpo,
                        color = if (estado.quedaron) Movi.colores.aviso else Movi.colores.texto,
                    )
                    PastillaDelPago("Listo", habilitada = true, onClick = onCerrar)
                }
                is EstadoDelLote.Anotando -> {
                    Text(tituloDelLote(estado.de), style = Movi.textos.titulo, color = Movi.colores.texto)
                    Text("Anotando ${estado.hechos} de ${estado.de}…", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
                EstadoDelLote.Cerrado, EstadoDelLote.Preguntando -> {
                    Text(tituloDelLote(lote.size), style = Movi.textos.titulo, color = Movi.colores.texto)
                    Text(
                        "Los que ya tienen monto, cuenta y categoría, sin nada que revisar. Los demás se quedan.",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                    lote.forEachIndexed { i, anotacion ->
                        if (i > 0) Separador(SangriaDeSeparador.Ninguna)
                        Column {
                            Text(
                                anotacion.leido.merchant,
                                style = Movi.textos.apoyo,
                                fontWeight = FontWeight.Medium,
                                color = Movi.colores.texto,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(anotacion.linea, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                        }
                    }
                    if (estado == EstadoDelLote.Preguntando) {
                        Text(preguntaDelLote(lote.size), style = Movi.textos.cuerpo, color = Movi.colores.texto)
                        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                            PastillaDelPago(SI_ANOTAR_EL_LOTE, principal = true, habilitada = habilitado && lote.isNotEmpty(), onClick = onConfirmar)
                            PastillaDelPago("Cancelar", habilitada = true, onClick = onCancelar)
                        }
                    } else {
                        PastillaDelPago(botonDelLote(lote.size), principal = true, habilitada = habilitado && lote.isNotEmpty(), onClick = onPedir)
                    }
                }
            }
        }
    }
}
