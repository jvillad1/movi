package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.balanceAdjustmentEventFor
import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.sms.parseSmsTime
import com.jvillada.movi.server.sms.saldoDelAviso
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.cuentaPropiaConEseNumero
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.group
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.selectAll
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * # El saldo del aviso → la propuesta de cuadrar (ver `PropuestaDeCuadrePorAviso` en :core)
 *
 * - `GET /api/cuadres-por-aviso`: las propuestas vigentes, una por cuenta como mucho.
 * - `POST /api/cuadres-por-aviso/{avisoId}/ajustar`: «Ajustar». Escribe el ajuste de siempre y
 *   contesta el movimiento que escribió. 404 si ya no hay nada que ajustar (otro toque ya lo hizo).
 * - `POST /api/cuadres-por-aviso/{avisoId}/descartar`: «No ajustar». No toca ningún saldo.
 *
 * **Nada se ajusta solo**: confirmar el aviso no escribe ningún ajuste; solo hace que la propuesta
 * exista. El ajuste lo escribe «Ajustar», y nada más.
 */
fun Route.cuadrePorAvisoRoutes() {
    get("/api/cuadres-por-aviso") {
        val uid = call.userId()
        call.respond(dbQuery { cuadresPorAvisoDe(uid) })
    }

    post("/api/cuadres-por-aviso/{avisoId}/ajustar") {
        val uid = call.userId()
        val avisoId = call.parameters["avisoId"]?.takeIf { it.isNotBlank() }
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val escrito = dbQuery { ajustarPorElAviso(uid, avisoId) }
            ?: return@post call.respond(HttpStatusCode.NotFound, "Esta cuenta ya no tiene nada que ajustar.")
        call.respond(escrito)
    }

    post("/api/cuadres-por-aviso/{avisoId}/descartar") {
        val uid = call.userId()
        val avisoId = call.parameters["avisoId"]?.takeIf { it.isNotBlank() && it.length <= 200 }
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        dbQuery {
            AnomaliasDescartadas.insertIgnore {
                it[userId] = uid
                it[huella] = huellaDelCuadrePorAviso(avisoId)
                it[descartadaEn] = System.currentTimeMillis()
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * La huella con que se guarda un «No ajustar». Vive en `anomalias_descartadas` —la tabla de «esto
 * ya lo vi y está bien»— con su propio prefijo, para no inventar otra tabla igual.
 */
internal fun huellaDelCuadrePorAviso(avisoId: String): String = "saldo-del-aviso:$avisoId"

/**
 * **Cuánto de diferencia ya no es diferencia.** El banco escribe centavos y Movi trabaja en pesos
 * enteros: $243.238,20 contra $243.238 coincide. Menos de un peso no es una propuesta.
 */
private const val MENOS_DE_UN_PESO = 1.0

/**
 * **Las propuestas vigentes de [uid].** Una por cuenta como mucho: la del aviso más reciente que
 * trae saldo, porque el saldo de después borra al de antes. Y ninguna en estos casos:
 *
 * - el aviso no está confirmado, o se confirmó sin saber con qué movimiento (`evento_id`): sin el
 *   movimiento no se sabe ni la cuenta ni el instante. **Salvo un rechazo** («…fue rechazada por
 *   saldo insuficiente. Tu saldo actual es $ 78,508.», el único aviso en que Bancolombia manda el
 *   saldo): no es un movimiento, la cuenta sale del número que nombra y el instante de su hora;
 * - ese movimiento está anulado o ya no existe;
 * - la cuenta es una deuda o un bien (ver `sePuedeCuadrar`), o está en otra moneda que el saldo;
 * - **la cuenta se cuadró después** (un ajuste posterior al aviso): el dueño ya la puso contra el
 *   banco, y la diferencia vieja dejó de importar;
 * - la diferencia es de centavos ([MENOS_DE_UN_PESO]);
 * - el dueño dijo «No ajustar».
 *
 * El saldo de Movi se mide **en el instante del movimiento del aviso** —los movimientos vivos hasta
 * ahí, sin anulados—, no hoy: el banco habló de ese instante, y lo que se anotó después no cambia
 * lo que había entonces.
 */
internal fun Transaction.cuadresPorAvisoDe(uid: String): List<PropuestaDeCuadrePorAviso> {
    /** Un saldo que dijo el banco, de qué cuenta y en qué instante. */
    data class Candidato(val avisoId: String, val origen: String, val cuentaId: String, val momento: Long, val saldo: Double, val moneda: String, val texto: String)

    val filas = SmsMessages.selectAll()
        .where { SmsMessages.userId eq uid }
        .mapNotNull { fila -> saldoDelAviso(fila[SmsMessages.text])?.let { fila to it } }
    if (filas.isEmpty()) return emptyList()

    val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
    val idsDeEventos = filas.mapNotNull { (fila, _) -> fila[SmsMessages.eventoId] }.distinct()
    val eventos = if (idsDeEventos.isEmpty()) emptyMap() else Events.selectAll()
        .where { (Events.userId eq uid) and (Events.id inList idsDeEventos) }
        .map { it.toFinancialEvent() }
        .filter { it.id !in anulados }
        .associateBy { it.id }
    val cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() }
    val porId = cuentas.associateBy { it.id }
    val descartadas = AnomaliasDescartadas.selectAll()
        .where { AnomaliasDescartadas.userId eq uid }
        .map { it[AnomaliasDescartadas.huella] }
        .toSet()

    val candidatos = filas.mapNotNull { (fila, saldo) ->
        val id = fila[SmsMessages.id]
        val origen = fila[SmsMessages.bank]
        val eventoId = fila[SmsMessages.eventoId]
        if (fila[SmsMessages.state] == SMS_STATE_CONFIRMED && eventoId != null) {
            // Confirmado con su movimiento: la cuenta y el instante son los del movimiento.
            val ev = eventos[eventoId] ?: return@mapNotNull null
            Candidato(id, origen, ev.accountId, ev.timestamp, saldo.monto, saldo.moneda, fila[SmsMessages.text])
        } else if (eventoId == null && RECHAZADO.containsMatchIn(fila[SmsMessages.text])) {
            // **El saldo de un aviso que no es un movimiento.** Bancolombia solo manda el saldo en
            // el SMS de una compra RECHAZADA («…fue rechazada por saldo insuficiente. Tu saldo
            // actual es $ 78,508.»): ese aviso se aparta y nunca se confirma, pero el saldo que trae
            // es tan del banco como cualquiera. Sin movimiento, la cuenta sale del número que nombra
            // («Tarjeta Debito *4057» → «Bancolombia Ahorros 8133 · 4057», la misma regla de las
            // cuentas por número) y el instante, de la hora del aviso. Sin número no se adivina.
            // **Solo un rechazo**: el saldo de una compra que SÍ pasó y todavía no se anotó
            // propondría un ajuste por la compra misma, que después se anotaría otra vez.
            val numero = NUMERO_DEL_AVISO.find(fila[SmsMessages.text])?.groupValues?.get(1) ?: return@mapNotNull null
            val cuenta = cuentaPropiaConEseNumero(numero, cuentas) ?: return@mapNotNull null
            val momento = parseSmsTime(fila[SmsMessages.time])?.atZone(AppClock.zone)?.toInstant()?.toEpochMilli()
                ?: return@mapNotNull null
            Candidato(id, origen, cuenta.id, momento, saldo.monto, saldo.moneda, fila[SmsMessages.text])
        } else {
            null
        }
    }

    return candidatos
        // **El mismo aviso subido dos veces con la hora corrida** (el barrido del teléfono en otra
        // zona horaria, 4-oct: el rechazo de las 13:32 volvió a entrar a las 15:32). Un rechazo no
        // trae su propia hora, así que el dedupe no lo junta; vale la copia más temprana, la que
        // llegó en el momento.
        .groupBy { it.cuentaId to it.texto }
        .map { (_, copias) -> copias.minWith(compareBy({ it.momento }, { it.avisoId })) }
        .filter { c -> porId[c.cuentaId]?.let { seCuadraContraElAviso(it, c.moneda) } == true }
        // El más reciente de cada cuenta. Dos avisos del mismo instante: el de id mayor, fijo.
        .groupBy { it.cuentaId }
        .mapNotNull { (_, deLaCuenta) -> deLaCuenta.maxWithOrNull(compareBy({ it.momento }, { it.avisoId })) }
        .mapNotNull { c ->
            if (huellaDelCuadrePorAviso(c.avisoId) in descartadas) return@mapNotNull null
            propuestaDe(uid, c.avisoId, c.origen, c.saldo, c.moneda, c.momento, porId.getValue(c.cuentaId))
        }
        .sortedByDescending { it.momento }
}

/** «fue rechazada», «rechazado»: el aviso de algo que no pasó, y por eso no es un movimiento. */
private val RECHAZADO = Regex("""\brechazad[ao]s?\b""", RegexOption.IGNORE_CASE)

/** «*4057», «*8133»: el número de cuenta o tarjeta que nombra un aviso del banco. */
private val NUMERO_DEL_AVISO = Regex("""\*\s?(\d{4,})""")

/** Solo Dinero e Inversión, en la moneda del aviso. La misma regla que el cuadre a mano. */
private fun seCuadraContraElAviso(cuenta: Account, moneda: String): Boolean =
    cuenta.type.group != AccountGroup.DEUDA && cuenta.bien == null && cuenta.currency == moneda

private fun Transaction.propuestaDe(
    uid: String,
    avisoId: String,
    origen: String,
    saldo: Double,
    moneda: String,
    momento: Long,
    cuenta: Account,
): PropuestaDeCuadrePorAviso? {
    val movimientos = loadNonVoidedEventsIn(uid, cuenta.id)
    // Un cuadre posterior —a mano o con otra propuesta— ya dijo la última palabra.
    if (movimientos.any { it.category == ADJUSTMENT_CATEGORY && it.timestamp > momento }) return null
    val enMovi = computeBalances(cuenta.type, movimientos.filter { it.timestamp <= momento })[moneda] ?: 0L
    if (abs(saldo - enMovi) < MENOS_DE_UN_PESO) return null
    val delBanco = saldo.roundToLong()
    if (delBanco == enMovi) return null
    return PropuestaDeCuadrePorAviso(
        avisoId = avisoId,
        cuentaId = cuenta.id,
        cuentaNombre = cuenta.name,
        origen = origen,
        saldoDelBanco = delBanco,
        saldoEnMovi = enMovi,
        moneda = moneda,
        momento = momento,
    )
}

/**
 * «Ajustar»: escribe el ajuste de la propuesta vigente de [avisoId], **fechado en el instante del
 * aviso** —el banco habló de ese instante—, con el constructor de siempre
 * ([balanceAdjustmentEventFor]: categoría «Ajuste de saldo», no cuenta como gasto ni ingreso).
 *
 * Se recalcula adentro de la transacción en vez de creerle a la pantalla: si el dueño anotó algo
 * entre que vio la propuesta y la aceptó, el ajuste es el de ahora. `null` si ya no hay propuesta
 * (otro toque la ajustó, o la descartó): un segundo «Ajustar» no escribe un segundo ajuste.
 */
internal fun Transaction.ajustarPorElAviso(uid: String, avisoId: String): FinancialEvent? {
    val propuesta = cuadresPorAvisoDe(uid).firstOrNull { it.avisoId == avisoId } ?: return null
    val cuenta = Accounts.selectAll()
        .where { (Accounts.id eq propuesta.cuentaId) and (Accounts.userId eq uid) }
        .forUpdate()
        .firstOrNull()?.toAccount() ?: return null
    val ajuste = balanceAdjustmentEventFor(cuenta, propuesta.saldoEnMovi, propuesta.saldoDelBanco, now = propuesta.momento)
        ?: return null
    insertEventRow(uid, ajuste)
    return ajuste
}
