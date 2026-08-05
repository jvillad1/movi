package com.jvillada.movi.server.sms

import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Clave de dedupe de un SMS bancario dentro de un usuario: el texto crudo y la hora del
 * wire tal como la mandó el cliente. El id NO forma parte de la clave a propósito — ver
 * [isDuplicateSms].
 */
data class SmsKey(val text: String, val time: String)

/**
 * Ventana dentro de la cual dos SMS con el mismo texto se consideran el mismo evento.
 *
 * Los dos caminos de captura fechan el MISMO SMS físico con relojes distintos: el receiver
 * en tiempo real usa `timestampMillis` del PDU y el backfill usa `Telephony.Sms.DATE`.
 * Ambos formatean con precisión de minuto ("yyyy-MM-dd HH:mm"), así que una diferencia de
 * pocos segundos entre las dos fuentes puede aparecer como un minuto entero de diferencia
 * si cae sobre el cambio de minuto. Dos minutos deja un minuto de margen sobre ese máximo
 * teórico sin acercarse a la escala en la que dos compras distintas con texto byte-idéntico
 * son plausibles (los SMS sin fecha ni referencia se repiten con horas o días de por medio).
 *
 * Más chica resucita el duplicado cross-esquema; más grande resucita la pérdida silenciosa.
 */
val SMS_DEDUPE_TOLERANCE: Duration = Duration.ofMinutes(2)

/**
 * Formato del wire ("yyyy-MM-dd HH:mm", lo que producen `SmsSync.captureItem` y
 * `SmsReader.rowToSmsMessage`), tolerando la variante ISO local con 'T' y los segundos
 * opcionales. La columna `time` es un varchar libre: cuanto más formas razonables se
 * puedan leer, menos filas caen en el fallback de "ilegible → insertar".
 */
private val WIRE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd[' ']['T']HH:mm[:ss]")

/** Devuelve null si el texto no es una fecha-hora legible. */
fun parseSmsTime(raw: String): LocalDateTime? =
    try {
        LocalDateTime.parse(raw.trim(), WIRE_TIME_FORMAT)
    } catch (_: DateTimeException) {
        null
    }

/**
 * ¿Son [a] y [b] el mismo SMS físico?
 *
 * Texto idéntico Y timestamps dentro de [SMS_DEDUPE_TOLERANCE]. El texto solo no alcanza:
 * los SMS de compra sin fecha, hora ni referencia ("Compra aprobada $28.500 en Uber BV.")
 * se repiten byte por byte entre transacciones reales distintas, y colapsarlas borraba un
 * movimiento del inbox sin dejar rastro.
 *
 * Si CUALQUIERA de los dos tiempos es ilegible, la respuesta es "no son el mismo": ante la
 * duda insertamos. Un duplicado visible el usuario lo ignora o lo corrige; una transacción
 * perdida no deja señal. Los dos caminos usan el mismo formato fijo, así que un tiempo
 * ilegible ya indica algo roto aguas arriba — y el chequeo por id de la ruta sigue
 * haciendo idempotente el re-envío de una misma fila, así que esto no multiplica filas
 * en cada sync.
 */
fun isSameSms(a: SmsKey, b: SmsKey): Boolean {
    if (a.text != b.text) return false
    val ta = parseSmsTime(a.time) ?: return false
    val tb = parseSmsTime(b.time) ?: return false
    return Duration.between(ta, tb).abs() <= SMS_DEDUPE_TOLERANCE
}

/** ¿[candidate] ya está entre [existing]? Función pura; [SmsDedupeIndex] la usa por bucket. */
fun isDuplicateSms(candidate: SmsKey, existing: Iterable<SmsKey>): Boolean =
    existing.any { isSameSms(candidate, it) }

/**
 * Índice de las claves ya conocidas, agrupadas por texto.
 *
 * Solo existe por costo: comparar contra todas las filas del usuario sería O(filas) por
 * mensaje entrante. Agrupar por texto deja cada bucket con las poquísimas filas que
 * comparten texto exacto, así que la comparación por tiempo se hace sobre un puñado de
 * candidatos. El criterio es exactamente [isDuplicateSms] — el índice no decide nada.
 *
 * No es thread-safe: se construye y se consume dentro de una sola transacción de sync.
 */
class SmsDedupeIndex(existing: Iterable<SmsKey>) {

    private val byText: MutableMap<String, MutableList<SmsKey>> = HashMap()

    init {
        existing.forEach(::add)
    }

    fun isDuplicate(candidate: SmsKey): Boolean =
        isDuplicateSms(candidate, byText[candidate.text].orEmpty())

    /** Siembra el índice con una fila recién insertada (dedupe dentro del mismo lote). */
    fun add(key: SmsKey) {
        byText.getOrPut(key.text) { mutableListOf() } += key
    }
}
