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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.avisos.PARA_QUE_SON_LOS_AVISOS
import com.jvillada.movi.avisos.PreferenciasDeAvisos
import com.jvillada.movi.avisos.avisosPidenPermiso
import com.jvillada.movi.avisos.puedeAvisar
import com.jvillada.movi.avisos.tienePermisoDeAvisos
import com.jvillada.movi.notificaciones.AlmacenDeNotificaciones
import com.jvillada.movi.sensor.InstallSource
import com.jvillada.movi.sensor.OnResume
import com.jvillada.movi.sensor.SmsPermissionVerdict
import com.jvillada.movi.sensor.SmsPermissions
import com.jvillada.movi.sensor.abrirAjustesDeNotificaciones
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
import com.jvillada.movi.sensor.shouldHintRestrictedNotificationAccess
import com.jvillada.movi.sensor.shouldHintRestrictedSettings
import com.jvillada.movi.sensor.shouldOpenSettings
import com.jvillada.movi.sensor.shouldWarnAboutHibernation
import com.jvillada.movi.sensor.smsPermissionVerdict
import com.jvillada.movi.sensor.tieneAccesoANotificaciones
import com.jvillada.movi.sms.BackfillOutcome
import com.jvillada.movi.sms.SmsBackfill
import com.jvillada.movi.sms.SmsFilterConfigStore
import com.jvillada.movi.sms.backfillMessage
import com.jvillada.movi.sms.captureOutageNotice
import com.jvillada.movi.sms.isBackfillError
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
    // El segundo sensor, y hoy el que más falta hace: el banco dejó de mandar SMS (el más nuevo
    // del teléfono del dueño es del 15-sep) pero sigue publicando una notificación por movimiento.
    SensorNotificationsCard(installSource)
    // Ola 1 · Movi avisa: el permiso de notificaciones de Movi —el otro sentido: no leer las del
    // banco sino mostrar las propias— y sus dos interruptores. Acá porque es donde ya se piden los
    // permisos de este teléfono; Hoy lo ofrece una sola vez, y esta es la puerta permanente.
    AvisosDelTelefonoCard()
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
            Text("Permiso de SMS", fontSize = 14.sp, color = Movi.colores.texto)
            Text(
                if (granted) "Concedido" else "Falta",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) Movi.colores.entra else Movi.colores.sale,
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
                    color = Movi.colores.textoMedio,
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
                        color = Movi.colores.textoMedio,
                    )
                }
            }
        }
    }
}

/**
 * **Acceso a las notificaciones**: el permiso del segundo sensor.
 *
 * No es un permiso normal. No hay `requestPermissions` que lo pida, no hay diálogo que la app pueda
 * lanzar y no aparece en la ficha de permisos: se concede a mano en una pantalla del sistema que
 * lista todas las apps que lo piden. Por eso acá solo hay estado y un botón que lleva ahí.
 *
 * El texto dice el alcance completo —qué se lee y qué no— y no lo adorna. Conceder esto es dejar
 * que una app vea TODAS las notificaciones del teléfono; que Movi solo mire las de una lista corta
 * es una decisión de su código, no un límite que el sistema imponga, y quien lo concede merece
 * leerlo en esos términos.
 */
@Composable
private fun SensorNotificationsCard(installSource: InstallSource) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var concedido by remember { mutableStateOf(tieneAccesoANotificaciones(context)) }
    var ultima by remember { mutableStateOf(AlmacenDeNotificaciones.ultimaAt(context)) }

    // Se concede y se revoca FUERA de la app, como los permisos de SMS: sin releer al volver, la
    // tarjeta seguiría diciendo «Falta» sobre un acceso ya concedido.
    OnResume(activity) {
        concedido = tieneAccesoANotificaciones(context)
        ultima = AlmacenDeNotificaciones.ultimaAt(context)
    }

    Spacer(Modifier.height(10.dp))
    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Acceso a notificaciones", fontSize = 14.sp, color = Movi.colores.texto)
            Text(
                if (concedido) "Concedido" else "Falta",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (concedido) Movi.colores.entra else Movi.colores.sale,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Hay bancos que ya no mandan un SMS por cada movimiento, pero su app sí muestra una " +
                "notificación. Con este acceso, Movi lee esas notificaciones y las deja en " +
                "«Por revisar», en Movimientos, igual que un SMS: tú decides cuáles se anotan.",
            fontSize = 13.sp,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Movi solo lee las notificaciones de las apps de banco que tiene en su lista, y solo " +
                "esas salen de este teléfono. Las de cualquier otra app —tus chats, tu correo, " +
                "todo lo demás— se descartan sin leerlas y sin contarlas.",
            fontSize = 12.sp,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Movi no toca la notificación: no la abre, no la borra y no responde por ti.",
            fontSize = 12.sp,
            color = Movi.colores.textoMedio,
        )
        if (concedido || ultima > 0L) {
            Spacer(Modifier.height(10.dp))
            // Línea propia, separada de las dos del historial de SMS: si esta fecha es reciente y
            // la de la captura de SMS no, el banco dejó de mandar mensajes y las notificaciones
            // están tapando el hueco. Mezclarlas escondería justo eso.
            Text(
                "Última notificación capturada: ${formatCaptureDate(ultima, "ninguna aún")}",
                fontSize = 12.sp,
                color = Movi.colores.textoMedio,
            )
        }
        if (!concedido) {
            Spacer(Modifier.height(12.dp))
            SensorButton("Dar acceso a las notificaciones") { abrirAjustesDeNotificaciones(context) }
            Spacer(Modifier.height(8.dp))
            Text(
                "Se abre la lista del sistema: busca Movi y activa su interruptor. Android te " +
                    "pedirá confirmar. Al volver aquí, esta tarjeta dice «Concedido».",
                fontSize = 12.sp,
                color = Movi.colores.textoMedio,
            )
            // El umbral acá es Android 13, no 15: la escucha de notificaciones fue de lo PRIMERO
            // que entró en los ajustes restringidos, dos versiones antes que el permiso de SMS.
            // Ver shouldHintRestrictedNotificationAccess.
            if (shouldHintRestrictedNotificationAccess(Build.VERSION.SDK_INT, installSource)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Si el interruptor de Movi aparece gris y no te deja activarlo, es porque la " +
                        "app no se instaló desde una tienda: ve a los ajustes de la app, menú de " +
                        "tres puntos (arriba a la derecha) → «Permitir ajustes restringidos», y " +
                        "vuelve a intentarlo.",
                    fontSize = 12.sp,
                    color = Movi.colores.textoMedio,
                )
            }
        }
    }
}

/**
 * **Avisos de Movi en este teléfono** (Ola 1): el permiso de notificaciones (Android 13+) y los dos
 * interruptores —«Avisarme cuando llegue un movimiento» y «Avisarme antes de que venza un pago»—,
 * encendidos por defecto (regla del dueño: todo se configura desde la app).
 *
 * Sin el permiso los interruptores se ven pero no se mueven: prenderlos no haría sonar nada, y un
 * interruptor encendido que no hace nada es la misma mentira que «AUTO-LECTURA ACTIVA» con la
 * captura muda. Si Android ya no muestra el diálogo (se negó dos veces), el botón lleva a los
 * ajustes de notificaciones de la app.
 */
@Composable
private fun AvisosDelTelefonoCard() {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var puede by remember { mutableStateOf(puedeAvisar(context)) }
    var conPermiso by remember { mutableStateOf(tienePermisoDeAvisos(context)) }
    var aAjustes by remember { mutableStateOf(false) }
    var movimientos by remember { mutableStateOf(PreferenciasDeAvisos.avisarMovimientos(context)) }
    var vencimientos by remember { mutableStateOf(PreferenciasDeAvisos.avisarVencimientos(context)) }
    var bancoMudo by remember { mutableStateOf(PreferenciasDeAvisos.avisarBancoMudo(context)) }
    var debitos by remember { mutableStateOf(PreferenciasDeAvisos.avisarDebitos(context)) }

    fun refrescar() {
        puede = puedeAvisar(context)
        conPermiso = tienePermisoDeAvisos(context)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        refrescar()
        // Negado y sin poder volver a preguntar: el próximo toque lleva a los ajustes del sistema.
        if (!concedido) aAjustes = !canShowRationaleFor(activity, Manifest.permission.POST_NOTIFICATIONS)
    }
    // Se concede y se apaga FUERA de la app (ajustes del sistema): se relee al volver.
    OnResume(activity) { refrescar() }

    Spacer(Modifier.height(10.dp))
    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Avisos de Movi", fontSize = 14.sp, color = Movi.colores.texto)
            Text(
                if (puede) "Activos" else "Apagados",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (puede) Movi.colores.entra else Movi.colores.sale,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(PARA_QUE_SON_LOS_AVISOS, fontSize = 13.sp, color = Movi.colores.textoMedio)
        if (!puede) {
            Spacer(Modifier.height(12.dp))
            SensorButton(if (conPermiso || aAjustes) "Abrir ajustes de notificaciones" else "Permitir avisos") {
                if (!conPermiso && !aAjustes && avisosPidenPermiso()) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    abrirAjustesDeAvisos(context)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        FilaDeInterruptor(
            texto = "Avisarme cuando llegue un movimiento",
            encendido = movimientos,
            habilitado = puede,
        ) {
            movimientos = it
            PreferenciasDeAvisos.ponerAvisarMovimientos(context, it)
        }
        FilaDeInterruptor(
            texto = "Avisarme antes de que venza un pago",
            encendido = vencimientos,
            habilitado = puede,
        ) {
            vencimientos = it
            PreferenciasDeAvisos.ponerAvisarVencimientos(context, it)
        }
        // Ola 2 · banco mudo: cuántos días cuentan como silencio se elige arriba, en esta misma
        // pantalla (vale para la web y el teléfono); acá solo si este teléfono suena.
        FilaDeInterruptor(
            texto = "Avisarme si mi banco deja de avisar",
            encendido = bancoMudo,
            habilitado = puede,
        ) {
            bancoMudo = it
            PreferenciasDeAvisos.ponerAvisarBancoMudo(context, it)
        }
        // Lo que el banco cobra solo (cuotas y recurrentes marcados «se debita solo»): el banco no
        // manda nada ese día, así que el aviso es de Movi. Una vez por débito y período.
        FilaDeInterruptor(
            texto = "Avisarme el día de un débito automático",
            encendido = debitos,
            habilitado = puede,
        ) {
            debitos = it
            PreferenciasDeAvisos.ponerAvisarDebitos(context, it)
        }
    }
}

/** Una fila «texto … interruptor»: toda la fila alterna, como en Perfil. */
@Composable
private fun FilaDeInterruptor(texto: String, encendido: Boolean, habilitado: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .noRippleClickable { if (habilitado) onCambio(!encendido) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            texto,
            fontSize = 14.sp,
            color = if (habilitado) Movi.colores.texto else Movi.colores.textoApagado,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = encendido && habilitado,
            onCheckedChange = { onCambio(it) },
            enabled = habilitado,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Movi.colores.sobreMarca,
                checkedTrackColor = Movi.colores.marca,
                uncheckedThumbColor = Movi.colores.textoApagado,
                uncheckedTrackColor = Movi.colores.tarjeta,
                uncheckedBorderColor = Movi.colores.borde,
            ),
        )
    }
}

/** Los ajustes de notificaciones de Movi en el sistema (Android 8+), o la ficha de la app si no. */
private fun abrirAjustesDeAvisos(context: android.content.Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isFailure) openAppSettings(context)
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
            color = Movi.colores.sale,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Usar Movi en el navegador no cuenta: para Android es otra app.",
            fontSize = 12.sp,
            color = Movi.colores.textoMedio,
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
            color = Movi.colores.textoMedio,
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
            Text(captureOutageNotice(outageSince), fontSize = 12.5.sp, color = Movi.colores.aviso)
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "Sube los SMS bancarios de tu período actual y del anterior que sigan en el teléfono. " +
                "Sirve para recuperar lo que la captura automática no alcanzó a mandar.",
            fontSize = 13.sp,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(10.dp))
        Text("Última captura automática: ${formatCaptureDate(lastCaptureAt, "ninguna aún")}", fontSize = 12.sp, color = Movi.colores.textoMedio)
        // Línea separada a propósito: si esta fecha es reciente y la de arriba no, el
        // receiver en tiempo real está mudo aunque el historial esté al día — justo lo que
        // este indicador existe para no esconder.
        Text("Último historial sincronizado: ${formatCaptureDate(lastBackfillAt)}", fontSize = 12.sp, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(12.dp))
        SensorButton(
            label = if (toSettings) "Abrir ajustes de la app" else "Sincronizar el período",
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
            Text("Leyendo el inbox y subiendo…", fontSize = 13.sp, color = Movi.colores.textoMedio)
        }
        if (toSettings) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Falta el permiso de lectura de SMS y Android ya no muestra el diálogo: " +
                    "concédelo en Permisos, dentro de los ajustes de la app.",
                fontSize = 12.sp,
                color = Movi.colores.textoMedio,
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
                    color = Movi.colores.textoMedio,
                )
            }
        }
        outcome?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                backfillMessage(it),
                fontSize = 13.sp,
                color = if (isBackfillError(it)) Movi.colores.sale else Movi.colores.entra,
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
            .background(if (enabled && !loading) Movi.colores.marca else Movi.colores.tarjeta)
            .noRippleClickable { if (enabled && !loading) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Movi.colores.marca, strokeWidth = 2.dp)
        } else {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Movi.colores.fondo else Movi.colores.textoMedio,
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
