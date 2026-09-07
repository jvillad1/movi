package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.subscriptions.runSubscriptionDetection
import com.jvillada.movi.shared.model.CreateSubscriptionRequest
import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.PeriodicidadDeCobro
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.montoMensualEquivalente
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
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
/**
 * El mismo `Json` que configura `configureSerialization`, para el PUT que deserializa a mano
 * desde el JSON crudo (ver ahí el porqué). **`ignoreUnknownKeys` no es opcional**: hasta la Ola
 * 16 ese cuerpo lo leía `call.receive<Subscription>()`, o sea el plugin, que lo trae puesto.
 * Decodificar con el `Json` por defecto habría vuelto ESTRICTA una ruta que era tolerante, y en
 * un proyecto donde el APK se entrega a mano eso significa que un cliente más nuevo que el
 * server —con un campo que el server todavía no conoce— se llevaría un 400 donde antes guardaba
 * bien.
 */
private val jsonDelWire = Json { isLenient = true; ignoreUnknownKeys = true }

private fun manualMerchantKey(displayName: String): String {
    val normalized = displayName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return (MANUAL_SUB_PREFIX + normalized.ifBlank { "sub" }).take(80)
}

/**
 * **¿Puede una fila con esta `merchantKey` quedar en este estado?** La guarda del `PUT /{id}`.
 *
 * [SubStatus.AUTO] y [SubStatus.CANDIDATE] son estados del DETECTOR: los escribe
 * `SubscriptionSync` en un barrido, y significan «esto lo encontró Movi» —AUTO además
 * arrastra «y la activó sola», que es la herencia de antes de F39 que la UI marca aparte—.
 * Un alta manual nace [SubStatus.CONFIRMED] (ver el POST de acá abajo) y el detector nunca
 * produce una clave `manual_*` (ver [MANUAL_SUB_PREFIX]), así que una fila manual en uno de
 * esos dos estados es una contradicción: no hay barrido que la haya puesto ahí.
 *
 * Hoy ningún cliente manda esa transición —«confirmar» escribe CONFIRMED/DISMISSED sobre
 * candidatas del detector, y «Quitar» sobre una manual BORRA en vez de marcar DISMISSED (ver
 * `quitarBorraLaSuscripcion`)—, pero eso es una invariante del CLIENTE y este endpoint recibe
 * el `Subscription` entero tal cual viene del body. Con la contradicción escrita en la DB,
 * `origenDeSuscripcion` lee el prefijo primero y la fila seguiría diciendo «la escribiste tú»
 * mientras «Quitar» la borra — el estado quedaría mintiendo en silencio, sin ninguna pantalla
 * que lo delate.
 *
 * Lo que a propósito NO restringe: las cuatro transiciones sobre una fila del detector. Una
 * detectada puede ir a CONFIRMED (el dueño la aceptó), a DISMISSED («no me la propongas más»,
 * que es lo que respeta el re-scan), y también volver a CANDIDATE o AUTO — el barrido las
 * escribe de todos modos, así que prohibirlas por HTTP no protegería nada y sí rompería
 * `confirmarCandidata` si alguna vez ofrece deshacer.
 */
private fun estadoPermitido(merchantKey: String, nuevo: SubStatus): Boolean =
    !merchantKey.startsWith(MANUAL_SUB_PREFIX) ||
        nuevo == SubStatus.CONFIRMED || nuevo == SubStatus.DISMISSED

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
                    // Ola 17 — **acá había un `null` fijo**, y era todo el agujero: la hoja ya
                    // le preguntaba al dueño de qué cuenta sale el cobro, y esta línea tiraba la
                    // respuesta. Una detectada sí guardaba su cuenta (SubscriptionSync la copia
                    // del evento), así que Movi sabía con qué tarjeta se paga lo que encontró
                    // sola y no sabía con cuál se paga lo que el dueño le escribió a mano.
                    //
                    // Se filtra por dueño y no se rechaza: la cuenta es opcional (ver
                    // [Subscription.accountId]), así que un id ajeno o inexistente guarda `null`
                    // y el alta sigue. Mismo criterio, y misma función, que el alta de una regla
                    // recurrente.
                    it[accountId]   = accountIdIfOwned(uid, body.accountId)
                    // El cobro real va tal cual en `amount`; lo que decide si eso es plata de
                    // cada mes o de una vez al año es esta columna. No hay nada que validar: el
                    // enum solo tiene dos valores y un cuerpo sin la clave llega MENSUAL, que es
                    // lo único que sabía anotar la hoja anterior a la Ola 16.
                    it[periodicidad] = body.periodicidad.name
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
            // Se recibe el JSON CRUDO además del objeto, para poder distinguir «el cliente mandó
            // este campo» de «el cliente no lo conoce» — misma técnica y mismo motivo que el
            // `PUT /api/credits/{id}` (ver su comentario largo).
            //
            // Acá el campo en juego es `periodicidad`. El APK se entrega a mano por Drive, así
            // que un cliente anterior a la Ola 16 sigue vivo y sigue mandando cuerpos sin esa
            // clave: al deserializar, el default la vuelve MENSUAL, y como este update pisa las
            // columnas que toca, «Quitar» un HBO Max desde el teléfono viejo lo dejaría
            // guardado como un cobro de $369.900 TODOS LOS MESES. El dueño no habría cambiado
            // nada — el número plausible aparece solo, que es la peor forma de un error de plata.
            //
            // La distinción no se puede hacer con el objeto deserializado (ahí «ausente» y «su
            // default» son lo mismo) ni con un valor centinela, que sería un valor legítimo.
            // Mirar las claves del JSON es exacto y no inventa nada.
            val crudo = call.receive<JsonObject>()
            val body = jsonDelWire.decodeFromJsonElement<Subscription>(crudo)
            val mandoLaPeriodicidad = "periodicidad" in crudo

            // La clave que manda el cliente en el body NO se lee para nada: este UPDATE ni
            // siquiera escribe `merchantKey`, así que el origen de la fila lo dice la clave
            // GUARDADA. Si se validara contra la del body, mandar `merchantKey: "netflix"`
            // sobre una fila `manual_*` saltearía la guarda de abajo.
            val stored = dbQuery {
                Subscriptions.selectAll()
                    .where { (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }
                    .firstOrNull()
            } ?: return@put call.respond(HttpStatusCode.NotFound)

            if (!estadoPermitido(stored[Subscriptions.merchantKey], body.status)) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    "Una suscripción que escribiste tú solo puede quedar activa o quitada",
                )
            }

            val updated = dbQuery {
                Subscriptions.update({ (Subscriptions.id eq id) and (Subscriptions.userId eq uid) }) {
                    it[status]      = body.status.name
                    it[displayName] = body.displayName
                    it[amount]      = body.amount
                    it[dayOfMonth]  = body.dayOfMonth.coerceIn(1, 31)
                    if (mandoLaPeriodicidad) it[periodicidad] = body.periodicidad.name
                }
            }
            if (updated == 0) return@put call.respond(HttpStatusCode.NotFound)  // borrado concurrente entre la lectura y el update
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
        // PRORRATEAR PRIMERO, convertir después — en ese orden, y el cliente hace lo mismo
        // (`copDeSuscripcion`, RecurrentesLogic.kt). Al revés el redondeo del medio cambiaría el
        // resultado, y dos totales que dicen contar lo mismo se separarían por pesos.
        //
        // La división vive en `:core` ([montoMensualEquivalente]) justamente para que esta suma
        // y la del cliente no puedan discrepar. Para una suscripción MENSUAL —o sea, todas las
        // que existían antes de la Ola 16— devuelve `amount` sin tocarlo.
        val mensual = s.montoMensualEquivalente()
        when (s.currency) {
            "COP" -> mensual
            "USD" -> (mensual * rate).roundToLong()
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
    // Una fila anterior a la Ola 16 trae 'MENSUAL' por el default de la columna, así que el
    // `runCatching` no cubre ninguna migración pendiente — cubre que un valor imposible en la
    // base (una escritura a mano, un enum que se saque en el futuro) no tumbe la lista entera
    // con un 500. Caer en MENSUAL es lo único razonable: es lo que era todo antes de existir
    // esta columna, y equivale a leer la fila como se leía siempre.
    periodicidad = runCatching { PeriodicidadDeCobro.valueOf(this[Subscriptions.periodicidad]) }
        .getOrDefault(PeriodicidadDeCobro.MENSUAL),
)
