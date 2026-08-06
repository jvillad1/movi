package com.jvillada.movi.server.sms

import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Clave de dedupe de un SMS bancario dentro de un usuario: el texto crudo y la hora del
 * wire tal como la mandó el cliente. El id NO forma parte de la clave a propósito — ver
 * [isDuplicateSms].
 */
data class SmsKey(val text: String, val time: String)

/**
 * Ventana dentro de la cual dos SMS con el mismo texto se consideran el mismo evento.
 *
 * Los dos caminos de captura fechan el MISMO SMS físico apuntando al mismo reloj — el del
 * BANCO — cuando pueden: el receiver en tiempo real usa `timestampMillis` del PDU sin
 * ningún chequeo de cordura (`SmsRealtimeReceiver.kt`), y el backfill usa
 * `Telephony.Sms.DATE_SENT` cuando es creíble contra `Telephony.Sms.DATE`, cayendo a
 * `DATE` (cuándo el TELÉFONO lo recibió) si no lo es (`effectiveSmsTime` en
 * `SmsReader.android.kt`). Lo que esta ventana — y el dedupe en general — compara es el
 * campo `time`, nunca el `id`: los dos esquemas de id (`sms_` + 32 hex acá, `sms_rt_` + 16
 * hex en tiempo real) no pueden coincidir como string con ninguna entrada, alineados los
 * relojes o no, así que esta ventana sigue siendo la única defensa contra el duplicado
 * cross-esquema. (Unificar los prefijos para que "colisionaran" rompería el hook de push
 * del server, que está acotado a `sms_rt_` justo para que un backfill no dispare
 * notificaciones — no es un camino que valga perseguir.)
 *
 * Ambos caminos formatean `time` con precisión de minuto ("yyyy-MM-dd HH:mm"), así que el
 * truncado por sí solo puede mover el reloj hasta un minuto entero si el evento cae justo
 * sobre el cambio de minuto. Un minuto cubre exactamente ese máximo teórico, sin margen
 * extra — más el margen que dejan los residuales de abajo.
 *
 * Residuales conocidos y deliberados que esta ventana NO cierra, porque alinear el reloj
 * (arriba) no cubre todos los casos en que `time` se aparta:
 * - `DATE_SENT` sin poblar (frecuente en varias ROMs/carriers): el backfill cae a `DATE`.
 * - Cola de entrega del SMSC más allá de la ventana que `effectiveSmsTime` acepta hacia
 *   atrás (`MAX_SMSC_QUEUE_MILLIS`, 48 h del lado del cliente) — con el teléfono apagado,
 *   en modo avión o sin cobertura por más de eso.
 * - Reloj del SMSC desalineado hacia adelante: `effectiveSmsTime` lo descarta
 *   (`MAX_SMS_CLOCK_SKEW_MILLIS`) y el backfill cae a `DATE`, que vuelve a diferir del
 *   `timestampMillis` crudo que usa tiempo real.
 * En cualquiera de estos, un banco que manda a las 14:00 y un teléfono que reconecta a las
 * 14:20 puede seguir produciendo una fila realtime en 14:00 y una de backfill en 14:20:
 * ninguna tolerancia finita cierra esa brecha, y ensancharla para intentarlo cuesta más de
 * lo que rescata (ver abajo). Es el lado seguro del error — dos filas para un movimiento,
 * visible y corregible, no una pérdida silenciosa —, pero un futuro mantenedor no debería
 * asumir que este número ya lo cubre.
 *
 * Por qué no ensanchar para compensar esos residuales: cada minuto extra compra supresión
 * del duplicado cross-esquema al costo de volver a perder movimientos reales en silencio —
 * el fallo que el issue #27 existe para eliminar, y el lado equivocado de esa prioridad.
 * Dos cobros idénticos (un doble-swipe de POS, o una compra partida en dos cargos iguales
 * al mismo comercio) separados por 2 minutos o más SIEMPRE sobreviven con esta ventana; a
 * dos minutos se perderían siempre. Ojo con el caso intermedio: una separación real de 90
 * segundos trunca a 1 o a 2 minutos según dónde caiga respecto del borde de minuto, así
 * que sobrevive solo en parte de los casos — mejor que perderse siempre, no equivalente.
 *
 * Más chica resucita el duplicado cross-esquema del truncado a minuto; más grande resucita
 * la pérdida silenciosa sin arreglar los residuales de arriba, que de todos modos ya son
 * más grandes que cualquier ventana razonable.
 */
val SMS_DEDUPE_TOLERANCE: Duration = Duration.ofMinutes(1)

/**
 * Formato del wire ("yyyy-MM-dd HH:mm", lo que producen `SmsSync.captureItem` y
 * `SmsReader.rowToSmsMessage`), tolerando la variante ISO local con 'T' y los segundos
 * opcionales. La columna `time` es un varchar libre: cuanto más formas razonables se
 * puedan leer, menos filas caen en el fallback de "ilegible → insertar".
 */
private val WIRE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd[' ']['T']HH:mm[:ss]", Locale.ROOT)

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
