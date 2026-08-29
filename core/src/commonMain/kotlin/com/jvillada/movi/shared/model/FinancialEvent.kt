package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class EventSource { MANUAL, SMS, OCR, STATEMENT }

@Serializable
enum class ReconciliationStatus { UNCONFIRMED, RECONCILED, UNMATCHED }

@Serializable
data class FinancialEvent(
    val id: String,
    val accountId: String,
    val type: TransactionType,          // INCOME | EXPENSE (reuse existing enum)
    val amount: Long,                   // in native currency units (see currency)
    val currency: String = "COP",       // native currency of the amount (e.g. "COP", "USD")
    val category: String,
    val description: String,
    val merchant: String? = null,
    val timestamp: Long,
    val source: EventSource = EventSource.MANUAL,
    val rawPayload: String? = null,
    val reconciliationStatus: ReconciliationStatus = ReconciliationStatus.UNCONFIRMED,
    val syncedAt: Long? = null,
    /**
     * Enlace entre las **dos patas de un traspaso** (ver [transferLegsFor]): el EXPENSE de la
     * cuenta de origen y el INCOME de la de destino comparten este id; `null` en cualquier otro
     * evento, que es la enorme mayoría.
     *
     * A diferencia de [countsAsCashFlow], esto **sí se almacena** — es un hecho, no una
     * derivación: sin él la anulación de una pata no puede encontrar a la otra (y anular una
     * sola dejaría el saldo mintiendo en una de las dos cuentas), y Movimientos no podría
     * mostrar el traspaso como un solo hecho en vez de dos renglones sueltos.
     *
     * El cliente lo genera junto con los dos ids de evento y los manda en un solo
     * `POST /api/transfers`, que crea las dos patas en una transacción. Mandarlo en un
     * `POST /api/events` suelto se rechaza: sería medio traspaso, sin la pata que lo compensa.
     */
    val transferId: String? = null,
    /**
     * ¿Cuenta como ingreso/egreso del mes? **Derivado, nunca almacenado**: sale del tipo de la
     * cuenta a la que pertenece el evento (ver [isCashFlow]) y se recalcula en cada lectura,
     * tanto en el server como en la caché local. Lo que mande un cliente en un POST se ignora.
     *
     * Existe como campo del wire porque las pantallas que agregan gasto (Análisis,
     * Presupuestos) solo reciben eventos, no el tipo de la cuenta — sin esto no podrían
     * distinguir un ajuste de deuda de una compra real.
     *
     * `true` por defecto: un evento sin cuenta conocida se cuenta, que es el comportamiento
     * histórico y el conservador para cuentas de activo.
     */
    val countsAsCashFlow: Boolean = true,
    /**
     * **Cuándo se anotó** el movimiento — no cuándo ocurrió. Eso es [timestamp], y son dos cosas
     * distintas desde que la fecha se elige a mano.
     *
     * Hace falta porque [timestamp] dejó de poder ordenar el día. Al elegir una fecha que no es
     * hoy, el cliente la convierte al **mediodía** de Bogotá (`timestampParaFecha`): solo «Hoy»
     * conserva la hora real. Así que cinco gastos de ayer anotados uno detrás del otro quedan
     * **todos con el mismo instante**, y ordenar por [timestamp] no decide nada entre ellos — el
     * orden que ve el dueño es el que salga. Con esto, el que anotó último queda arriba.
     *
     * **Es el desempate, no el criterio principal** (ver `MAS_RECIENTE_PRIMERO`): [timestamp]
     * sigue mandando. Un SMS del banco de ayer a las 23:00 tiene hora real y tiene que quedar
     * arriba de un gasto de ayer anotado a mano (mediodía), aunque este último se haya escrito
     * después. La creación solo entra cuando los dos instantes son iguales, que es exactamente el
     * caso que estaba roto.
     *
     * **Nullable, y sin inventar nada.** Los movimientos que ya existen no la tienen y no hay de
     * dónde sacarla: la tabla nunca guardó cuándo se creó una fila. Un `null` cae a [timestamp]
     * en el comparador.
     *
     * Eso **no** quiere decir que esos movimientos queden donde estaban. Entre dos filas viejas
     * del mismo instante el respaldo empata también, y el orden lo termina fijando el `id`, que es
     * un UUID al azar: estable en cada lectura, pero arbitrario. O sea que lo que ya está cargado
     * no recibe el arreglo — solo deja de bailar. El detalle, con el porqué de no hacer backfill,
     * está en `MAS_RECIENTE_PRIMERO`.
     *
     * **La pone el cliente al escribir el movimiento**, y el server solo la completa si no viene:
     * es el único instante que el cliente conoce y el server no. Si la estampara el server al
     * recibir, un movimiento anotado sin señal y sincronizado dos días después quedaría «creado»
     * dos días más tarde y saltaría al tope de su día sin motivo. El riesgo del reloj del
     * teléfono está acotado a propósito: esto **no** decide a qué día pertenece el movimiento ni
     * entra en ningún total — solo desempata renglones dentro de un mismo día.
     *
     * Lo que entra solo (SMS, extracto, OCR) trae su propia fecha en [timestamp] pero se «anota»
     * cuando se captura o se importa, así que ahí la creación es ese momento.
     */
    val createdAt: Long? = null,
)

/**
 * Body de `PUT /api/events/{id}/category`.
 *
 * Un DTO propio en vez de reusar [FinancialEvent] entero: el cliente solo tiene voz sobre la
 * categoría (ver [FinancialEvent.countsAsCashFlow], que es derivado y se ignora si viene en el
 * body), así que el wire de entrada no debería ni sugerir que se puede mandar el resto de campos.
 */
@Serializable
data class UpdateEventCategoryRequest(val category: String)

/**
 * Body de `PUT /api/events/{id}/timestamp` — **corregir la fecha de un movimiento ya anotado**.
 *
 * Es un epoch-ms y no un `"AAAA-MM-DD"` a propósito: el almacenamiento de Movi es epoch-ms y cada
 * pantalla lo vuelve a fechar en la zona de la app (ver `AppTimeZone`), así que mandar una fecha
 * civil obligaría al server a elegir una hora del día — y la hora que elija decide en qué día cae
 * el movimiento visto desde otra zona. El cliente ya sabe hacer esa conversión (al **mediodía** de
 * Bogotá, ver `epochAlMediodia`), y es el único que la hace, en un solo lugar.
 *
 * DTO propio y no [FinancialEvent] entero por el mismo motivo que [UpdateEventCategoryRequest]:
 * acá el cliente solo tiene voz sobre la fecha.
 */
@Serializable
data class UpdateEventTimestampRequest(val timestamp: Long)

/**
 * El rechazo de `PUT /api/events/{id}/timestamp` cuando la fecha pedida todavía no llegó.
 *
 * Vive en `:core` para que el server y el cliente digan **exactamente lo mismo** — mismo criterio
 * que [TRANSFER_RECATEGORIZE_BLOCKED]: la hoja corta antes para poder explicarlo, y el server
 * repite la guarda porque no puede confiar en que el que llama sea esa hoja.
 */
const val EVENT_DATE_IN_FUTURE: String =
    "Esa fecha todavía no llegó: un movimiento se anota cuando la plata ya se movió."

/**
 * La marca de «esto ya ocurrió» que un recurrente puso sobre un movimiento — respuesta de
 * `GET /api/events/{id}/occurrence`, `null` cuando no hay ninguna.
 *
 * Existe para que la hoja que corrige la fecha pueda **avisar antes** de soltar un sello, en vez
 * de dejar que el dueño se entere el día que no le llega el recordatorio.
 *
 * [validFrom] y [validTo] son la ventana de fechas que sostiene el sello (`occurrenceWindow` en el
 * server, la misma que usa el emparejador para proponer). Vienen calculadas del server a propósito:
 * la ventana es lógica del emparejador y no puede vivir en dos lados. El cliente solo compara la
 * fecha que el dueño acaba de tocar contra estos dos días.
 */
@Serializable
data class EventOccurrenceMark(
    val ruleId: String,
    val ruleName: String,
    /** "YYYY-MM" del vencimiento sellado. */
    val period: String,
    /** "YYYY-MM-DD" inclusive. */
    val validFrom: String,
    /** "YYYY-MM-DD" inclusive. */
    val validTo: String,
)

@Serializable
data class VoidEvent(
    val id: String,
    val originalEventId: String,
    val reason: String? = null,
    val timestamp: Long,
)

// Day-grouped view (replaces TransactionDay)
@Serializable
data class EventDay(
    val date: String,
    val total: Long,
    val items: List<FinancialEvent>,
)
