package com.jvillada.movi.server.routes

import com.jvillada.movi.server.reminders.OCCURRENCE_WINDOW_DAYS
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadCardRulePairs
import com.jvillada.movi.server.reminders.loadCreditRulePairs
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.reminders.loadOccurredBy
import com.jvillada.movi.server.reminders.loadOccurrenceRows
import com.jvillada.movi.server.reminders.loadUsedOccurrenceEventIds
import com.jvillada.movi.server.reminders.occurrenceCandidatesFor
import com.jvillada.movi.server.reminders.occurrenceInMonth
import com.jvillada.movi.server.reminders.periodOf

import com.jvillada.movi.server.reminders.upcomingPayments
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.DEFAULT_REMINDER_LEAD_DAYS
import com.jvillada.movi.shared.model.MarkOccurrenceRequest
import com.jvillada.movi.shared.model.OccurrenceState

import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isReservedCategory
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.YearMonth
import java.util.UUID
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis

private fun org.jetbrains.exposed.sql.ResultRow.toRule() = RecurringRule(
    id = this[RecurringRules.id],
    name = this[RecurringRules.name],
    category = this[RecurringRules.category],
    amount = this[RecurringRules.amount],
    dayOfMonth = this[RecurringRules.dayOfMonth],
    type = TransactionType.valueOf(this[RecurringRules.type]),
    remindMe = this[RecurringRules.remindMe],
    accountId = this[RecurringRules.accountId],
)

/**
 * Ola 9 · D: ¿esta cuenta es de este usuario? La cuenta de una regla recurrente es **opcional**,
 * así que un id desconocido no rechaza el alta: se guarda `null`. Rechazar dejaría al dueño sin
 * poder anotar su arriendo por un id que mandó mal un cliente viejo, y el plan mensual (nombre,
 * monto, día) es válido igual — perder el plan es peor que perder la cuenta.
 */
private fun org.jetbrains.exposed.sql.Transaction.accountIdIfOwned(uid: String, accountId: String?): String? {
    val id = accountId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val exists = Accounts.selectAll()
        .where { (Accounts.id eq id) and (Accounts.userId eq uid) }
        .firstOrNull() != null
    return if (exists) id else null
}

fun Route.reminderRoutes() {
    get("/api/recurring-rules") {
        val uid = call.userId()
        val rules = dbQuery {
            RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
        }
        call.respond(rules)
    }

    post("/api/recurring-rules") {
        val uid = call.userId()
        val body = call.receive<RecurringRule>()
        val newId = "rr_${UUID.randomUUID()}"
        val storedAccountId = dbQuery {
            val safeAccountId = accountIdIfOwned(uid, body.accountId)
            RecurringRules.insert {
                it[id] = newId
                it[userId] = uid
                it[name] = body.name
                it[category] = body.category
                it[amount] = body.amount
                it[dayOfMonth] = body.dayOfMonth.coerceIn(1, 31)
                it[type] = body.type.name
                // El body de un cliente viejo no trae el campo; el default del modelo lo pone
                // en true, que es el comportamiento que ese cliente espera.
                it[remindMe] = body.remindMe
                // Ola 9 · D: la cuenta es opcional y, si viene, tiene que ser de este usuario
                // (ver [accountIdIfOwned]).
                it[accountId] = safeAccountId
            }
            safeAccountId
        }
        // La respuesta dice lo que QUEDÓ guardado, no lo que se pidió: si la cuenta no era suya
        // se guardó null, y devolver el id igual haría que el cliente pinte una cuenta que la
        // regla no tiene.
        call.respond(HttpStatusCode.Created, body.copy(id = newId, accountId = storedAccountId))
    }

    put("/api/recurring-rules/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val body = call.receive<RecurringRule>()
        var storedAccountId: String? = null
        val updated = dbQuery {
            // Ola 9 · D — **un cliente viejo NO puede borrar la cuenta sin querer.**
            //
            // El APK 1.6 que el dueño ya tiene instalado no conoce este campo, así que su PUT
            // llega sin él y kotlinx lo deserializa como `null`. Si `null` significara «quitá la
            // cuenta», corregir el monto desde el teléfono le borraría en silencio la cuenta que
            // había puesto desde la web. Es el mismo agujero que `remindMe` evita con su default
            // `true`, y acá no alcanzaba un default porque «sin cuenta» es un estado legítimo.
            //
            // Por eso el campo es de tres estados en el wire (ver `RecurringRule.accountId`):
            //   · `null`            → no lo toques (cliente viejo, o un PUT que no habla de cuentas)
            //   · cadena vacía      → quitá la cuenta (el dueño eligió «Sin cuenta»)
            //   · un id             → esa cuenta, si es suya
            val cuentaActual = RecurringRules.selectAll()
                .where { (RecurringRules.id eq id) and (RecurringRules.userId eq uid) }
                .firstOrNull()?.get(RecurringRules.accountId)
            val pedida = body.accountId
            val safeAccountId = when {
                pedida == null -> cuentaActual
                pedida.isBlank() -> null
                else -> accountIdIfOwned(uid, pedida)
            }
            storedAccountId = safeAccountId
            RecurringRules.update({ (RecurringRules.id eq id) and (RecurringRules.userId eq uid) }) {
                it[name] = body.name
                it[category] = body.category
                it[amount] = body.amount
                it[dayOfMonth] = body.dayOfMonth.coerceIn(1, 31)
                it[type] = body.type.name
                it[remindMe] = body.remindMe
                it[accountId] = safeAccountId
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound)
        else call.respond(body.copy(id = id, accountId = storedAccountId))
    }

    delete("/api/recurring-rules/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val deleted = dbQuery {
            // Las ocurrencias se van con la regla, en la misma transacción. Si quedaran, sus
            // movimientos seguirían contando como «ya usados» para siempre: marcar el salario,
            // borrar la regla y volver a crearla dejaba ese ingreso fuera de toda propuesta, sin
            // ninguna pantalla desde donde limpiarlo — el caso que motivó esta rama, convertido
            // en permanente. Va primero para que un fallo deje la regla en pie en vez de dejar
            // filas sueltas.
            RecurringOccurrences.deleteWhere {
                (RecurringOccurrences.ruleId eq id) and (RecurringOccurrences.userId eq uid)
            }
            RecurringRules.deleteWhere { (RecurringRules.id eq id) and (RecurringRules.userId eq uid) }
        }
        if (deleted == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
    }

    get("/api/payments/upcoming") {
        val uid = call.userId()
        val leadDays = System.getenv("REMINDER_LEAD_DAYS")?.toIntOrNull() ?: DEFAULT_REMINDER_LEAD_DAYS
        val (rules, occurredBy) = dbQuery {
            val r = RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
            r to loadOccurredBy(uid)
        }
        val creditRules = loadCreditRulePairs(uid).map { it.first }
        // F20: el pago de la tarjeta también es un próximo pago — con la deuda actual como monto.
        val cardRules = loadCardRulePairs(uid).map { it.first }
        // Lo que el dueño ya dio por ocurrido no vuelve a leerse como vencido: su vencimiento
        // vigente rodó al mes que viene (ver `dueDateFor`). Un cliente que no conoce esta función
        // —el APK 1.6 instalado en el teléfono— no ve ningún campo nuevo: ve la fecha correcta.
        call.respond(
            upcomingPayments(rules + creditRules + cardRules, AppClock.today(), leadDays, occurredBy),
        )
    }

    // ── «Esto ya ocurrió» ─────────────────────────────────────────────────────
    // El porqué de todo esto está en RecurringOccurrence (:core) y en OccurrenceMatching.

    /**
     * El estado del periodo **que está en juego** de cada recurrente: si ya se dio por ocurrido, y
     * si no, qué movimientos podrían serlo.
     *
     * Endpoint aparte de `/api/payments/upcoming` a propósito: ese ya lo consume el APK que el
     * dueño tiene instalado, y crecerle campos (o agregarle un valor al enum `PaymentStatus`) le
     * rompería la deserialización. Uno nuevo lo ignora quien no lo conoce.
     */
    get("/api/payments/occurrences") {
        val uid = call.userId()
        val today = AppClock.today()
        val mesEnCurso = YearMonth.from(today)
        val periodoEnCurso = mesEnCurso.toString()
        val estados = dbQuery {
            // Solo reglas REALES. La cuota de un crédito y el pago de una tarjeta son reglas
            // sintéticas derivadas de `credit_terms`/`card_terms`, con su propia pantalla y su
            // propia forma de saldarse (ahí el pago mueve la deuda, que es un hecho más fuerte
            // que un sello). Meterlas acá sería un segundo mecanismo compitiendo con ese.
            val rules = RecurringRules.selectAll()
                .where { RecurringRules.userId eq uid }
                .map { it.toRule() }
            if (rules.isEmpty()) return@dbQuery emptyList<OccurrenceState>()

            val ocurrencias = loadOccurrenceRows(uid).associateBy { it.ruleId to it.period }
            val ocurridos = loadOccurredBy(uid)
            val usados = loadUsedOccurrenceEventIds(uid)
            // Solo la franja donde puede haber candidatos, no todos los movimientos de la vida
            // del usuario: desde el primero del mes (el piso del emparejador) hasta la ventana
            // por delante del vencimiento más tardío posible.
            val eventos = loadEventsBetween(
                uid = uid,
                desde = appDateToEpochMillis(mesEnCurso.atDay(1)),
                hastaExclusivo = appDateToEpochMillis(
                    mesEnCurso.atEndOfMonth().plusDays(OCCURRENCE_WINDOW_DAYS + 1),
                ),
            )

            rules.mapNotNull { rule ->
                // **La unidad es la ocurrencia del MES EN CURSO**, y punto.
                //
                // Antes se usaba `dueDateFor`, o sea la fecha ya rodada por la ventana de gracia,
                // y ahí estaba el agujero: para una regla de día 1 o 2, durante los últimos días
                // del mes el vencimiento vigente ya es el del mes SIGUIENTE. La app terminaba
                // preguntando «¿ya lo pagaste?» sobre septiembre el 27 de agosto y ofreciendo
                // como respuesta el pago de agosto — con el monto exacto, así que ni siquiera
                // salía el aviso de monto distinto. El rodado de la gracia sigue viviendo en
                // `/api/payments/upcoming`, que es donde tiene sentido; acá estorbaba.
                //
                // Además, mirar el mes en curso mantiene el «Ya ocurrió» y su «Deshacer» a la
                // vista TODO el mes, en vez de hacerlos desaparecer a los pocos días.
                val due = occurrenceInMonth(mesEnCurso, rule.dayOfMonth)
                val cerrado = periodoEnCurso in ocurridos[rule.id].orEmpty()
                when {
                    cerrado -> {
                        val fila = ocurrencias[rule.id to periodoEnCurso]
                        OccurrenceState(
                            ruleId = rule.id,
                            period = periodoEnCurso,
                            dueDate = due.toString(),
                            occurred = true,
                            eventId = fila?.eventId,
                            confirmedAt = fila?.confirmedAt ?: 0L,
                        )
                    }
                    // El día todavía no llegó: no se pregunta nada. Preguntar «¿ya ocurrió?» por
                    // algo que vence dentro de tres semanas es ruido, y peor: invita a cerrar un
                    // periodo antes de que pase.
                    due.isAfter(today) -> null
                    else -> OccurrenceState(
                        ruleId = rule.id,
                        period = periodoEnCurso,
                        dueDate = due.toString(),
                        occurred = false,
                        candidates = occurrenceCandidatesFor(rule, due, eventos, usados),
                    )
                }
            }
        }
        call.respond(estados)
    }

    /**
     * Sellar un periodo como ocurrido: con el movimiento que el dueño confirmó, o sin ninguno
     * (el «ya lo pagué» / «ya me llegó»).
     *
     * Idempotente: volver a mandarlo reemplaza el sello. Eso es lo que hace que «no fue este, fue
     * aquel» funcione sin un paso de deshacer en el medio.
     */
    post("/api/recurring-rules/{id}/occurrence") {
        val uid = call.userId()
        val ruleId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        // Este proyecto no tiene StatusPages, así que un body malformado sale como 500 sin
        // atrapar. Un 500 le dice al cliente «el server se rompió» y lo invita a reintentar algo
        // que nunca va a funcionar; un 400 dice la verdad. (Solo se arregla acá: cambiarlo para
        // todos los endpoints es otra rama.)
        val body = try {
            call.receive<MarkOccurrenceRequest>()
        } catch (e: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, "No se pudo leer la marca: ${e.message}")
        }
        val today = AppClock.today()
        if (!PERIOD_REGEX.matches(body.period)) {
            return@post call.respond(HttpStatusCode.BadRequest, "Periodo inválido: usa \"YYYY-MM\".")
        }
        if (ruleId.startsWith(CREDIT_RULE_PREFIX) || ruleId.startsWith(CARD_RULE_PREFIX)) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                "La cuota de un crédito y el pago de una tarjeta se gestionan en Créditos.",
            )
        }
        val resultado: MarcaResult = dbQuery {
            val rule = RecurringRules.selectAll()
                .where { (RecurringRules.id eq ruleId) and (RecurringRules.userId eq uid) }
                .firstOrNull()?.toRule()
                ?: return@dbQuery MarcaResult.Error(HttpStatusCode.NotFound)
            // **Techo: el mes de hoy.** No se puede cerrar un mes que todavía no pasó.
            //
            // El techo era `periodOf(dueDateFor(rule, today))` — la fecha YA RODADA por la
            // ventana de gracia—, así que el 27 de agosto aceptaba `"2026-09"` para una regla de
            // día 1: sellaba septiembre antes de que llegara y le apagaba el aviso. El mes de hoy
            // no depende de la regla ni de la gracia, y dice exactamente lo que hay que decir.
            if (body.period > periodOf(today)) {
                return@dbQuery MarcaResult.Error(
                    HttpStatusCode.BadRequest,
                    "Ese periodo todavía no llegó: no se puede dar por ocurrido.",
                )
            }
            // Y un piso, para que un cliente con un bug no ensucie la tabla con periodos
            // arqueológicos que además queman ids en `usedEventIds` (un movimiento sellado no
            // vuelve a proponerse nunca).
            if (body.period < periodOf(today.minusMonths(MAX_MESES_HACIA_ATRAS))) {
                return@dbQuery MarcaResult.Error(
                    HttpStatusCode.BadRequest,
                    "Ese periodo es demasiado viejo para darlo por ocurrido.",
                )
            }
            val eventId = body.eventId?.trim()?.takeIf { it.isNotEmpty() }
            if (eventId != null) {
                val evento = Events.selectAll()
                    .where { (Events.id eq eventId) and (Events.userId eq uid) }
                    .firstOrNull()
                    ?: return@dbQuery MarcaResult.Error(HttpStatusCode.BadRequest, "Ese movimiento no existe.")
                val anulado = VoidEvents.selectAll()
                    .where { (VoidEvents.originalEventId eq eventId) and (VoidEvents.userId eq uid) }
                    .firstOrNull() != null
                if (anulado) {
                    return@dbQuery MarcaResult.Error(HttpStatusCode.BadRequest, "Ese movimiento está anulado.")
                }
                // Las mismas dos puertas que cierra `occurrenceCandidatesFor`, cerradas también
                // acá: la UI solo ofrece candidatos, pero el endpoint no puede confiar en eso.
                if (evento[Events.transferId] != null || isReservedCategory(evento[Events.category])) {
                    return@dbQuery MarcaResult.Error(
                        HttpStatusCode.BadRequest,
                        "Un traspaso o un asiento interno no puede ser la ocurrencia de un recurrente.",
                    )
                }
                // Un mismo movimiento no puede cerrar dos periodos: sería una sola entrada de
                // plata dando por saldados dos meses.
                val yaUsado = RecurringOccurrences.selectAll()
                    .where { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.eventId eq eventId) }
                    .any { it[RecurringOccurrences.ruleId] != ruleId || it[RecurringOccurrences.period] != body.period }
                if (yaUsado) {
                    return@dbQuery MarcaResult.Error(
                        HttpStatusCode.Conflict,
                        "Ese movimiento ya está marcado como la ocurrencia de otro periodo.",
                    )
                }
            }
            val now = System.currentTimeMillis()
            RecurringOccurrences.deleteWhere {
                (RecurringOccurrences.userId eq uid) and
                    (RecurringOccurrences.ruleId eq ruleId) and
                    (RecurringOccurrences.period eq body.period)
            }
            RecurringOccurrences.insert {
                it[RecurringOccurrences.userId] = uid
                it[RecurringOccurrences.ruleId] = ruleId
                it[RecurringOccurrences.period] = body.period
                it[RecurringOccurrences.eventId] = eventId
                it[confirmedAt] = now
            }
            MarcaResult.Ok(
                RecurringOccurrence(
                    ruleId = ruleId,
                    period = body.period,
                    eventId = eventId,
                    confirmedAt = now,
                ),
            )
        }
        when (resultado) {
            is MarcaResult.Ok -> call.respond(HttpStatusCode.Created, resultado.occurrence)
            is MarcaResult.Error ->
                if (resultado.message == null) call.respond(resultado.code)
                else call.respond(resultado.code, resultado.message)
        }
    }

    /** Deshacer: marcar por error tiene que poder revertirse, y sin ceremonia. */
    delete("/api/recurring-rules/{id}/occurrence/{period}") {
        val uid = call.userId()
        val ruleId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val period = call.parameters["period"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val borrados = dbQuery {
            RecurringOccurrences.deleteWhere {
                (RecurringOccurrences.userId eq uid) and
                    (RecurringOccurrences.ruleId eq ruleId) and
                    (RecurringOccurrences.period eq period)
            }
        }
        if (borrados == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
    }
}

/** `"YYYY-MM"`, con mes real: `2026-13` no es un periodo. */
private val PERIOD_REGEX = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")

/**
 * Hasta cuántos meses atrás se puede sellar una ocurrencia. Es un piso defensivo, no una regla de
 * negocio: la pantalla solo ofrece el mes en curso, así que nadie llega acá de a pie.
 */
private const val MAX_MESES_HACIA_ATRAS: Long = 12

/**
 * Lo que decidió el sellado, decidido DENTRO de la transacción y respondido afuera.
 *
 * Un tipo propio y no un `Any`: las validaciones son varias y cada una tiene su código y su
 * mensaje, y un cast sin chequear en el medio es exactamente donde se cuela el error que nadie
 * ve hasta que un usuario recibe un 500 en vez de un «ese movimiento está anulado».
 */
private sealed interface MarcaResult {
    data class Ok(val occurrence: RecurringOccurrence) : MarcaResult
    data class Error(val code: HttpStatusCode, val message: String? = null) : MarcaResult
}
