package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * La categoría de una cuota de crédito pagada desde una cuenta de dinero.
 *
 * **No es reservada, y eso es la decisión entera.** El dueño eligió que la cuota cuente en sus
 * «Gastos del mes»: *«sí, es plata que salió»*. Antes, registrar la cuota del carro como traspaso
 * la sacaba del mes —los dos lados excluidos— y sus gastos quedaban $4.215.223 por debajo de lo
 * real, con el «Flujo del mes» viéndose mejor de lo que era.
 *
 * Que sea una categoría normal es lo que la hace contar. Su contraparte —la pata que baja la
 * deuda— vive en una cuenta LOAN, que [isCashFlow] excluye por tipo, así que no hay doble conteo.
 */
const val CUOTA_CATEGORY = "Cuota de crédito"

/**
 * Pagar la cuota de un crédito, o el extracto de una tarjeta, en **una sola acción**.
 *
 * ### El problema que resuelve
 *
 * Hasta acá la misma acción tenía dos mecánicas distintas y ninguna completa:
 *
 * - **Un crédito** se pagaba con un traspaso. La deuda bajaba, pero la cuota no aparecía en los
 *   gastos del mes.
 * - **Una tarjeta** no admitía traspaso (ver [TRANSFER_CARD_BLOCKED]): se anotaba como un gasto
 *   con la categoría «Pago de tarjeta» y **la deuda quedaba igual**. Y una tarjeta ni siquiera
 *   tiene «Ajustar saldo», que sí tienen los créditos — justo la que más cambia de saldo.
 *
 * El dueño lo pidió así: *«necesito poder agregar un tipo de movimiento que sea pago de cuota, que
 * pueda asociar a un crédito o tarjeta, y que vos sepas cómo manejarlo por debajo»*.
 *
 * ### Las dos patas, y por qué sus categorías son distintas
 *
 * Siempre se escriben dos eventos enlazados por [transferId], igual que un traspaso: un EXPENSE en
 * la cuenta de donde sale la plata y un INCOME en la deuda, que la baja vía `signedDelta`.
 *
 * Lo que cambia según **qué** se paga es la categoría de la pata del dinero:
 *
 * - **Crédito** → [CUOTA_CATEGORY], que es una categoría normal y **cuenta** en el mes.
 * - **Tarjeta** → [CARD_PAYMENT_CATEGORY], reservada y excluida. Las compras ya contaron cuando se
 *   hicieron; contar también el pago sería contar la misma plata dos veces.
 *
 * Esa asimetría no es un capricho: un crédito nunca pasó por «Gastos del mes» (el desembolso entró
 * como deuda, no como consumo), así que su cuota es la primera y única vez que esa plata se ve
 * salir. La compra con tarjeta ya se vio.
 */
@Serializable
data class CreatePagoDeCuotaRequest(
    /** De dónde sale la plata: una cuenta de dinero o inversión, nunca otra deuda. */
    val fromAccountId: String,
    /** Qué se paga: una cuenta LOAN o CREDIT_CARD. */
    val debtAccountId: String,
    val amount: Long,
    val timestamp: Long,
    val note: String? = null,
    /**
     * Los tres ids los genera el CLIENTE, por lo mismo que en un traspaso: hacen la operación
     * idempotente si la petición se reintenta, en vez de dejar dos pagos donde hubo uno.
     */
    val transferId: String,
    val fromEventId: String,
    val toEventId: String,
)

/** Lo que se le dice a quien intenta pagar desde una deuda. */
const val PAGO_DESDE_DEUDA_BLOQUEADO =
    "La plata para pagar una cuota sale de una cuenta tuya, no de otra deuda."

/** Lo que se le dice a quien elige algo que no es un crédito ni una tarjeta. */
const val PAGO_A_NO_DEUDA_BLOQUEADO =
    "Elige el crédito o la tarjeta que estás pagando."

/** Lo que se le dice a quien intenta pagar entre monedas distintas. */
const val PAGO_MONEDAS_DISTINTAS =
    "La cuenta y la deuda están en monedas distintas. Anota el pago en la moneda de la deuda."

/**
 * ¿Se puede registrar este pago? Devuelve el motivo, o `null` si está bien.
 *
 * Función pura y con pruebas porque decide sobre plata: un pago mal armado deja la deuda o el
 * saldo mintiendo, y los dos son números que el dueño usa para decidir.
 */
fun validarPagoDeCuota(request: CreatePagoDeCuotaRequest, from: Account?, debt: Account?): String? = when {
    from == null || debt == null -> "Elige de dónde sale la plata y qué estás pagando"
    request.amount <= 0L -> "El monto tiene que ser mayor que cero"
    from.id == debt.id -> PAGO_A_NO_DEUDA_BLOQUEADO
    from.type.group == AccountGroup.DEUDA -> PAGO_DESDE_DEUDA_BLOQUEADO
    debt.type != AccountType.LOAN && debt.type != AccountType.CREDIT_CARD -> PAGO_A_NO_DEUDA_BLOQUEADO
    // Sin conversión automática: mezclar monedas acá haría que el saldo de una de las dos cuentas
    // quedara mal por el tipo de cambio del día, en silencio. La tarjeta en dólares del dueño se
    // paga con la cuenta en dólares, o se anota aparte.
    from.currency != debt.currency -> PAGO_MONEDAS_DISTINTAS
    else -> null
}

/**
 * Las dos patas del pago. Ver el KDoc de [CreatePagoDeCuotaRequest] para el porqué de cada
 * categoría.
 */
fun pagoDeCuotaLegs(
    request: CreatePagoDeCuotaRequest,
    from: Account,
    debt: Account,
): Pair<FinancialEvent, FinancialEvent> {
    val esTarjeta = debt.type == AccountType.CREDIT_CARD
    val categoriaDelDinero = if (esTarjeta) CARD_PAYMENT_CATEGORY else CUOTA_CATEGORY
    val nota = request.note?.trim().orEmpty()
    fun describir(base: String) = if (nota.isEmpty()) base else "$base · $nota"

    fun pata(id: String, accountId: String, tipo: TransactionType, categoria: String, texto: String) =
        FinancialEvent(
            id = id,
            accountId = accountId,
            type = tipo,
            amount = request.amount,
            currency = debt.currency,
            category = categoria,
            description = texto,
            timestamp = request.timestamp,
            source = EventSource.MANUAL,
            // Lo anotó el dueño con sus propios dedos. «Por confirmar» es para lo que entra solo.
            reconciliationStatus = ReconciliationStatus.RECONCILED,
            transferId = request.transferId,
            // Redundante con `isCashFlow` —el server la vuelve a derivar en cada lectura— pero
            // deja el objeto coherente consigo mismo desde el primer instante, igual que
            // `transferLegsFor`. Sin esto las dos patas salían con el default `true`, y la
            // respuesta del server afirmaba que el pago de una tarjeta cuenta en el mes.
            countsAsCashFlow = isCashFlow(
                accountType = if (accountId == debt.id) debt.type else from.type,
                type = tipo,
                category = categoria,
            ),
        )

    return pata(
        request.fromEventId, from.id, TransactionType.EXPENSE, categoriaDelDinero,
        describir(if (esTarjeta) "Pago de ${debt.name}" else "Cuota de ${debt.name}"),
    ) to pata(
        // La pata de la deuda lleva la MISMA categoría que la del dinero, y da igual cuál sea:
        // vive en una cuenta LOAN o CREDIT_CARD, que `isCashFlow` excluye por tipo de cuenta. Se
        // usa la misma para que las dos filas se lean como lo que son, una sola operación.
        request.toEventId, debt.id, TransactionType.INCOME, categoriaDelDinero,
        describir("Pago desde ${from.name}"),
    )
}

/** Lo que la app necesita para pintar el resultado sin volver a preguntar. */
@Serializable
data class PagoDeCuotaResult(
    /** Cuánto queda debiendo después del pago — el número que el dueño vino a ver bajar. */
    val deudaRestante: Long,
    val patas: List<FinancialEvent>,
)
