package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.balanceAdjustmentEventFor
import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.enrichWith
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.paidPctFor
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.insertEventRow
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MAX_CREDIT_DEBT_COP
import com.jvillada.movi.shared.model.PropuestaDelExtractoDeCredito
import com.jvillada.movi.shared.model.ResumenDelExtractoDeCredito
import com.jvillada.movi.shared.model.cuotaMasCercana
import com.jvillada.movi.shared.model.tasaMensualDeUnaEA
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import kotlin.math.round

/**
 * # «Actualizar» con lo que dijo el extracto de un crédito
 *
 * La lectura del extracto (`leerElExtracto`, sin IA) arma la propuesta
 * ([propuestaDelExtractoDeCredito]); esto es el sí del dueño: `POST /api/credits/{id}/extracto`
 * con el mismo [ResumenDelExtractoDeCredito] que vio. Escribe, en una transacción:
 *
 * - en las condiciones, el seguro, la tasa y el interés del extracto para su cuota
 *   ([CreditTerms.interesReal] + [CreditTerms.interesRealCuota]); lo que el extracto no dice no se toca;
 * - si el saldo de capital no coincide, **el ajuste de siempre** ([balanceAdjustmentEventFor],
 *   categoría «Ajuste de saldo»), fechado al final del día del corte: el extracto habla de ese día,
 *   y lo que se pagó después no cambia lo que se debía entonces.
 *
 * Contesta el [CreditSummary] que quedó, con el ajuste en `adjustmentEvent` (lo espeja el teléfono,
 * igual que el ajuste a mano). Solo créditos en pesos, como el ajuste a mano.
 */
fun Route.extractoDeCreditoRoutes() {
    post("/api/credits/{accountId}/extracto") {
        val uid = call.userId()
        val accountId = call.parameters["accountId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val resumen = runCatching { call.receive<ResumenDelExtractoDeCredito>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta lo que dijo el extracto")
        motivoParaNoActualizar(resumen)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
        when (val hecho = dbQuery { actualizarConElExtracto(uid, accountId, resumen, ahora = System.currentTimeMillis()) }) {
            is Actualizacion.Hecha -> call.respond(hecho.resumen)
            is Actualizacion.Rechazada -> call.respond(hecho.estado, hecho.motivo)
        }
    }
}

private sealed interface Actualizacion {
    data class Hecha(val resumen: CreditSummary) : Actualizacion
    data class Rechazada(val estado: HttpStatusCode, val motivo: String) : Actualizacion
}

/** Lo que no se le cree a un cuerpo que dice venir de un extracto. */
private fun motivoParaNoActualizar(r: ResumenDelExtractoDeCredito): String? {
    val saldo = r.saldoCapital
    val interes = r.interesDelPeriodo
    val seguro = r.seguro
    val tasa = r.tasaEa
    return when {
        !r.traeAlgo -> "El extracto no trae nada que actualizar"
        saldo != null && (saldo < 0L || saldo > MAX_CREDIT_DEBT_COP) -> "Saldo fuera de rango — revisa el monto"
        interes != null && interes < 0L -> "El interés no puede ser negativo"
        seguro != null && seguro < 0L -> "El seguro no puede ser negativo"
        tasa != null && (tasa <= 0.0 || tasa >= 100.0 || !tasa.isFinite()) -> "Tasa fuera de rango"
        r.fechaDeCorte != null && runCatching { LocalDate.parse(r.fechaDeCorte) }.isFailure -> "Fecha de corte inválida"
        r.fechaDePago != null && runCatching { LocalDate.parse(r.fechaDePago) }.isFailure -> "Fecha de pago inválida"
        else -> null
    }
}

/** El último instante del día del corte, o `null` si el extracto no lo dice. */
private fun finDelCorte(r: ResumenDelExtractoDeCredito): Long? =
    r.fechaDeCorte?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.let { appDateToEpochMillis(it.plusDays(1)) - 1 }

/** La cuota (`"2026-09"`) a la que pertenece el interés del extracto: la de su fecha de pago. */
private fun cuotaDelInteres(r: ResumenDelExtractoDeCredito, diaDePago: Int): String? =
    r.fechaDePago?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.let { cuotaMasCercana(it.year, it.monthValue, it.dayOfMonth, diaDePago) }

/**
 * **El crédito que nombra el extracto**: la cuenta de préstamo de [uid] cuyo nombre lleva uno de
 * [numeros] (los últimos cuatro de la obligación primero), como «Libre inversión 9695». Con más de
 * una o ninguna, `null`: no se adivina de quién es la deuda.
 */
private fun Transaction.creditoDelExtracto(uid: String, numeros: List<String>): Account? {
    val prestamos = Accounts.selectAll()
        .where { (Accounts.userId eq uid) and (Accounts.type eq AccountType.LOAN.name) }
        .map { it.toAccount() }
    for (numero in numeros.filter { it.length == 4 && it.all(Char::isDigit) }) {
        val conEseNumero = prestamos.filter { Regex("""(?<!\d)$numero(?!\d)""").containsMatchIn(it.name) }
        if (conEseNumero.size == 1) return conEseNumero.single()
    }
    return null
}

private fun Transaction.condicionesDe(uid: String, accountId: String): CreditTerms? =
    Credits.selectAll()
        .where { (Credits.accountId eq accountId) and (Credits.userId eq uid) }
        .firstOrNull()?.toCreditTerms()

/** La deuda de [cuenta] en el corte (todo si no hay corte), sin anulados, en su moneda. */
private fun Transaction.deudaAlCorte(uid: String, cuenta: Account, corte: Long?): Long =
    computeBalances(
        cuenta.type,
        loadNonVoidedEventsIn(uid, cuenta.id).filter { corte == null || it.timestamp <= corte },
    )[cuenta.currency] ?: 0L

/**
 * **La propuesta**: lo que dice el extracto al lado de lo que dice Movi. Sin el crédito en Movi,
 * solo el resumen: la pantalla dice que no lo encontró y no ofrece «Actualizar».
 *
 * El interés que «Movi estimaba» es la misma cuenta de `desglosarCuota`: la deuda al corte por la
 * tasa mensual equivalente, redondeado. Sin tasa no hay estimación; sin intereses declarado, cero.
 */
internal fun Transaction.propuestaDelExtractoDeCredito(
    uid: String,
    resumen: ResumenDelExtractoDeCredito,
    numeros: List<String>,
): PropuestaDelExtractoDeCredito {
    val cuenta = creditoDelExtracto(uid, numeros) ?: return PropuestaDelExtractoDeCredito(resumen)
    val terms = condicionesDe(uid, cuenta.id)
    val deuda = deudaAlCorte(uid, cuenta, finDelCorte(resumen))
    val estimado = when {
        terms == null -> null
        terms.sinIntereses -> 0L
        terms.rateEa > 0.0 -> round(deuda.coerceAtLeast(0L).toDouble() * tasaMensualDeUnaEA(terms.rateEa)).toLong()
        else -> null
    }
    return PropuestaDelExtractoDeCredito(
        resumen = resumen,
        cuentaId = cuenta.id,
        cuentaNombre = cuenta.name,
        saldoEnMovi = deuda,
        interesEstimado = estimado,
        seguroEnMovi = terms?.insuranceMonthly,
        tasaEnMovi = terms?.let { if (it.sinIntereses) 0.0 else it.rateEa.takeIf { t -> t > 0.0 } },
    )
}

private fun Transaction.actualizarConElExtracto(
    uid: String,
    accountId: String,
    r: ResumenDelExtractoDeCredito,
    ahora: Long,
): Actualizacion {
    val cuenta = Accounts.selectAll()
        .where { (Accounts.id eq accountId) and (Accounts.userId eq uid) }
        .forUpdate()
        .firstOrNull()?.toAccount()
        ?: return Actualizacion.Rechazada(HttpStatusCode.NotFound, "No existe ese crédito")
    if (cuenta.type != AccountType.LOAN) {
        return Actualizacion.Rechazada(HttpStatusCode.UnprocessableEntity, "Solo un crédito se actualiza con su extracto")
    }
    if (cuenta.currency != "COP") {
        return Actualizacion.Rechazada(HttpStatusCode.UnprocessableEntity, "Solo se pueden actualizar créditos en pesos")
    }
    val antes = condicionesDe(uid, accountId)
        ?: return Actualizacion.Rechazada(
            HttpStatusCode.UnprocessableEntity,
            "Este crédito no tiene condiciones todavía. Regístralas primero y vuelve a leer el extracto.",
        )
    val cuotaDelInteres = cuotaDelInteres(r, antes.dayOfMonth)
    val despues = antes.copy(
        insuranceMonthly = r.seguro?.takeIf { it > 0L } ?: antes.insuranceMonthly,
        rateEa = r.tasaEa ?: antes.rateEa,
        // Una tasa del banco dice que el crédito sí cobra: la casilla de «no cobra» deja de ser verdad.
        sinIntereses = if (r.tasaEa != null) false else antes.sinIntereses,
        interesReal = if (r.interesDelPeriodo != null && cuotaDelInteres != null) r.interesDelPeriodo else antes.interesReal,
        interesRealCuota = if (r.interesDelPeriodo != null && cuotaDelInteres != null) cuotaDelInteres else antes.interesRealCuota,
    )
    Credits.update({ (Credits.accountId eq accountId) and (Credits.userId eq uid) }) {
        it[insuranceMonthly] = despues.insuranceMonthly
        it[rateEa] = despues.rateEa
        it[sinIntereses] = despues.sinIntereses
        it[interesReal] = despues.interesReal
        it[interesRealCuota] = despues.interesRealCuota
    }

    var ajuste: FinancialEvent? = null
    val saldoDelExtracto = r.saldoCapital
    if (saldoDelExtracto != null) {
        val corte = finDelCorte(r)
        val deuda = deudaAlCorte(uid, cuenta, corte)
        ajuste = balanceAdjustmentEventFor(cuenta, deuda, saldoDelExtracto, now = corte ?: ahora)
        ajuste?.let { insertEventRow(uid, it) }
    }

    val eventos = loadNonVoidedEventsIn(uid, accountId)
    // Un crédito en pesos no convierte nada: la tasa de cambio no se usa (ver `estimatedTotalCop`).
    val actualizada = enrichWith(cuenta, eventos, rate = 0.0)
    return Actualizacion.Hecha(
        CreditSummary(
            account = actualizada,
            terms = despues,
            paidPct = paidPctFor(despues.principal, actualizada.balance),
            adjustmentEvent = ajuste,
            hasMovements = eventos.isNotEmpty(),
        ),
    )
}
