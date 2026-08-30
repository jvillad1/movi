package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class Scope { SELF, FAMILY }

@Serializable
data class FinanceSummary(
    val scope: Scope,
    val balance: Long,
    val ingresos: Long,
    val egresos: Long,
    /**
     * Cuántos **movimientos** no anulados tiene el usuario (todas las cuentas, no solo el mes ni
     * el [scope]) — el server ya los carga completos para calcular este resumen
     * ([com.jvillada.movi.server.balance.loadNonVoidedEvents]), así que este campo es
     * prácticamente gratis. Existe para que el Dashboard pueda saber "¿esta cuenta tiene algún
     * movimiento?" — o, más importante, "¿el usuario ya anotó algo?" para apagar la guía de
     * primeros pasos — sin traerse la lista completa con `GET /api/events`.
     *
     * **Movimientos, no filas**: quién es cuál lo decide [movementCount] en `:core`, y el porqué
     * de cada regla está en su KDoc. En resumen: la apertura de cuenta no cuenta (F54) y un
     * traspaso cuenta **una vez**, no dos, aunque sean dos eventos — igual que en Movimientos,
     * donde las dos patas se ven como un solo renglón.
     *
     * Con default para que un cliente viejo (que no lo espera) y un server viejo (que no lo
     * manda) sigan deserializando sin romperse.
     */
    val eventCount: Int = 0,
)

@Serializable
data class Holding(
    val name: String,
    val sub: String,
    val amount: Long,
    val change: Double,
)

@Serializable
data class CreditTerms(
    val accountId: String,
    val bank: String,
    val principal: Long,        // capital original (COP)
    val rateEa: Double,         // % EA, p.ej. 17.46
    val termMonths: Int,
    val installment: Long,      // cuota mensual total (incl. seguros)
    val dayOfMonth: Int,        // día de pago
    val startDate: String,      // ISO "2026-06-01" (desembolso)
    val notes: String? = null,
    /** Ver [RecurringRule.remindMe]: la cuota de este crédito entra (o no) al barrido de avisos. */
    val remindMe: Boolean = true,
    /**
     * **Libranza**: la cuota la retiene el empleador del sueldo antes de depositarlo.
     *
     * Cambia lo que Movi tiene que pedirle al dueño. Una cuota normal es un gasto que él paga y
     * registra; esta **ya se pagó sola** y la plata nunca llegó a su cuenta. Pedirle que la
     * registre como gasto haría que descuente dos veces —el sueldo que ve ya viene neto— y no
     * pedirle nada dejaría la deuda congelada.
     *
     * Ver [PAYROLL_DEDUCTION_CATEGORY] para cómo se registra sin romper ninguna de las dos cosas.
     */
    val payrollDeduction: Boolean = false,
)

@Serializable
data class CreditSummary(
    val account: Account,       // cuenta LOAN con deuda derivada en balance
    val terms: CreditTerms?,    // null si la cuenta LOAN aún no tiene términos
    val paidPct: Double?,       // 1 − deuda/principal clampado a [0,1]; null sin términos
    /**
     * Movimiento que el server acabó de registrar, o null si no registró ninguno.
     *
     * Solo lo llena `POST /api/credits/{id}/balance-adjustment`; en GET/PUT es null. Está acá
     * para que el cliente offline-first pueda espejar en su DB local el evento exacto que
     * escribió el server, en vez de adivinarlo: [com.jvillada.movi.shared.repository]
     * lo inserta ya marcado como sincronizado. Sin esto, en Android el ajuste no aparecía
     * en movimientos ni movía el saldo cacheado de la cuenta.
     */
    val adjustmentEvent: FinancialEvent? = null,
    /**
     * ¿Esta cuenta LOAN tiene **algún** movimiento vivo (no anulado)?
     *
     * Existe por una sola pregunta que `paidPct` no sabe responder: **una deuda en $0 puede
     * significar dos cosas opuestas.** O el crédito está pagado —se llegó ahí con eventos: la
     * apertura, las cuotas, los abonos— o todavía no se registró nada y el $0 es "no sé", no
     * "cero". Desde que un crédito se puede crear con deuda inicial $0 (para que el desembolso
     * sea lo que crea la deuda, ver `POST /api/credits`), el segundo caso existe de verdad, y
     * sin este dato la tarjeta de Créditos anunciaba **«100% pagado» sobre un crédito de
     * $257.000.000 recién creado**, con la barra llena. El error caro que esta rama evitaba —la
     * deuda contada dos veces— venía con un aviso a la vista; este no tenía ninguno, y encima
     * erraba hacia el lado optimista.
     *
     * "Vivo" y no "alguna vez": sale de los mismos eventos no anulados de los que se deriva la
     * deuda, así que anular el desembolso devuelve la tarjeta a "falta registrarlo" en vez de
     * dejarla diciendo que está pagado.
     *
     * Default `true` = la respuesta conservadora para un cliente que hable con un server viejo
     * que no manda el campo: se muestra el porcentaje, que es lo que se mostraba antes.
     */
    val hasMovements: Boolean = true,
    /**
     * Las dos patas del **desembolso que el alta acaba de registrar**, o null si no registró
     * ninguno (que es siempre, salvo en `POST /api/credits` con [CreateCreditRequest.disbursement]).
     *
     * Existe por el mismo motivo que [adjustmentEvent], y no es simetría de adorno: en Android
     * Movimientos, Cuentas y el detalle leen de SQLDelight, y el `SyncEngine` **solo empuja,
     * nunca trae**. Sin estas dos patas en la respuesta, el desembolso que el server escribió en
     * su transacción quedaría invisible en el teléfono —la plata no aparecería en la cuenta
     * corriente— hasta que alguien abriera la web. El cliente las espeja tal cual, con los ids
     * que el server les puso, ya marcadas como sincronizadas.
     *
     * Default null para que un cliente que hable con un server viejo (que no manda el campo)
     * siga deserializando igual que siempre.
     */
    val disbursement: TransferResult? = null,
)

/**
 * Prefijo de los ids de las reglas recurrentes sintéticas derivadas de credit_terms.
 * Compartido entre el server (que las genera) y la UI (que las distingue de las reales).
 */
const val CREDIT_RULE_PREFIX = "credit_"

/**
 * **A qué cuenta le entró la plata de un crédito recién desembolsado, y cuánto.**
 *
 * Viaja adentro de [CreateCreditRequest] —no en un `POST /api/transfers` aparte— porque el
 * desembolso y el crédito tienen que nacer juntos. El porqué completo está en
 * [CreateCreditRequest.disbursement].
 *
 * El **monto es editable y no se asume igual al capital**: muchos créditos desembolsan neto de
 * costos (estudio, seguros, papeleo, financiados adentro del propio crédito). Si de un capital de
 * $257.000.000 al dueño le entraron $250.000.000, las dos cifras son ciertas y distintas, y Movi
 * tiene que poder decir las dos. Qué pasa con los $7.000.000 de diferencia también está en
 * [CreateCreditRequest.disbursement].
 *
 * Los ids de las patas NO viajan acá, a diferencia de [CreateTransferRequest], donde los pone el
 * cliente para que un reintento no duplique el traspaso. Este endpoint no puede ser idempotente
 * de todos modos —cada POST crea una cuenta nueva—, así que ids del cliente no comprarían nada.
 * El server los genera y los devuelve en [CreditSummary.disbursement], que es lo que el espejo
 * local necesita para escribir exactamente las mismas filas.
 */
@Serializable
data class CreditDisbursement(
    val toAccountId: String,    // cuenta de dinero/inversión (COP) donde el banco depositó
    val amount: Long,           // lo que efectivamente entró a esa cuenta (COP), > 0
)

/**
 * Alta atómica de un crédito: cuenta LOAN + evento de apertura + términos (+ desembolso, si lo
 * hay) en una sola operación server-side.
 */
@Serializable
data class CreateCreditRequest(
    val name: String,           // nombre de la cuenta LOAN a crear
    val initialDebt: Long,      // deuda actual (COP) — genera el evento "Deuda inicial"
    val terms: CreditTerms,     // accountId se ignora; el server asigna el de la cuenta nueva
    /**
     * **El desembolso, cuando es un crédito que el dueño acaba de recibir.** `null` = el crédito
     * ya venía de antes y entra a Movi con su deuda de hoy en [initialDebt]: el camino de
     * siempre, el único que existía hasta la Ola 16 y el único que manda el APK 1.9 que el dueño
     * tiene instalado. Por eso el campo es opcional con default null y no un parámetro más.
     *
     * ## Por qué viaja con el alta y no como un segundo paso
     *
     * Es el mismo argumento con el que se escribió `POST /api/credits`: el flujo cliente en dos
     * pasos *«dejaba cuentas huérfanas/duplicadas ante fallos parciales»*. Acá el estado parcial
     * tiene nombre y cifra: crear el crédito, ser interrumpido, y quedarse con un crédito de
     * $257.000.000 que la app declara **«100% pagado»** — deuda de menos y patrimonio de más, en
     * la dirección optimista, que es la peor. Si el desembolso nace con el crédito, esa ventana
     * no existe. El aviso «Falta registrar el desembolso» de `progresoDeCredito` pasa a ser la
     * red para los casos raros (anular el desembolso después), no el camino normal.
     *
     * ## Qué pasa con la diferencia entre el capital y lo que entró
     *
     * **La cubre [initialDebt], y la calcula el server: con `disbursement != null`, `initialDebt`
     * TIENE que llegar en 0** (si no, 400 con [DISBURSEMENT_WITH_INITIAL_DEBT]). El server abre
     * la cuenta con `terms.principal − disbursement.amount` de deuda —ver
     * [aperturaDeCreditoDesembolsado]— y encima le suma la pata del desembolso, así que la deuda
     * del crédito arranca valiendo **exactamente el capital** y el efectivo sube **exactamente lo
     * que entró**.
     *
     * No es un refinamiento contable: si el desembolso fuera lo único que crea deuda, un crédito
     * desembolsado neto ($250M de $257M) nacería debiendo $250M y `paidPct` diría **«2% pagado»
     * sobre un crédito que nadie ha pagado todavía** — la misma familia de mentira optimista que
     * la ola anterior cerró, más chica pero por la misma puerta.
     *
     * Y derivarla en el server, en vez de dejar que el cliente mande los dos números, es lo que
     * hace estructuralmente imposible el otro error, el caro: la deuda contada dos veces
     * ($514M por $257M reales). Con esta regla no hay ningún cuerpo, ni siquiera escrito a mano,
     * que pueda pedir deuda inicial **y** desembolso a la vez.
     */
    val disbursement: CreditDisbursement? = null,
)

/**
 * Lo que se le dice a quien manda una deuda inicial junto con un desembolso. Ver
 * [CreateCreditRequest.disbursement]: los dos números juntos son, exactamente, cómo se cuenta la
 * deuda dos veces.
 */
const val DISBURSEMENT_WITH_INITIAL_DEBT =
    "Un crédito recién recibido no lleva deuda actual: la deuda la arma el desembolso."

/** Ver [validateCreditDisbursement]. La plata de un crédito entra a una cuenta tuya, no a otra deuda. */
const val DISBURSEMENT_TARGET_NOT_MONEY =
    "La plata del crédito entra a una cuenta tuya de dinero o inversión, no a otra deuda."

/** Ver [validateCreditDisbursement]. La cuenta del crédito nace en pesos y el traspaso no cruza monedas. */
const val DISBURSEMENT_ONLY_COP =
    "Por ahora el desembolso solo se puede registrar en una cuenta en pesos."

/** Ver [validateCreditDisbursement]. Más plata que capital = uno de los dos números está mal tecleado. */
const val DISBURSEMENT_OVER_PRINCIPAL =
    "Lo que te entró no puede ser mayor que el capital del crédito. Revisa las dos cifras."

/**
 * ¿Se puede registrar este desembolso junto con el alta del crédito? `null` si sí; si no, **el
 * mensaje en español** que se le muestra al dueño.
 *
 * Mismo idioma que [validateTransfer], y por el mismo motivo: hace falta la misma frase en dos
 * lugares —la hoja de crear crédito, que apaga el botón y explica por qué, y el 422 del server,
 * última defensa para un cliente viejo o un POST a mano—. Con un enum, las dos puntas habrían
 * escrito su propia versión del texto y se habrían ido separando.
 *
 * Las reglas, en orden, y qué mentira evita cada una:
 * - **Falta la cuenta.** No hay a dónde poner la plata.
 * - **La cuenta no es de dinero ni de inversión.** Un «desembolso» a otro crédito o a una tarjeta
 *   no es un desembolso; [validateTransfer] ya lo rechaza del otro lado con sus propias palabras.
 * - **La cuenta no es COP.** La cuenta del crédito se crea siempre en pesos, y un traspaso entre
 *   monedas todavía no existe (ver [validateTransfer]).
 * - **Monto > 0.** Un desembolso de $0 es el camino viejo escrito de la forma difícil, y dejaría
 *   el crédito diciendo «Falta registrar el desembolso» justo después de registrarlo.
 * - **Monto ≤ capital.** Que entre MÁS plata que el capital del crédito significa que uno de los
 *   dos números está mal tecleado, y guardarlo dejaría efectivo sin deuda que lo respalde:
 *   patrimonio inflado, en silencio. Se dice que no en vez de adivinar cuál de los dos corregir.
 */
fun validateCreditDisbursement(principal: Long, destino: Account?, amount: Long): String? = when {
    destino == null -> "Elige a qué cuenta te entró la plata"
    destino.type.group == AccountGroup.DEUDA -> DISBURSEMENT_TARGET_NOT_MONEY
    destino.currency != "COP" -> DISBURSEMENT_ONLY_COP
    amount <= 0L -> "Escribe cuánto te entró a la cuenta"
    amount > principal -> DISBURSEMENT_OVER_PRINCIPAL
    else -> null
}

/**
 * La deuda con la que **abre** la cuenta del crédito cuando hay desembolso: el pedazo del capital
 * que nunca se volvió plata en el bolsillo (costos, seguros, papeleo — financiados adentro del
 * crédito). Sumada a la pata del desembolso da el capital exacto.
 *
 * Es una función y no una resta suelta porque la calculan dos lugares que tienen que dar lo
 * mismo: el server, que la escribe, y la hoja, que la explica antes de guardar. Se pisa en 0 por
 * si acaso — [validateCreditDisbursement] ya rechaza `amount > principal`, y de todos modos una
 * apertura negativa sería una cuenta de deuda que arranca a favor.
 */
fun aperturaDeCreditoDesembolsado(principal: Long, disbursed: Long): Long =
    (principal - disbursed).coerceAtLeast(0L)

/**
 * Ajusta la deuda de un crédito ya existente al saldo real que reporta el banco.
 *
 * Se manda el saldo OBJETIVO, no la diferencia: el server calcula el delta contra los
 * eventos actuales de la cuenta y lo registra como un movimiento visible. Si el cliente
 * mandara el delta, una vista desactualizada (la deuda se mueve a diario por intereses)
 * dejaría el saldo en otra cifra.
 */
@Serializable
data class AdjustCreditBalanceRequest(
    val targetBalance: Long,    // deuda real (en la moneda de la cuenta), >= 0
)

/**
 * Techo defensivo para la deuda objetivo de un crédito (COP). No es un límite de negocio:
 * atrapa el dedazo de teclear dígitos de más al copiar el saldo de la banca en línea.
 *
 * Vive en `:core` a propósito — el server lo aplica y la hoja de ajuste lo espeja para poder
 * explicar el rechazo *antes* de llamar, en vez de que el usuario reciba un error genérico.
 *
 * **Deuda conocida, anotada y NO arreglada acá (revisión de la Ola 16):** este techo solo lo
 * aplica `POST /api/credits/{id}/balance-adjustment`. `POST /api/credits` **no lo mira**, así que
 * un alta con capital 9×10¹⁵ responde 201. Y por el mismo camino viejo, un `startDate` más largo
 * que su columna o un banco de más de 80 caracteres revientan en el INSERT y salen como **500**
 * en vez de 400. El alta con desembolso quedó mejor validada que la de siempre (le agregamos el
 * `LocalDate.parse` y su guarda de año); emparejar las dos es su propia tarea, no la de la rama
 * que estrenó el desembolso.
 */
const val MAX_CREDIT_DEBT_COP = 1_000_000_000_000L // un billón de pesos

/**
 * F26: nace con el alta manual (nombre, objetivo, cuenta donde se ahorra, fecha opcional) — antes
 * el modelo existía pero no había forma de crear una. [saved] es SIEMPRE derivado del saldo de
 * [accountId] (ver `GET /api/goals` en `GoalRoutes.kt`), nunca un aporte manual: si la plata está
 * en la cuenta, cuenta. El cliente lo manda en 0 al crear/editar y el server lo ignora — el campo
 * solo tiene sentido en la respuesta.
 */
@Serializable
data class Goal(
    val id: String = "",
    val name: String,
    val target: Long,
    val accountId: String,
    val targetDate: String? = null,   // ISO "2027-01-01", opcional
    val saved: Long = 0,
)

@Serializable
data class RecurringRule(
    val id: String,
    val name: String,
    val category: String,
    val amount: Long,
    val dayOfMonth: Int,
    val type: TransactionType,
    /**
     * **Desde cuándo corre esta regla.** ISO `"2026-09-01"`, o `null` = desde siempre.
     *
     * Existe por la cuota de un crédito. El dueño registró un préstamo desembolsado el 1 de
     * septiembre con pago el día 1, y Movi le anunció la primera cuota **para ese mismo día**:
     * *«no entiendo por qué quedó cargado el desembolso el mismo día que es la cuota; normalmente
     * un desembolso es un mes aproximadamente antes de la primera cuota»*. Tenía razón — la regla
     * sintética se armaba solo con el día del mes e ignoraba la fecha de desembolso.
     *
     * Con esto, una ocurrencia **anterior o igual** a esta fecha no existe: la primera cuota es
     * la primera vez que cae el día de pago **después** del desembolso.
     *
     * `null` para las reglas que el dueño escribió a mano (un salario, un gimnasio): esas no
     * tienen «desembolso» y corren desde siempre, como hasta ahora.
     */
    val activeFrom: String? = null,
    /**
     * Ola 9 · D: **a qué cuenta entra (o de cuál sale) esto todos los meses.** Un movimiento
     * siempre tuvo cuenta; una regla recurrente no, así que Movi sabía que el salario entra el
     * 25 pero no dónde — y al ofrecer «esto se repite» desde un movimiento (Ola 9 · B) el dato
     * se perdía justo en el paso que lo tenía a mano.
     *
     * **Opcional a propósito, y opcional para siempre.** Las reglas que el dueño ya tiene en
     * producción nacieron sin cuenta: exigirla ahora sería inventarle un dato que nadie le
     * pidió, y bloquear la edición de lo que ya escribió. `null` significa exactamente «no se
     * sabe», y la pantalla lo muestra así, sin drama.
     *
     * Si la cuenta se borra, la regla **no** se borra: el server la deja en `null` (misma
     * política que ya usaba `AccountRoutes` con la pata hermana de un traspaso — se suelta la
     * referencia, no se destruye el hecho). El plan «arriendo, día 5, $1.800.000» sigue siendo
     * cierto aunque la cuenta de la que salía ya no esté en Movi.
     *
     * **En un PUT este campo tiene tres estados, y la diferencia importa:**
     *  - `null` → «no lo toques». Es lo que manda un cliente que no conoce el campo (el APK 1.6
     *    que el dueño ya tiene instalado). Sin esta regla, corregir el monto desde el teléfono
     *    le borraba en silencio la cuenta que había puesto desde la web.
     *  - `""` → «quitá la cuenta»: el dueño eligió «Sin cuenta» a propósito.
     *  - un id → esa cuenta, si es suya; si no lo es, se guarda `null` y la respuesta lo dice.
     *
     * En un POST no hay nada que preservar: `null` y `""` significan lo mismo (sin cuenta).
     */
    val accountId: String? = null,
    /**
     * ¿Este pago entra al barrido de recordatorios? Lo decide el dueño con la casilla
     * «Recordarme unos días antes» al crear o editar la regla.
     *
     * Default `true` a propósito, y en tres lugares que tienen que coincidir: acá (clientes y
     * server viejos que no mandan el campo siguen deserializando), en la columna
     * `recurring_rules.remind_me` (`.default(true)`, así las filas que ya existían siguen
     * avisando) y en la casilla de la UI, que nace marcada. Cualquier otro default cambiaría
     * en silencio el comportamiento que el dueño ya tiene.
     *
     * Apagarlo silencia SOLO este pago: sigue apareciendo en Próximos y en los totales — no es
     * un "archivar", es "no me avises". El filtro vive en
     * `com.jvillada.movi.server.reminders.selectDueForReminder`.
     */
    val remindMe: Boolean = true,
)

@Serializable
data class Budget(
    val category: String,
    val monthlyLimit: Long,
)

// F17: cuerpo de PUT /api/budgets/{category}/rename — la categoría vieja va en la URL, la
// nueva en el body. Tipo propio (no reusar Budget) porque el monto no se manda: el server
// conserva el límite existente, renombrar y cambiar el monto son dos operaciones separadas.
@Serializable
data class RenameBudgetRequest(
    val newCategory: String,
)

/**
 * Estado de un mensaje del banco. Vocabulario ÚNICO para todo el sistema: la ingesta del
 * server, la bandeja, el detalle y el contador del Inicio hablan de los mismos tres valores.
 *
 * El dueño del estado es el server: la ingesta (`POST /api/sms/sync`) siempre escribe
 * [SMS_STATE_PENDING] — nunca el `state` que venga en el payload — y solo
 * `POST /api/sms/{id}/confirm` e `/ignore` lo mueven.
 *
 * Hubo un segundo nombre para el recién llegado, `"new"`, que escribía la ingesta mientras
 * todos los lectores filtraban por `"pending"`: los SMS capturados quedaban invisibles para el
 * Inicio y sin botón «Revisar» en la bandeja. `Migrations.renameLegacyNewSmsStateToPending()`
 * convierte las filas viejas; no queda código escribiendo `"new"`.
 */
const val SMS_STATE_PENDING = "pending"

/** El dueño lo revisó y lo convirtió en movimiento. */
const val SMS_STATE_CONFIRMED = "confirmed"

/** El dueño lo descartó: no es un movimiento suyo. */
const val SMS_STATE_IGNORED = "ignored"

@Serializable
data class SmsMessage(
    val id: String,
    val time: String,
    val bank: String,
    val text: String,
    /** Uno de [SMS_STATE_PENDING], [SMS_STATE_CONFIRMED] o [SMS_STATE_IGNORED]. Lo fija el server. */
    val state: String,
    val det: String,
)

@Serializable
data class ParsedSms(
    val amount: Double,
    val merchant: String,
    val type: TransactionType,
    val category: String,
)

@Serializable
enum class ChatRole { USER, ASSISTANT }

@Serializable
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    // F32: adjunto opcional — foto de un recibo, extracto u oferta del banco. Nulo en casi
    // todos los mensajes; el default mantiene compatibilidad de red con clientes viejos que
    // solo mandan role+content (ver ChatModelTest).
    val imageBase64: String? = null,
    val imageMime: String? = null,
)

@Serializable
data class AiChatRequest(val messages: List<ChatMessage>)

@Serializable
data class AiChatResponse(val text: String)

/**
 * Días de anticipación con los que el barrido avisa un vencimiento.
 *
 * Vive en `:core` porque lo necesitan los dos lados: el server lo usa como fallback de
 * `REMINDER_LEAD_DAYS` y la UI lo usa para poder decir, debajo de la casilla «Recordarme unos
 * días antes», *cuántos* días antes avisa. Sin un número compartido la casilla tendría que
 * inventarse uno — y prometer algo distinto de lo que hace el barrido.
 */
const val DEFAULT_REMINDER_LEAD_DAYS: Int = 3

@Serializable
enum class PaymentStatus { OVERDUE, DUE_TODAY, DUE_SOON, UPCOMING }

@Serializable
data class UpcomingPayment(
    val rule: RecurringRule,
    val dueDate: String,    // ISO "2026-06-05", current month
    val daysUntil: Int,     // negative if overdue
    val status: PaymentStatus,
)
