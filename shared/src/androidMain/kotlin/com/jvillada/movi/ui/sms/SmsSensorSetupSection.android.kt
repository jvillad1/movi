package com.jvillada.movi.ui.sms

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.sensor.InstallSource
import com.jvillada.movi.sensor.OnResume
import com.jvillada.movi.sensor.SmsPermissionVerdict
import com.jvillada.movi.sensor.SmsPermissions
import com.jvillada.movi.sensor.canShowRationale
import com.jvillada.movi.sensor.canShowRationaleFor
import com.jvillada.movi.sensor.findComponentActivity
import com.jvillada.movi.sensor.hasReadSmsPermission
import com.jvillada.movi.sensor.hasSmsPermissions
import com.jvillada.movi.sensor.isAutoRevokeExempt
import com.jvillada.movi.sensor.markPermissionAsked
import com.jvillada.movi.sensor.openAppSettings
import com.jvillada.movi.sensor.openHibernationSettings
import com.jvillada.movi.sensor.readInstallSource
import com.jvillada.movi.sensor.readPermissionAsked
import com.jvillada.movi.sensor.shouldHintRestrictedSettings
import com.jvillada.movi.sensor.shouldOpenSettings
import com.jvillada.movi.sensor.shouldWarnAboutHibernation
import com.jvillada.movi.sensor.smsPermissionVerdict
import com.jvillada.movi.sms.BackfillOutcome
import com.jvillada.movi.sms.SmsBackfill
import com.jvillada.movi.sms.SmsFilterConfigStore
import com.jvillada.movi.sms.backfillMessage
import com.jvillada.movi.sms.captureOutageNotice
import com.jvillada.movi.sms.isBackfillError
import com.jvillada.movi.theme.MinBg
import com.jvillada.movi.theme.MinExpense
import com.jvillada.movi.theme.MinIncome
import com.jvillada.movi.theme.MinPrimary
import com.jvillada.movi.theme.MinSurfaceContainerHigh
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextMute
import com.jvillada.movi.theme.MinWarn
import com.jvillada.movi.ui.auth.noRippleClickable
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Actual Android: la única plataforma donde la captura de SMS existe. Las piezas vienen
 * de la SensorScreen del APK sensor (su login se eliminó: la sesión ahora es la de la
 * app, la misma que ya leían los Workers de sync).
 */
@Composable
actual fun SmsSensorSetupSection(onSynced: () -> Unit) {
    val context = LocalContext.current

    // El origen de la instalación no cambia mientras la app vive y la consulta cruza un
    // binder al PackageManager: se resuelve una vez acá y baja a las dos tarjetas que lo
    // necesitan.
    val installSource = remember(context) { readInstallSource(context) }

    // La marca de 401 (KEY_AUTH_ERROR_AT) se lee ANTES de limpiarla: es el "desde cuándo"
    // del aviso de pausa que muestra la tarjeta de historial. Esta sección solo se pinta
    // con sesión activa (la app sin sesión vive en LoginScreen), así que llegar acá
    // logueado ya es la prueba de que se volvió a entrar: la marca se limpia para la
    // próxima, pero el aviso queda en pantalla lo que dure esta visita. rememberSaveable,
    // no remember: la sección vive en un item{} de la LazyColumn y con bandeja larga el
    // scroll la descarta — un remember pelado se re-ejecutaría contra la pref ya limpiada
    // y el aviso desaparecería en mitad de la misma visita.
    val outageSince = rememberSaveable { SmsFilterConfigStore.authErrorAt(context) }
    LaunchedEffect(Unit) {
        if (SessionManager.loggedIn) SmsFilterConfigStore.clearAuthExpired(context)
    }

    Spacer(Modifier.height(18.dp))
    MinSectionHeader(title = "Captura en este teléfono")
    SensorPermissionsCard(installSource)
    // Se dibuja a sí misma solo cuando el aviso aplica; el Spacer va adentro para no
    // dejar un hueco doble cuando la app ya está exenta.
    SensorHibernationCard()
    Spacer(Modifier.height(10.dp))
    SensorBackfillCard(installSource, outageSince, onSynced)
}

@Composable
private fun SensorPermissionsCard(installSource: InstallSource) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var granted by remember { mutableStateOf(hasSmsPermissions(context)) }
    var asked by remember { mutableStateOf(readPermissionAsked(context)) }
    var rationale by remember { mutableStateOf(canShowRationale(activity)) }

    fun refresh() {
        granted = hasSmsPermissions(context)
        asked = readPermissionAsked(context)
        rationale = canShowRationale(activity)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // El resultado del launcher no alcanza: hace falta releer el rationale de después
        // para saber si todavía queda camino dentro de la app.
        refresh()
    }

    // Los permisos concedidos o revocados fuera de la app (ajustes del sistema,
    // auto-revoke por hibernación) no llegan por el launcher.
    OnResume(activity) { refresh() }

    fun requestPermissions() {
        markPermissionAsked(context)
        asked = true
        launcher.launch(SmsPermissions)
    }

    val verdict = if (activity == null && !granted) {
        // Sin Activity no podemos consultar el rationale ni lanzar el diálogo: caemos al
        // estado genérico, que además es el único con salida (los ajustes del sistema).
        SmsPermissionVerdict.DENIED
    } else {
        smsPermissionVerdict(
            askedBefore = asked,
            granted = granted,
            canShowRationale = rationale,
        )
    }

    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Permiso de SMS", fontSize = 14.sp, color = MinText)
            Text(
                if (granted) "Concedido" else "Falta",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) MinIncome else MinExpense,
            )
        }
        when (verdict) {
            SmsPermissionVerdict.GRANTED -> Unit

            SmsPermissionVerdict.ASK_IN_APP -> {
                Spacer(Modifier.height(12.dp))
                SensorButton("Conceder permisos") { requestPermissions() }
            }

            SmsPermissionVerdict.DENIED -> {
                Spacer(Modifier.height(12.dp))
                SensorButton("Abrir ajustes de la app") { openAppSettings(context) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android ya no muestra el diálogo: concede SMS en Permisos, dentro de los ajustes de la app.",
                    fontSize = 12.sp,
                    color = MinTextMute,
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
                        color = MinTextMute,
                    )
                }
            }
        }
    }
}

/**
 * Aviso de hibernación / auto-revoke.
 *
 * Un teléfono cuyo dueño usa Movi sobre todo por la web puede pasar meses sin abrir esta
 * app, y eso es exactamente lo que Android castiga: le revoca los permisos y la
 * force-stopea, con lo que el receiver deja de recibir SMS_RECEIVED y la captura muere en
 * silencio. Abrir la PWA no cuenta — es otra app.
 *
 * Nada que ver con la optimización de batería / Doze: eso es otro problema, con otras
 * APIs, y WorkManager ya lo sobrevive.
 */
@Composable
private fun SensorHibernationCard() {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var exempt by remember { mutableStateOf(isAutoRevokeExempt(context)) }

    // El usuario cambia esto FUERA de la app: sin releer al volver, el aviso seguiría
    // en pantalla después de haberlo resuelto.
    OnResume(activity) { exempt = isAutoRevokeExempt(context) }

    if (!shouldWarnAboutHibernation(Build.VERSION.SDK_INT, exempt)) return

    Spacer(Modifier.height(10.dp))
    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated) {
        Text(
            // Sin nombrar la hibernación: desde Android 11 se revocan los permisos, y desde
            // Android 13 además se detiene la app. La consecuencia es la misma en ambos y es
            // lo único que le importa a quien lee esto.
            "Si no abres esta app durante unos meses, Android le revoca los permisos: " +
                "la captura de SMS se detiene y no avisa.",
            fontSize = 13.sp,
            color = MinExpense,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Usar Movi en el navegador no cuenta: para Android es otra app.",
            fontSize = 12.sp,
            color = MinTextMute,
        )
        Spacer(Modifier.height(12.dp))
        SensorButton("Evitar que Android la pause") { openHibernationSettings(context) }
        Spacer(Modifier.height(8.dp))
        Text(
            // El texto exacto del interruptor cambia por versión de Android ("Administrar la
            // app si no se usa" en 13+, "Pausar actividad de la app si no se usa" antes), así
            // que nombramos la sección, que sí es estable.
            "Se abre la ficha de la app: hasta abajo, en «Apps sin usar», apaga el " +
                "interruptor. Al volver aquí, este aviso desaparece.",
            fontSize = 12.sp,
            color = MinTextMute,
        )
    }
}

/**
 * Recuperación manual del historial. La captura puede quedar muda sin avisar (token
 * vencido, force-stop, hibernación, permisos revocados) y esa ventana sería pérdida de
 * datos permanente aunque los SMS sigan en el inbox. Este es el único camino que los
 * recupera — con el MISMO filtro bancario que el receiver: lo que no matchea nunca sale
 * del teléfono.
 */
@Composable
private fun SensorBackfillCard(installSource: InstallSource, outageSince: Long, onSynced: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val scope = rememberCoroutineScope()
    val loggedIn = SessionManager.loggedIn
    var canRead by remember { mutableStateOf(hasReadSmsPermission(context)) }
    var asked by remember { mutableStateOf(readPermissionAsked(context)) }
    var rationale by remember { mutableStateOf(canShowRationaleFor(activity, Manifest.permission.READ_SMS)) }
    var running by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<BackfillOutcome?>(null) }
    var lastCaptureAt by remember { mutableStateOf(SmsFilterConfigStore.lastCaptureAt(context)) }
    var lastBackfillAt by remember { mutableStateOf(SmsFilterConfigStore.lastBackfillAt(context)) }

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
        lastCaptureAt = SmsFilterConfigStore.lastCaptureAt(context)
        // Igual que en la tarjeta de permisos: si el permiso se concedió desde ajustes del
        // sistema, el NoPermission que quedó en pantalla ya no describe la realidad.
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
            if (result is BackfillOutcome.Uploaded) {
                lastBackfillAt = SmsFilterConfigStore.lastBackfillAt(context)
                onSynced()
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canRead = granted
        rationale = canShowRationaleFor(activity, Manifest.permission.READ_SMS)
        if (granted) start() else outcome = BackfillOutcome.NoPermission
    }

    val toSettings = !canRead && (activity == null || shouldOpenSettings(asked, rationale))

    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated) {
        if (outageSince > 0L) {
            // m2: quien fue deslogueado por el Worker aterrizó en un login genérico sin
            // saber que la captura estuvo muda. Este es el único lector de la marca, y el
            // remedio (el historial) está justo abajo.
            Text(captureOutageNotice(outageSince), fontSize = 12.5.sp, color = MinWarn)
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "Sube los SMS bancarios de los últimos 30 días que sigan en el teléfono. " +
                "Sirve para recuperar lo que la captura automática no alcanzó a mandar.",
            fontSize = 13.sp,
            color = MinTextMute,
        )
        Spacer(Modifier.height(10.dp))
        Text("Última captura automática: ${formatCaptureDate(lastCaptureAt, "ninguna aún")}", fontSize = 12.sp, color = MinTextMute)
        // Línea separada a propósito: si esta fecha es reciente y la de arriba no, el
        // receiver en tiempo real está mudo aunque el historial esté al día — justo lo que
        // este indicador existe para no esconder.
        Text("Último historial sincronizado: ${formatCaptureDate(lastBackfillAt)}", fontSize = 12.sp, color = MinTextMute)
        Spacer(Modifier.height(12.dp))
        SensorButton(
            label = if (toSettings) "Abrir ajustes de la app" else "Sincronizar últimos 30 días",
            enabled = loggedIn && !running,
            loading = running,
        ) {
            when {
                canRead -> start()
                toSettings -> openAppSettings(context)
                else -> {
                    markPermissionAsked(context)
                    asked = true
                    launcher.launch(Manifest.permission.READ_SMS)
                }
            }
        }
        if (running) {
            Spacer(Modifier.height(10.dp))
            Text("Leyendo el inbox y subiendo…", fontSize = 13.sp, color = MinTextMute)
        }
        if (toSettings) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Falta el permiso de lectura de SMS y Android ya no muestra el diálogo: " +
                    "concédelo en Permisos, dentro de los ajustes de la app.",
                fontSize = 12.sp,
                color = MinTextMute,
            )
            // Sin esto la línea de arriba mandaría a tocar un interruptor que puede estar
            // gris, sin decir cómo destrabarlo. Misma condición y mismo tono condicional que
            // el aviso de la tarjeta de permisos: acá tampoco se afirma que el bloqueo exista.
            if (shouldHintRestrictedSettings(Build.VERSION.SDK_INT, installSource)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Si ese interruptor aparece gris, primero hay que permitir los ajustes " +
                        "restringidos desde el menú de tres puntos de esa misma ficha.",
                    fontSize = 12.sp,
                    color = MinTextMute,
                )
            }
        }
        outcome?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                backfillMessage(it),
                fontSize = 13.sp,
                color = if (isBackfillError(it)) MinExpense else MinIncome,
            )
        }
    }
}

/** Botón pill del estilo de la app (ver el submit de LoginScreen) — no Material Button. */
@Composable
private fun SensorButton(
    label: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled && !loading) MinPrimary else MinSurfaceContainerHigh)
            .noRippleClickable { if (enabled && !loading) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MinPrimary, strokeWidth = 2.dp)
        } else {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MinBg else MinTextMute,
            )
        }
    }
}

/** [vacio] lo pone quien llama: las dos líneas que usan esto tienen género distinto. */
private fun formatCaptureDate(millis: Long, vacio: String = "nunca"): String =
    if (millis <= 0L) vacio
    else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

/**
 * Actual Android: el permiso de SMS es la única condición que puede faltar acá — la
 * pantalla que consume esto ya exige sesión, y una revocación por hibernación también se
 * manifiesta como permiso ausente. Se relee al volver a la pantalla por la misma razón que
 * las tarjetas de la sección: lo concedido en ajustes del sistema no avisa.
 */
@Composable
actual fun rememberSmsCaptureReady(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var granted by remember { mutableStateOf(hasSmsPermissions(context)) }
    OnResume(activity) { granted = hasSmsPermissions(context) }
    return granted
}
