package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.conLoQueSeLeMando
import com.jvillada.movi.shared.model.nombreDeLaCuentaPropiaConEseNumero
import com.jvillada.movi.shared.model.mensajeDeNumeroPropio
import com.jvillada.movi.shared.model.mensajeDeLlaveRepetida
import com.jvillada.movi.shared.model.mensajeDeNumeroRepetido
import com.jvillada.movi.shared.model.llaveNormalizada
import com.jvillada.movi.shared.model.normalizarLlave
import com.jvillada.movi.shared.model.movimientosHaciaElDestino
import com.jvillada.movi.shared.model.rechazoDelDestino
import com.jvillada.movi.shared.model.soloLosDigitos
import com.jvillada.movi.shared.model.totalesHaciaElDestino
import com.jvillada.movi.shared.model.ultimosCuatro
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
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * **«Cuentas de otros»** — el registro de cuentas ajenas del dueño (`/api/destinos`).
 *
 * Ver `DestinoConocido` en `:core` para el diseño: qué es, por qué no es una `Account`, y cuáles son
 * las dos cosas que hace (ponerle nombre a lo que el banco manda sin nombre, y juntar lo que fue
 * para allá).
 *
 * Acá viven las tres guardas del alta, y las tres son de plata:
 *
 * 1. **La forma del dato** (400) — `rechazoDelDestino`, la misma función que usa la hoja, para que
 *    el server y la pantalla digan exactamente lo mismo.
 * 2. **El número no puede ser de una cuenta suya** (422) — `cuentaPropiaConEseNumero`. Sin esto, un
 *    SMS de un retiro de su propia Fiducuenta se propondría con el nombre de otra persona y después
 *    se contaría en «lo que le mandé».
 * 3. **Dos destinos no pueden compartir los últimos cuatro dígitos** (409) — si los comparten,
 *    `destinoQueNombra` no elige ninguno (empate) y los dos se llevarían los mismos movimientos.
 *    Lo mismo con la llave (29-sep), comparada exacta.
 *
 * **La llave y el APK viejo.** Un destino puede conocerse por su número, por su llave, o por los
 * dos; con solo la llave, `numero` va vacío. El APK instalado no sabe de llaves: su `POST` llega
 * sin el campo (y crea un destino con número, como siempre) y su `PUT` también — por eso en un
 * `PUT` una llave ausente (`null`) **no toca** la guardada, y solo `""` la borra.
 *
 * Todo con `call.userId()`, como el resto del server: quien pide el destino de otro recibe «no
 * existe», no «no puedes».
 */
fun Route.destinoRoutes() {
    route("/api/destinos") {
        get {
            val uid = call.userId()
            val destinos = dbQuery { destinosDe(uid) }
            if (destinos.isEmpty()) return@get call.respond(emptyList<DestinoConocido>())
            // Una sola lectura de movimientos para todos los destinos: el cruce es en memoria (ver
            // `vaHaciaElDestino`), y una consulta por destino sería N+1 sobre la tabla más grande.
            val eventos = loadNonVoidedEvents(uid)
            // El período en curso del DUEÑO (su corte, sus inicios propios): es lo que lee la
            // tarjeta «Cuentas de otros» de Patrimonio. Ver `DestinoConocido.totalesDelPeriodo`.
            val ajustes = ajustesDePeriodoDe(uid)
            val ahora = System.currentTimeMillis()
            call.respond(destinos.map { conLoQueSeLeMando(it, eventos, ajustes, ahora) })
        }

        post {
            val uid = call.userId()
            val body = call.receive<DestinoConocido>()
            val nombre = body.nombre.trim()
            val numero = soloLosDigitos(body.numero)
            val deQuien = body.deQuien?.trim()?.ifBlank { null }
            val llave = body.llave?.let(::normalizarLlave)?.ifBlank { null }

            rechazoDelDestino(nombre, numero, deQuien, llave)?.let { motivo ->
                return@post call.respond(HttpStatusCode.BadRequest, motivo)
            }
            rechazoPorLoQueYaTiene(uid, numero, llave, yaExiste = null)?.let { (status, motivo) ->
                return@post call.respond(status, motivo)
            }

            val destino = DestinoConocido(
                id = "dst_${UUID.randomUUID()}",
                nombre = nombre,
                numero = numero,
                deQuien = deQuien,
                llave = llave,
            )
            dbQuery {
                KnownDestinations.insert {
                    it[id]        = destino.id
                    it[userId]    = uid
                    it[this.nombre]  = destino.nombre
                    it[this.numero]  = destino.numero
                    it[this.deQuien] = destino.deQuien
                    it[this.llave]   = destino.llave
                    it[createdAt] = System.currentTimeMillis()
                }
            }
            // Recién creado ya puede tener movimientos: el dueño lo registra DESPUÉS de haberle
            // transferido, que es literalmente el caso que trajo esta feature.
            call.respond(
                HttpStatusCode.Created,
                conLoQueSeLeMando(destino, loadNonVoidedEvents(uid), ajustesDePeriodoDe(uid), System.currentTimeMillis()),
            )
        }

        put("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Falta el id")
            val body = call.receive<DestinoConocido>()
            val nombre = body.nombre.trim()
            val numero = soloLosDigitos(body.numero)
            val deQuien = body.deQuien?.trim()?.ifBlank { null }
            val guardado = dbQuery {
                KnownDestinations.selectAll()
                    .where { (KnownDestinations.id eq id) and (KnownDestinations.userId eq uid) }
                    .firstOrNull()?.toDestino()
            } ?: return@put call.respond(HttpStatusCode.NotFound)
            // `null` = el cliente no sabe de llaves (el APK instalado): se queda la que había.
            val llave = if (body.llave == null) guardado.llave else normalizarLlave(body.llave!!).ifBlank { null }

            rechazoDelDestino(nombre, numero, deQuien, llave)?.let { motivo ->
                return@put call.respond(HttpStatusCode.BadRequest, motivo)
            }
            rechazoPorLoQueYaTiene(uid, numero, llave, yaExiste = id)?.let { (status, motivo) ->
                return@put call.respond(status, motivo)
            }

            val actualizadas = dbQuery {
                KnownDestinations.update({ (KnownDestinations.id eq id) and (KnownDestinations.userId eq uid) }) {
                    it[this.nombre]  = nombre
                    it[this.numero]  = numero
                    it[this.deQuien] = deQuien
                    it[this.llave]   = llave
                }
            }
            if (actualizadas == 0) return@put call.respond(HttpStatusCode.NotFound)
            val destino = DestinoConocido(id = id, nombre = nombre, numero = numero, deQuien = deQuien, llave = llave)
            call.respond(conLoQueSeLeMando(destino, loadNonVoidedEvents(uid), ajustesDePeriodoDe(uid), System.currentTimeMillis()))
        }

        delete("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Falta el id")
            val borradas = dbQuery {
                // Ola V — las reglas recurrentes que apuntaban acá NO se borran con el destino: se
                // sueltan, mismo criterio que ya usa `AccountRoutes` con `RecurringRule.accountId`
                // (ver su KDoc). «Tía Caro, día 1, $100.000» sigue siendo un plan real aunque el
                // dueño borre el registro de «Caro» — lo único que se pierde es la seña extra del
                // número de cuenta en el emparejador, no la regla.
                RecurringRules.update({
                    (RecurringRules.userId eq uid) and (RecurringRules.destinoConocidoId eq id)
                }) { it[RecurringRules.destinoConocidoId] = null }
                KnownDestinations.deleteWhere { (KnownDestinations.id eq id) and (KnownDestinations.userId eq uid) }
            }
            // Borrar un destino **no toca un solo movimiento**: lo que se olvida es el nombre y la
            // agrupación, no la plata. Los movimientos siguen ahí, con el nombre que tengan.
            if (borradas == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.NoContent)
        }

        get("/{id}/movimientos") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta el id")
            val destino = dbQuery {
                KnownDestinations.selectAll()
                    .where { (KnownDestinations.id eq id) and (KnownDestinations.userId eq uid) }
                    .firstOrNull()?.toDestino()
            } ?: return@get call.respond(HttpStatusCode.NotFound)

            val suyos = movimientosHaciaElDestino(destino, loadNonVoidedEvents(uid))
            call.respond(
                MovimientosDelDestino(
                    destino = destino.copy(totales = totalesHaciaElDestino(suyos), cuantos = suyos.size),
                    movimientos = suyos,
                ),
            )
        }
    }
}

/**
 * Los destinos del usuario, **alfabéticos**. Sin `ORDER BY` esta lista salía en el orden físico de
 * la tabla —el que un UPDATE cambia sin avisar— y podía verse distinta entre dos lecturas: el mismo
 * defecto que tenían la bandeja de SMS y las metas.
 */
private fun org.jetbrains.exposed.sql.Transaction.destinosDe(uid: String): List<DestinoConocido> =
    KnownDestinations.selectAll()
        .where { KnownDestinations.userId eq uid }
        .orderBy(KnownDestinations.nombre to SortOrder.ASC)
        .map { it.toDestino() }

/**
 * Las guardas que necesitan mirar lo que el dueño ya tiene: sus cuentas y sus otros destinos.
 * Devuelve el par (código, texto) del rechazo, o `null` si el número y la llave se pueden usar.
 *
 * [yaExiste] es el id del destino que se está editando, para que guardarlo sin cambiarle el número
 * no choque consigo mismo. Un destino solo con llave ([numero] vacío) no pasa por las guardas del
 * número: no nombra ninguna cuenta.
 */
private suspend fun rechazoPorLoQueYaTiene(
    uid: String,
    numero: String,
    llave: String?,
    yaExiste: String?,
): Pair<HttpStatusCode, String>? {
    val otros = dbQuery { destinosDe(uid) }.filter { it.id != yaExiste }
    if (numero.isNotEmpty()) {
        val nombresDeSusCuentas = dbQuery {
            Accounts.selectAll().where { Accounts.userId eq uid }.map { it[Accounts.name] }
        }
        nombreDeLaCuentaPropiaConEseNumero(numero, nombresDeSusCuentas)?.let { propia ->
            return HttpStatusCode.UnprocessableEntity to mensajeDeNumeroPropio(propia)
        }
        val cola = ultimosCuatro(numero)
        otros.firstOrNull { cola != null && ultimosCuatro(it.numero) == cola }?.let { choca ->
            return HttpStatusCode.Conflict to mensajeDeNumeroRepetido(choca.nombre)
        }
    }
    if (llave != null) {
        otros.firstOrNull { it.llaveNormalizada() == llave }?.let { choca ->
            return HttpStatusCode.Conflict to mensajeDeLlaveRepetida(choca.nombre)
        }
    }
    return null
}

private fun ResultRow.toDestino() = DestinoConocido(
    id      = this[KnownDestinations.id],
    nombre  = this[KnownDestinations.nombre],
    numero  = this[KnownDestinations.numero],
    deQuien = this[KnownDestinations.deQuien],
    llave   = this[KnownDestinations.llave],
)
