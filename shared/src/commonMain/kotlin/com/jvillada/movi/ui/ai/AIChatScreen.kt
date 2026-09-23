package com.jvillada.movi.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.extractos.TiposDeArchivo
import com.jvillada.movi.ui.extractos.rememberFilePicker
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.launch

/** F32: lo que espera a ser enviado — foto de un recibo, extracto u oferta del banco. */
private data class PendingImage(val fileName: String, val bytes: ByteArray, val mimeType: String)

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun AIChatScreen(
    onNavigate: (Screen) -> Unit,
    /**
     * Una pregunta que se manda sola apenas abre la pantalla —ver [Screen.AIChat.preguntaInicial]—.
     * `null` abre el chat vacío, con el arranque de [ArranqueDelChat].
     */
    preguntaInicial: String? = null,
) {
    val coroutine = rememberCoroutineScope()
    // **Arranca vacía.** El saludo ya no es un mensaje del asistente sembrado en la lista: es el
    // arranque de [ArranqueDelChat], que se pinta mientras no haya conversación. Así no hay nada
    // que descartar antes de mandar (la API exige que el primer mensaje sea del usuario; ver
    // [mensajesParaEnviar], que igual lo sigue garantizando).
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<PendingImage?>(null) }
    var attachError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    // Las sugerencias salen de lo que el Inicio dejó en su caché: el chat no pide nada nuevo para
    // armarlas (ver [preguntasSugeridas], que tampoco llama al modelo). Sin caché —se abrió el chat
    // antes que el Inicio— salen las de respaldo, que valen para cualquiera.
    val sugeridas = remember { preguntasSugeridas(DashboardDataCache.data ?: DashboardData()) }
    val nombre = remember { primerNombre(SessionManager.userName) }

    // F32: el picker de la Ola 1 (extractos) acepta cualquier archivo — acá se filtra por
    // mime de imagen en el cliente, sin tocar el picker en sí.
    val launchPicker = rememberFilePicker(TiposDeArchivo.IMAGENES) { fileName, bytes, mimeType ->
        if (!mimeType.startsWith("image/")) {
            attachError = "Por ahora solo imágenes"
        } else if (bytes.size > 5 * 1024 * 1024) {
            // El mismo techo que valida el server. Rechazar acá y no allá importa: el server
            // rechaza el HISTORIAL completo en cada envío, así que una imagen grande que llegara
            // a entrar al chat lo dejaba respondiendo el mismo 422 para siempre.
            attachError = "La imagen pesa más de 5 MB — prueba con una captura más liviana"
        } else {
            attachError = null
            pendingImage = PendingImage(fileName, bytes, mimeType)
        }
    }

    /**
     * Manda [texto] (y la imagen pendiente, si hay). Es la única puerta de salida: la usan el campo
     * de escribir, los chips de sugerencias y la pregunta con la que se abrió la pantalla, así que
     * las tres pasan por el mismo recorte de [mensajesParaEnviar] y la misma guarda de «ya hay
     * una pregunta en camino».
     */
    fun enviar(texto: String) {
        val text = texto.trim()
        val image = pendingImage
        if ((text.isEmpty() && image == null) || loading) return
        messages.add(
            ChatMessage(
                role = ChatRole.USER,
                content = text,
                imageBase64 = image?.let { Base64.encode(it.bytes) },
                imageMime = image?.mimeType,
            ),
        )
        input = ""
        pendingImage = null
        loading = true
        coroutine.launch {
            // Lo que viaja NO es la lista de la pantalla: sin las imágenes de turnos anteriores
            // (ya se mandaron y se pagaron una vez) y con tope. Ver [mensajesParaEnviar].
            val history = mensajesParaEnviar(messages)
            val reply = runCatching { Repositories.wallets.chatAi(AiChatRequest(history)) }
            val replyText = reply.getOrNull()?.text
                ?: "No pude conectarme con el AI. ${reply.exceptionOrNull()?.message ?: ""}"
            messages.add(ChatMessage(ChatRole.ASSISTANT, replyText))
            loading = false
        }
    }

    fun send() = enviar(input)

    // **La pregunta con la que se abrió, una sola vez.** `rememberSaveable` y no `remember`: al
    // volver a esta entrada de la pila (o al girar el teléfono) la conversación se rearma vacía,
    // pero la pregunta ya se mandó y se pagó — mandarla de nuevo sería cobrarle dos veces el mismo
    // toque. Queda el arranque con sus sugerencias, y el dueño decide.
    var yaSeMandoLaInicial by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(preguntaInicial) {
        if (!yaSeMandoLaInicial && !preguntaInicial.isNullOrBlank()) {
            yaSeMandoLaInicial = true
            enviar(preguntaInicial)
        }
    }

    LaunchedEffect(messages.size, loading) {
        val target = messages.size + if (loading) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }
    // **Y al abrir el teclado también.** La lista encoge por el `imePadding()` de la raíz, así que
    // sin esto lo último que se dijo queda tapado justo cuando el dueño va a escribir la respuesta
    // — el mismo síntoma que el teclado encima del campo, un renglón más arriba.
    val tecladoALaVista = elTecladoEstaALaVista()
    LaunchedEffect(tecladoALaVista) {
        if (tecladoALaVista && messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60 · F22: encabezado único; Movi AI se abre desde Más — destino de reserva si no
        // hay historial. El «BETA» pasa a la derecha, como marca, no como parte del título.
        MinScreenHeader(
            title = "Movi AI",
            leading = HeaderLeading.Back(fallback = Screen.Mas),
            subtitle = "Conoce tus finanzas",
            action = {
                Text(
                    "BETA",
                    color = Movi.colores.textoMedio,
                    style = Movi.textos.rotulo,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(18.dp),
        ) {
            if (messages.isEmpty() && !loading) {
                item {
                    ArranqueDelChat(
                        nombre = nombre,
                        preguntas = sugeridas,
                        onPregunta = { enviar(it) },
                    )
                }
            }
            items(messages) { msg ->
                if (msg.role == ChatRole.USER) {
                    AIMsgUser(msg.content, hasImage = msg.imageBase64 != null)
                } else {
                    AIMsgAI(msg.content)
                }
            }
            if (loading) {
                item { AIMsgAI("…") }
            }
        }

        // F32: nombre del adjunto + X para quitarlo, o el aviso de "por ahora solo imágenes"
        // si eligieron otra cosa (p.ej. un PDF) — visible arriba de la barra de escribir.
        if (pendingImage != null || attachError != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (pendingImage != null) {
                    Icon(Icons.Rounded.Image, contentDescription = null, tint = Movi.colores.textoMedio, modifier = Modifier.size(16.dp))
                    Text(
                        text = pendingImage?.fileName ?: "",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Quitar imagen",
                        tint = Movi.colores.textoMedio,
                        modifier = Modifier.size(16.dp).clickable { pendingImage = null },
                    )
                } else {
                    Text(text = attachError ?: "", style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Movi.colores.tarjeta)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // F32: clip para adjuntar una foto (recibo, extracto, oferta del banco).
            Icon(
                Icons.Rounded.AttachFile,
                contentDescription = "Adjuntar imagen",
                tint = Movi.colores.textoMedio,
                modifier = Modifier.size(20.dp).clickable(enabled = !loading) { attachError = null; launchPicker() },
            )
            Box(modifier = Modifier.weight(1f)) {
                // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver
                // [esAtajoDeSeleccionarTodo].
                val campo = rememberCampoConSeleccion(input) { input = it }
                BasicTextField(
                    value = campo.valor,
                    onValueChange = campo::alCambiar,
                    enabled = !loading,
                    cursorBrush = SolidColor(Movi.colores.texto),
                    textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                    // Ola 8 · V2: mismo agujero que tenía la nota de Agregar. El `Box(weight)`
                    // reserva el ancho, pero el área que responde al toque es la del campo, y
                    // con el texto vacío mide cero: se tocaba «Pregúntale a Movi…» y no pasaba
                    // nada. Sin `fillMaxWidth` solo se podía escribir después de acertarle a
                    // una raya invisible.
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                "Pregúntale a Movi…",
                                style = Movi.textos.cuerpo,
                                color = Movi.colores.textoMedio,
                            )
                        }
                        inner()
                    },
                )
            }
            val canSend = (input.trim().isNotEmpty() || pendingImage != null) && !loading
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Movi.colores.texto else Movi.colores.tarjeta)
                    .clickable(enabled = canSend) { send() },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    // Glifo de espera dentro del botón de enviar: va al tamaño del botón, no es texto de la escala.
                    Text(text = "…", fontSize = 20.sp, color = if (canSend) Movi.colores.fondo else Movi.colores.textoMedio, fontWeight = FontWeight.Bold)
                } else {
                    // Ola 2 #5 (F11): "›" como texto suelto salía roto en la web, igual que "‹".
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Enviar",
                        tint = if (canSend) Movi.colores.fondo else Movi.colores.textoMedio,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AIMsgUser(text: String, hasImage: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Movi.colores.texto)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column {
                if (hasImage) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = Movi.colores.fondo, modifier = Modifier.size(13.dp))
                        Text("Imagen adjunta", style = Movi.textos.apoyo, color = Movi.colores.fondo)
                    }
                    if (text.isNotBlank()) Spacer(Modifier.height(4.dp))
                }
                if (text.isNotBlank()) {
                    Text(text = text, style = Movi.textos.cuerpo, fontWeight = FontWeight.Normal, color = Movi.colores.fondo, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun AIMsgAI(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Movi.colores.tarjeta),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Movi.colores.texto, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.widthIn(max = 290.dp)) {
            Text(text = text, style = Movi.textos.cuerpo, fontWeight = FontWeight.Normal, color = Movi.colores.texto, lineHeight = 20.sp)
        }
    }
}

/**
 * # Lo que se ve antes de la primera pregunta
 *
 * Antes era un solo globo: «¡Hola Camilo! Pregúntame lo que quieras sobre tu plata.» —con el
 * nombre escrito a mano en el código, así que cualquier otro usuario también era Camilo—. El dueño
 * contestó «Hola, me puedes ayudar?»: un campo en blanco no dice qué sabe hacer el asistente.
 *
 * Ahora son cuatro piezas, en este orden y ninguna más:
 *
 * 1. **El saludo**, con su nombre de verdad (o sin nombre, si la sesión no lo tiene).
 * 2. **Qué mira Movi**, en una línea: movimientos, deudas, presupuestos y bienes. Es lo que el
 *    contexto del server de verdad le pasa al modelo — no prometer lo que no ve.
 * 3. **Tres preguntas tocables** sacadas de sus datos ([preguntasSugeridas]). Tocar una la manda:
 *    es la misma puerta que escribirla y darle enviar.
 * 4. **Una nota honesta**: Movi orienta con sus datos, no reemplaza a un asesor financiero
 *    certificado. Una línea, apagada y al final — está para que sea verdad, no para asustar.
 *
 * `internal` para que la prueba lo pinte suelto, sin la pantalla ni el repositorio.
 */
@Composable
internal fun ArranqueDelChat(
    nombre: String?,
    preguntas: List<String>,
    onPregunta: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = Movi.espacios.amplio),
        verticalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        Text(
            text = saludoDelChat(nombre),
            style = Movi.textos.titulo,
            color = Movi.colores.texto,
        )
        Text(
            text = QUE_MIRA_MOVI,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(
            text = ROTULO_DE_LAS_SUGERENCIAS,
            style = Movi.textos.rotulo,
            color = Movi.colores.textoMedio,
        )
        preguntas.forEach { pregunta ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Movi.formas.amplia))
                    .background(Movi.colores.tarjeta)
                    .clickable { onPregunta(pregunta) }
                    .padding(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.medio),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Movi.colores.marca,
                    modifier = Modifier.size(Movi.espacios.amplio),
                )
                Text(
                    text = pregunta,
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.texto,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(
            text = NO_REEMPLAZA_A_UN_ASESOR,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
        )
    }
}

/** El saludo del arranque. Sin nombre no se inventa uno: «¡Hola!» a secas. */
internal fun saludoDelChat(nombre: String?): String =
    if (nombre.isNullOrBlank()) "¡Hola!" else "¡Hola, $nombre!"

/**
 * El primer nombre de la sesión: «Camilo Andrés Villada» saluda como «Camilo». Un saludo con el
 * nombre completo suena a formulario, no a alguien que te conoce.
 */
internal fun primerNombre(nombreCompleto: String?): String? =
    nombreCompleto?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() }

/**
 * Lo que Movi mira para contestar. Tiene que coincidir con lo que el server de verdad le pasa al
 * modelo (`buildUserContext` y las herramientas): prometer que «mira tus inversiones» sin que el
 * contexto las lleve sería la primera mentira de la conversación.
 */
internal const val QUE_MIRA_MOVI =
    "Miro tus movimientos, deudas, presupuestos y bienes para contestarte con tus números."

/** El rótulo arriba de los chips. En la escala `rotulo`, que va en mayúsculas como los demás. */
internal const val ROTULO_DE_LAS_SUGERENCIAS = "PREGÚNTALE, POR EJEMPLO"

/** La nota honesta del arranque. Una línea, sin sermón. */
internal const val NO_REEMPLAZA_A_UN_ASESOR =
    "Movi AI te orienta con tus datos, pero no reemplaza a un asesor financiero certificado."
