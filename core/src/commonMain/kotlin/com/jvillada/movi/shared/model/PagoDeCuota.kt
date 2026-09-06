package com.jvillada.movi.shared.model

import kotlin.math.pow
import kotlin.math.round
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
 * **Las dos NO valen lo mismo, y eso es deliberado**: en un crédito que amortiza, la pata de la
 * deuda vale solo el **capital** de la cuota. Ver [DesgloseDeCuota] para el porqué y para el
 * tamaño del error que corrige. La pata del dinero sigue por el monto completo.
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
    /**
     * **El interés que el banco cobró de verdad en esta cuota, si el dueño lo tiene a mano.**
     *
     * ### Por qué existe
     *
     * La estimación ([desglosarCuota]) sale de `saldo × tasa mensual`, y contra el extracto se
     * queda corta **por mucho**: en el Libre inversión ·9695 (saldo $40.710.555, 11,27 % E.A.)
     * Movi estimó $363.905 de interés y el banco cobró **$473.227**. Son $109.322 en UNA cuota,
     * siempre en la misma dirección —la deuda baja de más— y con seis créditos con tasa el
     * error se apila todos los meses. El dueño ya lo corrigió dos veces a mano en la base; desde
     * la app no había dónde escribirlo.
     *
     * ### El contrato
     *
     * - `null` (o el campo ausente) significa **«estímalo»**, que es lo que hace un cliente viejo
     *   —el APK 1.17 instalado no conoce este campo— y lo que manda la hoja de «Cuota» cuando el
     *   dueño no tocó la estimación. **Sin `@EncodeDefault`** a propósito: un `null` omitido tiene
     *   que ser indistinguible de un cliente que no sabe que el campo existe.
     * - Con un valor, el server lo usa **en lugar** de la estimación: la pata de la deuda pasa a
     *   valer `cuota − interesReal − seguro`, y `noAmortiza` guarda `interesReal + seguro`. La
     *   estimación NO se mira; la tasa tampoco, así que un crédito **sin tasa** también lo acepta
     *   —el extracto sabe más que las condiciones.
     * - El server **no le cree al cliente**: `≥ 0`, solo sobre un crédito (una tarjeta no lleva
     *   interés adentro del pago, ver [MotivoDelDesglose.TARJETA]), y `interesReal + seguro ≤
     *   cuota` — si no, el capital sería negativo y la deuda **subiría** con un pago. Ver
     *   [validarInteresReal], que es la única definición y la usan la hoja y la ruta.
     *
     * ### El seguro NO se sobreescribe por cuota, y es una decisión
     *
     * El seguro de vida deudor es un cargo **fijo** que el dueño ya declara una vez en las
     * condiciones del crédito (`credit_terms.insurance_monthly`, editable desde la app). No
     * depende del saldo como el interés, así que si el extracto dice otra cifra, lo que está mal
     * son las condiciones, y arreglarlas ahí corrige TODAS las cuotas siguientes en vez de una.
     * Además el par guarda la suma (`noAmortiza = interés + seguro`), no cada parte: un seguro
     * distinto por cuota sería indistinguible después de guardado. Y si hiciera falta igual, el
     * total que no amortiza se puede meter entero en este campo — lo que decide cuánto baja la
     * deuda es la suma.
     */
    val interesReal: Long? = null,
)

/**
 * **Qué parte de una cuota baja de verdad la deuda.**
 *
 * ### El error que esto corrige
 *
 * Hasta acá [pagoDeCuotaLegs] escribía las dos patas por el **monto completo** de la cuota, así
 * que la deuda bajaba también por el interés. En un crédito que amortiza eso es falso: la mayor
 * parte de una cuota es interés del período, y el interés **no baja el capital** — se causa y se
 * paga, y al mes siguiente vuelve.
 *
 * En los números reales del dueño el error no era un decimal: de $28.526.537 de cuotas mensuales,
 * **$18.671.083 son interés** y solo $9.855.454 abonan a capital. Registrar las seis cuotas de un
 * mes le mostraba $18,7 millones menos de deuda de la que tiene, y el error se acumulaba todos los
 * meses. Es el peor tipo de error: el número queda plausible.
 *
 * ### La decisión, aprobada por el dueño
 *
 * - La pata del **dinero** sigue por el monto completo: esa plata sí salió y sí cuenta en «Gastos
 *   del mes» ([CUOTA_CATEGORY] es una categoría normal).
 * - La pata de la **deuda** baja solo por el **capital** = cuota − interés del período − seguro.
 *
 * Vive en `:core` y no en la pantalla porque la usan cuatro lugares —el server que escribe las
 * patas, la ruta de la cuota que paga otro (`payroll-deduction`, que hasta el arreglo del interés
 * real seguía bajando la deuda por la cuota entera), la hoja de «Cuota» que muestra el desglose
 * antes de guardar, y la corrección del monto de una pata— y una regla sobre plata duplicada en
 * dos pantallas ya sobrevivió tres rondas de arreglos en este proyecto.
 *
 * ### La estimación se queda corta, y por eso el interés se puede escribir
 *
 * `saldo × tasa mensual` no es lo que cobra el banco: en el ·9695 da $363.905 contra $473.227 del
 * extracto. Cuando el dueño tiene el extracto, escribe el interés real
 * ([CreatePagoDeCuotaRequest.interesReal]) y esta estimación no se usa — ver
 * [desglosarCuotaConInteresReal] y [MotivoDelDesglose.INTERES_REAL].
 *
 * ### Qué pasa con los pagos de cuota YA registrados
 *
 * **Nada, y a propósito.** Medido contra la base de producción el día de este cambio: no hay
 * ninguna cuota de crédito registrada —los únicos pares que existen son un desembolso (traspaso) y
 * un pago de la tarjeta AMEX, que es simétrico y correcto—, así que no hay historia que migrar.
 *
 * Y si apareciera una, tampoco se toca: reescribirle la pata de la deuda hoy sería recalcular el
 * interés de un mes que ya pasó con el saldo de hoy, o sea inventar una cifra distinta de la que
 * el banco cobró. La deuda se corrige donde este sistema corrige deudas: con «Ajustar saldo»,
 * contra lo que dice el extracto. Un par viejo (las dos patas iguales) sigue funcionando en todo lo
 * demás — [montoDeLaHermanaAlCorregir] lo trata como simétrico, que es exactamente lo que es.
 */
@Serializable
data class DesgloseDeCuota(
    /** Lo que sale de la cuenta: el monto que el dueño escribió. */
    val cuota: Long,
    /** Interés del período. Cero cuando no se pudo calcular ([MotivoDelDesglose.SIN_TASA]). */
    val interes: Long,
    /** Seguro de vida deudor u otro cargo mensual fijo que tampoco amortiza. */
    val seguro: Long,
    /** Lo que de verdad baja la deuda. Nunca negativo. */
    val capital: Long,
    val motivo: MotivoDelDesglose,
)

/** Por qué [DesgloseDeCuota.capital] vale lo que vale. Lo lee la pantalla para saber qué decir. */
@Serializable
enum class MotivoDelDesglose {
    /**
     * Un crédito que amortiza, con tasa registrada: se separó interés (y seguro) del capital.
     */
    AMORTIZA,

    /**
     * Una tarjeta de crédito. Pagar el extracto baja la deuda **exactamente por lo que se pagó**,
     * y eso ya es correcto: los intereses de una tarjeta se causan como un movimiento aparte, no
     * escondidos adentro del pago. No hay nada que separar ni nada que avisar.
     */
    TARJETA,

    /**
     * Un crédito **sin tasa registrada** (sin condiciones, o con `rateEa` en 0). No hay forma de
     * separar interés de capital, así que la deuda baja por el monto completo —el comportamiento
     * de siempre— y **la pantalla tiene que decirlo antes de guardar**. Inventar un interés
     * plausible sería exactamente el error que esta rama vino a matar, con otro disfraz.
     */
    SIN_TASA,

    /**
     * Un crédito cuyo interés **lo escribió el dueño** del extracto
     * ([CreatePagoDeCuotaRequest.interesReal]) en vez de estimarse sobre el saldo. El seguro
     * sigue saliendo de las condiciones. La pantalla NO le agrega «calculado sobre tu deuda de
     * hoy»: ese aviso es de la estimación, y acá no hubo estimación.
     *
     * Un cliente viejo nunca lo recibe: solo se produce cuando la petición trajo el campo, y un
     * cliente viejo no lo manda.
     */
    INTERES_REAL,
}

/**
 * La tasa **mensual** equivalente a una efectiva anual, que es como los bancos de acá publican la
 * suya (`credit_terms.rate_ea`, en por ciento: `17.46` = 17,46 % E.A.).
 *
 * `(1 + r)^(1/12) − 1` y no `r/12`: la EA ya viene capitalizada, así que dividirla por 12 da una
 * mensual más alta que la real (para 17,46 % E.A. daría 1,455 % en vez de 1,349 %) y le inventaría
 * al dueño ~8 % de interés de más en cada cuota.
 */
fun tasaMensualDeUnaEA(rateEa: Double): Double = (1.0 + rateEa / 100.0).pow(1.0 / 12.0) - 1.0

/**
 * Separa una cuota en interés, seguro y capital. Ver [DesgloseDeCuota].
 *
 * ### Por qué el saldo entra como parámetro y no se deriva acá
 *
 * La deuda de una cuenta en Movi **nunca se almacena**: se deriva de sus eventos no anulados
 * (`computeBalances`). Quien llama es el único que sabe de qué conjunto de eventos está hablando —
 * el server excluye las patas de este mismo pago para que un reintento no calcule un interés
 * distinto; la pantalla usa el saldo ya derivado que le llegó con la cuenta.
 *
 * ### Aritmética
 *
 * El único `Double` es la tasa. El interés se redondea a Long apenas se calcula y **todo lo demás
 * es aritmética Long**: este proyecto ya se quemó con un `Float` cuya mantisa de 24 bits no
 * representa todos los enteros por encima de 16.777.216, y una deuda de $768.430.394 vive muy por
 * encima de eso. `Double` sí representa exacto cualquier entero hasta 2^53, así que multiplicar el
 * saldo por la tasa y redondear es seguro; comparar plata contra plata, en cambio, se hace en Long.
 *
 * @param saldoDeLaDeuda capital vigente **antes** de este pago, en la moneda de la deuda.
 *   Un saldo negativo (deuda pagada de más) se trata como cero: no causa intereses.
 *
 *   **Es el saldo de HOY, no el del mes de la cuota, y eso no se puede arreglar acá.** La deuda de
 *   una cuenta en Movi es la suma de todos sus eventos no anulados, sin mirar fechas: no existe
 *   «el saldo al 15 de julio». Así que anotar las cuotas en orden cronológico da una amortización
 *   que se parece a la real, y anotar una cuota vieja **después** de una nueva la calcula sobre un
 *   saldo ya reducido — medido: ~$24.278 de capital de más por mes desfasado en el vehículo,
 *   ~$21.353 en la hipoteca, del orden de $100.000 con los seis créditos por cada mes anotado al
 *   revés, y siempre en la misma dirección (deuda subestimada).
 *
 *   Por eso la limitación no vive solo en este KDoc: la frase que el dueño lee antes de guardar lo
 *   dice con todas las letras («calculado sobre tu deuda de hoy», ver `textoDelDesglose`). Una
 *   limitación que solo conoce quien lee el código no está anotada, está escondida.
 */
fun desglosarCuota(
    cuota: Long,
    tipoDeLaDeuda: AccountType,
    saldoDeLaDeuda: Long,
    rateEa: Double?,
    seguroMensual: Long?,
): DesgloseDeCuota {
    // Una tarjeta no amortiza nada: lo que se paga baja la deuda tal cual. Ver [MotivoDelDesglose].
    if (tipoDeLaDeuda != AccountType.LOAN) {
        return DesgloseDeCuota(cuota, interes = 0L, seguro = 0L, capital = cuota, motivo = MotivoDelDesglose.TARJETA)
    }
    // Sin tasa no se puede separar, y **no se inventa**: la deuda baja por todo, como hasta hoy, y
    // la pantalla lo anuncia. El seguro tampoco se resta acá: restar solo esa mitad daría un
    // capital igual de desconocido pero con pinta de calculado.
    if (rateEa == null || rateEa <= 0.0 || !rateEa.isFinite()) {
        return DesgloseDeCuota(cuota, interes = 0L, seguro = 0L, capital = cuota, motivo = MotivoDelDesglose.SIN_TASA)
    }
    val saldo = saldoDeLaDeuda.coerceAtLeast(0L)
    val interes = round(saldo.toDouble() * tasaMensualDeUnaEA(rateEa)).toLong().coerceAtLeast(0L)
    val seguro = (seguroMensual ?: 0L).coerceAtLeast(0L)
    // Clampado a 0 y no negativo: una cuota que no alcanza a cubrir interés + seguro no *sube* la
    // deuda por esta puerta. Sube sola, cuando el banco capitaliza, y eso se anota con «Ajustar
    // saldo» — que es un hecho del banco, no una deducción nuestra.
    //
    // El caso existe de verdad, pero no es el que decía acá antes: la cuota **completa** de la
    // libranza ·4818 ($6.040.259 sobre un saldo de $283.000.000 al 15,50 % E.A.) abona $2.394.248
    // a capital. Lo que sí es 100 % interés es un **pago parcial** —un abono de $3.000.000 contra
    // un interés de $3.646.011—, que es exactamente lo que el dueño hace cuando la cuota no le
    // alcanza en un mes.
    //
    // Y clampar acá ya no pierde información: lo que no amortizó queda guardado en la pata de la
    // deuda (ver [FinancialEvent.noAmortiza]), así que corregir el monto después no tiene que
    // deducirlo de una resta que en este caso miente.
    val capital = (cuota - interes - seguro).coerceAtLeast(0L)
    return DesgloseDeCuota(cuota, interes = interes, seguro = seguro, capital = capital, motivo = MotivoDelDesglose.AMORTIZA)
}

/** Lo que se le dice a quien manda un interés negativo. */
const val INTERES_REAL_NEGATIVO: String =
    "El interés no puede ser negativo. Si el banco no cobró interés este mes, escribe 0."

/** Lo que se le dice a quien manda un interés real sobre una tarjeta. */
const val INTERES_REAL_EN_TARJETA: String =
    "El pago de una tarjeta baja la deuda por todo lo pagado: los intereses de la tarjeta se " +
        "anotan como un movimiento aparte, no adentro del pago."

/**
 * «1204064» → «1.204.064». Solo para textos que nacen en `:core` o en el server (mensajes de rechazo,
 * el concepto de un movimiento); la app tiene su `formatMoney`, que además sabe de monedas.
 */
fun conPuntosDeMiles(valor: Long): String {
    val digitos = valor.toString().trimStart('-')
    val agrupado = digitos.reversed().chunked(3).joinToString(".").reversed()
    return if (valor < 0) "-$agrupado" else agrupado
}

/**
 * Lo que se le dice cuando el interés más el seguro se comen la cuota entera y algo más: con esas
 * cifras el capital sería negativo, o sea que **la deuda subiría con un pago**. Se rechaza en vez
 * de clamparse a cero como hace la estimación: acá los tres números los escribió una persona, y
 * uno de los tres está mal.
 */
fun mensajeDeInteresQueNoCabe(cuota: Long, interesReal: Long, seguro: Long): String {
    // Concatenación y no plantilla: un `$` literal pegado a una plantilla ya rompió una URL en
    // producción, y así el símbolo queda donde se lee.
    val conSeguro = if (seguro > 0L) " más el seguro ($" + conPuntosDeMiles(seguro) + ")" else ""
    return "El interés ($" + conPuntosDeMiles(interesReal) + ")" + conSeguro + " supera la cuota ($" +
        conPuntosDeMiles(cuota) + "): con eso la deuda subiría en vez de bajar. Revisa el interés o el monto."
}

/**
 * ¿Se puede usar este interés real? Devuelve el motivo, o `null` si está bien — incluido el caso
 * en que no vino ninguno, que es «estímalo» y siempre está bien.
 *
 * **Es la única definición**, igual que [validarPagoDeCuota]: la hoja de «Cuota» apaga el botón
 * con esto y el server rechaza con 422 con esto. Es plata; dos copias de la regla ya costaron
 * caro en este proyecto.
 *
 * @param seguroMensual el de las condiciones del crédito, que es el que el server va a restar.
 */
fun validarInteresReal(
    interesReal: Long?,
    cuota: Long,
    tipoDeLaDeuda: AccountType,
    seguroMensual: Long?,
): String? {
    if (interesReal == null) return null
    if (interesReal < 0L) return INTERES_REAL_NEGATIVO
    if (tipoDeLaDeuda != AccountType.LOAN) return INTERES_REAL_EN_TARJETA
    val seguro = (seguroMensual ?: 0L).coerceAtLeast(0L)
    // `cuota − interés − seguro < 0` y no `interés + seguro > cuota`: la resta no desborda con
    // cifras que ya pasaron `MONTO_MAXIMO`, la suma de dos entradas ajenas podría.
    if (cuota - interesReal - seguro < 0L) return mensajeDeInteresQueNoCabe(cuota, interesReal, seguro)
    return null
}

/**
 * El desglose de una cuota cuyo interés **vino del extracto**, no de la estimación.
 *
 * Solo aritmética: `capital = cuota − interesReal − seguro`. Ni la tasa ni el saldo entran, así
 * que sirve igual para un crédito sin tasa registrada — ahí la estimación no puede separar nada
 * y el extracto sí. Exige que [validarInteresReal] haya pasado: un capital negativo acá no es
 * un caso a clampar, es un pago mal escrito que se rechazó antes.
 */
fun desglosarCuotaConInteresReal(
    cuota: Long,
    tipoDeLaDeuda: AccountType,
    interesReal: Long,
    seguroMensual: Long?,
): DesgloseDeCuota {
    require(validarInteresReal(interesReal, cuota, tipoDeLaDeuda, seguroMensual) == null) {
        "Un interés real inválido se rechaza antes de desglosar; llama a validarInteresReal primero"
    }
    val seguro = (seguroMensual ?: 0L).coerceAtLeast(0L)
    return DesgloseDeCuota(
        cuota = cuota,
        interes = interesReal,
        seguro = seguro,
        capital = cuota - interesReal - seguro,
        motivo = MotivoDelDesglose.INTERES_REAL,
    )
}

/**
 * **El único punto de entrada para repartir una cuota que se va a escribir**: con interés real
 * si vino, estimado si no. Lo usan el server y la hoja para que los dos elijan igual; un call
 * site que llame a [desglosarCuota] directo cuando había un interés real escrito es exactamente
 * la clase de olvido que este proyecto ya pagó.
 *
 * No valida: [validarInteresReal] va antes, y quien no lo llamó se entera por el `require` de
 * [desglosarCuotaConInteresReal].
 */
fun desglosarCuotaRegistrada(
    cuota: Long,
    tipoDeLaDeuda: AccountType,
    saldoDeLaDeuda: Long,
    rateEa: Double?,
    seguroMensual: Long?,
    interesReal: Long?,
): DesgloseDeCuota =
    if (interesReal != null) {
        desglosarCuotaConInteresReal(cuota, tipoDeLaDeuda, interesReal, seguroMensual)
    } else {
        desglosarCuota(cuota, tipoDeLaDeuda, saldoDeLaDeuda, rateEa, seguroMensual)
    }

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
 *
 * [desglose] **no tiene valor por defecto a propósito**: es el parámetro que decide cuánto baja la
 * deuda, y un default lo habría dejado pasar en silencio en cualquier call site que alguien agregue
 * mañana — que es exactamente la forma en que este proyecto ya aplicó un arreglo a 1 de 3
 * endpoints. Sin default, olvidarlo no compila. Se calcula con [desglosarCuota].
 */
fun pagoDeCuotaLegs(
    request: CreatePagoDeCuotaRequest,
    from: Account,
    debt: Account,
    desglose: DesgloseDeCuota,
): Pair<FinancialEvent, FinancialEvent> {
    val esTarjeta = debt.type == AccountType.CREDIT_CARD
    val categoriaDelDinero = if (esTarjeta) CARD_PAYMENT_CATEGORY else CUOTA_CATEGORY
    val nota = request.note?.trim().orEmpty()
    fun describir(base: String) = if (nota.isEmpty()) base else "$base · $nota"

    fun pata(
        id: String,
        accountId: String,
        tipo: TransactionType,
        categoria: String,
        texto: String,
        monto: Long,
        noAmortiza: Long? = null,
    ) =
        FinancialEvent(
            id = id,
            accountId = accountId,
            type = tipo,
            amount = monto,
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
            noAmortiza = noAmortiza,
        )

    return pata(
        request.fromEventId, from.id, TransactionType.EXPENSE, categoriaDelDinero,
        describir(if (esTarjeta) "Pago de ${debt.name}" else "Cuota de ${debt.name}"),
        // La plata que de verdad salió de la cuenta: la cuota entera, intereses y seguro incluidos.
        monto = request.amount,
    ) to pata(
        // La pata de la deuda lleva la MISMA categoría que la del dinero, y da igual cuál sea:
        // vive en una cuenta LOAN o CREDIT_CARD, que `isCashFlow` excluye por tipo de cuenta. Se
        // usa la misma para que las dos filas se lean como lo que son, una sola operación.
        request.toEventId, debt.id, TransactionType.INCOME, categoriaDelDinero,
        // El concepto DICE que solo baja el capital cuando de verdad solo baja el capital. Sin
        // esto, el detalle de la cuenta LOAN mostraba «Pago desde Ahorros · $813.843» sobre una
        // cuota de $1.286.548 y no había en toda la app dónde leer a dónde se fue la diferencia.
        describir(if (desglose.capital != desglose.cuota) "Abono a capital desde ${from.name}" else "Pago desde ${from.name}"),
        // **Y acá está el cambio entero de esta rama**: la deuda baja por el capital, no por la
        // cuota. Ver [DesgloseDeCuota].
        monto = desglose.capital,
        // Lo que esta cuota NO amortizó, guardado en la fila y no deducido de la resta de las dos
        // patas. La resta miente justo cuando el capital se clampa a cero, y ahí es donde la
        // corrección del monto borraba deuda en silencio. Ver [FinancialEvent.noAmortiza].
        //
        // Solo en un crédito que amortiza: una tarjeta y un crédito sin tasa arman un par
        // simétrico, y para esos `null` dice la verdad —no hay nada que no amortice— mientras que
        // un 0 explícito diría lo mismo con pinta de calculado.
        noAmortiza = if (desglose.motivo == MotivoDelDesglose.AMORTIZA || desglose.motivo == MotivoDelDesglose.INTERES_REAL) {
            desglose.interes + desglose.seguro
        } else {
            null
        },
    )
}

/** Lo que la app necesita para pintar el resultado sin volver a preguntar. */
@Serializable
data class PagoDeCuotaResult(
    /** Cuánto queda debiendo después del pago — el número que el dueño vino a ver bajar. */
    val deudaRestante: Long,
    val patas: List<FinancialEvent>,
    /**
     * Cómo se repartió la cuota. Es **lo que el server de verdad usó**, no lo que la pantalla
     * había previsto: la pantalla calcula el desglose con el saldo que tenía cargado y el server
     * lo recalcula contra los eventos vivos, así que si algo se movió en el medio, esto lo dice.
     *
     * Con default `null` para que un cliente viejo (que no lo espera) y un server viejo (que no lo
     * manda) sigan deserializando, igual que el resto de los campos agregados en este proyecto.
     */
    val desglose: DesgloseDeCuota? = null,
)

/**
 * El cuerpo **opcional** de `POST /api/credits/{id}/payroll-deduction`: la cuota que paga otro
 * (la nómina, Skandia, un familiar) también puede llevar el interés real del extracto.
 *
 * Opcional de verdad: el botón «Registrar descuento» de la app manda la petición **sin cuerpo** y
 * el server estima, igual que con [CreatePagoDeCuotaRequest.interesReal] en `null`. Mismo
 * contrato, mismas guardas ([validarInteresReal]), mismo `noAmortiza` guardado en la fila.
 */
@Serializable
data class RegistrarCuotaAjenaRequest(
    val interesReal: Long? = null,
)
