package com.jvillada.movi.ui.primeruso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.platform.hojaDeCompartir
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.recurrentes.NumeroEnCirculo
import com.jvillada.movi.ui.sms.tituloDeCapturaDelBanco

/*
 * # Recibir las alertas del banco por correo (vuelta 31)
 *
 * La pieza que la guía de primer uso ya mostraba en la web y en iOS —tu dirección de Movi para
 * copiar y cómo crear el reenvío en Gmail—, sacada de `PrimerUsoScreen` para que Ajustes la use
 * igual («Recibe tus alertas por correo»). Una sola pieza: la guía y Ajustes no pueden explicar el
 * reenvío de dos maneras.
 */

/** Los pasos para crear el reenvío en Gmail, cortos y en el orden en que se tocan. */
val PASOS_DEL_REENVIO_EN_GMAIL: List<String>
    get() = listOf(
        "En Gmail, abre Configuración y toca «Ver todos los ajustes».",
        "En «Reenvío y correo POP/IMAP», toca «Agregar una dirección de reenvío» y pega tu dirección de Movi.",
        "Gmail manda un código a Movi: búscalo en Ajustes → «$tituloDeCapturaDelBanco» y escríbelo en Gmail.",
        "Crea un filtro con el correo de tu banco en «De» y elige «Reenviarlo a» tu dirección de Movi.",
    )

/** El tag del texto con la dirección de Movi, para las pruebas. */
const val TAG_DIRECCION_DE_MOVI: String = "direccion-de-movi"

/**
 * **La dirección de Movi del dueño** (`GET /api/correo-entrante/direccion`). [leida] es que la lectura
 * terminó; [direccion] es `null` mientras no llegó, si falló o si el server no tiene el reenvío
 * configurado.
 */
@Stable
class DireccionDeMovi internal constructor() {
    var leida: Boolean by mutableStateOf(false)
        internal set
    var direccion: String? by mutableStateOf(null)
        internal set
}

/** Lee la dirección una vez por composición. Sin red o sin configurar, queda en `null`. */
@Composable
fun rememberDireccionDeMovi(): DireccionDeMovi {
    val estado = remember { DireccionDeMovi() }
    LaunchedEffect(Unit) {
        val leida = runCatching { Repositories.wallets.getDireccionDeCorreo() }.getOrNull()
        estado.direccion = leida?.takeIf { it.configurado }?.direccion
        estado.leida = true
    }
    return estado
}

/**
 * **Tu dirección de Movi y cómo crear el reenvío**: la dirección con «Copiar» y los pasos de Gmail,
 * numerados. Mientras lee, un esqueleto; sin dirección, que todavía no está disponible.
 */
@Composable
fun ReenvioPorCorreo(
    direccion: DireccionDeMovi,
    onCopiada: () -> Unit = {},
    sinDireccion: String = "El reenvío por correo todavía no está disponible.",
) {
    val hoja = hojaDeCompartir()
    var copiada by remember { mutableStateOf(false) }
    Seccion("Tu dirección de Movi")
    val laDireccion = direccion.direccion
    when {
        !direccion.leida -> Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) { EsqueletoDeFila() }
        laDireccion == null -> Text(
            sinDireccion,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        else -> {
            Superficie(modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        laDireccion,
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.texto,
                        modifier = Modifier.testTag(TAG_DIRECCION_DE_MOVI),
                    )
                }
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Boton(
                    if (copiada) "Copiada" else "Copiar",
                    onClick = {
                        hoja.copiar(laDireccion)
                        copiada = true
                        onCopiada()
                    },
                    variante = VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                    icono = Icons.Rounded.ContentCopy,
                )
            }
            Spacer(Modifier.height(Medida.entreSecciones))
            Seccion("Cómo crear el reenvío en Gmail")
            Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                PASOS_DEL_REENVIO_EN_GMAIL.forEachIndexed { i, texto ->
                    if (i > 0) Separador()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.s),
                        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NumeroEnCirculo(i + 1)
                        Text(texto, style = Movi.textos.cuerpo, color = Movi.colores.texto, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
