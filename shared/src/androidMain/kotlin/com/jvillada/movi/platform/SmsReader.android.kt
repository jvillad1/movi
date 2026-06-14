package com.jvillada.movi.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.jvillada.movi.shared.model.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── pure functions (unit-testable, no Compose) ────────────────────────────────

/** Deterministic stable ID so repeated syncs deduplicate server-side. */
fun stableSmsId(address: String, date: Long, body: String): String =
    "sms_" + (address + date.toString() + body).hashCode().toLong().and(0xFFFFFFFFL).toString(16)

private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/** Map a raw SMS row to the shared SmsMessage wire model. */
fun rowToSmsMessage(address: String?, date: Long, body: String?): SmsMessage {
    val addr = address ?: ""
    val text = body ?: ""
    return SmsMessage(
        id    = stableSmsId(addr, date, text),
        time  = DATE_FMT.format(Date(date)),
        bank  = addr,
        text  = text,
        state = "new",
        det   = "",
    )
}

/** Query Telephony.Sms.Inbox for messages from the last 30 days. Runs on Dispatchers.IO. */
suspend fun readDeviceSms(context: Context): List<SmsMessage> = withContext(Dispatchers.IO) {
    val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val uri = Telephony.Sms.Inbox.CONTENT_URI
    val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
    )
    val selection = "${Telephony.Sms.DATE} >= ?"
    val selectionArgs = arrayOf(thirtyDaysAgo.toString())
    val sortOrder = "${Telephony.Sms.DATE} DESC"

    val results = mutableListOf<SmsMessage>()
    context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
        val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
        while (cursor.moveToNext()) {
            val address = if (addrIdx >= 0) cursor.getString(addrIdx) else null
            val body    = if (bodyIdx >= 0) cursor.getString(bodyIdx) else null
            val date    = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
            results += rowToSmsMessage(address, date, body)
        }
    }
    results
}

// ── Composable actual ──────────────────────────────────────────────────────────

@Composable
actual fun rememberSmsSync(onResult: (List<SmsMessage>) -> Unit): SmsSyncController {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val messages = readDeviceSms(context)
                onResult(messages)
            }
        }
    }

    val requestAndRead = {
        val already = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
        if (already == PackageManager.PERMISSION_GRANTED) {
            scope.launch {
                val messages = readDeviceSms(context)
                onResult(messages)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
        Unit
    }

    return SmsSyncController(available = true, requestAndRead = requestAndRead)
}
