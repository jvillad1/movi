package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * Alta de un **traspaso**: mover plata entre dos cuentas propias.
 *
 * El cliente genera los tres ids ([transferId], [fromEventId], [toEventId]) igual que ya genera
 * el id de un evento suelto (ver [newId]) — así el traspaso tiene identidad desde antes de tocar
 * ninguna base, y un reintento no puede duplicarlo. El server no inventa ninguno.
 *
 * Un solo POST, no dos: las dos patas se insertan en UNA transacción del server (precedente:
 * `POST /api/credits`, que crea cuenta + apertura + términos de una). Con dos POST sueltos, un
 * fallo entre medio dejaba medio traspaso — plata que salió de una cuenta y no entró en ninguna.
 */
@Serializable
data class CreateTransferRequest(
    val transferId: String,
    val fromEventId: String,
    val toEventId: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amount: Long,
    val timestamp: Long,
    /** Concepto opcional; se agrega a la descripción de las dos patas (ver [transferLegsFor]). */
    val note: String? = null,
)

/**
 * Lo que se le dice a alguien que intenta sacar una pata de [TRANSFER_CATEGORY].
 *
 * Vive acá, no en el handler ni en la hoja, porque hacen falta las mismas palabras en tres
 * lugares: el 422 de `PUT /api/events/{id}/category`, la guarda del espejo local (que responde
 * sin red) y la hoja de cambiar categoría, que lo muestra en vez de la lista.
 */
const val TRANSFER_RECATEGORIZE_BLOCKED =
    "Un traspaso no se puede recategorizar: es plata que se movió entre tus cuentas, no un gasto " +
        "ni un ingreso. Si te equivocaste, anúlalo y vuelve a hacerlo."

/** Lo que se le dice a alguien que intenta *entrar* a [TRANSFER_CATEGORY] recategorizando. */
const val TRANSFER_CATEGORY_RESERVED =
    "«Traspaso» es una categoría reservada: para mover plata entre tus cuentas usa Agregar → Traspaso."

/** Las dos patas que quedaron creadas, tal como el server las guardó. */
@Serializable
data class TransferResult(
    val from: FinancialEvent,
    val to: FinancialEvent,
)

/**
 * ¿Se puede hacer este traspaso? `null` si sí; si no, **el mensaje en español** que se le muestra
 * al dueño.
 *
 * Devuelve el texto y no un código a propósito: es la misma frase en los dos lugares donde hace
 * falta —la hoja de Agregar (que apaga el botón y explica por qué) y el 422 del server (última
 * línea de defensa para cualquier cliente viejo o para un POST a mano)—. Con un enum, las dos
 * puntas habrían escrito su propia versión del texto y se habrían ido separando.
 *
 * Las reglas, en orden:
 * - **Faltan cuentas.** No hay nada que validar todavía.
 * - **Origen ≠ destino.** Traspasar una cuenta a sí misma es un no-op con dos eventos de ruido.
 * - **Monto > 0.** Un traspaso de $0 no mueve nada; uno negativo es el traspaso al revés escrito
 *   mal, y aceptarlo sería adivinar la intención.
 * - **Nada del grupo DEUDA** (tarjeta o préstamo), ni como origen ni como destino. Pagar la
 *   tarjeta ya tiene su propio camino y su propia regla de flujo de caja
 *   ([CARD_PAYMENT_CATEGORY]); convertirlo en traspaso duplicaría esa lógica y le cambiaría el
 *   signo a la deuda. Los préstamos se manejan en Créditos.
 * - **Misma moneda.** Un traspaso entre monedas necesita una tasa y decidir cuál de los dos
 *   montos manda; hasta que eso exista, se dice que no en vez de inventar una conversión.
 */
fun validateTransfer(from: Account?, to: Account?, amount: Long): String? = when {
    from == null || to == null -> "Elige la cuenta de origen y la de destino"
    from.id == to.id -> "El origen y el destino tienen que ser cuentas distintas"
    amount <= 0L -> "El monto tiene que ser mayor que cero"
    from.type.group == AccountGroup.DEUDA || to.type.group == AccountGroup.DEUDA ->
        "Las tarjetas y los préstamos se manejan en Créditos, no con un traspaso"
    from.currency != to.currency -> "Por ahora solo entre cuentas de la misma moneda"
    else -> null
}

/**
 * Las dos patas de un traspaso: un EXPENSE en [from] y un INCOME en [to], enlazados por
 * [CreateTransferRequest.transferId] y los dos con la categoría reservada [TRANSFER_CATEGORY].
 *
 * **Por qué dos eventos normales y no un tipo de movimiento nuevo:** los saldos se derivan de
 * los eventos vía `signedDelta`/`computeBalances`, y así cada pata es un evento común y corriente
 * de su cuenta — esa derivación no cambia ni una línea, y el detalle de cada cuenta muestra su
 * pata con el signo correcto sin saber nada de traspasos. Lo único que hace falta enseñarle al
 * sistema es que este par no es flujo de caja, y para eso ya existía el mecanismo de la
 * categoría reservada ([isCashFlow]).
 *
 * Vive en `:core` (no en el server) para que el cliente y el server construyan las patas con la
 * misma función: la categoría, el signo y la descripción no pueden divergir entre el espejo
 * local y lo que quedó guardado.
 *
 * La descripción dice hacia dónde va la plata ("Traspaso a CDT" / "Traspaso desde Ahorros") en
 * vez de repetir "Traspaso" de los dos lados: en el detalle de UNA cuenta, que es donde se lee
 * la pata suelta, lo que falta saber es cuál es la otra punta. La nota, si la hay, se agrega
 * después de un separador — no reemplaza la descripción, porque perder la otra punta para
 * mostrar "alquiler" dejaría la pata sin contexto.
 */
fun transferLegsFor(
    request: CreateTransferRequest,
    from: Account,
    to: Account,
): Pair<FinancialEvent, FinancialEvent> {
    val note = request.note?.trim().orEmpty()
    fun describe(base: String) = if (note.isEmpty()) base else "$base · $note"
    fun leg(id: String, accountId: String, type: TransactionType, description: String) = FinancialEvent(
        id = id,
        accountId = accountId,
        type = type,
        amount = request.amount,
        currency = from.currency,
        category = TRANSFER_CATEGORY,
        description = description,
        timestamp = request.timestamp,
        source = EventSource.MANUAL,
        // Lo anotó el dueño con sus propios dedos: ya está confirmado, igual que lo que sale de
        // QuickAdd. "Por confirmar" es solo para lo que entra solo (SMS, OCR, extracto).
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        transferId = request.transferId,
        // Redundante con isCashFlow (el server la vuelve a derivar en cada lectura), pero deja
        // el objeto que devuelve esta función coherente consigo mismo desde el primer instante.
        countsAsCashFlow = false,
    )
    return leg(request.fromEventId, from.id, TransactionType.EXPENSE, describe("Traspaso a ${to.name}")) to
        leg(request.toEventId, to.id, TransactionType.INCOME, describe("Traspaso desde ${from.name}"))
}
