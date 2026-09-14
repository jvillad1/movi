package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Ventana de gracia por defecto, en días.
 *
 * movi no sabe si un pago se hizo: solo conoce la regla y el día. Afirmar "vencido" de forma
 * indefinida sería afirmar un hecho que no puede verificar, así que la afirmación se acota:
 * pasados [DEFAULT_GRACE_DAYS] días del vencimiento, el pago se considera hecho y la fecha
 * rueda al mes siguiente. Cinco días cubren un vencimiento en viernes más el fin de semana
 * y un par de días hábiles de rezago bancario, que es el atraso que sí vale la pena avisar.
 */
const val DEFAULT_GRACE_DAYS: Int = 5

/** La ocurrencia de [dayOfMonth] dentro de [month], recortada al largo real de ese mes. */
fun occurrenceInMonth(month: YearMonth, dayOfMonth: Int): LocalDate =
    month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

/**
 * Periodo "YYYY-MM" de una fecha — la unidad con la que se sella un recordatorio.
 *
 * **Es el mes de calendario del VENCIMIENTO, y está bien que lo sea**, aunque el dueño viva por
 * períodos (corte 25). Un recurrente mensual tiene exactamente una ocurrencia por mes de
 * calendario, así que esta clave identifica la ocurrencia sin ambigüedad y no se mueve nunca: si el
 * dueño cambia su corte o declara que un período arrancó otro día, los sellos que ya puso siguen
 * apuntando a la misma cuota. Lo que sí sigue al período es **cuál ocurrencia está en juego** hoy
 * ([ocurrenciaEnJuego]) y cómo se nombra en pantalla ([periodoDelDueno]).
 */
fun periodOf(date: LocalDate): String = YearMonth.from(date).toString()

/**
 * Los días del período del dueño que contiene [fecha], con los dos bordes incluidos.
 *
 * Delega en [periodoDe] y [ventanaDe] de `:core`, las mismas funciones que usan Movimientos,
 * Presupuestos y el Inicio: si acá se reimplementara la regla del corte, «el mes» de Recurrentes y
 * el de Movimientos podrían volver a separarse por un día, que es justo lo que esto vino a cerrar.
 */
fun diasDelPeriodo(fecha: LocalDate, settings: PeriodSettings, zone: ZoneId = AppClock.zone): ClosedRange<LocalDate> {
    // Mediodía y no medianoche: un borde de zona horaria no puede correr el día.
    val millis = appDateToEpochMillis(fecha, zone) + 12 * 3_600_000L
    val ventana = ventanaDe(periodoDe(millis, settings), settings)
    return epochMillisToAppDate(ventana.first, zone)..epochMillisToAppDate(ventana.last, zone)
}

/** El nombre del período del dueño en que cae [fecha], como `"2026-10"` — para decirlo en pantalla. */
fun periodoDelDueno(fecha: LocalDate, settings: PeriodSettings, zone: ZoneId = AppClock.zone): String {
    val millis = appDateToEpochMillis(fecha, zone) + 12 * 3_600_000L
    return periodoDe(millis, settings).prefijo
}

/** La primera ocurrencia de [dayOfMonth] que cae en [desde] o después. */
fun primeraOcurrenciaDesde(desde: LocalDate, dayOfMonth: Int): LocalDate {
    val mes = YearMonth.from(desde)
    val enEseMes = occurrenceInMonth(mes, dayOfMonth)
    return if (enEseMes.isBefore(desde)) occurrenceInMonth(mes.plusMonths(1), dayOfMonth) else enEseMes
}

/**
 * **La ocurrencia que está en juego hoy**: la primera que cae dentro del período del dueño que
 * contiene [hoy], o `null` si ese período no tiene ninguna.
 *
 * Con corte 1 es la del mes de calendario, exactamente lo de siempre. Con corte 25 el período
 * «octubre» va del 25 de septiembre al 24 de octubre, así que un arriendo del día 28 que se paga el
 * 28 de septiembre **es de octubre** —igual que lo cuenta Movimientos— y uno del día 10 es el 10 de
 * octubre. Antes Recurrentes preguntaba por el mes de calendario y los dos lados de la app podían
 * hablar de meses distintos sobre el mismo pago.
 *
 * `null` solo pasa con un período acortado a mano (un inicio propio que lo deja más corto que un
 * mes) que no alcanza a contener el día de la regla: ese período no tiene ocurrencia que preguntar.
 */
fun ocurrenciaEnJuego(hoy: LocalDate, dayOfMonth: Int, settings: PeriodSettings, zone: ZoneId = AppClock.zone): LocalDate? {
    val dias = diasDelPeriodo(hoy, settings, zone)
    return primeraOcurrenciaDesde(dias.start, dayOfMonth).takeIf { it in dias }
}

/**
 * **La última ocurrencia que ya llegó** (en [hoy] o antes), si lleva como mucho [graceDays] días de
 * atraso; `null` si la última ya pasó la gracia.
 *
 * Existe por el cambio de período. Con corte 25, un pago del día 24 que no se registró el 24 de
 * septiembre desaparecía el 25: el período nuevo (25-sep a 24-oct) solo contiene el 24 de octubre,
 * así que [dueDateFor] decía «vence en un mes» y `/api/payments/occurrences` dejaba de ofrecer
 * «Ya lo pagué» **al día siguiente del vencimiento**. Lo mismo pasaba por calendario con los días
 * 27-31 en los primeros días del mes. La gracia existe justamente para ese atraso; el borde del
 * período no puede cortarla.
 *
 * No mira el período a propósito: la gracia se cuenta en días desde el vencimiento, y un
 * recurrente mensual tiene a lo sumo una ocurrencia dentro de cinco días hacia atrás.
 */
fun ocurrenciaEnGracia(hoy: LocalDate, dayOfMonth: Int, graceDays: Int = DEFAULT_GRACE_DAYS): LocalDate? {
    val mes = YearMonth.from(hoy)
    val enEsteMes = occurrenceInMonth(mes, dayOfMonth)
    val ultima = if (enEsteMes.isAfter(hoy)) occurrenceInMonth(mes.minusMonths(1), dayOfMonth) else enEsteMes
    return ultima.takeIf { ChronoUnit.DAYS.between(it, hoy) <= graceDays }
}

/**
 * **La ocurrencia sobre la que `/api/payments/occurrences` pregunta hoy**: la que está en juego en
 * el período ([ocurrenciaEnJuego]) salvo que la del período anterior siga dentro de la gracia y la
 * del período en curso todavía no haya llegado. En ese caso la pregunta sigue siendo por la
 * anterior —con su «Ya lo pagué» si está abierta, o con su «Deshacer» si ya se selló— hasta que
 * pase la gracia, igual que [dueDateFor] la sigue mostrando vencida.
 *
 * No se mira si la anterior está sellada para elegirla: sellarla no puede hacer desaparecer el
 * «Deshacer» un segundo después del toque. Lo que sí rueda con el sello es «Próximos».
 */
fun ocurrenciaPorPreguntar(
    hoy: LocalDate,
    rule: RecurringRule,
    settings: PeriodSettings,
    graceDays: Int = DEFAULT_GRACE_DAYS,
    zone: ZoneId = AppClock.zone,
): LocalDate? {
    val enJuego = ocurrenciaEnJuego(hoy, rule.dayOfMonth, settings, zone)
    val enGracia = ocurrenciaEnGracia(hoy, rule.dayOfMonth, graceDays)
    val usarLaDeGracia = enGracia != null &&
        ruleIsActiveOn(rule, enGracia) &&
        (enJuego == null || (enGracia.isBefore(enJuego) && enJuego.isAfter(hoy)))
    return if (usarLaDeGracia) enGracia else enJuego
}

/**
 * Clave de dedupe del vencimiento actual de una regla.
 *
 * Es el periodo del vencimiento vigente (no el de hoy), calculado con la misma [dueDateFor] que
 * decide el estado del pago. [selectDueForReminder] y [com.jvillada.movi.server.reminders.ReminderScheduler]
 * DEBEN sellar/filtrar con esta misma función — de lo contrario nada garantiza que sus criterios
 * de "ya se avisó este vencimiento" coincidan.
 */
fun reminderKeyFor(
    rule: RecurringRule,
    today: LocalDate,
    graceDays: Int = DEFAULT_GRACE_DAYS,
    occurredPeriods: Set<String> = emptySet(),
    settings: PeriodSettings = PeriodSettings(),
): String = periodOf(dueDateFor(rule, today, graceDays, occurredPeriods, settings))

/**
 * Cuántos periodos ya-ocurridos seguidos se aguanta [dueDateFor] antes de dejar de rodar.
 *
 * No es un límite de negocio: nadie cierra dos años de periodos por adelantado (el alta ni
 * siquiera deja marcar más allá del vencimiento en juego). Es un tope para que un conjunto raro
 * —una base tocada a mano, un cliente con un bug— no convierta un bucle en un cuelgue del server.
 */
private const val MAX_OCCURRENCE_ROLLS: Int = 24

/**
 * Fecha de vencimiento vigente de la regla.
 *
 * Es la ocurrencia de este mes mientras siga adelante o lleve como mucho [graceDays] de atraso;
 * pasada la gracia rueda a la ocurrencia del mes siguiente (recortada al largo de *ese* mes, y
 * con cambio de año en diciembre). Una fecha rodada nunca queda en el pasado, así que nunca
 * vuelve a leerse como OVERDUE.
 *
 * ## [occurredPeriods] — «esto ya ocurrió», con la misma mecánica que la gracia
 *
 * Son los periodos `"YYYY-MM"` que el dueño ya dio por ocurridos para ESTA regla (ver
 * [com.jvillada.movi.shared.model.RecurringOccurrence]). Un periodo cerrado **rueda al
 * siguiente**, exactamente como ya rodaba un vencimiento pasado de gracia.
 *
 * Se resolvió así —rodando la fecha— y no agregando un estado nuevo a `PaymentStatus`, por dos
 * razones que apuntan al mismo lado:
 *
 *  1. **El APK 1.6 que el dueño tiene instalado.** kotlinx revienta al deserializar un valor de
 *     enum que no conoce: un `PaymentStatus.OCCURRED` le rompería `GET /api/payments/upcoming`
 *     entero, o sea la pantalla, por una función que ni siquiera puede usar. Rodando la fecha, un
 *     cliente viejo simplemente lee la verdad («vence el 25 del mes que viene») sin enterarse de
 *     nada.
 *  2. **Una sola noción de "el vencimiento vigente".** Todo lo que ya deriva de [dueDateFor] —el
 *     estado, el orden de «Próximos», la clave de dedupe de los avisos— hereda el cierre sin que
 *     haya que acordarse de mirar la tabla en cada lugar. En particular
 *     [selectDueForReminder]: un recurrente cerrado deja de avisar ese mes porque su vencimiento
 *     vigente ya es el del mes que viene, no por un `if` aparte que alguien pueda olvidar.
 *
 * Y al mes siguiente vuelve a estar pendiente solo: el periodo nuevo no está en el conjunto.
 *
 * ## [settings] — el período del dueño
 *
 * La ocurrencia de partida es la del **período** que contiene [today] ([ocurrenciaEnJuego]), no la
 * del mes de calendario. Con corte 1 es lo mismo de siempre. Con corte 25 cambia un caso que antes
 * se perdía: el 2 de septiembre, un pago del día 28 que no se registró el 28 de agosto seguía
 * dentro de la gracia, pero el cálculo por calendario ya saltaba al 28 de septiembre y lo daba por
 * hecho sin que nadie lo dijera. Ahora se sigue viendo vencido hasta que pase la gracia o se marque.
 *
 * Y lo mismo **cuando la ocurrencia quedó en el período anterior** ([ocurrenciaEnGracia]): con corte
 * 25, un pago del 24 sin registrar sigue vencido el 25, el 26… hasta el 29, y el 30 rueda al 24 de
 * octubre. Por calendario, un pago del 30 sigue vencido el 2 del mes siguiente.
 */
fun dueDateFor(
    rule: RecurringRule,
    today: LocalDate,
    graceDays: Int = DEFAULT_GRACE_DAYS,
    occurredPeriods: Set<String> = emptySet(),
    settings: PeriodSettings = PeriodSettings(),
): LocalDate {
    val natural = if (settings.esMesDeCalendario) {
        occurrenceInMonth(YearMonth.from(today), rule.dayOfMonth)
    } else {
        // Un período sin ocurrencia (acortado a mano) arranca por la primera que venga después.
        primeraOcurrenciaDesde(diasDelPeriodo(today, settings).start, rule.dayOfMonth)
    }
    val inicio = rule.activeFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    // **Antes de rodar hacia adelante, la ocurrencia anterior que sigue en gracia.**
    //
    // `natural` sale del período que contiene hoy, así que al cambiar de período la ocurrencia de
    // ayer ya no está ahí: con corte 25, el 25-sep un pago del 24 sin registrar saltaba al 24-oct
    // como UPCOMING, sin «vencido» ni aviso. Si la última que llegó lleva ≤ [graceDays] días y su
    // sello (`periodOf`, el mes del vencimiento, que no cambia) no está puesto, sigue siendo el
    // vencimiento vigente. Sellada, se sigue de largo como siempre.
    val enGracia = ocurrenciaEnGracia(today, rule.dayOfMonth, graceDays)
    if (enGracia != null &&
        enGracia.isBefore(natural) &&
        periodOf(enGracia) !in occurredPeriods &&
        (inicio == null || enGracia.isAfter(inicio))
    ) {
        return enGracia
    }
    var due =if (ChronoUnit.DAYS.between(natural, today) > graceDays) {
        occurrenceInMonth(YearMonth.from(natural).plusMonths(1), rule.dayOfMonth)
    } else {
        natural
    }
    // **Una regla que todavía no arrancó no vence.**
    //
    // La primera cuota de un crédito no cae el mismo día del desembolso. El dueño lo dijo con su
    // crédito del techo: «el desembolso es ese día pero en realidad la cuota es 1 mes después».
    //
    // Esto ya se había arreglado una vez… **en un solo endpoint de tres**. `ruleIsActiveOn` vivía
    // suelto en `/api/payments/occurrences`, así que «Próximos pagos» del Inicio y el barrido de
    // avisos seguían mostrando la cuota el día del desembolso — que es justo donde el dueño la
    // vio. Un filtro que cada consumidor tiene que acordarse de llamar es un filtro que alguno se
    // va a olvidar; el vencimiento mismo tiene que saberlo.
    //
    // Rueda mes a mes con el mismo tope que el bucle de abajo: sin él, una fecha de inicio
    // absurda (un año 2400 mal tecleado) daría un bucle infinito en vez de un dato raro.
    var sinArrancar = 0
    while (inicio != null && !due.isAfter(inicio) && sinArrancar < MAX_OCCURRENCE_ROLLS) {
        due = occurrenceInMonth(YearMonth.from(due).plusMonths(1), rule.dayOfMonth)
        sinArrancar++
    }

    var rodadas = 0
    while (periodOf(due) in occurredPeriods && rodadas < MAX_OCCURRENCE_ROLLS) {
        due = occurrenceInMonth(YearMonth.from(due).plusMonths(1), rule.dayOfMonth)
        rodadas++
    }
    return due
}

fun statusFor(dueDate: LocalDate, today: LocalDate, leadDays: Int): PaymentStatus = when {
    dueDate.isBefore(today) -> PaymentStatus.OVERDUE
    dueDate.isEqual(today)  -> PaymentStatus.DUE_TODAY
    ChronoUnit.DAYS.between(today, dueDate) <= leadDays -> PaymentStatus.DUE_SOON
    else -> PaymentStatus.UPCOMING
}

/**
 * @param occurredBy id de regla → periodos que el dueño ya dio por ocurridos. Lo que no esté en
 *   el mapa se comporta exactamente como antes de esta función, que es lo que hoy ve todo el
 *   mundo: nadie tiene ninguna ocurrencia sellada todavía.
 */
fun upcomingPayments(
    rules: List<RecurringRule>,
    today: LocalDate,
    leadDays: Int,
    occurredBy: Map<String, Set<String>> = emptyMap(),
    settings: PeriodSettings = PeriodSettings(),
): List<UpcomingPayment> =
    rules.map { rule ->
        val due = dueDateFor(rule, today, DEFAULT_GRACE_DAYS, occurredBy[rule.id].orEmpty(), settings)
        UpcomingPayment(
            rule = rule,
            dueDate = due.toString(),
            daysUntil = ChronoUnit.DAYS.between(today, due).toInt(),
            status = statusFor(due, today, leadDays),
        )
    }.sortedBy { it.dueDate }

/**
 * Pure sweep-selection filter.
 *
 * Given a list of (RecurringRule, lastRemindedPeriod?) pairs, returns the EXPENSE rules whose
 * due date is OVERDUE, DUE_TODAY, or DUE_SOON, that have `remindMe` on, AND that have not yet
 * been reminded *for that due date*.
 *
 * La unidad de deduplicación es el periodo del **vencimiento**, no el de hoy: cerca de fin de
 * mes [dueDateFor] puede devolver una fecha del mes siguiente, y comparar contra el periodo de
 * hoy notificaría el mismo pago dos veces (una en el mes viejo y otra al cambiar el mes).
 * [ReminderScheduler] sella con [reminderKeyFor], la misma función que filtra aquí.
 *
 * Un vencimiento que el dueño ya dio por ocurrido ([occurredBy]) no avisa: no hay un `if` aparte
 * para eso — su vencimiento vigente ya rodó al mes siguiente (ver [dueDateFor]), así que cae solo
 * en UPCOMING y sale por el filtro que ya estaba. Al mes siguiente vuelve a entrar.
 *
 * @param rules       pairs of rule + the value of `lastRemindedPeriod` from the DB row
 * @param today       reference date (normally AppClock.today(), la fecha civil de Bogotá)
 * @param leadDays    how many days before due is considered DUE_SOON
 * @param occurredBy  id de regla → periodos ya dados por ocurridos
 */
fun selectDueForReminder(
    rules: List<Pair<RecurringRule, String?>>,
    today: LocalDate,
    leadDays: Int,
    occurredBy: Map<String, Set<String>> = emptyMap(),
    settings: PeriodSettings = PeriodSettings(),
): List<RecurringRule> =
    rules
        .filter { (rule, lastRemindedPeriod) ->
            val ocurridos = occurredBy[rule.id].orEmpty()
            val due = dueDateFor(rule, today, DEFAULT_GRACE_DAYS, ocurridos, settings)
            // remindMe primero: si el dueño desmarcó «Recordarme unos días antes» para ESTE
            // pago, no hay nada más que evaluar. El pago sigue existiendo (aparece en Próximos
            // y en los totales) — lo único que se apaga es el aviso.
            rule.remindMe &&
                rule.type == TransactionType.EXPENSE &&
                lastRemindedPeriod != reminderKeyFor(rule, today, DEFAULT_GRACE_DAYS, ocurridos, settings) &&
                statusFor(due, today, leadDays) != PaymentStatus.UPCOMING
        }
        .map { it.first }

/**
 * ¿Esta regla ya está corriendo en [date]?
 *
 * Una regla con [RecurringRule.activeFrom] no existe **antes ni el mismo día** de esa fecha. Es lo
 * que hace que la primera cuota de un crédito caiga después del desembolso y no el mismo día: el
 * dueño registró un préstamo desembolsado el 1 de septiembre con pago el día 1, y Movi le anunciaba
 * la cuota para ese mismo 1 de septiembre.
 *
 * Las reglas escritas a mano (un salario, un gimnasio) tienen `activeFrom = null` y corren desde
 * siempre — no tienen un «desembolso» que marque un antes.
 */
fun ruleIsActiveOn(rule: RecurringRule, date: LocalDate): Boolean {
    val desde = rule.activeFrom ?: return true
    val inicio = runCatching { LocalDate.parse(desde) }.getOrNull() ?: return true
    return date.isAfter(inicio)
}
