package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.ChangePasswordRequest
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.PieDeHoja

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
/** El título de la hoja y su botón. */
const val CAMBIAR_CONTRASENA: String = "Cambiar contraseña"

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
            result.onSuccess { tokenNuevo ->
                // Cambiar la contraseña cerró las sesiones de TODOS los aparatos, también la de
                // este. El server manda un token de la versión nueva para que este siga adentro;
                // sin guardarlo, el pedido siguiente sería un 401 y la app pediría entrar de nuevo.
                tokenNuevo?.let { SessionManager.token = it }
                success = true
            }.onFailure { error = it.toUserMessage() }
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

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        if (success) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = Espacio.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Listo, tu contraseña quedó actualizada.",
                    style = Movi.textos.cuerpo, color = Movi.colores.entra,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    "Cerramos la sesión en tus otros aparatos.",
                    style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                // Vuelta 41: la hoja abre con su encabezado, como las demás (no tenía título).
                EncabezadoDeHoja(
                    CAMBIAR_CONTRASENA,
                    subtitulo = "Al cambiarla, se cierra la sesión en tus otros aparatos.",
                )
                Spacer(Modifier.height(Medida.entreSecciones))
                Campo(
                    rotulo = "Contraseña actual",
                    valor = current,
                    alCambiar = { current = it },
                    guia = "Tu contraseña de hoy",
                    secreto = true,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Campo(
                    rotulo = "Contraseña nueva",
                    valor = new,
                    alCambiar = { new = it },
                    guia = "•".repeat(PasswordPolicy.MIN_LENGTH),
                    secreto = true,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Campo(
                    rotulo = "Repetir la nueva",
                    valor = repeat,
                    alCambiar = { repeat = it },
                    guia = "Escríbela de nuevo",
                    secreto = true,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    acciones = KeyboardActions(onDone = { save() }),
                )
            }

            Spacer(Modifier.height(Espacio.l))
            // Vuelta 41: el pie del sistema —el error encima, la primera cosa que falta debajo— en vez
            // de una caja negra hecha a mano.
            PieDeHoja(
                texto = CAMBIAR_CONTRASENA,
                onClick = { save() },
                habilitado = missingFieldMessage == null,
                razon = missingFieldMessage,
                cargando = saving,
                error = error,
            )
        }
        Spacer(Modifier.height(Espacio.l))
    }
}
