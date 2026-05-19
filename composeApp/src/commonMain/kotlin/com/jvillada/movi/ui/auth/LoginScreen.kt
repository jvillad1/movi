package com.jvillada.movi.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(MinBg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Movi", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MinText)
        Text("Finanzas personales", fontSize = 14.sp, color = MinTextMute)
        Spacer(Modifier.height(40.dp))

        MinCard(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(20.dp)) {
            Text("Correo", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(value = email, onChange = { email = it }, placeholder = "tu@correo.com")
            Spacer(Modifier.height(16.dp))
            Text("Contraseña", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(value = password, onChange = { password = it }, placeholder = "••••••", isPassword = true)

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontSize = 12.sp, color = MinExpense)
            }

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (loading) MinSurfaceContainerHigh else MinPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (loading) "Entrando…" else "Entrar",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (loading) MinTextMute else MinBg,
                    modifier = Modifier.noRippleClickable {
                        if (!loading) {
                            loading = true
                            error = null
                            coroutine.launch {
                                runCatching {
                                    Repositories.wallets.login(LoginRequest(email.trim(), password))
                                }.onSuccess { resp ->
                                    SessionManager.save(resp.token, resp.userId, resp.name, resp.email)
                                    loading = false
                                    onNavigate(Screen.Dashboard)
                                }.onFailure {
                                    error = "Correo o contraseña incorrectos"
                                    loading = false
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "¿No tenés cuenta? Registrate",
            fontSize = 13.sp, color = MinPrimary,
            modifier = Modifier.noRippleClickable { onNavigate(Screen.Register) }
        )
    }
}

@Composable
internal fun AuthField(value: String, onChange: (String) -> Unit, placeholder: String, isPassword: Boolean = false) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(fontSize = 15.sp, color = MinText),
        cursorBrush = SolidColor(MinPrimary),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MinSurfaceContainer).padding(12.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, color = MinTextFaint, fontSize = 15.sp)
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun Modifier.noRippleClickable(onClick: () -> Unit) = this.then(
    clickable(
        indication = null,
        interactionSource = MutableInteractionSource(),
        onClick = onClick,
    )
)
