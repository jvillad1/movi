package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.ui.components.categoriaSirveParaTipo
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.epochMillisToAppDate

/**
 * Ola 9 · B — **«¿esto se repite todos los meses?», preguntado en el único momento en que el
 * dueño lo sabe: justo después de anotarlo.**
 *
 * Hasta ahora tenía que acordarse solo, ir a Recurrentes y volver a escribir todo. Acá se le
 * ofrece el formulario ya lleno con lo que acaba de anotar.
 *
 * ## Primero se guarda, después se ofrece
 *
 * El movimiento se guarda pase lo que pase. Esto no es un paso del alta ni un diálogo que haya
 * que resolver: aparece **después**, con el movimiento ya en la base. Ignorarlo, cerrarlo o irse
 * a otra pantalla no pierde nada.
 *
 * ## Por qué es un OFRECIMIENTO y no una pregunta
 *
 * El riesgo real de esta función es volverse insoportable: el dueño anota comida varias veces
 * por semana y que le pregunten cada vez si el almuerzo es recurrente sería motivo suficiente
 * para dejar de anotar. Se consideraron los filtros "inteligentes" —solo montos grandes, solo
 * ciertas categorías, solo lo que parece un compromiso— y se descartaron **a propósito**: son
 * adivinanzas sin datos detrás (la base del dueño arrancó vacía este mes) y su forma de fallar
 * es la peor posible — esconder la función justo en el estreno, cuando anota su primer arriendo
 * y su primera nómina, que es exactamente para lo que la pidió.
 *
 * Lo que se hace en cambio es bajar a cero el costo de decir que no: no hay pregunta que
 * contestar, hay una barra que aparece junto al «guardado», se puede ignorar y se va sola.
 * Ignorarla cuesta cero toques y cero decisiones. Encima de eso, tres guardas que apagan el
 * ofrecimiento cuando ya sabemos que sobra:
 *
 * 1. **Nunca en un traspaso.** `RecurringRule` no modela traspasos: no hay dónde poner la otra
 *    cuenta, y ofrecerlo llevaría a un recurrente que miente sobre lo que pasa cada mes.
 * 2. **Nunca si ya existe un recurrente equivalente** (mismo nombre normalizado, la misma
 *    comparación que usa el resto de Recurrentes — ver [claveDeNombre]). Si ya tiene «Arriendo»,
 *    anotar el arriendo de este mes no se lo vuelve a ofrecer.
 * 3. **Como mucho una vez por "cosa" y por sesión, y como mucho [MAX_SIN_TOMAR] veces por
 *    categoría.** La primera comida de la semana lo ofrece; las otras cuatro, no.
 *
 *    Qué es una "cosa" costó dos intentos, los dos por el mismo error de tomar UN identificador
 *    y esperar que sirva para todo:
 *
 *    - **El nombre** (o sea, la nota) no sirve: con notas distintas —«Almuerzo lunes», «Almuerzo
 *      con Ana»— cada gasto de comida volvía a disparar la barra, que es el modo de falla que
 *      esta guarda existe para evitar.
 *    - **La categoría sola** tampoco: la segunda cosa genuinamente recurrente de una categoría
 *      no se ofrecía en toda la sesión. Netflix y Spotify bajo «Entretenimiento» la misma noche
 *      y solo Netflix recibía la barra — justo el día de configurar la app, que es el día para
 *      el que la función existe.
 *
 *    La "cosa" es entonces **tipo + categoría + monto exacto** ([throttleKeyFor]), y no depende
 *    de la nota. El monto no es un parche: un recurrente ES un cobro del mismo monto todos los
 *    meses, así que dos montos distintos son dos candidatos distintos — Netflix ($44.900) no es
 *    Spotify ($16.900). Y lo que se repite con monto variable (el almuerzo) no tiene forma de
 *    recurrente, así que ofrecerlo importa menos.
 *
 *    Encima va el techo por categoría, que es lo que evita que un almuerzo de precio distinto
 *    cada día vuelva a nagear: a las [MAX_SIN_TOMAR] barras **no tomadas** de una misma categoría
 *    (tipo incluido, ver [categoryThrottleKeyFor]) se apaga la categoría entera por lo que queda
 *    de sesión. Se cuentan las NO tomadas: aceptar una la descuenta, así que quien está
 *    configurando la app —acepta Netflix, acepta Spotify, acepta HBO— nunca choca contra el
 *    techo. Es la misma medida de siempre, dicha bien: ya se lo ofrecimos y no lo tomó.
 *
 * Y, por construcción, **nunca dos veces por el mismo movimiento**: el ofrecimiento sale una
 * sola vez, del guardado, y no se persiste ninguna cola.
 */
data class RecurringPrefill(
    val name: String,
    val amount: Long,
    val category: String,
    val type: TransactionType,
    val dayOfMonth: Int,
    /** Ola 9 · D: de qué cuenta salió (o a cuál entró). Puede ser null si el movimiento no la traía. */
    val accountId: String?,
    /**
     * **La fecha del movimiento que originó la regla**, ISO `"2026-08-15"` — y con eso, la fecha
     * desde la que la regla corre (ver [RecurringRule.activeFrom]).
     *
     * Es la pieza que evita **contar el pago dos veces**, y hace falta en los dos caminos que
     * llegan acá (la barra de después de guardar y «Esto se repite» desde el detalle del
     * movimiento). El movimiento que origina la regla YA ocurrió y ya está en «Gastos del mes»;
     * sin esta fecha, la regla nace con su vencimiento en el período de ese mismo movimiento y
     * Movi lo propone otra vez —«¿ya pagaste el arriendo de agosto?»— sobre un arriendo que acaba
     * de anotar. Con ella, `dueDateFor` adelanta el primer vencimiento al período siguiente
     * (rueda mientras `due <= activeFrom`), que es justo lo que el dueño espera.
     *
     * `null` = «desde siempre», que es como se comportaban todas las reglas hasta esta ola y como
     * siguen naciendo las que se escriben a mano en Recurrentes.
     */
    val activeFrom: String?,
)

/**
 * ¿Se le ofrece convertir [event] en recurrente? Las guardas están explicadas arriba.
 *
 * @param existingRules lo que el dueño ya tiene anotado como recurrente.
 * @param alreadyOffered claves de "cosa" ([throttleKeyFor]) ya ofrecidas en esta sesión (guarda 3).
 */
fun shouldOfferRecurring(
    event: FinancialEvent,
    existingRules: List<RecurringRule>,
    alreadyOffered: Set<String> = emptySet(),
    /**
     * Los cobros que Movi ya conoce como suscripción. Recurrentes muestra reglas y suscripciones
     * en UNA sola lista y las suma juntas en «Gastos recurrentes», así que ofrecer una regla
     * «Netflix» a quien ya tiene la suscripción «Netflix» no solo repite la fila: le **cuenta el
     * cobro dos veces** en el flujo libre. La comparación es la misma que usa esa pantalla.
     */
    existingSubscriptionNames: List<String> = emptyList(),
    /**
     * Clave de categoría ([categoryThrottleKeyFor]) -> cuántas barras de esa categoría se
     * ofrecieron en esta sesión y NO se tomaron. A las [MAX_SIN_TOMAR] la categoría se apaga por
     * lo que queda de sesión (guarda 3).
     */
    sinTomarPorCategoria: Map<String, Int> = emptyMap(),
): Boolean {
    // Un traspaso, por cualquiera de sus dos señas: el enlace entre patas o la categoría
    // reservada. Se miran las dos porque una pata suelta (cuenta borrada) pierde el enlace
    // pero no deja de ser lo que fue.
    if (event.transferId != null) return false
    // Ola 10: **ninguna** categoría reservada, no solo la de traspaso. Acá solo se filtraba
    // «Traspaso», así que un gasto anotado como «Pago de tarjeta» —que ya queda fuera del mes por
    // `isCashFlow`— disparaba además «¿"Pago de tarjeta" se repite todos los meses?». Un
    // movimiento invisible para las cifras y encima propuesto para repetirse todos los meses es
    // el peor de los dos mundos. Lo mismo vale para «Saldo inicial» (una apertura no se repite)
    // y «Cuenta eliminada».
    if (isReservedCategory(event.category)) return false
    if (event.amount <= 0L) return false
    val key = claveDeNombre(prefillNameFor(event))
    if (key.isEmpty()) return false
    // La molestia se mide por cosa y por categoría (ver la guarda 3), el duplicado por nombre.
    if (throttleKeyFor(event) in alreadyOffered) return false
    if ((sinTomarPorCategoria[categoryThrottleKeyFor(event)] ?: 0) >= MAX_SIN_TOMAR) return false
    if (existingRules.any { claveDeNombre(it.name) == key }) return false
    return existingSubscriptionNames.none { claveDeNombre(it) == key }
}

/**
 * Cuántas barras **no tomadas** de una misma categoría se aguantan por sesión antes de apagarla
 * entera. Ver la guarda 3. Tres y no una: el día de configurar la app, varios recurrentes de la
 * misma categoría de una sentada es el caso normal, no el raro.
 */
const val MAX_SIN_TOMAR: Int = 3

/**
 * La clave de "esta cosa exacta ya se ofreció en esta sesión": tipo + categoría + monto. No
 * depende de la nota. Ver la guarda 3 en el KDoc de arriba para por qué no es el nombre y por qué
 * no es la categoría sola.
 */
fun throttleKeyFor(event: FinancialEvent): String =
    "${event.type.name}:${claveDeNombre(event.category)}:${event.amount}"

/**
 * La clave de "cuántas veces le insistí con esta categoría": tipo + categoría, sin el monto. Es
 * la unidad en la que se mide la molestia (ver [MAX_SIN_TOMAR]), no la identidad de la cosa.
 */
fun categoryThrottleKeyFor(event: FinancialEvent): String =
    "${event.type.name}:${claveDeNombre(event.category)}"

/** La misma clave de categoría, pero desde el formulario ya prellenado (para descontar al aceptar). */
fun categoryThrottleKeyFor(prefill: RecurringPrefill): String =
    "${prefill.type.name}:${claveDeNombre(prefill.category)}"

/**
 * El nombre con el que nacería el recurrente: la nota que escribió el dueño («Arriendo agosto»)
 * y, si no escribió ninguna, la categoría — que es lo mismo que QuickAdd ya guarda como
 * descripción cuando la nota va vacía.
 */
fun prefillNameFor(event: FinancialEvent): String =
    event.description.trim().ifEmpty { event.category.trim() }

/**
 * El formulario ya lleno con lo que el dueño acaba de anotar. **El día del mes sale de la fecha
 * del movimiento** (en la zona de la app, no en la del sistema: un gasto de las 9 pm del 31 en
 * Bogotá no puede caer día 1).
 *
 * Todo esto es editable en la hoja: es un formulario prellenado, no una confirmación.
 */
fun prefillFrom(event: FinancialEvent): RecurringPrefill {
    val fecha = epochMillisToAppDate(event.timestamp)
    return RecurringPrefill(
        name = prefillNameFor(event),
        amount = event.amount,
        category = event.category.trim(),
        type = event.type,
        dayOfMonth = fecha.dayOfMonth,
        accountId = event.accountId.takeIf { it.isNotBlank() },
        // La MISMA fecha del movimiento, y por eso la regla no vuelve a proponerlo — ver
        // [RecurringPrefill.activeFrom]. Se saca del mismo `fecha` que el día del mes: son dos
        // caras del mismo dato y calcularlas dos veces era la forma de que se separaran.
        activeFrom = fecha.toString(),
    )
}

/**
 * ¿Se puede ofrecer «esto se repite» sobre este movimiento **desde su hoja de detalle**?
 *
 * Es el hermano de [shouldOfferRecurring] y comparte con él las guardas **estructurales** —lo que
 * no puede ser un recurrente por lo que es— pero **no las de molestia**, y esa diferencia es toda
 * la función:
 *
 * - [shouldOfferRecurring] decide si Movi **interrumpe** al dueño con una barra que él no pidió,
 *   así que lleva encima tres capas de anti-nag (una vez por cosa y por sesión, techo por
 *   categoría, se va sola a los 12 segundos).
 * - Esta decide si se **dibuja una fila** en una hoja que el dueño abrió a propósito, mirando un
 *   movimiento que eligió. Ahí no hay a quién molestar: si la fila no aplica, no se dibuja; si
 *   aplica, se dibuja siempre, todas las veces que la abra. Aplicarle el throttle acá sería
 *   esconderle una acción que vino a buscar.
 *
 * El dueño lo pidió así: *«si no marqué algo recurrente pero lo es, poder hacerlo desde el
 * movimiento luego»*. «Luego» es justamente cuando la barra ya se fue.
 *
 * Las guardas, y por qué cada una:
 *
 * 1. **Ninguna pata de un par** (`transferId`): un traspaso, un pago de cuota o el pago de una
 *    tarjeta son dos movimientos enlazados y `RecurringRule` no modela ninguno de los tres —no
 *    hay dónde poner la otra cuenta—, así que el recurrente mentiría sobre lo que pasa cada mes.
 *    Y la cuota de un crédito **ya tiene** su recordatorio, el que arma el crédito solo.
 * 2. **Ninguna categoría reservada**: «Traspaso», «Saldo inicial», «Pago de tarjeta», «Cuenta
 *    eliminada», «Descuento de nómina» y «Pago de un tercero» son asientos internos de Movi, no
 *    compromisos mensuales. Un saldo inicial no se repite; un descuento de nómina ya lo lleva la
 *    libranza.
 * 3. **Monto > 0**: un recurrente de $0 no es un compromiso.
 *
 * Lo que **no** se decide acá porque necesita red —si ya existe una regla o una suscripción con
 * ese nombre, o si este movimiento ya está sellado como la ocurrencia de alguna— se pregunta al
 * **tocar** la fila, y ahí se explica en vez de crear un duplicado. Ver [equivalenteYaAnotado] y
 * `SeccionEstoSeRepite`.
 */
fun puedeOfrecerseComoRecurrenteDesdeElDetalle(event: FinancialEvent): Boolean {
    // **No unificar estas guardas con las de [nombreRecurrenteDe], por más que se parezcan: las
    // dos funciones contestan preguntas distintas.**
    //
    // Acá se pregunta «¿le ofrezco CREAR una regla desde este movimiento?» y allá «¿este
    // movimiento SE LEE como recurrente?». Para una cuota de crédito ya pagada las respuestas son
    // opuestas a propósito: se lee como recurrente (es lo más grande que sale del bolsillo todos
    // los meses) pero no se puede crear una regla desde ella —`RecurringRule` no modela un par y
    // el crédito ya arma su propio recordatorio—, así que ofrecerlo fabricaría un duplicado que
    // encima mentiría sobre lo que pasa cada mes. Compartir el `transferId != null` fue justamente
    // lo que escondió las cuotas del chip «Recurrentes» hasta este cambio.
    if (event.transferId != null) return false
    if (isReservedCategory(event.category)) return false
    if (event.amount <= 0L) return false
    return claveDeNombre(prefillNameFor(event)).isNotEmpty()
}

/**
 * Los nombres de las suscripciones que **de verdad suman** en «Gastos recurrentes» — las que
 * hacen que anotar una regla con ese nombre cuente el cobro dos veces.
 *
 * Mismo filtro que `resumenRecurrentes`: una candidata que el dueño **descartó** no bloquea nada,
 * porque no está sumando. Vive acá y no dentro de `RecurringOfferGate` para que los dos caminos
 * que ofrecen crear un recurrente —la barra de después de guardar y «Esto se repite» desde el
 * detalle del movimiento— midan lo mismo.
 */
fun nombresDeSuscripcionesQueYaSuman(suscripciones: List<Subscription>): List<String> =
    suscripciones
        .filter { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED }
        .map { it.displayName }

/**
 * **¿Con qué nombre ya está anotado este cobro?** — o `null` si no lo está y se puede crear.
 *
 * Es la guarda anti-duplicado de «Esto se repite», y mira **las tres** puertas por las que un
 * mismo cobro puede estar ya contado:
 *
 * 1. **El sello de ocurrencia** ([selloDeOcurrencia], de `GET /api/events/{id}/occurrence`): este
 *    movimiento ya ES el pago de este mes de una regla que existe. Crear otra sería tener el
 *    arriendo dos veces en «Próximos pagos».
 * 2. **Una regla con el mismo nombre** ([reglas]).
 * 3. **Una suscripción con el mismo nombre** ([suscripcionesQueYaSuman]). Esta faltaba, y era la
 *    puerta más fácil de cruzar: las suscripciones se **auto-descubren**, así que el dueño puede
 *    tener «Netflix» sin haberlo escrito nunca. Recurrentes muestra reglas y suscripciones en UNA
 *    lista y las suma juntas, así que una regla «Netflix» encima de la suscripción «Netflix»
 *    duplica la fila **y** el gasto. La barra de después de guardar ya lo miraba
 *    ([shouldOfferRecurring] con `existingSubscriptionNames`) y el server ya cierra la simétrica
 *    del otro lado (no se auto-descubre una suscripción para la que ya hay regla): faltaba
 *    justamente esta.
 *
 * La comparación es [claveDeNombre] en las tres, que es la misma que usa la pantalla de
 * Recurrentes para decidir que dos filas son la misma cosa.
 *
 * Función pura y afuera del `@Composable` a propósito: decide si se crea o no una fila que suma
 * plata todos los meses, y eso se prueba.
 */
fun equivalenteYaAnotado(
    selloDeOcurrencia: String?,
    reglas: List<RecurringRule>,
    suscripcionesQueYaSuman: List<String>,
    nombre: String,
): String? {
    selloDeOcurrencia?.takeIf { it.isNotBlank() }?.let { return it }
    val clave = claveDeNombre(nombre)
    if (clave.isEmpty()) return null
    reglas.firstOrNull { claveDeNombre(it.name) == clave }?.let { return it.name }
    return suscripcionesQueYaSuman.firstOrNull { claveDeNombre(it) == clave }
}

/**
 * PR 1 del rediseño de Recurrentes (2026-09): **¿este movimiento es una ocurrencia reconocida
 * como recurrente?**, para Movimientos — el chip nuevo y la marca en la fila.
 *
 * Comparte con [equivalenteYaAnotado] las puertas 2 y 3 (una regla con este nombre, una
 * suscripción que ya suma) pero deja afuera **a propósito** la puerta 1, el sello de ocurrencia
 * (`GET /api/events/{id}/occurrence`): esa lectura es POR movimiento, y Movimientos pinta un día
 * entero —o varios— de una sola vez. Pedirle el sello a cada fila visible convertiría una lista
 * de treinta movimientos en una lista de treinta movimientos MÁS treinta viajes de red que crecen
 * con lo que hay en pantalla, y esta lista no tiene el lujo de "una sola cosa a la vez" que sí
 * tiene el detalle de un movimiento (ver `SeccionEstoSeRepite` en `CategorySheets.kt`, que sí paga
 * esa llamada porque ahí solo hay UN movimiento).
 *
 * El nombre alcanza para el caso común: un recurrente de verdad se llama igual mes a mes
 * («Arriendo», «Netflix»), así que compararlo contra las reglas y las suscripciones que YA están
 * cargadas (sin ningún viaje extra) reconoce la enorme mayoría de las filas. Lo que se pierde: un
 * movimiento puntual que quedó sellado con un nombre que el dueño **renombró después** en su
 * regla no se reconoce acá con el nombre nuevo — un caso angosto, y es justo el que ya se resuelve
 * abriendo el detalle, que sí tiene el sello.
 *
 * Estructuralmente, tampoco aplica a lo que [equivalenteYaAnotado] nunca compara: una pata de un
 * par (`transferId` puesto) o una categoría reservada — ninguna de las dos puede ser un
 * recurrente, y sin este corte un «Traspaso» o un «Saldo inicial» podrían, por accidente de
 * nombre, matchear una regla real.
 *
 * **Con una excepción, y es el arreglo entero de este cambio: la cuota de un crédito ya pagada**
 * ([nombreDeCuotaPagada]). Es una pata de un par, así que el corte de arriba la dejaba afuera, y
 * el dueño la echó de menos con todas las letras: *«en recurrentes no estoy viendo los pagos de
 * cuota realizados para mis créditos… me permite entender mi flujo de caja mensual»*. Con
 * $15.500.000 mensuales en cuotas, es lo más grande que sale de su bolsillo todos los meses.
 *
 * @return el nombre con el que ya está anotado, o `null` si no matchea nada.
 */
fun nombreRecurrenteDe(
    event: FinancialEvent,
    reglas: List<RecurringRule>,
    suscripcionesQueYaSuman: List<String>,
): String? {
    // **Antes de cualquier guarda: la cuota de un crédito ya pagada.** No se reconoce por nombre
    // —no hay contra qué compararla, ver [nombreDeCuotaPagada]— y sus dos patas llevan
    // `transferId`, así que el corte de abajo la mataría. Va primero y no adentro del `if` para
    // que quede a la vista que son dos caminos distintos, no una excepción de aquel.
    nombreDeCuotaPagada(event)?.let { return it }
    if (event.transferId != null) return null
    if (isReservedCategory(event.category)) return null
    return equivalenteYaAnotado(
        selloDeOcurrencia = null,
        reglas = reglas,
        suscripcionesQueYaSuman = suscripcionesQueYaSuman,
        nombre = prefillNameFor(event),
    )
}

/**
 * **¿Este movimiento es la cuota de un crédito que el dueño YA pagó?** — y con qué nombre se lee.
 *
 * ### Por qué no se puede reconocer por nombre, que es como se reconoce todo lo demás
 *
 * Los recurrentes de un crédito **no existen como filas**: `GET /api/recurring-rules` devuelve
 * solo lo que hay en la tabla `recurring_rules`, y la regla de cada crédito la fabrica el server
 * al vuelo desde las condiciones del crédito (`CREDIT_RULE_PREFIX`, en `CreditReminders.kt`),
 * únicamente para armar «Próximos pagos» y los recordatorios. O sea que la lista contra la que
 * [equivalenteYaAnotado] compara nombres **nunca contiene** «Cuota de Vehículo», y buscarla ahí
 * no habría encontrado nada por más que se le quitara la guarda del `transferId`.
 *
 * ### Cómo se reconoce entonces: por su forma, que es exacta
 *
 * Un pago de cuota son dos patas enlazadas que escribe [pagoDeCuotaLegs] y nada más
 * (`validarPagoDeCuota` cierra las otras puertas):
 *
 * - **La pata del dinero** — EXPENSE, en la cuenta de donde salió la plata, con [CUOTA_CATEGORY].
 *   Es esta, y es la única que se reconoce acá.
 * - **La pata de la deuda** — INCOME, en la cuenta LOAN, con la MISMA categoría. Es el otro lado
 *   del mismo hecho: contarla también pondría dos filas por una cuota en una lista que el dueño
 *   lee justamente para sumar lo que sale al mes. El `type` es lo que las separa.
 *
 * ### Un pago de tarjeta NO entra, y esa es la parte que hay que no romper
 *
 * También es un par con esta misma forma, pero su pata del dinero lleva [CARD_PAYMENT_CATEGORY]
 * —reservada y excluida de las cifras del mes— porque **el pago de una tarjeta no es un gasto**:
 * las compras ya contaron cuando se hicieron, y contar también el pago sería contar la misma
 * plata dos veces (ver el KDoc de `CreatePagoDeCuotaRequest`). La comparación es contra
 * [CUOTA_CATEGORY] y solo contra ella, así que la tarjeta queda afuera por construcción; hay un
 * test que lo fija.
 *
 * La cuota que paga un tercero (la nómina, Skandia, un familiar) tampoco entra: lleva sus propias
 * categorías reservadas y esa plata no sale del bolsillo del dueño, así que no es su flujo de caja.
 *
 * @return el concepto de la cuota, que **ya nombra el crédito** («Cuota de Vehículo»): lo escribe
 *   [pagoDeCuotaLegs] con el nombre de la deuda, así que no hay que inventarle ninguno. Si por lo
 *   que sea llegara vacío, cae en la categoría, igual que [prefillNameFor] en todos lados.
 */
fun nombreDeCuotaPagada(event: FinancialEvent): String? {
    if (event.type != TransactionType.EXPENSE) return null
    if (event.category.trim() != CUOTA_CATEGORY) return null
    return prefillNameFor(event).takeIf { it.isNotBlank() }
}

/**
 * Ola 9 · D (segundo hallazgo): **si el nombre ya dice qué es, no dejes la categoría en un
 * genérico.** El dueño llamó «Salario» a su recurrente y quedó categorizado como «Otros
 * ingresos», teniendo «Salario» en el catálogo: la hoja arranca en «Otros» y nadie la corrigió.
 *
 * Solo coincidencia EXACTA (sin tildes ni mayúsculas) contra el catálogo del tipo elegido. Nada
 * de parecidos ni de subcadenas: esto **propone**, y una propuesta que se equivoca en silencio
 * le cambia la categoría a alguien que no la pidió. Quien llama tiene además la última palabra
 * —no se aplica si el dueño ya tocó la categoría a mano— así que el peor caso acá es no
 * proponer nada.
 *
 * **Ola 10 (revisión): el filtro por tipo pasa por [categoriaSirveParaTipo], no por el `type`
 * clavado del catálogo.** Esta era la tercera puerta que seguía leyendo el camino viejo, y las
 * dos formas de romperse eran reales: proponer una categoría que el dueño **escondió**, y dejar
 * de proponer una que fijó en «Ambos». Que la propuesta contradiga lo que él acaba de decidir es
 * peor que no proponer.
 */
fun categoriaSugeridaPorNombre(
    name: String,
    type: TransactionType,
    usedCategories: Map<String, Set<TransactionType>> = emptyMap(),
    prefs: Map<String, CategoryPref> = emptyMap(),
): String? {
    val key = claveDeNombre(name)
    if (key.isEmpty()) return null
    return PREDEFINED_CATEGORIES
        .firstOrNull { claveDeNombre(it.name) == key }
        ?.name
        ?.takeIf { categoriaSirveParaTipo(it, type, usedCategories, prefs) }
}
