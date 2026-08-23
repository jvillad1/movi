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
