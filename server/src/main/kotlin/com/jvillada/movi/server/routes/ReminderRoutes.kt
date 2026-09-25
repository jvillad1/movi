package com.jvillada.movi.server.routes

import com.jvillada.movi.server.reminders.periodoDelDueno
import com.jvillada.movi.server.reminders.ocurrenciaEnJuego
import com.jvillada.movi.server.reminders.ocurrenciaPorPreguntar
import com.jvillada.movi.server.reminders.DEFAULT_GRACE_DAYS
import com.jvillada.movi.server.reminders.diasDelPeriodo
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.server.reminders.OCCURRENCE_WINDOW_DAYS
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.push.WebPushSender
import com.jvillada.movi.server.reminders.cargarPagosDeDeuda
import com.jvillada.movi.server.reminders.loadCardRulePairs
import com.jvillada.movi.server.reminders.loadCreditRulePairs
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.reminders.loadOccurredBy
import com.jvillada.movi.server.reminders.loadOccurrenceRows
import com.jvillada.movi.server.reminders.loadRejectedPairs
import com.jvillada.movi.server.reminders.loadUsedOccurrenceEventIds
import com.jvillada.movi.server.reminders.occurrenceCandidatesFor
import com.jvillada.movi.server.reminders.ocurrenciaConcluyente
import com.jvillada.movi.server.reminders.occurrenceInMonth
import com.jvillada.movi.server.reminders.pagosDeDeudaPorPeriodo
import com.jvillada.movi.server.reminders.plataQueSalio
import com.jvillada.movi.server.reminders.periodosSaldados
import com.jvillada.movi.server.reminders.unirOcurridos
import com.jvillada.movi.server.reminders.ruleIsActiveOn
import com.jvillada.movi.server.reminders.ReminderConfig
import com.jvillada.movi.server.reminders.periodOf

import com.jvillada.movi.server.reminders.upcomingPayments
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MarkOccurrenceRequest
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings

import com.jvillada.movi.shared.model.RechazarOcurrenciaRequest
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ReminderChannels
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.rechazoDelMonto
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
import com.jvillada.movi.server.reminders.leadDaysOf

internal fun org.jetbrains.exposed.sql.ResultRow.toRule() = RecurringRule(
    id = this[RecurringRules.id],
    name = this[RecurringRules.name],
    category = this[RecurringRules.category],
    amount = this[RecurringRules.amount],
    dayOfMonth = this[RecurringRules.dayOfMonth],
    type = TransactionType.valueOf(this[RecurringRules.type]),
    remindMe = this[RecurringRules.remindMe],
    accountId = this[RecurringRules.accountId],
    // El piso de la regla: los períodos anteriores al de esta fecha no existen (ver
    // `arranqueDeLaRegla`). Sin leerla acá, la regla la guardaría y ninguna pantalla la
    // respetaría.
    activeFrom = this[RecurringRules.activeFrom],
)

/**
 * Ola 9 · D: ¿esta cuenta es de este usuario? La cuenta de una regla recurrente es **opcional**,
 * así que un id desconocido no rechaza el alta: se guarda `null`. Rechazar dejaría al dueño sin
 * poder anotar su arriendo por un id que mandó mal un cliente viejo, y el plan mensual (nombre,
 * monto, día) es válido igual — perder el plan es peor que perder la cuenta.
 *
 * Ola 17: dejó de ser `private` porque el alta de una SUSCRIPCIÓN necesita exactamente la misma
 * decisión —cuenta opcional, id ajeno se degrada a `null`, el alta nunca se rechaza por eso— y
 * copiarla en `SubscriptionRoutes` habría dejado dos versiones de una regla de seguridad que
 * puede cambiar. Sigue viviendo acá porque acá nació; `internal` la comparte dentro de `:server`
 * sin exponerla al wire.
 */
internal fun org.jetbrains.exposed.sql.Transaction.accountIdIfOwned(uid: String, accountId: String?): String? {
    val id = accountId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val exists = Accounts.selectAll()
        .where { (Accounts.id eq id) and (Accounts.userId eq uid) }
        .firstOrNull() != null
    return if (exists) id else null
}

/**
 * La fecha ISO de [crudo] si de verdad es una fecha, o `null`.
 *
 * Se valida y no se guarda a ciegas porque esta columna la lee `dueDateFor` para decidir cuándo
 * vence la regla: una cadena que `LocalDate.parse` no entienda quedaría guardada y se ignoraría
 * en silencio, y la regla creada desde un movimiento volvería a proponer el pago que ya ocurrió.
 * Mejor `null` explícito («desde siempre») que un dato que miente sobre lo que hace.
 */
private fun fechaIsoValida(crudo: String?): String? {
    val texto = crudo?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { java.time.LocalDate.parse(texto).toString() }.getOrNull()
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
        // Una regla con monto en cero o negativo suma al revés en «Flujo libre» y en «Próximos
        // pagos». Misma regla que un movimiento, ver `rechazoDelMonto`.
        if (body.name.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Falta el nombre")
        rechazoDelMonto(body.amount)?.let { motivo ->
            return@post call.respond(HttpStatusCode.BadRequest, motivo)
        }
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
                // **Desde cuándo corre.** Lo manda quien crea la regla a partir de un movimiento
                // que ya ocurrió: con la fecha de ese movimiento acá, la regla no se inventa
                // ocurrencias en los períodos ANTERIORES. El período de ese movimiento sí existe,
                // y lo cierra su propio sello (ver `eventoDeOrigen` más abajo). Vacío o mal
                // formado se guarda como NULL —«desde siempre»—, que es como nacen las reglas
                // escritas a mano.
                it[activeFrom] = fechaIsoValida(body.activeFrom)
            }
            safeAccountId
        }
        // **El movimiento que originó la regla queda como su evidencia.**
        //
        // Va en su propia transacción, DESPUÉS de que la regla existe y envuelto en un
        // `runCatching`, y las dos cosas son la misma decisión: el sellado no puede tumbar el
        // alta. Si algo sale mal acá —el movimiento no existe, es un traspaso, ya lo usa otra
        // regla, la base se cayó justo ahí— el dueño igual se queda con su recurrente y, como
        // mucho, con una pregunta de más que puede contestar con un toque. Al revés (perder el
        // alta por no poder poner un sello) sería perder lo que pidió.
        //
        // Por qué hace falta aunque `ocurrenciaConcluyente` empareje solo: ver
        // [sellarElMovimientoDeOrigen].
        val origen = body.eventoDeOrigen?.trim()?.takeIf { it.isNotEmpty() }
        if (origen != null) {
            runCatching {
                dbQuery {
                    val regla = RecurringRules.selectAll()
                        .where { (RecurringRules.id eq newId) and (RecurringRules.userId eq uid) }
                        .firstOrNull()?.toRule()
                    if (regla != null) {
                        sellarElMovimientoDeOrigen(
                            uid = uid,
                            rule = regla,
                            eventId = origen,
                            today = AppClock.today(),
                            settings = ajustesDelPeriodoSinSuspender(uid),
                        )
                    }
                }
            }
        }
        // La respuesta dice lo que QUEDÓ guardado, no lo que se pidió: si la cuenta no era suya
        // se guardó null, y devolver el id igual haría que el cliente pinte una cuenta que la
        // regla no tiene. Lo mismo con la fecha de arranque. Y `eventoDeOrigen` vuelve en null
        // porque en la fila no queda: es un campo de ida, no una columna (ver su KDoc).
        call.respond(
            HttpStatusCode.Created,
            body.copy(
                id = newId,
                accountId = storedAccountId,
                activeFrom = fechaIsoValida(body.activeFrom),
                eventoDeOrigen = null,
            ),
        )
    }

    put("/api/recurring-rules/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val body = call.receive<RecurringRule>()
        if (body.name.isBlank()) return@put call.respond(HttpStatusCode.BadRequest, "Falta el nombre")
        rechazoDelMonto(body.amount)?.let { motivo ->
            return@put call.respond(HttpStatusCode.BadRequest, motivo)
        }
        var storedAccountId: String? = null
        var storedActiveFrom: String? = null
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
            val filaActual = RecurringRules.selectAll()
                .where { (RecurringRules.id eq id) and (RecurringRules.userId eq uid) }
                .firstOrNull()
            val cuentaActual = filaActual?.get(RecurringRules.accountId)
            val pedida = body.accountId
            val safeAccountId = when {
                pedida == null -> cuentaActual
                pedida.isBlank() -> null
                else -> accountIdIfOwned(uid, pedida)
            }
            storedAccountId = safeAccountId
            // **`activeFrom` se PRESERVA en un PUT**, y por el mismo agujero que el de la cuenta,
            // solo que sin la variante «quítala»: ningún cliente de hoy edita esta fecha —la pone
            // el alta desde un movimiento y nada más— así que un `null` en el body es siempre «no
            // lo toques», nunca «desde siempre». Sin esto, corregirle el monto a una regla creada
            // desde un movimiento le borraba el piso y la regla volvía a deber los períodos
            // anteriores, reintroducidos por una edición cualquiera.
            val arranqueActual = filaActual?.get(RecurringRules.activeFrom)
            val arranqueGuardado = fechaIsoValida(body.activeFrom) ?: arranqueActual
            storedActiveFrom = arranqueGuardado
            RecurringRules.update({ (RecurringRules.id eq id) and (RecurringRules.userId eq uid) }) {
                it[name] = body.name
                it[category] = body.category
                it[amount] = body.amount
                it[dayOfMonth] = body.dayOfMonth.coerceIn(1, 31)
                it[type] = body.type.name
                it[remindMe] = body.remindMe
                it[accountId] = safeAccountId
                it[activeFrom] = arranqueGuardado
            }
        }
        if (updated == 0) call.respond(HttpStatusCode.NotFound)
        else call.respond(body.copy(id = id, accountId = storedAccountId, activeFrom = storedActiveFrom))
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

    /**
     * **Por dónde le pueden llegar los recordatorios a este usuario.**
     *
     * El cliente no tiene forma de saberlo: las variables de entorno son del server, y la
     * dirección de correo del barrido es la de la fila de `users`. Sin este endpoint, el aviso
     * de la app miraba solo el permiso de notificaciones del navegador y de ahí concluía «no hay
     * ningún canal» — falso en producción, donde `RESEND_API_KEY` está puesta y el correo sale.
     *
     * Autenticado a propósito (a diferencia de `/api/push/vapid-key`, que es público porque el
     * navegador necesita la clave antes de tener sesión): acá se devuelve **a qué dirección**
     * sale el correo, y eso es un dato del usuario.
     *
     * Lo que se afirma sale de [ReminderConfig], el mismo objeto —y, desde que el barrido relee
     * en cada pasada, en el mismo momento— que decide si el correo sale. Queda un único desfase
     * posible, y está acotado: el barrido decide **al arrancar** si existe, así que agregarle la
     * clave a un server ya andando haría que este endpoint dijera «hay correo» hasta el próximo
     * reinicio. En Railway no se alcanza, porque tocar una variable reinicia el deploy.
     *
     * Y lo que este endpoint NO puede afirmar está declarado en el modelo: `email = true`
     * significa «hay una clave», no «la entrega funciona» (ver
     * [com.jvillada.movi.shared.model.ReminderChannels.email]).
     */
    get("/api/reminders/channels") {
        val uid = call.userId()
        val hayCorreo = ReminderConfig.emailEnabled()
        val email = if (hayCorreo) {
            dbQuery { Users.selectAll().where { Users.id eq uid }.firstOrNull()?.get(Users.email) }
        } else null
        call.respond(
            ReminderChannels(
                email = hayCorreo,
                emailTo = email,
                emailSandbox = hayCorreo && ReminderConfig.senderIsSandbox(),
                push = WebPushSender.isConfigured(),
                // El del usuario, no el global: ver [leadDaysOf].
                leadDays = leadDaysOf(uid),
            ),
        )
    }

    get("/api/payments/upcoming") {
        val uid = call.userId()
        call.respond(proximosPagos(uid, AppClock.today()))
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
     *
     * Las reglas sintéticas entran **solo cuando ya están pagadas**, y derivado del movimiento
     * (ver el bloque de abajo y `PagosDeDeuda.kt`): nunca abiertas, nunca con candidatos, nunca
     * con un sello propio.
     */
    get("/api/payments/occurrences") {
        val uid = call.userId()
        val today = AppClock.today()
        // **El período del dueño, no el mes de calendario.** Con corte 25, «octubre» va del 25 de
        // septiembre al 24 de octubre, y la pregunta «¿ya pagaste el de octubre?» tiene que ser
        // sobre la ocurrencia que cae ahí —la misma que Movimientos cuenta en octubre—.
        val periodo = ajustesDePeriodoDe(uid)
        // Las reglas REALES, con lo que Movi emparejó solo adentro. Vive en una función aparte
        // porque el contexto de Movi AI necesita EXACTAMENTE esta misma respuesta: ver
        // [estadosDeLasOcurrenciasReales].
        val estados = dbQuery { estadosDeLasOcurrenciasReales(uid, today, periodo) }
        // ── Las sintéticas que YA ESTÁN PAGADAS ──────────────────────────────────────
        //
        // «Ya ocurrieron · 1» era falso: había cuatro pagos registrados que nadie leía. Estas
        // filas salen del movimiento que bajó la deuda, no de un sello, y por eso viajan marcadas
        // con `derivadaDeUnMovimiento` — la pantalla no puede ofrecerles un «Deshacer» que no
        // haría nada (para deshacerlo hay que borrar el movimiento).
        //
        // **Solo las pagadas.** Una sintética abierta no se emite: la pantalla pintaría su
        // propuesta con el botón «Ya lo pagué», que en una regla sintética responde 400 — un
        // control muerto — y si respondiera 201 sería el segundo mecanismo de sellado que este
        // endpoint existe para no tener.
        //
        // Y no se le exige que el vencimiento ya haya llegado, a diferencia de las reales: ahí la
        // guarda evita preguntar por algo que todavía no pasó, pero acá no se pregunta nada. El
        // movimiento existe; la cuota de Crediágil pagada el 5 está pagada aunque venza el 15.
        val sinteticas = loadCreditRulePairs(uid).map { it.first } + loadCardRulePairs(uid).map { it.first }
        val derivadas = if (sinteticas.isEmpty()) emptyList() else {
            val pagos = cargarPagosDeDeuda(uid, today)
            pagosDeDeudaPorPeriodo(sinteticas, pagos, settings = periodo).mapNotNull { (ruleId, porPeriodo) ->
                val rule = sinteticas.first { it.id == ruleId }
                val due = ocurrenciaPorPreguntar(today, rule, periodo) ?: return@mapNotNull null
                val pago = porPeriodo[periodOf(due)] ?: return@mapNotNull null
                // **Cuánta plata fue.** El monto no filtra —no puede: movi no conoce el extracto,
                // y el saldo de la tarjeta o la cuota del crédito no son comparables con lo que se
                // movió (ver `PagosDeDeuda.kt`)— así que un abono de $50.000 salda el periodo
                // igual que un pago completo. Lo que queda es decirlo: la fila viaja con el monto
                // para que el dueño vea el abono en vez de un «ya ocurrió» pelado.
                //
                // Y es la plata que SALIÓ DE LA CUENTA, no la que bajó la deuda: en una cuota son
                // distintas a propósito ($12.157 de capital de una cuota de $26.485).
                val salida = plataQueSalio(pago, pagos)
                OccurrenceState(
                    ruleId = ruleId,
                    period = periodOf(due),
                    dueDate = due.toString(),
                    periodoDelDueno = periodoDelDueno(due, periodo),
                    occurred = true,
                    eventId = pago.id,
                    // No hubo confirmación que fechar: lo más cierto que se puede decir es cuándo
                    // quedó respaldada, que es cuándo se hizo el pago.
                    confirmedAt = pago.timestamp,
                    derivadaDeUnMovimiento = true,
                    montoDelPago = salida.amount,
                    monedaDelPago = salida.currency,
                )
            }
        }
        call.respond(estados + derivadas)
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
        // Esta ruta atrapa el body sola desde antes de `configureStatusPages`, que hoy hace lo
        // mismo para toda la API; se deja porque su mensaje nombra qué no se pudo leer.
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
            sellarOcurrencia(uid, rule, body.period, body.eventId, today)
        }
        when (resultado) {
            is MarcaResult.Ok -> call.respond(HttpStatusCode.Created, resultado.occurrence)
            is MarcaResult.Error ->
                if (resultado.message == null) call.respond(resultado.code)
                else call.respond(resultado.code, resultado.message)
        }
    }

    /**
     * **«No, ese movimiento no es esto»**, guardado para siempre.
     *
     * Es la única forma de revertir un emparejamiento automático, y por eso tiene que persistir:
     * la marca automática no es una fila que borrar —se deriva en cada lectura— así que un rechazo
     * que viviera en la pantalla duraría hasta el próximo F5 y después Movi volvería a emparejar
     * lo mismo. El «no» tiene que sobrevivir a la lectura siguiente o no sirve de nada.
     *
     * Sirve igual para bajar una PROPUESTA que el dueño ya descartó, que es lo que la pantalla
     * hacía en memoria hasta hoy.
     *
     * **Idempotente**: repetirlo no falla ni recorre la fecha. Si ya está rechazado, está
     * rechazado — un doble toque desde una conexión mala no puede ser un error, y mover
     * `rejectedAt` no agregaría ninguna información (lo que importa es el hecho, no el minuto).
     *
     * **No se valida que la regla exista.** Un rechazo sobre una regla borrada no le hace daño a
     * nadie: nadie lo vuelve a leer. Lo que sí se valida es el movimiento, porque un id ajeno o
     * inventado en esta tabla sería una forma silenciosa de escribir basura con el nombre de otro.
     */
    post("/api/recurring-rules/{id}/occurrence/rechazo") {
        val uid = call.userId()
        val ruleId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val body = try {
            call.receive<RechazarOcurrenciaRequest>()
        } catch (e: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, "No se pudo leer el rechazo: ${e.message}")
        }
        val eventId = body.eventId.trim()
        if (eventId.isEmpty()) {
            return@post call.respond(HttpStatusCode.BadRequest, "Falta el movimiento que se rechaza.")
        }
        // `null` = salió bien. Un tipo propio para «no devuelvo nada» habría obligado a una rama
        // muerta en el `when` del sellado, que comparte [MarcaResult] y nunca puede dar vacío.
        val problema: MarcaResult.Error? = dbQuery {
            val existe = Events.selectAll()
                .where { (Events.id eq eventId) and (Events.userId eq uid) }
                .firstOrNull() != null
            if (!existe) return@dbQuery MarcaResult.Error(HttpStatusCode.BadRequest, "Ese movimiento no existe.")
            val yaRechazado = OccurrenceRejections.selectAll()
                .where {
                    (OccurrenceRejections.userId eq uid) and
                        (OccurrenceRejections.ruleId eq ruleId) and
                        (OccurrenceRejections.eventId eq eventId)
                }
                .firstOrNull() != null
            // Se consulta antes en vez de borrar-e-insertar: así `rejectedAt` conserva CUÁNDO se
            // dijo que no la primera vez, y un reintento no se disfraza de rechazo nuevo.
            if (!yaRechazado) {
                OccurrenceRejections.insert {
                    it[OccurrenceRejections.userId] = uid
                    it[OccurrenceRejections.ruleId] = ruleId
                    it[OccurrenceRejections.eventId] = eventId
                    it[rejectedAt] = System.currentTimeMillis()
                }
            }
            null
        }
        if (problema == null) call.respond(HttpStatusCode.NoContent)
        else if (problema.message == null) call.respond(problema.code)
        else call.respond(problema.code, problema.message)
    }

    /** Deshacer: marcar por error tiene que poder revertirse, y sin ceremonia. */
    delete("/api/recurring-rules/{id}/occurrence/{period}") {
        val uid = call.userId()
        val ruleId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val period = call.parameters["period"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        // Mismo chequeo que al marcar: un periodo mal escrito es un pedido mal hecho (400), no
        // «no había nada que deshacer» (404).
        if (!PERIOD_REGEX.matches(period)) {
            return@delete call.respond(HttpStatusCode.BadRequest, "Periodo inválido: usa \"YYYY-MM\".")
        }
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

/**
 * **La respuesta de `GET /api/payments/upcoming`** para [uid] en [hoy]: el vencimiento vigente de
 * cada regla, real o sintética.
 *
 * Afuera de la ruta para poder probarla con un «hoy» fijo: los casos que importan dependen del día
 * (un período que arrancó por excepción, un pago de hace dos días todavía en gracia) y `AppClock`
 * no se puede mover desde una prueba.
 */
internal suspend fun proximosPagos(uid: String, hoy: java.time.LocalDate): List<UpcomingPayment> {
    // Misma lectura que el barrido y que `/api/reminders/channels` — ver [ReminderConfig].
    // Antes era un `System.getenv` suelto, que ignoraba `server/.env` y podía dar un número
    // distinto al que de verdad usa el scheduler.
    val leadDays = leadDaysOf(uid)
    // El período del dueño decide qué vencimiento está en juego (ver `dueDateFor`).
    val periodo = ajustesDePeriodoDe(uid)
    val rules = dbQuery {
        RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
    }
    val creditRules = loadCreditRulePairs(uid).map { it.first }
    // F20: el pago de la tarjeta también es un próximo pago — con la deuda actual como monto.
    val cardRules = loadCardRulePairs(uid).map { it.first }
    val sinteticas = creditRules + cardRules
    val ocurridos = ocurridosDeLosVencimientos(uid, hoy, periodo, sinteticas)
    return upcomingPayments(rules + sinteticas, hoy, leadDays, ocurridos, periodo)
}

/**
 * **regla → períodos que ya ocurrieron**, en el mapa `occurredPeriods` con el que `dueDateFor` rueda
 * un vencimiento. Lo leen «Próximos» ([proximosPagos]) y el barrido de recordatorios
 * (`queAvisarle`), y los dos tienen que ver lo mismo que el checklist del período.
 *
 * Son tres fuentes, y las tres entran por el MISMO parámetro que un sello a mano y no por un `if`
 * aparte: así el vencimiento vigente rueda al mes que viene una sola vez, en `dueDateFor`, y todo lo
 * que deriva de él —el estado, el orden, la clave de dedupe de los avisos— lo hereda sin que nadie
 * tenga que acordarse. Y el APK que el dueño tiene instalado no ve ningún campo nuevo ni ningún valor
 * de enum que no conozca: ve la fecha correcta.
 *
 *  1. **Los sellos** de `recurring_occurrences` que siguen valiendo (`loadOccurredBy`).
 *  2. **Lo que Movi emparejó solo** en una regla real ([estadosDeLasOcurrenciasReales], la misma
 *     respuesta del checklist). El 24-sep «Próximos» decía «Celular · Vencido hace 2 días» con el
 *     pago «Celular» del 22 en la base y el checklist dándolo por hecho: el emparejamiento se
 *     deriva en cada lectura y no escribe sello, así que un mapa armado solo con sellos no lo veía.
 *     Se toma la respuesta del checklist entera y no una segunda pasada del emparejador: una sola
 *     decisión, incluida la reserva de ids entre reglas y el «con dos candidatos, pregunto» — un
 *     emparejamiento con dudas sale `occurred = false` y acá no cuenta.
 *  3. **Las cuotas sintéticas ya pagadas.** Las reglas de crédito y tarjeta no se sellan a mano —el
 *     POST de ocurrencias las rechaza a propósito— así que su estado salía solo del calendario: la
 *     app decía «Vencido hace 5 días» sobre la cuota de Crediágil con el pago registrado, con sus
 *     dos patas, en la misma base. Ver `PagosDeDeuda.kt`.
 */
internal suspend fun ocurridosDeLosVencimientos(
    uid: String,
    hoy: java.time.LocalDate,
    periodo: PeriodSettings,
    sinteticas: List<RecurringRule>,
): Map<String, Set<String>> {
    val reales = dbQuery {
        val emparejadas = estadosDeLasOcurrenciasReales(uid, hoy, periodo)
            .filter { it.occurred }
            .groupBy({ it.ruleId }, { it.period })
            .mapValues { (_, periodos) -> periodos.toSet() }
        unirOcurridos(loadOccurredBy(uid), emparejadas)
    }
    val derivadas = if (sinteticas.isEmpty()) emptyMap()
        else periodosSaldados(sinteticas, cargarPagosDeDeuda(uid, hoy), settings = periodo)
    return unirOcurridos(reales, derivadas)
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

/**
 * **Sellar un período de [rule] como ocurrido, con o sin movimiento que lo pruebe.**
 *
 * Es el cuerpo entero de `POST /api/recurring-rules/{id}/occurrence`, extraído para que el alta de
 * una regla **desde un movimiento** selle por el mismo camino en vez de copiarle las guardas (ver
 * [com.jvillada.movi.shared.model.RecurringRule.eventoDeOrigen]). Copiarlas habría dejado dos
 * versiones de un conjunto de reglas de seguridad que está argumentado una por una acá, y la
 * segunda copia se habría quedado atrás en el primer cambio.
 *
 * Idempotente: reescribe la fila del período. Eso es lo que hace que «no fue este, fue aquel»
 * funcione sin un paso de deshacer en el medio.
 */
private fun org.jetbrains.exposed.sql.Transaction.sellarOcurrencia(
    uid: String,
    rule: RecurringRule,
    period: String,
    eventIdCrudo: String?,
    today: java.time.LocalDate,
): MarcaResult {
    val ruleId = rule.id
    // **Techo: el vencimiento de ese periodo tiene que haber LLEGADO — por día, no por
    // mes.** Es la misma guarda que el GET, escrita igual.
    //
    // Fue un techo por mes, y ahí quedaba un hueco: el 27 de agosto, `"2026-08"` sobre
    // una regla de día 31 pasaba —el mes ya empezó— y le apagaba el vencimiento del 31,
    // que todavía no había llegado. No es alcanzable desde la pantalla (solo manda el
    // periodo que le dio el GET), pero este archivo ya argumenta, para las cuatro
    // puertas, que la UI ofrece y el endpoint no puede confiar en eso. Vale igual acá.
    val vencimientoDelPeriodo = occurrenceInMonth(YearMonth.parse(period), rule.dayOfMonth)
    if (vencimientoDelPeriodo.isAfter(today)) {
        return MarcaResult.Error(
            HttpStatusCode.BadRequest,
            "Ese vencimiento todavía no llegó: no se puede dar por ocurrido.",
        )
    }
    // Y un piso, para que un cliente con un bug no ensucie la tabla con periodos
    // arqueológicos que además queman ids en `usedEventIds` (un movimiento sellado no
    // vuelve a proponerse nunca).
    if (period < periodOf(today.minusMonths(MAX_MESES_HACIA_ATRAS))) {
        return MarcaResult.Error(
            HttpStatusCode.BadRequest,
            "Ese periodo es demasiado viejo para darlo por ocurrido.",
        )
    }
    val eventId = eventIdCrudo?.trim()?.takeIf { it.isNotEmpty() }
    if (eventId != null) {
        val evento = Events.selectAll()
            .where { (Events.id eq eventId) and (Events.userId eq uid) }
            .firstOrNull()
            ?: return MarcaResult.Error(HttpStatusCode.BadRequest, "Ese movimiento no existe.")
        val anulado = VoidEvents.selectAll()
            .where { (VoidEvents.originalEventId eq eventId) and (VoidEvents.userId eq uid) }
            .firstOrNull() != null
        if (anulado) {
            return MarcaResult.Error(HttpStatusCode.BadRequest, "Ese movimiento está anulado.")
        }
        // Las mismas dos puertas que cierra `occurrenceCandidatesFor`, cerradas también
        // acá: la UI solo ofrece candidatos, pero el endpoint no puede confiar en eso.
        if (evento[Events.transferId] != null || isReservedCategory(evento[Events.category])) {
            return MarcaResult.Error(
                HttpStatusCode.BadRequest,
                "Un traspaso o un asiento interno no puede ser la ocurrencia de un recurrente.",
            )
        }
        // Un mismo movimiento no puede cerrar dos periodos: sería una sola entrada de
        // plata dando por saldados dos meses.
        val yaUsado = RecurringOccurrences.selectAll()
            .where { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.eventId eq eventId) }
            .any { it[RecurringOccurrences.ruleId] != ruleId || it[RecurringOccurrences.period] != period }
        if (yaUsado) {
            return MarcaResult.Error(
                HttpStatusCode.Conflict,
                "Ese movimiento ya está marcado como la ocurrencia de otro periodo.",
            )
        }
    }
    val now = System.currentTimeMillis()
    RecurringOccurrences.deleteWhere {
        (RecurringOccurrences.userId eq uid) and
            (RecurringOccurrences.ruleId eq ruleId) and
            (RecurringOccurrences.period eq period)
    }
    RecurringOccurrences.insert {
        it[RecurringOccurrences.userId] = uid
        it[RecurringOccurrences.ruleId] = ruleId
        it[RecurringOccurrences.period] = period
        it[RecurringOccurrences.eventId] = eventId
        it[confirmedAt] = now
    }
    return MarcaResult.Ok(
        RecurringOccurrence(
            ruleId = ruleId,
            period = period,
            eventId = eventId,
            confirmedAt = now,
        ),
    )
}

/**
 * **El movimiento que originó la regla queda como su evidencia**: sella el período de ese
 * movimiento con ese `eventId`.
 *
 * Por qué hace falta aunque el server ya empareje solo: `ocurrenciaConcluyente` prefiere preguntar
 * cuando hay DOS candidatos concluyentes, y ahí volvería la molestia original —Movi preguntando
 * por el pago que el dueño acaba de convertir en regla—. Sellarlo explícitamente cierra ese hueco
 * en el único momento en que Movi sabe, sin heurística, cuál es el movimiento: cuando el cliente
 * se lo dice.
 *
 * **La clave del sello es el mes del VENCIMIENTO, no el del movimiento.** Con corte 25 un pago del
 * 5 de septiembre prueba el vencimiento del 30 de agosto, y ese sello se llama `"2026-08"`: por eso
 * se busca primero la ocurrencia que cae en el **período del dueño** que contiene al movimiento
 * ([ocurrenciaEnJuego]) y recién después se la nombra con [periodOf].
 *
 * **Nunca falla el alta.** Devuelve `true`/`false` para poder contarlo en una prueba, pero quien
 * llama ignora el `false`: una regla sin sello es una pregunta de más, una regla que no se creó es
 * el pedido del dueño perdido. Un período sin ocurrencia (acortado a mano), un movimiento anulado,
 * un traspaso, un id ajeno o un movimiento que ya usa otra regla: todos caen acá y se van en
 * silencio.
 */
private fun org.jetbrains.exposed.sql.Transaction.sellarElMovimientoDeOrigen(
    uid: String,
    rule: RecurringRule,
    eventId: String,
    today: java.time.LocalDate,
    settings: PeriodSettings,
): Boolean {
    val fila = Events.selectAll()
        .where { (Events.id eq eventId) and (Events.userId eq uid) }
        .firstOrNull() ?: return false
    val fecha = epochMillisToAppDate(fila[Events.timestamp])
    val period = ocurrenciaEnJuego(fecha, rule.dayOfMonth, settings)?.let(::periodOf) ?: return false
    return sellarOcurrencia(uid, rule, period, eventId, today) is MarcaResult.Ok
}

/**
 * **El estado de cada recurrente REAL en el período en curso**, con lo que Movi emparejó solo
 * adentro (ver `ocurrenciaConcluyente`). Es la respuesta de `GET /api/payments/occurrences` sin las
 * sintéticas de crédito y tarjeta.
 *
 * **Está afuera de la ruta porque hay dos que la necesitan y no pueden contestar distinto.** El
 * 23-sep, Movi AI le dijo al dueño «todavía te faltan $291.677 de recurrentes (Tía Caro y Coomeva
 * Familiar)» mientras su Inicio decía «Pagaste los 12 del período»: el contexto del asistente
 * miraba solo los sellos guardados en `recurring_occurrences`, y esos tres (Tía Caro, Coomeva,
 * Celular) los había emparejado Movi SOLO — se derivan en cada lectura y no se escriben. El
 * asistente contradecía a la pantalla con los datos de la misma base.
 *
 * Corre dentro de una transacción (la llaman `dbQuery` de la ruta y el armado del contexto).
 */
internal fun org.jetbrains.exposed.sql.Transaction.estadosDeLasOcurrenciasReales(
    uid: String,
    today: java.time.LocalDate,
    periodo: com.jvillada.movi.shared.model.PeriodSettings,
): List<OccurrenceState> {
    val diasDelPeriodoEnCurso = diasDelPeriodo(today, periodo)
    // Solo reglas REALES. La cuota de un crédito y el pago de una tarjeta son reglas
    // sintéticas derivadas de `credit_terms`/`card_terms`, con su propia pantalla y su
    // propia forma de saldarse (ahí el pago mueve la deuda, que es un hecho más fuerte
    // que un sello). Meterlas acá sería un segundo mecanismo compitiendo con ese.
    //
    // Siguen sin entrar por acá: lo que se agregó abajo NO sella nada — lee el pago que
    // ya está registrado y lo reporta. Es la otra mitad de este mismo argumento, la que
    // faltaba: el hecho más fuerte existía y nadie lo leía.
    val rules = RecurringRules.selectAll()
        .where { RecurringRules.userId eq uid }
        .map { it.toRule() }
    if (rules.isEmpty()) return emptyList()

    val ocurrencias = loadOccurrenceRows(uid).associateBy { it.ruleId to it.period }
    val ocurridos = loadOccurredBy(uid)
    val usados = loadUsedOccurrenceEventIds(uid)
    // Los «no fue este» guardados: se excluyen de las propuestas Y del emparejamiento
    // automático. Sin esto, rechazar algo que Movi emparejó solo no serviría de nada —
    // la lectura siguiente lo volvería a emparejar, para siempre.
    val rechazados = loadRejectedPairs(uid)
    // Solo la franja donde puede haber candidatos, no todos los movimientos de la vida
    // del usuario: desde el primero del mes (el piso del emparejador) hasta la ventana
    // por delante del vencimiento más tardío posible.
    // El piso baja también hasta la ventana de una ocurrencia del período ANTERIOR que
    // sigue en gracia (ver `ocurrenciaPorPreguntar`): sus candidatos caen antes del corte.
    val pisoDeLaGracia = today.minusDays(DEFAULT_GRACE_DAYS + OCCURRENCE_WINDOW_DAYS)
    val eventos = loadEventsBetween(
        uid = uid,
        desde = appDateToEpochMillis(minOf(diasDelPeriodoEnCurso.start, pisoDeLaGracia)),
        hastaExclusivo = appDateToEpochMillis(
            diasDelPeriodoEnCurso.endInclusive.plusDays(OCCURRENCE_WINDOW_DAYS + 1),
        ),
    )

    // **Qué reglas tienen algo que decir, y con qué vencimiento.** Se calcula una vez y lo
    // usan las dos pasadas de abajo. El orden es por id de regla y no el que devolvió la
    // base: la pasada 1 reserva ids a medida que empareja, así que un orden que cambiara
    // entre recargas haría que dos reglas se turnaran el mismo movimiento. Ver ahí.
    val enJuego = rules.sortedBy { it.id }.mapNotNull { rule ->
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
        // La del PERÍODO en curso (ver arriba). Un período acortado a mano que no alcanza a
        // contener el día de la regla no tiene nada que preguntar.
        //
        // Salvo la del período ANTERIOR mientras siga en gracia: con corte 25, el pago
        // del 24 sin marcar se sigue ofreciendo («Ya lo pagué») del 25 al 29, en vez de
        // desaparecer al día siguiente de vencer. Ver `ocurrenciaPorPreguntar`.
        val due = ocurrenciaPorPreguntar(today, rule, periodo) ?: return@mapNotNull null
        // Una regla no tiene ocurrencia antes de su arranque: un crédito desembolsado
        // este mes no debe cuotas de los meses de antes, y su primera cuota cae DESPUÉS
        // del desembolso, no el mismo día. En cambio el período del movimiento que originó
        // un recurrente sí tiene ocurrencia — son dos semánticas distintas y las dos viven
        // en `arranqueDeLaRegla`; ver `RecurringRule.arranqueEsDesembolso`.
        if (!ruleIsActiveOn(rule, due, periodo)) return@mapNotNull null
        rule to due
    }

    // ── Pasada 1: lo que Movi empareja SOLO ──────────────────────────────────
    //
    // Hasta acá este endpoint solo proponía: la casilla del checklist sellaba con
    // `eventId = null` y daba por pagado **sin ninguna evidencia**. En los datos reales
    // del dueño quedaron tres sellos así cuyo movimiento SÍ existía, con el nombre casi
    // calcado. Ahora, cuando hay un único movimiento concluyente (ver
    // `ocurrenciaConcluyente`), la fila sale ya emparejada; con cero o con dos, se
    // pregunta como siempre.
    //
    // **Y no se escribe nada en `recurring_occurrences`.** Esto se DERIVA en cada lectura,
    // igual que las sintéticas de abajo y por el mismo motivo: un sello sobrevive a que su
    // evidencia cambie, una derivación no. Si el movimiento se anula, se borra, se le
    // corrige la fecha o se le cambia el monto, la marca desaparece sola sin que ningún
    // camino de borrado tenga que acordarse de esta tabla.
    //
    // **La reserva de ids.** `reservados` arranca con los ya sellados y va creciendo: un
    // movimiento que la regla A emparejó no puede ser además el candidato de la regla B en
    // la misma respuesta (una sola entrada de plata cerrando dos periodos es exactamente
    // el «marcar de más» que este archivo evita). Se recorre en orden de id de regla, que
    // es estable, así que ante un empate imposible —el mismo movimiento concluyente para
    // dos reglas distintas— gana siempre la misma y la respuesta no baila entre recargas.
    val automaticas = mutableMapOf<String, FinancialEvent>()
    val reservados = usados.toMutableSet()
    enJuego.forEach { (rule, due) ->
        if (periodOf(due) in ocurridos[rule.id].orEmpty()) return@forEach
        // El día todavía no llegó: no se empareja nada, igual que no se pregunta nada.
        if (due.isAfter(today)) return@forEach
        val sinRechazados = eventos.filterNot { (rule.id to it.id) in rechazados }
        val concluyente =
            ocurrenciaConcluyente(rule, due, sinRechazados, reservados, settings = periodo)
                ?: return@forEach
        automaticas[rule.id] = concluyente
        reservados += concluyente.id
    }

    // ── Pasada 2: la respuesta ───────────────────────────────────────────────
    return enJuego.mapNotNull { (rule, due) ->
        // La clave del sello sigue siendo el mes del vencimiento (ver `periodOf`): estable
        // aunque el dueño cambie su corte. El nombre que se muestra es el del período.
        val periodoEnCurso = periodOf(due)
        val nombreDelPeriodo = periodoDelDueno(due, periodo)
        val cerrado = periodoEnCurso in ocurridos[rule.id].orEmpty()
        val automatica = automaticas[rule.id]
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
                    periodoDelDueno = nombreDelPeriodo,
                )
            }
            automatica != null -> OccurrenceState(
                ruleId = rule.id,
                period = periodoEnCurso,
                dueDate = due.toString(),
                occurred = true,
                eventId = automatica.id,
                // No hubo confirmación que fechar —nadie tildó nada—, así que lo más cierto
                // que se puede decir es cuándo ocurrió el movimiento que la prueba. Mismo
                // criterio que las sintéticas de abajo.
                confirmedAt = automatica.timestamp,
                // Las dos marcas, y significan cosas distintas: `derivada` = «no hay sello
                // que borrar, no le ofrezcas Deshacer»; `automatica` = «además, esto lo
                // dedujo Movi y se puede rechazar». Ver el KDoc de `OccurrenceState`.
                derivadaDeUnMovimiento = true,
                automatica = true,
                montoDelPago = automatica.amount,
                monedaDelPago = automatica.currency,
                periodoDelDueno = nombreDelPeriodo,
            )
            // El día todavía no llegó: no se pregunta nada. Preguntar «¿ya ocurrió?» por
            // algo que vence dentro de tres semanas es ruido, y peor: invita a cerrar un
            // periodo antes de que pase.
            due.isAfter(today) -> null
            else -> OccurrenceState(
                ruleId = rule.id,
                period = periodoEnCurso,
                dueDate = due.toString(),
                occurred = false,
                // `reservados` y no `usados`: lo que otra regla ya emparejó sola no se
                // vuelve a ofrecer acá. Y lo rechazado se saca antes de puntuar, para que
                // un «no fue este» no se gaste uno de los tres lugares de la propuesta.
                candidates = occurrenceCandidatesFor(
                    rule,
                    due,
                    eventos.filterNot { (rule.id to it.id) in rechazados },
                    reservados,
                    settings = periodo,
                ),
                periodoDelDueno = nombreDelPeriodo,
            )
        }
    }
}
