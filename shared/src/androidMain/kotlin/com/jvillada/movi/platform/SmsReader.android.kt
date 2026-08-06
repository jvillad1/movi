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
 *
 * [date] must stay `Telephony.Sms.DATE` — never `DATE_SENT`, even though [rowToSmsMessage]
 * now dates the wire `time` from `DATE_SENT` when it's usable. That id/time split is
 * deliberate and preserves backfill idempotency; see the KDoc on [rowToSmsMessage] before
 * changing what this function is called with.
 */
fun stableSmsId(address: String, date: Long, body: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$address|$date|$body".toByteArray(Charsets.UTF_8))
    val hex = digest.take(16).joinToString("") { "%02x".format(it) }
    return "sms_$hex"
}

/**
 * Formato del wire ("yyyy-MM-dd HH:mm") — el que parsea `SmsDedupe.parseSmsTime` en el server.
 *
 * `SimpleDateFormat` no es thread-safe, así que el formatter se construye adentro, por
 * llamada — igual que `SmsSync.captureItem` (ver ese archivo). Es API pública: dos llamadas
 * concurrentes a `readDeviceSms` (p.ej. un doble-tap del botón de sync) no pueden compartir
 * una instancia sin arriesgar un `time` corrupto en una fila financiera.
 */
fun smsWireTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(millis))

/**
 * Deriva máxima hacia ADELANTE que se le tolera a `DATE_SENT` respecto de `DATE`, antes de
 * descartarlo y caer a `DATE`.
 *
 * El objetivo NO es plausibilidad física en abstracto — es coincidir con el camino en
 * tiempo real. `SmsRealtimeReceiver` no aplica ningún chequeo de cordura: graba el
 * `timestampMillis` crudo del PDU tal cual llega, adelantado o no. Si el reloj del SMSC
 * está adelantado (p.ej. desfase de zona horaria del centro de mensajes), la fila en
 * tiempo real queda con ese valor adelantado sin filtrar, mientras que esta guarda hace
 * que el backfill lo descarte y caiga a `DATE`. Resultado: las dos filas del mismo SMS
 * físico terminan separadas por horas otra vez — el duplicado que este cambio existe para
 * evitar vuelve, y vuelve justo en el escenario de desfase de zona horaria que motiva esta
 * guarda. (La mitad "hacia atrás" del mismo desfase sí pasa la guarda y sí queda alineada
 * con tiempo real — el problema es asimétrico.)
 *
 * Tampoco asuma que `DATE` es la referencia sana frente a la que se mide: `DATE` sale del
 * reloj del TELÉFONO en el momento de recibir, mientras que `DATE_SENT` sale del PDU y es
 * independiente de ese reloj. Con el teléfono con la hora mal puesta (batería muerta, sin
 * hora de red), `DATE` es el valor corrupto y `DATE_SENT` el sano — y esta guarda puede
 * rechazar el `DATE_SENT` sano por "desviarse" del `DATE` corrupto. Ojo que el daño es
 * asimétrico: con el reloj del teléfono ATRASADO la deriva sale positiva y la guarda de
 * +5 min lo rechaza siempre; con el reloj ADELANTADO la deriva sale negativa y el
 * `DATE_SENT` sano se sigue usando hasta las 48 h.
 *
 * Se mantiene la guarda igual: el costo de sacarla es peor que el de tenerla — aceptar
 * cualquier `DATE_SENT` futuro sin filtro fecharía un movimiento financiero a voluntad de
 * lo que venga en el PDU. Esto documenta lo que cuesta, no una promesa de que cubre todos
 * los casos.
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
 * Piso absoluto de plausibilidad para un `DATE_SENT` sin `DATE` con el que compararlo.
 *
 * El chequeo normal (`MAX_SMSC_QUEUE_MILLIS`) ya rechaza un epoch-en-segundos frente a un
 * `DATE` bueno, pero cuando no hay `DATE` de referencia (`date <= 0`) no hay nada contra
 * qué medir la deriva — sin este piso, un `DATE_SENT` en segundos en vez de milisegundos
 * (el mismo bug de provider que motiva `MAX_SMSC_QUEUE_MILLIS`) pasaría directo como si
 * fuera dato bueno.
 *
 * Lo que compra el piso NO es evitar un 1970: sin `DATE` útil, caer al fallback devuelve
 * ese mismo `date <= 0` y el SMS igual queda fechado en 1970. Lo que compra es que un
 * valor implausible deje de aceptarse EN SILENCIO como si fuera el instante real de envío.
 * Hoy la rama es inalcanzable — la query de `readDeviceSms` garantiza `DATE > 0` — pero
 * `rowToSmsMessage` es API pública de un módulo compartido, así que el piso queda puesto.
 */
const val MIN_PLAUSIBLE_SMS_MILLIS: Long = 946_684_800_000L // 2000-01-01T00:00:00Z

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
    if (date <= 0L) {
        // Sin `DATE` de referencia, solo queda el piso absoluto — ver MIN_PLAUSIBLE_SMS_MILLIS.
        return if (dateSent >= MIN_PLAUSIBLE_SMS_MILLIS) dateSent else date
    }
    val delta = dateSent - date
    if (delta > MAX_SMS_CLOCK_SKEW_MILLIS) return date   // desalineado con tiempo real, no necesariamente falso — ver doc de MAX_SMS_CLOCK_SKEW_MILLIS
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

/**
 * Query Telephony.Sms.Inbox for messages from the last 30 days. Runs on Dispatchers.IO.
 *
 * El filtro de 30 días es sobre `DATE` (cuándo el teléfono guardó la fila), no sobre `time`.
 * Como `time` puede quedar hasta `MAX_SMSC_QUEUE_MILLIS` (48 h) más viejo que `DATE`, una
 * fila cerca del borde de estos 30 días — o de un cambio de mes — puede salir con `time`
 * apenas fuera de esa ventana, o en el mes calendario anterior. Es correcto (el banco la
 * mandó ahí), no un bug del filtro ni del dedupe.
 */
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
