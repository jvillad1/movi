package com.jvillada.movi.sms

import android.content.Context
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.platform.readDeviceSms
import com.jvillada.movi.shared.model.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recuperación manual del historial: lee el inbox del teléfono (últimos 30 días), se queda
 * SOLO con los SMS bancarios y los sube por el mismo endpoint que la captura en vivo.
 *
 * Existe porque el sensor puede quedar mudo sin que nadie se entere — token vencido,
 * force-stop, hibernación, permisos auto-revocados — y en esa ventana los mensajes siguen
 * en el inbox del teléfono pero nada los podía recuperar. El camino en tiempo real
 * (SmsRealtimeReceiver) no se toca: sigue sin red y sin tocar el inbox.
 *
 * OJO con el filtro: es la MISMA condición del receiver. Lo que no matchea nunca se
 * serializa ni sale del teléfono — esto es más restrictivo que el backfill que existía
 * antes del ciclo sensor, que subía el inbox completo sin filtrar.
 */
object SmsBackfill {

    suspend fun run(context: Context): BackfillOutcome = withContext(Dispatchers.IO) {
        val token = SessionManager.token
        if (token.isNullOrBlank()) return@withContext BackfillOutcome.NoSession

        val inbox = runCatching { readDeviceSms(context) }
            .getOrElse { return@withContext outcomeForReadFailure(it) }
        val bank = filterBankMessages(inbox, SmsFilterConfigStore.load(context))
        if (bank.isEmpty()) return@withContext BackfillOutcome.NothingFound

        val payload = buildSmsSyncPayload(
            bank.map { SmsSyncItem(id = it.id, time = it.time, bank = it.bank, text = it.text) }
        )
        when (val result = postSmsSync(token, payload)) {
            is SmsSyncResult.Success -> {
                // Mismo reset que el Worker: fuera del pipeline Ktor nadie más corta la
                // racha de 401, y una racha que no se corta convierte transitorios viejos
                // en un logout "consecutivo".
                SessionManager.onAuthSuccess()
                // OJO: NO markLastCapture acá. Esa marca significa "el receiver en tiempo
                // real anduvo" — es el indicador de que el sensor sigue mudo (token vencido,
                // force-stop, hibernación). Si el backfill la pisara, una sincronización
                // manual exitosa escondería justo el síntoma que esta función existe para
                // detectar. Se guarda aparte en markLastBackfill.
                SmsFilterConfigStore.markLastBackfill(context)
                BackfillOutcome.Uploaded(found = bank.size, synced = result.synced)
            }
            SmsSyncResult.Unauthorized -> {
                // Mismo tratamiento que el Worker: la racha de SessionManager (3 seguidos)
                // decide si la sesión se cierra — un clear() directo deslogueaba al usuario
                // de toda la app por un solo 401. La marca queda para el aviso de pausa.
                SmsFilterConfigStore.markAuthExpired(context)
                SessionManager.onUnauthorized()
                if (SessionManager.loggedIn) {
                    // La sesión sobrevivió al 401: no hay que volver a entrar (todavía).
                    BackfillOutcome.AuthRetry
                } else {
                    // La racha se agotó: la app navega sola a LoginScreen.
                    BackfillOutcome.SessionExpired
                }
            }
            else -> BackfillOutcome.Failed
        }
    }
}

/**
 * SecurityException es lo que `contentResolver.query` tira cuando READ_SMS se revocó entre
 * el chequeo de la UI y esta llamada — exactamente lo que el auto-revoke por hibernación le
 * hace a esta app. Distinguirla de cualquier otra falla de lectura importa: [NoPermission]
 * ya tiene la copia correcta ("sin el permiso...") y, vía OnResume en la pantalla, la ruta a
 * ajustes — mientras que [Failed] dice "revisa la conexión", que es un consejo falso y sin
 * salida para este caso. Función pura para poder testearla sin mockear Android.
 */
internal fun outcomeForReadFailure(t: Throwable): BackfillOutcome =
    if (t is SecurityException) BackfillOutcome.NoPermission else BackfillOutcome.Failed

/** Resultado del backfill — cada rama tiene su mensaje en [backfillMessage]. */
sealed interface BackfillOutcome {
    /** [synced] es lo que el server insertó (el resto ya estaba); null si no se pudo leer. */
    data class Uploaded(val found: Int, val synced: Int?) : BackfillOutcome
    data object NothingFound : BackfillOutcome
    data object NoSession : BackfillOutcome

    /** El usuario negó READ_SMS en el diálogo — el inbox no se puede leer. */
    data object NoPermission : BackfillOutcome

    /** 401 que la racha todavía no convirtió en logout: la sesión sigue viva. */
    data object AuthRetry : BackfillOutcome
    data object SessionExpired : BackfillOutcome
    data object Failed : BackfillOutcome
}

/**
 * El filtro de privacidad aplicado al inbox. Idéntico al del receiver: remitente bancario
 * o keyword en el cuerpo. Función pura para poder testearla sin Android.
 */
internal fun filterBankMessages(messages: List<SmsMessage>, config: FilterConfig): List<SmsMessage> =
    messages.filter { it.text.isNotBlank() && BankSenderFilter.matches(it.bank, it.text, config) }

/** Texto que ve el usuario. Ninguna rama es silenciosa: siempre hay algo que leer. */
internal fun backfillMessage(outcome: BackfillOutcome): String = when (outcome) {
    is BackfillOutcome.Uploaded -> when {
        outcome.synced == null -> "${outcome.found} mensajes bancarios enviados."
        outcome.synced == 0 -> "${outcome.found} mensajes bancarios encontrados; el server ya los tenía todos."
        else -> "${outcome.found} mensajes bancarios encontrados · ${outcome.synced} nuevos subidos."
    }
    BackfillOutcome.NothingFound -> "No hay SMS bancarios en los últimos 30 días del teléfono."
    BackfillOutcome.NoSession -> "Entra primero: sin sesión no se puede subir nada."
    BackfillOutcome.NoPermission -> "Sin el permiso de lectura de SMS no se puede recuperar el historial."
    BackfillOutcome.AuthRetry -> "El servidor rechazó la sesión esta vez — prueba de nuevo en un momento."
    BackfillOutcome.SessionExpired -> "Sesión vencida — vuelve a entrar y prueba de nuevo."
    BackfillOutcome.Failed -> "No se pudo sincronizar. Revisa la conexión y prueba de nuevo."
}

/** Solo [BackfillOutcome.Uploaded] con algo subido es un final feliz; el resto avisa en rojo. */
internal fun isBackfillError(outcome: BackfillOutcome): Boolean = when (outcome) {
    is BackfillOutcome.Uploaded, BackfillOutcome.NothingFound -> false
    else -> true
}

/**
 * Aviso de pausa de la captura (m2 de la revisión): [sinceMillis] es la marca de
 * KEY_AUTH_ERROR_AT — el momento del primer 401 del episodio. Quien fue deslogueado por el
 * Worker aterriza en un LoginScreen genérico; al volver a la sección con sesión, este texto
 * le cuenta que la captura estuvo muda y que el historial es el camino para recuperar lo
 * que llegó en ese hueco. Función pura para poder testear el texto sin Android.
 */
internal fun captureOutageNotice(sinceMillis: Long): String {
    // SimpleDateFormat no es thread-safe: se construye por llamada, igual que en el resto
    // del subsistema.
    val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(sinceMillis))
    return "La captura estuvo pausada por un problema de sesión desde el $fecha — " +
        "sincroniza el historial para recuperar lo que haya llegado en ese tiempo."
}
