package com.jvillada.movi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.ChangePasswordRequest
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.auth.AuthField
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * F42 — cambiar contraseña estando adentro de la app. Distinto del reset por correo de
 * [com.jvillada.movi.ui.auth] (eso es para cuando no se puede entrar); esto es
 * `PUT /api/users/me/password` con la contraseña actual como prueba de identidad.
 *
 * [PasswordPolicy] se espeja acá SOLO como cortesía — el mismo mínimo que ve la persona en
 * Registro ([com.jvillada.movi.ui.auth.RegisterScreen]), pero quien manda de verdad es el
 * servidor (ver `UserRoutes.kt`); un 400 del servidor pasa igual por [toUserMessage].
 *
 * **Limitación conocida, no resuelta acá:** en la web, el overlay HTML de `index.html` es solo
 * para login — no se entera de que la contraseña cambió, así que el gestor de contraseñas del
 * navegador no ofrece actualizar lo guardado. Arreglarlo implica tocar `index.html`, fuera del
 * alcance de esta tarea (solo `PerfilScreen.kt` + hojas nuevas en `ui/profile/`).
 */
@Composable
fun ChangePasswordSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    // Mismo patrón "Falta…" que CardTermsSheet/CreditTermsSheet: la primera cosa que falta,
    // en el orden de los campos — nunca un botón apagado sin explicación.
    val missingFieldMessage = when {
        current.isBlank() -> "Falta tu contraseña actual"
        PasswordPolicy.problemWith(new) != null -> PasswordPolicy.messageFor(PasswordPolicy.problemWith(new)!!)
        repeat != new -> "Las contraseñas nuevas no coinciden"
        else -> null
    }
    val canSave = missingFieldMessage == null && !saving && !success

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                Repositories.wallets.changePassword(ChangePasswordRequest(current = current, new = new))
            }
            saving = false
            result.onSuccess { success = true }.onFailure { error = it.toUserMessage() }
        }
    }

    // Confirmación breve y cierre solo: no hay snackbar en este repo para este caso, y la hoja
    // ya dice "quedó actualizada" — cerrar de más al toque sería perder ese mensaje.
    LaunchedEffect(success) {
        if (success) {
            delay(1400)
            onSaved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !saving, onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss, enabled = !saving)

            if (success) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Listo, tu contraseña quedó actualizada.",
                        fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinIncome,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                    Text("Contraseña actual", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
                    AuthField(
                        value = current,
                        onChange = { current = it },
                        placeholder = "Tu contraseña de hoy",
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Contraseña nueva", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
                    AuthField(
                        value = new,
                        onChange = { new = it },
                        placeholder = "•".repeat(PasswordPolicy.MIN_LENGTH),
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Repetir la nueva", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
                    AuthField(
                        value = repeat,
                        onChange = { repeat = it },
                        placeholder = "Escríbela de nuevo",
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                    )

                    error?.let {
                        Spacer(Modifier.height(10.dp))
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
                    Text(if (saving) "Cambiando…" else "Cambiar contraseña", color = MinBg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
