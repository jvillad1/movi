package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.withCashFlowFlag
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.reminders.ocurrenciaPorPreguntar
import com.jvillada.movi.server.reminders.periodOf
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.AnotarElPagoDeLaOcurrencia
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CATEGORY_RESERVED_NOT_MANUAL
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY_NOT_MANUAL
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PagoDeLaOcurrenciaAnotado
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_LEG_NOT_STANDALONE
import com.jvillada.movi.shared.model.esCategoriaDelDesembolso
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.rechazoDeLosTextos
import com.jvillada.movi.shared.model.rechazoDelMonto
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

/**
 * # «Anotar pago» salda la factura — el movimiento y su ocurrencia, en una transacción (vuelta 43)
 *
 * El día del dueño (28-oct): «Pago vencido · hace 23 días» → «Anotar pago» → Guardar. Quedó un gasto
 * suelto: la fila siguió en «Movi no encontró el movimiento de este pago», «Te quedan» bajó $1.800.000
 * **otra vez** (el fijo pendiente seguía restado y el pago entró además como gasto variable) y «Movi
 * propone» sacó «Gasto nuevo en Otros».
 *
 * **Por qué fallaba.** La hoja de Agregar nunca supo de qué ocurrencia era el pago: el diseño confiaba
 * en que el emparejamiento automático lo reconociera en la lectura siguiente. Y no podía: el
 * emparejador busca candidatos hasta 10 días después del vencimiento (`OCCURRENCE_WINDOW_DAYS`), y un
 * pago anotado hoy por un vencimiento de hace 23 días queda afuera —ni siquiera salía como «¿Es
 * este?»—. Dentro de la ventana tampoco era seguro: si el dueño cambiaba la nota, quedaban la
 * categoría y el monto, y la tercera circunstancia (la cuenta) no puede pegar en una regla sin cuenta.
 *
 * **El arreglo no afloja el emparejador** (el movimiento sigue siendo la evidencia, y aflojarlo es
 * tildar con menos evidencia): la hoja SABE de qué pago es, así que lo dice. Esto escribe el
 * movimiento y lo sella como la ocurrencia —el mismo [sellarOcurrencia] de «Sí, fue este», con todas
 * sus guardas— en una sola transacción. Si el sello no se puede poner, tampoco queda el movimiento.
 *
 * Reglas del pedido:
 * - **Solo reglas reales.** La cuota de un crédito y el pago de una tarjeta no se sellan: se derivan de
 *   la pata que baja la deuda (`PagosDeDeuda.kt`), y la fila las abre en la pestaña «Cuota» de Agregar.
 * - **El mismo movimiento que acepta `POST /api/events`**: mismas guardas de monto, textos, categorías
 *   reservadas, fecha y cuenta del dueño; el tipo, el de la regla.
 * - **Idempotente por el id del movimiento**: un reintento (la respuesta se perdió) contesta 200 con lo
 *   que quedó, sin escribir otro gasto.
 * - **Una ocurrencia ya saldada por OTRO movimiento vivo** contesta 409 y no escribe nada: anotar otro
 *   pago la pagaría dos veces.
 */
internal sealed interface PagoDeLaOcurrencia {
    data class Hecho(val anotado: PagoDeLaOcurrenciaAnotado, val nuevo: Boolean) : PagoDeLaOcurrencia
    data class Rechazado(val codigo: HttpStatusCode, val motivo: String) : PagoDeLaOcurrencia
}

/** Corta la transacción entera: con una excepción, Exposed deshace lo que se alcanzó a escribir. */
private class PagoRechazado(val codigo: HttpStatusCode, motivo: String) : RuntimeException(motivo)

private val PERIODO_DEL_SELLO = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")

internal const val PAGO_YA_ANOTADO = "Ese pago ya está anotado con otro movimiento."
internal const val PAGO_DE_DEUDA_EN_CUOTA =
    "La cuota de un crédito y el pago de una tarjeta se anotan en «Cuota»: bajan la deuda."

/**
 * **«Anotar pago», entero**: abre su transacción, y si algo no pasa una guarda la deshace y contesta
 * el motivo. Con [hoy] por parámetro: las pruebas lo fijan (`AppClock` no se puede mover).
 *
 * @param mandoLaMoneda si el cliente mandó `currency`: sin ella el movimiento toma la de la cuenta,
 *   igual que `POST /api/events` (el default `"COP"` del modelo no viaja).
 */
internal suspend fun anotarElPagoDeLaOcurrencia(
    uid: String,
    ruleId: String,
    pedido: AnotarElPagoDeLaOcurrencia,
    mandoLaMoneda: Boolean,
    hoy: java.time.LocalDate,
    ahora: Long = System.currentTimeMillis(),
): PagoDeLaOcurrencia = try {
    dbQuery { anotarOFallar(uid, ruleId, pedido, mandoLaMoneda, hoy, ahora) }
} catch (e: PagoRechazado) {
    // La excepción salió de la transacción: Exposed ya deshizo lo que se alcanzó a escribir.
    PagoDeLaOcurrencia.Rechazado(e.codigo, e.message ?: "No se pudo anotar el pago.")
}

private fun rechazar(codigo: HttpStatusCode, motivo: String): Nothing = throw PagoRechazado(codigo, motivo)

private fun Transaction.anotarOFallar(
    uid: String,
    ruleId: String,
    pedido: AnotarElPagoDeLaOcurrencia,
    mandoLaMoneda: Boolean,
    hoy: java.time.LocalDate,
    ahora: Long,
): PagoDeLaOcurrencia.Hecho {
    if (ruleId.startsWith(CREDIT_RULE_PREFIX) || ruleId.startsWith(CARD_RULE_PREFIX)) {
        rechazar(HttpStatusCode.BadRequest, PAGO_DE_DEUDA_EN_CUOTA)
    }
    pedido.periodo?.let { if (!PERIODO_DEL_SELLO.matches(it)) rechazar(HttpStatusCode.BadRequest, "Periodo inválido: usa \"YYYY-MM\".") }

    // ── El movimiento: las mismas guardas que `POST /api/events` ──────────────
    val crudo = pedido.movimiento
    if (crudo.id.isBlank()) rechazar(HttpStatusCode.BadRequest, "Falta el id del movimiento.")
    if (crudo.transferId != null || crudo.category == TRANSFER_CATEGORY) {
        rechazar(HttpStatusCode.UnprocessableEntity, TRANSFER_LEG_NOT_STANDALONE)
    }
    if (esCategoriaDelDesembolso(crudo.category)) rechazar(HttpStatusCode.BadRequest, DESEMBOLSO_CATEGORY_NOT_MANUAL)
    rechazoDelMonto(crudo.amount)?.let { rechazar(HttpStatusCode.BadRequest, it) }
    rechazoDeLosTextos(crudo.category, crudo.description)?.let { rechazar(HttpStatusCode.BadRequest, it) }
    if (isReservedCategory(crudo.category)) rechazar(HttpStatusCode.UnprocessableEntity, CATEGORY_RESERVED_NOT_MANUAL)
    val timestamp = if (crudo.timestamp == 0L) ahora else crudo.timestamp
    if (epochMillisToAppDate(timestamp).year !in 2000..2100) rechazar(HttpStatusCode.BadRequest, "Esa fecha no es de este siglo.")

    // ── La regla y la cuenta, del dueño ───────────────────────────────────────
    val rule = RecurringRules.selectAll()
        .where { (RecurringRules.id eq ruleId) and (RecurringRules.userId eq uid) }
        .firstOrNull()?.toRule()
        ?: rechazar(HttpStatusCode.NotFound, "Ese recurrente no existe.")
    if (crudo.type != rule.type) {
        rechazar(HttpStatusCode.UnprocessableEntity, "Ese movimiento no es del tipo de «${rule.name}».")
    }
    val monedaDeLaCuenta = Accounts.selectAll()
        .where { (Accounts.id eq crudo.accountId) and (Accounts.userId eq uid) }
        .firstOrNull()?.get(Accounts.currency)
        ?: rechazar(HttpStatusCode.NotFound, "Esa cuenta no existe.")

    // ── Qué ocurrencia paga ───────────────────────────────────────────────────
    val periodo = pedido.periodo
        ?: ocurrenciaPorPreguntar(hoy, rule, ajustesDelPeriodoSinSuspender(uid))?.let(::periodOf)
        ?: rechazar(HttpStatusCode.UnprocessableEntity, "«${rule.name}» no tiene un pago en este período.")

    // Ya saldada por OTRO movimiento vivo: un segundo pago la pagaría dos veces.
    val selloActual = RecurringOccurrences.selectAll()
        .where {
            (RecurringOccurrences.userId eq uid) and (RecurringOccurrences.ruleId eq ruleId) and
                (RecurringOccurrences.period eq periodo)
        }
        .firstOrNull()?.get(RecurringOccurrences.eventId)
    if (selloActual != null && selloActual != crudo.id && estaVivo(uid, selloActual)) {
        rechazar(HttpStatusCode.Conflict, PAGO_YA_ANOTADO)
    }

    // ── El reintento: el mismo id que ya llegó ────────────────────────────────
    val existente = Events.selectAll().where { Events.id eq crudo.id }.firstOrNull()
    val nuevo = existente == null
    val movimiento: FinancialEvent = if (existente != null) {
        if (existente[Events.userId] != uid || !estaVivo(uid, crudo.id)) {
            rechazar(HttpStatusCode.Conflict, "Ese movimiento ya no se puede usar.")
        }
        existente.toFinancialEvent()
    } else {
        val evento = crudo.copy(
            timestamp = timestamp,
            currency = if (mandoLaMoneda) crudo.currency else monedaDeLaCuenta,
            createdAt = crudo.createdAt?.takeIf { epochMillisToAppDate(it).year in 2000..2100 } ?: ahora,
            // Lo anotó el dueño con sus dedos: ya está confirmado (ver `POST /api/events`, F12).
            reconciliationStatus = if (crudo.source in ORIGENES_YA_REVISADOS) ReconciliationStatus.RECONCILED else crudo.reconciliationStatus,
        )
        insertEventRow(uid, evento)
        evento
    }

    // ── El sello: el de «Sí, fue este», con todas sus guardas ─────────────────
    val ocurrencia = when (val sello = sellarOcurrencia(uid, rule, periodo, movimiento.id, hoy)) {
        is MarcaResult.Ok -> sello.occurrence
        is MarcaResult.Error -> rechazar(sello.code, sello.message ?: "No se pudo marcar el pago.")
    }
    return PagoDeLaOcurrencia.Hecho(
        anotado = PagoDeLaOcurrenciaAnotado(
            movimiento = movimiento.withCashFlowFlag(accountTypesFor(uid)),
            ocurrencia = ocurrencia,
        ),
        nuevo = nuevo,
    )
}

/** El movimiento [eventId] de [uid] existe y no está anulado. */
private fun estaVivo(uid: String, eventId: String): Boolean {
    val existe = Events.selectAll().where { (Events.id eq eventId) and (Events.userId eq uid) }.any()
    if (!existe) return false
    return VoidEvents.selectAll()
        .where { (VoidEvents.originalEventId eq eventId) and (VoidEvents.userId eq uid) }
        .none()
}
