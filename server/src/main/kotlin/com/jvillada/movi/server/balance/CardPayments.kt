package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY

/**
 * Frases que, en los extractos reales de Bancolombia, aparecen en la descripción del pago
 * del extracto de una tarjeta de crédito. Case-insensitive — se compara en minúsculas.
 */
private val CARD_PAYMENT_PATTERNS = listOf(
    "pago autom tc",
    "pago tarjeta",
    "pago tc ",
    "abono tarjeta",
    "pago a tarjeta",
)

/**
 * ¿[description] *parece* el pago del extracto de una tarjeta de crédito?
 *
 * Es una **propuesta**, no una clasificación: existe para que el dueño encuentre candidatos a
 * revisar, no para recategorizar nada solo. Por eso puede errar de más (un falso positivo se
 * descarta con un click) pero no puede errar de menos en el caso que importa: "PAGO QR ..." y
 * "PAGO PSE ..." son gastos reales en comercios — no tocan ninguno de los patrones de arriba —
 * y confundirlos con un pago de tarjeta borraría gasto real del mes.
 *
 * Si [category] ya es [CARD_PAYMENT_CATEGORY] no hay nada que proponer: el evento ya está bien.
 */
fun looksLikeCardPayment(description: String, category: String): Boolean {
    if (category == CARD_PAYMENT_CATEGORY) return false
    val normalized = description.lowercase()
    return CARD_PAYMENT_PATTERNS.any { it in normalized }
}
