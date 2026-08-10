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
 * revisar en [CardPaymentCandidatesSheet][com.jvillada.movi.ui.transactions.CardPaymentCandidatesSheet],
 * no para recategorizar nada solo. Un falso positivo acá — marcar como candidato un gasto real
 * como "PAGO QR ..." o "PAGO PSE ..." — es el error caro: si el dueño lo confirma sin fijarse,
 * ese gasto real deja de contar en el mes (cae en la regla de [CARD_PAYMENT_CATEGORY] de
 * `isCashFlow`). Por eso la lista de patrones es angosta a propósito y ninguno de ellos toca
 * "PAGO QR"/"PAGO PSE". Un falso negativo (no detectar un pago de tarjeta real) es más barato:
 * el evento se queda sin marcar, contado como "Otros", corregible a mano después con
 * [com.jvillada.movi.ui.transactions.ChangeCategorySheet].
 *
 * Hoy **no hay forma de descartar** un candidato — la hoja solo tiene el botón "Marcar"
 * ([CardPaymentCandidatesSheet]) — así que un falso positivo simplemente se queda proponiéndose
 * cada vez que se abre la hoja, hasta que el dueño lo confirme (equivocadamente) o hasta que
 * alguien construya el descarte. Es una decisión de producto pendiente, no un bug de esta
 * función: no se resuelve acá.
 *
 * Si [category] ya es [CARD_PAYMENT_CATEGORY] no hay nada que proponer: el evento ya está bien.
 */
fun looksLikeCardPayment(description: String, category: String): Boolean {
    if (category == CARD_PAYMENT_CATEGORY) return false
    val normalized = description.lowercase()
    return CARD_PAYMENT_PATTERNS.any { it in normalized }
}
