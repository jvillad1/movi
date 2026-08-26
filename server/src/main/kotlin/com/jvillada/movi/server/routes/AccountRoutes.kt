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
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.orphanedLegDescription
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

private val accountsLog = LoggerFactory.getLogger("AccountRoutes")

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
        //
        // Y antes que todo eso, lo único que NO se borra: la pata hermana de un traspaso, que no
        // es de esta cuenta (ver [desenlazarPatasHermanas]).
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

                // ANTES de borrar los eventos: si esta cuenta era una punta de algún traspaso,
                // la pata hermana vive en OTRA cuenta y se queda sin su mitad. Hay que dejarla
                // explicándose sola — ver [desenlazarPatasHermanas], que es donde está escrito
                // por qué se la suelta en vez de borrarla o de impedir el borrado.
                val sueltas = desenlazarPatasHermanas(uid, id)
                if (sueltas > 0) {
                    accountsLog.info(
                        "DELETE /api/accounts/$id: $sueltas pata(s) de traspaso quedaron sueltas " +
                            "en otras cuentas (transfer_id a null, categoría propia)",
                    )
                }

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
                // Ola 9 · D — las reglas recurrentes que apuntaban acá NO se borran: se sueltan.
                // Mismo criterio que la pata hermana de un traspaso ([desenlazarPatasHermanas]):
                // se suelta la referencia, no se destruye el hecho. «Arriendo, día 5, $1.800.000»
                // sigue siendo verdad aunque la cuenta de la que salía ya no esté en Movi, y
                // borrar el plan (con su recordatorio) porque cambió de banco sería una pérdida
                // silenciosa que el dueño no pidió. La cuenta es opcional en el modelo justamente
                // para que `null` se pueda mostrar sin drama.
                val reglasSueltas = RecurringRules.update({
                    (RecurringRules.userId eq uid) and (RecurringRules.accountId eq id)
                }) { it[RecurringRules.accountId] = null }
                if (reglasSueltas > 0) {
                    accountsLog.info(
                        "DELETE /api/accounts/$id: $reglasSueltas regla(s) recurrente(s) quedaron sin cuenta",
                    )
                }
                Accounts.deleteWhere { (Accounts.id eq id) and (Accounts.userId eq uid) }
                true
            }
            if (!deleted) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * **La otra mitad de cada traspaso que tocaba la cuenta [accountId] no puede quedar colgando.**
 *
 * El escenario: el dueño traspasa $1.000.000 de Bancolombia a Nequi, después cierra Nequi y la
 * borra. La pata de Nequi se va con la cuenta; la de Bancolombia sobrevive, con la categoría
 * reservada «Traspaso», la descripción «Traspaso a Nequi» y un `transfer_id` cuya otra mitad ya
 * no existe. Esa fila era imposible de entender (apunta a una cuenta que no se puede abrir) e
 * imposible de arreglar: `PUT /api/events/{id}/category` rechaza recategorizar cualquier pata de
 * traspaso, así que el dueño no tenía forma de sacarla de ahí.
 *
 * **Qué se hace y por qué esto y no otra cosa:**
 *
 * - **No se borra la pata que queda, y no se le toca el monto.** La plata SÍ salió de
 *   Bancolombia; borrarla devolvería $1.000.000 a un saldo que en el banco no los tiene. Ese es
 *   el único invariante que no se negocia acá.
 * - **No se bloquea el borrado.** La política de esta ruta ya estaba escrita antes de que
 *   existieran los traspasos: borrar una cuenta borra en cascada todo lo suyo, sin preguntar y
 *   sin excepciones. Inventarle una excepción a los traspasos sería una segunda política para el
 *   mismo botón. El aviso —«N de estos movimientos son traspasos»— va donde tiene que ir: en la
 *   hoja de confirmación, antes (ver `DeleteAccountSheet`).
 * - **Se la suelta del par** (`transfer_id = NULL`): ya no es media pareja, es un movimiento
 *   suelto, y nada tiene que salir a buscarle una hermana que no existe (la anulación en cascada
 *   de `POST /api/events/{id}/void`, [movementCount], `collapseTransfers`).
 * - **Se la saca de la categoría reservada** hacia una propia que dice qué pasó
 *   ([ORPHANED_LEG_CATEGORY]; ahí está escrito por qué no es «Otros» ni una por dirección), y la
 *   descripción nombra la cuenta que ya no está ([orphanedLegDescription]). Sí, eso hace que ese movimiento
 *   vuelva a contar como gasto (o ingreso) del mes en que ocurrió — y es lo correcto: con la otra
 *   cuenta fuera de Movi, esa plata efectivamente salió del perímetro que la app lleva. Es además
 *   el mismo criterio que ya aplicaba `StatementRoutes` con una fila de extracto etiquetada
 *   «Traspaso» sin hermana.
 *
 * Corre dentro de la misma transacción que el resto del borrado.
 *
 * @return cuántas patas hermanas quedaron sueltas
 */
private fun Transaction.desenlazarPatasHermanas(uid: String, accountId: String): Int {
    val transferIds = Events
        .select(Events.transferId)
        .where {
            (Events.userId eq uid) and (Events.accountId eq accountId) and Events.transferId.isNotNull()
        }
        .mapNotNull { it[Events.transferId] }
        .toSet()
    if (transferIds.isEmpty()) return 0

    // Solo las patas de OTRAS cuentas: las de esta se van con la cuenta un par de líneas más
    // abajo, y reescribirlas sería trabajo tirado a la basura.
    val hermanas = Events.selectAll()
        .where {
            (Events.userId eq uid) and
                (Events.transferId inList transferIds.toList()) and
                (Events.accountId neq accountId)
        }
        .map { it[Events.id] to it[Events.description] }

    hermanas.forEach { (eventId, description) ->
        Events.update({ (Events.id eq eventId) and (Events.userId eq uid) }) {
            it[Events.transferId] = null
            // La categoría es la misma para las dos direcciones y no tiene tipo: ver
            // [ORPHANED_LEG_CATEGORY], que explica por qué no es «Otros» ni una por dirección.
            it[Events.category]    = ORPHANED_LEG_CATEGORY
            it[Events.description] = orphanedLegDescription(description)
        }
    }
    return hermanas.size
}
