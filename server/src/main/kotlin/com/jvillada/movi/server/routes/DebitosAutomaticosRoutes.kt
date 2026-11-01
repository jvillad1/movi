package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DebitosDescartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.reminders.occurrenceInMonth
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.cargarPagosDeDeuda
import com.jvillada.movi.server.reminders.entraAlBarridoDeAvisos
import com.jvillada.movi.server.reminders.fechaEsperada
import com.jvillada.movi.server.reminders.occurrenceWindow
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.momentoDelSms
import kotlin.math.roundToLong
import com.jvillada.movi.server.reminders.ocurrenciaPorPreguntar
import com.jvillada.movi.server.reminders.pausadaEn
import com.jvillada.movi.server.reminders.periodOf
import com.jvillada.movi.server.reminders.periodosSaldados
import com.jvillada.movi.server.reminders.ruleIsActiveOn
import com.jvillada.movi.server.reminders.virtualRuleFor
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.GRACIA_DE_LO_ESPERADO_DIAS
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.ConfirmarDebitoAutomatico
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.NOTA_DEL_DEBITO_AUTOMATICO
import com.jvillada.movi.shared.model.OperacionDelAviso
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
 * - «Sí, se cobró» es `POST /api/debitos-automaticos/confirmar`: la cuota de dos patas de un crédito
 *   (por [escribirLasPatasDelAviso], la función de la confirmación de un aviso de dos patas), o el
 *   gasto de un recurrente con el sello de su período;
 * - «No se cobró» es `POST /api/debitos-automaticos/descartar`.
 */
fun Route.debitosAutomaticosRoutes() {
    /**
     * Vuelta 90: con `?conEsperados=1` (lo pide el cliente nuevo) la lista suma **lo esperado**: los
     * recurrentes con cuenta que ya debieron pasar y no tienen movimiento ([recurrentesPorConfirmar]).
     * Sin el parámetro contesta exactamente lo de antes: un APK viejo no sabe decir «¿te llegó?» y
     * pintaría la nómina como «Débito automático … ¿se cobró?».
     */
    get("/api/debitos-automaticos") {
        val conEsperados = call.request.queryParameters["conEsperados"] == "1"
        call.respond(debitosPorConfirmar(call.userId(), AppClock.today(), System.currentTimeMillis(), conEsperados))
    }

    /**
     * **«Sí, se cobró»**, con el monto que el dueño confirmó («Cambiar monto» manda otro). Todo en una
     * transacción, y con los ids que trajo la propuesta:
     *
     * - **La cuota de un crédito** (`credit_<cuenta>`): las dos patas por [escribirLasPatasDelAviso], la
     *   misma función que confirma un aviso de dos patas — el desglose (interés, seguro, capital) contra
     *   la deuda viva, [pagoDeCuotaLegs] y la idempotencia por ids. La fila de «Pagos del período» se
     *   tilda sola porque la deriva la pata de la deuda (`PagosDeDeuda.kt`).
     * - **Un recurrente**: el gasto (cuenta y categoría de la regla, fechado el día del vencimiento) y
     *   el sello de ese período con ese gasto, por [sellarOcurrencia] —el de «Sí, fue este», con sus
     *   guardas—. Si el sello no se puede poner, tampoco queda el gasto.
     *
     * El vencimiento tiene que haber llegado: el banco no debita antes. Con los mismos ids un segundo
     * pedido contesta lo que ya quedó (200), con el monto que quedó. 404 si el crédito o la regla no
     * son del usuario.
     */
    post("/api/debitos-automaticos/confirmar") {
        val uid = call.userId()
        val pedido = runCatching { call.receive<ConfirmarDebitoAutomatico>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "No se pudo leer el pedido.")
        if (!PERIODO.matches(pedido.periodo)) {
            return@post call.respond(HttpStatusCode.BadRequest, "Periodo inválido: usa \"YYYY-MM\".")
        }
        if (!esIdDelDebito(pedido.eventoId, "ev_deb_")) {
            return@post call.respond(HttpStatusCode.BadRequest, "Ese identificador no es de un débito automático.")
        }
        rechazoDelMonto(pedido.monto)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
        val hoy = AppClock.today()
        val ahora = System.currentTimeMillis()
        val resultado: ConfirmacionDelDebito = try {
            dbQuery {
                if (pedido.ruleId.startsWith(CREDIT_RULE_PREFIX)) confirmarLaCuota(uid, pedido, hoy, ahora)
                else confirmarElRecurrente(uid, pedido, hoy)
            }
        } catch (e: DebitoRechazado) {
            ConfirmacionDelDebito.Rechazada(e.codigo, e.message ?: "")
        }
        when (resultado) {
            is ConfirmacionDelDebito.Hecha -> call.respond(
                if (resultado.nueva) HttpStatusCode.Created else HttpStatusCode.OK,
                resultado.movimientos,
            )
            is ConfirmacionDelDebito.Rechazada -> call.respond(resultado.codigo, resultado.motivo)
        }
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

private fun esIdDelDebito(id: String?, prefijo: String): Boolean =
    id != null && id.startsWith(prefijo) && id.length <= 50

private sealed interface ConfirmacionDelDebito {
    data class Hecha(val movimientos: List<FinancialEvent>, val nueva: Boolean) : ConfirmacionDelDebito
    data class Rechazada(val codigo: HttpStatusCode, val motivo: String) : ConfirmacionDelDebito
}

/** Corta la transacción entera: con una excepción, Exposed deshace lo que se alcanzó a escribir. */
private class DebitoRechazado(val codigo: HttpStatusCode, motivo: String) : RuntimeException(motivo)

private const val TODAVIA_NO_VENCE = "Ese vencimiento todavía no llegó: el banco no lo ha cobrado."

/** La cuota de un crédito, dentro de la transacción. Ver la ruta. */
private fun Transaction.confirmarLaCuota(
    uid: String,
    pedido: ConfirmarDebitoAutomatico,
    hoy: LocalDate,
    ahora: Long,
): ConfirmacionDelDebito {
    val deudaId = pedido.ruleId.removePrefix(CREDIT_RULE_PREFIX)
    val terms = Credits.selectAll()
        .where { (Credits.userId eq uid) and (Credits.accountId eq deudaId) }
        .firstOrNull()?.toCreditTerms()
        ?: return ConfirmacionDelDebito.Rechazada(HttpStatusCode.NotFound, "Ese crédito no existe.")
    val desde = terms.debitoAutomaticoDesde
        ?: return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, "Ese crédito no se debita solo.")
    val transferId = pedido.transferId
    val pataDeLaDeuda = pedido.pataDeLaDeudaId
    if (!esIdDelDebito(transferId, "tr_deb_") || !esIdDelDebito(pataDeLaDeuda, "ev_deb_")) {
        return ConfirmacionDelDebito.Rechazada(HttpStatusCode.BadRequest, "Faltan los identificadores de la cuota.")
    }
    val vence = occurrenceInMonth(YearMonth.parse(pedido.periodo), terms.dayOfMonth)
    if (vence.isAfter(hoy)) return ConfirmacionDelDebito.Rechazada(HttpStatusCode.BadRequest, TODAVIA_NO_VENCE)
    val patas = DosPatasDelAviso(
        operacion = OperacionDelAviso.CUOTA,
        origenId = desde,
        destinoId = deudaId,
        monto = pedido.monto,
        // El día que debitó el banco, aunque el dueño lo confirme después: es el que salda la cuota
        // de ese período (ver `periodoQueSalda`).
        timestamp = appDateToEpochMillis(vence) + MEDIODIA,
        transferId = transferId!!,
        origenEventId = pedido.eventoId,
        destinoEventId = pataDeLaDeuda!!,
        nota = NOTA_DEL_DEBITO_AUTOMATICO,
    )
    // Anotado por el dueño, no leído de un aviso: sin texto del banco y con origen MANUAL.
    return when (val escritas = escribirLasPatasDelAviso(uid, patas, EventSource.MANUAL, textoDelAviso = null, ahora = ahora)) {
        is PatasDelAviso.Rechazadas -> ConfirmacionDelDebito.Rechazada(escritas.estado, escritas.motivo)
        is PatasDelAviso.Escritas -> ConfirmacionDelDebito.Hecha(
            movimientos = Events.selectAll()
                .where { (Events.userId eq uid) and (Events.transferId eq transferId) }
                .map { it.toFinancialEvent() },
            nueva = escritas.creadas,
        )
    }
}

/** El gasto de un recurrente y el sello de su período, dentro de la transacción. Ver la ruta. */
private fun Transaction.confirmarElRecurrente(
    uid: String,
    pedido: ConfirmarDebitoAutomatico,
    hoy: LocalDate,
): ConfirmacionDelDebito {
    val rule = RecurringRules.selectAll()
        .where { (RecurringRules.id eq pedido.ruleId) and (RecurringRules.userId eq uid) }
        .firstOrNull()?.toRule()
        ?: return ConfirmacionDelDebito.Rechazada(HttpStatusCode.NotFound, "Ese recurrente no existe.")
    // Vuelta 90: también lo esperado (un recurrente con cuenta sin la marca, un gasto o la nómina). Lo
    // que no se puede confirmar desde acá es lo que tampoco se propone: sin cuenta, un abono a una
    // deuda (su ocurrencia es un traspaso, no un gasto) o una cuenta que no admite ese movimiento.
    if (rule.seDebitaSolo == true) {
        validarDebitoDeLaRegla(true, rule.type, rule.accountId)?.let {
            return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, it)
        }
    } else if (!seEsperaConCuenta(rule)) {
        return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, NO_SE_PUEDE_CONFIRMAR_ASI)
    }
    val cuenta = Accounts.selectAll()
        .where { (Accounts.id eq rule.accountId!!) and (Accounts.userId eq uid) }
        .firstOrNull()?.toAccount()
        ?: return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, DEBITO_DE_REGLA_SIN_CUENTA)
    if (rule.seDebitaSolo != true && !cuentaAdmiteLoEsperado(rule.type, cuenta)) {
        return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, NO_SE_PUEDE_CONFIRMAR_ASI)
    }

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
        return ConfirmacionDelDebito.Hecha(listOf(existente.toFinancialEvent()), nueva = false)
    }

    // El día que se esperaba: el del vencimiento, salvo la nómina que cae el día de corte, que se
    // espera el día que arranca su período (`fechaEsperada`). Con ese día se fecha el movimiento.
    val ajustes = ajustesDelPeriodoSinSuspender(uid)
    val vence = fechaEsperada(rule, occurrenceInMonth(YearMonth.parse(pedido.periodo), rule.dayOfMonth), ajustes)
    if (vence.isAfter(hoy)) return ConfirmacionDelDebito.Rechazada(HttpStatusCode.BadRequest, TODAVIA_NO_VENCE)
    // Revisión de la vuelta 90 (I1): una tarjeta vieja no anota dos veces lo que ya se resolvió por otro lado.
    porQueYaNoSeAnota(uid, rule, pedido.periodo, pedido.eventoId, hoy, ajustes)?.let {
        return ConfirmacionDelDebito.Rechazada(HttpStatusCode.Conflict, it)
    }
    val evento = FinancialEvent(
        id = pedido.eventoId,
        accountId = cuenta.id,
        // El tipo de la regla: un gasto, o la nómina (vuelta 90). Nunca otro.
        type = rule.type,
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
        createdAt = System.currentTimeMillis(),
    )
    // Mismas guardas de texto que un movimiento cualquiera: la categoría y el nombre vienen de la
    // regla, que ya pasó por ellas, pero el insert no puede caer con un 500 por un texto largo.
    rechazoDeLosTextos(evento.category, evento.description)?.let {
        return ConfirmacionDelDebito.Rechazada(HttpStatusCode.UnprocessableEntity, it)
    }
    insertEventRow(uid, evento)
    // Si el sello no se puede poner (el período es viejo, el movimiento ya cierra otro…), la
    // excepción deshace también el gasto: nada a medias.
    sellarOcurrencia(uid, rule, pedido.periodo, pedido.eventoId, hoy).alFallar()
    return ConfirmacionDelDebito.Hecha(listOf(evento), nueva = true)
}

/** Revisión de la vuelta 90 (I1) · el 409 de una tarjeta que ya no aplica. */
internal const val YA_ESTA_ANOTADO: String =
    "Eso ya está anotado (o hay un movimiento que podría serlo): revísalo en Pagos antes de anotarlo otra vez."

/** Revisión de la vuelta 90 (I1) · el 409 de lo que el dueño ya descartó en ese período. */
internal const val YA_SE_DESCARTO: String = "Ya dijiste que eso no pasó en ese período."

/**
 * **¿Por qué ya no se anota esta propuesta?** (revisión de la vuelta 90, I1). La tarjeta de «Por
 * revisar» puede llevar un rato abierta —o estar en otro aparato— mientras el dueño anota el pago a mano
 * o lo marca desde Pagos. Confirmar a ciegas dejaba dos gastos y movía el sello al segundo
 * ([sellarOcurrencia] borra y re-sella). Dentro de la misma transacción del insert, `null` solo si:
 *
 * - el período no está entre lo descartado («No se pagó» / «Ya lo anoté»);
 * - no hay un sello de (regla, período) sin movimiento, o con un movimiento VIVO distinto de [eventoId];
 * - el checklist (la misma lectura de `GET /api/payments/occurrences`) no la da por ocurrida con otro
 *   movimiento ni le propone candidatos.
 */
private fun Transaction.porQueYaNoSeAnota(
    uid: String,
    rule: RecurringRule,
    periodo: String,
    eventoId: String,
    hoy: LocalDate,
    ajustes: PeriodSettings,
): String? {
    val descartado = DebitosDescartados.selectAll()
        .where { (DebitosDescartados.userId eq uid) and (DebitosDescartados.ruleId eq rule.id) and (DebitosDescartados.periodo eq periodo) }
        .any()
    if (descartado) return YA_SE_DESCARTO
    val sello = RecurringOccurrences.selectAll()
        .where { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.ruleId eq rule.id) and (RecurringOccurrences.period eq periodo) }
        .firstOrNull()
    if (sello != null) {
        val otro = sello[RecurringOccurrences.eventId] ?: return YA_ESTA_ANOTADO
        if (otro != eventoId && esUnMovimientoVivo(uid, otro)) return YA_ESTA_ANOTADO
    }
    val estado = estadosDeLasOcurrenciasReales(uid, hoy, ajustes).firstOrNull { it.ruleId == rule.id && it.period == periodo }
    if (estado != null && ((estado.occurred && estado.eventId != eventoId) || estado.candidates.isNotEmpty())) {
        return YA_ESTA_ANOTADO
    }
    return null
}

private fun Transaction.esUnMovimientoVivo(uid: String, eventoId: String): Boolean {
    val existe = Events.selectAll().where { (Events.id eq eventoId) and (Events.userId eq uid) }.any()
    if (!existe) return false
    return VoidEvents.selectAll().where { (VoidEvents.originalEventId eq eventoId) and (VoidEvents.userId eq uid) }.none()
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
 * 5. la cuenta del débito y la del crédito siguen existiendo;
 * 6. **el crédito todavía se debe**: con el saldo de la deuda en cero o menos, no hay cuota que cobrar.
 *
 * El monto propuesto es la cuota pactada. No se estima nada más: el dueño la cambia si el banco
 * cobró otra cifra.
 */
internal suspend fun debitosPorConfirmar(
    uid: String,
    hoy: LocalDate,
    ahora: Long,
    conEsperados: Boolean = false,
): List<DebitoAutomaticoPorConfirmar> =
    (cuotasPorConfirmar(uid, hoy, ahora) + recurrentesPorConfirmar(uid, hoy, ahora, conEsperados)).sortedBy { it.vence }

/** Vuelta 90 · el 422 de confirmar un recurrente que no se propone (sin cuenta, un abono, una cuenta que no va). */
internal const val NO_SE_PUEDE_CONFIRMAR_ASI: String =
    "Ese pago no se confirma desde aquí: anótalo desde «Agregar»."

/**
 * **Vuelta 90 · ¿Movi puede preguntar por esta regla aunque no esté marcada «se debita solo»?** Un
 * gasto o un ingreso **con cuenta**, que no sea el abono mensual a una deuda (`haciaDeuda`: su
 * ocurrencia es un traspaso, y anotarlo como gasto dejaría la deuda sin bajar) ni lleve una categoría
 * reservada o la del desembolso.
 */
internal fun seEsperaConCuenta(rule: RecurringRule): Boolean =
    !rule.accountId.isNullOrBlank() && rule.haciaDeuda.isNullOrBlank() &&
        (rule.type == TransactionType.EXPENSE || rule.type == TransactionType.INCOME) &&
        // Un pago de tarjeta o un asiento interno no es la ocurrencia de un recurrente (el sello lo
        // rechazaría), y el desembolso de un crédito solo lo escriben sus dos patas.
        !isReservedCategory(rule.category) && rule.category != DESEMBOLSO_CATEGORY

/**
 * **Vuelta 90 · ¿la cuenta de la regla admite el movimiento esperado?** Un ingreso entra a una cuenta
 * de dinero (no a una deuda). Un gasto sale de una cuenta de dinero **o de una tarjeta de crédito**
 * (una regla recurrente cargada a la tarjeta: la compra con tarjeta es gasto, la regla de siempre); nunca de un crédito.
 */
internal fun cuentaAdmiteLoEsperado(tipo: TransactionType, cuenta: Account): Boolean = when (tipo) {
    TransactionType.INCOME -> cuenta.type.group != AccountGroup.DEUDA
    TransactionType.EXPENSE -> cuenta.type.group != AccountGroup.DEUDA || cuenta.type == AccountType.CREDIT_CARD
    else -> false
}

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
            // **Un crédito pagado no se cobra.** Si la deuda ya está en cero (o a favor), el banco no
            // tiene qué debitar aunque el crédito siga marcado «El banco la cobra solo»: proponerla
            // invitaría a anotar una cuota que no existe. El saldo es el de la cuenta, en su moneda,
            // con los movimientos vivos — el mismo que muestra Créditos.
            if (saldoDeLaDeuda(uid, deuda) <= 0L) return@mapNotNull null
            if (hayUnAvisoPendienteDelMismoPago(uid, rule.amount, cuenta.currency, TransactionType.EXPENSE, vence, ahora)) return@mapNotNull null
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

/** Lo que se debe hoy en [deuda] (positivo = deuda), en su moneda, sin los anulados. */
private fun Transaction.saldoDeLaDeuda(uid: String, deuda: Account): Long =
    computeBalances(deuda.type, loadNonVoidedEventsIn(uid, deuda.id))[deuda.currency] ?: 0L

/**
 * Los recurrentes comunes de [debitosPorConfirmar]: las reglas marcadas «se debita solo», con cuenta,
 * cuyo vencimiento en juego ya llegó y el checklist da por **abierto y sin candidatos** — la misma
 * respuesta de `GET /api/payments/occurrences` ([estadosDeLasOcurrenciasReales]). Con un candidato el
 * checklist ya pregunta «¿fue este?», y proponer anotar otro sería invitar a un duplicado.
 *
 * **Vuelta 90 · con [conEsperados], también lo esperado**: cualquier recurrente con cuenta
 * ([seEsperaConCuenta]) —un gasto fijo, la nómina; solo `recurring_rules`, no `subscriptions`— con la MISMA derivación (el
 * emparejador de siempre: nombre, categoría + cuenta + monto, sellos; y ningún aviso pendiente que lo
 * cubra), pero solo cuando pasaron [GRACIA_DE_LO_ESPERADO_DIAS] días desde la fecha esperada
 * (`fechaEsperada`, la del checklist: el salario del 25 que cae domingo se espera el viernes 23). Lo
 * marcado como débito no espera: el banco debita ese día.
 */
private suspend fun recurrentesPorConfirmar(
    uid: String,
    hoy: LocalDate,
    ahora: Long,
    conEsperados: Boolean,
): List<DebitoAutomaticoPorConfirmar> {
    val settings = ajustesDePeriodoDe(uid)
    return dbQuery {
        val reglas = reglasRealesDe(uid).filter { rule ->
            val esDebito = rule.seDebitaSolo == true && rule.type == TransactionType.EXPENSE && rule.accountId != null
            esDebito || (conEsperados && rule.seDebitaSolo != true && seEsperaConCuenta(rule))
        }
        if (reglas.isEmpty()) return@dbQuery emptyList()
        val estados = estadosDeLasOcurrenciasReales(uid, hoy, settings).associateBy { it.ruleId }
        val descartados = descartadosDe(uid)
        // Los avisos pendientes, leídos una sola vez y solo si algo esperado los necesita.
        val pendientes = lazy { avisosPendientesLeidos(uid, ahora) }
        val cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() }.associateBy { it.id }
        reglas.mapNotNull { rule ->
            val esperado = rule.seDebitaSolo != true
            val estado = estados[rule.id] ?: return@mapNotNull null
            if (estado.occurred || estado.candidates.isNotEmpty()) return@mapNotNull null
            // `dueDate` ya es la fecha ESPERADA (`fechaEsperada`): la del vencimiento, o la del arranque
            // del período para la nómina del día de corte.
            val vence = runCatching { LocalDate.parse(estado.dueDate) }.getOrNull() ?: return@mapNotNull null
            if (vence.isAfter(hoy)) return@mapNotNull null
            if (esperado && vence.plusDays(GRACIA_DE_LO_ESPERADO_DIAS.toLong()).isAfter(hoy)) return@mapNotNull null
            // Vuelta 72: lo que vence en la pausa de una regla no se propone (la misma regla que la cuota).
            if (pausadaEn(rule, vence)) return@mapNotNull null
            if ("${rule.id}@${estado.period}" in descartados) return@mapNotNull null
            val cuenta = cuentas[rule.accountId] ?: return@mapNotNull null
            if (esperado) {
                if (!cuentaAdmiteLoEsperado(rule.type, cuenta)) return@mapNotNull null
            } else if (cuenta.type.group == AccountGroup.DEUDA) {
                return@mapNotNull null
            }
            val ultimo = ultimoMontoDe(uid, rule).takeIf { it != rule.amount }
            // El aviso que lo cubre puede venir por el monto de la regla o por el de la última vez.
            val montos = listOfNotNull(rule.amount.takeIf { it > 0 }, ultimo).distinct()
            val cubierto = if (esperado) {
                // Revisión de la vuelta 90 (I4): lo esperado se apaga con un aviso pendiente PARECIDO —±10 %
                // del monto, desde que abre la ventana del vencimiento—, no solo con el mismo monto en 48 h:
                // la suscripción que subió o la nómina con otro valor también están cubiertas por su aviso.
                val desde = appDateToEpochMillis(occurrenceWindow(vence, settings = settings).start)
                pendientes.value.any { it.cubre(montos, cuenta.currency, rule.type, desde, ahora) }
            } else {
                montos.any { hayUnAvisoPendienteDelMismoPago(uid, it, cuenta.currency, rule.type, vence, ahora) }
            }
            if (cubierto) return@mapNotNull null
            DebitoAutomaticoPorConfirmar(
                ruleId = rule.id,
                periodo = estado.period,
                origen = OrigenDelDebito.RECURRENTE,
                nombre = rule.name,
                // El de la regla; si la regla no tiene monto (una vieja en 0), el de la última vez, y si
                // tampoco hay, 0: la tarjeta pide escribirlo. Nunca una cifra inventada.
                monto = rule.amount.takeIf { it > 0 } ?: ultimo ?: 0L,
                moneda = cuenta.currency,
                vence = vence.toString(),
                cuentaId = cuenta.id,
                cuentaNombre = cuenta.name,
                categoria = rule.category,
                pataDelDineroId = idDelGastoDelDebito(uid, rule.id, estado.period),
                tipo = rule.type,
                esperado = esperado,
                ultimoMonto = ultimo,
            )
        }
    }
}

/**
 * **Vuelta 90 · el monto con que se anotó la última vez [rule]**: el movimiento del período sellado más
 * reciente (con movimiento y sin anular). `null` sin historia. Solo los sellos guardados: lo que Movi
 * empareja solo se deriva en cada lectura y no deja rastro de períodos viejos.
 */
private fun Transaction.ultimoMontoDe(uid: String, rule: RecurringRule): Long? {
    val sellos = RecurringOccurrences.selectAll()
        .where { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.ruleId eq rule.id) }
        .mapNotNull { fila -> fila[RecurringOccurrences.eventId]?.let { fila[RecurringOccurrences.period] to it } }
        .sortedByDescending { it.first }
    for ((_, eventoId) in sellos) {
        val anulado = VoidEvents.selectAll()
            .where { (VoidEvents.originalEventId eq eventoId) and (VoidEvents.userId eq uid) }
            .any()
        if (anulado) continue
        val fila = Events.selectAll().where { (Events.id eq eventoId) and (Events.userId eq uid) }.firstOrNull() ?: continue
        return fila[Events.amount].takeIf { it > 0 }
    }
    return null
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
    // Vuelta 72: lo que vence en la pausa de una regla no se propone como cobrado.
    if (pausadaEn(rule, vence)) return null
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
    tipo: TransactionType,
    vence: LocalDate,
    ahora: Long,
): Boolean {
    val finDelVencimiento = appDateToEpochMillis(vence.plusDays(1))
    return listOf(ahora, finDelVencimiento).distinct().any { momento ->
        avisosPendientesParecidos(uid, monto, moneda, tipo, ahora = momento).isNotEmpty()
    }
}

/** Un aviso pendiente de la bandeja, ya leído: monto, moneda, tipo y cuándo llegó. */
private data class AvisoPendienteLeido(val monto: Long, val moneda: String, val tipo: TransactionType, val momento: Long) {
    /**
     * ¿Cubre lo esperado? Mismo tipo y moneda, llegado entre [desde] y [ahora] (una hora de holgura por
     * relojes), y un monto a ±[TOLERANCIA_DEL_AVISO_PCT] % de alguno de [montos]. Elegido, no medido.
     */
    fun cubre(montos: List<Long>, moneda: String, tipo: TransactionType, desde: Long, ahora: Long): Boolean =
        this.tipo == tipo && this.moneda == moneda && momento in desde..(ahora + 3_600_000L) &&
            montos.any { m -> m > 0 && kotlin.math.abs(monto - m) * 100 <= m * TOLERANCIA_DEL_AVISO_PCT }
}

/** Revisión de la vuelta 90 (I4) · cuánto puede diferir el aviso de lo esperado y seguir cubriéndolo. */
internal const val TOLERANCIA_DEL_AVISO_PCT: Long = 10

/** Los avisos pendientes y sin anotar de la bandeja (la misma de «Por revisar»), con lo que dice el parser. */
private fun Transaction.avisosPendientesLeidos(uid: String, ahora: Long): List<AvisoPendienteLeido> =
    bandejaConLosPagos(uid, ahora)
        .filter { it.state == SMS_STATE_PENDING && it.yaAnotadoCon == null }
        .mapNotNull { aviso ->
            val momento = momentoDelSms(aviso.time, Long.MAX_VALUE).takeIf { it != Long.MAX_VALUE } ?: return@mapNotNull null
            val leido = parseSms(aviso.text, aviso.bank) ?: return@mapNotNull null
            AvisoPendienteLeido(leido.amount.roundToLong(), leido.currency, leido.type, momento)
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
