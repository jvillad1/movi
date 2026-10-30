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
    @Suppress("UNUSED_PARAMETER") initialColor: String? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
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
                Repositories.wallets.updateUserProfile(UpdateProfileRequest(name = trimmed))
            }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            // Vuelta de diseño 18: la hoja abre con su encabezado, como las demás; «Alias» era un
            // rótulo suelto que hacía de título.
            EncabezadoDeHoja("Tu perfil", subtitulo = "Cómo te llama Movi.")
            Spacer(Modifier.height(Medida.entreSecciones))
            // Vuelta 41: el campo del sistema (era `FieldBox`, el de las hojas de Créditos).
            Campo(name, { name = it }, rotulo = "Alias", guia = "Tu nombre", habilitado = !saving)

            // Vuelta 80: sin «Color del avatar». El avatar del dueño va siempre en el tinte, con sus
            // iniciales (`AvatarDelDueno`): un color elegido ya no pintaba nada.
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
