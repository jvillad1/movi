package com.jvillada.movi.ui.sms

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.data.SessionManager
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
import com.jvillada.movi.ui.components.MinSectionHeader
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.jvillada.movi.ui.components.ds.Superficie

/**
 * Actual Android: la única plataforma donde la captura de SMS existe. Las piezas vienen
 * de la SensorScreen del APK sensor (su login se eliminó: la sesión ahora es la de la
 * app, la misma que ya leían los Workers de sync).
 */
@Composable
actual fun SmsSensorSetupSection(onSynced: () -> Unit, conAccionPrincipal: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    // Vuelta 88 · un amarillo por vista: lo primero que falta lleva el botón principal (ver el KDoc del
    // expect). Se relee al volver de los ajustes del sistema, como cada tarjeta.
    var smsConcedido by remember { mutableStateOf(hasSmsPermissions(context)) }
    var notificacionesConcedidas by remember { mutableStateOf(tieneAccesoANotificaciones(context)) }
    OnResume(activity) {
        smsConcedido = hasSmsPermissions(context)
        notificacionesConcedidas = tieneAccesoANotificaciones(context)
    }

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

    Spacer(Modifier.height(Medida.dentroDeSeccion))
    MinSectionHeader(title = "Captura en este teléfono")
    SensorPermissionsCard(installSource, principal = conAccionPrincipal && !smsConcedido)
    // El segundo sensor, y hoy el que más falta hace: el banco dejó de mandar SMS (el más nuevo
    // del teléfono del dueño es del 15-sep) pero sigue publicando una notificación por movimiento.
    SensorNotificationsCard(installSource, principal = conAccionPrincipal && smsConcedido && !notificacionesConcedidas)
    // Vuelta 73: los avisos de Movi (el permiso y sus interruptores) se mudaron a Tú → Notificaciones, la
    // casa de todos los avisos (ver `AvisosDeEsteTelefono`). Esta pantalla queda con la captura y un
    // enlace a ellos (en `CapturaDelBancoScreen`).
    // Se dibuja a sí misma solo cuando el aviso aplica; el Spacer va adentro para no
    // dejar un hueco doble cuando la app ya está exenta.
    SensorHibernationCard(principal = conAccionPrincipal && smsConcedido && notificacionesConcedidas)
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    SensorBackfillCard(installSource, outageSince, onSynced)
}

@Composable
private fun SensorPermissionsCard(installSource: InstallSource, principal: Boolean) {
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

    Superficie(modifier = Modifier.fillMaxWidth()) {
        EstadoDelPermiso("Permiso de SMS", granted)
        when (verdict) {
            SmsPermissionVerdict.GRANTED -> Unit

            SmsPermissionVerdict.ASK_IN_APP -> {
                Spacer(Modifier.height(Espacio.s))
                SensorButton("Conceder permisos", principal = principal) { requestPermissions() }
            }

            SmsPermissionVerdict.DENIED -> {
                Spacer(Modifier.height(Espacio.s))
                SensorButton("Abrir ajustes de la app", principal = principal) { openAppSettings(context) }
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    "Android ya no muestra el diálogo: concede SMS en Permisos, dentro de los ajustes de la app.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                if (shouldHintRestrictedSettings(Build.VERSION.SDK_INT, installSource)) {
                    Spacer(Modifier.height(Espacio.xs))
                    Text(
                        // Condicional a propósito: acá no sabemos si el bloqueo existe (ver
                        // shouldHintRestrictedSettings). Lo que sí sabemos es que en esta
                        // instalación es posible, y que el usuario que se lo encuentre no
                        // tiene forma de adivinar el menú donde se desactiva.
                        "Si el interruptor de SMS aparece gris y no te deja activarlo, es porque la app " +
                            "no se instaló desde una tienda: en esa misma ficha, menú de tres puntos " +
                            "(arriba a la derecha) → «Permitir ajustes restringidos». Después el " +
                            "interruptor se deja activar.",
                        style = Movi.textos.apoyo,
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
private fun SensorNotificationsCard(installSource: InstallSource, principal: Boolean) {
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

    Spacer(Modifier.height(Medida.dentroDeSeccion))
    Superficie(modifier = Modifier.fillMaxWidth()) {
        EstadoDelPermiso("Acceso a notificaciones", concedido)
        Spacer(Modifier.height(Espacio.s))
        Text(
            "Hay bancos que ya no mandan un SMS por cada movimiento, pero su app sí muestra una " +
                "notificación. Con este acceso, Movi lee esas notificaciones y las deja en " +
                "«Por revisar», en Actividad, igual que un SMS: tú decides cuáles se anotan.",
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(Espacio.xs))
        Text(
            "Movi solo lee las notificaciones de las apps de banco que tiene en su lista, y solo " +
                "esas salen de este teléfono. Las de cualquier otra app —tus chats, tu correo, " +
                "todo lo demás— se descartan sin leerlas y sin contarlas.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(Espacio.xs))
        Text(
            "Movi no toca la notificación: no la abre, no la borra y no responde por ti.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        if (concedido || ultima > 0L) {
            Spacer(Modifier.height(Espacio.s))
            // Línea propia, separada de las dos del historial de SMS: si esta fecha es reciente y
            // la de la captura de SMS no, el banco dejó de mandar mensajes y las notificaciones
            // están tapando el hueco. Mezclarlas escondería justo eso.
            Text(
                "Última notificación capturada: ${formatCaptureDate(ultima, "ninguna aún")}",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }
        if (!concedido) {
            Spacer(Modifier.height(Espacio.s))
            SensorButton("Dar acceso a las notificaciones", principal = principal) { abrirAjustesDeNotificaciones(context) }
            Spacer(Modifier.height(Espacio.xs))
            Text(
                "Se abre la lista del sistema: busca Movi y activa su interruptor. Android te " +
                    "pedirá confirmar. Al volver aquí, esta tarjeta dice «Concedido».",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            // El umbral acá es Android 13, no 15: la escucha de notificaciones fue de lo PRIMERO
            // que entró en los ajustes restringidos, dos versiones antes que el permiso de SMS.
            // Ver shouldHintRestrictedNotificationAccess.
            if (shouldHintRestrictedNotificationAccess(Build.VERSION.SDK_INT, installSource)) {
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    "Si el interruptor de Movi aparece gris y no te deja activarlo, es porque la " +
                        "app no se instaló desde una tienda: ve a los ajustes de la app, menú de " +
                        "tres puntos (arriba a la derecha) → «Permitir ajustes restringidos», y " +
                        "vuelve a intentarlo.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
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
private fun SensorHibernationCard(principal: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var exempt by remember { mutableStateOf(isAutoRevokeExempt(context)) }

    // El usuario cambia esto FUERA de la app: sin releer al volver, el aviso seguiría
    // en pantalla después de haberlo resuelto.
    OnResume(activity) { exempt = isAutoRevokeExempt(context) }

    if (!shouldWarnAboutHibernation(Build.VERSION.SDK_INT, exempt)) return

    Spacer(Modifier.height(Medida.dentroDeSeccion))
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Text(
            // Sin nombrar la hibernación: desde Android 11 se revocan los permisos, y desde
            // Android 13 además se detiene la app. La consecuencia es la misma en ambos y es
            // lo único que le importa a quien lee esto.
            // Vuelta 88: en tinta, no en rojo. Es información (el rojo es para errores) y en la guía
            // de primer uso asustaba al papá del dueño antes de que pasara nada.
            "Si no abres esta app durante unos meses, Android le revoca los permisos: " +
                "la captura de SMS se detiene y no avisa.",
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            modifier = Modifier.testTag(TAG_AVISO_DE_HIBERNACION),
        )
        Spacer(Modifier.height(Espacio.xs))
        Text(
            "Usar Movi en el navegador no cuenta: para Android es otra app.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(Espacio.s))
        SensorButton("Evitar que Android la pause", principal = principal) { openHibernationSettings(context) }
        Spacer(Modifier.height(Espacio.xs))
        Text(
            // El texto exacto del interruptor cambia por versión de Android ("Administrar la
            // app si no se usa" en 13+, "Pausar actividad de la app si no se usa" antes), así
            // que nombramos la sección, que sí es estable.
            "Se abre la ficha de la app: hasta abajo, en «Apps sin usar», apaga el " +
                "interruptor. Al volver aquí, este aviso desaparece.",
            style = Movi.textos.apoyo,
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

    Superficie(modifier = Modifier.fillMaxWidth()) {
        if (outageSince > 0L) {
            // m2: quien fue deslogueado por el Worker aterrizó en un login genérico sin
            // saber que la captura estuvo muda. Este es el único lector de la marca, y el
            // remedio (el historial) está justo abajo.
            Text(captureOutageNotice(outageSince), style = Movi.textos.apoyo, color = Movi.colores.aviso)
            Spacer(Modifier.height(Espacio.s))
        }
        Text(
            "Sube los SMS bancarios de tu período actual y del anterior que sigan en el teléfono. " +
                "Sirve para recuperar lo que la captura automática no alcanzó a mandar.",
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(Espacio.s))
        Text("Última captura automática: ${formatCaptureDate(lastCaptureAt, "ninguna aún")}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        // Línea separada a propósito: si esta fecha es reciente y la de arriba no, el
        // receiver en tiempo real está mudo aunque el historial esté al día — justo lo que
        // este indicador existe para no esconder.
        Text("Último historial sincronizado: ${formatCaptureDate(lastBackfillAt)}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Espacio.s))
        SensorButton(
            label = if (toSettings) "Abrir ajustes de la app" else "Sincronizar el período",
            enabled = loggedIn && !running,
            loading = running,
            principal = false,
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
            Spacer(Modifier.height(Espacio.s))
            Text("Leyendo el inbox y subiendo…", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        }
        if (toSettings) {
            Spacer(Modifier.height(Espacio.xs))
            Text(
                "Falta el permiso de lectura de SMS y Android ya no muestra el diálogo: " +
                    "concédelo en Permisos, dentro de los ajustes de la app.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            // Sin esto la línea de arriba mandaría a tocar un interruptor que puede estar
            // gris, sin decir cómo destrabarlo. Misma condición y mismo tono condicional que
            // el aviso de la tarjeta de permisos: acá tampoco se afirma que el bloqueo exista.
            if (shouldHintRestrictedSettings(Build.VERSION.SDK_INT, installSource)) {
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    "Si ese interruptor aparece gris, primero hay que permitir los ajustes " +
                        "restringidos desde el menú de tres puntos de esa misma ficha.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }
        }
        outcome?.let {
            Spacer(Modifier.height(Espacio.s))
            Text(
                backfillMessage(it),
                style = Movi.textos.cuerpo,
                color = if (isBackfillError(it)) Movi.colores.sale else Movi.colores.entra,
            )
        }
    }
}

/**
 * Los botones de la sección son el [Boton] del sistema (vuelta 88). Eran una caja de alto fijo (44) con
 * el texto en el crema del fondo sobre el amarillo: con la letra grande, «Dar acceso a las
 * notificaciones» se partía en dos renglones y el segundo quedaba afuera del botón, pegado al borde.
 * [principal] decide el amarillo: uno solo por vista (ver el KDoc del expect).
 */
@Composable
private fun SensorButton(
    label: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    principal: Boolean,
    onClick: () -> Unit,
) {
    Boton(
        label,
        onClick = onClick,
        variante = if (principal) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
        cargando = loading,
        habilitado = enabled,
        anchoCompleto = true,
    )
}

/** «Permiso de SMS · Concedido»: el nombre del permiso y su estado en una píldora (nunca en rojo: no es un error). */
@Composable
private fun EstadoDelPermiso(nombre: String, concedido: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto, modifier = Modifier.weight(1f))
        Pildora(if (concedido) "Concedido" else "Falta", if (concedido) TonoDePildora.Exito else TonoDePildora.Neutra)
    }
}

/** El tag del aviso de hibernación (para la prueba de su tono). */
const val TAG_AVISO_DE_HIBERNACION: String = "captura-aviso-de-hibernacion"

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
