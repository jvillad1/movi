package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.fx.convertirEntreMonedas
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_ID_ALREADY_USED
import com.jvillada.movi.shared.model.pagoDeCuotaLegs
import com.jvillada.movi.shared.model.pataDelAviso
import com.jvillada.movi.shared.model.patasDelAvance
import com.jvillada.movi.shared.model.pedidoDePago
import com.jvillada.movi.shared.model.pedidoDeTraspaso
import com.jvillada.movi.shared.model.transferLegsFor
import com.jvillada.movi.shared.model.validarDosPatas
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

/*
 * # Al confirmar un aviso, Movi arma solo las dos patas (arreglo 1 de la auditoría de la ingesta)
 *
 * Una sola función escribe las patas de un aviso de dos lados —el pago de una tarjeta, la cuota de un
 * crédito, un traspaso entre cuentas suyas, un avance— y la usan las dos confirmaciones: la de un
 * aviso suelto (`POST /api/sms/{id}/confirm` con cuerpo) y la de un pago avisado varias veces (`POST
 * /api/sms/grupo/{grupoId}/confirmar` con `patas`). Las dos entran por [confirmarElMismoPago], que es
 * quien bloquea los avisos (`FOR UPDATE`), decide si ya estaba anotado y los marca con `evento_id` y
 * `confirmado_en`; esto es lo que escribe los movimientos, dentro de esa misma transacción.
 *
 * ## Por qué la app manda la intención y el server no la deduce
 *
 * Las patas se crean **solo cuando la app nueva manda [DosPatasDelAviso]**. El server no las deduce de
 * un aviso por su cuenta, por dos razones:
 *
 * - **Compatibilidad**: el APK 1.69 crea el movimiento él mismo (`POST /api/events`) y después
 *   confirma. Si el server armara dos patas al confirmar, ese movimiento quedaría duplicado. Sin
 *   cuerpo, `/confirm` hace exactamente lo de siempre.
 * - **El destino es del dueño**: el aviso nombra un número (*3684) que puede ser dos cuentas (la
 *   Master Black en pesos y en dólares), o una cuenta que no lleva el número en el nombre (*8133). La
 *   app propone, el dueño ve «Sale de … · entra a …» y puede cambiar el destino antes de confirmar.
 */

/** Lo que pasó al escribir las patas: los ids, o por qué no. */
internal sealed interface PatasDelAviso {
    /** [pataDelAviso] es la que queda en `sms_messages.evento_id`; [creadas] es falso si ya estaban (un reintento). */
    data class Escritas(val pataDelAviso: String, val ids: List<String>, val creadas: Boolean) : PatasDelAviso
    data class Rechazadas(val estado: HttpStatusCode, val motivo: String) : PatasDelAviso
}

private const val MAX_ID = 50

/**
 * **Escribe las dos patas de [patas]**, dentro de la transacción del llamador: o las dos, o ninguna.
 * Primero se valida todo y recién después se escribe, así que un rechazo no deja nada a medias.
 *
 * - **Aislamiento**: las dos cuentas tienen que ser de [uid] (si no, 404, como en el resto).
 * - **Las reglas de siempre**: [validarDosPatas] (que pasa por `validateTransfer` y `validarPagoDeCuota`)
 *   y, para una deuda, el desglose de [desgloseDelPagoDeDeuda] con su validación del interés real.
 * - **Las patas de siempre**: [transferLegsFor] para un traspaso, [pagoDeCuotaLegs] para un pago o una
 *   cuota —la misma que usan el pago de cuota y la Ola Y, así que el período queda marcado igual que
 *   cuando el dueño vincula a mano (`PagosDeDeuda.kt` lee la pata de la deuda)— y [patasDelAvance].
 * - **Idempotente por los ids**: si el `transferId` ya tiene exactamente estas dos patas, no se escribe
 *   nada y se contesta con ellas. Si los ids los usa otra cosa, se rechaza en vez de mezclar.
 *
 * Las dos patas salen con el origen del aviso ([origen]: `SMS`, u `OCR` si es un comprobante),
 * confirmadas, y la del dinero lleva el texto del aviso en `raw_payload`, como el movimiento suelto de
 * siempre (`movimientoConfirmadoDelSms`): es la pista con que se reconoce después.
 */
internal fun Transaction.escribirLasPatasDelAviso(
    uid: String,
    patas: DosPatasDelAviso,
    origen: EventSource,
    textoDelAviso: String?,
    ahora: Long,
): PatasDelAviso {
    listOf(patas.transferId, patas.origenEventId, patas.destinoEventId).forEach { id ->
        if (id.isBlank() || id.length > MAX_ID) {
            return PatasDelAviso.Rechazadas(HttpStatusCode.UnprocessableEntity, "Falta un identificador del pago, o es demasiado largo")
        }
    }
    if (patas.origenEventId == patas.destinoEventId) {
        return PatasDelAviso.Rechazadas(HttpStatusCode.UnprocessableEntity, "Las dos patas no pueden compartir el mismo identificador")
    }
    if (epochMillisToAppDate(patas.timestamp).year !in 2000..2100) {
        return PatasDelAviso.Rechazadas(HttpStatusCode.BadRequest, "Esa fecha no es de este siglo.")
    }

    fun cuenta(id: String) = Accounts.selectAll()
        .where { (Accounts.userId eq uid) and (Accounts.id eq id) }
        .firstOrNull()?.toAccount()
    val desde = cuenta(patas.origenId)
    val hacia = cuenta(patas.destinoId)
    if (desde == null || hacia == null) return PatasDelAviso.Rechazadas(HttpStatusCode.NotFound, "Cuenta no encontrada")
    validarDosPatas(patas, desde, hacia)?.let { return PatasDelAviso.Rechazadas(HttpStatusCode.UnprocessableEntity, it) }

    val ids = listOf(patas.origenEventId, patas.destinoEventId)
    // ¿Ya están? Un reintento manda los mismos tres ids: el pago ya ocurrió y se contesta con él.
    val delTraspaso = Events.select(Events.id)
        .where { (Events.userId eq uid) and (Events.transferId eq patas.transferId) }
        .map { it[Events.id] }.toSet()
    if (delTraspaso == ids.toSet()) return PatasDelAviso.Escritas(patas.pataDelAviso, ids, creadas = false)
    if (delTraspaso.isNotEmpty()) return PatasDelAviso.Rechazadas(HttpStatusCode.UnprocessableEntity, TRANSFER_ID_ALREADY_USED)
    // Un id que ya usa otro movimiento —de este usuario o de otro— no se pisa.
    if (Events.selectAll().where { Events.id inList ids }.count() > 0) {
        return PatasDelAviso.Rechazadas(HttpStatusCode.Conflict, "Ese id ya existe")
    }

    val (salida, entrada) = when (patas.operacion) {
        OperacionDelAviso.TRASPASO -> transferLegsFor(pedidoDeTraspaso(patas), desde, hacia)
        OperacionDelAviso.AVANCE -> patasDelAvance(patas, desde, hacia)
        OperacionDelAviso.PAGO_DE_TARJETA, OperacionDelAviso.CUOTA -> {
            // Lo que baja la deuda, en su moneda: entre monedas (solo tarjetas) lo que dijo la app o
            // la TRM del día (ver [conLaTasaDelDia]); si no, lo que salió de la cuenta.
            val cuota = if (desde.currency != hacia.currency) patas.montoEnLaMonedaDeLaDeuda ?: patas.monto else patas.monto
            when (val calculado = desgloseDelPagoDeDeuda(uid, hacia, patas.transferId, patas.timestamp, cuota, patas.interesReal)) {
                is DesgloseDelPago.Bien -> pagoDeCuotaLegs(pedidoDePago(patas), desde, hacia, calculado.desglose)
                is DesgloseDelPago.Mal -> return PatasDelAviso.Rechazadas(HttpStatusCode.UnprocessableEntity, calculado.motivo)
            }
        }
    }
    fun delAviso(pata: FinancialEvent) = pata.copy(
        source = origen,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        rawPayload = if (pata.id == patas.pataDelAviso) textoDelAviso?.takeIf { it.isNotBlank() } else null,
        createdAt = ahora,
    )
    insertEventRow(uid, delAviso(salida))
    insertEventRow(uid, delAviso(entrada))
    return PatasDelAviso.Escritas(patas.pataDelAviso, ids, creadas = true)
}

/**
 * **Los ids de los movimientos del pago al que pertenece [eventoId]**: él primero y, si es una pata,
 * su hermana. Es lo que contesta un doble toque: lo mismo que el primero.
 */
internal fun Transaction.patasDelMovimiento(uid: String, eventoId: String): List<String> {
    val transferId = Events.select(Events.transferId)
        .where { (Events.userId eq uid) and (Events.id eq eventoId) }
        .firstOrNull()?.get(Events.transferId) ?: return emptyList()
    val hermanas = Events.select(Events.id)
        .where { (Events.userId eq uid) and (Events.transferId eq transferId) }
        .map { it[Events.id] }
        .filter { it != eventoId }
        .sorted()
    return listOf(eventoId) + hermanas
}

/**
 * **Pagar una tarjeta en otra moneda sin que la app diga cuánto bajó la deuda**: se convierte con la
 * TRM del día, igual que la Ola Y (`vincular-deuda`). Va **antes** de la transacción —pedir la tasa
 * puede salir a la red, y eso no se hace con los avisos bloqueados—; si no hay una tasa de verdad
 * (solo la de respaldo), no se inventa: el pedido sigue sin el monto y la validación lo dice.
 */
internal suspend fun conLaTasaDelDia(uid: String, patas: DosPatasDelAviso?): DosPatasDelAviso? {
    if (patas == null || patas.operacion != OperacionDelAviso.PAGO_DE_TARJETA || patas.montoEnLaMonedaDeLaDeuda != null) return patas
    val (desde, hacia) = dbQuery {
        fun cuenta(id: String) = Accounts.selectAll()
            .where { (Accounts.userId eq uid) and (Accounts.id eq id) }
            .firstOrNull()?.toAccount()
        cuenta(patas.origenId) to cuenta(patas.destinoId)
    }
    if (desde == null || hacia == null || desde.currency == hacia.currency || hacia.type != AccountType.CREDIT_CARD) return patas
    val convertido = convertirEntreMonedas(patas.monto, desde.currency, hacia.currency, FxRateService.tasaUsdCop())
        ?: return patas
    return patas.copy(montoEnLaMonedaDeLaDeuda = convertido)
}
