package com.jvillada.movi.sensor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.jvillada.movi.sms.BackfillOutcome
import com.jvillada.movi.sms.SmsBackfill
import com.jvillada.movi.sms.SmsFilterConfigStore
import com.jvillada.movi.sms.backfillMessage
import com.jvillada.movi.sms.isBackfillError
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

/**
 * Recuerda que una petición hecha por ESTA versión volvió con la firma del bloqueo.
 *
 * No duplica a [KEY_PERM_REQUESTED] — ese guarda que pedimos el permiso alguna vez, este
 * guarda CÓMO respondió el sistema la última vez que lo pedimos: sin diálogo, sin conceder
 * y sin habilitar el rationale. Es la única señal que autoriza a decir "esto lo bloqueó
 * Android", y es una afirmación positiva: mientras no exista, el copy es el genérico.
 *
 * No es un latch. Se reescribe en cada petición y se borra en cuanto se ve cualquier prueba
 * de que el diálogo funciona ([sawPermissionDialog]), así que ni una medición desafortunada
 * ni un permiso concedido más tarde dejan la pantalla mintiendo. Ver RestrictedSettings.kt.
 */
private const val KEY_PERM_FAST_REQUEST_SEEN = "perm_fast_request_seen"

private val SmsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

/**
 * Pantalla única del APK sensor: captura SMS bancarios en background y sube al server.
 * La UI real de Movi vive en la PWA — este Compose es deliberadamente mínimo y local a
 * :androidApp (no depende de componentes de UI de :shared).
 */
@Composable
fun SensorScreen() {
    val context = LocalContext.current

    // El origen de la instalación no cambia mientras la app vive y la consulta cruza un
    // binder al PackageManager: se resuelve una vez acá y baja a las dos tarjetas que lo
    // necesitan, en vez de repetirse en cada una al arrancar.
    val installSource = remember(context) { readInstallSource(context) }

    var lastCaptureAt by remember { mutableStateOf(readLastCaptureAt(context)) }
    var lastBackfillAt by remember { mutableStateOf(SmsFilterConfigStore.lastBackfillAt(context)) }
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

            PermissionsCard(context, installSource)
            Spacer(Modifier.height(16.dp))

            // Se dibuja a sí misma solo cuando el aviso aplica; el Spacer va adentro para
            // no dejar un hueco doble cuando la app ya está exenta.
            HibernationCard(context)

            SensorInfoCard(lastCaptureAt, lastBackfillAt, senderCodes)
            Spacer(Modifier.height(16.dp))

            BackfillCard(
                context,
                installSource,
                onSynced = { lastBackfillAt = SmsFilterConfigStore.lastBackfillAt(context) },
            )
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

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasSmsPermissions(context: Context): Boolean = SmsPermissions.all { hasPermission(context, it) }

/** El backfill lee el inbox: necesita READ_SMS, que el camino en tiempo real no usa. */
private fun hasReadSmsPermission(context: Context): Boolean =
    hasPermission(context, Manifest.permission.READ_SMS)

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

private fun canShowRationaleFor(activity: Activity?, permission: String): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

private fun canShowRationale(activity: Activity?): Boolean =
    SmsPermissions.any { canShowRationaleFor(activity, it) }

/**
 * Re-chequeo al volver a la pantalla. Los permisos concedidos desde los ajustes del
 * sistema no llegan por el launcher: sin esto las tarjetas seguirían diciendo "Faltan"
 * hasta reiniciar la app.
 */
@Composable
private fun OnResume(activity: ComponentActivity?, onResume: () -> Unit) {
    val current by rememberUpdatedState(onResume)
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
}

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
private fun PermissionsCard(context: Context, installSource: InstallSource) {
    val activity = remember(context) { context.findComponentActivity() }
    var granted by remember { mutableStateOf(hasSmsPermissions(context)) }
    var asked by remember { mutableStateOf(readPermissionAsked(context)) }
    var rationale by remember { mutableStateOf(canShowRationale(activity)) }
    var blockLike by remember { mutableStateOf(readBlockLikeRequest(context)) }

    // elapsedRealtime del último launch; 0 = no hay petición en vuelo que medir. Reloj
    // monotónico a propósito: el de pared puede saltar mientras el diálogo está abierto.
    // Si una recreación de la Activity se lo lleva, perderlo cae del lado seguro (ver
    // requestDurationSaysDialogShown), así que no hace falta sobrevivirla.
    var requestStartedAt by remember { mutableLongStateOf(0L) }

    fun refresh() {
        granted = hasSmsPermissions(context)
        asked = readPermissionAsked(context)
        rationale = canShowRationale(activity)
        // Una relectura NO puede afirmar el bloqueo — no hubo petición que medir — pero sí
        // desmentirlo: tener el permiso o poder mostrar el rationale prueban que esta
        // instalación no está restringida. Sin esto, una marca vieja seguiría sosteniendo la
        // acusación después de que el usuario concediera el permiso desde los ajustes y la
        // hibernación se lo revocara meses más tarde.
        if (sawPermissionDialog(rationale, granted)) clearBlockLikeRequest(context)
        blockLike = readBlockLikeRequest(context)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Cancelar el diálogo con Atrás o tocando afuera devuelve denegado sin tocar el
        // rationale — igual que el bloqueo. Lo único que los separa es cuánto tardó.
        val slow = requestDurationSaysDialogShown(requestStartedAt, SystemClock.elapsedRealtime())
        requestStartedAt = 0L
        // El resultado del launcher no alcanza: hace falta el rationale de después para
        // saber si el sistema llegó a preguntar. Se escribe siempre, en los dos sentidos.
        writeBlockLikeRequest(
            context,
            requestLooksBlocked(
                canShowRationale = canShowRationale(activity),
                granted = hasSmsPermissions(context),
                requestWasSlow = slow,
            ),
        )
        refresh()
    }

    // Cubre también el primer ON_RESUME: el observador se registra con el ciclo de vida ya
    // en RESUMED y LifecycleRegistry le reenvía el evento, así que el latch queda anotado
    // aunque la sesión no pase nunca por background. Verificado en API 35 con el permiso
    // concedido de entrada — el caso que importa, porque es el que la hibernación revoca
    // después sin pasar jamás por rationale true.
    OnResume(activity) { refresh() }

    fun requestPermissions() {
        markPermissionAsked(context)
        asked = true
        requestStartedAt = SystemClock.elapsedRealtime()
        launcher.launch(SmsPermissions)
    }

    val verdict = if (activity == null && !granted) {
        // Sin Activity no podemos consultar el rationale, así que no tenemos con qué
        // sostener ninguna afirmación: caemos al estado genérico, que además es el único
        // con salida (los ajustes del sistema).
        SmsPermissionVerdict.DENIED
    } else {
        smsPermissionVerdict(
            sdkInt = Build.VERSION.SDK_INT,
            installSource = installSource,
            askedBefore = asked,
            blockLikeRequestSeen = blockLike,
            granted = granted,
            canShowRationale = rationale,
        )
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
        when (verdict) {
            SmsPermissionVerdict.GRANTED -> Unit

            SmsPermissionVerdict.ASK_IN_APP -> {
                Spacer(Modifier.height(12.dp))
                PermissionButton("Conceder permisos") { requestPermissions() }
            }

            SmsPermissionVerdict.DENIED -> {
                Spacer(Modifier.height(12.dp))
                PermissionButton("Abrir ajustes de la app") { openAppSettings(context) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android ya no muestra el diálogo: concedé SMS en Permisos, dentro de los ajustes de la app.",
                    fontSize = 12.sp,
                    color = TextMutedColor,
                )
                if (shouldHintRestrictedSettings(Build.VERSION.SDK_INT, installSource)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        // Condicional a propósito: acá no sabemos si el bloqueo existe (ver
                        // shouldHintRestrictedSettings). Lo que sí sabemos es que en esta
                        // instalación es posible, y que el usuario que se lo encuentre no
                        // tiene forma de adivinar el menú donde se desactiva.
                        "Si el interruptor de SMS aparece gris y no te deja activarlo, es porque la app " +
                            "no se instaló desde una tienda: en esa misma ficha, menú de tres puntos " +
                            "(arriba a la derecha) → «Permitir ajustes restringidos». Después el " +
                            "interruptor se deja activar.",
                        fontSize = 12.sp,
                        color = TextMutedColor,
                    )
                }
            }

            SmsPermissionVerdict.BLOCKED_BY_RESTRICTED_SETTINGS -> RestrictedSettingsHelp(
                onOpenSettings = { openAppSettings(context) },
                onRetry = { requestPermissions() },
            )
        }
    }
}

@Composable
private fun PermissionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF1A1A1A)),
    ) {
        Text(label)
    }
}

/**
 * Qué se le dice al usuario cuando Android bloqueó el permiso por ajustes restringidos.
 *
 * Dos cosas que este texto NO puede hacer, porque el sistema no lo permite: activar el
 * desbloqueo por su cuenta y llevar directo al menú de tres puntos (no hay intent para
 * esa opción). Lo único honesto es nombrar el camino exacto. El botón cae en la ficha de
 * la app, que es la pantalla donde vive ese menú: de ahí, un toque.
 */
@Composable
private fun RestrictedSettingsHelp(onOpenSettings: () -> Unit, onRetry: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(
        "Android bloqueó el permiso sin preguntarte. No fue algo que hiciste: como esta app " +
            "no se instaló desde una tienda, el sistema no deja concederle SMS hasta que lo " +
            "habilites a mano.",
        fontSize = 13.sp,
        color = ErrorColor,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Ajustes → Aplicaciones → Movi → menú de tres puntos (arriba a la derecha) → " +
            "«Permitir ajustes restringidos». La app no puede activarlo por vos: Android no " +
            "expone ninguna forma de hacerlo desde acá.",
        fontSize = 12.sp,
        color = TextMutedColor,
    )
    Spacer(Modifier.height(12.dp))
    PermissionButton("Abrir la ficha de la app", onOpenSettings)
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("Ya lo permití — pedir de nuevo")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Con los ajustes restringidos habilitados, el diálogo de SMS vuelve a salir.",
        fontSize = 12.sp,
        color = TextMutedColor,
    )
}

/**
 * Lleva a la pantalla donde se apaga la pausa por inactividad.
 *
 * Verificado en API 35 (AOSP): ACTION_AUTO_REVOKE_PERMISSIONS resuelve a
 * com.android.settings InstalledAppDetails, o sea la ficha "Información de la app" con el
 * interruptor de pausa. Si no resolviera (ROM sin esa Activity, filtrado por visibilidad
 * de paquetes), caemos a los ajustes de la app, que es el mismo destino por otro camino.
 */
private fun openHibernationSettings(context: Context) {
    val intent = Intent(
        Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Sin precheck de resolveActivity a propósito: con targetSdk 35 y sin <queries>, el
    // filtrado de visibilidad de paquetes puede devolver null aunque la Activity exista,
    // y nos sacaría del intent específico sin motivo. startActivity NO sufre ese filtrado
    // (resuelve en system_server), y runCatching ya cubre el único fallo real.
    runCatching { context.startActivity(intent) }
        .onFailure { openAppSettings(context) }
}

/**
 * Aviso de hibernación / auto-revoke.
 *
 * La premisa de esta pantalla es "instalar y olvidar", y eso es exactamente lo que
 * Android castiga: a los pocos meses sin abrir la app le revoca los permisos y la
 * force-stopea, con lo que el receiver deja de recibir SMS_RECEIVED y el sensor muere en
 * silencio. Abrir la PWA no cuenta — es otra app.
 *
 * Nada que ver con la optimización de batería / Doze: eso es otro problema, con otras
 * APIs, y WorkManager ya lo sobrevive.
 */
@Composable
private fun HibernationCard(context: Context) {
    val activity = remember(context) { context.findComponentActivity() }
    var exempt by remember { mutableStateOf(isAutoRevokeExempt(context)) }

    // El usuario cambia esto FUERA de la app: sin releer al volver, el aviso seguiría
    // en pantalla después de haberlo resuelto.
    OnResume(activity) { exempt = isAutoRevokeExempt(context) }

    if (!shouldWarnAboutHibernation(Build.VERSION.SDK_INT, exempt)) return

    SensorCard(title = "HIBERNACIÓN") {
        Text(
            // Sin nombrar la hibernación: desde Android 11 se revocan los permisos, y desde
            // Android 13 además se detiene la app. La consecuencia es la misma en ambos y es
            // lo único que le importa a quien lee esto.
            "Si no abrís esta app durante unos meses, Android le revoca los permisos: " +
                "el sensor deja de capturar SMS y no avisa.",
            fontSize = 13.sp,
            color = ErrorColor,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Usar Movi en el navegador no cuenta: para Android es otra app.",
            fontSize = 12.sp,
            color = TextMutedColor,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { openHibernationSettings(context) },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF1A1A1A)),
        ) {
            Text("Evitar que Android la pause", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            // El texto exacto del interruptor cambia por versión de Android ("Administrar la
            // app si no se usa" en 13+, "Pausar actividad de la app si no se usa" antes), así
            // que nombramos la sección, que sí es estable.
            "Se abre la ficha de la app: hasta abajo, en «Apps sin usar», apagá el " +
                "interruptor. Al volver acá, este aviso desaparece.",
            fontSize = 12.sp,
            color = TextMutedColor,
        )
    }
    Spacer(Modifier.height(16.dp))
}

/**
 * Recuperación manual del historial. El sensor puede quedar mudo sin avisar (token
 * vencido, force-stop, hibernación, permisos revocados) y hasta ahora esa ventana era
 * pérdida de datos permanente aunque los SMS siguieran en el inbox. Este es el único
 * camino que los recupera — con el MISMO filtro bancario que el receiver, así que sube
 * menos que el backfill viejo, no más.
 */
@Composable
private fun BackfillCard(context: Context, installSource: InstallSource, onSynced: () -> Unit) {
    val activity = remember(context) { context.findComponentActivity() }
    val scope = rememberCoroutineScope()
    val loggedIn = SessionManager.loggedIn
    var canRead by remember { mutableStateOf(hasReadSmsPermission(context)) }
    var asked by remember { mutableStateOf(readPermissionAsked(context)) }
    var rationale by remember { mutableStateOf(canShowRationaleFor(activity, Manifest.permission.READ_SMS)) }
    var blockLike by remember { mutableStateOf(readBlockLikeRequest(context)) }
    var requestStartedAt by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<BackfillOutcome?>(null) }

    // OJO: NO remember(loggedIn) para outcome. SmsBackfill.run puede ser la MISMA llamada
    // que produce SessionExpired Y desloguea (un 401 dispara SessionManager.clear()), así
    // que la key y el resultado cambiarían en el mismo instante: el resultado se escribiría
    // en un slot que la recomposición ya descartó y el aviso jamás se vería. En cambio,
    // reaccionamos solo a la TRANSICIÓN a logueado (login exitoso) — ahí sí un outcome
    // SessionExpired que haya quedado en pantalla ya no describe la realidad.
    LaunchedEffect(loggedIn) {
        if (loggedIn) outcome = null
    }

    OnResume(activity) {
        val wasBlocked = !canRead
        canRead = hasReadSmsPermission(context)
        asked = readPermissionAsked(context)
        rationale = canShowRationaleFor(activity, Manifest.permission.READ_SMS)
        // Misma señal que en PERMISOS y por el mismo motivo: acá tampoco hubo petición que
        // medir, así que solo se puede desmentir el bloqueo, nunca afirmarlo. Tener READ_SMS
        // alcanza: el bloqueo es del grupo entero, no de un permiso suelto.
        if (sawPermissionDialog(rationale, canRead)) clearBlockLikeRequest(context)
        blockLike = readBlockLikeRequest(context)
        // Igual que arriba: si el permiso se concedió desde ajustes del sistema, el
        // NoPermission que quedó en pantalla ya no describe la realidad.
        if (wasBlocked && canRead) outcome = null
    }

    fun start() {
        if (running) return
        running = true
        outcome = null
        scope.launch {
            val result = SmsBackfill.run(context)
            outcome = result
            running = false
            // El permiso pudo revocarse entre el chequeo de la UI y la lectura (auto-revoke
            // por hibernación). Sin esto la tarjeta dice "falta el permiso" pero el botón
            // sigue ofreciendo reintentar en vez de llevar a Ajustes.
            if (result is BackfillOutcome.NoPermission) canRead = false
            if (result is BackfillOutcome.Uploaded) onSynced()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val slow = requestDurationSaysDialogShown(requestStartedAt, SystemClock.elapsedRealtime())
        requestStartedAt = 0L
        canRead = granted
        rationale = canShowRationaleFor(activity, Manifest.permission.READ_SMS)
        writeBlockLikeRequest(context, requestLooksBlocked(rationale, granted, slow))
        blockLike = readBlockLikeRequest(context)
        if (granted) start() else outcome = BackfillOutcome.NoPermission
    }

    val toSettings = !canRead && (activity == null || shouldOpenSettings(asked, rationale))

    // Sin esto la tarjeta diría "concedelo en Permisos" justo debajo de un aviso que dice
    // que Android no deja concederlo — y ese interruptor está gris. Se calcula con las
    // mismas señales que PERMISOS: el bloqueo es del grupo SMS, no de un permiso suelto.
    val blocked = activity != null && smsPermissionVerdict(
        sdkInt = Build.VERSION.SDK_INT,
        installSource = installSource,
        askedBefore = asked,
        blockLikeRequestSeen = blockLike,
        granted = canRead,
        canShowRationale = rationale,
    ) == SmsPermissionVerdict.BLOCKED_BY_RESTRICTED_SETTINGS

    SensorCard(title = "HISTORIAL") {
        Text(
            "Sube los SMS bancarios de los últimos 30 días que sigan en el teléfono. " +
                "Sirve para recuperar lo que el sensor no alcanzó a mandar.",
            fontSize = 13.sp,
            color = TextMutedColor,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                when {
                    canRead -> start()
                    toSettings -> openAppSettings(context)
                    else -> {
                        markPermissionAsked(context)
                        asked = true
                        requestStartedAt = SystemClock.elapsedRealtime()
                        launcher.launch(Manifest.permission.READ_SMS)
                    }
                }
            },
            enabled = loggedIn && !running,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF1A1A1A)),
        ) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color(0xFF1A1A1A), strokeWidth = 2.dp)
            } else {
                Text(
                    if (toSettings) "Abrir ajustes de la app" else "Sincronizar últimos 30 días",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (running) {
            Spacer(Modifier.height(10.dp))
            Text("Leyendo el inbox y subiendo…", fontSize = 13.sp, color = TextMutedColor)
        }
        if (!loggedIn) {
            Spacer(Modifier.height(8.dp))
            Text("Entrá arriba para poder subir el historial.", fontSize = 12.sp, color = TextMutedColor)
        }
        if (toSettings) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (blocked) {
                    "Falta el permiso de lectura de SMS y el interruptor está bloqueado: " +
                        "seguí primero los pasos del aviso de PERMISOS, acá arriba."
                } else {
                    "Falta el permiso de lectura de SMS y Android ya no muestra el diálogo: " +
                        "concedelo en Permisos, dentro de los ajustes de la app."
                },
                fontSize = 12.sp,
                color = TextMutedColor,
            )
        }
        outcome?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                backfillMessage(it),
                fontSize = 13.sp,
                color = if (isBackfillError(it)) ErrorColor else AccentColor,
            )
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

private fun readBlockLikeRequest(context: Context): Boolean =
    context.getSharedPreferences(SmsFilterConfigStore.PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_PERM_FAST_REQUEST_SEEN, false)

/**
 * Guarda cómo se comportó la última petición del permiso. Ver [KEY_PERM_FAST_REQUEST_SEEN].
 *
 * Escribe los dos valores a propósito: una petición que sí mostró el diálogo tiene que
 * BORRAR la marca de la anterior. No sobrevive a una desinstalación ni a un borrado de
 * datos, que es justo lo que se quiere — sin esta instalación no hay observación.
 */
private fun writeBlockLikeRequest(context: Context, blockLike: Boolean) {
    if (blockLike == readBlockLikeRequest(context)) return
    context.getSharedPreferences(SmsFilterConfigStore.PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_PERM_FAST_REQUEST_SEEN, blockLike).apply()
}

/** Desmiente el bloqueo sin haber pedido nada. Solo lo llaman las relecturas. */
private fun clearBlockLikeRequest(context: Context) = writeBlockLikeRequest(context, false)

private fun readLastCaptureAt(context: Context): Long = SmsFilterConfigStore.lastCaptureAt(context)

@Composable
private fun SensorInfoCard(lastCaptureAt: Long, lastBackfillAt: Long, senderCodes: List<String>) {
    SensorCard(title = "SENSOR") {
        Text("Última captura: ${formatCaptureDate(lastCaptureAt, "ninguna aún")}", fontSize = 14.sp, color = TextColor)
        Spacer(Modifier.height(6.dp))
        // Línea separada a propósito: si esta fecha es reciente y la de arriba no, el
        // receiver en tiempo real está mudo aunque el historial esté al día — justo lo que
        // este indicador existe para no esconder.
        Text(
            "Último historial sincronizado: ${formatCaptureDate(lastBackfillAt)}",
            fontSize = 14.sp,
            color = TextColor,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Remitentes vigentes: ${senderCodes.joinToString().ifBlank { "—" }}",
            fontSize = 14.sp,
            color = TextColor,
        )
    }
}

/** [vacio] lo pone quien llama: las dos líneas que usan esto tienen género distinto. */
private fun formatCaptureDate(millis: Long, vacio: String = "nunca"): String =
    if (millis <= 0L) vacio
    else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
