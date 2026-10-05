package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DebitosDescartados
import com.jvillada.movi.server.db.Events
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
import com.jvillada.movi.shared.model.CreditTerms
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

/**
 * # Lo que el banco cobra solo
 *
 * `GET /api/debitos-automaticos`: las cuotas (y, desde la parte 3, los recurrentes) que el banco
 * debita solo, **ya vencidas y sin ningún movimiento que las pruebe**, armadas para que el dueño las
 * confirme desde «Por revisar». El porqué de que se deriven en cada lectura y no vivan en
 * `sms_messages` está en [DebitoAutomaticoPorConfirmar].
 *
 * Nada de esto escribe un movimiento. La cuota se confirma por el camino de siempre
 * (`POST /api/payments/installment`, con los ids que la propuesta trae); «No se cobró» es
 * `POST /api/debitos-automaticos/descartar`.
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
internal suspend fun debitosPorConfirmar(uid: String, hoy: LocalDate, ahora: Long): List<DebitoAutomaticoPorConfirmar> {
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
        }.sortedBy { it.vence }
    }
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
