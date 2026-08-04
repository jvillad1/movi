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
            .getOrElse { return@withContext BackfillOutcome.Failed }
        val bank = filterBankMessages(inbox, SmsFilterConfigStore.load(context))
        if (bank.isEmpty()) return@withContext BackfillOutcome.NothingFound

        val payload = buildSmsSyncPayload(
            bank.map { SmsSyncItem(id = it.id, time = it.time, bank = it.bank, text = it.text) }
        )
        when (val result = postSmsSync(token, payload)) {
            is SmsSyncResult.Success -> {
                SmsFilterConfigStore.markLastCapture(context)
                BackfillOutcome.Uploaded(found = bank.size, synced = result.synced)
            }
            SmsSyncResult.Unauthorized -> {
                // Mismo tratamiento que el Worker: marcamos el vencimiento y cerramos
                // sesión para que la tarjeta de SESIÓN muestre el aviso y el login.
                SmsFilterConfigStore.markAuthExpired(context)
                SessionManager.clear()
                BackfillOutcome.SessionExpired
            }
            else -> BackfillOutcome.Failed
        }
    }
}

/** Resultado del backfill — cada rama tiene su mensaje en [backfillMessage]. */
sealed interface BackfillOutcome {
    /** [synced] es lo que el server insertó (el resto ya estaba); null si no se pudo leer. */
    data class Uploaded(val found: Int, val synced: Int?) : BackfillOutcome
    data object NothingFound : BackfillOutcome
    data object NoSession : BackfillOutcome

    /** El usuario negó READ_SMS en el diálogo — el inbox no se puede leer. */
    data object NoPermission : BackfillOutcome
    data object SessionExpired : BackfillOutcome
    data object Failed : BackfillOutcome
}

/**
 * El filtro de privacidad aplicado al inbox. Idéntico al del receiver: remitente bancario
 * o keyword en el cuerpo. Función pura para poder testearla sin Android.
 */
fun filterBankMessages(messages: List<SmsMessage>, config: FilterConfig): List<SmsMessage> =
    messages.filter { it.text.isNotBlank() && BankSenderFilter.matches(it.bank, it.text, config) }

/** Texto que ve el usuario. Ninguna rama es silenciosa: siempre hay algo que leer. */
fun backfillMessage(outcome: BackfillOutcome): String = when (outcome) {
    is BackfillOutcome.Uploaded -> when {
        outcome.synced == null -> "${outcome.found} mensajes bancarios enviados."
        outcome.synced == 0 -> "${outcome.found} mensajes bancarios encontrados; el server ya los tenía todos."
        else -> "${outcome.found} mensajes bancarios encontrados · ${outcome.synced} nuevos subidos."
    }
    BackfillOutcome.NothingFound -> "No hay SMS bancarios en los últimos 30 días del teléfono."
    BackfillOutcome.NoSession -> "Entrá primero: sin sesión no se puede subir nada."
    BackfillOutcome.NoPermission -> "Sin el permiso de lectura de SMS no se puede recuperar el historial."
    BackfillOutcome.SessionExpired -> "Sesión vencida — volvé a entrar y probá de nuevo."
    BackfillOutcome.Failed -> "No se pudo sincronizar. Revisá la conexión y probá de nuevo."
}

/** Solo [BackfillOutcome.Uploaded] con algo subido es un final feliz; el resto avisa en rojo. */
fun isBackfillError(outcome: BackfillOutcome): Boolean = when (outcome) {
    is BackfillOutcome.Uploaded, BackfillOutcome.NothingFound -> false
    else -> true
}
