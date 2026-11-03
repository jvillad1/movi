package com.jvillada.movi.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.shared.model.CONVERSACIONES_QUE_SE_GUARDAN
import com.jvillada.movi.shared.model.ConversacionEnLaLista
import com.jvillada.movi.shared.model.textoDeLaRetencion
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.fondoDeLaFilaElegida
import com.jvillada.movi.ui.fecha.etiquetaCortaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.datetime.LocalDate

/**
 * # Las conversaciones con Movi AI (vuelta 117)
 *
 * El dueño: «el chat de AI no me lista los chats del pasado, me gustaría poder abrir un chat anterior
 * o eliminarlo y navegar entre múltiples chats». La lista sale del server
 * (`GET /api/ai/conversaciones`): solo las que tienen alguna pregunta, la más reciente primero. Cada
 * fila dice su título (la primera pregunta, recortada), cuándo se usó por última vez y cuántos
 * mensajes tiene; tocarla la abre, y su «⋯» ofrece «Eliminar», que **siempre pregunta antes**.
 *
 * En el teléfono va en una hoja ([HojaDeConversaciones]) que abre el botón de historial del
 * encabezado del chat; en pantalla ancha, como columna a la izquierda del chat (ver `AIChatScreen`).
 * Debajo va siempre la regla de cuánto se guarda (`textoDeLaRetencion`).
 */

internal const val TITULO_DE_LAS_CONVERSACIONES = "Conversaciones"
internal const val ELIMINAR_CONVERSACION = "Eliminar"
internal const val PREGUNTA_DE_ELIMINAR_LA_CONVERSACION = "¿Eliminar esta conversación?"
internal const val DETALLE_DE_ELIMINAR_LA_CONVERSACION = "No se puede deshacer."
internal const val NINGUNA_CONVERSACION_TODAVIA =
    "Todavía no tienes conversaciones guardadas. Lo que le preguntes a Movi AI aparecerá aquí."
internal const val NO_PUDE_LEER_LAS_CONVERSACIONES = "No pude cargar tus conversaciones."
internal const val NO_PUDE_ELIMINAR_LA_CONVERSACION =
    "No pude eliminar la conversación. Revisa tu conexión e intenta otra vez."
internal const val NO_PUDE_ABRIR_LA_CONVERSACION =
    "No pude abrir esa conversación. Revisa tu conexión e intenta otra vez."

internal const val TAG_LISTA_DE_CONVERSACIONES = "ai-lista-de-conversaciones"
internal const val TAG_FILA_DE_CONVERSACION = "ai-fila-de-conversacion"
internal const val TAG_RETENCION_DE_LAS_CONVERSACIONES = "ai-retencion-de-las-conversaciones"
internal const val TAG_COLUMNA_DE_CONVERSACIONES = "ai-columna-de-conversaciones"

/** Lo que dice el lector de pantalla del «⋯» de una fila: el título de la conversación va adentro. */
internal fun descripcionDelMenuDeLaConversacion(titulo: String): String = "Más opciones de «$titulo»"

/** «Hoy · 6 mensajes», «Ayer · 2 mensajes», «12 oct · 4 mensajes». */
internal fun subtituloDeLaConversacion(conversacion: ConversacionEnLaLista, hoy: LocalDate): String {
    val cuando = etiquetaCortaDeFecha(fechaDeEpoch(conversacion.ultimaEn), hoy)
    val n = conversacion.mensajes
    return "$cuando · $n ${if (n == 1) "mensaje" else "mensajes"}"
}

/**
 * **Lo que la pantalla sabe de la lista**: `null` mientras no llegó; [fallo] si la última lectura no
 * contestó (con lo de antes a la vista, si había).
 */
internal data class EstadoDeLasConversaciones(
    val conversaciones: List<ConversacionEnLaLista>? = null,
    val tope: Int = CONVERSACIONES_QUE_SE_GUARDAN,
    val cargando: Boolean = false,
    val fallo: Boolean = false,
    /** Lo que no se pudo hacer desde la lista (eliminar sin red), dicho debajo de «Nueva conversación». */
    val aviso: String? = null,
)

/**
 * **La lista**, la misma en la hoja y en la columna: «Nueva conversación» arriba (secundario: el único
 * amarillo de Movi AI es el de su propuesta), las filas y la regla de la retención al final. [abierta]
 * se marca como elegida. [habilitado] en `false` mientras Movi contesta: cambiar de conversación a
 * mitad de una respuesta la dejaría colgando de la otra.
 */
@Composable
internal fun ListaDeConversaciones(
    estado: EstadoDeLasConversaciones,
    abierta: String?,
    hoy: LocalDate,
    habilitado: Boolean,
    onAbrir: (ConversacionEnLaLista) -> Unit,
    onNueva: () -> Unit,
    onEliminar: (ConversacionEnLaLista) -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(TAG_LISTA_DE_CONVERSACIONES), verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        Boton(
            NUEVA_CONVERSACION,
            onClick = onNueva,
            variante = VarianteDeBoton.Secundario,
            icono = Icons.Rounded.Add,
            habilitado = habilitado,
            anchoCompleto = true,
        )
        estado.aviso?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
        val lista = estado.conversaciones
        when {
            lista == null && estado.fallo -> NoSePudoLeer(NO_PUDE_LEER_LAS_CONVERSACIONES, onReintentar = onReintentar)
            lista == null -> Superficie(relleno = RellenoDeSuperficie.Lista) {
                repeat(3) { EsqueletoDeFila(conInicio = false, conFinal = false) }
            }
            lista.isEmpty() -> Text(NINGUNA_CONVERSACION_TODAVIA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            else -> Superficie(modifier = Modifier.weight(1f, fill = false), relleno = RellenoDeSuperficie.Lista) {
                LazyColumn {
                    items(lista, key = { it.id }) { conversacion ->
                        FilaDeConversacion(
                            conversacion = conversacion,
                            hoy = hoy,
                            elegida = conversacion.id == abierta,
                            habilitado = habilitado,
                            onAbrir = { onAbrir(conversacion) },
                            onEliminar = { onEliminar(conversacion) },
                        )
                    }
                }
            }
        }
        if (lista != null) {
            Text(
                textoDeLaRetencion(lista.size, estado.tope),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
                modifier = Modifier.fillMaxWidth().testTag(TAG_RETENCION_DE_LAS_CONVERSACIONES),
            )
        }
    }
}

@Composable
private fun FilaDeConversacion(
    conversacion: ConversacionEnLaLista,
    hoy: LocalDate,
    elegida: Boolean,
    habilitado: Boolean,
    onAbrir: () -> Unit,
    onEliminar: () -> Unit,
) {
    Fila(
        titulo = conversacion.titulo,
        subtitulo = subtituloDeLaConversacion(conversacion, hoy),
        onClick = if (habilitado) onAbrir else null,
        chevron = false,
        lineasDelTitulo = 2,
        entidadDeUso = conversacion.id,
        modifier = Modifier
            .testTag(TAG_FILA_DE_CONVERSACION)
            .semantics { selected = elegida }
            .then(if (elegida) Modifier.background(fondoDeLaFilaElegida(Movi.colores)) else Modifier),
        final = {
            Box {
                var abierto by remember { mutableStateOf(false) }
                BotonDeIcono(
                    icono = Icons.Rounded.MoreHoriz,
                    descripcion = descripcionDelMenuDeLaConversacion(conversacion.titulo),
                    onClick = { abierto = true },
                    habilitado = habilitado,
                )
                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }, containerColor = Movi.colores.tarjeta) {
                    DropdownMenuItem(
                        text = { Text(ELIMINAR_CONVERSACION, style = Movi.textos.cuerpo, color = Movi.colores.sale) },
                        onClick = { abierto = false; onEliminar() },
                    )
                }
            }
        },
    )
}

/**
 * **La hoja del teléfono**: el título y la [ListaDeConversaciones], con un alto acotado para que una
 * lista larga se desplace adentro.
 */
@Composable
internal fun HojaDeConversaciones(
    estado: EstadoDeLasConversaciones,
    abierta: String?,
    hoy: LocalDate,
    habilitado: Boolean,
    onAbrir: (ConversacionEnLaLista) -> Unit,
    onNueva: () -> Unit,
    onEliminar: (ConversacionEnLaLista) -> Unit,
    onReintentar: () -> Unit,
    onCerrar: () -> Unit,
) {
    MarcoDeHoja(onDismiss = onCerrar, fraccionDeAltoMaximo = FRACCION_DE_ALTO_DE_LA_HOJA_DE_CONVERSACIONES) {
        EncabezadoDeHoja(TITULO_DE_LAS_CONVERSACIONES)
        ListaDeConversaciones(
            estado = estado,
            abierta = abierta,
            hoy = hoy,
            habilitado = habilitado,
            onAbrir = onAbrir,
            onNueva = onNueva,
            onEliminar = onEliminar,
            onReintentar = onReintentar,
            modifier = Modifier.fillMaxWidth().padding(top = Medida.dentroDeSeccion, bottom = Espacio.xl),
        )
    }
}

/** Cuánto del alto puede tomar la hoja de las conversaciones: con 50, la lista se desplaza adentro. */
internal const val FRACCION_DE_ALTO_DE_LA_HOJA_DE_CONVERSACIONES = 0.85f

/**
 * **«¿Eliminar esta conversación?»**: la confirmación de siempre (`ConfirmarEnHoja`), con «Eliminar»
 * en `Peligro`. Borrar pide red: mientras va, la ruedita y «Cancelar» apagado.
 */
@Composable
internal fun ConfirmarEliminarLaConversacion(
    ocupado: Boolean,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    ConfirmarEnHoja(
        pregunta = PREGUNTA_DE_ELIMINAR_LA_CONVERSACION,
        detalle = DETALLE_DE_ELIMINAR_LA_CONVERSACION,
        textoConfirmar = ELIMINAR_CONVERSACION,
        ocupado = ocupado,
        onConfirmar = onConfirmar,
        onCancelar = onCancelar,
    )
}

/**
 * **¿La lista va como columna al lado del chat?** (vuelta 117, `docs/diseno/sistema.md` §79). Fuera del
 * teléfono y con lugar para las dos cosas: la lista ([Medida.anchoDeLaListaDeConversaciones]), el hilo
 * que las separa y una ficha de chat ([Medida.anchoDeFicha]). Por debajo, el chat va solo en su columna
 * de lectura y la lista se abre en la hoja, como en el teléfono. [anchoDeLaPantalla] ya sin el rail.
 */
internal fun laListaVaAlLadoDelChat(anchoDeLaPantalla: androidx.compose.ui.unit.Dp): Boolean =
    anchoDeLaPantalla >= Medida.anchoDeLaListaDeConversaciones + Medida.trazo + Medida.anchoDeFicha
