package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * F20 — términos de una tarjeta de crédito (1:1 con su cuenta CREDIT_CARD), el equivalente de
 * [CreditTerms] para tarjetas. Son cosas distintas a propósito: una tarjeta no tiene «capital
 * original», tasa contractual ni plazo — tiene cupo, día de corte y día de pago. Meterlas en la
 * misma tabla habría llenado credit_terms de columnas nullable que mienten sobre qué es cada fila.
 *
 * `creditLimit` y `cutoffDay` son opcionales: no todo el mundo se sabe su cupo o su corte de
 * memoria, y exigirlos dejaría tarjetas sin registrar. `paymentDay` sí es obligatorio — es lo
 * que alimenta el recordatorio de pago, la razón de existir de esta tabla.
 *
 * Montos en la moneda de la cuenta (las Mastercard en USD existen): el cupo de una tarjeta USD
 * es un número en USD, igual que su deuda derivada.
 */
@Serializable
data class CardTerms(
    val accountId: String,
    val bank: String,
    val creditLimit: Long? = null,  // cupo total, en la moneda de la cuenta
    val cutoffDay: Int? = null,     // día de corte (1–31)
    val paymentDay: Int,            // día límite de pago (1–31)
    val notes: String? = null,
    /**
     * Ver [com.jvillada.movi.shared.model.RecurringRule.remindMe]: el pago de esta tarjeta entra
     * (o no) al barrido de avisos. Default `true` — las tarjetas que ya existían siguen avisando.
     */
    val remindMe: Boolean = true,
)

/**
 * Una tarjeta como la lista `GET /api/cards`: la cuenta CREDIT_CARD con su deuda derivada de
 * eventos, los términos si ya los tiene, y el cupo disponible cuando hay cupo declarado.
 */
@Serializable
data class CardSummary(
    val account: Account,     // cuenta CREDIT_CARD con deuda derivada en balance
    val terms: CardTerms?,    // null si la tarjeta aún no tiene términos (creada desde Cuentas)
    /**
     * Cupo − deuda, en la moneda de la cuenta; null sin cupo declarado. Puede ser negativo
     * (deuda por encima del cupo): se devuelve tal cual — recortarlo a 0 sería un número que
     * miente sobre un sobregiro real.
     */
    val available: Long? = null,
)

/**
 * Prefijo de los ids de las reglas recurrentes sintéticas derivadas de card_terms — el
 * equivalente de [CREDIT_RULE_PREFIX] para tarjetas. Compartido entre el server (que las
 * genera) y la UI (que las distingue de las reglas reales editables).
 */
const val CARD_RULE_PREFIX = "card_"

/**
 * Alta atómica de una tarjeta: cuenta CREDIT_CARD + evento de deuda inicial (si la hay) +
 * términos en una sola operación server-side — mismo patrón que [CreateCreditRequest].
 *
 * A diferencia de un préstamo (que sin deuda no existe), una tarjeta recién sacada puede estar
 * en $0: `initialDebt = 0` es válido y simplemente no genera evento de apertura.
 */
@Serializable
data class CreateCardRequest(
    val name: String,           // nombre de la cuenta CREDIT_CARD a crear
    val initialDebt: Long = 0,  // deuda actual; 0 = tarjeta al día, sin evento de apertura
    val currency: String = "COP",
    val terms: CardTerms,       // accountId se ignora; el server asigna el de la cuenta nueva
)
