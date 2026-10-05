package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # Al confirmar un aviso, Movi arma solo las dos patas
 *
 * Arreglo 1 de la auditoría de la ingesta (4-oct-2026). En 45 días el dueño —o Claude por SQL—
 * armó a mano 24 «segundas patas»: el pago de la Master Black desde Ahorros, el retiro de la
 * Fiducuenta hacia Ahorros, el avance de la AMEX, la cuota de un crédito. El aviso del banco ya
 * trae los dos lados («Pagaste $386.902 en la tarjeta de crédito *3684 desde la cuenta *8133»);
 * confirmar creaba solo uno, y el otro quedaba para después.
 *
 * Esto es lo que cliente y server comparten: qué operación es, qué viaja y qué patas salen. Las
 * patas las escribe el server en una sola transacción (`LasDosPatasDelAviso.kt` del server), con
 * las mismas funciones de siempre: [transferLegsFor] para un traspaso, [pagoDeCuotaLegs] para el
 * pago de una tarjeta o la cuota de un crédito, y [patasDelAvance] para el avance, que es lo único
 * que no tenía función propia.
 */

/** La categoría que `parseSms` le pone al avance de una tarjeta («Hiciste un avance de $6,200,000 … desde tu T.Credito *9208 a la cuenta *8133»). */
const val AVANCE_DE_TARJETA_CATEGORY = "Avance de tarjeta"

/**
 * **Qué hecho de dos patas avisa el banco.** Lo decide el tipo de las dos cuentas, igual que
 * [transferKindFor]; viaja escrito para que el server pueda decir que no si la app y las cuentas no
 * dicen lo mismo (ver [validarDosPatas]).
 */
@Serializable
enum class OperacionDelAviso {
    /** De una cuenta de dinero a una tarjeta suya: sale con «Pago de tarjeta», entra como abono. */
    PAGO_DE_TARJETA,

    /** De una cuenta de dinero a un crédito suyo: sale con «Cuota de crédito», baja el capital. */
    CUOTA,

    /** Entre dos cuentas suyas de dinero o inversión: «Traspaso» en las dos. */
    TRASPASO,

    /**
     * De una tarjeta a una cuenta suya: la plata que entra cuenta como desembolso (decisión del
     * dueño, ver [DESEMBOLSO_CATEGORY]) y la deuda de la tarjeta sube como traspaso.
     */
    AVANCE,
}

/**
 * **La propuesta confirmada de un aviso de dos patas**: el cuerpo de `POST /api/sms/{id}/confirm` y
 * el campo [ConfirmarElMismoPago.patas]. Los tres ids los genera el CLIENTE, como en un traspaso o un
 * pago de cuota: un reintento con los mismos ids no crea nada nuevo.
 *
 * @property origenId de dónde sale la plata: la cuenta de dinero, o la tarjeta en un [OperacionDelAviso.AVANCE].
 * @property destinoId a dónde entra: la tarjeta o el crédito que se paga, la cuenta del traspaso, o la
 *   cuenta a la que llegó el avance.
 * @property monto lo que salió de [origenId], en su moneda.
 * @property montoEnLaMonedaDeLaDeuda solo para pagar una tarjeta en otra moneda (ver
 *   [CreatePagoDeCuotaRequest.montoEnLaMonedaDeLaDeuda]). Si no viene, el server lo convierte con la
 *   TRM del día, igual que `vincular-deuda`.
 */
@Serializable
data class DosPatasDelAviso(
    val operacion: OperacionDelAviso,
    val origenId: String,
    val destinoId: String,
    val monto: Long,
    val timestamp: Long,
    val transferId: String,
    val origenEventId: String,
    val destinoEventId: String,
    val nota: String? = null,
    val montoEnLaMonedaDeLaDeuda: Long? = null,
    val interesReal: Long? = null,
)

/**
 * **El id de la pata que queda enlazada al aviso** (`sms_messages.evento_id`): la de la cuenta de
 * dinero, que es el movimiento que el aviso describe. En un avance es la que ENTRA a la cuenta; en lo
 * demás, la que sale de ella.
 */
val DosPatasDelAviso.pataDelAviso: String
    get() = if (operacion == OperacionDelAviso.AVANCE) destinoEventId else origenEventId

/**
 * **¿Qué operación de dos patas admite este par de cuentas?** `null` si ninguna: dos deudas, la misma
 * cuenta, un bien (la casa no recibe plata de un aviso) o un crédito como origen (un desembolso no
 * llega por un aviso de estos; se anota desde Agregar).
 */
fun operacionEntre(origen: Account, destino: Account): OperacionDelAviso? {
    if (origen.id == destino.id || origen.esBien || destino.esBien) return null
    val origenEsDeuda = origen.type.group == AccountGroup.DEUDA
    return when {
        origen.type == AccountType.CREDIT_CARD && destino.type.group != AccountGroup.DEUDA -> OperacionDelAviso.AVANCE
        origenEsDeuda -> null
        destino.type == AccountType.CREDIT_CARD -> OperacionDelAviso.PAGO_DE_TARJETA
        destino.type == AccountType.LOAN -> OperacionDelAviso.CUOTA
        else -> OperacionDelAviso.TRASPASO
    }
}

/** Lo que se dice cuando la operación que pide la app no es la que dicen las cuentas. */
const val DOS_PATAS_NO_ENCAJAN =
    "Esas dos cuentas no sirven para esta operación. Revisa de dónde sale y a dónde entra la plata."

/**
 * **¿Se pueden escribir estas dos patas?** `null` si sí; si no, el motivo en palabras del dueño. Las
 * reglas de cada operación son las de su camino de siempre, no unas nuevas: [validateTransfer] para el
 * traspaso, [validarPagoDeCuota] para el pago y la cuota, y para el avance las mismas que un traspaso
 * salvo la tarjeta, que acá es justamente el origen.
 */
fun validarDosPatas(patas: DosPatasDelAviso, origen: Account?, destino: Account?): String? {
    if (origen == null || destino == null) return "Elige de dónde sale la plata y a dónde entra"
    if (patas.monto <= 0L) return "El monto tiene que ser mayor que cero"
    if (operacionEntre(origen, destino) != patas.operacion) return DOS_PATAS_NO_ENCAJAN
    return when (patas.operacion) {
        OperacionDelAviso.TRASPASO -> validateTransfer(origen, destino, patas.monto)
        OperacionDelAviso.AVANCE ->
            if (origen.currency != destino.currency) "Por ahora solo entre cuentas de la misma moneda" else null
        OperacionDelAviso.PAGO_DE_TARJETA, OperacionDelAviso.CUOTA ->
            validarPagoDeCuota(pedidoDePago(patas), origen, destino)
    }
}

/** El pago de cuota que estas patas son, para pasarlo por [validarPagoDeCuota] y [pagoDeCuotaLegs]. */
fun pedidoDePago(patas: DosPatasDelAviso): CreatePagoDeCuotaRequest = CreatePagoDeCuotaRequest(
    fromAccountId = patas.origenId,
    debtAccountId = patas.destinoId,
    amount = patas.monto,
    timestamp = patas.timestamp,
    note = patas.nota,
    transferId = patas.transferId,
    fromEventId = patas.origenEventId,
    toEventId = patas.destinoEventId,
    montoEnLaMonedaDeLaDeuda = patas.montoEnLaMonedaDeLaDeuda,
    interesReal = patas.interesReal,
)

/** El traspaso que estas patas son, para pasarlo por [transferLegsFor]. */
fun pedidoDeTraspaso(patas: DosPatasDelAviso): CreateTransferRequest = CreateTransferRequest(
    transferId = patas.transferId,
    fromEventId = patas.origenEventId,
    toEventId = patas.destinoEventId,
    fromAccountId = patas.origenId,
    toAccountId = patas.destinoId,
    amount = patas.monto,
    timestamp = patas.timestamp,
    note = patas.nota,
)

/**
 * **Las dos patas de un avance**: la tarjeta sube su deuda y la cuenta recibe la plata. Así lo anotó
 * el dueño el 3-oct (`ev_avance_amex_*`), y así quedan:
 *
 * - **La tarjeta: EXPENSE con [TRANSFER_CATEGORY].** Con [DESEMBOLSO_CATEGORY] contaría como gasto,
 *   porque en una tarjeta todo EXPENSE es flujo ([isCashFlow]); con «Traspaso» la deuda sube y el mes
 *   no se entera.
 * - **La cuenta: INCOME con [DESEMBOLSO_CATEGORY].** Es plata prestada que entró, y el dueño decidió
 *   que eso cuenta en «Entró» (ver [DESEMBOLSO_CATEGORY]), igual que el desembolso de un crédito.
 *
 * Primero la de la tarjeta (el origen), después la de la cuenta: el mismo orden que [transferLegsFor].
 */
fun patasDelAvance(patas: DosPatasDelAviso, tarjeta: Account, cuenta: Account): Pair<FinancialEvent, FinancialEvent> {
    val nota = patas.nota?.trim().orEmpty()
    fun describir(base: String) = if (nota.isEmpty()) base else "$base · $nota"
    fun pata(id: String, en: Account, tipo: TransactionType, categoria: String, texto: String) = FinancialEvent(
        id = id,
        accountId = en.id,
        type = tipo,
        amount = patas.monto,
        currency = en.currency,
        category = categoria,
        description = describir(texto),
        timestamp = patas.timestamp,
        source = EventSource.MANUAL,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        transferId = patas.transferId,
        countsAsCashFlow = isCashFlow(en.type, tipo, categoria),
    )
    return pata(patas.origenEventId, tarjeta, TransactionType.EXPENSE, TRANSFER_CATEGORY, "Avance a ${cuenta.name}") to
        pata(patas.destinoEventId, cuenta, TransactionType.INCOME, DESEMBOLSO_CATEGORY, "Avance desde ${tarjeta.name}")
}

/**
 * **Lo que se va a crear, dicho antes de confirmar**: «Sale de Bancolombia Ahorros · entra a Master
 * Black 3684 como pago». Lo muestra la tarjeta de Reconciliar.
 */
fun resumenDeLasDosPatas(operacion: OperacionDelAviso, origen: Account, destino: Account): String = when (operacion) {
    OperacionDelAviso.PAGO_DE_TARJETA -> "Sale de ${origen.name} · entra a ${destino.name} como pago"
    OperacionDelAviso.CUOTA -> "Sale de ${origen.name} · entra a ${destino.name} como cuota"
    OperacionDelAviso.TRASPASO -> "Sale de ${origen.name} · entra a ${destino.name} como traspaso"
    OperacionDelAviso.AVANCE -> "Sale de ${origen.name} como avance · entra a ${destino.name}"
}
