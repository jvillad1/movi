package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.enrichWith
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

fun Route.accountRoutes() {
    route("/api/accounts") {
        get {
            val uid = call.userId()
            val rate = FxRateService.usdToCop()
            val rows = dbQuery {
                Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() }
            }
            val byAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }
            val enriched = rows.map { enrichWith(it, byAccount[it.id] ?: emptyList(), rate) }
            call.respond(enriched)
        }

        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val base = dbQuery {
                Accounts.selectAll()
                    .where { (Accounts.id eq id) and (Accounts.userId eq uid) }
                    .firstOrNull()?.toAccount()
            } ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(enrichWith(base, loadNonVoidedEvents(uid, base.id), FxRateService.usdToCop()))
        }

        // Solo crea la fila de la cuenta. NO fabrica un evento de apertura a partir de
        // `account.balance` — eso era lo que hacía esta ruta hasta la Ola 1b, y era la mitad
        // server de un doble conteo: una cuenta creada offline sincroniza su `balance` crudo vía
        // `SyncEngine.syncAccounts`, y si esta ruta lo hubiera vuelto a convertir en evento acá,
        // el ingreso/gasto real que el dueño ya había anotado (y que `syncEvents` empuja justo
        // después) se habría sumado ENCIMA de esa apertura fabricada. Ahora el evento de apertura
        // lo crea el cliente, explícito y una sola vez (ver `openingEventFor` en :core y su único
        // call site, `CreateAccountSheet.kt`) — esta ruta ni sabe que existe. La columna cruda
        // `accounts.balance` no se lee para nada más: el balance que ve el cliente sale siempre
        // de `enrichWith`/`computeBalances`, derivado de los eventos reales.
        post {
            val body = call.receive<Account>()
            val uid = call.userId()
            val account = body.copy(
                id = body.id.ifBlank { "acc_${System.currentTimeMillis()}" }
            )
            dbQuery {
                Accounts.insert {
                    it[id]       = account.id
                    it[userId]   = uid
                    it[name]     = account.name
                    it[type]     = account.type.name
                    it[balance]  = account.balance
                    it[currency] = account.currency
                }
            }
            call.respond(HttpStatusCode.Created, account)
        }

        // F55: borrar una cuenta no existía en ninguna capa — lo único que la app ofrecía era
        // anular el "Saldo inicial", que deja la cuenta en $0 pero SIGUE existiendo. Acá sí se
        // borra todo lo que le pertenece, en UNA transacción (dbQuery ya envuelve el bloque en
        // una transacción de Exposed): primero lo que referencia a los eventos de la cuenta
        // (dismissals de pago de tarjeta, anulaciones), después los eventos mismos, después los
        // términos de crédito si es un LOAN, y al final la cuenta. Ese orden importa porque
        // credit_terms/void_events/card_payment_dismissals no tienen FK con ON DELETE CASCADE —
        // si se borrara la cuenta primero y algo de abajo fallara, quedarían filas huérfanas
        // apuntando a una cuenta que ya no existe.
        delete("/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
            val uid = call.userId()
            val deleted = dbQuery {
                val exists = Accounts.selectAll()
                    .where { (Accounts.id eq id) and (Accounts.userId eq uid) }
                    .firstOrNull() != null
                if (!exists) return@dbQuery false

                val eventIds = Events.selectAll()
                    .where { (Events.accountId eq id) and (Events.userId eq uid) }
                    .map { it[Events.id] }

                if (eventIds.isNotEmpty()) {
                    CardPaymentDismissals.deleteWhere {
                        (CardPaymentDismissals.userId eq uid) and (CardPaymentDismissals.eventId inList eventIds)
                    }
                    VoidEvents.deleteWhere {
                        (VoidEvents.userId eq uid) and (VoidEvents.originalEventId inList eventIds)
                    }
                }
                Events.deleteWhere { (Events.accountId eq id) and (Events.userId eq uid) }
                Credits.deleteWhere { (Credits.accountId eq id) and (Credits.userId eq uid) }
                // Y los términos de tarjeta (card_terms nació en esta misma ola, después de que
                // este DELETE se escribiera): sin esto, borrar una tarjeta dejaba la fila de
                // términos huérfana para siempre — justo lo que este comentario promete que no pasa.
                Cards.deleteWhere { (Cards.accountId eq id) and (Cards.userId eq uid) }
                // Y las metas ancladas a esta cuenta (goals nació en la Ola 6, después que este
                // DELETE): una meta cuyo «ahorrado» sale del saldo de una cuenta borrada no
                // significa nada — se va con ella.
                Goals.deleteWhere { (Goals.accountId eq id) and (Goals.userId eq uid) }
                Accounts.deleteWhere { (Accounts.id eq id) and (Accounts.userId eq uid) }
                true
            }
            if (!deleted) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

