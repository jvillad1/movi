package com.jvillada.movi.sensor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.sms.SmsFilterConfigStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BgColor = Color(0xFF121212)
private val CardColor = Color(0xFF1E1E1E)
private val AccentColor = Color(0xFFC9B8FF)
private val TextColor = Color(0xFFF5F5F5)
private val TextMutedColor = Color(0xFFA0A0A0)
private val ErrorColor = Color(0xFFFF6B6B)

private const val SMS_PREFS = "movi_sms_filter"

private val SmsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

/**
 * Pantalla única del APK sensor: captura SMS bancarios en background y sube al server.
 * La UI real de Movi vive en la PWA — este Compose es deliberadamente mínimo y local a
 * :androidApp (no depende de componentes de UI de :shared).
 */
@Composable
fun SensorScreen() {
    val context = LocalContext.current

    var lastCaptureAt by remember { mutableStateOf(readLastCaptureAt(context)) }
    var senderCodes by remember { mutableStateOf(SmsFilterConfigStore.load(context).senderCodes) }

    LaunchedEffect(Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        SmsFilterConfigStore.refreshIfStale(context) {
            // Invocado desde el hilo background del fetch — saltamos a main antes de tocar
            // estado de Compose.
            mainHandler.post {
                lastCaptureAt = readLastCaptureAt(context)
                senderCodes = SmsFilterConfigStore.load(context).senderCodes
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentColor,
            onPrimary = Color(0xFF1A1A1A),
            background = BgColor,
            onBackground = TextColor,
            surface = CardColor,
            onSurface = TextColor,
            error = ErrorColor,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("Movi Sensor", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextColor)
            Text("Captura de SMS bancarios", fontSize = 14.sp, color = TextMutedColor)
            Spacer(Modifier.height(24.dp))

            SessionCard()
            Spacer(Modifier.height(16.dp))

            PermissionsCard(context)
            Spacer(Modifier.height(16.dp))

            SensorInfoCard(lastCaptureAt, senderCodes)
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apiBaseUrl)))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF1A1A1A)),
            ) {
                Text("Abrir Movi", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SensorCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMutedColor)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SessionCard() {
    SensorCard(title = "SESIÓN") {
        if (SessionManager.loggedIn) {
            Text(SessionManager.userEmail ?: "—", fontSize = 15.sp, color = TextColor)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { SessionManager.clear() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cerrar sesión")
            }
        } else {
            LoginForm()
        }
    }
}

@Composable
private fun LoginForm() {
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf(SessionManager.rememberedEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading) return
        loading = true
        error = null
        coroutineScope.launch {
            runCatching {
                Repositories.wallets.login(LoginRequest(email.trim(), password))
            }.onSuccess { resp ->
                SessionManager.save(resp.token, resp.userId, resp.name, resp.email)
                loading = false
            }.onFailure {
                error = "Correo o contraseña incorrectos"
                loading = false
            }
        }
    }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Correo") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Contraseña") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )

    error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, fontSize = 12.sp, color = ErrorColor)
    }

    Spacer(Modifier.height(14.dp))
    Button(
        onClick = { submit() },
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF1A1A1A)),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color(0xFF1A1A1A), strokeWidth = 2.dp)
        } else {
            Text("Entrar", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextColor,
    unfocusedTextColor = TextColor,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = TextMutedColor,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = TextMutedColor,
    cursorColor = AccentColor,
)

private fun hasSmsPermissions(context: Context): Boolean =
    SmsPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@Composable
private fun PermissionsCard(context: Context) {
    var granted by remember { mutableStateOf(hasSmsPermissions(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasSmsPermissions(context)
    }

    SensorCard(title = "PERMISOS") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SMS (RECEIVE_SMS / READ_SMS)", fontSize = 14.sp, color = TextColor)
            Text(
                if (granted) "Concedidos" else "Faltan",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) AccentColor else ErrorColor,
            )
        }
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { launcher.launch(SmsPermissions) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF1A1A1A)),
            ) {
                Text("Conceder permisos")
            }
        }
    }
}

private fun readLastCaptureAt(context: Context): Long =
    context.getSharedPreferences(SMS_PREFS, Context.MODE_PRIVATE)
        .getLong(SmsFilterConfigStore.KEY_LAST_CAPTURE_AT, 0L)

@Composable
private fun SensorInfoCard(lastCaptureAt: Long, senderCodes: List<String>) {
    SensorCard(title = "SENSOR") {
        Text("Última captura: ${formatCaptureDate(lastCaptureAt)}", fontSize = 14.sp, color = TextColor)
        Spacer(Modifier.height(6.dp))
        Text(
            "Remitentes vigentes: ${senderCodes.joinToString().ifBlank { "—" }}",
            fontSize = 14.sp,
            color = TextColor,
        )
    }
}

private fun formatCaptureDate(millis: Long): String =
    if (millis <= 0L) "ninguna aún"
    else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
