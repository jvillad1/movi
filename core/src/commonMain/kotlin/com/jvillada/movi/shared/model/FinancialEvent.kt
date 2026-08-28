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
