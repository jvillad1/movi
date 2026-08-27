package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.ui.components.categoriaSirveParaTipo
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
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
    if (event.category.trim() == TRANSFER_CATEGORY) return false
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
fun prefillFrom(event: FinancialEvent): RecurringPrefill = RecurringPrefill(
    name = prefillNameFor(event),
    amount = event.amount,
    category = event.category.trim(),
    type = event.type,
    dayOfMonth = epochMillisToAppDate(event.timestamp).dayOfMonth,
    accountId = event.accountId.takeIf { it.isNotBlank() },
)

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
