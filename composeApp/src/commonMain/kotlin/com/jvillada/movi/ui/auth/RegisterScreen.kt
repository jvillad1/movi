package com.jvillada.movi.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
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

    Column(
        modifier = Modifier.fillMaxSize().background(MinBg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Crear cuenta", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MinText)
        Text("Ingresá tus datos para empezar", fontSize = 14.sp, color = MinTextMute)
        Spacer(Modifier.height(32.dp))

        MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(20.dp)) {
            Text("Nombre", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(value = name, onChange = { name = it }, placeholder = "Tu nombre")
            Spacer(Modifier.height(14.dp))
            Text("Correo", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(value = email, onChange = { email = it }, placeholder = "tu@correo.com")
            Spacer(Modifier.height(14.dp))
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
                    if (loading) "Creando…" else "Crear cuenta",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (loading) MinTextMute else MinBg,
                    modifier = Modifier.noRippleClickable {
                        if (!loading) {
                            if (name.isBlank() || email.isBlank() || password.length < 6) {
                                error = "Nombre, correo y contraseña (mín. 6 chars) requeridos"
                                return@noRippleClickable
                            }
                            loading = true; error = null
                            coroutine.launch {
                                runCatching {
                                    Repositories.wallets.register(RegisterRequest(email.trim(), name.trim(), password))
                                }.onSuccess { resp ->
                                    SessionManager.save(resp.token, resp.userId, resp.name, resp.email)
                                    onNavigate(Screen.Dashboard)
                                }.onFailure {
                                    error = if (it.message?.contains("409") == true) "Ese correo ya está registrado"
                                            else "Error al crear la cuenta"
                                    loading = false
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("¿Ya tenés cuenta? Entrá", fontSize = 13.sp, color = MinPrimary,
            modifier = Modifier.noRippleClickable { onNavigate(Screen.Login) })
    }
}
