package com.jvillada.movi.shared.model

/*
 * # Lo que el banco cobra solo
 *
 * Algunas cuotas de crédito (y algunos recurrentes: un seguro, una suscripción) **se debitan solas**
 * de una cuenta del dueño, y el banco no manda ningún aviso cuando las cobra. La auditoría de la
 * ingesta (4-oct-2026, causa D4) contó cinco cargadas a mano en 45 días: Libre inversión 9695
 * ($1.204.064), Crediágil 3090 ($26.485) y Techo Gardenera ($474.240).
 *
 * El dueño marca qué se debita solo y de qué cuenta ([CreditTerms.debitoAutomaticoDesde]); el día del
 * vencimiento, si ningún movimiento lo prueba, Movi lo propone armado en «Por revisar». **Nunca lo
 * anota solo**: el checklist lo tilda el movimiento, y el movimiento lo confirma el dueño.
 */

/** Ver [validarDebitoAutomatico]. Las tres respuestas a «¿esta cuota sale de tu cuenta?» se excluyen. */
const val DEBITO_CON_LIBRANZA: String =
    "Una cuota que se descuenta de tu nómina no se debita de una cuenta. Desmarca una de las dos."

/** Ver [validarDebitoAutomatico]. */
const val DEBITO_CON_TERCERO: String =
    "Una cuota que paga otra persona no se debita de tu cuenta. Borra quién la paga o quita el débito."

/** Ver [validarDebitoAutomatico]. */
const val DEBITO_SIN_CUENTA: String = "Elige de qué cuenta la cobra el banco."

/** Ver [validarDebitoAutomatico]. */
const val DEBITO_DESDE_UNA_DEUDA: String =
    "El banco cobra la cuota de una cuenta de dinero, no de otra deuda."

/** Ver [validarDebitoAutomatico]. */
const val DEBITO_EN_OTRA_MONEDA: String =
    "La cuenta del débito tiene que estar en la misma moneda que el crédito."

/**
 * ¿Se puede guardar este débito automático? `null` si sí (o si no hay débito); si no, **el motivo en
 * español**, que es lo que dicen la hoja del crédito y el 400 del server.
 *
 * Mismo idioma que [validarTasaDelCredito]: una sola función para la hoja y para `POST`/`PUT
 * /api/credits`, así crear y editar validan igual y un cliente viejo o un cuerpo escrito a mano
 * tropiezan con la misma frase.
 *
 * - **Libranza o «la paga otro»**: las dos dicen que la cuota no sale de una cuenta del dueño; el
 *   débito dice de cuál sale. Juntas son dos respuestas a la misma pregunta.
 * - **La cuenta no existe o no es suya** ([cuenta] en `null`).
 * - **La cuenta es otra deuda**: el banco no debita una cuota de una tarjeta ni de otro crédito, y
 *   `POST /api/payments/installment` —el camino con que se confirma— lo rechazaría igual.
 * - **Otra moneda**: el pago de una cuota no cruza monedas (ver [validarPagoDeCuota]).
 */
fun validarDebitoAutomatico(terms: CreditTerms, cuenta: Account?, monedaDelCredito: String): String? {
    if (terms.debitoAutomaticoDesde.isNullOrBlank()) return null
    return when {
        terms.payrollDeduction -> DEBITO_CON_LIBRANZA
        !terms.paidBy.isNullOrBlank() -> DEBITO_CON_TERCERO
        cuenta == null -> DEBITO_SIN_CUENTA
        cuenta.type.group == AccountGroup.DEUDA -> DEBITO_DESDE_UNA_DEUDA
        cuenta.currency != monedaDelCredito -> DEBITO_EN_OTRA_MONEDA
        else -> null
    }
}

/**
 * Las cuentas de las que el banco puede debitar una cuota en [moneda]: las que no son deuda, en esa
 * moneda. Es la lista que ofrece la hoja del crédito; la misma regla que [validarDebitoAutomatico],
 * para no ofrecer una cuenta y después decir que no se puede.
 */
fun cuentasParaElDebito(cuentas: List<Account>, moneda: String = "COP"): List<Account> =
    cuentas.filter { it.type.group != AccountGroup.DEUDA && it.currency == moneda }

/** Ver [validarDebitoDeLaRegla]. */
const val DEBITO_DE_REGLA_SIN_CUENTA: String = "Para que Movi te proponga el débito, elige de qué cuenta sale."

/** Ver [validarDebitoDeLaRegla]. */
const val DEBITO_DE_UN_INGRESO: String = "Solo un gasto se debita solo de tu cuenta."

/**
 * ¿Se puede marcar esta regla recurrente como «se debita sola»? `null` si sí (o si no se marca).
 * Solo un gasto, y con la cuenta de la que la cobra el banco: sin cuenta no hay qué proponer.
 */
fun validarDebitoDeLaRegla(seDebitaSolo: Boolean, tipo: TransactionType, cuentaId: String?): String? = when {
    !seDebitaSolo -> null
    tipo != TransactionType.EXPENSE -> DEBITO_DE_UN_INGRESO
    cuentaId.isNullOrBlank() -> DEBITO_DE_REGLA_SIN_CUENTA
    else -> null
}

// ── La propuesta en «Por revisar» ────────────────────────────────────────────

/** De dónde sale la propuesta: la cuota de un crédito, o una regla recurrente común. */
@kotlinx.serialization.Serializable
enum class OrigenDelDebito { CUOTA_DE_CREDITO, RECURRENTE }

/**
 * **Una cuota (o un recurrente) que el banco debita solo, vencida y sin movimiento que la pruebe.**
 * La arma `GET /api/debitos-automaticos` y la pinta «Por revisar» como una tarjeta lista para
 * confirmar: «Débito automático: Cuota Libre inversión 9695 · $1.204.064 desde Bancolombia Ahorros
 * — ¿se cobró?».
 *
 * ## Dónde vive, y por qué no es un aviso del banco
 *
 * Se **deriva en cada lectura** —igual que el checklist del período— y no se escribe en ningún lado
 * hasta que el dueño decide. No es una fila de `sms_messages` a propósito: un aviso sintético con
 * origen «Movi» heredaría la bandeja, pero también se haría pasar por algo que el banco dijo, y el
 * banco justamente no dijo nada. Contaría como captura para «banco mudo» (`soloLoQueLlegoSolo`) y se
 * agruparía con los avisos de verdad como si fuera uno más. Derivado, desaparece solo:
 *
 * - cuando hay un movimiento que salda ese vencimiento (la cuota de dos patas, o el pago del
 *   recurrente) — incluido el que el dueño anote a mano o confirme desde un aviso que llegó tarde;
 * - cuando el dueño dice «No se cobró» (queda en `debitos_automaticos_descartados` para ese período);
 * - cuando hay un aviso del banco pendiente por el mismo monto: el aviso es mejor evidencia, y la
 *   bandeja no muestra dos tarjetas para el mismo pago.
 *
 * ## Los ids
 *
 * [pataDelDineroId], [pataDeLaDeudaId] y [transferId] los pone el server, **deterministas por
 * (regla, período)**: confirmar dos veces —un doble toque, una respuesta perdida— manda los mismos
 * ids y `POST /api/debitos-automaticos/confirmar` contesta lo que ya quedó guardado en vez de
 * duplicar. Si un pago anterior con esos ids se anuló, el server elige los siguientes libres.
 *
 * @property periodo el `"YYYY-MM"` del vencimiento (el sello del período, `OccurrenceState.period`).
 * @property vence el día del vencimiento, ISO. Es la fecha con que se anota el movimiento: el banco
 *   debita ese día, aunque el dueño lo confirme después.
 * @property deudaId la cuenta del crédito, solo en [OrigenDelDebito.CUOTA_DE_CREDITO].
 */
@kotlinx.serialization.Serializable
data class DebitoAutomaticoPorConfirmar(
    val ruleId: String,
    val periodo: String,
    val origen: OrigenDelDebito,
    val nombre: String,
    val monto: Long,
    val moneda: String = "COP",
    val vence: String,
    val cuentaId: String,
    val cuentaNombre: String,
    val categoria: String,
    val pataDelDineroId: String,
    val deudaId: String? = null,
    val pataDeLaDeudaId: String? = null,
    val transferId: String? = null,
) {
    /** La llave de la tarjeta en la bandeja: una por regla y período. */
    val clave: String get() = "$ruleId@$periodo"
}

/** La nota con que queda anotado lo que se confirmó desde la propuesta: dice de dónde salió. */
const val NOTA_DEL_DEBITO_AUTOMATICO: String = "Débito automático"

/** «Débito automático: Cuota Libre inversión 9695 · $1.204.064 desde Bancolombia Ahorros — ¿se cobró?» */
fun textoDelDebitoAutomatico(debito: DebitoAutomaticoPorConfirmar): String =
    "$NOTA_DEL_DEBITO_AUTOMATICO: ${debito.nombre} · ${montoDelDebito(debito.monto, debito.moneda)} " +
        "desde ${debito.cuentaNombre} — ¿se cobró?"

/** El monto con su símbolo: pesos con «$», cualquier otra moneda con su código adelante. */
fun montoDelDebito(monto: Long, moneda: String): String =
    if (moneda == "COP") "$${conPuntosDeMiles(monto)}" else "$moneda ${conPuntosDeMiles(monto)}"

/**
 * Cuerpo de `POST /api/debitos-automaticos/confirmar`: **«Sí, se cobró»**, con el [monto] que el dueño
 * confirmó («Cambiar monto»: la cuota puede variar) y los ids que trajo la propuesta.
 *
 * - **La cuota de un crédito**: el server escribe las dos patas con la función de la confirmación de un
 *   aviso de dos patas (`escribirLasPatasDelAviso`): [eventoId] es la pata del dinero,
 *   [pataDeLaDeudaId] la de la deuda, [transferId] las enlaza.
 * - **Un recurrente**: [eventoId] es el gasto, y el server sella el período con él.
 *
 * Con los mismos ids, confirmar dos veces devuelve lo que ya quedó.
 */
@kotlinx.serialization.Serializable
data class ConfirmarDebitoAutomatico(
    val ruleId: String,
    val periodo: String,
    val monto: Long,
    val eventoId: String,
    val transferId: String? = null,
    val pataDeLaDeudaId: String? = null,
)

/** El pedido que confirma [debito] con [monto]: los ids son los de la propuesta, nunca unos nuevos. */
fun confirmacionDelDebito(debito: DebitoAutomaticoPorConfirmar, monto: Long): ConfirmarDebitoAutomatico =
    ConfirmarDebitoAutomatico(
        ruleId = debito.ruleId,
        periodo = debito.periodo,
        monto = monto,
        eventoId = debito.pataDelDineroId,
        transferId = debito.transferId,
        pataDeLaDeudaId = debito.pataDeLaDeudaId,
    )

/** Cuerpo de `POST /api/debitos-automaticos/descartar`: «No se cobró» para ese vencimiento. */
@kotlinx.serialization.Serializable
data class DescartarDebitoAutomatico(val ruleId: String, val periodo: String)
