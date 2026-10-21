package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.balanceAdjustmentEventFor
import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.enrichWith
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.parsing.sinTildes
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.shared.model.CuentaParaElSaldo
import com.jvillada.movi.shared.model.ExtractoDeSaldoDe
import com.jvillada.movi.shared.model.MAX_ACCOUNT_BALANCE_COP
import com.jvillada.movi.shared.model.PropuestaDelExtractoDeSaldo
import com.jvillada.movi.shared.model.ResumenDelExtractoDeSaldo
import com.jvillada.movi.shared.model.group
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.security.MessageDigest
import java.time.LocalDate

/**
 * # «Actualizar el saldo» con lo que dijo el extracto de Skandia o de la AFC
 *
 * La lectura del extracto (`leerElExtracto`, sin IA, ver `leerUnExtractoDeSaldo`) arma la
 * propuesta ([propuestaDelExtractoDeSaldo]); esto es el sí del dueño:
 * `POST /api/accounts/{id}/saldo-del-extracto` con el mismo [ResumenDelExtractoDeSaldo] que vio.
 *
 * Escribe **un ajuste** con el constructor de siempre ([balanceAdjustmentEventFor]: categoría
 * «Ajuste de saldo», no cuenta como gasto ni como ingreso) **fechado al final del día del corte**:
 * el extracto habla de ese día, y lo que se anotó después no cambia lo que había entonces. El
 * saldo de Movi se mide ahí, sin anulados.
 *
 * - **Nunca se ajusta solo**: leer el extracto no escribe nada; solo esta ruta.
 * - **Idempotente**: el saldo se recalcula adentro de la transacción, con la cuenta bloqueada, así
 *   que el segundo «Actualizar» encuentra la cuenta ya cuadrada al corte y no escribe nada. Además
 *   el ajuste lleva un id que sale del extracto (cuenta, corte, saldo): si ya existe —aunque el
 *   dueño lo haya anulado después— no se escribe otro.
 * - **Una cuenta cuadrada después del corte no se toca** (409): un ajuste a una fecha anterior a
 *   ese cuadre lo movería. Hay que subir un extracto más nuevo.
 * - En Skandia el ajuste es la **valuación del fondo** (rendimientos menos comisiones): es lo que
 *   cierra «sin valuación de inversiones».
 *
 * Contesta lo mismo que el cuadre a mano ([AdjustAccountBalanceResponse]): la cuenta como quedó y
 * el ajuste, que el teléfono espeja.
 */
fun Route.extractoDeSaldoRoutes() {
    post("/api/accounts/{accountId}/saldo-del-extracto") {
        val uid = call.userId()
        val accountId = call.parameters["accountId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val resumen = runCatching { call.receive<ResumenDelExtractoDeSaldo>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta lo que dijo el extracto")
        motivoParaNoAjustar(resumen)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
        when (val hecho = dbQuery { ajustarConElExtracto(uid, accountId, resumen) }) {
            is AjusteDelExtracto.Hecho -> call.respond(hecho.respuesta)
            is AjusteDelExtracto.Rechazado -> call.respond(hecho.estado, hecho.motivo)
        }
    }
}

private sealed interface AjusteDelExtracto {
    data class Hecho(val respuesta: AdjustAccountBalanceResponse) : AjusteDelExtracto
    data class Rechazado(val estado: HttpStatusCode, val motivo: String) : AjusteDelExtracto
}

/** Lo que no se le cree a un cuerpo que dice venir de un extracto. */
private fun motivoParaNoAjustar(r: ResumenDelExtractoDeSaldo): String? {
    val corte = runCatching { LocalDate.parse(r.fechaDeCorte) }.getOrNull()
    return when {
        r.saldo < 0L || r.saldo > MAX_ACCOUNT_BALANCE_COP -> "Saldo fuera de rango — revisa el monto"
        corte == null -> "Fecha de corte inválida"
        corte.isAfter(AppClock.today()) -> "La fecha de corte del extracto todavía no llega"
        else -> null
    }
}

/** El último instante del día del corte. */
private fun finDelCorte(r: ResumenDelExtractoDeSaldo): Long =
    appDateToEpochMillis(LocalDate.parse(r.fechaDeCorte).plusDays(1)) - 1

/**
 * ¿A [cuenta] se le puede poner el saldo de un extracto en [moneda]? Dinero e Inversión, no deudas
 * ni bienes, en la misma moneda: la misma regla del cuadre a mano y del cuadre por aviso.
 */
private fun recibeElSaldo(cuenta: Account, moneda: String): Boolean =
    cuenta.type.group != AccountGroup.DEUDA && cuenta.bien == null && cuenta.currency == moneda

/** Lo que Movi tiene en [cuenta] al corte, sin anulados, y si se cuadró después del corte. */
private fun Transaction.alCorte(uid: String, cuenta: Account, moneda: String, corte: Long): CuentaParaElSaldo {
    val movimientos = loadNonVoidedEventsIn(uid, cuenta.id)
    return CuentaParaElSaldo(
        id = cuenta.id,
        nombre = cuenta.name,
        saldoEnMovi = computeBalances(cuenta.type, movimientos.filter { it.timestamp <= corte })[moneda] ?: 0L,
        cuadradaDespues = movimientos.any { it.category == ADJUSTMENT_CATEGORY && it.timestamp > corte },
    )
}

/**
 * **La cuenta que nombra el extracto**, sin adivinar: la AFC por los últimos cuatro de su número
 * en el nombre («AFC Davibank 9497»), Skandia por llamarse así. Con ninguna o más de una, `null`:
 * el dueño elige.
 */
private fun laQueNombra(r: ResumenDelExtractoDeSaldo, cuentas: List<Account>): Account? {
    val candidatas = when (r.de) {
        ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO -> cuentas.filter { sinTildes(it.name).contains("skandia") }
        ExtractoDeSaldoDe.DAVIBANK_AHORROS -> {
            val numero = r.numero?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: return null
            cuentas.filter { c -> Regex("""\d+""").findAll(c.name).any { it.value.takeLast(4) == numero } }
        }
    }
    return candidatas.singleOrNull()
}

/**
 * **La propuesta**: lo que dice el extracto, la cuenta que nombra (si la hay sin duda) y las cuentas
 * entre las que el dueño puede elegir, cada una con lo que Movi tiene al corte.
 */
internal fun Transaction.propuestaDelExtractoDeSaldo(uid: String, r: ResumenDelExtractoDeSaldo): PropuestaDelExtractoDeSaldo {
    val corte = finDelCorte(r)
    val cuentas = Accounts.selectAll().where { Accounts.userId eq uid }
        .map { it.toAccount() }
        .filter { recibeElSaldo(it, r.moneda) }
    val nombrada = laQueNombra(r, cuentas)
    val ordenadas = listOfNotNull(nombrada) + cuentas.filter { it.id != nombrada?.id }.sortedBy { it.name.lowercase() }
    return PropuestaDelExtractoDeSaldo(
        resumen = r,
        cuentaId = nombrada?.id,
        opciones = ordenadas.map { alCorte(uid, it, r.moneda, corte) },
    )
}

/**
 * El id del ajuste de un extracto: el mismo extracto aplicado a la misma cuenta da el mismo id.
 * Con el usuario adentro, para que dos dueños nunca choquen.
 */
internal fun idDelAjusteDelExtracto(uid: String, cuentaId: String, r: ResumenDelExtractoDeSaldo): String {
    val huella = MessageDigest.getInstance("SHA-256")
        .digest("$uid|$cuentaId|${r.de}|${r.fechaDeCorte}|${r.saldo}".toByteArray())
        .joinToString("") { "%02x".format(it) }
    return "ev_extracto_${huella.take(32)}"
}

private fun Transaction.ajustarConElExtracto(uid: String, accountId: String, r: ResumenDelExtractoDeSaldo): AjusteDelExtracto {
    val cuenta = Accounts.selectAll()
        .where { (Accounts.id eq accountId) and (Accounts.userId eq uid) }
        .forUpdate()
        .firstOrNull()?.toAccount()
        ?: return AjusteDelExtracto.Rechazado(HttpStatusCode.NotFound, "No existe esa cuenta")
    if (!recibeElSaldo(cuenta, r.moneda)) {
        return AjusteDelExtracto.Rechazado(
            HttpStatusCode.UnprocessableEntity,
            "Este extracto se pone en una cuenta de dinero o de inversión en ${r.moneda}, no en una deuda ni en un bien",
        )
    }
    val corte = finDelCorte(r)
    val alCorte = alCorte(uid, cuenta, r.moneda, corte)
    if (alCorte.cuadradaDespues) {
        return AjusteDelExtracto.Rechazado(
            HttpStatusCode.Conflict,
            "Esta cuenta ya se cuadró después del corte del extracto. Sube un extracto más nuevo.",
        )
    }
    val id = idDelAjusteDelExtracto(uid, cuenta.id, r)
    val yaEstaba = Events.selectAll().where { Events.id eq id }.any()
    val ajuste = if (yaEstaba) null else
        balanceAdjustmentEventFor(cuenta, alCorte.saldoEnMovi, r.saldo, now = corte)?.copy(id = id)
    ajuste?.let { insertEventRow(uid, it) }
    // Solo cuentas en la moneda del extracto (pesos): la tasa de cambio no se usa, igual que en el
    // extracto de un crédito — y así esta ruta no sale a la red.
    return AjusteDelExtracto.Hecho(
        AdjustAccountBalanceResponse(
            account = enrichWith(cuenta, loadNonVoidedEventsIn(uid, cuenta.id), rate = 0.0),
            adjustmentEvent = ajuste,
        ),
    )
}
