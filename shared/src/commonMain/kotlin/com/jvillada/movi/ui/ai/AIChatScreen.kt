package com.jvillada.movi.ui.ai

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.datetime.Clock
import com.jvillada.movi.ui.components.ds.AccionDeEncabezado
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.CompositionLocalProvider
import com.jvillada.movi.ui.components.LocalHayTecladoFisico
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.EstadoDePropuesta
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.esCodigoDeIaNoDisponible
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.dashboard.teQuedanDelInicio
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
    /**
     * El [PreguntaSugerida.id] de [preguntaInicial] si es una de las tres que Movi contesta sin el
     * modelo (el chip de Hoy lo trae). `null` = pregunta libre.
     */
    sugeridaInicial: String? = null,
    /**
     * Vuelta 33: Movi AI es la raíz de su pestaña (el rail de una ventana ancha, ver
     * [com.jvillada.movi.ui.moviAiEsLaRaizDeSuPestana]): el encabezado lleva el avatar, como las
     * otras raíces. `false` = se abrió encima de otra pantalla y lleva la flecha.
     */
    comoRaiz: Boolean = false,
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
    val todasLasSugeridas = remember { preguntasParaTocar(DashboardDataCache.data ?: DashboardData()) }
    // Vuelta de diseño 30: ¿la IA contesta preguntas libres? Lo dice la conversación al llegar
    // (`iaDisponible`) y lo corrige cada respuesta: un aviso de «no disponible» la apaga, una del
    // modelo la prende. Sin ella, solo se ofrecen las tres que Movi contesta sin el modelo.
    var iaDisponible by remember { mutableStateOf(true) }
    val sugeridas = preguntasQueSePuedenHacer(todasLasSugeridas, iaDisponible)
    // «Empezar una conversación nueva» arriba, si la guardada es de otro día y el dueño no dijo
    // «Seguir con esta».
    var seguirConLaVieja by remember { mutableStateOf(false) }
    val hoy = remember { hoyEnAppZone() }
    // La última respuesta dijo que la IA no está disponible: debajo van las tres que Movi contesta
    // sin ella, para que «sí funcionan» se pueda tocar (ver [MOVI_AI_NO_DISPONIBLE]).
    var ofrecerLasQueFuncionan by remember { mutableStateOf(false) }
    // **La conversación sobrevive** (Ola 3): al abrir se pide la conversación en curso al server.
    // Mientras no llega no se pinta el arranque, para que no parpadee un chat vacío antes de que
    // aparezca lo que ya se habló. Si falla (sin señal) el chat arranca vacío, como antes.
    var historialCargado by remember { mutableStateOf(false) }
    // Lo que el dueño decidió de cada tarjeta, por id (Ola 3). Vive acá y no en la tarjeta: una
    // tarjeta que sale de la pantalla al hacer scroll no puede volver con «Hacerlo» ya hecha.
    val estadosDePropuestas = remember { mutableStateMapOf<String, EstadoDePropuesta>() }
    var aviso by remember { mutableStateOf<String?>(null) }
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
    fun enviar(texto: String, delChip: PreguntaSugerida? = null) {
        val text = texto.trim()
        // Vuelta 61: lo escrito a mano casi igual que una de las tres («¿Cómo voy?») viaja con su id,
        // como el chip, y se contesta sin la IA. Con una foto pendiente no: la foto es para el modelo.
        val sugerida = delChip ?: PreguntaSugerida.delTexto(text).takeIf { pendingImage == null }
        // Una de las tres sugeridas viaja sin la foto pendiente: la contesta Movi con sus números, y
        // la foto se queda esperando la pregunta para la que se adjuntó.
        val image = if (sugerida != null) null else pendingImage
        if ((text.isEmpty() && image == null) || loading) return
        messages.add(
            ChatMessage(
                role = ChatRole.USER,
                content = text,
                imageBase64 = image?.let { Base64.encode(it.bytes) },
                imageMime = image?.mimeType,
                preguntaSugerida = sugerida?.id,
                enviadoEn = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        input = ""
        if (image != null) pendingImage = null
        ofrecerLasQueFuncionan = false
        loading = true
        coroutine.launch {
            // Lo que viaja NO es la lista de la pantalla: sin las imágenes de turnos anteriores
            // (ya se mandaron y se pagaron una vez) y con tope. Ver [mensajesParaEnviar].
            val history = mensajesParaEnviar(messages)
            val reply = runCatching { Repositories.wallets.chatAi(AiChatRequest(history)) }
            val replyText = textoDeLaRespuesta(reply.getOrNull(), reply.exceptionOrNull())
            messages.add(
                ChatMessage(
                    ChatRole.ASSISTANT, replyText, propuestas = reply.getOrNull()?.propuestas.orEmpty(),
                    enviadoEn = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            ofrecerLasQueFuncionan = esCodigoDeIaNoDisponible(reply.getOrNull()?.codigo)
            // Lo que contestó dice si la IA anda: el aviso la apaga; una respuesta del modelo (no una
            // de las tres sin IA, que contestan igual sin crédito) la prende.
            if (ofrecerLasQueFuncionan) iaDisponible = false
            else if (reply.isSuccess && sugerida == null) iaDisponible = true
            loading = false
        }
    }

    fun send() = enviar(input)

    // **La pregunta con la que se abrió, una sola vez.** `rememberSaveable` y no `remember`: al
    // volver a esta entrada de la pila (o al girar el teléfono) la conversación se rearma vacía,
    // pero la pregunta ya se mandó y se pagó — mandarla de nuevo sería cobrarle dos veces el mismo
    // toque. Queda el arranque con sus sugerencias, y el dueño decide.
    var yaSeMandoLaInicial by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Primero lo que ya se habló, después la pregunta con la que se abrió: así la nueva queda
        // al final de la conversación y no antes de lo que se recarga.
        val guardada = runCatching { Repositories.wallets.getConversacionDelAsistente() }.getOrNull()
        if (guardada != null && messages.isEmpty()) messages.addAll(guardada.mensajes)
        if (guardada != null) iaDisponible = guardada.iaDisponible
        historialCargado = true
        if (!yaSeMandoLaInicial && !preguntaInicial.isNullOrBlank()) {
            yaSeMandoLaInicial = true
            enviar(preguntaInicial, PreguntaSugerida.deId(sugeridaInicial))
        }
    }

    fun nuevaConversacion() {
        if (loading) return
        coroutine.launch {
            val hecho = runCatching { Repositories.wallets.empezarConversacionNueva() }.isSuccess
            if (hecho) {
                messages.clear()
                aviso = null
            } else {
                aviso = NO_PUDE_EMPEZAR_OTRA
            }
        }
    }

    // Vuelta de diseño 30: la lista pinta piezas (los separadores de día y los mensajes), no los
    // mensajes sueltos: el último índice sale de ellas.
    // Vuelta 45: una respuesta de HOY también se pone vieja —«Esto era a las 9:40; ahora te quedan
    // $773.950»— si pasó más de una hora o si su «Te quedan» ya no es el del Inicio (ver
    // [loQueDiceLaRespuestaVieja]). El momento es el de abrir: lo que llega después no se juzga.
    val abiertoEn = remember { Clock.System.now().toEpochMilliseconds() }
    val teQuedanAhora = remember { DashboardDataCache.data?.let { teQuedanDelInicio(it) }?.teQuedan }
    val datosDelInicioDe = remember { DashboardDataCache.cargadoEn.takeIf { it > 0L } }
    val piezas = piezasDelChat(
        messages,
        hoy,
        ahora = abiertoEn,
        teQuedanAhora = teQuedanAhora,
        datosDe = datosDelInicioDe,
        cifra = ::formatCOP,
    )
    LaunchedEffect(messages.size, loading) {
        val target = piezas.size + if (loading) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }
    // **Y al abrir el teclado también.** La lista encoge por el `imePadding()` de la raíz, así que
    // sin esto lo último que se dijo queda tapado justo cuando el dueño va a escribir la respuesta
    // — el mismo síntoma que el teclado encima del campo, un renglón más arriba.
    val tecladoALaVista = elTecladoEstaALaVista()
    LaunchedEffect(tecladoALaVista) {
        if (tecladoALaVista && piezas.isNotEmpty()) listState.animateScrollToItem(piezas.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60 · F22: encabezado único; Movi AI se abre desde Más — destino de reserva si no
        // hay historial. El «BETA» pasa a la derecha, como marca, no como parte del título.
        // Vuelta de diseño 30: **con fondo opaco y encima de la lista**. Al hacer scroll el texto de
        // la conversación se veía por detrás del título.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .background(Movi.colores.fondo)
                .testTag(TAG_ENCABEZADO_DE_MOVI_AI),
        ) {
        MinScreenHeader(
            title = "Movi AI",
            // Vuelta 38: como raíz del rail, sin avatar (va solo en Inicio) ni flecha.
            leading = if (comoRaiz) HeaderLeading.Ninguno else HeaderLeading.Back(fallback = Screen.Tu),
            subtitle = "Conoce tus finanzas",
            // Con conversación, la acción es empezar otra (con la forma de toda acción de encabezado);
            // sin conversación no hay nada que limpiar y queda la marca «Beta», una [Pildora].
            action = if (messages.isEmpty()) {
                { Pildora(MARCA_BETA) }
            } else null,
            accion = if (messages.isNotEmpty()) {
                AccionDeEncabezado(NUEVA_CONVERSACION, Icons.Rounded.Add, onClick = { nuevaConversacion() }, habilitado = !loading)
            } else null,
        )
        }

        // La conversación guardada es de otro día: se ofrece empezar otra, sin borrar nada sola.
        val tituloDeLaVieja = if (seguirConLaVieja || loading) null else tituloDeLaConversacionVieja(messages, hoy)
        if (tituloDeLaVieja != null) {
            TarjetaDePropuesta(
                icono = Icons.Rounded.History,
                titulo = tituloDeLaVieja,
                cuerpo = CUERPO_DE_LA_CONVERSACION_VIEJA,
                accion = EMPEZAR_UNA_CONVERSACION_NUEVA,
                onAccion = { nuevaConversacion() },
                // Vuelta 82: la única acción principal del chat.
                varianteDeLaAccion = com.jvillada.movi.ui.components.ds.VarianteDeBoton.Principal,
                accionSecundaria = SEGUIR_ESTA,
                onAccionSecundaria = { seguirConLaVieja = true },
                modifier = Modifier
                    .padding(horizontal = Medida.margenDePantalla)
                    .padding(bottom = Espacio.xs)
                    .testTag(TAG_CONVERSACION_DE_OTRO_DIA),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).clipToBounds(),
            state = listState,
            contentPadding = PaddingValues(Espacio.m),
        ) {
            if (messages.isEmpty() && !loading && historialCargado) {
                item {
                    ArranqueDelChat(
                        nombre = nombre,
                        // Vuelta 77: las tres gratis (sin la IA) siempre primero.
                        preguntas = preguntasDelArranque(todasLasSugeridas, iaDisponible),
                        onPregunta = { enviar(it.texto, it.sugerida) },
                    )
                }
            }
            // Vuelta de diseño 30: los días separados, y lo de otro día atenuado y con su fecha.
            items(piezas) { pieza ->
                when (pieza) {
                    is PiezaDelChat.Dia -> Seccion(titulo = pieza.rotulo, modifier = Modifier.testTag(TAG_DIA_DEL_CHAT))
                    is PiezaDelChat.Mensaje -> {
                val msg = pieza.mensaje
                if (msg.role == ChatRole.USER) {
                    AIMsgUser(msg.content, hasImage = msg.imageBase64 != null || msg.teniaImagen)
                } else {
                    // Vuelta 61: los nombres de pantalla de la respuesta llevan a ella.
                    CompositionLocalProvider(LocalAbrirPantallaDesdeElChat provides onNavigate) {
                        AIMsgAI(msg.content, apagado = pieza.deOtroDia)
                    }
                    val loQueEra = pieza.vieja ?: pieza.cifrasDe?.let(::textoDeLasCifrasViejas)
                    if (pieza.deOtroDia && loQueEra != null) {
                        // «Preguntar de nuevo» solo si se puede contestar: una pregunta libre sin la IA
                        // daría el aviso otra vez.
                        val repetir = pieza.pregunta?.takeIf { iaDisponible || it.preguntaSugerida != null }
                        LasCifrasEranDeOtroDia(
                            texto = loQueEra,
                            onPreguntarDeNuevo = repetir?.let { p -> { preguntaParaRepetir(p).let { enviar(it.texto, it.sugerida) } } },
                            habilitado = !loading,
                        )
                    }
                    msg.propuestas.forEach { propuesta ->
                        PropuestaDelAsistente(
                            propuesta = propuesta,
                            estado = estadosDePropuestas[propuesta.id] ?: propuesta.estado,
                            onEstado = { estadosDePropuestas[propuesta.id] = it },
                        )
                    }
                }
                    }
                }
            }
            if (loading) {
                item { AIMsgAI("…") }
            }
            if (ofrecerLasQueFuncionan && !loading) {
                item {
                    LasQueFuncionanSinLaIa { enviar(it.texto, it.sugerida) }
                }
            }
            // Vuelta de diseño 12: debajo de la última respuesta, las que no se hicieron todavía.
            // Mientras Movi escribe no van; con el aviso de la IA no disponible ya están las tres
            // de arriba, que son las que funcionan.
            val paraSeguir = if (!loading && !ofrecerLasQueFuncionan && messages.lastOrNull()?.role == ChatRole.ASSISTANT) {
                preguntasParaSeguir(sugeridas, messages)
            } else emptyList()
            if (paraSeguir.isNotEmpty()) {
                item {
                    PreguntasParaSeguir(paraSeguir) { enviar(it.texto, it.sugerida) }
                }
            }
        }

        // F32: nombre del adjunto + X para quitarlo, o el aviso de "por ahora solo imágenes"
        // si eligieron otra cosa (p.ej. un PDF) — visible arriba de la barra de escribir.
        aviso?.let {
            Text(
                text = it,
                style = Movi.textos.apoyo,
                color = Movi.colores.sale,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.l, vertical = Espacio.xxs),
            )
        }

        if (pendingImage != null || attachError != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                if (pendingImage != null) {
                    Icon(Icons.Rounded.Image, contentDescription = null, tint = Movi.colores.textoMedio, modifier = Modifier.size(Icono.s))
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
                        modifier = Modifier.size(Icono.s).clickable { pendingImage = null },
                    )
                } else {
                    Text(text = attachError ?: "", style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Espacio.xs))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Espacio.m, vertical = Espacio.s)
                .clip(RoundedCornerShape(Radio.xl))
                .background(Movi.colores.tarjeta)
                .padding(horizontal = Espacio.m, vertical = Espacio.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            // F32: clip para adjuntar una foto (recibo, extracto, oferta del banco).
            Icon(
                Icons.Rounded.AttachFile,
                contentDescription = "Adjuntar imagen",
                tint = Movi.colores.textoMedio,
                modifier = Modifier.size(Icono.m).clickable(enabled = !loading) { attachError = null; launchPicker() },
            )
            Box(modifier = Modifier.weight(1f)) {
                // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver
                // [esAtajoDeSeleccionarTodo].
                val campo = rememberCampoConSeleccion(input) { input = it }
                // Vuelta 61: con teclado físico (la web, iOS con teclado), **Enter envía** y Shift+Enter
                // sigue siendo un salto de renglón (no se atiende acá: lo hace el campo). En el
                // teléfono Android el Enter del teclado en pantalla sigue siendo un salto de renglón.
                val tecladoFisico = LocalHayTecladoFisico.current
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
                        .testTag(TAG_CAMPO_DE_MOVI_AI)
                        .onPreviewKeyEvent { evento ->
                            if (tecladoFisico && esEnterParaEnviar(evento)) {
                                send()
                                true
                            } else {
                                campo.atajoDeSeleccionarTodo(evento)
                            }
                        },
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
                    .size(Medida.altoChico)
                    .clip(CircleShape)
                    .background(if (canSend) Movi.colores.texto else Movi.colores.tarjeta)
                    .clickable(enabled = canSend) { send() },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    // Glifo de espera dentro del botón de enviar: va al tamaño del botón, no es texto de la escala.
                    Text(text = "…", style = Movi.textos.titulo, color = if (canSend) Movi.colores.fondo else Movi.colores.textoMedio)
                } else {
                    // Ola 2 #5 (F11): "›" como texto suelto salía roto en la web, igual que "‹".
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Enviar",
                        tint = if (canSend) Movi.colores.fondo else Movi.colores.textoMedio,
                        modifier = Modifier.size(Icono.l),
                    )
                }
            }
        }
    }
}

/** Tag del campo de escribir de Movi AI. */
internal const val TAG_CAMPO_DE_MOVI_AI: String = "ai-campo-de-escribir"

/**
 * **¿Esta tecla envía?** (vuelta 61): Enter (o el Enter del teclado numérico) al bajar, sin Shift.
 * Shift+Enter es el salto de renglón. Solo se mira con teclado físico (ver el campo de Movi AI).
 */
internal fun esEnterParaEnviar(evento: KeyEvent): Boolean =
    evento.type == KeyEventType.KeyDown &&
        (evento.key == Key.Enter || evento.key == Key.NumPadEnter) &&
        !evento.isShiftPressed

/** Tag del encabezado de Movi AI, con su fondo opaco. */
internal const val TAG_ENCABEZADO_DE_MOVI_AI: String = "ai-encabezado"

/** Tag de cada separador de día del chat. */
internal const val TAG_DIA_DEL_CHAT: String = "ai-dia-del-chat"

/** Tag de la línea «Estas cifras eran de ayer» de una respuesta de otro día. */
internal const val TAG_CIFRAS_DE_OTRO_DIA: String = "ai-cifras-de-otro-dia"

/** Tag de la tarjeta que ofrece empezar otra conversación. */
internal const val TAG_CONVERSACION_DE_OTRO_DIA: String = "ai-conversacion-de-otro-dia"

/**
 * Debajo de una respuesta de otro día: «Estas cifras eran de ayer» y, si se puede, «Preguntar de
 * nuevo» (un [Boton] de texto). Alineado con el texto de la respuesta, después del ícono.
 */
@Composable
private fun LasCifrasEranDeOtroDia(texto: String, onPreguntarDeNuevo: (() -> Unit)?, habilitado: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Icono.l + Espacio.s, bottom = Espacio.s)
            .testTag(TAG_CIFRAS_DE_OTRO_DIA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Text(
            text = texto,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.weight(1f),
        )
        if (onPreguntarDeNuevo != null) {
            Boton(
                PREGUNTAR_DE_NUEVO,
                onClick = onPreguntarDeNuevo,
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
                habilitado = habilitado,
            )
        }
    }
}

/** Tag estable de la burbuja de lo que escribió el dueño. */
internal const val TAG_BURBUJA_DEL_DUENO: String = "ai-burbuja-del-dueno"

/**
 * **El fondo de la burbuja del dueño** (vuelta de diseño 12): el lavado de la marca. Era el color
 * del texto (negra en el tema claro, blanca en el oscuro), sin relación con la paleta.
 * `ContrasteDeLosTokensTest` mide que [textoDeLaBurbujaDelDueno] se lea encima, en los dos temas.
 */
internal fun fondoDeLaBurbujaDelDueno(c: ColoresDeMovi): Color = c.lavado(c.marca)

/** El texto de la burbuja del dueño: el del sistema, que se lee sobre el lavado de la marca. */
internal fun textoDeLaBurbujaDelDueno(c: ColoresDeMovi): Color = c.texto

@Composable
private fun AIMsgUser(text: String, hasImage: Boolean = false) {
    val colorDelTexto = textoDeLaBurbujaDelDueno(Movi.colores)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Espacio.s),
        horizontalArrangement = Arrangement.End,
    ) {
        // El respiro a la izquierda marca el ancho máximo de la burbuja (antes, 280 dp sueltos):
        // una pregunta corta queda a la derecha, una larga no llega al borde de la otra mano.
        Spacer(Modifier.width(Espacio.xxxl))
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(Radio.l))
                .background(fondoDeLaBurbujaDelDueno(Movi.colores))
                .testTag(TAG_BURBUJA_DEL_DUENO)
                .padding(horizontal = Espacio.s, vertical = Espacio.s),
        ) {
            Column {
                if (hasImage) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = colorDelTexto, modifier = Modifier.size(Icono.s))
                        Text("Imagen adjunta", style = Movi.textos.apoyo, color = colorDelTexto)
                    }
                    if (text.isNotBlank()) Spacer(Modifier.height(Espacio.xxs))
                }
                if (text.isNotBlank()) {
                    Text(text = text, style = Movi.textos.cuerpoRegular, color = colorDelTexto)
                }
            }
        }
    }
}

@Composable
private fun AIMsgAI(text: String, apagado: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Espacio.s),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(Icono.l)
                .clip(CircleShape)
                .background(Movi.colores.tarjeta),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Movi.colores.texto, modifier = Modifier.size(Icono.s))
        }
        Spacer(Modifier.width(Espacio.s))
        // Vuelta de diseño 12: con formato (párrafos, listas, negrita, cifras) y a todo el ancho que
        // queda; antes era un solo texto plano de 290 dp como mucho.
        TextoDelAsistente(text, modifier = Modifier.weight(1f), apagado = apagado)
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
    preguntas: List<PreguntaParaTocar>,
    onPregunta: (PreguntaParaTocar) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = Medida.entreSecciones),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
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
        Seccion(titulo = ROTULO_DE_LAS_SUGERENCIAS)
        preguntas.forEach { pregunta -> ChipDePregunta(pregunta.texto) { onPregunta(pregunta) } }
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(
            text = NO_REEMPLAZA_A_UN_ASESOR,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
        )
    }
}

/** Un chip de pregunta: tocarlo la manda. Lo usan el arranque y [LasQueFuncionanSinLaIa]. */
@Composable
private fun ChipDePregunta(texto: String, onClick: () -> Unit) {
    // Ola R4: una superficie tocable del sistema (la pregunta puede ocupar dos renglones, por eso
    // no es una `Fila`, que corta en uno).
    Superficie(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Movi.colores.enlace, modifier = Modifier.size(Icono.m))
            Text(text = texto, style = Movi.textos.cuerpo, color = Movi.colores.texto, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * **Las tres que Movi contesta sin la IA**, debajo de una respuesta que dijo que la IA no está
 * disponible: la frase dice que sí funcionan, y acá se tocan. Siempre las tres de respaldo —las que
 * llevan su id—, no las que arman las reglas: esas van al modelo.
 */
@Composable
internal fun LasQueFuncionanSinLaIa(onPregunta: (PreguntaParaTocar) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Espacio.xxl, bottom = Movi.espacios.amplio),
        verticalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
    ) {
        PREGUNTAS_PARA_TOCAR_DE_RESPALDO.forEach { pregunta -> ChipDePregunta(pregunta.texto) { onPregunta(pregunta) } }
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

/** El título de la sección de los chips: una `Seccion`, en frase (ola R4). */
internal const val ROTULO_DE_LAS_SUGERENCIAS = "Pregúntale, por ejemplo"

/** La acción del encabezado cuando hay conversación (Ola 3). */
internal const val NUEVA_CONVERSACION = "Nueva conversación"

/** La marca del encabezado de Movi AI, mientras no hay conversación: una [Pildora] del sistema. */
internal const val MARCA_BETA = "Beta"

/** Lo que se dice si «Nueva conversación» no llegó al server: no se borra en pantalla lo que sigue allá. */
internal const val NO_PUDE_EMPEZAR_OTRA = "No pude empezar una conversación nueva. Revisa tu conexión e intenta otra vez."

/** La nota honesta del arranque. Una línea, sin sermón. */
internal const val NO_REEMPLAZA_A_UN_ASESOR =
    "Movi AI te orienta con tus datos, pero no reemplaza a un asesor financiero certificado."
