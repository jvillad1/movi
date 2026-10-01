package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.claveComparableDeNombre
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.nombreDeMovimientoPegaConRegla
import com.jvillada.movi.shared.model.nombraAlDestino
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * **Emparejamiento SUGERIDO: qué movimiento parece ser la ocurrencia de este recurrente.**
 *
 * Esto no marca nada. Devuelve una lista ordenada de propuestas que la pantalla le muestra al
 * dueño para que él confirme («sí, fue este» / «no fue este»). La asimetría del riesgo manda todo
 * el diseño: **marcar de más es peor que el ruido de hoy** — si la app da por ocurrido algo que no
 * ocurrió, el dueño deja de recibir el aviso de una deuda real, y eso cuesta plata. Un ruido de
 * más solo cuesta un toque.
 *
 * ## El monto NO filtra: ordena
 *
 * Palabras del dueño: «hay meses que mi salario es tal cual lo escribí en la base de datos pero
 * otros meses puede ser menos o más dependiendo de retenciones y cosas similares». O sea que el
 * monto de un recurrente es un **estimado, no un contrato**. Exigir monto exacto —o un margen
 * fijo de ±10 % elegido a ojo— haría fallar justo el caso que motivó la función. Así que:
 *
 *  - **Identifica** lo estable: el tipo (ingreso/gasto), la ventana alrededor del vencimiento y la
 *    coincidencia de nombre o categoría. La cuenta suma pero no identifica sola (ver abajo).
 *  - **Ordena** por lo variable: entre los candidatos, el más cercano al monto esperado va
 *    primero — pero uno con una retención de más **no** queda descartado, solo va después.
 *  - **Decide el dueño.** Con confirmación humana no hace falta clavar un margen y rezar.
 *
 * ## Las cuatro puertas cerradas (lo que NUNCA es candidato)
 *
 *  1. **Anulado.** Quien llama pasa solo movimientos vivos (`loadNonVoidedEvents`): un movimiento
 *     anulado no ocurrió.
 *  2. **Pata de traspaso.** Mover plata de una cuenta propia a otra no es el pago del arriendo ni
 *     la llegada del sueldo. Se mira `transferId` **y** la categoría reservada, porque una pata
 *     huérfana (cuenta borrada) pierde el enlace pero no deja de ser lo que fue.
 *  3. **Categoría reservada** («Traspaso», «Saldo inicial», «Pago de tarjeta», «Cuenta
 *     eliminada»): son asientos internos de Movi, no hechos del mes.
 *  4. **Ya usado como ocurrencia** de este u otro recurrente. Sin esto, un mismo ingreso podría
 *     cerrar el «Salario» de agosto y el de septiembre — dos periodos cerrados con una sola
 *     entrada de plata es exactamente el «marcar de más» que hay que evitar.
 *
 * ## Y una señal mínima, para no proponer cualquier cosa
 *
 * Además de las puertas, un candidato tiene que **llamarse igual, compartir la categoría, o ir hacia
 * el [com.jvillada.movi.shared.model.DestinoConocido] que la regla tiene asociado** (Ola V — un
 * traspaso a un tercero ya registrado, ver [candidatosPuntuados]). La cuenta *suma* —ordena mejor a
 * lo que cae donde el dueño dijo que cae— pero **no alcanza sola**,
 * y esto costó una revisión: `rule.accountId != null` no mira el movimiento, así que con la
 * cuenta como seña suficiente TODO gasto de esa cuenta en la ventana pasaba el mínimo. La regla
 * «Arriendo · Vivienda · $1.800.000 · Bancolombia» proponía el mercado del Éxito de $1.750.000
 * como el arriendo — un toque y el arriendo real dejaba de avisar. Es literalmente el modo de
 * falla que este párrafo decía evitar.
 *
 * Lo que sí queda pasando, y conviene tener presente: varias cosas de la MISMA categoría —«Agua»,
 * «Gas», «Internet», todas en «Servicios»— se proponen la una por la otra, y el mismo pago puede
 * salir ofrecido en las tres tarjetas a la vez. Con el **nombre** puesto (la nota, o el comercio
 * que trae el banco) esto se resuelve solo: el nombre pesa 3 contra 1 de la categoría, así que el
 * candidato correcto queda arriba en su regla. Sin nombre ni comercio, la tarjeta solo puede
 * mostrar el día y el monto, y ahí decide el dueño con lo que sabe.
 *
 * Lo que hace tolerable ese residual es el «no fue este», y por eso su descarte se guarda por
 * **(regla, movimiento)** y no por movimiento (ver `claveDescartada` en `OccurrenceLogic.kt`):
 * rechazar el pago del gas en la regla del agua no puede quitárselo a la del gas, que es donde sí
 * era el correcto.
 *
 * ## La cuenta ya NO filtra
 *
 * Antes, una regla con cuenta descartaba todo lo que estuviera en otra. El silencio era el lado
 * seguro del error, pero dejaba sin propuesta un «Salario» anotado en Nequi que se llamaba
 * exactamente igual que la regla — el nombre idéntico pesando menos que un campo que el dueño
 * llenó de pasada. Ahora la cuenta suma como seña y el orden hace el resto.
 */

/**
 * Cuántos días alrededor del vencimiento se buscan candidatos.
 *
 * Diez a cada lado: cubre el pago adelantado, el sueldo que cae el viernes porque el 25 fue
 * domingo, y el atraso de un par de días hábiles.
 *
 * **Pero nunca hacia atrás más allá del mes del vencimiento** (ver [occurrenceCandidatesFor]).
 * Sin ese piso, una regla de día 1 o 2 —el día típico de un arriendo o una nómina— proponía el
 * pago del mes ANTERIOR para cerrar el vencimiento de este: el arriendo de agosto pagado tarde el
 * 25 de agosto ofrecido como el arriendo de septiembre, con el monto exacto (así que ni siquiera
 * salía el aviso de «no es el monto que anotaste») y sin que ningún texto dijera de qué mes se
 * hablaba. Confirmarlo hacía desaparecer el arriendo de septiembre: fuera de «Próximos», fuera
 * del barrido, sin correo. Perder una propuesta legítima —el sueldo que cayó el último día del
 * mes anterior— cuesta un «Ya me llegó»; cerrar el mes equivocado cuesta plata.
 */
const val OCCURRENCE_WINDOW_DAYS: Long = 10

/**
 * **La ventana de un periodo, como par de fechas.** `[primer día del mes del vencimiento
 * .. vencimiento + [windowDays]]`, o sea las dos condiciones de fecha que aplica
 * [occurrenceCandidatesFor], escritas una sola vez.
 *
 * Existe porque ahora hay **dos** lugares que necesitan la misma regla y no pueden divergir:
 * quién se PROPONE como ocurrencia de un periodo, y —desde que la fecha de un movimiento se puede
 * corregir— quién sigue SOSTENIENDO un sello que ya se puso (ver `PUT /api/events/{id}/timestamp`
 * y [sostieneLaOcurrencia]). Si las dos definiciones se separaran, habría movimientos que el
 * emparejador nunca habría propuesto pero que igual mantienen un mes dado por pagado.
 */
fun occurrenceWindow(
    dueDate: LocalDate,
    windowDays: Long = OCCURRENCE_WINDOW_DAYS,
    settings: PeriodSettings = PeriodSettings(),
): ClosedRange<LocalDate> {
    // El piso nunca cae antes del PERÍODO del vencimiento: ver el KDoc de OCCURRENCE_WINDOW_DAYS.
    // Con corte 1 es el primer día del mes, lo de siempre; con corte 25 un pago del día 1 que se
    // hizo el 27 del mes anterior ya es de ese período, y tiene que poder proponerse.
    val inicio = if (settings.esMesDeCalendario) YearMonth.from(dueDate).atDay(1) else diasDelPeriodo(dueDate, settings).start
    val piso = maxOf(inicio, dueDate.minusDays(windowDays))
    return piso..dueDate.plusDays(windowDays)
}

/**
 * ¿Un movimiento fechado [fecha] puede ser la ocurrencia del vencimiento [dueDate]?
 *
 * Es la misma pregunta que hace [occurrenceCandidatesFor] al proponer, hecha ahora también al
 * **corregir la fecha de un movimiento ya sellado**: si la respuesta pasa a ser «no», ese sello
 * dejó de tener evidencia y se suelta (ver `PUT /api/events/{id}/timestamp`).
 *
 * La ventana **no es el mes**, y en las dos direcciones importa:
 *
 *  - Hacia adelante llega hasta 10 días DESPUÉS del vencimiento, así que puede cruzar al mes
 *    siguiente: el arriendo del 31 pagado el 3 sigue sosteniendo el sello de su mes.
 *  - Hacia atrás **se corta en el primer día del mes del vencimiento** y no antes. Eso no es un
 *    descuido de esta función: es el piso deliberado de [OCCURRENCE_WINDOW_DAYS] (leer su KDoc),
 *    puesto para que el pago del mes ANTERIOR no cierre el vencimiento de este.
 *
 * O sea que lo que suelta un sello es exactamente una fecha que el emparejador **nunca habría
 * propuesto** para ese periodo — ni una más ancha ni una más angosta.
 */
fun sostieneLaOcurrencia(
    fecha: LocalDate,
    dueDate: LocalDate,
    windowDays: Long = OCCURRENCE_WINDOW_DAYS,
    settings: PeriodSettings = PeriodSettings(),
): Boolean = fecha in occurrenceWindow(dueDate, windowDays, settings)

/** Cuántas propuestas se le muestran al dueño. Más de tres es una lista, no una propuesta. */
const val MAX_OCCURRENCE_CANDIDATES: Int = 3

/**
 * La moneda en la que están los montos de un [RecurringRule]. El modelo no tiene campo de moneda —
 * las reglas son pesos, igual que asume el «Flujo libre» de la pantalla de Recurrentes.
 */
const val MONEDA_DE_LAS_REGLAS: String = "COP"

/**
 * Los movimientos que **podrían** ser la ocurrencia de [rule] en el vencimiento [dueDate], del más
 * probable al menos. Ver el KDoc de arriba para el porqué de cada regla.
 *
 * @param events       movimientos vivos (no anulados) del usuario.
 * @param usedEventIds ids ya sellados como ocurrencia de cualquier regla.
 */
fun occurrenceCandidatesFor(
    rule: RecurringRule,
    dueDate: LocalDate,
    events: List<FinancialEvent>,
    usedEventIds: Set<String> = emptySet(),
    zone: ZoneId = AppClock.zone,
    windowDays: Long = OCCURRENCE_WINDOW_DAYS,
    max: Int = MAX_OCCURRENCE_CANDIDATES,
    settings: PeriodSettings = PeriodSettings(),
    // Ola V: los destinos conocidos del dueño, por id — ver el parámetro homónimo de
    // [candidatosPuntuados].
    destinos: Map<String, DestinoConocido> = emptyMap(),
): List<FinancialEvent> =
    candidatosPuntuados(rule, dueDate, events, usedEventIds, zone, windowDays, settings, destinos)
        .take(max)
        .map { it.event }

/**
 * **Los mismos candidatos que [occurrenceCandidatesFor], sin recortar y con su puntaje a la vista.**
 *
 * No es otro emparejador: es el cuerpo de [occurrenceCandidatesFor], que ahora lo llama y se queda
 * con los tres primeros. Existe porque el gasto variable del Inicio (ver `PagosDelChecklist.kt`)
 * tiene que repartir movimientos entre VARIAS reglas a la vez, y para eso necesita comparar la
 * seña de un candidato en una regla contra la del mismo movimiento en otra: el «Crédito Papá»
 * dicho por su nombre le gana al «Crédito Mamá» que solo comparte la categoría.
 */
/**
 * **El nombre pega**: la nota del movimiento o el comercio que dijo el banco coincide con el nombre
 * de la regla, perdonando que el movimiento agregue el mes/año («Salario Octubre 2026» pega con
 * «Salario» — ver [nombreDeMovimientoPegaConRegla] en `:core`).
 *
 * **Ola V — el destino de la regla NO entra por acá.** La primera versión de esta ola metía la seña
 * del destino adentro de esta misma función, con el mismo peso que el nombre — y eso la volvía la
 * puerta 1 de lo que decide sola (ver el `concluyente` de [CandidatoPuntuado]), sin exigir ningún
 * monto. Encontrado en revisión con datos reales del dueño: el destino «Caro» es la cuenta de su
 * ESPOSA, y a esa cuenta le llega plata por motivos distintos cada mes (el mercado, la cuota de un
 * crédito compartido, la mesada de la tía). Un nombre («Salario») identifica un CONCEPTO; un destino
 * identifica a una PERSONA, que no es lo mismo — se parece más a la cuenta propia, que este mismo
 * archivo ya rechazó como seña suficiente (ver el KDoc de cabecera, el caso del mercado del Éxito
 * propuesto como el arriendo). Por eso el destino tiene su propia puerta, más angosta, en
 * [candidatosPuntuados]: exige ADEMÁS el monto exacto para poder decidir solo.
 */
private fun nombrePegaCon(rule: RecurringRule, event: FinancialEvent): Boolean =
    nombreDeMovimientoPegaConRegla(rule.name, event.description) ||
        nombreDeMovimientoPegaConRegla(rule.name, event.merchant.orEmpty())

/**
 * **Ola V — ¿el texto de [event] nombra el NÚMERO del [DestinoConocido] que [rule] tiene asociado**
 * (si tiene alguno)?
 *
 * Reusa [nombraAlDestino] de `:core` (el número, o desde el 29-sep la llave, exacta) y **no**
 * [com.jvillada.movi.shared.model.vaHaciaElDestino] —
 * esa función también acepta el NOMBRE del destino como palabra suelta («Almuerzo caro», «Mercado
 * caro»: «caro» es un adjetivo común en español), una seña floja hecha a propósito para que un
 * movimiento anotado a mano se enganche solo con escribirle el nombre. Ahí un falso positivo infla
 * un total en pantalla; acá podría emparejar solo un gasto ajeno y apagar el aviso de una deuda
 * real, así que la única seña que se admite es el **hecho** que escribió el banco: el número o
 * la llave.
 *
 * `destinos` trae SOLO los destinos que el llamador ya resolvió como del dueño de esta regla — un
 * mapa y no una lista para no recorrerla por cada movimiento de la ventana.
 */
private fun destinoPegaCon(rule: RecurringRule, event: FinancialEvent, destinos: Map<String, DestinoConocido>): Boolean {
    val destino = rule.destinoConocidoId?.let { destinos[it] } ?: return false
    return nombraAlDestino(event, destino)
}

/**
 * El monto de [event] es EXACTAMENTE el de [rule] (y en la misma moneda). Puerta compartida por la
 * decisión de categoría+cuenta y la de destino en [candidatosPuntuados] — ver el KDoc de
 * [CandidatoPuntuado.concluyente] para el porqué de que acá no valga un margen.
 */
private fun montoExactoCon(rule: RecurringRule, event: FinancialEvent): Boolean =
    event.currency == MONEDA_DE_LAS_REGLAS && event.amount == rule.amount

fun candidatosPuntuados(
    rule: RecurringRule,
    dueDate: LocalDate,
    events: List<FinancialEvent>,
    usedEventIds: Set<String> = emptySet(),
    zone: ZoneId = AppClock.zone,
    windowDays: Long = OCCURRENCE_WINDOW_DAYS,
    settings: PeriodSettings = PeriodSettings(),
    /**
     * Ola V: los destinos conocidos del dueño, por id. Solo hace falta traer TODOS los suyos (no
     * solo el de esta regla) porque el llamador los resuelve una única vez por respuesta y los
     * reusa para todas las reglas — ver `destinosDelDueno` en `:server`. Vacío por default: nada
     * cambia para quien no lo pasa, que es exactamente lo que pide el brief («sin destino
     * asociado, el comportamiento de hoy no cambia»).
     */
    destinos: Map<String, DestinoConocido> = emptyMap(),
): List<CandidatoPuntuado> {
    val claveCategoria = claveComparableDeNombre(rule.category)
    // La ventana (con su piso en el primer día del mes del vencimiento) sale de
    // [occurrenceWindow]: es la MISMA que decide si un sello ya puesto sigue teniendo evidencia.
    val ventana = occurrenceWindow(dueDate, windowDays, settings)

    return events
        .asSequence()
        .filter { it.id !in usedEventIds }
        .filter { it.type == rule.type }
        .filter { it.transferId == null }
        .filter { !isReservedCategory(it.category) }
        // **Solo pesos.** `RecurringRule` no tiene moneda: sus montos son COP por modelo (lo mismo
        // que asume `resumenRecurrentes` al sumar el flujo libre). Un cobro en dólares que se
        // llame igual entraba igual, y entonces el orden comparaba 12 con 1.800.000 y la tarjeta
        // anunciaba «no es el monto que anotaste ($1.800.000)» contra un US$12 — dos cifras que
        // no son comparables, presentadas como si lo fueran. Hasta que un recurrente pueda decir
        // en qué moneda es, lo honesto es no proponerlo.
        .filter { it.currency == MONEDA_DE_LAS_REGLAS }
        .mapNotNull { event ->
            val fecha = epochMillisToAppDate(event.timestamp, zone)
            if (fecha !in ventana) return@mapNotNull null
            val dias = ChronoUnit.DAYS.between(dueDate, fecha)
            val nombrePega = nombrePegaCon(rule, event)
            val categoriaPega = claveCategoria.isNotEmpty() &&
                claveComparableDeNombre(event.category) == claveCategoria
            // Ola V: el destino asociado a la regla es la tercera seña mínima — un traspaso a un
            // tercero ya registrado, que casi nunca repite ni el nombre ni la categoría de la
            // regla. Ver el KDoc de cabecera y el de [destinoPegaCon].
            val destinoPega = destinoPegaCon(rule, event, destinos)
            // La seña mínima es el NOMBRE, la CATEGORÍA o el DESTINO. La cuenta no basta sola: no
            // dice nada del movimiento, solo de dónde está guardado (ver el KDoc de arriba).
            if (!nombrePega && !categoriaPega && !destinoPega) return@mapNotNull null
            val laCuentaPega = rule.accountId != null && event.accountId == rule.accountId
            val montoExacto = montoExactoCon(rule, event)
            // El nombre pesa más que la categoría: «Salario» dicho igual identifica mejor que
            // «Otros ingresos» compartido con media docena de cosas. La cuenta desempata. El
            // destino pesa MENOS que el nombre —ver [SENA_DEL_DESTINO]— y esto es SOLO para
            // ordenar: lo que de verdad puede decidir (emparejar solo, absorber en el Disponible)
            // vive en [identidadFuerte] y [concluyente], no en este puntaje. Ver su KDoc.
            val senas = (if (nombrePega) SENA_DEL_NOMBRE else 0) +
                (if (categoriaPega) 1 else 0) +
                (if (laCuentaPega) 1 else 0) +
                (if (destinoPega) SENA_DEL_DESTINO else 0)
            // Ola V (fix de una revisión): el destino identifica a la PERSONA que recibe, no el
            // CONCEPTO del pago, y una persona recibe plata por motivos distintos (la esposa del
            // dueño recibe el mercado, la cuota de un crédito compartido y la mesada de la tía,
            // los tres a la MISMA cuenta). Por eso nunca decide con el peso puro del nombre: hace
            // falta ADEMÁS el monto exacto — la misma exigencia que ya tenía categoría+cuenta.
            val identidadFuerte = nombrePega || (destinoPega && montoExacto)
            CandidatoPuntuado(
                event = event,
                senas = senas,
                distanciaMonto = abs(event.amount - rule.amount),
                distanciaDias = abs(dias),
                identidadFuerte = identidadFuerte,
                // Las TRES puertas de «sin lugar a dudas» — ver el KDoc de [ocurrenciaConcluyente].
                concluyente = identidadFuerte || (categoriaPega && laCuentaPega && montoExacto),
            )
        }
        .sortedWith(ORDEN_DE_CANDIDATOS)
        .toList()
}

/**
 * Señas primero (identidad), después el monto (lo variable, que ordena y no filtra), después la
 * cercanía al vencimiento. El id al final para que dos candidatos idénticos salgan siempre en el
 * mismo orden — una propuesta que baila entre recargas se ve como un error.
 */
val ORDEN_DE_CANDIDATOS: Comparator<CandidatoPuntuado> =
    compareByDescending<CandidatoPuntuado> { it.senas }
        .thenBy { it.distanciaMonto }
        .thenBy { it.distanciaDias }
        .thenBy { it.event.id }

/**
 * Lo que suma que el nombre pegue — el PESO MÁS ALTO de los cuatro. Antes de Ola V, `senas >=
 * SENA_DEL_NOMBRE` alcanzaba para decir «el nombre pega, y nada menos lo garantiza» (categoría +
 * cuenta sumaban como mucho 2). **Ya no es así**: el destino ([SENA_DEL_DESTINO]) también suma para
 * el ORDEN y puede combinarse con categoría o cuenta para llegar a 3 sin que el nombre haya pegado.
 * `senas` sigue sirviendo para UNA sola cosa —ordenar las propuestas del «¿Es este?»— y nada más:
 * ninguna decisión automática (emparejar solo, absorber en el Disponible) lee este número directo,
 * las dos leen [CandidatoPuntuado.identidadFuerte] o [CandidatoPuntuado.concluyente].
 */
const val SENA_DEL_NOMBRE: Int = 3

/**
 * Lo que suma que el destino asociado a la regla pegue — Ola V. Menor que [SENA_DEL_NOMBRE] a
 * propósito: el destino identifica a una PERSONA, no a un concepto, así que por sí solo ordena
 * peor que el nombre (ver el KDoc de cabecera y el de [destinoPegaCon]). Es un peso para ORDENAR
 * nada más: lo que decide si el destino puede marcar solo un pago es [CandidatoPuntuado.identidadFuerte]
 * (destino + monto exacto), no este número.
 */
const val SENA_DEL_DESTINO: Int = 2

/**
 * Un candidato con lo que lo ordena (sus señas y distancias) y lo que decide (ver abajo).
 */
data class CandidatoPuntuado(
    val event: FinancialEvent,
    /** Señas: nombre 3, categoría 1, cuenta 1, destino 2. SOLO para ordenar — ver su KDoc. */
    val senas: Int,
    val distanciaMonto: Long,
    val distanciaDias: Long,
    /**
     * **¿Esto identifica el pago tan bien como si dijera el nombre entero?** `true` cuando el
     * nombre pega, o cuando el destino asociado pega Y el monto es exacto. Es lo único que
     * [PagosDelChecklist.parteFijaDelChecklist] deja absorber un fijo del checklist (antes,
     * «Solo el NOMBRE absorbe»; Ola V agrega el destino, con la misma exigencia de monto que ya
     * tenía la cuenta+categoría para decidir en [concluyente]).
     */
    val identidadFuerte: Boolean,
    /**
     * **¿Esto es, sin lugar a dudas, la ocurrencia de la regla?** `identidadFuerte`, o las tres
     * circunstancias de siempre (categoría + cuenta + monto exacto). Ver el KDoc de
     * [ocurrenciaConcluyente] para las tres puertas completas.
     */
    val concluyente: Boolean,
)

/**
 * **El movimiento que es, sin lugar a dudas, la ocurrencia de [rule] — o `null`, que quiere decir
 * «preguntale al dueño».**
 *
 * Esto sí marca: lo que devuelve acá sale de `GET /api/payments/occurrences` con `occurred = true`
 * sin que nadie haya tildado nada. Así que es la única parte de este archivo donde la asimetría
 * del riesgo —**marcar de más es peor que el ruido**— se paga en serio, y por eso la puerta es
 * mucho más angosta que la de [occurrenceCandidatesFor].
 *
 * ## Por qué hizo falta
 *
 * La casilla del «Checklist del período» sellaba con `eventId = null`: daba por pagado **sin
 * ninguna evidencia**. En los datos reales del dueño quedaron tres sellos así cuyo movimiento SÍ
 * existía, con el nombre casi calcado: «Mercado» contra «Mercado» de $2.000.000, «Gimnasio Cami»
 * contra «Gimnasio Cami» de $180.000, «Salario» contra «Salario Septiembre 2026» de $20.308.659
 * en la misma cuenta y la misma categoría. O sea: la evidencia estaba a la vista y el sello la
 * ignoraba. Emparejar solo esos casos no es una heurística agresiva — es leer lo que ya está.
 *
 * ## Qué cuenta como concluyente
 *
 * Un candidato lo es cuando pasa **una** de estas tres puertas (ver [CandidatoPuntuado.concluyente]):
 *
 *  1. **El nombre pega**: la clave comparable de la nota del movimiento (o del comercio que dijo
 *     el banco) es idéntica a la de la regla — o el movimiento dice el nombre de la regla y le
 *     agrega solo el mes o el año (`nombreDeMovimientoPegaConRegla` en `:core`), que es como el
 *     dueño anota su sueldo: «Salario Octubre 2026» pega con la regla «Salario». Decirle «Mercado»
 *     a un gasto en el mes en que vence el recurrente «Mercado» no es una coincidencia que valga
 *     la pena poner en duda.
 *  2. **Pegan las tres circunstancias a la vez**: la categoría, la cuenta y el **monto exacto**.
 *     Ninguna de las tres sola dice nada (el KDoc de arriba cuenta cómo la cuenta sola proponía el
 *     mercado del Éxito como el arriendo), pero las tres juntas describen un hecho muy específico:
 *     tanta plata, esa cifra y no otra, saliendo de esa cuenta, anotada en esa categoría, en la
 *     ventana del vencimiento. Es lo que hace que un movimiento que no repite ni el nombre ni el
 *     mes de la regla —«Reintegro» contra la regla «Salario», digamos— siga contando como la
 *     ocurrencia si el monto es exacto.
 *  3. **Ola V — el destino de la regla pega, Y el monto es exacto.** Un traspaso a un tercero ya
 *     registrado («Tía Caro» → el destino «Caro») nunca repite el nombre de la regla —el banco solo
 *     nombra el número de la cuenta que recibe— así que sin esta puerta nunca sería concluyente. Pero
 *     el destino identifica a la PERSONA, no al concepto del pago, y una persona recibe plata por
 *     motivos distintos: la cuenta de la esposa del dueño recibe el mercado, la cuota de un crédito
 *     compartido y la mesada de la tía, las tres al mismo número. Sin el monto exacto, cualquiera de
 *     esas transferencias marcaría «Tía Caro» como pagada — apagaría su aviso real y subiría el
 *     Disponible por plata que no era de ella. Con el monto exacto puesto, es tan específico como la
 *     puerta 2.
 *
 * **«Monto exacto» es exacto.** El KDoc de arriba argumenta contra los márgenes de ±10 % elegidos
 * a ojo, y ese argumento vale doblemente acá: si el monto de un recurrente es un estimado, un
 * margen inventado no lo vuelve un contrato, solo agranda la puerta. Cuando el monto no es el
 * mismo, esta función no decide — y eso no pierde nada, porque el candidato sigue apareciendo en
 * [occurrenceCandidatesFor] para que el dueño confirme (con el destino como seña, ver
 * [candidatosPuntuados]).
 *
 * ## Y solo con UNO. Con dos, pregunta.
 *
 * Con dos o más concluyentes Movi no puede saber cuál es cuál, y elegir «el mejor» sería inventar
 * un desempate donde no hay información. El caso real: el gimnasio del dueño, con un movimiento
 * «Gimnasio Cami» de $180.000 y otro «Gimnasio» de $180.000 en la misma cuenta y categoría dentro
 * de la misma ventana. Son dos pagos de gimnasio iguales; emparejar uno al azar quemaría el
 * movimiento bueno (un id sellado no se vuelve a proponer) para cerrar el mes con el otro.
 * Devolver `null` deja los dos en `candidates` y el dueño elige en un toque.
 *
 * Con cero, lo mismo: no hay nada que afirmar.
 *
 * @param usedEventIds ids ya sellados —o ya emparejados automáticamente en esta misma respuesta,
 *                     ver `ReminderRoutes`— que no pueden volver a usarse.
 */
fun ocurrenciaConcluyente(
    rule: RecurringRule,
    dueDate: LocalDate,
    events: List<FinancialEvent>,
    usedEventIds: Set<String> = emptySet(),
    zone: ZoneId = AppClock.zone,
    windowDays: Long = OCCURRENCE_WINDOW_DAYS,
    settings: PeriodSettings = PeriodSettings(),
    destinos: Map<String, DestinoConocido> = emptyMap(),
): FinancialEvent? =
    // `singleOrNull`: cero o dos es lo mismo acá — no hay nada que afirmar, se pregunta.
    ocurrenciasConcluyentes(rule, dueDate, events, usedEventIds, zone, windowDays, settings, destinos).singleOrNull()

/**
 * **Todos los candidatos que pasan una de las tres puertas de [ocurrenciaConcluyente]**, sin elegir.
 *
 * Es el cuerpo de [ocurrenciaConcluyente], que se queda con el único; suelto porque «Tus períodos»
 * necesita distinguir los dos `null` que ella junta: con cero concluyentes el pago está pendiente,
 * con dos o más Movi tiene dudas. La decisión de emparejar sigue siendo una sola.
 *
 * Lee [CandidatoPuntuado.concluyente] directo —ya lo calculó [candidatosPuntuados] con la MISMA
 * `destinos` que se le pasó acá— en vez de recalcularlo: una sola definición de «concluyente» para
 * quien pregunta por la lista completa y quien pregunta por el único.
 */
fun ocurrenciasConcluyentes(
    rule: RecurringRule,
    dueDate: LocalDate,
    events: List<FinancialEvent>,
    usedEventIds: Set<String> = emptySet(),
    zone: ZoneId = AppClock.zone,
    windowDays: Long = OCCURRENCE_WINDOW_DAYS,
    settings: PeriodSettings = PeriodSettings(),
    destinos: Map<String, DestinoConocido> = emptyMap(),
): List<FinancialEvent> =
    // **Sobre los candidatos SIN recortar**, no sobre los tres que se muestran. Si se mirara la
    // lista recortada, un cuarto concluyente quedaría invisible y los tres de arriba parecerían
    // «exactamente uno»: la ambigüedad se taparía justo cuando más movimientos parecidos hay, que
    // es cuando más caro sale equivocarse.
    candidatosPuntuados(rule, dueDate, events, usedEventIds, zone, windowDays, settings, destinos)
        .filter { it.concluyente }
        .map { it.event }
