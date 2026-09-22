package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **«Esto ya ocurrió»**: el sello de que el recurrente [ruleId] efectivamente pasó en el periodo
 * [period].
 *
 * ## Por qué existe
 *
 * Hasta hoy `upcomingPayments` decidía el estado de un pago **solo con el calendario**: comparaba
 * el día de la regla con hoy y nada más. Nunca miraba los movimientos, y no había ningún vínculo
 * entre un movimiento y la regla que lo originó. El resultado, con la plata real del dueño: su
 * «Salario» del 25 aparecía «Vencido hace 1 día» mientras el ingreso ya estaba anotado ahí abajo,
 * en la misma pantalla.
 *
 * ## La unidad es la ocurrencia del MES CALENDARIO — y esto ya se decidió dos veces
 *
 * [period] es `"YYYY-MM"` y es el mes al que pertenece la ocurrencia: el «arriendo de agosto», el
 * «salario de agosto». Un recurrente cierra el mes que se está viviendo, y solo una vez que su día
 * llegó (ver `GET /api/payments/occurrences`).
 *
 * **El primer intento usó el «vencimiento vigente» —la fecha que devuelve `dueDateFor`— y eso fue
 * un bug con plata adentro.** El razonamiento parecía sólido: es el mismo criterio con el que
 * `reminderKeyFor` deduplica los avisos, así que las dos mitades del sistema hablarían del mismo
 * mes. Lo que ese razonamiento pasaba por alto es que `dueDateFor` **rueda con la ventana de
 * gracia**: para una regla de día 1 o 2, durante la última semana del mes el vencimiento vigente
 * ya es el del mes SIGUIENTE. La app terminaba preguntando «¿ya pagaste el arriendo?» sobre
 * septiembre el 27 de agosto y ofreciendo, como respuesta, el pago de agosto — con el monto
 * exacto, así que ni siquiera saltaba el aviso de monto distinto. Confirmarlo apagaba el arriendo
 * de septiembre: fuera de «Próximos», fuera del barrido, sin correo.
 *
 * Las dos nociones son distintas y cada una sirve para lo suyo. El **aviso** pregunta «¿qué es lo
 * próximo que vence?», y ahí rodar es correcto. La **ocurrencia** pregunta «¿el de este mes ya
 * pasó?», y ahí rodar es exactamente el error. Que coincidan casi siempre es lo que hizo que el
 * primer intento se viera bien.
 *
 * Si alguna vez te dan ganas de «unificar» esto de vuelta contra `dueDateFor`: es este bug.
 *
 * Como consecuencia, «ya ocurrió» es por mes: cerrar agosto no dice nada de septiembre, y al mes
 * siguiente el recurrente vuelve a estar pendiente solo.
 *
 * ## [eventId] puede ser null, y la diferencia importa
 *
 * - **Con movimiento**: el dueño confirmó *cuál* movimiento fue. Es un hecho exacto, no una
 *   adivinanza, y por eso vale más que cualquier heurística: la app propone, él decide.
 * - **Sin movimiento** (`null`): el viejo «Ya lo pagué» / «Ya me llegó», que cerraba el periodo
 *   cuando no había nada que emparejar (lo pagó en efectivo, todavía no lo anotó, lo anotó en otra
 *   cuenta). Cierra el periodo igual, pero sin nada que lo respalde.
 *
 *   **La app ya no crea ninguno.** El dueño lo cortó: *«no me debería dejar hacer check sin que el
 *   movimiento asociado exista»*, y tenía razón por donde más duele — un sello sin evidencia apaga
 *   el aviso de una deuda que puede seguir viva, y después no queda nada en pantalla que permita
 *   notarlo. Donde antes había un «ya lo pagué» ahora hay un «Anotar el movimiento», que abre la
 *   hoja de Agregar con los datos del recurrente puestos. Esta rama sigue acá porque en la base de
 *   producción hay sellos viejos hechos así: se muestran diciendo que están marcados a mano y con
 *   un «Quitar la marca», que es la única forma honesta de deshacer algo que no tiene nada detrás.
 *
 * Una ocurrencia **con** movimiento solo vale mientras ese movimiento siga vivo y sin anular: si
 * se anula o desaparece, la ocurrencia deja de contar y el pago vuelve a estar pendiente. Es el
 * lado seguro del error — volver a avisar de más molesta, callar una deuda real cuesta plata.
 */
@Serializable
data class RecurringOccurrence(
    val ruleId: String,
    /** `"YYYY-MM"` — el periodo del **vencimiento**, ver el KDoc de arriba. */
    val period: String,
    /** El movimiento que fue esta ocurrencia, o `null` si el dueño la cerró sin emparejar nada. */
    val eventId: String? = null,
    val confirmedAt: Long = 0L,
)

/** Body de `POST /api/recurring-rules/{id}/occurrence`. */
@Serializable
data class MarkOccurrenceRequest(
    val period: String,
    /** `null` = «ya lo pagué / ya me llegó», sin movimiento que emparejar. */
    val eventId: String? = null,
)

/**
 * Body de `POST /api/recurring-rules/{id}/occurrence/rechazo`: **«no, ese movimiento no es esto»**.
 *
 * ## Por qué el «no fue este» dejó de vivir en la pantalla
 *
 * Hasta hoy el rechazo era un `var` de Compose (`descartadas` en `TransactionsScreen`): se perdía
 * al recargar, y estaba bien que así fuera, porque rechazar una PROPUESTA no es un hecho sobre la
 * plata de nadie — la propuesta se volvía a ofrecer y el dueño la volvía a ignorar, gratis.
 *
 * Desde que Movi **empareja solo** cuando está seguro, ese razonamiento se dio vuelta. Si el
 * emparejamiento automático se equivoca y el dueño lo rechaza, un rechazo que solo vive en la
 * pantalla haría que la siguiente lectura volviera a emparejar lo mismo: el periodo se daría por
 * ocurrido otra vez, sin que nadie lo tocara, y el único modo de que dejara de pasar sería no
 * volver a abrir la app. O sea que ahora el «no» sí tiene que sobrevivir a un F5.
 *
 * ## La clave es (regla, movimiento), nunca el movimiento solo
 *
 * Misma semántica que `claveDescartada` en la pantalla, por el mismo motivo: con «Agua», «Gas» e
 * «Internet» todas en «Servicios», el pago del gas se propone en las tres. Rechazarlo en la regla
 * del agua —correcto, no era el agua— no puede quitárselo a la regla del gas, que es donde sí era
 * el bueno.
 */
@Serializable
data class RechazarOcurrenciaRequest(
    /** El movimiento que NO es la ocurrencia de esta regla. */
    val eventId: String,
)

/**
 * Lo que `GET /api/payments/occurrences` le cuenta a la pantalla sobre **el periodo que está en
 * juego** de un recurrente: si ya se dio por ocurrido, y si no, qué movimientos podrían serlo.
 *
 * Va en una respuesta aparte de `/api/payments/upcoming` a propósito: ese endpoint lo consume el
 * APK que el dueño ya tiene instalado, y agregarle campos (o peor, un valor nuevo al enum
 * `PaymentStatus`) rompería su deserialización. Un endpoint nuevo lo ignora quien no lo conoce.
 *
 * @param period    el **mes en curso**, `"YYYY-MM"` — de lo que habla esta entrada. Nunca es el
 *                  mes siguiente: ver arriba por qué usar el vencimiento vigente fue un bug.
 *                  (`/api/payments/upcoming` sí rueda, y ahí corresponde.)
 * @param dueDate   la fecha en que ese mes vence para esta regla, ISO `"2026-08-25"`, recortada al
 *                  largo del mes.
 * @param occurred  `true` = el dueño ya lo dio por ocurrido; entonces [candidates] va vacío.
 * @param eventId   con qué movimiento quedó emparejado, si quedó con alguno.
 * @param candidates lo que la app **propone** cuando todavía no está cerrado, del más probable al
 *                  menos (ver `occurrenceCandidatesFor` en el server para por qué el monto ordena
 *                  y no filtra). Que haya candidatos significa que Movi **no** estuvo seguro: con
 *                  un único movimiento concluyente empareja solo y esta lista va vacía (ver
 *                  [OccurrenceState.automatica]); con cero o con dos, pregunta.
 * @param derivadaDeUnMovimiento ver abajo.
 * @param montoDelPago cuánta plata prueba la fila derivada, y con [monedaDelPago] en qué moneda:
 *                  el monto **no** decide si el periodo quedó saldado (no puede), así que se
 *                  muestra. Ver abajo.
 */
@Serializable
data class OccurrenceState(
    val ruleId: String,
    val period: String,
    val dueDate: String,
    val occurred: Boolean,
    val eventId: String? = null,
    val confirmedAt: Long = 0L,
    val candidates: List<FinancialEvent> = emptyList(),
    /**
     * **No hay sello que borrar: esto se dedujo de un movimiento que ya existe.**
     *
     * Es `true` en dos casos, y los dos comparten exactamente esa consecuencia:
     *
     *  1. las reglas SINTÉTICAS —la cuota de un crédito ([CREDIT_RULE_PREFIX]) y el pago de una
     *     tarjeta ([CARD_RULE_PREFIX])—, que no se sellan en `recurring_occurrences` y nunca lo
     *     harán: ahí el pago **mueve la deuda**, y ese hecho es más fuerte que un sello. El server
     *     lo lee del movimiento (ver `PagosDeDeuda.kt`) en vez de pedirle al dueño que confirme
     *     por segunda vez algo que ya registró;
     *  2. las que Movi **emparejó sola** con un movimiento concluyente (ver [automatica]), que
     *     tampoco escriben nada: se derivan en cada lectura.
     *
     * ## Por qué la pantalla TIENE que distinguirlo
     *
     * Porque **«Deshacer» no existe para esto**. Un sello a mano se borra con un DELETE; una
     * ocurrencia derivada solo desaparece si desaparece el movimiento que la prueba, y no hay
     * ningún endpoint que «desderive» nada. Una fila en «Ya ocurrieron» con un «Deshacer» que no
     * hace nada es un control muerto —el error exacto que este repo ya cometió una vez— así que
     * la fila derivada se pinta sin él y dice de dónde sale.
     *
     * Por eso este campo viaja también en las automáticas, aunque esas SÍ se puedan revertir: el
     * botón que corresponde ahí no es «Deshacer» (no hay fila que borrar) sino «no fue este», que
     * es otro endpoint. Un cliente viejo que solo conoce este campo se queda sin ofrecer nada, que
     * es lo correcto; uno nuevo lee [automatica] y ofrece el rechazo.
     *
     * Es un CAMPO nuevo con default y no un valor nuevo en ningún enum, por lo de siempre: un
     * campo lo ignora el cliente que no lo conoce, un valor de enum le revienta la
     * deserialización. (Este endpoint es nuevo igual, pero la regla vale para los dos.)
     */
    val derivadaDeUnMovimiento: Boolean = false,
    /**
     * **Además de derivada: esto lo dedujo Movi, y el dueño lo puede rechazar.**
     *
     * `true` solo cuando el server encontró **exactamente un** movimiento concluyente en la
     * ventana del vencimiento y lo emparejó sin preguntar (ver `ocurrenciaConcluyente` en
     * `OccurrenceMatching.kt`). Nada se escribió en `recurring_occurrences`: la marca se vuelve a
     * deducir en cada lectura, así que anular o editar el movimiento la hace desaparecer sola.
     *
     * ## La diferencia con [derivadaDeUnMovimiento], dicha corta
     *
     *  - [derivadaDeUnMovimiento] = «**no hay sello que borrar**». Es lo que la pantalla necesita
     *    para no pintar un «Deshacer» muerto.
     *  - [automatica] = «**además, esto lo dedujo Movi**», y por eso se puede rechazar con
     *    `POST /api/recurring-rules/{id}/occurrence/rechazo` ([RechazarOcurrenciaRequest]). Una
     *    cuota de crédito no: ahí el movimiento mueve la deuda y no hay nada que discutir; lo
     *    único que la revierte es borrar o anular el pago.
     *
     * Toda automática viene con [derivadaDeUnMovimiento] en `true`, nunca al revés.
     *
     * Por qué un campo nuevo y no un valor de enum: lo de siempre (ver arriba).
     */
    val automatica: Boolean = false,
    /**
     * El nombre del **período del dueño** en que cae [dueDate] (`"2026-10"`), para decirlo en
     * pantalla. [period] es la clave del sello —el mes de calendario del vencimiento, estable
     * aunque cambie el corte— y con corte 25 un pago del 28 de septiembre tiene clave
     * `"2026-09"` pero es «el de octubre», igual que en Movimientos. `null` desde un server viejo:
     * ahí se muestra [period], que era lo de antes.
     */
    val periodoDelDueno: String? = null,
    /**
     * **Cuánta plata prueba esta fila** —y su moneda—, cuando sale de un movimiento y no de un
     * sello. `null` en las selladas a mano: ahí lo que hay es la palabra del dueño.
     *
     * Existe porque el monto **no filtra**: un abono de $50.000 sobre un extracto de $1.008.902
     * salda el periodo igual que un pago completo y apaga el recordatorio. No puede filtrar —movi
     * no conoce el extracto, y ni el saldo de la tarjeta ni la cuota del crédito son comparables
     * con lo que se movió; el porqué largo está en `PagosDeDeuda.kt`—, así que la fila hace lo
     * único honesto que queda: **decir el número**, para que «ya ocurrió» no tape un abono
     * simbólico.
     *
     * Es la plata que SALIÓ DE LA CUENTA, no la que bajó la deuda: en una cuota son distintas a
     * propósito (capital contra cuota). Ver `plataQueSalio`.
     */
    val montoDelPago: Long? = null,
    val monedaDelPago: String? = null,
)
