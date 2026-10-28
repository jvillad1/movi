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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AvatarPalette
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.avatarColorOrDefault
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.RotuloDeCampo

/**
 * F42 · F46 — editar alias y color de avatar. Se abre desde el "Editar" del encabezado de
 * Ajustes ([com.jvillada.movi.ui.mas.MasScreen]). Mismo patrón que las hojas de Créditos ([com.jvillada.movi.ui.credits.CardTermsSheet]):
 * `MarcoDeHoja`, `EncabezadoDeHoja`, `Campo` y `PieDeHoja`: el botón nunca se apaga en silencio
 * (dice la primera cosa que falta debajo).
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

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            // Vuelta de diseño 18: la hoja abre con su encabezado, como las demás; «Alias» era un
            // rótulo suelto que hacía de título.
            EncabezadoDeHoja("Tu perfil", subtitulo = "Cómo te llama Movi y el color de tu avatar.")
            Spacer(Modifier.height(Medida.entreSecciones))
            // Vuelta 41: el campo del sistema (era `FieldBox`, el de las hojas de Créditos).
            Campo(name, { name = it }, rotulo = "Alias", guia = "Tu nombre", habilitado = !saving)

            Spacer(Modifier.height(Espacio.l))
            RotuloDeCampo("Color del avatar")
            Spacer(Modifier.height(Espacio.xs))
            for (row in AvatarPalette.COLORS.chunked(4)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                ) {
                    for (hex in row) {
                        ColorSwatch(hex = hex, selected = hex == color, onClick = { color = hex })
                    }
                }
                Spacer(Modifier.height(Espacio.s))
            }
        }

        Spacer(Modifier.height(Espacio.m))
        // El pie del sistema: el botón y, si está apagado, la primera cosa que falta debajo.
        PieDeHoja(
            texto = "Guardar",
            onClick = { save() },
            habilitado = missingFieldMessage == null,
            razon = missingFieldMessage,
            cargando = saving,
            error = error,
        )
        Spacer(Modifier.height(Espacio.l))
    }
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Medida.avatarGrande)
            .clip(CircleShape)
            .background(avatarColorOrDefault(hex))
            .then(if (selected) Modifier.border(2.dp, Movi.colores.texto, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Elegido", tint = Movi.colores.sobreAvatar, modifier = Modifier.size(Icono.m))
        }
    }
}
