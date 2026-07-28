package com.jvillada.movi.sensor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

/** Recuerda que YA pedimos los permisos en la app: distingue "nunca preguntó" de "denegó". */
private const val KEY_PERM_REQUESTED = "perm_requested"

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
                // fillMaxSize, no fillMaxWidth: con contenido corto el fondo oscuro debe
                // llegar al borde inferior, si no se ve el blanco del tema de la Activity.
                .fillMaxSize()
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
                onClick = { openMoviWeb(context) },
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
    val context = LocalContext.current
    val loggedIn = SessionManager.loggedIn
    // Re-leído cuando cambia loggedIn: si el Worker corta la sesión con la pantalla
    // abierta, el aviso aparece sin reiniciar la Activity.
    var authErrorAt by remember(loggedIn) { mutableStateOf(SmsFilterConfigStore.authErrorAt(context)) }

    SensorCard(title = "SESIÓN") {
        if (loggedIn) {
            Text(SessionManager.userEmail ?: "—", fontSize = 15.sp, color = TextColor)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { SessionManager.clear() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cerrar sesión")
            }
        } else {
            if (SmsFilterConfigStore.isSessionExpired(authErrorAt, loggedIn)) {
                Text(
                    "Sesión vencida — volvé a entrar. Los SMS que lleguen hasta entonces se pierden: " +
                        "el sensor no los guarda para subirlos después.",
                    fontSize = 13.sp,
                    color = ErrorColor,
                )
                Spacer(Modifier.height(12.dp))
            }
            LoginForm(onLoggedIn = {
                SmsFilterConfigStore.clearAuthExpired(context)
                authErrorAt = 0L
            })
        }
    }
}

@Composable
private fun LoginForm(onLoggedIn: () -> Unit) {
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
                onLoggedIn()
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

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

private fun canShowRationale(activity: Activity?): Boolean =
    activity != null && SmsPermissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

/**
 * Decide entre pedir el permiso en la app y mandar a los ajustes del sistema.
 *
 * En Android 11+ tras una denegación (13+ tras dos), `requestPermissions` es un no-op
 * silencioso: el botón no haría nada y el sensor quedaría mudo sin salida desde la app.
 * Si ya preguntamos y el sistema ya no deja mostrar el diálogo, el único camino es
 * ACTION_APPLICATION_DETAILS_SETTINGS.
 */
internal fun shouldOpenSettings(askedBefore: Boolean, canShowRationale: Boolean): Boolean =
    askedBefore && !canShowRationale

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openMoviWeb(context: Context) {
    // Sin navegador que atienda el ACTION_VIEW esto tira ActivityNotFoundException y
    // crashea el sensor; que el botón no haga nada es preferible.
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apiBaseUrl))) }
}

@Composable
private fun PermissionsCard(context: Context) {
    val activity = remember(context) { context.findComponentActivity() }
    var granted by remember { mutableStateOf(hasSmsPermissions(context)) }
    var asked by remember { mutableStateOf(readPermissionAsked(context)) }
    var rationale by remember { mutableStateOf(canShowRationale(activity)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasSmsPermissions(context)
        rationale = canShowRationale(activity)
    }

    // Los permisos concedidos desde los ajustes del sistema no llegan por el launcher:
    // sin este re-chequeo la tarjeta seguiría diciendo "Faltan" hasta reiniciar la app.
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasSmsPermissions(context)
                rationale = canShowRationale(activity)
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
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
            val toSettings = activity == null || shouldOpenSettings(asked, rationale)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (toSettings) {
                        openAppSettings(context)
                    } else {
                        markPermissionAsked(context)
                        asked = true
                        launcher.launch(SmsPermissions)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF1A1A1A)),
            ) {
                Text(if (toSettings) "Abrir ajustes de la app" else "Conceder permisos")
            }
            if (toSettings) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android ya no muestra el diálogo: concedé SMS en Permisos, dentro de los ajustes de la app.",
                    fontSize = 12.sp,
                    color = TextMutedColor,
                )
            }
        }
    }
}

private fun readPermissionAsked(context: Context): Boolean =
    context.getSharedPreferences(SmsFilterConfigStore.PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_PERM_REQUESTED, false)

private fun markPermissionAsked(context: Context) {
    context.getSharedPreferences(SmsFilterConfigStore.PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_PERM_REQUESTED, true).apply()
}

private fun readLastCaptureAt(context: Context): Long = SmsFilterConfigStore.lastCaptureAt(context)

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
