package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.subscriptions.runSubscriptionDetection
import com.jvillada.movi.shared.model.CreateSubscriptionRequest
import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.math.roundToLong

// F38: el prefijo que separa las suscripciones de alta manual del dominio del detector se mudó
// a `:core` (MANUAL_SUB_PREFIX) — el cliente lo necesita para marcar en la lista única de
// Recurrentes cuáles encontró Movi sola. La garantía es la misma: `upsertDetected`/`applyExisting`
// (SubscriptionSync.kt) nunca generan esa clave, así que un re-scan no toca ni duplica un alta manual.
private fun manualMerchantKey(displayName: String): String {
    val normalized = displayName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return (MANUAL_SUB_PREFIX + normalized.ifBlank { "sub" }).take(80)
}

fun Route.subscriptionRoutes() {
    route("/api/subscriptions") {
        get {
            val uid = call.userId()
            call.respond(resultFor(uid))
        }

        post("/detect") {
            val uid = call.userId()
            runSubscriptionDetection(uid)
            call.respond(resultFor(uid))
        }

        // F38: alta manual — la creó el dueño, así que nace CONFIRMED (no CANDIDATE: no hay
        // nada que confirmar). `confidence` no aplica a un alta manual; HIGH es el valor menos
        // falso de los tres (no hay "sin confianza" en el enum) y no se lee para nada en este
        // camino (resultFor solo filtra por status).
        post {
            val uid = call.userId()
            val body = call.receive<CreateSubscriptionRequest>()
            val name = body.displayName.trim()
            if (name.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Falta el nombre")
            if (body.amount <= 0L) return@post call.respond(HttpStatusCode.BadRequest, "Falta el monto")
            if (body.currency != "COP" && body.currency != "USD") {
                return@post call.respond(HttpStatusCode.BadRequest, "Moneda inválida — usa COP o USD")
            }
            val day = body.dayOfMonth.coerceIn(1, 31)
            val key = manualMerchantKey(name)
            val now = System.currentTimeMillis()

            val newId = "sub_${UUID.randomUUID()}"
            val created = dbQuery {
                // El choque se mide contra lo que el dueño PUEDE VER. Una fila DISMISSED está
                // fuera de la lista y no se puede recuperar desde ninguna pantalla: si contara
                // como duplicado, «Ya tienes una suscripción llamada X» hablaría de algo
                // invisible e irrecuperable, y la única salida sería inventarle otro nombre.
                // Al re-crearla se reemplaza la fila muerta (ver el delete de abajo).
                val choque = Subscriptions.selectAll()
                    .where {
                        (Subscriptions.userId eq uid) and
                            (Subscriptions.merchantKey eq key) and
                            (Subscriptions.currency eq body.currency)
                    }
                    .map { it[Subscriptions.id] to SubStatus.valueOf(it[Subscriptions.status]) }
                if (choque.any { it.second != SubStatus.DISMISSED }) return@dbQuery false
                choque.forEach { (deadId, _) ->
                    Subscriptions.deleteWhere { (Subscriptions.id eq deadId) and (Subscriptions.userId eq uid) }
                }
                Subscriptions.insert {
                    it[id]          = newId
                    it[userId]      = uid
                    it[merchantKey] = key
                    it[displayName] = name
                    it[amount]      = body.amount
                    it[currency]    = body.currency
                    it[dayOfMonth]  = day
                    it[status]      = SubStatus.CONFIRMED.name
                    it[confidence]  = SubConfidence.HIGH.name
                    it[firstSeen]   = now
                    it[lastSeen]    = now
                    it[occurrences] = 0
                    it[accountId]   = null
                }
                true
            }
            if (!created) {
                return@post call.respond(HttpStatusCode.Conflict, "Ya tienes una suscripción llamada \"$name\" en ${body.currency}")
            }
            val row = dbQuery {
                Subscriptions.selectAll().where { Subscriptions.id eq newId }.first()
            }
            call.respond(HttpStatusCode.Created, row.toSubscription())
        }

        put("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receive<Subscription>()
            val updated = dbQuery {
                Subscriptions.update({ (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }) {
                    it[status]      = body.status.name
                    it[displayName] = body.displayName
                    it[amount]      = body.amount
                    it[dayOfMonth]  = body.dayOfMonth.coerceIn(1, 31)
                }
            }
            if (updated == 0) return@put call.respond(HttpStatusCode.NotFound)
            val row = dbQuery {
                Subscriptions.selectAll()
                    .where { (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }
                    .firstOrNull()
            } ?: return@put call.respond(HttpStatusCode.NotFound)  // borrado concurrente entre el update y el re-read
            call.respond(row.toSubscription())
        }

        delete("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
            val deleted = dbQuery {
                Subscriptions.deleteWhere { (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }
            }
            if (deleted == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun resultFor(uid: String): SubscriptionsResult {
    val subs = dbQuery {
        Subscriptions.selectAll()
            .where { Subscriptions.userId eq uid }
            .map { it.toSubscription() }
    }
    val active = subs.filter { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED }
    val needsFx = active.any { it.currency == "USD" }
    val rate = if (needsFx) FxRateService.usdToCop() else 0.0
    val total = active.sumOf { s ->
        when (s.currency) {
            "COP" -> s.amount
            "USD" -> (s.amount * rate).roundToLong()
            else  -> 0L
        }
    }
    // La tasa viaja junto al total: el cliente la necesita para restar del total una fila en
    // dólares (ver KDoc de SubscriptionsResult.usdToCop).
    return SubscriptionsResult(subscriptions = subs, monthlyTotalCop = total, usdToCop = rate)
}

private fun ResultRow.toSubscription() = Subscription(
    id          = this[Subscriptions.id],
    merchantKey = this[Subscriptions.merchantKey],
    displayName = this[Subscriptions.displayName],
    amount      = this[Subscriptions.amount],
    currency    = this[Subscriptions.currency],
    dayOfMonth  = this[Subscriptions.dayOfMonth],
    status      = SubStatus.valueOf(this[Subscriptions.status]),
    confidence  = SubConfidence.valueOf(this[Subscriptions.confidence]),
    firstSeen   = this[Subscriptions.firstSeen],
    lastSeen    = this[Subscriptions.lastSeen],
    occurrences = this[Subscriptions.occurrences],
    accountId   = this[Subscriptions.accountId],
)
