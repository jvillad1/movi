package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DebitosDescartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.reminders.occurrenceInMonth
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.cargarPagosDeDeuda
import com.jvillada.movi.server.reminders.entraAlBarridoDeAvisos
import com.jvillada.movi.server.reminders.ocurrenciaPorPreguntar
import com.jvillada.movi.server.reminders.periodOf
import com.jvillada.movi.server.reminders.periodosSaldados
import com.jvillada.movi.server.reminders.ruleIsActiveOn
import com.jvillada.movi.server.reminders.virtualRuleFor
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.ConfirmarDebitoAutomatico
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DEBITO_DE_REGLA_SIN_CUENTA
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.rechazoDeLosTextos
import com.jvillada.movi.shared.model.rechazoDelMonto
import com.jvillada.movi.shared.model.validarDebitoDeLaRegla
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.DescartarDebitoAutomatico
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

/**
 * # Lo que el banco cobra solo
 *
 * `GET /api/debitos-automaticos`: las cuotas de crédito y los recurrentes que el banco debita solo,
 * **ya vencidos y sin ningún movimiento que los pruebe**, armados para que el dueño los confirme
 * desde «Por revisar». El porqué de que se deriven en cada lectura y no vivan en `sms_messages` está
 * en [DebitoAutomaticoPorConfirmar].
 *
 * La lectura no escribe nada. Lo que escribe lo dispara siempre un toque del dueño:
 * - la cuota de un crédito se confirma por el camino de siempre (`POST /api/payments/installment`,
 *   con los ids que la propuesta trae);
 * - un recurrente, por `POST /api/debitos-automaticos/confirmar` (el gasto + el sello del período,
 *   juntos);
 * - «No se cobró» es `POST /api/debitos-automaticos/descartar`.
 */
fun Route.debitosAutomaticosRoutes() {
    get("/api/debitos-automaticos") {
        call.respond(debitosPorConfirmar(call.userId(), AppClock.today(), System.currentTimeMillis()))
    }

    /**
     * **«No se cobró»**: el banco no debitó ese vencimiento (sin saldo, lo pagó por otro lado). Queda
     * escrito para ese período y no vuelve; el período siguiente es otro vencimiento y sí se propone.
     *
     * Idempotente (un doble toque no falla ni mueve la fecha). No se valida que la regla exista: la
     * fila lleva el usuario, así que una regla ajena o inventada no le saca nada a nadie.
     */
    /**
     * **«Sí, se cobró» de un recurrente que se debita solo**: anota el gasto (con el id que trajo la
     * propuesta, la cuenta y la categoría de la regla, fechado el día del vencimiento) y sella ese
     * período con ese movimiento, **en una sola transacción**: un gasto anotado sin su sello volvería
     * a proponerse, y un sello sin gasto es justo lo que el checklist ya no acepta.
     *
     * El sello pasa por [sellarOcurrencia], el mismo de «Sí, fue este», con todas sus guardas (el
     * vencimiento tiene que haber llegado, el movimiento no puede cerrar otro período…).
     *
     * Idempotente por el id: con el mismo `eventoId` un segundo pedido devuelve el movimiento que ya
     * quedó (200), con el monto que quedó. 404 si la regla no es del usuario; 409 si el id ya lo usa
     * un movimiento ajeno o anulado.
     */
    post("/api/debitos-automaticos/confirmar") {
        val uid = call.userId()
        val pedido = runCatching { call.receive<ConfirmarDebitoAutomatico>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "No se pudo leer el pedido.")
        if (!PERIODO.matches(pedido.periodo)) {
            return@post call.respond(HttpStatusCode.BadRequest, "Periodo inválido: usa \"YYYY-MM\".")
        }
        if (!pedido.eventoId.startsWith("ev_deb_") || pedido.eventoId.length > 50) {
            return@post call.respond(HttpStatusCode.BadRequest, "Ese identificador no es de un débito automático.")
        }
        rechazoDelMonto(pedido.monto)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
        val hoy = AppClock.today()
        val resultado: ConfirmacionDelDebito = try {
            dbQuery { confirmarElRecurrente(uid, pedido, hoy) }
        } catch (e: DebitoRechazado) {
            ConfirmacionDelDebito.Rechazada(e.codigo, e.message ?: "")
        }
        when (resultado) {
            is ConfirmacionDelDebito.Hecha -> call.respond(
                if (resultado.nueva) HttpStatusCode.Created else HttpStatusCode.OK,
                resultado.evento,
            )
            is ConfirmacionDelDebito.Rechazada -> call.respond(resultado.codigo, resultado.motivo)
        }
    }

    post("/api/debitos-automaticos/descartar") {
        val uid = call.userId()
        val cuerpo = runCatching { call.receive<DescartarDebitoAutomatico>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "No se pudo leer el pedido.")
        val ruleId = cuerpo.ruleId.trim()
        if (ruleId.isEmpty() || ruleId.length > 80) {
            return@post call.respond(HttpStatusCode.BadRequest, "Falta el pago que no se cobró.")
        }
        if (!PERIODO.matches(cuerpo.periodo)) {
            return@post call.respond(HttpStatusCode.BadRequest, "Periodo inválido: usa \"YYYY-MM\".")
        }
        dbQuery {
            val ya = DebitosDescartados.selectAll()
                .where {
                    (DebitosDescartados.userId eq uid) and (DebitosDescartados.ruleId eq ruleId) and
                        (DebitosDescartados.periodo eq cuerpo.periodo)
                }
                .any()
            if (!ya) {
                DebitosDescartados.insert {
                    it[userId] = uid
                    it[DebitosDescartados.ruleId] = ruleId
                    it[periodo] = cuerpo.periodo
                    it[descartadoEn] = System.currentTimeMillis()
                }
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

private val PERIODO = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")

private sealed interface ConfirmacionDelDebito {
    data class Hecha(val evento: FinancialEvent, val nueva: Boolean) : ConfirmacionDelDebito
    data class Rechazada(val codigo: HttpStatusCode, val motivo: String) : ConfirmacionDelDebito
}

/** Corta la transacción entera: con una excepción, Exposed deshace lo que se alcanzó a escribir. */
private class DebitoRechazado(val codigo: HttpStatusCode, motivo: String) : RuntimeException(motivo)

/**
 * El cuerpo de `POST /api/debitos-automaticos/confirmar`, dentro de la transacción. Ver la ruta.
 */
private fun Transaction.confirmarElRecurrente(
    uid: String,
    pedido: ConfirmarDebitoAutomatico,
    hoy: LocalDate,
): ConfirmacionDelDebito {
    val rule = RecurringRules.selectAll()
        .where { (RecurringRules.id eq pedido.ruleId) and (RecurringRules.userId eq uid) }
        .firstOrNull()?.toRule()
        ?: return ConfirmacionDelDebito.Rechazada(HttpStatusCode.NotFound, "Ese recurrente no existe.")
    if (rule.seDebitaSolo != true) {
        return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, "Ese recurrente no se debita solo.")
    }
    validarDebitoDeLaRegla(true, rule.type, rule.accountId)?.let {
        return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, it)
    }
    val cuenta = Accounts.selectAll()
        .where { (Accounts.id eq rule.accountId!!) and (Accounts.userId eq uid) }
        .firstOrNull()?.toAccount()
        ?: return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, DEBITO_DE_REGLA_SIN_CUENTA)

    // ¿Ya está? El reintento de verdad trae el mismo id: se contesta lo que quedó, con su monto.
    val existente = Events.selectAll().where { Events.id eq pedido.eventoId }.firstOrNull()
    if (existente != null) {
        val anulado = VoidEvents.selectAll()
            .where { (VoidEvents.originalEventId eq pedido.eventoId) and (VoidEvents.userId eq uid) }
            .any()
        if (existente[Events.userId] != uid || anulado) {
            return ConfirmacionDelDebito.Rechazada(HttpStatusCode.Conflict, "Ese movimiento ya no se puede usar. Vuelve a abrir «Por revisar».")
        }
        sellarOcurrencia(uid, rule, pedido.periodo, pedido.eventoId, hoy).alFallar()
        return ConfirmacionDelDebito.Hecha(existente.toFinancialEvent(), nueva = false)
    }

    val vence = occurrenceInMonth(YearMonth.parse(pedido.periodo), rule.dayOfMonth)
    val evento = FinancialEvent(
        id = pedido.eventoId,
        accountId = cuenta.id,
        type = TransactionType.EXPENSE,
        amount = pedido.monto,
        currency = cuenta.currency,
        category = rule.category,
        // El nombre de la regla, para que se lea como lo que es y el emparejador lo reconozca por el
        // nombre si alguna vez se quita el sello.
        description = rule.name,
        timestamp = appDateToEpochMillis(vence) + MEDIODIA,
        source = EventSource.MANUAL,
        // Lo confirmó el dueño con su toque: no espera otra confirmación.
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )
    // Mismas guardas de texto que un movimiento cualquiera: la categoría y el nombre vienen de la
    // regla, que ya pasó por ellas, pero el insert no puede caer con un 500 por un texto largo.
    rechazoDeLosTextos(evento.category, evento.description)?.let {
        return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, it)
    }
    insertEventRow(uid, evento)
    // Si el sello no se puede poner (el vencimiento no llegó, el período es viejo…), la excepción
    // deshace también el gasto: nada a medias.
    sellarOcurrencia(uid, rule, pedido.periodo, pedido.eventoId, hoy).alFallar()
    return ConfirmacionDelDebito.Hecha(evento, nueva = true)
}

private fun MarcaResult.alFallar() {
    if (this is MarcaResult.Error) throw DebitoRechazado(code, message ?: "No se pudo marcar el período.")
}

private const val MEDIODIA = 12L * 60 * 60 * 1000

/**
 * **Lo que contesta `GET /api/debitos-automaticos`** para [uid] en [hoy], fuera de la ruta para
 * poder probarlo con un «hoy» fijo.
 *
 * Una cuota con débito automático se propone cuando, todo a la vez:
 *
 * 1. **su vencimiento ya llegó**: el que el checklist del período tiene en juego
 *    ([ocurrenciaPorPreguntar]: el del período del dueño, o el anterior si sigue en sus días de
 *    gracia), y es hoy o antes. El banco debita ese día, no antes;
 * 2. **ningún movimiento lo salda** ([periodosSaldados], la misma regla que tilda la fila de
 *    «Pagos del período»): si el dueño ya la anotó, la confirmó desde un aviso o la vinculó, no hay
 *    nada que preguntar;
 * 3. **el dueño no dijo «No se cobró»** para ese período;
 * 4. **no hay un aviso del banco pendiente por el mismo monto** alrededor del vencimiento: si el
 *    banco sí avisó, ese aviso es la evidencia y la bandeja no pinta dos tarjetas para un pago;
 * 5. la cuenta del débito y la del crédito siguen existiendo.
 *
 * El monto propuesto es la cuota pactada. No se estima nada más: el dueño la cambia si el banco
 * cobró otra cifra.
 */
internal suspend fun debitosPorConfirmar(uid: String, hoy: LocalDate, ahora: Long): List<DebitoAutomaticoPorConfirmar> =
    (cuotasPorConfirmar(uid, hoy, ahora) + recurrentesPorConfirmar(uid, hoy, ahora)).sortedBy { it.vence }

/** Las cuotas de crédito de [debitosPorConfirmar]. */
private suspend fun cuotasPorConfirmar(uid: String, hoy: LocalDate, ahora: Long): List<DebitoAutomaticoPorConfirmar> {
    val creditos: List<Pair<CreditTerms, String>> = dbQuery {
        Credits.join(Accounts, JoinType.INNER, Credits.accountId, Accounts.id)
            .selectAll()
            .where { (Credits.userId eq uid) and (Accounts.userId eq uid) }
            .map { it.toCreditTerms() to it[Accounts.name] }
            // Solo los que de verdad salen de una cuenta suya: una libranza o una cuota que paga otro
            // no se debitan (la hoja y el server ya no dejan guardar las dos cosas a la vez).
            .filter { (terms, _) -> !terms.debitoAutomaticoDesde.isNullOrBlank() && entraAlBarridoDeAvisos(terms) }
    }
    if (creditos.isEmpty()) return emptyList()
    val settings = ajustesDePeriodoDe(uid)
    val reglas = creditos.map { (terms, nombre) -> virtualRuleFor(terms, nombre) to terms }
    val saldados = periodosSaldados(reglas.map { it.first }, cargarPagosDeDeuda(uid, hoy), settings = settings)
    return dbQuery {
        val descartados = descartadosDe(uid)
        val cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() }.associateBy { it.id }
        reglas.mapNotNull { (rule, terms) ->
            val vence = vencimientoPorProponer(rule, hoy, settings) ?: return@mapNotNull null
            val periodo = periodOf(vence)
            if (periodo in saldados[rule.id].orEmpty()) return@mapNotNull null
            if ("${rule.id}@$periodo" in descartados) return@mapNotNull null
            val cuenta = cuentas[terms.debitoAutomaticoDesde] ?: return@mapNotNull null
            val deuda = cuentas[terms.accountId] ?: return@mapNotNull null
            if (hayUnAvisoPendienteDelMismoPago(uid, rule.amount, cuenta.currency, vence, ahora)) return@mapNotNull null
            val ids = idsLibresDelDebito(rule.id, periodo)
            DebitoAutomaticoPorConfirmar(
                ruleId = rule.id,
                periodo = periodo,
                origen = OrigenDelDebito.CUOTA_DE_CREDITO,
                nombre = rule.name,
                monto = rule.amount,
                moneda = cuenta.currency,
                vence = vence.toString(),
                cuentaId = cuenta.id,
                cuentaNombre = cuenta.name,
                categoria = rule.category,
                pataDelDineroId = ids.dinero,
                deudaId = deuda.id,
                pataDeLaDeudaId = ids.deuda,
                transferId = ids.transfer,
            )
        }
    }
}

/**
 * Los recurrentes comunes de [debitosPorConfirmar]: las reglas marcadas «se debita solo», con cuenta,
 * cuyo vencimiento en juego ya llegó y el checklist da por **abierto y sin candidatos** — la misma
 * respuesta de `GET /api/payments/occurrences` ([estadosDeLasOcurrenciasReales]). Con un candidato el
 * checklist ya pregunta «¿fue este?», y proponer anotar otro sería invitar a un duplicado.
 */
private suspend fun recurrentesPorConfirmar(uid: String, hoy: LocalDate, ahora: Long): List<DebitoAutomaticoPorConfirmar> {
    val settings = ajustesDePeriodoDe(uid)
    return dbQuery {
        val reglas = reglasRealesDe(uid).filter { it.seDebitaSolo == true && it.type == TransactionType.EXPENSE && it.accountId != null }
        if (reglas.isEmpty()) return@dbQuery emptyList()
        val estados = estadosDeLasOcurrenciasReales(uid, hoy, settings).associateBy { it.ruleId }
        val descartados = descartadosDe(uid)
        val cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() }.associateBy { it.id }
        reglas.mapNotNull { rule ->
            val estado = estados[rule.id] ?: return@mapNotNull null
            if (estado.occurred || estado.candidates.isNotEmpty()) return@mapNotNull null
            val vence = runCatching { LocalDate.parse(estado.dueDate) }.getOrNull() ?: return@mapNotNull null
            if (vence.isAfter(hoy)) return@mapNotNull null
            if ("${rule.id}@${estado.period}" in descartados) return@mapNotNull null
            val cuenta = cuentas[rule.accountId] ?: return@mapNotNull null
            if (cuenta.type.group == AccountGroup.DEUDA) return@mapNotNull null
            if (hayUnAvisoPendienteDelMismoPago(uid, rule.amount, cuenta.currency, vence, ahora)) return@mapNotNull null
            DebitoAutomaticoPorConfirmar(
                ruleId = rule.id,
                periodo = estado.period,
                origen = OrigenDelDebito.RECURRENTE,
                nombre = rule.name,
                monto = rule.amount,
                moneda = cuenta.currency,
                vence = vence.toString(),
                cuentaId = cuenta.id,
                cuentaNombre = cuenta.name,
                categoria = rule.category,
                pataDelDineroId = idDelGastoDelDebito(uid, rule.id, estado.period),
            )
        }
    }
}

/**
 * El id del gasto de un recurrente, determinista por (regla, período) y libre — mismo criterio que
 * [idsLibresDelDebito]. Libre quiere decir: sin usar, o usado por un movimiento VIVO de este mismo
 * dueño (eso solo pasa si se confirmó y el sello todavía no se ve: el reintento tiene que reusarlo).
 */
private fun Transaction.idDelGastoDelDebito(uid: String, ruleId: String, periodo: String): String {
    for (intento in 0 until 50) {
        val id = "ev_deb_${huella("$ruleId|$periodo|$intento")}_s"
        val fila = Events.selectAll().where { Events.id eq id }.firstOrNull() ?: return id
        val anulado = VoidEvents.selectAll()
            .where { (VoidEvents.originalEventId eq id) and (VoidEvents.userId eq uid) }
            .any()
        if (fila[Events.userId] == uid && !anulado) return id
    }
    return "ev_deb_${huella("$ruleId|$periodo|${System.nanoTime()}")}_s"
}

/**
 * El vencimiento de [rule] que se le pregunta al dueño hoy, si **ya llegó** y la regla ya corría ese
 * día; `null` si todavía no vence (o es anterior al arranque: la primera cuota de un crédito va
 * después del desembolso).
 */
internal fun vencimientoPorProponer(rule: RecurringRule, hoy: LocalDate, settings: PeriodSettings): LocalDate? {
    val vence = ocurrenciaPorPreguntar(hoy, rule, settings) ?: return null
    if (vence.isAfter(hoy)) return null
    if (!ruleIsActiveOn(rule, vence, settings)) return null
    return vence
}

/** `"<regla>@<período>"` de lo que el dueño ya dijo que no se cobró. */
internal fun Transaction.descartadosDe(uid: String): Set<String> =
    DebitosDescartados.selectAll()
        .where { DebitosDescartados.userId eq uid }
        .map { "${it[DebitosDescartados.ruleId]}@${it[DebitosDescartados.periodo]}" }
        .toSet()

/**
 * ¿Hay un aviso del banco **pendiente** por este monto alrededor del vencimiento? Se mira con la
 * misma función que usa «Agregar» para ofrecer el aviso parecido ([avisosPendientesParecidos]), en
 * dos ventanas: la de las últimas horas (un aviso que llegó hoy) y la del día del vencimiento (el
 * aviso de un débito llega ese día, y el dueño puede abrir la bandeja tres días después).
 */
private fun Transaction.hayUnAvisoPendienteDelMismoPago(
    uid: String,
    monto: Long,
    moneda: String,
    vence: LocalDate,
    ahora: Long,
): Boolean {
    val finDelVencimiento = appDateToEpochMillis(vence.plusDays(1))
    return listOf(ahora, finDelVencimiento).distinct().any { momento ->
        avisosPendientesParecidos(uid, monto, moneda, TransactionType.EXPENSE, ahora = momento).isNotEmpty()
    }
}

/** Los ids con que se confirma una propuesta. Ver [DebitoAutomaticoPorConfirmar] («Los ids»). */
internal data class IdsDelDebito(val transfer: String, val dinero: String, val deuda: String)

/**
 * **Ids deterministas por (regla, período)**, y libres: confirmar dos veces manda los mismos y no
 * duplica. Si ya los usa un movimiento —solo puede ser uno ANULADO, porque uno vivo habría saldado el
 * período y la propuesta no saldría—, se prueba con el siguiente intento. Globales y no por usuario:
 * `events.id` es la clave primaria de la tabla entera, y la regla ya lleva el id de una cuenta, que
 * es único.
 */
internal fun Transaction.idsLibresDelDebito(ruleId: String, periodo: String): IdsDelDebito {
    for (intento in 0 until 50) {
        val h = huella("$ruleId|$periodo|$intento")
        val ids = IdsDelDebito(transfer = "tr_deb_$h", dinero = "ev_deb_${h}_s", deuda = "ev_deb_${h}_e")
        val usados = Events.select(Events.id)
            .where { (Events.id inList listOf(ids.dinero, ids.deuda)) or (Events.transferId eq ids.transfer) }
            .any()
        if (!usados) return ids
    }
    // Cincuenta pagos anulados del mismo vencimiento no pasan; si pasara, un id nuevo no duplica nada.
    val h = huella("$ruleId|$periodo|${System.nanoTime()}")
    return IdsDelDebito(transfer = "tr_deb_$h", dinero = "ev_deb_${h}_s", deuda = "ev_deb_${h}_e")
}

private fun huella(texto: String): String =
    MessageDigest.getInstance("SHA-256").digest(texto.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(20)
