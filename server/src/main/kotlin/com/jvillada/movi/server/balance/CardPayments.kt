package com.jvillada.movi.server.balance

import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll

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
 * Un falso positivo ya no se queda proponiéndose para siempre: la hoja tiene un segundo botón,
 * "No es" ([CardPaymentCandidatesSheet]), que llama a `POST /api/events/{id}/not-card-payment`
 * ([com.jvillada.movi.server.routes.eventRoutes]) y descarta el candidato de forma persistente
 * sin tocar su categoría. El GET de arriba filtra por [dismissedCardPaymentEventIds], así que un
 * evento descartado no vuelve a aparecer. No hay "restaurar" ese descarte: si fue un error, el
 * movimiento sigue en Movimientos y se recategoriza a mano desde ahí
 * ([com.jvillada.movi.ui.transactions.ChangeCategorySheet]).
 *
 * Si [category] ya es [CARD_PAYMENT_CATEGORY] no hay nada que proponer: el evento ya está bien.
 *
 * Y una **pata de traspaso** ([TRANSFER_CATEGORY]) tampoco se propone nunca, aunque su
 * descripción matchee: la nota que el dueño le escribió al traspaso viaja pegada a la descripción
 * ("Traspaso a Nequi · pago tarjeta"), así que era alcanzable sin mala fe. Proponerla era ofrecer
 * un botón que no lleva a ningún lado: confirmarla llama a `PUT /api/events/{id}/category`, y esa
 * ruta rechaza tocar una pata de traspaso con un 422 (TRANSFER_RECATEGORIZE_BLOCKED). El dueño
 * veía un candidato que la app no se deja arreglar.
 */
fun looksLikeCardPayment(description: String, category: String): Boolean {
    if (category == CARD_PAYMENT_CATEGORY) return false
    if (category == TRANSFER_CATEGORY) return false
    val normalized = description.lowercase()
    return CARD_PAYMENT_PATTERNS.any { it in normalized }
}

/**
 * Ids de eventos que [uid] ya descartó como "No es" un pago de tarjeta (ver
 * [CardPaymentDismissals]). El GET de candidatos resta este conjunto — es lo único que hace que
 * el botón "No es" signifique algo: sin este filtro el candidato volvería a proponerse la
 * próxima vez que se abriera la hoja.
 */
fun Transaction.dismissedCardPaymentEventIds(uid: String): Set<String> =
    CardPaymentDismissals.selectAll()
        .where { CardPaymentDismissals.userId eq uid }
        .map { it[CardPaymentDismissals.eventId] }
        .toSet()
