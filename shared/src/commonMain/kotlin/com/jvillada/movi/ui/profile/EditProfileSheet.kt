package com.jvillada.movi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AvatarPalette
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.credits.FieldBox
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.avatarColorOrDefault
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch

/**
 * F42 · F46 — editar alias y color de avatar. Se abre desde el "Editar" del encabezado de
 * [PerfilScreen]. Mismo patrón que las hojas de Créditos ([com.jvillada.movi.ui.credits.CardTermsSheet]):
 * scrim + hoja, `SheetHandleWithClose`, botón que nunca se apaga en silencio (dice la primera
 * cosa que falta debajo).
 */
@Composable
fun EditProfileSheet(
    initialName: String,
    initialColor: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor ?: AvatarPalette.DEFAULT) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val trimmed = name.trim()
    val missingFieldMessage = when {
        trimmed.isBlank() -> "Falta tu nombre"
        trimmed.length > 100 -> "El nombre no puede superar los 100 caracteres"
        else -> null
    }
    val canSave = missingFieldMessage == null && !saving

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                Repositories.wallets.updateUserProfile(UpdateProfileRequest(name = trimmed, avatarColor = color))
            }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !saving, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss, enabled = !saving)

            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                Text("ALIAS", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MinTextMute, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(8.dp))
                FieldBox(
                    placeholder = "Tu nombre",
                    value = name,
                    onValueChange = { name = it },
                    keyboardType = KeyboardType.Text,
                )

                Spacer(Modifier.height(20.dp))
                Text("COLOR DEL AVATAR", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MinTextMute, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(10.dp))
                for (row in AvatarPalette.COLORS.chunked(4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        for (hex in row) {
                            ColorSwatch(hex = hex, selected = hex == color, onClick = { color = hex })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = MinExpense)
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) MinText else MinTextFaint)
                    .clickable(enabled = canSave) { save() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (saving) "Guardando…" else "Guardar", color = MinBg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            if (!canSave && !saving && missingFieldMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = missingFieldMessage,
                    fontSize = 12.sp,
                    color = MinTextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(avatarColorOrDefault(hex))
            .then(if (selected) Modifier.border(2.dp, MinText, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Elegido", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
