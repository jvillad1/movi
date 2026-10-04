package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.DestinosDescartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.aDestino
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.identificadoresComoJson
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.sms.parseSmsTime
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.DescartarSugerido
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DestinoSugerido
import com.jvillada.movi.shared.model.DestinosDescartados as DescartadosDelDueno
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.LLAVE_DEMASIADO_CORTA
import com.jvillada.movi.shared.model.LLAVE_DEMASIADO_LARGA
import com.jvillada.movi.shared.model.MAX_DIGITOS_DEL_NUMERO
import com.jvillada.movi.shared.model.MAX_LARGO_DE_LA_LLAVE
import com.jvillada.movi.shared.model.MIN_DIGITOS_DEL_NUMERO
import com.jvillada.movi.shared.model.MIN_LARGO_DE_LA_LLAVE
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.NUMERO_DEMASIADO_CORTO
import com.jvillada.movi.shared.model.NUMERO_DEMASIADO_LARGO
import com.jvillada.movi.shared.model.RenombradosDelDestino
import com.jvillada.movi.shared.model.RenombrarMovimientos
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.clave
import com.jvillada.movi.shared.model.colasPropiasEn
import com.jvillada.movi.shared.model.conIdentificadores
import com.jvillada.movi.shared.model.conLoQueSeLeMando
import com.jvillada.movi.shared.model.destinosSugeridos
import com.jvillada.movi.shared.model.esUnNombreIlegible
import com.jvillada.movi.shared.model.mismoIdentificador
import com.jvillada.movi.shared.model.movimientosDesdeElDestino
import com.jvillada.movi.shared.model.movimientosHaciaElDestino
import com.jvillada.movi.shared.model.nombreDeLaCuentaPropiaConEseNumero
import com.jvillada.movi.shared.model.nombreDesdeElDestino
import com.jvillada.movi.shared.model.nombreHaciaElDestino
import com.jvillada.movi.shared.model.nombresPropiosEn
import com.jvillada.movi.shared.model.mensajeDeNumeroPropio
import com.jvillada.movi.shared.model.mensajeDeLlaveRepetida
import com.jvillada.movi.shared.model.mensajeDeNumeroRepetido
import com.jvillada.movi.shared.model.normalizado
import com.jvillada.movi.shared.model.normalizarLlave
import com.jvillada.movi.shared.model.rastroDeUnAviso
import com.jvillada.movi.shared.model.rastroDeUnMovimiento
import com.jvillada.movi.shared.model.rechazoDelDestino
import com.jvillada.movi.shared.model.soloLosDigitos
import com.jvillada.movi.shared.model.todosLosIdentificadores
import com.jvillada.movi.shared.model.totalesHaciaElDestino
import com.jvillada.movi.shared.model.vaHaciaElDestino
import com.jvillada.movi.shared.model.vieneDelDestino
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.math.roundToLong

/**
 * **«Cuentas de otros»** — el registro de cuentas ajenas del dueño (`/api/destinos`).
 *
 * Ver `DestinoConocido` en `:core` para el diseño: qué es, por qué no es una `Account`, y cuáles son
 * las dos cosas que hace (ponerle nombre a lo que el banco manda sin nombre, y juntar lo que fue
 * para allá). Y `TercerosSugeridos.kt` para lo que Movi encuentra solo.
 *
 * Acá viven las tres guardas del alta, y las tres son de plata:
 *
 * 1. **La forma del dato** (400) — `rechazoDelDestino`, la misma función que usa la hoja, para que
 *    el server y la pantalla digan exactamente lo mismo.
 * 2. **El número no puede ser de una cuenta suya** (422) — `cuentaPropiaConEseNumero`. Sin esto, un
 *    SMS de un retiro de su propia Fiducuenta se propondría con el nombre de otra persona y después
 *    se contaría en «lo que le mandé».
 * 3. **Dos destinos no pueden compartir un identificador** (409) — un número por sus últimos cuatro
 *    dígitos, una llave exacta. Si lo comparten, `destinoQueNombra` no elige ninguno (empate) y los
 *    dos se llevarían los mismos movimientos. Desde el 4-oct se mira **cada** identificador.
 *
 * **Varios identificadores y el APK viejo** (4-oct-2026). Un tercero tiene una lista de
 * identificadores (`identificadores`, una columna JSON); `numero` y `llave` siguen llenos con el
 * primero de cada clase. El APK instalado no conoce la lista: su `POST` llega sin ella (y crea un
 * destino con su número, como siempre) y su `PUT` también — entonces el número o la llave que
 * cambió reemplaza al viejo en la lista, y todo lo demás de la lista se conserva. En un `PUT` una
 * llave ausente (`null`) **no toca** la guardada, y solo `""` la borra.
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
            val deQuien = body.deQuien?.trim()?.ifBlank { null }
            // Un cliente nuevo manda la lista; el APK instalado, el número y la llave.
            val pedidos = body.conIdentificadores(
                body.identificadores.ifEmpty {
                    listOfNotNull(
                        soloLosDigitos(body.numero).takeIf { it.isNotEmpty() }
                            ?.let { IdentificadorDelDestino(TipoDeIdentificador.NUMERO, it) },
                        body.llave?.let(::normalizarLlave)?.ifBlank { null }
                            ?.let { IdentificadorDelDestino(TipoDeIdentificador.LLAVE, it) },
                    )
                },
            ).todosLosIdentificadores()

            rechazoDeLaForma(nombre, deQuien, pedidos)?.let { motivo ->
                return@post call.respond(HttpStatusCode.BadRequest, motivo)
            }
            rechazoPorLoQueYaTiene(uid, pedidos, yaExiste = null)?.let { (status, motivo) ->
                return@post call.respond(status, motivo)
            }

            val destino = DestinoConocido(
                id = "dst_${UUID.randomUUID()}",
                nombre = nombre,
                numero = "",
                deQuien = deQuien,
                tipo = body.tipo,
            ).conIdentificadores(pedidos)
            dbQuery {
                KnownDestinations.insert {
                    it[id]        = destino.id
                    it[userId]    = uid
                    it[this.nombre]  = destino.nombre
                    it[this.numero]  = destino.numero
                    it[this.deQuien] = destino.deQuien
                    it[this.llave]   = destino.llave
                    it[identificadores] = identificadoresComoJson(destino.identificadores)
                    it[tipo] = destino.tipo?.name
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
            val deQuien = body.deQuien?.trim()?.ifBlank { null }
            val guardado = dbQuery { destinoDe(uid, id) } ?: return@put call.respond(HttpStatusCode.NotFound)

            val pedidos = if (body.identificadores.isNotEmpty()) {
                body.conIdentificadores(body.identificadores).todosLosIdentificadores()
            } else {
                loQueCambioUnClienteViejo(guardado, body)
            }

            rechazoDeLaForma(nombre, deQuien, pedidos)?.let { motivo ->
                return@put call.respond(HttpStatusCode.BadRequest, motivo)
            }
            rechazoPorLoQueYaTiene(uid, pedidos, yaExiste = id)?.let { (status, motivo) ->
                return@put call.respond(status, motivo)
            }

            val destino = guardado.copy(
                nombre = nombre,
                deQuien = deQuien,
                // `null` = el cliente no sabe de tipos (el APK instalado): se queda el que había.
                tipo = body.tipo ?: guardado.tipo,
            ).conIdentificadores(pedidos)
            val actualizadas = dbQuery { guardarDestino(uid, destino) }
            if (actualizadas == 0) return@put call.respond(HttpStatusCode.NotFound)
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
            val destino = dbQuery { destinoDe(uid, id) } ?: return@get call.respond(HttpStatusCode.NotFound)

            val eventos = loadNonVoidedEvents(uid)
            val suyos = movimientosHaciaElDestino(destino, eventos)
            val conTotales = conLoQueSeLeMando(destino, eventos, ajustesDePeriodoDe(uid), System.currentTimeMillis())
            call.respond(
                MovimientosDelDestino(
                    destino = conTotales.copy(totales = totalesHaciaElDestino(suyos), cuantos = suyos.size),
                    movimientos = suyos,
                    recibidos = movimientosDesdeElDestino(destino, eventos),
                ),
            )
        }

        // ── Un identificador más, uno menos ─────────────────────────────────────

        /**
         * **«Es de un tercero que ya tengo» / «¿Es Caro? Sí» / «Mover a otra persona»**: sumarle un
         * identificador a este tercero. Nunca crea uno nuevo. Si otro tercero ya lo tenía, 409 —
         * salvo con `mover`, que se lo quita a ese (si no era lo único que lo identificaba).
         */
        post("/{id}/identificadores") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el id")
            val pedido = call.receive<AgregarIdentificador>()
            val nuevo = pedido.identificador.normalizado()
            rechazoDeUnIdentificador(nuevo)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }

            val resultado: Pair<HttpStatusCode, Any> = dbQuery {
                val destino = destinoDe(uid, id) ?: return@dbQuery HttpStatusCode.NotFound to "No existe"
                if (nuevo.tipo == TipoDeIdentificador.NUMERO) {
                    val nombres = Accounts.selectAll().where { Accounts.userId eq uid }.map { it[Accounts.name] }
                    nombreDeLaCuentaPropiaConEseNumero(nuevo.valor, nombres)?.let { propia ->
                        return@dbQuery HttpStatusCode.UnprocessableEntity to mensajeDeNumeroPropio(propia)
                    }
                }
                val otro = destinosDe(uid).filter { it.id != id }
                    .firstOrNull { d -> d.todosLosIdentificadores().any { mismoIdentificador(it, nuevo) } }
                if (otro != null) {
                    if (!pedido.mover) {
                        val mensaje = if (nuevo.tipo == TipoDeIdentificador.NUMERO) mensajeDeNumeroRepetido(otro.nombre)
                        else mensajeDeLlaveRepetida(otro.nombre)
                        return@dbQuery HttpStatusCode.Conflict to mensaje
                    }
                    val leQueda = otro.todosLosIdentificadores().filterNot { mismoIdentificador(it, nuevo) }
                    if (leQueda.isEmpty()) {
                        return@dbQuery HttpStatusCode.Conflict to mensajeDeUnicoIdentificador(otro.nombre)
                    }
                    guardarDestino(uid, otro.conIdentificadores(leQueda))
                }
                if (destino.todosLosIdentificadores().any { mismoIdentificador(it, nuevo) }) {
                    return@dbQuery HttpStatusCode.OK to destino
                }
                val actualizado = destino.conIdentificadores(destino.todosLosIdentificadores() + nuevo)
                guardarDestino(uid, actualizado)
                // Lo que se sumó deja de ser un sugerido descartado: ahora es de alguien.
                DestinosDescartados.deleteWhere { (DestinosDescartados.userId eq uid) and (DestinosDescartados.clave eq nuevo.clave) }
                HttpStatusCode.OK to actualizado
            }
            val (status, cuerpo) = resultado
            if (cuerpo is DestinoConocido) {
                call.respond(status, conLoQueSeLeMando(cuerpo, loadNonVoidedEvents(uid), ajustesDePeriodoDe(uid), System.currentTimeMillis()))
            } else {
                call.respond(status, cuerpo as String)
            }
        }

        /** Quitarle un identificador. No el último: un tercero sin identificadores no reconoce nada. */
        post("/{id}/identificadores/quitar") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el id")
            val quitar = call.receive<IdentificadorDelDestino>().normalizado()
            val resultado: Pair<HttpStatusCode, Any> = dbQuery {
                val destino = destinoDe(uid, id) ?: return@dbQuery HttpStatusCode.NotFound to "No existe"
                val leQueda = destino.todosLosIdentificadores().filterNot { it.clave == quitar.clave }
                if (leQueda.isEmpty()) return@dbQuery HttpStatusCode.BadRequest to SIN_IDENTIFICADORES
                val actualizado = destino.conIdentificadores(leQueda)
                guardarDestino(uid, actualizado)
                HttpStatusCode.OK to actualizado
            }
            val (status, cuerpo) = resultado
            if (cuerpo is DestinoConocido) {
                call.respond(status, conLoQueSeLeMando(cuerpo, loadNonVoidedEvents(uid), ajustesDePeriodoDe(uid), System.currentTimeMillis()))
            } else {
                call.respond(status, cuerpo as String)
            }
        }

        // ── Lo que Movi encontró solo ───────────────────────────────────────────

        /** Ver `destinosSugeridos` en `:core`. Solo lee: nada se guarda sin que el dueño lo pida. */
        get("/sugeridos") {
            val uid = call.userId()
            call.respond(sugeridosDe(uid))
        }

        /** «Ignorar» o «Es mía»: que ese sugerido no vuelva. Idempotente. */
        post("/sugeridos/descartar") {
            val uid = call.userId()
            val pedido = call.receive<DescartarSugerido>()
            val id = pedido.identificador.normalizado()
            if (id.valor.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Falta el identificador")
            dbQuery {
                DestinosDescartados.insertIgnore {
                    it[userId] = uid
                    it[clave] = id.clave.take(120)
                    it[motivo] = pedido.motivo.name
                    it[creadoEn] = System.currentTimeMillis()
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        /**
         * **Lo que la fila «¿De quién es…?» no tiene que preguntar**: lo descartado, el nombre del
         * dueño como lo escribe su banco, y las colas de las cuentas que el banco dice que son suyas
         * («desde tu cuenta *8133» → `COLA:8133`).
         */
        get("/descartados") {
            val uid = call.userId()
            val claves = dbQuery {
                val descartadas = DestinosDescartados.selectAll().where { DestinosDescartados.userId eq uid }
                    .map { it[DestinosDescartados.clave] }
                val textos = SmsMessages.select(SmsMessages.text).where { SmsMessages.userId eq uid }.map { it[SmsMessages.text] }
                descartadas +
                    nombresPropiosEn(textos).map { "LLAVE:$it" } +
                    colasPropiasEn(textos).map { "COLA:$it" }
            }
            call.respond(DescartadosDelDueno(claves.distinct()))
        }

        // ── Ponerle el nombre a lo que ya estaba anotado ────────────────────────

        /**
         * **Los movimientos que se pueden renombrar**: los que nombran a este tercero por el texto
         * del banco y tienen un nombre ilegible («Transferencia a la cuenta *41279033068», «Pago QR ·
         * llave 0047142708» — ver `esUnNombreIlegible`). La app los muestra con la cantidad a la
         * vista y pide confirmación; esto no cambia nada.
         */
        get("/{id}/renombrables") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta el id")
            val destino = dbQuery { destinoDe(uid, id) } ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(
                MovimientosParaRenombrar(
                    nombreNuevoDelGasto = nombreHaciaElDestino(destino),
                    nombreNuevoDelIngreso = nombreDesdeElDestino(destino),
                    movimientos = renombrablesDe(destino, loadNonVoidedEvents(uid)),
                ),
            )
        }

        /**
         * **Renombrar los que el dueño confirmó.** Cada id se vuelve a validar: tiene que ser suyo,
         * no anulado, nombrar a este tercero y tener un nombre ilegible. Lo que no cumple se omite y
         * se cuenta — un nombre que el dueño escribió **nunca** se pisa, aunque venga en la lista.
         */
        post("/{id}/renombrar") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el id")
            val pedido = call.receive<RenombrarMovimientos>()
            val ids = pedido.ids.distinct()
            if (ids.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest, "No hay movimientos para renombrar")
            val resultado = dbQuery {
                val destino = destinoDe(uid, id) ?: return@dbQuery null
                val validos = renombrablesDe(destino, loadNonVoidedEventsIn(uid)).filter { it.id in ids }
                val ahora = System.currentTimeMillis()
                validos.forEach { ev ->
                    Events.update({ (Events.userId eq uid) and (Events.id eq ev.id) }) {
                        it[description] = if (ev.type == TransactionType.INCOME) nombreDesdeElDestino(destino)
                        else nombreHaciaElDestino(destino)
                        // Como toda corrección de concepto: la edad de la edición, para que un
                        // reenvío del teléfono no le devuelva el nombre viejo.
                        it[lastEditedAt] = ahora
                    }
                }
                RenombradosDelDestino(renombrados = validos.size, omitidos = ids.size - validos.size)
            } ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respond(resultado)
        }
    }
}

/** Lo que se le dice a quien intenta dejar a un tercero sin identificadores. */
internal const val SIN_IDENTIFICADORES: String =
    "Un tercero necesita al menos un número, una llave o un nombre. Si ya no lo usas, bórralo."

internal fun mensajeDeUnicoIdentificador(nombre: String): String =
    "Es lo único con que Movi reconoce a «$nombre». Agrégale otro dato antes de moverlo, o bórralo."

/** Ver `destinosSugeridos`. Público dentro del server para que una prueba lo llame sin HTTP. */
internal suspend fun sugeridosDe(uid: String, ahora: Long = System.currentTimeMillis()): List<DestinoSugerido> {
    val eventos = loadNonVoidedEvents(uid)
    return dbQuery {
        val destinos = destinosDe(uid)
        val nombresDeCuentas = Accounts.selectAll().where { Accounts.userId eq uid }.map { it[Accounts.name] }
        val descartados = DestinosDescartados.selectAll().where { DestinosDescartados.userId eq uid }
            .map { it[DestinosDescartados.clave] }.toSet()
        val avisos = SmsMessages.selectAll().where { SmsMessages.userId eq uid }
            // Lo apartado no es un movimiento (un código de confirmación, una publicidad).
            .filter { it[SmsMessages.motivoApartado] == null }
            .map { Triple(it[SmsMessages.text], it[SmsMessages.bank], it[SmsMessages.time]) }
        val textos = avisos.map { it.first }
        val rastros = avisos.mapNotNull { (texto, banco, hora) ->
            val leido = parseSms(texto, banco) ?: return@mapNotNull null
            val cuando = parseSmsTime(hora)?.atZone(AppClock.zone)?.toInstant()?.toEpochMilli() ?: return@mapNotNull null
            rastroDeUnAviso(
                texto = texto,
                monto = leido.amount.roundToLong(),
                moneda = leido.currency,
                tipo = leido.type,
                timestamp = cuando,
                esPagoDeTarjeta = leido.category == CARD_PAYMENT_CATEGORY,
            )
        } + eventos.mapNotNull(::rastroDeUnMovimiento)
        destinosSugeridos(
            rastros = rastros,
            destinos = destinos,
            nombresDeCuentas = nombresDeCuentas,
            colasPropias = colasPropiasEn(textos + eventos.mapNotNull { it.rawPayload }),
            nombresPropios = nombresPropiosEn(textos),
            descartados = descartados,
            ahora = ahora,
        )
    }
}

/** Los movimientos de [destino] (de ida o de vuelta) con un nombre ilegible. */
private fun renombrablesDe(destino: DestinoConocido, eventos: List<com.jvillada.movi.shared.model.FinancialEvent>) =
    eventos.filter { ev ->
        ev.transferId == null &&
            (vaHaciaElDestino(ev, destino) || vieneDelDestino(ev, destino)) &&
            esUnNombreIlegible(ev.description) &&
            // Que lo nombre el TEXTO DEL BANCO y no solo el concepto: un concepto ilegible no dice
            // el nombre del tercero, así que esto ya lo garantiza — se deja explícito igual.
            ev.description != nombreHaciaElDestino(destino) && ev.description != nombreDesdeElDestino(destino)
    }.sortedByDescending { it.timestamp }

/**
 * Lo que cambió un cliente que no conoce la lista (el APK instalado): mandó `numero` y quizás
 * `llave`. El número nuevo reemplaza al primero de la lista; la llave, igual (`null` = no la toques,
 * `""` = bórrala). Todo lo demás que tenía se conserva.
 */
private fun loQueCambioUnClienteViejo(guardado: DestinoConocido, body: DestinoConocido): List<IdentificadorDelDestino> {
    var lista = guardado.todosLosIdentificadores()
    val numeroNuevo = soloLosDigitos(body.numero)
    val numeroViejo = soloLosDigitos(guardado.numero)
    if (numeroNuevo != numeroViejo) {
        lista = lista.filterNot { it.tipo == TipoDeIdentificador.NUMERO && it.valor == numeroViejo }
        if (numeroNuevo.isNotEmpty()) lista = listOf(IdentificadorDelDestino(TipoDeIdentificador.NUMERO, numeroNuevo)) + lista
    }
    val llavePedida = body.llave
    if (llavePedida != null) {
        val llaveVieja = guardado.llave?.let(::normalizarLlave)
        val llaveNueva = normalizarLlave(llavePedida)
        if (llaveNueva != llaveVieja) {
            lista = lista.filterNot { it.tipo == TipoDeIdentificador.LLAVE && it.valor == llaveVieja }
            if (llaveNueva.isNotEmpty()) {
                val i = lista.indexOfFirst { it.tipo == TipoDeIdentificador.LLAVE }.let { if (it < 0) lista.size else it }
                lista = lista.take(i) + IdentificadorDelDestino(TipoDeIdentificador.LLAVE, llaveNueva) + lista.drop(i)
            }
        }
    }
    return lista
}

/**
 * La forma: nombre y nota como siempre (`rechazoDelDestino`, con el primer número y la primera
 * llave), y además **cada** identificador de la lista.
 */
private fun rechazoDeLaForma(nombre: String, deQuien: String?, ids: List<IdentificadorDelDestino>): String? {
    val numero = ids.firstOrNull { it.tipo == TipoDeIdentificador.NUMERO }?.valor.orEmpty()
    val llave = ids.firstOrNull { it.tipo == TipoDeIdentificador.LLAVE }?.valor
    rechazoDelDestino(nombre, numero, deQuien, llave)?.let { return it }
    return ids.firstNotNullOfOrNull(::rechazoDeUnIdentificador)
}

private fun rechazoDeUnIdentificador(id: IdentificadorDelDestino): String? = when (id.tipo) {
    TipoDeIdentificador.NUMERO -> when {
        soloLosDigitos(id.valor).length < MIN_DIGITOS_DEL_NUMERO -> NUMERO_DEMASIADO_CORTO
        soloLosDigitos(id.valor).length > MAX_DIGITOS_DEL_NUMERO -> NUMERO_DEMASIADO_LARGO
        else -> null
    }
    TipoDeIdentificador.LLAVE -> when {
        normalizarLlave(id.valor).length < MIN_LARGO_DE_LA_LLAVE -> LLAVE_DEMASIADO_CORTA
        normalizarLlave(id.valor).length > MAX_LARGO_DE_LA_LLAVE -> LLAVE_DEMASIADO_LARGA
        else -> null
    }
}

/**
 * Los destinos del usuario, **alfabéticos**. Sin `ORDER BY` esta lista salía en el orden físico de
 * la tabla —el que un UPDATE cambia sin avisar— y podía verse distinta entre dos lecturas: el mismo
 * defecto que tenían la bandeja de SMS y las metas.
 */
private fun Transaction.destinosDe(uid: String): List<DestinoConocido> =
    KnownDestinations.selectAll()
        .where { KnownDestinations.userId eq uid }
        .orderBy(KnownDestinations.nombre to SortOrder.ASC)
        .map { it.aDestino() }

private fun Transaction.destinoDe(uid: String, id: String): DestinoConocido? =
    KnownDestinations.selectAll()
        .where { (KnownDestinations.id eq id) and (KnownDestinations.userId eq uid) }
        .firstOrNull()?.aDestino()

/** Escribe nombre, nota, tipo, la lista y —para el APK instalado— el primer número y la primera llave. */
private fun Transaction.guardarDestino(uid: String, destino: DestinoConocido): Int =
    KnownDestinations.update({ (KnownDestinations.id eq destino.id) and (KnownDestinations.userId eq uid) }) {
        it[nombre] = destino.nombre
        it[numero] = destino.numero
        it[deQuien] = destino.deQuien
        it[llave] = destino.llave
        it[identificadores] = identificadoresComoJson(destino.identificadores)
        it[tipo] = destino.tipo?.name
    }

/**
 * Las guardas que necesitan mirar lo que el dueño ya tiene: sus cuentas y sus otros destinos.
 * Devuelve el par (código, texto) del rechazo, o `null` si todos los identificadores se pueden usar.
 *
 * [yaExiste] es el id del destino que se está editando, para que guardarlo sin cambiarle nada no
 * choque consigo mismo.
 */
private suspend fun rechazoPorLoQueYaTiene(
    uid: String,
    ids: List<IdentificadorDelDestino>,
    yaExiste: String?,
): Pair<HttpStatusCode, String>? {
    val otros = dbQuery { destinosDe(uid) }.filter { it.id != yaExiste }
    val numeros = ids.filter { it.tipo == TipoDeIdentificador.NUMERO }
    if (numeros.isNotEmpty()) {
        val nombresDeSusCuentas = dbQuery {
            Accounts.selectAll().where { Accounts.userId eq uid }.map { it[Accounts.name] }
        }
        numeros.forEach { n ->
            nombreDeLaCuentaPropiaConEseNumero(n.valor, nombresDeSusCuentas)?.let { propia ->
                return HttpStatusCode.UnprocessableEntity to mensajeDeNumeroPropio(propia)
            }
        }
    }
    ids.forEach { id ->
        otros.firstOrNull { d -> d.todosLosIdentificadores().any { mismoIdentificador(it, id) } }?.let { choca ->
            val mensaje = if (id.tipo == TipoDeIdentificador.NUMERO) mensajeDeNumeroRepetido(choca.nombre)
            else mensajeDeLlaveRepetida(choca.nombre)
            return HttpStatusCode.Conflict to mensaje
        }
    }
    return null
}
