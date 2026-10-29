package com.jvillada.movi.server.routes

import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.PartesDelAviso
import com.jvillada.movi.shared.model.pataDelAviso
import io.ktor.server.request.receive
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import com.jvillada.movi.server.plugins.jsonDeLaApi
import com.jvillada.movi.shared.model.MAXIMO_DE_PARTES_DEL_AVISO
import com.jvillada.movi.shared.model.ParteElegida
import com.jvillada.movi.shared.model.RepartoRecordado
import com.jvillada.movi.shared.model.claveDelReparto
import com.jvillada.movi.shared.model.operacion
import com.jvillada.movi.shared.model.parteParaRecordar
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
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
    class Leido(val propuesta: SmsMessage, val parsed: ParsedSms?, val destino: DestinoConocido?, val nombraAAlguien: Boolean, val cuando: Long, val recordado: RepartoRecordado?)
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
        val clave = miembros.firstNotNullOfOrNull { claveDelReparto(it.text, sabe.destinos) }
        Leido(propuesta, parsed, destino, nombra, cuando, repartoRecordado(uid, clave, sabe.destinos))
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
        recordado = leido.recordado,
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
        // Vuelta 63: un abono planeado se salda con su traspaso, no repartiendo un aviso.
        .filter { !it.pagado && !it.esIngreso && !it.montoEsSaldo && !it.esAbono && it.moneda == moneda && it.monto > 0L }
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
    data class Hecho(val eventoDelAviso: String, val ids: List<String>, val moneda: String = "COP") : Repartido
    data class Rechazado(val estado: HttpStatusCode, val motivo: String) : Repartido
}

/**
 * **Escribe el reparto de [aviso]**, dentro de la transacción de [confirmarElMismoPago]: o todas las
 * partes, o ninguna (si una falla a mitad de camino, se deshace lo escrito). Lee el aviso —tiene que
 * ser plata que salió— y escribe con [escribirLasPartes], la misma que reparte un movimiento ya
 * anotado ([repartirUnMovimiento]).
 */
internal fun Transaction.escribirElReparto(
    uid: String,
    aviso: SmsMessage,
    partes: List<ParteDelReparto>,
    recordarDestinoId: String?,
    ahora: Long,
): Repartido {
    val leido = (if (esUnComprobante(aviso)) parsedDeUnComprobante(uid, aviso.id, aviso.text) else null)
        ?: parseSms(aviso.text, aviso.bank)
        ?: return Repartido.Rechazado(HttpStatusCode.UnprocessableEntity, NO_TRAE_UN_MOVIMIENTO)
    if (leido.type != TransactionType.EXPENSE) return Repartido.Rechazado(HttpStatusCode.UnprocessableEntity, "Solo se reparte la plata que salió.")
    val origen = if (esIdDeComprobante(aviso.id)) EventSource.OCR else EventSource.SMS
    return escribirLasPartes(uid, leido.amount.roundToLong(), leido.currency, aviso.text, origen, partes, recordarDestinoId, ahora)
}

/**
 * **Escribe las partes de una salida de [total] en [moneda]**, dentro de la transacción del llamador:
 * o todas, o ninguna.
 *
 * - **Suman exactamente [total]**, cada una mayor que cero ([EL_REPARTO_NO_SUMA]), y **salen todas de la
 *   misma cuenta**, que tiene que ser del dueño y estar en [moneda]: la cuenta de origen recibe N
 *   salidas que suman el aviso.
 * - **Cada parte es válida igual que una sola.** Con dos patas ([ParteDelReparto.patas]: una cuota, el
 *   pago de una tarjeta, un traspaso) la escribe [escribirLasPatasDelAviso] —la misma de un aviso
 *   suelto, con `validarDosPatas` y el desglose de la deuda—; un gasto ([ParteDelReparto.evento]) pasa
 *   por [movimientoDelAviso], las reglas de `POST /api/events`. Un avance no se reparte.
 * - **Un pago del período por parte, a lo sumo una vez** ([ParteDelReparto.ruleId]): la cuota de un
 *   crédito que él paga (lo que paga otro no sale de esta plata) o un recurrente común, que queda
 *   sellado en su período ([sellarOcurrencia]; si su vencimiento todavía no llegó, el sello solo se
 *   pone si cae en el período en curso, el techo de [sellarOcurrencia]). Nada de un pago de tarjeta
 *   del checklist. Una parte **suelta** ([ParteDelReparto.ruleId] vacío) no sella nada: una cuota igual
 *   marca su período por la pata de la deuda (`pagoDeCuotaLegs`, lo que lee `PagosDeDeuda.kt`).
 * - [recordarDestinoId]: los pagos elegidos quedan asociados a ese tercero (si es del dueño y el pago no
 *   tenía uno), para que la próxima vez la suma exacta se proponga sola.
 *
 * Devuelve el movimiento con que queda el aviso —la pata del dinero de la primera parte— y todos los
 * que se crearon, en orden.
 */
internal fun Transaction.escribirLasPartes(
    uid: String,
    total: Long,
    moneda: String,
    textoDelAviso: String?,
    origen: EventSource,
    partes: List<ParteDelReparto>,
    recordarDestinoId: String?,
    ahora: Long,
): Repartido {
    fun no(motivo: String, estado: HttpStatusCode = HttpStatusCode.UnprocessableEntity) = Repartido.Rechazado(estado, motivo)
    if (partes.isEmpty() || partes.size > MAXIMO_DE_PARTES_DEL_AVISO) return no("Un reparto lleva entre 1 y $MAXIMO_DE_PARTES_DEL_AVISO partes.")
    val conPago = partes.map { it.ruleId }.filter { it.isNotBlank() }
    if (conPago.distinct().size != conPago.size) return no("Un pago no puede ir dos veces en el mismo reparto.")
    if (partes.any { it.monto <= 0L } || partes.sumOf { it.monto } != total) return no(EL_REPARTO_NO_SUMA)

    // Todas salen de la misma cuenta, del dueño y en la moneda del aviso.
    val origenes = partes.map { it.patas?.origenId ?: it.evento?.accountId }
    if (origenes.any { it == null }) return no("Cada parte lleva su movimiento, o las dos patas.", HttpStatusCode.BadRequest)
    if (origenes.distinct().size != 1) return no("Todas las partes salen de la misma cuenta.")
    val cuentaDeOrigen = Accounts.selectAll()
        .where { (Accounts.userId eq uid) and (Accounts.id eq origenes.first()!!) }
        .firstOrNull() ?: return no("Cuenta no encontrada", HttpStatusCode.NotFound)
    if (cuentaDeOrigen[Accounts.currency] != moneda) return no("Las partes salen en $moneda, la moneda del aviso.")

    // Primero se valida todo lo que no depende de escribir.
    for (parte in partes) {
        val patas = parte.patas
        val evento = parte.evento
        if ((evento == null) == (patas == null)) {
            return no("Cada parte lleva su movimiento, o las dos patas si es una cuota, un pago de tarjeta o un traspaso.", HttpStatusCode.BadRequest)
        }
        // Lo que dice la app que es, y lo que trae, tienen que ser lo mismo.
        val tipo = parte.tipo
        if (tipo != null && (if (patas != null) tipo.operacion != patas.operacion else tipo.operacion != null)) {
            return no("Esa parte no es lo que dice ser. Revisa qué fue.")
        }
        if (parte.ruleId.startsWith(CARD_RULE_PREFIX)) return no("El pago de una tarjeta no se reparte como pago del período: elígelo como «Pago de tarjeta».")
        when {
            parte.ruleId.startsWith(CREDIT_RULE_PREFIX) -> {
                if (patas == null || patas.operacion != OperacionDelAviso.CUOTA ||
                    "$CREDIT_RULE_PREFIX${patas.destinoId}" != parte.ruleId || patas.monto != parte.monto
                ) return no("La cuota de un crédito se reparte con sus dos patas, hacia ese crédito y por su parte.")
                val terms = Credits.selectAll()
                    .where { (Credits.accountId eq patas.destinoId) and (Credits.userId eq uid) }
                    .firstOrNull()?.toCreditTerms() ?: return no("Ese crédito no existe.", HttpStatusCode.NotFound)
                // La regla de plata de «¿Quién paga esta cuota?»: lo que paga otro no sale de este envío.
                if (!entraAlBarridoDeAvisos(terms)) return no("Esa cuota la paga otro o se descuenta de la nómina: no sale de tu cuenta.")
            }
            parte.ruleId.isNotBlank() -> {
                if (evento == null) return no("Solo la cuota de un crédito lleva dos patas.")
                if (!PERIODO_DEL_REPARTO.matches(parte.periodo)) return no("Periodo inválido: usa \"YYYY-MM\".", HttpStatusCode.BadRequest)
                if (evento.type != TransactionType.EXPENSE || evento.amount != parte.monto) {
                    return no("El movimiento de cada pago tiene que ser un gasto por su parte.")
                }
                val existe = RecurringRules.selectAll()
                    .where { (RecurringRules.id eq parte.ruleId) and (RecurringRules.userId eq uid) }
                    .count() > 0
                if (!existe) return no("Ese pago no existe.", HttpStatusCode.NotFound)
            }
            // Una parte suelta: lo que el dueño eligió en «Repartir en partes».
            patas != null -> {
                if (patas.operacion == OperacionDelAviso.AVANCE) return no("Un avance no se reparte: es plata que entra.")
                if (patas.monto != parte.monto) return no("Las dos patas de cada parte van por su monto.")
            }
            else -> {
                if (evento!!.type != TransactionType.EXPENSE || evento.amount != parte.monto) {
                    return no("El movimiento de cada parte tiene que ser un gasto por su monto.")
                }
                if (evento.currency != moneda) return no("Las partes salen en $moneda, la moneda del aviso.")
            }
        }
    }

    val transaccion = this
    fun deshacer(rechazo: Repartido.Rechazado): Repartido.Rechazado = rechazo.also { transaccion.rollback() }
    val hoy = AppClock.today()
    val ids = mutableListOf<String>()
    var delAviso: String? = null
    for (parte in partes) {
        val patas = parte.patas
        if (patas != null) {
            when (val escritas = escribirLasPatasDelAviso(uid, patas, origen, textoDelAviso, ahora)) {
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
        if (parte.ruleId.isBlank()) continue
        val regla = RecurringRules.selectAll()
            .where { (RecurringRules.id eq parte.ruleId) and (RecurringRules.userId eq uid) }
            .first().toRule()
        // Si su vencimiento todavía no llegó y no es del período en curso, el sello no se puede poner
        // (el techo de `sellarOcurrencia`): el pago se reconoce por el nombre cuando venza.
        sellarOcurrencia(uid, regla, parte.periodo, evento.id, hoy)
    }

    recordarElTercero(uid, partes.filter { it.ruleId.isNotBlank() }, recordarDestinoId)
    return Repartido.Hecho(delAviso!!, ids, moneda)
}

/**
 * **Enlaza los avisos [smsIds] con todos los movimientos del reparto** (`sms_messages.partes_eventos`),
 * en orden. `sms_messages.evento_id` lo pone [confirmarElMismoPago], como siempre, con el primero.
 */
internal fun Transaction.enlazarLasPartes(uid: String, smsIds: List<String>, eventoIds: List<String>) {
    if (smsIds.isEmpty()) return
    val json = jsonDeLaApi.encodeToString(ListSerializer(String.serializer()), eventoIds)
    SmsMessages.update({ (SmsMessages.userId eq uid) and (SmsMessages.id inList smsIds) }) {
        it[SmsMessages.partesEventos] = json
    }
}

/** Todos los movimientos con que quedó repartido el aviso de [fila], en orden; vacía si no se repartió. */
internal fun partesDeLaFila(fila: ResultRow): List<String> =
    fila[SmsMessages.partesEventos]?.let { json ->
        runCatching { jsonDeLaApi.decodeFromString(ListSerializer(String.serializer()), json) }.getOrNull()
    }.orEmpty()

/** Todos los movimientos con que quedó repartido el aviso [smsId], en orden; vacía si no se repartió. */
internal fun Transaction.partesEnlazadas(uid: String, smsId: String): List<String> =
    SmsMessages.selectAll()
        .where { (SmsMessages.userId eq uid) and (SmsMessages.id eq smsId) }
        .firstOrNull()?.let(::partesDeLaFila).orEmpty()

/**
 * **Recuerda el reparto** para proponerlo la próxima vez: lo guarda en los mismos avisos
 * (`sms_messages.reparto`). Solo con dos partes o más. «La última vez» es el último aviso repartido a
 * la misma persona ([repartoRecordado]): la memoria es el aviso, sin tabla aparte.
 */
internal fun Transaction.recordarElReparto(uid: String, smsIds: List<String>, partes: List<ParteDelReparto>, moneda: String) {
    if (partes.size < 2 || smsIds.isEmpty()) return
    val elegidas = partes.map { parteParaRecordar(it) ?: return }
    val origenId = partes.first().let { it.patas?.origenId ?: it.evento?.accountId } ?: return
    val recordado = RepartoRecordado(origenId = origenId, monto = partes.sumOf { it.monto }, moneda = moneda, partes = elegidas)
    val json = jsonDeLaApi.encodeToString(RepartoRecordado.serializer(), recordado)
    SmsMessages.update({ (SmsMessages.userId eq uid) and (SmsMessages.id inList smsIds) }) {
        it[SmsMessages.reparto] = json
    }
}

/**
 * **El reparto de la última vez a [clave]** ([claveDelReparto]): el del aviso repartido más reciente
 * que va a la misma persona. `null` si nunca se repartió uno así, o si no se puede leer.
 */
internal fun Transaction.repartoRecordado(uid: String, clave: String?, destinos: List<DestinoConocido>): RepartoRecordado? {
    if (clave == null) return null
    return SmsMessages.selectAll()
        .where { (SmsMessages.userId eq uid) and SmsMessages.reparto.isNotNull() }
        .sortedByDescending { it[SmsMessages.confirmadoEn] ?: 0L }
        .firstOrNull { claveDelReparto(it[SmsMessages.text], destinos) == clave }
        ?.let { fila -> runCatching { jsonDeLaApi.decodeFromString(RepartoRecordado.serializer(), fila[SmsMessages.reparto]!!) }.getOrNull() }
}

/**
 * **Reparte en partes un movimiento ya anotado** (vuelta 27, punto 3): el giro que se confirmó antes
 * como un solo gasto o un solo traspaso. En una transacción:
 *
 * 1. Se bloquea el movimiento (y su hermana si es una pata) y se anula con el mecanismo de siempre
 *    (`void_events`, las dos patas juntas, como `POST /api/events/{id}/void`). Los sellos de recurrentes
 *    que apuntaban a él se sueltan: las partes sellan lo suyo.
 * 2. Se escriben las partes con [escribirLasPartes] —la misma del aviso— por el monto de la salida,
 *    desde su cuenta, con su texto del banco y su origen. Si algo no pasa, se deshace todo (también la
 *    anulación).
 * 3. El aviso que estaba enlazado al original queda enlazado a la primera parte, y todas en
 *    `partes_de_avisos`.
 *
 * **Idempotente**: si el original ya está anulado y la primera parte ya existe, contesta con las partes
 * sin escribir nada.
 */
internal fun Transaction.repartirUnMovimiento(uid: String, eventoId: String, partes: List<ParteDelReparto>, ahora: Long): Repartido {
    fun no(motivo: String, estado: HttpStatusCode = HttpStatusCode.UnprocessableEntity) = Repartido.Rechazado(estado, motivo)
    val original = Events.selectAll()
        .where { (Events.id eq eventoId) and (Events.userId eq uid) }
        .forUpdate()
        .firstOrNull()?.toFinancialEvent() ?: return no("Movimiento no encontrado", HttpStatusCode.NotFound)
    val hermanas = original.transferId?.let { tr ->
        Events.selectAll().where { (Events.userId eq uid) and (Events.transferId eq tr) }.forUpdate().map { it.toFinancialEvent() }
    } ?: listOf(original)
    val ids = hermanas.map { it.id }
    val anulados = VoidEvents.selectAll()
        .where { (VoidEvents.userId eq uid) and (VoidEvents.originalEventId inList ids) }
        .map { it[VoidEvents.originalEventId] }.toSet()
    val idsDeLasPartes = partes.flatMap { listOfNotNull(it.patas?.origenEventId, it.patas?.destinoEventId, it.evento?.id) }
    if (anulados.isNotEmpty()) {
        val yaEstan = idsDeLasPartes.isNotEmpty() &&
            Events.selectAll().where { (Events.userId eq uid) and (Events.id inList idsDeLasPartes) }.count() == idsDeLasPartes.size.toLong()
        if (yaEstan) {
            val primera = partes.first().let { it.patas?.pataDelAviso ?: it.evento?.id }!!
            return Repartido.Hecho(primera, idsDeLasPartes, original.currency)
        }
        return no("Ese movimiento está anulado.", HttpStatusCode.Conflict)
    }
    val salida = hermanas.singleOrNull { it.type == TransactionType.EXPENSE }
        ?.takeIf { hermanas.size <= 2 }
        ?: return no("Solo se reparte la plata que salió.")
    val origenes = partes.map { it.patas?.origenId ?: it.evento?.accountId }.distinct()
    if (origenes != listOf(salida.accountId)) return no("Las partes salen de la cuenta del movimiento.")

    // 1. Anular, como siempre (antes de escribir: el desglose de una cuota no puede ver el original).
    hermanas.forEach { pata ->
        VoidEvents.insert {
            it[VoidEvents.id] = "void_${java.util.UUID.randomUUID()}"
            it[VoidEvents.userId] = uid
            it[VoidEvents.originalEventId] = pata.id
            it[VoidEvents.reason] = MOTIVO_DE_LA_ANULACION_AL_REPARTIR
            it[VoidEvents.timestamp] = ahora
        }
    }
    RecurringOccurrences.deleteWhere { (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.eventId inList ids) }

    // 2. Las partes, con la misma función del aviso.
    val hecho = when (val escritas = escribirLasPartes(uid, salida.amount, salida.currency, salida.rawPayload, salida.source, partes, null, ahora)) {
        is Repartido.Hecho -> escritas
        is Repartido.Rechazado -> { rollback(); return escritas }
    }

    // Vuelta 67: cada pata de cada parte sabe de qué compra es, para decirlo y para deshacerlo. Las
    // partes de una división anterior que se deshizo se desligan antes: ya no son de esta compra.
    Events.update({ (Events.userId eq uid) and (Events.repartoDe eq salida.id) and (Events.id notInList hecho.ids) }) { it[Events.repartoDe] = null }
    Events.update({ (Events.userId eq uid) and (Events.id inList hecho.ids) }) { it[Events.repartoDe] = salida.id }

    // 3. Los avisos que apuntaban al original, a la primera parte.
    val avisos = SmsMessages.selectAll()
        .where { (SmsMessages.userId eq uid) and (SmsMessages.eventoId inList ids) }
        .map { it[SmsMessages.id] }
    if (avisos.isNotEmpty()) {
        SmsMessages.update({ (SmsMessages.userId eq uid) and (SmsMessages.id inList avisos) }) {
            it[SmsMessages.eventoId] = hecho.eventoDelAviso
        }
        enlazarLasPartes(uid, avisos, hecho.ids)
        recordarElReparto(uid, avisos, partes, hecho.moneda)
    }
    return hecho
}

/** El motivo que queda en `void_events` al repartir un movimiento ya anotado. */
const val MOTIVO_DE_LA_ANULACION_AL_REPARTIR: String = com.jvillada.movi.shared.model.MOTIVO_DEL_REPARTIDO_EN_PARTES

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
    /**
     * `POST /api/events/{id}/repartir` con [PartesDelAviso]: reparte un movimiento ya anotado (ver
     * [repartirUnMovimiento]). 200 con [MismoPagoConfirmado] (`eventoId` = la primera parte, `patas` =
     * todas); 404 si no es del usuario; 409 si ya estaba anulado; 422 si las partes no cuadran.
     */
    post("/api/events/{id}/repartir") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val pedido = runCatching { call.receive<PartesDelAviso>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val partes = pedido.partes.map { parte -> parte.copy(patas = conLaTasaDelDia(uid, parte.patas)) }
        when (val hecho = dbQuery { repartirUnMovimiento(uid, id, partes, System.currentTimeMillis()) }) {
            is Repartido.Hecho -> call.respond(MismoPagoConfirmado(eventoId = hecho.eventoDelAviso, creado = true, patas = hecho.ids))
            is Repartido.Rechazado -> call.respond(hecho.estado, hecho.motivo)
        }
    }

    get("/api/sms/{id}/reparto") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val propuesta = propuestaDeRepartoDe(uid, id, ahora = System.currentTimeMillis())
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(propuesta)
    }
}
