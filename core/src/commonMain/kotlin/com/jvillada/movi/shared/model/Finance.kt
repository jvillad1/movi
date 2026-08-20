package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class Scope { SELF, FAMILY }

@Serializable
data class FinanceSummary(
    val scope: Scope,
    val balance: Long,
    val ingresos: Long,
    val egresos: Long,
    /**
     * Cantidad de eventos no anulados **y que no sean de apertura de cuenta** del usuario (todas
     * las cuentas, no solo el mes ni el [scope]) — el server ya los carga completos para calcular
     * este resumen ([com.jvillada.movi.server.balance.loadNonVoidedEvents]), así que este campo
     * es prácticamente gratis. Existe para que el Dashboard pueda saber "¿esta cuenta tiene algún
     * movimiento?" — o, más importante, "¿el usuario ya anotó algo?" para apagar la guía de
     * primeros pasos — sin traerse la lista completa con `GET /api/events`.
     *
     * F54: el evento "Saldo inicial"/"Deuda inicial" (categoría
     * [com.jvillada.movi.shared.model.OPENING_CATEGORY]) NO cuenta acá. Crear una cuenta con
     * plata que ya tenías no es "un movimiento" a ojos del usuario — sin este filtro, la guía de
     * primeros pasos se apagaba sola apenas creabas la primera cuenta con saldo, antes de que
     * anotaras nada de verdad.
     *
     * Con default para que un cliente viejo (que no lo espera) y un server viejo (que no lo
     * manda) sigan deserializando sin romperse.
     */
    val eventCount: Int = 0,
)

@Serializable
data class Holding(
    val name: String,
    val sub: String,
    val amount: Long,
    val change: Double,
)

@Serializable
data class CreditTerms(
    val accountId: String,
    val bank: String,
    val principal: Long,        // capital original (COP)
    val rateEa: Double,         // % EA, p.ej. 17.46
    val termMonths: Int,
    val installment: Long,      // cuota mensual total (incl. seguros)
    val dayOfMonth: Int,        // día de pago
    val startDate: String,      // ISO "2026-06-01" (desembolso)
    val notes: String? = null,
)

@Serializable
data class CreditSummary(
    val account: Account,       // cuenta LOAN con deuda derivada en balance
    val terms: CreditTerms?,    // null si la cuenta LOAN aún no tiene términos
    val paidPct: Double?,       // 1 − deuda/principal clampado a [0,1]; null sin términos
    /**
     * Movimiento que el server acabó de registrar, o null si no registró ninguno.
     *
     * Solo lo llena `POST /api/credits/{id}/balance-adjustment`; en GET/PUT es null. Está acá
     * para que el cliente offline-first pueda espejar en su DB local el evento exacto que
     * escribió el server, en vez de adivinarlo: [com.jvillada.movi.shared.repository]
     * lo inserta ya marcado como sincronizado. Sin esto, en Android el ajuste no aparecía
     * en movimientos ni movía el saldo cacheado de la cuenta.
     */
    val adjustmentEvent: FinancialEvent? = null,
)

/**
 * Prefijo de los ids de las reglas recurrentes sintéticas derivadas de credit_terms.
 * Compartido entre el server (que las genera) y la UI (que las distingue de las reales).
 */
const val CREDIT_RULE_PREFIX = "credit_"

/** Alta atómica de un crédito: cuenta LOAN + evento de apertura + términos en una sola operación server-side. */
@Serializable
data class CreateCreditRequest(
    val name: String,           // nombre de la cuenta LOAN a crear
    val initialDebt: Long,      // deuda actual (COP) — genera el evento "Deuda inicial"
    val terms: CreditTerms,     // accountId se ignora; el server asigna el de la cuenta nueva
)

/**
 * Ajusta la deuda de un crédito ya existente al saldo real que reporta el banco.
 *
 * Se manda el saldo OBJETIVO, no la diferencia: el server calcula el delta contra los
 * eventos actuales de la cuenta y lo registra como un movimiento visible. Si el cliente
 * mandara el delta, una vista desactualizada (la deuda se mueve a diario por intereses)
 * dejaría el saldo en otra cifra.
 */
@Serializable
data class AdjustCreditBalanceRequest(
    val targetBalance: Long,    // deuda real (en la moneda de la cuenta), >= 0
)

/**
 * Techo defensivo para la deuda objetivo de un crédito (COP). No es un límite de negocio:
 * atrapa el dedazo de teclear dígitos de más al copiar el saldo de la banca en línea.
 *
 * Vive en `:core` a propósito — el server lo aplica y la hoja de ajuste lo espeja para poder
 * explicar el rechazo *antes* de llamar, en vez de que el usuario reciba un error genérico.
 */
const val MAX_CREDIT_DEBT_COP = 1_000_000_000_000L // un billón de pesos

/**
 * F26: nace con el alta manual (nombre, objetivo, cuenta donde se ahorra, fecha opcional) — antes
 * el modelo existía pero no había forma de crear una. [saved] es SIEMPRE derivado del saldo de
 * [accountId] (ver `GET /api/goals` en `GoalRoutes.kt`), nunca un aporte manual: si la plata está
 * en la cuenta, cuenta. El cliente lo manda en 0 al crear/editar y el server lo ignora — el campo
 * solo tiene sentido en la respuesta.
 */
@Serializable
data class Goal(
    val id: String = "",
    val name: String,
    val target: Long,
    val accountId: String,
    val targetDate: String? = null,   // ISO "2027-01-01", opcional
    val saved: Long = 0,
)

@Serializable
data class RecurringRule(
    val id: String,
    val name: String,
    val category: String,
    val amount: Long,
    val dayOfMonth: Int,
    val type: TransactionType,
)

@Serializable
data class Budget(
    val category: String,
    val monthlyLimit: Long,
)

// F17: cuerpo de PUT /api/budgets/{category}/rename — la categoría vieja va en la URL, la
// nueva en el body. Tipo propio (no reusar Budget) porque el monto no se manda: el server
// conserva el límite existente, renombrar y cambiar el monto son dos operaciones separadas.
@Serializable
data class RenameBudgetRequest(
    val newCategory: String,
)

@Serializable
data class SmsMessage(
    val id: String,
    val time: String,
    val bank: String,
    val text: String,
    val state: String,
    val det: String,
)

@Serializable
data class ParsedSms(
    val amount: Double,
    val merchant: String,
    val type: TransactionType,
    val category: String,
)

@Serializable
enum class ChatRole { USER, ASSISTANT }

@Serializable
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    // F32: adjunto opcional — foto de un recibo, extracto u oferta del banco. Nulo en casi
    // todos los mensajes; el default mantiene compatibilidad de red con clientes viejos que
    // solo mandan role+content (ver ChatModelTest).
    val imageBase64: String? = null,
    val imageMime: String? = null,
)

@Serializable
data class AiChatRequest(val messages: List<ChatMessage>)

@Serializable
data class AiChatResponse(val text: String)

@Serializable
enum class PaymentStatus { OVERDUE, DUE_TODAY, DUE_SOON, UPCOMING }

@Serializable
data class UpcomingPayment(
    val rule: RecurringRule,
    val dueDate: String,    // ISO "2026-06-05", current month
    val daysUntil: Int,     // negative if overdue
    val status: PaymentStatus,
)
