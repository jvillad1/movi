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
