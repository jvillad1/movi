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
 * Los dos caminos de captura fechan el MISMO SMS físico con relojes distintos: el receiver
 * en tiempo real usa `timestampMillis` del PDU (`SmsRealtimeReceiver.kt`, cuándo el BANCO
 * mandó el SMS) y el backfill usa `Telephony.Sms.DATE` (`SmsReader.android.kt`, cuándo el
 * TELÉFONO lo recibió y guardó). Ambos formatean con precisión de minuto
 * ("yyyy-MM-dd HH:mm"), así que el truncado por sí solo puede mover el reloj hasta un
 * minuto entero si el evento cae justo sobre el cambio de minuto. Un minuto cubre
 * exactamente ese máximo teórico, sin margen extra.
 *
 * Residual conocido y deliberado, que NO cierra esta ventana: la cola de entrega del SMS
 * puede demorar la llegada al teléfono minutos u horas más allá del truncado — con el
 * teléfono apagado, en modo avión o sin cobertura, el delay es no acotado. Un banco que
 * manda a las 14:00 y un teléfono que reconecta a las 14:20 produce una fila realtime en
 * 14:00 y una fila de backfill en 14:20: ninguna tolerancia finita cierra esa brecha, y
 * ensancharla para intentarlo cuesta más de lo que rescata (ver abajo). Es el lado seguro
 * del error — dos filas para un movimiento, visible y corregible, no una pérdida
 * silenciosa —, pero un futuro mantenedor no debería asumir que este número ya lo cubre.
 * El arreglo real es hashear/formatear `Telephony.Sms.DATE_SENT` (cuándo lo mandó el banco)
 * en vez de `DATE` en `SmsReader.android.kt`: alinearía las dos fuentes con el mismo reloj
 * y los dos esquemas de id colisionarían exacto, sin necesitar esta heurística. Fuera de
 * alcance acá — es un cambio de cliente.
 *
 * Por qué no ensanchar para compensar ese residual: cada minuto extra compra supresión del
 * duplicado cross-esquema al costo de volver a perder movimientos reales en silencio — el
 * fallo que el issue #27 existe para eliminar, y el lado equivocado de esa prioridad. Dos
 * cobros idénticos 90 segundos aparte (un doble-swipe de POS, o una compra partida en dos
 * cargos iguales al mismo comercio) sobreviven a un minuto de ventana; a dos minutos se
 * pierden.
 *
 * Más chica resucita el duplicado cross-esquema del truncado a minuto; más grande resucita
 * la pérdida silenciosa sin arreglar la brecha de delivery-delay, que de todos modos ya es
 * más grande que cualquier ventana razonable.
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
