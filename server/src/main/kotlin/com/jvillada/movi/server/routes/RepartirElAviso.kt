package com.jvillada.movi.server.routes

import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.entraAlBarridoDeAvisos
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.EL_REPARTO_NO_SUMA
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.MAXIMO_DE_PARTES
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.PagoParaRepartir
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.ParteDelReparto
import com.jvillada.movi.shared.model.PropuestaDeReparto
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.destinoQueNombra
import com.jvillada.movi.shared.model.esIdDeComprobante
import com.jvillada.movi.shared.model.esUnComprobante
import com.jvillada.movi.shared.model.identificadorDelDestinoEn
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.repartoExacto
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import kotlin.math.roundToLong

/*
 * # Repartir un envío entre varios pagos fijos, del lado del server
 *
 * Ver `RepartirUnEnvio.kt` en `:core` para el porqué. Acá viven las dos mitades:
 *
 * - **La propuesta** (`GET /api/sms/{id}/reparto`): los pagos del período que el envío puede saldar
 *   —la MISMA lista «Pagos del período» de Plan ([checklistDelPeriodo] sobre `/upcoming` y
 *   `/occurrences`), en el período del día del aviso—, cuáles van al tercero del aviso, y la suma
 *   exacta si hay una sola.
 * - **La escritura** ([escribirElReparto]): la llama [confirmarElMismoPago] —la misma función,
 *   transacción, bloqueo e idempotencia con que se confirma cualquier aviso— cuando el pedido trae
 *   `reparto`. Un movimiento por parte: la cuota de un crédito con sus dos patas
 *   ([escribirLasPatasDelAviso]); un recurrente común con su gasto y el sello de su período.
 */

/** Mediodía: un instante cualquiera del día que no cae en el borde de una ventana. */
private const val MEDIO_DIA_DEL_REPARTO = 12L * 60 * 60 * 1000

/**
 * **La propuesta de reparto del aviso [smsId]**, o `null` si el aviso no es del usuario. Sin pagos
 * cuando el aviso no es una salida hacia alguien (una compra en un comercio no se reparte): ninguno
 * de sus avisos nombra una cuenta, una llave ni un tercero guardado.
 *
 * De un pago avisado varias veces (un SMS y el correo de PSE del mismo giro) el monto sale del aviso
 * que más dice ([propuestaDelGrupo]) y el tercero, del primer aviso que lo nombre: el correo de PSE
 * no dice la cuenta, el SMS sí.
 */
internal suspend fun propuestaDeRepartoDe(uid: String, smsId: String, ahora: Long): PropuestaDeReparto? {
    class Leido(val propuesta: SmsMessage, val parsed: ParsedSms?, val destino: DestinoConocido?, val nombraAAlguien: Boolean, val cuando: Long)
    val leido = dbQuery {
        val fila = SmsMessages.selectAll()
            .where { (SmsMessages.id eq smsId) and (SmsMessages.userId eq uid) }
            .firstOrNull()?.toSmsMessage() ?: return@dbQuery null
        val sabe = loQueMoviSabeDelDueno(uid, ahora)
        val marcado = sabe.bandeja.firstOrNull { it.id == fila.id } ?: fila
        val miembros = marcado.miembrosDelGrupo.takeIf { it.size > 1 }
            ?.let { ids -> sabe.bandeja.filter { it.id in ids } }?.ifEmpty { null } ?: listOf(marcado)
        val propuesta = if (miembros.size > 1) propuestaDelGrupo(miembros) else marcado
        val parsed = (leerElAviso(uid, propuesta, sabe) as? LecturaDelAviso.Leido)?.parsed
        val destino = miembros.firstNotNullOfOrNull { destinoQueNombra(it.text, sabe.destinos) }
        val nombra = destino != null || miembros.any { identificadorDelDestinoEn(it.text) != null }
        val cuando = miembros.minOf { momentoDelSms(it.time, ahora) }
        Leido(propuesta, parsed, destino, nombra, cuando)
    } ?: return null
    val parsed = leido.parsed
    val base = PropuestaDeReparto(avisoId = leido.propuesta.id, destinoId = leido.destino?.id, destinoNombre = leido.destino?.nombre)
    if (parsed == null || parsed.type != TransactionType.EXPENSE || !leido.nombraAAlguien) return base
    val monto = parsed.amount.roundToLong()
    val pagos = pagosParaRepartir(uid, epochMillisToAppDate(leido.cuando), leido.destino?.id, parsed.currency)
    return base.copy(
        monto = monto,
        moneda = parsed.currency,
        pagos = pagos,
        exacto = repartoExacto(monto, pagos)?.map { it.ruleId }.orEmpty(),
    )
}

/**
 * **Los pagos del período del día [dia] que un envío puede saldar**: los gastos pendientes de la
 * lista «Pagos del período», en la moneda del envío, sin el pago de una tarjeta (su monto es un saldo,
 * no una cuota). Primero los que el dueño asoció al tercero [destinoId] (`destinoConocidoId` de la
 * regla, o del crédito en la regla sintética de su cuota), después por vencimiento.
 *
 * Las cuotas de un crédito que paga otro o que se descuentan de la nómina no están: no entran al
 * barrido de avisos (`entraAlBarridoDeAvisos`), así que no son pagos del dueño en ese período. Es la
 * regla de plata de «¿Quién paga esta cuota?»: el envío del dueño no puede saldar lo que paga otro.
 */
internal suspend fun pagosParaRepartir(uid: String, dia: LocalDate, destinoId: String?, moneda: String): List<PagoParaRepartir> {
    val ajustes = ajustesDePeriodoDe(uid)
    val upcoming = proximosPagos(uid, dia)
    val periodo = periodoDe(appDateToEpochMillis(dia) + MEDIO_DIA_DEL_REPARTO, ajustes)
    val filas = checklistDelPeriodo(upcoming, ocurrenciasDelPeriodo(uid, dia), periodo, ajustes)
    val reglas = upcoming.associate { it.rule.id to it.rule }
    return filas
        .filter { !it.pagado && !it.esIngreso && !it.montoEsSaldo && it.moneda == moneda && it.monto > 0L }
        .filterNot { it.ruleId.startsWith(CARD_RULE_PREFIX) }
        .map { fila ->
            val esCuota = fila.ruleId.startsWith(CREDIT_RULE_PREFIX)
            PagoParaRepartir(
                ruleId = fila.ruleId,
                nombre = fila.nombre,
                monto = fila.monto,
                categoria = if (esCuota) CUOTA_CATEGORY else fila.categoria,
                periodo = fila.vence.take(7),
                vence = fila.vence,
                cuentaDeLaDeudaId = if (esCuota) fila.ruleId.removePrefix(CREDIT_RULE_PREFIX) else null,
                asociado = destinoId != null && reglas[fila.ruleId]?.destinoConocidoId == destinoId,
            )
        }
        .sortedWith(compareByDescending<PagoParaRepartir> { it.asociado }.thenBy { it.vence }.thenBy { it.nombre })
}

/** Cómo terminó el reparto: con qué movimiento queda el aviso y todos los que se crearon, o por qué no. */
internal sealed interface Repartido {
    data class Hecho(val eventoDelAviso: String, val ids: List<String>) : Repartido
    data class Rechazado(val estado: HttpStatusCode, val motivo: String) : Repartido
}

/**
 * **Escribe el reparto de [aviso]**, dentro de la transacción de [confirmarElMismoPago]: o todas las
 * partes, o ninguna (si una falla a mitad de camino, se deshace lo escrito).
 *
 * - Las partes suman **exactamente** lo que dice el aviso, cada pago va una sola vez y cada parte
 *   lleva su movimiento: [ParteDelReparto.patas] para la cuota de un crédito suyo que él paga,
 *   [ParteDelReparto.evento] para un recurrente común. Nada de un pago de tarjeta.
 * - Un recurrente queda sellado en su período con su movimiento ([sellarOcurrencia]). Si su
 *   vencimiento todavía no llegó, el sello solo se pone si cae en el período en curso (el techo de
 *   [sellarOcurrencia]: pagar antes es lo normal); si no, el pago se reconoce por el nombre cuando
 *   venza: el movimiento lleva el nombre del pago.
 * - [recordarDestinoId]: los pagos elegidos quedan asociados a ese tercero (si es del dueño y el pago
 *   no tenía uno), para que la próxima vez la suma exacta se proponga sola.
 *
 * Devuelve el movimiento con que queda el aviso: el de la primera parte (su pata del dinero).
 */
internal fun Transaction.escribirElReparto(
    uid: String,
    aviso: SmsMessage,
    partes: List<ParteDelReparto>,
    recordarDestinoId: String?,
    ahora: Long,
): Repartido {
    fun no(motivo: String, estado: HttpStatusCode = HttpStatusCode.UnprocessableEntity) = Repartido.Rechazado(estado, motivo)
    if (partes.isEmpty() || partes.size > MAXIMO_DE_PARTES) return no("Un reparto lleva entre 1 y $MAXIMO_DE_PARTES pagos.")
    if (partes.map { it.ruleId }.distinct().size != partes.size) return no("Un pago no puede ir dos veces en el mismo reparto.")
    val leido = (if (esUnComprobante(aviso)) parsedDeUnComprobante(uid, aviso.id, aviso.text) else null)
        ?: parseSms(aviso.text, aviso.bank)
        ?: return no(NO_TRAE_UN_MOVIMIENTO)
    if (leido.type != TransactionType.EXPENSE) return no("Solo se reparte la plata que salió.")
    if (partes.any { it.monto <= 0L } || partes.sumOf { it.monto } != leido.amount.roundToLong()) return no(EL_REPARTO_NO_SUMA)

    // Primero se valida todo lo que no depende de escribir.
    for (parte in partes) {
        if ((parte.evento == null) == (parte.patas == null)) {
            return no("Cada pago lleva su movimiento, o las dos patas si es una cuota.", HttpStatusCode.BadRequest)
        }
        if (parte.ruleId.startsWith(CARD_RULE_PREFIX)) return no("El pago de una tarjeta no se reparte: se anota con Reconciliar.")
        val patas = parte.patas
        if (parte.ruleId.startsWith(CREDIT_RULE_PREFIX)) {
            if (patas == null || patas.operacion != OperacionDelAviso.CUOTA ||
                "$CREDIT_RULE_PREFIX${patas.destinoId}" != parte.ruleId || patas.monto != parte.monto
            ) return no("La cuota de un crédito se reparte con sus dos patas, hacia ese crédito y por su parte.")
            val terms = Credits.selectAll()
                .where { (Credits.accountId eq patas.destinoId) and (Credits.userId eq uid) }
                .firstOrNull()?.toCreditTerms() ?: return no("Ese crédito no existe.", HttpStatusCode.NotFound)
            // La regla de plata de «¿Quién paga esta cuota?»: lo que paga otro no sale de este envío.
            if (!entraAlBarridoDeAvisos(terms)) return no("Esa cuota la paga otro o se descuenta de la nómina: no sale de tu cuenta.")
        } else {
            val evento = parte.evento ?: return no("Cada pago lleva su movimiento.")
            if (patas != null) return no("Solo la cuota de un crédito lleva dos patas.")
            if (!PERIODO_DEL_REPARTO.matches(parte.periodo)) return no("Periodo inválido: usa \"YYYY-MM\".", HttpStatusCode.BadRequest)
            if (evento.type != TransactionType.EXPENSE || evento.amount != parte.monto) {
                return no("El movimiento de cada pago tiene que ser un gasto por su parte.")
            }
            val existe = RecurringRules.selectAll()
                .where { (RecurringRules.id eq parte.ruleId) and (RecurringRules.userId eq uid) }
                .count() > 0
            if (!existe) return no("Ese pago no existe.", HttpStatusCode.NotFound)
        }
    }

    val transaccion = this
    fun deshacer(rechazo: Repartido.Rechazado): Repartido.Rechazado = rechazo.also { transaccion.rollback() }
    val origen = if (esIdDeComprobante(aviso.id)) EventSource.OCR else EventSource.SMS
    val hoy = AppClock.today()
    val ids = mutableListOf<String>()
    var delAviso: String? = null
    for (parte in partes) {
        val patas = parte.patas
        if (patas != null) {
            when (val escritas = escribirLasPatasDelAviso(uid, patas, origen, aviso.text, ahora)) {
                is PatasDelAviso.Escritas -> {
                    ids += escritas.ids
                    if (delAviso == null) delAviso = escritas.pataDelAviso
                }
                is PatasDelAviso.Rechazadas -> return deshacer(Repartido.Rechazado(escritas.estado, escritas.motivo))
            }
            continue
        }
        val evento = when (val validado = movimientoDelAviso(uid, parte.evento!!, ahora)) {
            is Validado.Bien -> validado.evento.copy(source = origen)
            is Validado.Mal -> return deshacer(Repartido.Rechazado(validado.estado, validado.motivo))
        }
        if (Events.selectAll().where { Events.id eq evento.id }.count() > 0) {
            return deshacer(Repartido.Rechazado(HttpStatusCode.Conflict, "Ese id ya existe"))
        }
        insertarMovimiento(uid, evento)
        ids += evento.id
        if (delAviso == null) delAviso = evento.id
        val regla = RecurringRules.selectAll()
            .where { (RecurringRules.id eq parte.ruleId) and (RecurringRules.userId eq uid) }
            .first().toRule()
        // Si su vencimiento todavía no llegó y no es del período en curso, el sello no se puede poner
        // (el techo de `sellarOcurrencia`): el pago se reconoce por el nombre cuando venza.
        sellarOcurrencia(uid, regla, parte.periodo, evento.id, hoy)
    }

    recordarElTercero(uid, partes, recordarDestinoId)
    return Repartido.Hecho(delAviso!!, ids)
}

/** «YYYY-MM». */
private val PERIODO_DEL_REPARTO = Regex("""\d{4}-\d{2}""")

/**
 * Deja asociados a [destinoId] los pagos del reparto que no tenían tercero: las reglas comunes por
 * `recurring_rules.destino_conocido_id`, las cuotas por `credit_terms.destino_conocido_id`. Lo que ya
 * tenía uno se respeta: lo eligió el dueño.
 */
private fun Transaction.recordarElTercero(uid: String, partes: List<ParteDelReparto>, destinoId: String?) {
    val suyo = destinoConocidoIdIfOwned(uid, destinoId) ?: return
    partes.forEach { parte ->
        if (parte.ruleId.startsWith(CREDIT_RULE_PREFIX)) {
            val cuenta = parte.ruleId.removePrefix(CREDIT_RULE_PREFIX)
            Credits.update({
                (Credits.accountId eq cuenta) and (Credits.userId eq uid) and (Credits.destinoConocidoId.isNull())
            }) { it[Credits.destinoConocidoId] = suyo }
        } else {
            RecurringRules.update({
                (RecurringRules.id eq parte.ruleId) and (RecurringRules.userId eq uid) and
                    (RecurringRules.destinoConocidoId.isNull())
            }) { it[RecurringRules.destinoConocidoId] = suyo }
        }
    }
}

/**
 * `GET /api/sms/{id}/reparto`: la propuesta de repartir el aviso entre pagos del período. 404 si el
 * aviso no es del usuario.
 */
fun Route.repartoRoutes() {
    get("/api/sms/{id}/reparto") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val propuesta = propuestaDeRepartoDe(uid, id, ahora = System.currentTimeMillis())
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(propuesta)
    }
}
