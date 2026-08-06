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
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── pure functions (unit-testable, no Compose) ────────────────────────────────

/**
 * Deterministic stable ID so repeated syncs deduplicate server-side. Uses SHA-256
 * (truncated) rather than String.hashCode() — a 32-bit hash collision would silently
 * drop a real transaction, which is unacceptable for finance data.
 */
fun stableSmsId(address: String, date: Long, body: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$address|$date|$body".toByteArray(Charsets.UTF_8))
    val hex = digest.take(16).joinToString("") { "%02x".format(it) }
    return "sms_$hex"
}

private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)

/** Formato del wire ("yyyy-MM-dd HH:mm") — el que parsea `SmsDedupe.parseSmsTime` en el server. */
fun smsWireTime(millis: Long): String = DATE_FMT.format(Date(millis))

/**
 * Deriva máxima hacia ADELANTE que se le tolera a `DATE_SENT` respecto de `DATE`.
 *
 * Un SMS no puede recibirse antes de mandarse, así que cualquier `DATE_SENT` posterior a
 * la recepción es reloj desalineado, no un dato. Se tolera una franja chica porque el
 * reloj del SMSC y el del teléfono no son el mismo; más allá de eso es basura conocida
 * (hay ROMs/carriers que escriben la hora local del centro de mensajes como si fuera UTC,
 * lo que produce desfases de horas enteras).
 */
const val MAX_SMS_CLOCK_SKEW_MILLIS: Long = 5 * 60 * 1000L

/**
 * Cola de entrega máxima que se le acepta a `DATE_SENT` hacia ATRÁS respecto de `DATE`.
 *
 * Esta es la ventana que el arreglo existe para cubrir: teléfono apagado, en modo avión o
 * sin cobertura, el SMSC encola y entrega horas después. 48 h cubre con holgura un fin de
 * semana sin señal y sigue por encima de la validez que los bancos suelen ponerle a sus
 * mensajes transaccionales (24–72 h; pasado eso el SMSC descarta y el SMS nunca llega).
 *
 * No es infinita a propósito: sin techo, un `DATE_SENT` basura arrastra el `time` de un
 * movimiento financiero a una fecha arbitraria. El caso concreto que este techo ataja es
 * el epoch en segundos en vez de milisegundos — un bug de provider real que aterriza el
 * mensaje en 1970 y lo mostraría con 55 años de antigüedad.
 */
const val MAX_SMSC_QUEUE_MILLIS: Long = 48 * 60 * 60 * 1000L

/**
 * Qué reloj fecha el SMS: `DATE_SENT` (cuándo lo mandó el banco) si es creíble, si no
 * `DATE` (cuándo lo recibió el teléfono).
 *
 * Por qué preferir `DATE_SENT`: el camino en tiempo real (`SmsRealtimeReceiver`) fecha con
 * `timestampMillis` del PDU, que ES la hora de envío. Si el backfill fecha con `DATE`, el
 * MISMO SMS físico llega al server con dos horas distintas, y cuando la cola de entrega
 * las separa más que la tolerancia de dedupe (`SmsDedupe.SMS_DEDUPE_TOLERANCE`, 1 min) el
 * backfill inserta una segunda fila de un movimiento ya capturado. Alinear el reloj hace
 * que esa tolerancia alcance.
 *
 * Por qué el fallback no es opcional: `DATE_SENT` se llena desde el timestamp del centro
 * de servicio del PDU y MUCHAS ROMs/carriers no lo llenan — queda en 0 (o la columna ni
 * existe). Leerlo a ciegas fecharía esos mensajes en 1970, que es peor que el duplicado
 * que estamos arreglando.
 *
 * Función pura para poder testear la decisión sin Android (ver SmsReaderTimeTest).
 */
fun effectiveSmsTime(dateSent: Long, date: Long): Long {
    if (dateSent <= 0L) return date            // no poblado: el caso frecuente
    if (date <= 0L) return dateSent            // sin referencia para el chequeo de cordura
    val delta = dateSent - date
    if (delta > MAX_SMS_CLOCK_SKEW_MILLIS) return date   // "enviado" después de recibido
    if (delta < -MAX_SMSC_QUEUE_MILLIS) return date      // demasiado viejo para ser real
    return dateSent
}

/**
 * Map a raw SMS row to the shared SmsMessage wire model.
 *
 * OJO con la asimetría — es deliberada, no un descuido: `time` sale de [effectiveSmsTime]
 * (o sea, de `DATE_SENT` cuando sirve) pero `id` sigue saliendo SOLO de `DATE`.
 *
 * El `id` no puede moverse porque es la clave de idempotencia del backfill: los mensajes
 * ya subidos están guardados con un id derivado de `DATE`. Si el id pasara a derivar de
 * `DATE_SENT`, re-hacer el backfill de un mensaje ya guardado produciría un id NUEVO,
 * mientras la fila vieja conserva su `time` viejo — y si los dos relojes difieren más que
 * la tolerancia de dedupe, el server insertaría un duplicado de una fila que ya tiene.
 * Anclando el id a `DATE` (que para un mensaje ya en el inbox nunca cambia), re-sincronizar
 * sigue siendo exacto: el chequeo por id lo ataja antes de mirar el tiempo.
 *
 * Y tampoco hace falta que coincidan: el id de este camino (`sms_` + 32 hex) y el del
 * tiempo real (`sms_rt_` + 16 hex) no pueden dar la misma cadena ni con la misma entrada,
 * y unificar los prefijos rompería el hook de push del server, que está acotado a
 * `sms_rt_` justo para que un backfill no dispare notificaciones. Lo que el dedupe compara
 * es texto + `time`; alinear `time` es todo lo que hace falta.
 */
fun rowToSmsMessage(address: String?, date: Long, dateSent: Long, body: String?): SmsMessage {
    val addr = address ?: ""
    val text = body ?: ""
    return SmsMessage(
        id    = stableSmsId(addr, date, text),
        time  = smsWireTime(effectiveSmsTime(dateSent = dateSent, date = date)),
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
        // Hora de envío del banco (timestamp del centro de servicio del PDU). Frecuentemente
        // 0 — ver effectiveSmsTime, que decide cuándo es usable.
        Telephony.Sms.DATE_SENT,
    )
    val selection = "${Telephony.Sms.DATE} >= ?"
    val selectionArgs = arrayOf(thirtyDaysAgo.toString())
    val sortOrder = "${Telephony.Sms.DATE} DESC"

    val results = mutableListOf<SmsMessage>()
    context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
        val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
        // -1 si la ROM no expone DATE_SENT: effectiveSmsTime se cae a DATE con el 0.
        val sentIdx = cursor.getColumnIndex(Telephony.Sms.DATE_SENT)
        while (cursor.moveToNext()) {
            val address  = if (addrIdx >= 0) cursor.getString(addrIdx) else null
            val body     = if (bodyIdx >= 0) cursor.getString(bodyIdx) else null
            val date     = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
            val dateSent = if (sentIdx >= 0) cursor.getLong(sentIdx) else 0L
            results += rowToSmsMessage(address, date, dateSent, body)
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
