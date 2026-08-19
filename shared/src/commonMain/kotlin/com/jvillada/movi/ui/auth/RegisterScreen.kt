package com.jvillada.movi.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (loading) return
        if (name.isBlank() || email.isBlank()) {
            error = "Nombre y correo requeridos"
            return
        }
        // El mínimo sale de PasswordPolicy (:core), el mismo objeto que usa el servidor para
        // validar. Antes había un `6` escrito a mano acá y otro en AuthRoutes.kt: mover uno no
        // movía el otro. Esto es cortesía de UI; la validación que manda es la del servidor.
        PasswordPolicy.problemWith(password)?.let {
            error = PasswordPolicy.messageFor(it)
            return
        }
        loading = true; error = null
        focusManager.clearFocus()
        coroutine.launch {
            runCatching {
                Repositories.wallets.register(RegisterRequest(email.trim(), name.trim(), password))
            }.onSuccess { resp ->
                SessionManager.save(resp.token, resp.userId, resp.name, resp.email)
                loading = false
                onNavigate(Screen.Dashboard)
            }.onFailure {
                error = if (it.message?.contains("409") == true) "Ese correo ya está registrado"
                        else "Error al crear la cuenta"
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MinBg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Crear cuenta", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MinText)
        Text("Ingresa tus datos para empezar", fontSize = 14.sp, color = MinTextMute)
        Spacer(Modifier.height(32.dp))

        MinCard(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(20.dp)) {
            Text("Nombre", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(
                value = name,
                onChange = { name = it },
                placeholder = "Tu nombre",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
            )
            Spacer(Modifier.height(14.dp))
            Text("Correo", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(
                value = email,
                onChange = { email = it },
                placeholder = "tu@correo.com",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                modifier = Modifier.focusRequester(emailFocus),
            )
            Spacer(Modifier.height(14.dp))
            Text("Contraseña", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(
                value = password,
                onChange = { password = it },
                placeholder = "•".repeat(PasswordPolicy.MIN_LENGTH),
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.focusRequester(passwordFocus),
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontSize = 12.sp, color = MinExpense)
            }

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (loading) MinSurfaceContainerHigh else MinPrimary)
                    .noRippleClickable { submit() },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MinPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "Crear cuenta",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MinBg,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("¿Ya tienes cuenta? Entra", fontSize = 13.sp, color = MinPrimary,
            modifier = Modifier.noRippleClickable { onNavigate(Screen.Login) })
    }
}
