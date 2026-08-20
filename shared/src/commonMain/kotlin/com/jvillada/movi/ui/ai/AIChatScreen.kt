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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.extractos.rememberFilePicker
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.launch

/** F32: lo que espera a ser enviado — foto de un recibo, extracto u oferta del banco. */
private data class PendingImage(val fileName: String, val bytes: ByteArray, val mimeType: String)

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun AIChatScreen(onNavigate: (Screen) -> Unit) {
    val goBack = LocalGoBack.current
    val coroutine = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf<ChatMessage>(
            ChatMessage(ChatRole.ASSISTANT, "¡Hola Camilo! Pregúntame lo que quieras sobre tu plata."),
        )
    }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<PendingImage?>(null) }
    var attachError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // F32: el picker de la Ola 1 (extractos) acepta cualquier archivo — acá se filtra por
    // mime de imagen en el cliente, sin tocar el picker en sí.
    val launchPicker = rememberFilePicker { fileName, bytes, mimeType ->
        if (!mimeType.startsWith("image/")) {
            attachError = "Por ahora solo imágenes"
        } else {
            attachError = null
            pendingImage = PendingImage(fileName, bytes, mimeType)
        }
    }

    fun send() {
        val text = input.trim()
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
            val history = messages.filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
            val reply = runCatching { Repositories.wallets.chatAi(AiChatRequest(history)) }
            val replyText = reply.getOrNull()?.text
                ?: "No pude conectarme con el AI. ${reply.exceptionOrNull()?.message ?: ""}"
            messages.add(ChatMessage(ChatRole.ASSISTANT, replyText))
            loading = false
        }
    }

    LaunchedEffect(messages.size, loading) {
        val target = messages.size + if (loading) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Volver",
                tint = MinText,
                // F22: Movi AI vive en Más (así la resalta su propia barra inferior) —
                // destino de reserva si no hay historial.
                modifier = Modifier.size(22.dp).clickable { goBack(Screen.Mas) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Movi AI", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.2).sp)
                    Text(
                        "BETA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinTextMute,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                    )
                }
                Text("Conoce tus finanzas", fontSize = 11.sp, color = MinTextMute)
            }
        }
        Hairline()

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(18.dp),
        ) {
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
                    Icon(Icons.Rounded.Image, contentDescription = null, tint = MinTextMute, modifier = Modifier.size(16.dp))
                    Text(
                        text = pendingImage?.fileName ?: "",
                        fontSize = 12.sp,
                        color = MinTextMute,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Quitar imagen",
                        tint = MinTextMute,
                        modifier = Modifier.size(16.dp).clickable { pendingImage = null },
                    )
                } else {
                    Text(text = attachError ?: "", fontSize = 12.sp, color = MinExpense, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MinSurface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // F32: clip para adjuntar una foto (recibo, extracto, oferta del banco).
            Icon(
                Icons.Rounded.AttachFile,
                contentDescription = "Adjuntar imagen",
                tint = MinTextMute,
                modifier = Modifier.size(20.dp).clickable(enabled = !loading) { attachError = null; launchPicker() },
            )
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !loading,
                    cursorBrush = SolidColor(MinText),
                    textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                "Pregúntale a Movi…",
                                fontSize = 13.5.sp,
                                color = MinTextMute,
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
                    .background(if (canSend) MinText else MinSurfaceContainerHigh)
                    .clickable(enabled = canSend) { send() },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    Text(text = "…", fontSize = 20.sp, color = if (canSend) MinBg else MinTextMute, fontWeight = FontWeight.Bold)
                } else {
                    // Ola 2 #5 (F11): "›" como texto suelto salía roto en la web, igual que "‹".
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Enviar",
                        tint = if (canSend) MinBg else MinTextMute,
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
                .background(MinText)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column {
                if (hasImage) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = MinBg, modifier = Modifier.size(13.dp))
                        Text("Imagen adjunta", fontSize = 11.sp, color = MinBg)
                    }
                    if (text.isNotBlank()) Spacer(Modifier.height(4.dp))
                }
                if (text.isNotBlank()) {
                    Text(text = text, fontSize = 13.5.sp, color = MinBg, lineHeight = 20.sp)
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
                .background(MinSurfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MinText, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.widthIn(max = 290.dp)) {
            Text(text = text, fontSize = 13.5.sp, color = MinText, lineHeight = 20.sp)
        }
    }
}
