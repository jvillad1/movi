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

/**
 * Lo que se le dice a alguien que intenta *entrar* a [TRANSFER_CATEGORY] recategorizando.
 *
 * Sin flecha: «→» sale como ▯ en wasm (la fuente del canvas no trae el glifo), y este texto se le
 * muestra al dueño tal cual.
 */
const val TRANSFER_CATEGORY_RESERVED =
    "«Traspaso» es una categoría reservada: para mover plata entre tus cuentas abre Agregar y elige Traspaso."

/**
 * Lo que se le dice a un cliente que intenta crear media pata suelta por `POST /api/events`.
 *
 * En castellano de persona, no de ruta HTTP: este texto puede terminar en la pantalla del dueño
 * (la UI muestra el cuerpo del error del server, ver `toUserMessage`), y «usá POST /api/transfers»
 * no le dice nada a nadie que no esté leyendo el código.
 */
const val TRANSFER_LEG_NOT_STANDALONE =
    "Un traspaso se registra completo, con sus dos puntas: abre Agregar y elige Traspaso."

/**
 * Lo que se le dice a un cliente que reusa un `transferId` que ya tiene patas de OTRO traspaso.
 *
 * No es el reintento del dedo —ese manda los mismos tres ids y se responde con las patas que ya
 * están, ver `POST /api/transfers`—: es un traspaso distinto pidiendo una identidad ocupada. Si se
 * dejara pasar, ese id terminaría con cuatro patas y nada podría volver a decir cuál compensa a
 * cuál: ni la anulación en cascada, ni el renglón único de Movimientos, ni el conteo del Inicio.
 */
const val TRANSFER_ID_ALREADY_USED =
    "Ese traspaso ya existe con otros movimientos. Vuelve a intentarlo desde Agregar."

/**
 * Categoría de la pata que **sobrevive al borrado de la cuenta de la otra punta** (ver
 * `DELETE /api/accounts/{id}`).
 *
 * **Por qué no se queda en [TRANSFER_CATEGORY]:** en este sistema, una pata sin la otra no puede
 * quedarse en la categoría reservada. Ahí adentro es un fantasma permanente — fuera del mes por
 * [isCashFlow], fuera de los chips Gastos e Ingresos, y encima imposible de arreglar, porque
 * `PUT /api/events/{id}/category` rechaza recategorizar cualquier pata de traspaso
 * ([TRANSFER_RECATEGORIZE_BLOCKED]). Mismo criterio que ya aplica `StatementRoutes` con una fila
 * de extracto etiquetada «Traspaso» sin hermana.
 *
 * **Por qué NO es «Otros», que era la primera respuesta:** este código ya cometió ese error y lo
 * dejó escrito. Ver `ADJUSTMENT_CATEGORY` en `BalanceAdjustment.kt`: el ajuste de saldo vivía en
 * «Otros» y ahí *«chocaba de frente con un presupuesto llamado "Otros", que quedaba en OVER al
 * instante»*. Acá el choque sería peor, no mejor: el ajuste al menos queda fuera del flujo de
 * caja, y esta pata **vuelve a entrar** (esa es toda la idea), así que cae derecho en
 * `spentByCategory` y podría poner en OVER el presupuesto «Otros» de un mes viejo, meses después,
 * por un traspaso que el dueño no tiene cómo relacionar con eso. Con nombre propio no hay
 * presupuesto que ensuciar y el desglose se explica solo.
 *
 * **Y por qué UNA sola categoría y no una por dirección**, que fue el primer intento («Traspaso a
 * cuenta eliminada» / «Traspaso desde cuenta eliminada»). Ese intento existía para no repetir el
 * otro problema de «Otros»: que está tipada como EXPENSE en `PREDEFINED_CATEGORIES`, así que
 * rotulaba como gasto una pata huérfana de tipo INCOME. Este nombre lo resuelve sin partirse en
 * dos: no es una categoría predefinida, **no tiene tipo**, y se lee igual de bien en las dos
 * direcciones — la dirección sigue estando donde el dueño la mira, en el signo del monto y en la
 * descripción («Traspaso a Nequi …» / «Traspaso desde Nequi …», ver [orphanedLegDescription]).
 *
 * Distinguir por dirección no habría comprado nada donde importa —`spentByCategory` solo suma
 * egresos, así que la variante de ingreso jamás aparecería en un presupuesto— y costaba caro:
 * verificado en el navegador, con 27 caracteres el renglón de Movimientos no tenía ancho para el
 * subtítulo «categoría · origen» y partía «MANUAL» en una letra por línea.
 *
 * **No es reservada.** A diferencia de [TRANSFER_CATEGORY], nada bloquea entrar ni salir de acá:
 * el dueño puede recategorizar la fila desde Movimientos, que es justo lo que antes no podía.
 *
 * ## Ola 14 — esto dejó de ser un caso raro, y quien lo tome después tiene que saberlo
 *
 * Desde que un préstamo puede ser una punta del traspaso ([validateTransfer]), la pata que
 * sobrevive puede valer **el monto entero de un crédito**. Medido en el navegador: borrada la
 * cuenta del crédito, la pata del banco —un INCOME de $257.000.000 en una cuenta de activo—
 * vuelve a entrar al flujo de caja por esta categoría, e Inicio pasó a decir **«Ingresos
 * $269,4M»** para alguien que había ganado $12,4M.
 *
 * Y no es que la rama haya dejado la probabilidad igual: **la sube**. El camino más corto para
 * llegar acá es la recuperación del propio error que la rama evita — crear el crédito con su
 * deuda (la costumbre de siempre), anotar además el desembolso, ver la deuda al doble, y **borrar
 * el crédito para empezar de nuevo**. Eso es lo que hace cualquiera al descubrir un duplicado.
 *
 * **No se arregló acá a propósito**, y no por tamaño: arreglarlo es cambiarle el significado a
 * esta categoría, que este mismo KDoc defiende con detalle (vuelve a entrar al flujo porque «esa
 * plata sí se movió»). Que una pata huérfana **de un crédito** sea la excepción a esa regla es
 * una decisión de producto con su propia discusión, no un ajuste al pasar. Queda escrito para que
 * quien la tome no lo lea como una rareza teórica.
 */
const val ORPHANED_LEG_CATEGORY = "Cuenta eliminada"

/**
 * Lo que se le agrega a la descripción de esa pata para que se explique sola.
 *
 * La descripción ya nombra la otra punta ("Traspaso a Nequi", ver [transferLegsFor]); lo único
 * que falta decir es que esa cuenta ya no está. Sin esto, el dueño se encontraba con un renglón
 * que apunta a una cuenta que no puede abrir.
 */
const val ORPHANED_LEG_SUFFIX = " · cuenta eliminada"

/** Largo de `financial_events.description` (ver `Tables.kt`) y de su espejo local. */
private const val MAX_DESCRIPTION_LENGTH = 255

/**
 * La descripción de [original] con [ORPHANED_LEG_SUFFIX] pegado, sin pasarse del largo de la
 * columna: si no cabe, se recorta la descripción —no el sufijo— porque el sufijo es justamente
 * la parte que el dueño necesita leer. Idempotente: una descripción que ya lo trae no lo repite
 * (borrar dos cuentas de dos traspasos distintos no puede encadenar sufijos).
 */
fun orphanedLegDescription(original: String): String {
    if (original.endsWith(ORPHANED_LEG_SUFFIX)) return original
    val room = MAX_DESCRIPTION_LENGTH - ORPHANED_LEG_SUFFIX.length
    return original.take(room) + ORPHANED_LEG_SUFFIX
}

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
 * - **Ninguna tarjeta de crédito**, ni como origen ni como destino ([TRANSFER_CARD_BLOCKED]).
 * - **No entre dos préstamos** ([TRANSFER_BOTH_LOANS_BLOCKED]).
 * - **Misma moneda.** Un traspaso entre monedas necesita una tasa y decidir cuál de los dos
 *   montos manda; hasta que eso exista, se dice que no en vez de inventar una conversión.
 *
 * ## Por qué un préstamo SÍ puede ser una de las dos puntas (y la tarjeta no)
 *
 * Hasta la Ola 14 esta función rechazaba **todo** el grupo [AccountGroup.DEUDA] con un solo
 * argumento: que meter deuda en un traspaso *«duplicaría esa lógica y le cambiaría el signo a la
 * deuda»*. La segunda mitad de esa frase se comprobó y **es falsa para los préstamos**:
 * [signedDelta] ya usa la convención de la cuenta, así que en una cuenta LOAN un EXPENSE **sube**
 * la deuda y un INCOME la **baja** — exactamente los dos hechos que hay que registrar:
 *
 * | Movimiento real | Pata de origen (EXPENSE) | Pata de destino (INCOME) |
 * |---|---|---|
 * | **Desembolso** — el banco deposita el crédito | préstamo: la deuda **sube** | cuenta: el efectivo **sube** |
 * | **Abono extraordinario** — plata extra contra el capital | cuenta: el efectivo **baja** | préstamo: la deuda **baja** |
 *
 * Los cuatro signos salen bien sin tocar una línea de `signedDelta`/`computeBalances`, y las dos
 * patas quedan fuera del mes por [TRANSFER_CATEGORY] — que es justo lo que hacía falta: **un
 * desembolso no es un ingreso.** Anotar la libranza de $257.000.000 como ingreso decía que el mes
 * había entrado $257 millones sin que el dueño ganara un peso.
 *
 * La primera mitad del argumento —la duplicación— **sí se sostiene para la tarjeta**, y por eso
 * la tarjeta se sigue rechazando: pagar el extracto ya tiene su camino ([CARD_PAYMENT_CATEGORY])
 * con su propia regla en [isCashFlow] y su propio detector de candidatos (`looksLikeCardPayment`).
 * Dos formas de anotar el mismo hecho, con dos categorías distintas y dos reglas distintas, es
 * cómo se rompe el mes. El préstamo no tenía ninguna: ese era el agujero.
 *
 * **Dos préstamos tampoco**, aunque los signos también darían: mover deuda de un crédito a otro
 * es una refinanciación, no un traspaso, y no hay forma de que quien la anote así entienda qué
 * quedó registrado. Se dice que no y se explica.
 */
fun validateTransfer(from: Account?, to: Account?, amount: Long): String? = when {
    from == null || to == null -> "Elige la cuenta de origen y la de destino"
    from.id == to.id -> "El origen y el destino tienen que ser cuentas distintas"
    amount <= 0L -> "El monto tiene que ser mayor que cero"
    from.type == AccountType.CREDIT_CARD || to.type == AccountType.CREDIT_CARD -> TRANSFER_CARD_BLOCKED
    from.type == AccountType.LOAN && to.type == AccountType.LOAN -> TRANSFER_BOTH_LOANS_BLOCKED
    from.currency != to.currency -> "Por ahora solo entre cuentas de la misma moneda"
    else -> null
}

/**
 * Qué está registrando este traspaso. Lo decide el **tipo de las dos cuentas**, no una opción que
 * el dueño elija: si sale de un préstamo es un desembolso y si entra a uno es un abono, no hay
 * tercera lectura posible. Existe para que la hoja de Agregar, la descripción de las patas y las
 * pruebas hablen del mismo hecho con el mismo nombre.
 */
enum class TransferKind {
    /** Del préstamo a una cuenta tuya: el banco desembolsó. La deuda sube y el efectivo sube. */
    DESEMBOLSO,

    /** De una cuenta tuya al préstamo: plata extra contra el capital. El efectivo baja y la deuda baja. */
    ABONO_EXTRAORDINARIO,

    /** Entre dos cuentas de dinero o inversión: el traspaso de toda la vida. */
    ENTRE_CUENTAS,
}

/**
 * Si sale de un préstamo es un desembolso; si entra a uno, un abono extraordinario.
 *
 * ## Qué pasa si el dueño confunde la cuota mensual con un abono extraordinario
 *
 * Las tres señales que separan un caso del otro —el texto de la hoja al elegir el crédito, el
 * nombre del renglón en Movimientos, y la descripción que queda guardada— alcanzan para quien
 * las lee. Si igual se equivoca y anota la cuota como traspaso, el fallo es **silencioso en las
 * cifras**: «Gastos del mes» queda subestimado en la cuota entera, y la deuda baja por el monto
 * completo cuando buena parte de una cuota es interés y no capital.
 *
 * Pero no queda sin ninguna alarma, y conviene tenerlo escrito: la conciliación de recurrentes
 * **descarta las patas de traspaso** (`occurrenceCandidatesFor` filtra `transferId != null`), así
 * que el recordatorio de esa cuota **no se cierra y le sigue insistiendo**. Un aviso que vuelve
 * sobre una cuota que él sabe que pagó es exactamente la pista de que usó la herramienta
 * equivocada. Ese filtro se escribió para otra cosa; acá hace de red, y por eso no se toca.
 *
 * Devuelve el [TransferKind] de este par de cuentas. Solo tiene sentido sobre un par que
 * [validateTransfer] aceptó.
 */
fun transferKindFor(from: Account, to: Account): TransferKind = when {
    from.type == AccountType.LOAN -> TransferKind.DESEMBOLSO
    to.type == AccountType.LOAN -> TransferKind.ABONO_EXTRAORDINARIO
    else -> TransferKind.ENTRE_CUENTAS
}

/**
 * Lo que se le dice a quien pone una **tarjeta de crédito** en cualquiera de las dos puntas.
 *
 * No es un «no» seco: dice a dónde ir. El pago del extracto se anota como un gasto normal desde
 * Agregar con la categoría [CARD_PAYMENT_CATEGORY], que es la que [isCashFlow] ya sabe dejar
 * fuera del mes — la misma que propone la hoja de candidatos a pago de tarjeta.
 */
const val TRANSFER_CARD_BLOCKED =
    "El pago de una tarjeta se anota como gasto con la categoría «Pago de tarjeta». Un traspaso no la toca."

/** Lo que se le dice a quien pone un préstamo en las DOS puntas. Ver [validateTransfer]. */
const val TRANSFER_BOTH_LOANS_BLOCKED =
    "Un traspaso va entre un crédito y una cuenta tuya, no entre dos créditos."

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
 * la pata suelta, lo que falta saber es cuál es la otra punta. Cuando una de las puntas es un
 * préstamo el sustantivo cambia a "Desembolso"/"Abono extraordinario" — ver
 * [transferLegHeadlines]. La nota, si la hay, se agrega después de un separador — no reemplaza la
 * descripción, porque perder la otra punta para mostrar "alquiler" dejaría la pata sin contexto.
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
    val (haciaAlla, desdeAca) = transferLegHeadlines(from, to)
    return leg(request.fromEventId, from.id, TransactionType.EXPENSE, describe(haciaAlla)) to
        leg(request.toEventId, to.id, TransactionType.INCOME, describe(desdeAca))
}

/**
 * El encabezado de cada pata: primero el de **origen** («… a Destino»), después el de **destino**
 * («… desde Origen»). Siempre nombra la OTRA punta, por el mismo motivo de siempre — en el detalle
 * de una cuenta, lo que falta saber es de dónde vino o a dónde fue.
 *
 * Lo que cambia con el crédito es el sustantivo, y no es cosmético: en Movimientos el dueño ve una
 * fila, no un diagrama de cuentas. «Traspaso a Bancolombia» desde una libranza no dice nada;
 * **«Desembolso a Bancolombia»** dice exactamente qué pasó, y **«Abono extraordinario a
 * Libranza»** lo separa a la vista de la cuota mensual — que se anota como un gasto normal y sí
 * cuenta en el mes (ver [isCashFlow] y el texto de la hoja de Traspaso).
 *
 * Sigue siendo la misma categoría reservada [TRANSFER_CATEGORY] en las dos patas: el nombre que se
 * lee cambia, la mecánica del mes y de los saldos no.
 *
 * Las tres formas dicen «… a Destino» / «… desde Origen», con la MISMA preposición en los tres
 * casos. La primera versión tenía «Desembolso **de** Libranza» junto a «Abono extraordinario
 * **desde** Ahorros»: dos formas de decir lo mismo en renglones que se leen uno debajo del otro,
 * sin que la diferencia significara nada.
 */
internal fun transferLegHeadlines(from: Account, to: Account): Pair<String, String> =
    when (transferKindFor(from, to)) {
        TransferKind.DESEMBOLSO ->
            "Desembolso a ${to.name}" to "Desembolso desde ${from.name}"
        TransferKind.ABONO_EXTRAORDINARIO ->
            "Abono extraordinario a ${to.name}" to "Abono extraordinario desde ${from.name}"
        TransferKind.ENTRE_CUENTAS ->
            "Traspaso a ${to.name}" to "Traspaso desde ${from.name}"
    }
