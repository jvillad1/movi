package com.jvillada.movi.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (loading) return
        loading = true
        error = null
        focusManager.clearFocus()
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
            AuthField(
                value = email,
                onChange = { email = it },
                placeholder = "tu@correo.com",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            )
            Spacer(Modifier.height(16.dp))
            Text("Contraseña", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(
                value = password,
                onChange = { password = it },
                placeholder = "••••••",
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
                        "Entrar",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MinBg,
                    )
                }
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
internal fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val showAsPassword = isPassword && !passwordVisible
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(fontSize = 15.sp, color = MinText),
        cursorBrush = SolidColor(MinPrimary),
        singleLine = true,
        visualTransformation = if (showAsPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MinSurfaceContainer).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = MinTextFaint, fontSize = 15.sp)
                    inner()
                }
                if (isPassword) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = MinTextMute,
                        modifier = Modifier.size(20.dp)
                            .noRippleClickable { passwordVisible = !passwordVisible },
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

internal fun Modifier.noRippleClickable(onClick: () -> Unit) = this.then(
    clickable(
        indication = null,
        interactionSource = MutableInteractionSource(),
        onClick = onClick,
    )
)
