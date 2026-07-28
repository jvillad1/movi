package com.jvillada.movi.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Captura SMS bancarios en el momento en que llegan y los encola para subir al server.
 * El filtro corre AQUÍ: los SMS que no matchean jamás salen del teléfono.
 */
class SmsRealtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Multiparte: agrupar por remitente y concatenar cuerpos en orden
        val bySender = messages.filterNotNull().groupBy { it.originatingAddress }
        for ((sender, parts) in bySender) {
            val body = parts.joinToString("") { it.messageBody.orEmpty() }
            if (body.isBlank() || !BankSenderFilter.matches(sender, body, SmsFilterConfigStore.load(context))) continue
            val ts = parts.first().timestampMillis
            val id = smsRealtimeId(sender, ts, body)
            val work = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setInputData(workDataOf("id" to id, "sender" to (sender ?: ""), "body" to body, "ts" to ts))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // Unique por id: re-entregas del broadcast no encolan duplicados
            WorkManager.getInstance(context).enqueueUniqueWork(id, ExistingWorkPolicy.KEEP, work)
        }
    }
}
