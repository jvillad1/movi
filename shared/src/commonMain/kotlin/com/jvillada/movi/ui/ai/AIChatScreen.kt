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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import kotlinx.coroutines.launch

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
    val listState = rememberLazyListState()

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || loading) return
        messages.add(ChatMessage(ChatRole.USER, text))
        input = ""
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
                    AIMsgUser(msg.content)
                } else {
                    AIMsgAI(msg.content)
                }
            }
            if (loading) {
                item { AIMsgAI("…") }
            }
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
            val canSend = input.trim().isNotEmpty() && !loading
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (canSend) MinText else MinSurfaceContainerHigh)
                    .clickable(enabled = canSend) { send() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (loading) "…" else "›",
                    fontSize = 20.sp,
                    color = if (canSend) MinBg else MinTextMute,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // F30: era, junto con SMS, la única pantalla de Más sin la barra. Va debajo del campo
        // de texto (arriba) para no taparlo; en web/escritorio ancho MinBottomNav no pinta nada.
        MinBottomNav(active = NavTab.MORE) { tab ->
            when (tab) {
                NavTab.HOME         -> onNavigate(Screen.Dashboard)
                NavTab.TRANSACTIONS -> onNavigate(Screen.Transactions)
                NavTab.ADD          -> onNavigate(Screen.QuickAdd())
                NavTab.BUDGETS      -> onNavigate(Screen.Budgets)
                NavTab.MORE         -> onNavigate(Screen.Mas)
            }
        }
    }
}

@Composable
private fun AIMsgUser(text: String) {
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
            Text(text = text, fontSize = 13.5.sp, color = MinBg, lineHeight = 20.sp)
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
