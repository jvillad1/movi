package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Espacios
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.GastosDelEspacio
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LiquidacionesDelEspacio
import com.jvillada.movi.server.db.RecurrentesDelHogar
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.espacios.espaciosDe
import com.jvillada.movi.server.espacios.gastosDe
import com.jvillada.movi.server.espacios.liquidacionesDe
import com.jvillada.movi.server.espacios.loCompartidoDelUsuario
import com.jvillada.movi.server.espacios.miembrosComoJson
import com.jvillada.movi.server.espacios.partesComoJson
import com.jvillada.movi.server.espacios.recurrentesDelHogarDe
import com.jvillada.movi.server.espacios.repartoComoJson
import com.jvillada.movi.server.espacios.repartoDeLaFila
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.sms.destinosDelDueno
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.shared.model.CompartirMovimientoRequest
import com.jvillada.movi.shared.model.DIAS_DE_MEMORIA_DEL_REPARTO
import com.jvillada.movi.shared.model.DIAS_PARA_PROPONER_REPARTIR
import com.jvillada.movi.shared.model.GastoRepartido
import com.jvillada.movi.shared.model.PropuestaDeRepartir
import com.jvillada.movi.shared.model.RecurrenteDelHogar
import com.jvillada.movi.shared.model.RepartoDelGasto
import com.jvillada.movi.shared.model.problemaDelReparto
import com.jvillada.movi.shared.model.repartoParaOtroMonto
import com.jvillada.movi.shared.model.propuestasDeRepartir
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MAX_NOMBRE_DE_ESPACIO
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.PropuestaDeSaldar
import com.jvillada.movi.shared.model.DIAS_ANTES_DEL_SALDADO
import com.jvillada.movi.shared.model.esperaLaTransferencia
import com.jvillada.movi.shared.model.huellaDeLaPropuestaDeSaldar
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.idsDeMiembros
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.repartoValido
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.shared.model.transferenciasParaSaldar
import com.jvillada.movi.shared.model.ventanaDe
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * # Espacios (R3, 26-oct-2026)
 *
 * Lo grupal con casa propia: «Casa con Caro», 50/50. Ver `Espacio.kt` en `:core` para la regla de
 * plata (cuenta solo tu parte; saldar no es ingreso ni gasto). Todo filtrado por el usuario del
 * token; cada alta es un upsert por el id que manda el cliente (el teléfono reintenta), así que
 * repetirla no duplica nada — en particular, **saldar dos veces con el mismo id es saldar una**.
 *
 * - `GET /api/espacios` · `POST /api/espacios` (upsert) · `DELETE /api/espacios/{id}`
 * - `GET /api/espacios/tu-parte`: tu parte de lo que pagaron otros, para los totales del cliente.
 * - `GET /api/espacios/propuestas`: las transferencias que Movi propone vincular, de todos los grupos
 *   (vuelta 66: también las que espera un saldado marcado antes de que llegara la plata).
 * - `GET /api/espacios/{id}`: [DetalleDelEspacio] (saldo, quién puso qué en el período, gastos,
 *   liquidaciones y las transferencias que Movi propone vincular).
 * - `POST /api/espacios/{id}/compartir` (repartir un movimiento propio) ·
 *   `DELETE /api/espacios/{id}/compartir/{eventoId}`
 * - `POST /api/espacios/{id}/gastos` (lo que pagó otro miembro) · `DELETE …/gastos/{gastoId}`
 * - `POST /api/espacios/{id}/liquidaciones` («Marcar como saldado») · `DELETE …/liquidaciones/{id}`
 *
 * Vuelta 97:
 * - `POST …/compartir` y `POST …/gastos` aceptan un `reparto` propio (porcentajes, montos o «Solo de
 *   …»), validado con `problemaDelReparto`: inválido es **422 sin tocar nada**. Con el movimiento ya
 *   repartido en ese grupo, `…/compartir` con un `reparto` lo **edita**.
 * - `GET /api/espacios/propuestas-de-reparto`: lo que Movi propone repartir (`propuestasDeRepartir`),
 *   derivado en cada lectura. Los descartes van por `POST /api/anomalias/descartar`.
 * - `GET /api/espacios/recurrentes-del-hogar` · `PUT`/`DELETE /api/espacios/{id}/recurrentes/{reglaId}`:
 *   «Se reparte en …», el interruptor del detalle de un recurrente.
 */
fun Route.espacioRoutes() {
    route("/api/espacios") {
        get { call.respond(dbQuery { espaciosDe(call.userId()) }) }

        // Tu parte de lo que pagaron otros, como movimientos de gasto para los totales que arma el
        // cliente (En qué se va). Nunca van a la lista de movimientos ni a un saldo.
        get("/tu-parte") { call.respond(dbQuery { loCompartidoDelUsuario(call.userId()).tuParteDeOtros }) }

        // Vuelta 66: las transferencias que Movi propone vincular, de todos los grupos (Por revisar y
        // la campana). Las mismas que dice cada grupo.
        get("/propuestas") {
            val ahora = AppClock.now().toInstant().toEpochMilli()
            call.respond(dbQuery { propuestasDeSaldarDe(call.userId(), ahora) })
        }

        // Vuelta 97: lo que Movi propone repartir («Movi propone hoy» y Por revisar). Nunca reparte solo.
        get("/propuestas-de-reparto") {
            val ahora = AppClock.now().toInstant().toEpochMilli()
            call.respond(dbQuery { propuestasDeRepartirDe(call.userId(), ahora) })
        }

        // Vuelta 97: «Se reparte en …» de cada recurrente marcado.
        get("/recurrentes-del-hogar") {
            val uid = call.userId()
            call.respond(dbQuery { recurrentesDelHogarDe(uid).map { (regla, espacio) -> RecurrenteDelHogar(regla, espacio) } })
        }

        put("/{id}/recurrentes/{reglaId}") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val reglaId = call.parameters["reglaId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val r = dbQuery { marcarDelHogar(uid, espacioId, reglaId) }
            if (r.valor != null) call.respond(r.estado, r.valor) else call.respond(r.estado, r.motivo)
        }

        delete("/{id}/recurrentes/{reglaId}") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val reglaId = call.parameters["reglaId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            dbQuery {
                RecurrentesDelHogar.deleteWhere {
                    (RecurrentesDelHogar.userId eq uid) and (RecurrentesDelHogar.espacioId eq espacioId) and
                        (RecurrentesDelHogar.reglaId eq reglaId)
                }
            }
            // Idempotente: quitar una marca que no está es quitarla.
            call.respond(HttpStatusCode.NoContent)
        }

        post {
            val uid = call.userId()
            val body = call.receive<Espacio>()
            val nombre = body.nombre.trim()
            if (nombre.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Falta el nombre")
            if (nombre.length > MAX_NOMBRE_DE_ESPACIO) {
                return@post call.respond(HttpStatusCode.BadRequest, "El nombre no puede pasar de $MAX_NOMBRE_DE_ESPACIO letras")
            }
            val otros = body.miembros.filter { it.id != MIEMBRO_YO }.distinctBy { it.id }
            if (otros.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest, "Elige con quién compartes")
            if (!repartoValido(body)) return@post call.respond(HttpStatusCode.BadRequest, "Los porcentajes tienen que sumar 100")
            val id = body.id.trim().ifBlank { "esp_${UUID.randomUUID()}" }
            val resultado = dbQuery {
                val terceros = KnownDestinations.selectAll().where { KnownDestinations.userId eq uid }
                    .map { it[KnownDestinations.id] }.toSet()
                if (otros.any { it.id !in terceros }) return@dbQuery HttpStatusCode.NotFound
                val existente = Espacios.selectAll().where { Espacios.id eq id }.firstOrNull()
                val miembros = miembrosComoJson(body.miembros, body.reparto)
                when {
                    existente == null -> {
                        Espacios.insert {
                            it[Espacios.id] = id
                            it[userId] = uid
                            it[Espacios.nombre] = nombre
                            it[Espacios.miembros] = miembros
                            it[reparto] = body.reparto.name
                            it[createdAt] = System.currentTimeMillis()
                        }
                        HttpStatusCode.Created
                    }
                    existente[Espacios.userId] != uid -> HttpStatusCode.Conflict
                    else -> {
                        Espacios.update({ (Espacios.id eq id) and (Espacios.userId eq uid) }) {
                            it[Espacios.nombre] = nombre
                            it[Espacios.miembros] = miembros
                            it[reparto] = body.reparto.name
                        }
                        HttpStatusCode.OK
                    }
                }
            }
            when (resultado) {
                HttpStatusCode.NotFound -> call.respond(HttpStatusCode.NotFound, "Esa persona no está en tus terceros")
                HttpStatusCode.Conflict -> call.respond(HttpStatusCode.Conflict, "Ese id ya existe")
                else -> call.respond(resultado, dbQuery { espaciosDe(uid) }.first { it.id == id })
            }
        }

        delete("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val borrados = dbQuery {
                val n = Espacios.deleteWhere { (Espacios.id eq id) and (Espacios.userId eq uid) }
                if (n > 0) {
                    GastosDelEspacio.deleteWhere { (GastosDelEspacio.espacioId eq id) and (GastosDelEspacio.userId eq uid) }
                    LiquidacionesDelEspacio.deleteWhere {
                        (LiquidacionesDelEspacio.espacioId eq id) and (LiquidacionesDelEspacio.userId eq uid)
                    }
                    RecurrentesDelHogar.deleteWhere { (RecurrentesDelHogar.espacioId eq id) and (RecurrentesDelHogar.userId eq uid) }
                }
                n
            }
            call.respond(if (borrados == 0) HttpStatusCode.NotFound else HttpStatusCode.NoContent)
        }

        get("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val ahora = AppClock.now().toInstant().toEpochMilli()
            val detalle = dbQuery { detalleDelEspacio(uid, id, ahora) }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(detalle)
        }

        post("/{id}/compartir") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<CompartirMovimientoRequest>()
            val r = dbQuery { compartirMovimiento(uid, espacioId, body) }
            if (r.valor != null) call.respond(r.estado, r.valor) else call.respond(r.estado, r.motivo)
        }

        delete("/{id}/compartir/{eventoId}") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val eventoId = call.parameters["eventoId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val n = dbQuery {
                GastosDelEspacio.deleteWhere {
                    (GastosDelEspacio.userId eq uid) and (GastosDelEspacio.espacioId eq espacioId) and
                        (GastosDelEspacio.eventoId eq eventoId)
                }
            }
            call.respond(if (n == 0) HttpStatusCode.NotFound else HttpStatusCode.NoContent)
        }

        post("/{id}/gastos") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<GastoDelEspacio>()
            val r = dbQuery { gastoQuePagoOtro(uid, espacioId, body) }
            if (r.valor != null) call.respond(r.estado, r.valor) else call.respond(r.estado, r.motivo)
        }

        delete("/{id}/gastos/{gastoId}") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val gastoId = call.parameters["gastoId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val n = dbQuery {
                GastosDelEspacio.deleteWhere {
                    (GastosDelEspacio.userId eq uid) and (GastosDelEspacio.espacioId eq espacioId) and
                        (GastosDelEspacio.id eq gastoId)
                }
            }
            call.respond(if (n == 0) HttpStatusCode.NotFound else HttpStatusCode.NoContent)
        }

        post("/{id}/liquidaciones") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<LiquidacionDelEspacio>()
            val r = dbQuery { liquidar(uid, espacioId, body) }
            if (r.valor != null) call.respond(r.estado, r.valor) else call.respond(r.estado, r.motivo)
        }

        delete("/{id}/liquidaciones/{liquidacionId}") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val liquidacionId = call.parameters["liquidacionId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val n = dbQuery {
                LiquidacionesDelEspacio.deleteWhere {
                    (LiquidacionesDelEspacio.userId eq uid) and (LiquidacionesDelEspacio.espacioId eq espacioId) and
                        (LiquidacionesDelEspacio.id eq liquidacionId)
                }
            }
            call.respond(if (n == 0) HttpStatusCode.NotFound else HttpStatusCode.NoContent)
        }
    }
}

/** Un rechazo con su motivo para el dueño. */
private class Rechazo(val estado: HttpStatusCode, val motivo: String) : RuntimeException(motivo)

/** Lo que contesta una escritura: el estado y lo guardado, o el motivo del rechazo. */
internal class Respuesta<T : Any>(val estado: HttpStatusCode, val valor: T?, val motivo: String = "")

private fun Transaction.anuladosDe(uid: String): Set<String> =
    VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()

private fun Transaction.espacioDelUsuario(uid: String, espacioId: String): Espacio? =
    espaciosDe(uid).firstOrNull { it.id == espacioId }

/**
 * La respuesta de `GET /api/espacios/{id}`, con un «ahora» que una prueba puede fijar; `null` si el
 * espacio no es de [uid].
 */
internal fun Transaction.detalleDelEspacio(uid: String, espacioId: String, ahora: Long): DetalleDelEspacio? {
    val espacios = espaciosDe(uid)
    val espacio = espacios.firstOrNull { it.id == espacioId } ?: return null
    val anulados = anuladosDe(uid)
    val gastos = gastosDe(uid, espacios, espacioId).filterNot { it.eventoId != null && it.eventoId in anulados }
    val liquidaciones = conElMovimiento(uid, liquidacionesDe(uid, espacioId).filterNot { it.eventoId != null && it.eventoId in anulados })
    val saldo = saldoDelEspacio(espacio, gastos, liquidaciones, anulados)
    val ventana = ventanaDe(periodoDe(ahora, ajustesDelPeriodoSinSuspender(uid)), ajustesDelPeriodoSinSuspender(uid))
    val delPeriodo = saldoDelEspacio(espacio, gastos.filter { it.fecha in ventana }, emptyList(), anulados)
    return DetalleDelEspacio(
        espacio = espacio,
        saldo = saldo,
        delPeriodo = delPeriodo,
        gastos = gastos,
        liquidaciones = liquidaciones,
        propuestas = propuestasDelEspacio(uid, espacio, saldo, liquidaciones, ahora),
    )
}

/**
 * **Lo que Movi propone vincular en [espacio]**: la transferencia que salda la deuda abierta (como
 * siempre) y, desde la vuelta 66, la que espera un saldado que se marcó antes de que llegara la plata
 * ([transferenciasQueEsperan] vía `transferenciasParaSaldar`). Sin lo que el dueño ya descartó con «No»
 * ([huellaDeLaPropuestaDeSaldar] en `anomalias_descartadas`). Cada propuesta lleva su grupo, para
 * mostrarla también en Por revisar y la campana.
 */
internal fun Transaction.propuestasDelEspacio(
    uid: String,
    espacio: Espacio,
    saldo: com.jvillada.movi.shared.model.SaldoDelEspacio,
    liquidaciones: List<LiquidacionDelEspacio>,
    ahora: Long,
): List<PropuestaDeSaldar> {
    val hayDeuda = saldo.reembolsos.any { it.de == MIEMBRO_YO || it.a == MIEMBRO_YO }
    val esperan = liquidaciones.filter { esperaLaTransferencia(it, ahora) }
    if (!hayDeuda && esperan.isEmpty()) return emptyList()
    val diasAtras = com.jvillada.movi.shared.model.DIAS_PARA_PROPONER_SALDAR * UN_DIA
    val desde = (listOf(ahora - diasAtras) + esperan.map { it.fecha - DIAS_ANTES_DEL_SALDADO * UN_DIA }).min()
    val eventos = loadEventsBetween(uid, desde, ahora + 1)
    val saldoParaProponer = if (hayDeuda) saldo else saldo.copy(reembolsos = emptyList())
    val descartadas = propuestasDescartadas(uid)
    return transferenciasParaSaldar(saldoParaProponer, eventos, destinosDelDueno(uid), ahora, liquidaciones = esperan)
        .filterNot { huellaDeLaPropuestaDeSaldar(it.eventoId) in descartadas }
        .map { p ->
            val otro = if (p.a == MIEMBRO_YO) p.de else p.a
            p.copy(
                espacioId = espacio.id,
                nombreDelEspacio = espacio.nombre,
                quien = espacio.miembros.firstOrNull { it.id == otro }?.nombre.orEmpty(),
            )
        }
}

private const val UN_DIA: Long = 24L * 60 * 60 * 1000

/** Las huellas que el dueño descartó (la tabla de las anomalías); vacío si la tabla no existe. */
private fun Transaction.propuestasDescartadas(uid: String): Set<String> =
    if (!runCatching { AnomaliasDescartadas.exists() }.getOrDefault(false)) emptySet()
    else AnomaliasDescartadas.selectAll().where { AnomaliasDescartadas.userId eq uid }
        .map { it[AnomaliasDescartadas.huella] }.filter { it.startsWith("saldar:") }.toSet()

/**
 * **Todas las propuestas de saldar del dueño** (vuelta 66, `GET /api/espacios/propuestas`): las de
 * cada grupo, para Por revisar y la campana. Antes solo se veían dentro del grupo.
 */
internal fun Transaction.propuestasDeSaldarDe(uid: String, ahora: Long): List<PropuestaDeSaldar> {
    val espacios = espaciosDe(uid)
    if (espacios.isEmpty()) return emptyList()
    val anulados = anuladosDe(uid)
    val gastos = gastosDe(uid, espacios).filterNot { it.eventoId != null && it.eventoId in anulados }
    val liquidaciones = liquidacionesDe(uid).filterNot { it.eventoId != null && it.eventoId in anulados }
    val usados = mutableSetOf<String>()
    return espacios.flatMap { espacio ->
        val suyas = liquidaciones.filter { it.espacioId == espacio.id }
        val saldo = saldoDelEspacio(espacio, gastos.filter { it.espacioId == espacio.id }, suyas, anulados)
        propuestasDelEspacio(uid, espacio, saldo, suyas, ahora).filter { usados.add(it.eventoId) }
    }
}

/** [liquidaciones] con el concepto de su movimiento vinculado («Transferencia de Caro»). */
private fun Transaction.conElMovimiento(uid: String, liquidaciones: List<LiquidacionDelEspacio>): List<LiquidacionDelEspacio> {
    val ids = liquidaciones.mapNotNull { it.eventoId }
    if (ids.isEmpty()) return liquidaciones
    val conceptos = Events.selectAll().where { (Events.userId eq uid) and (Events.id inList ids) }
        .associate { it[Events.id] to it[Events.description] }
    return liquidaciones.map { l -> l.eventoId?.let { conceptos[it] }?.let { l.copy(movimiento = it) } ?: l }
}

/**
 * Reparte un movimiento propio en el espacio. Idempotente: el mismo movimiento en el mismo espacio
 * devuelve el gasto que ya existe. Un movimiento está en un espacio o en ninguno (409 si ya está en
 * otro). Solo un gasto vivo en pesos, que no sea la mitad de un traspaso ni salde un espacio.
 *
 * Vuelta 97 · con [CompartirMovimientoRequest.reparto] no nulo sobre un movimiento ya repartido en este
 * espacio, **edita** el reparto. **`reparto = null` no revierte un reparto propio**: deja el que estaba
 * (es el reintento de un APK viejo, que no conoce el campo). Para volver a la regla del grupo se manda
 * explícito `{"forma":"DEL_GRUPO"}`.
 */
internal fun Transaction.compartirMovimiento(
    uid: String,
    espacioId: String,
    body: CompartirMovimientoRequest,
): Respuesta<GastoDelEspacio> = try {
    val espacio = espacioDelUsuario(uid, espacioId) ?: throw Rechazo(HttpStatusCode.NotFound, "Grupo no encontrado")
    val fila = Events.selectAll().where { (Events.id eq body.eventoId) and (Events.userId eq uid) }.firstOrNull()
        ?: throw Rechazo(HttpStatusCode.NotFound, "Movimiento no encontrado")
    if (body.eventoId in anuladosDe(uid)) throw Rechazo(HttpStatusCode.NotFound, "Ese movimiento está anulado")
    if (fila[Events.type] != TransactionType.EXPENSE.name) throw Rechazo(HttpStatusCode.BadRequest, "Solo se reparte un gasto")
    if (fila[Events.currency] != "COP") throw Rechazo(HttpStatusCode.BadRequest, "Por ahora solo se reparten gastos en pesos")
    if (fila[Events.transferId] != null) throw Rechazo(HttpStatusCode.BadRequest, "Un traspaso entre tus cuentas no se reparte")
    val salda = LiquidacionesDelEspacio.selectAll()
        .where { (LiquidacionesDelEspacio.userId eq uid) and (LiquidacionesDelEspacio.eventoId eq body.eventoId) }
        .any()
    if (salda) throw Rechazo(HttpStatusCode.Conflict, "Ese movimiento ya salda un grupo")
    val yaEsta = GastosDelEspacio.selectAll()
        .where { (GastosDelEspacio.userId eq uid) and (GastosDelEspacio.eventoId eq body.eventoId) }
        .firstOrNull()
    if (yaEsta != null && yaEsta[GastosDelEspacio.espacioId] != espacioId) {
        throw Rechazo(HttpStatusCode.Conflict, "Ese movimiento ya está repartido en otro grupo")
    }
    // Vuelta 97: el reparto propio se valida antes de escribir nada (422 y no toca nada).
    val monto = fila[Events.amount]
    problemaDelReparto(body.reparto, monto, espacio)?.let { throw Rechazo(HttpStatusCode.UnprocessableEntity, it) }
    if (yaEsta != null && body.reparto != null) {
        // Editar el reparto de un gasto ya compartido: las partes nuevas, y el saldo se recalcula solo.
        // Sin reparto (un APK viejo que reintenta), nada cambia.
        GastosDelEspacio.update({ (GastosDelEspacio.id eq yaEsta[GastosDelEspacio.id]) and (GastosDelEspacio.userId eq uid) }) {
            it[GastosDelEspacio.monto] = monto
            it[partes] = partesComoJson(partesDelGasto(monto, espacio, MIEMBRO_YO, body.reparto))
            it[reparto] = repartoComoJson(body.reparto)
        }
    }
    if (yaEsta == null) {
        val id = body.gastoId.trim().ifBlank { "gde_${UUID.randomUUID()}" }
        val ajeno = GastosDelEspacio.selectAll().where { GastosDelEspacio.id eq id }.firstOrNull()
        if (ajeno != null) throw Rechazo(HttpStatusCode.Conflict, "Ese id ya existe")
        GastosDelEspacio.insert {
            it[GastosDelEspacio.id] = id
            it[userId] = uid
            it[GastosDelEspacio.espacioId] = espacioId
            it[eventoId] = body.eventoId
            it[pagadoPor] = MIEMBRO_YO
            it[GastosDelEspacio.monto] = monto
            it[categoria] = fila[Events.category].take(100)
            it[descripcion] = fila[Events.description].take(200)
            it[fecha] = fila[Events.timestamp]
            it[partes] = partesComoJson(partesDelGasto(monto, espacio, MIEMBRO_YO, body.reparto))
            it[reparto] = repartoComoJson(body.reparto)
            it[createdAt] = System.currentTimeMillis()
        }
    }
    val gasto = gastosDe(uid, listOf(espacio), espacioId).first { it.eventoId == body.eventoId }
    Respuesta(if (yaEsta == null) HttpStatusCode.Created else HttpStatusCode.OK, gasto)
} catch (r: Rechazo) {
    Respuesta(r.estado, null, r.motivo)
}

/** Un gasto que pagó otro miembro. Upsert por id: reenviarlo no lo duplica. */
internal fun Transaction.gastoQuePagoOtro(
    uid: String,
    espacioId: String,
    body: GastoDelEspacio,
): Respuesta<GastoDelEspacio> = try {
    val espacio = espacioDelUsuario(uid, espacioId) ?: throw Rechazo(HttpStatusCode.NotFound, "Grupo no encontrado")
    if (body.eventoId != null) throw Rechazo(HttpStatusCode.BadRequest, "Un movimiento tuyo se reparte desde su detalle")
    if (body.pagadoPor == MIEMBRO_YO || body.pagadoPor !in idsDeMiembros(espacio)) {
        throw Rechazo(HttpStatusCode.BadRequest, "Elige quién lo pagó")
    }
    if (body.monto <= 0L) throw Rechazo(HttpStatusCode.BadRequest, "El monto debe ser mayor que cero")
    val categoria = body.categoria.trim()
    if (categoria.isBlank()) throw Rechazo(HttpStatusCode.BadRequest, "Falta la categoría")
    val id = body.id.trim().ifBlank { "gde_${UUID.randomUUID()}" }
    val existente = GastosDelEspacio.selectAll().where { GastosDelEspacio.id eq id }.firstOrNull()
    if (existente != null && existente[GastosDelEspacio.userId] != uid) throw Rechazo(HttpStatusCode.Conflict, "Ese id ya existe")
    val fecha = if (body.fecha > 0L) body.fecha else System.currentTimeMillis()
    val descripcion = body.descripcion.trim().ifBlank { categoria }.take(200)
    // Vuelta 97: sin reparto en el cuerpo, uno que ya estaba se conserva (el reenvío de un APK viejo no
    // lo borra); el cliente nuevo siempre lo manda, también «Partes iguales».
    val reparto: RepartoDelGasto? = body.reparto
        ?: existente?.get(GastosDelEspacio.reparto)?.let(::repartoDeLaFila)
            ?.let { repartoParaOtroMonto(it, body.monto, espacio, body.pagadoPor) }
            ?.takeIf { problemaDelReparto(it, body.monto, espacio) == null }
    problemaDelReparto(reparto, body.monto, espacio)?.let { throw Rechazo(HttpStatusCode.UnprocessableEntity, it) }
    val partes = partesComoJson(partesDelGasto(body.monto, espacio, body.pagadoPor, reparto))
    if (existente == null) {
        GastosDelEspacio.insert {
            it[GastosDelEspacio.id] = id
            it[userId] = uid
            it[GastosDelEspacio.espacioId] = espacioId
            it[eventoId] = null
            it[pagadoPor] = body.pagadoPor
            it[monto] = body.monto
            it[GastosDelEspacio.categoria] = categoria.take(100)
            it[GastosDelEspacio.descripcion] = descripcion
            it[GastosDelEspacio.fecha] = fecha
            it[GastosDelEspacio.partes] = partes
            it[GastosDelEspacio.reparto] = repartoComoJson(reparto)
            it[createdAt] = System.currentTimeMillis()
        }
    } else {
        GastosDelEspacio.update({ (GastosDelEspacio.id eq id) and (GastosDelEspacio.userId eq uid) }) {
            it[GastosDelEspacio.espacioId] = espacioId
            it[pagadoPor] = body.pagadoPor
            it[monto] = body.monto
            it[GastosDelEspacio.categoria] = categoria.take(100)
            it[GastosDelEspacio.descripcion] = descripcion
            it[GastosDelEspacio.fecha] = fecha
            it[GastosDelEspacio.partes] = partes
            it[GastosDelEspacio.reparto] = repartoComoJson(reparto)
        }
    }
    val gasto = gastosDe(uid, listOf(espacio), espacioId).first { it.id == id }
    Respuesta(if (existente == null) HttpStatusCode.Created else HttpStatusCode.OK, gasto)
} catch (r: Rechazo) {
    Respuesta(r.estado, null, r.motivo)
}

/**
 * «Marcar como saldado». **Idempotente por id**: la misma liquidación dos veces es una, y devuelve la
 * primera tal como quedó. Con [LiquidacionDelEspacio.eventoId], la transferencia real: tiene que ser
 * del dueño, viva, en la dirección que corresponde (un ingreso si el dueño recibe, un gasto si paga)
 * y no estar repartida ni saldando otra cosa. Desde ahí no cuenta como ingreso ni gasto.
 */
internal fun Transaction.liquidar(
    uid: String,
    espacioId: String,
    body: LiquidacionDelEspacio,
): Respuesta<LiquidacionDelEspacio> = try {
    val espacio = espacioDelUsuario(uid, espacioId) ?: throw Rechazo(HttpStatusCode.NotFound, "Grupo no encontrado")
    val id = body.id.trim().ifBlank { "liq_${UUID.randomUUID()}" }
    val existente = LiquidacionesDelEspacio.selectAll().where { LiquidacionesDelEspacio.id eq id }.firstOrNull()
    when {
        existente != null && existente[LiquidacionesDelEspacio.userId] != uid ->
            throw Rechazo(HttpStatusCode.Conflict, "Ese id ya existe")
        // Vuelta 66 · un saldado que espera su transferencia y ahora llega con ella: se vincula (la
        // misma validación de siempre). Lo demás de la liquidación no cambia: saldar dos veces sigue
        // siendo saldar una.
        existente != null && existente[LiquidacionesDelEspacio.eventoId] == null && body.eventoId != null &&
            existente[LiquidacionesDelEspacio.espacioId] == espacioId -> {
            val guardada = liquidacionesDe(uid, espacioId).first { it.id == id }
            validarElVinculo(uid, body.eventoId!!, guardada)
            LiquidacionesDelEspacio.update({ (LiquidacionesDelEspacio.id eq id) and (LiquidacionesDelEspacio.userId eq uid) }) {
                it[eventoId] = body.eventoId
            }
            Respuesta(HttpStatusCode.OK, liquidacionesDe(uid, espacioId).first { it.id == id })
        }
        existente != null -> Respuesta(HttpStatusCode.OK, liquidacionesDe(uid, espacioId).first { it.id == id })
        else -> {
            val miembros = idsDeMiembros(espacio)
            if (body.de !in miembros || body.a !in miembros || body.de == body.a) {
                throw Rechazo(HttpStatusCode.BadRequest, "Elige quién le pagó a quién")
            }
            if (body.monto <= 0L) throw Rechazo(HttpStatusCode.BadRequest, "El monto debe ser mayor que cero")
            body.eventoId?.let { eventoId -> validarElVinculo(uid, eventoId, body) }
            LiquidacionesDelEspacio.insert {
                it[LiquidacionesDelEspacio.id] = id
                it[userId] = uid
                it[LiquidacionesDelEspacio.espacioId] = espacioId
                it[de] = body.de
                it[a] = body.a
                it[monto] = body.monto
                it[fecha] = if (body.fecha > 0L) body.fecha else System.currentTimeMillis()
                it[eventoId] = body.eventoId
                it[createdAt] = System.currentTimeMillis()
            }
            Respuesta(HttpStatusCode.Created, liquidacionesDe(uid, espacioId).first { it.id == id })
        }
    }
} catch (r: Rechazo) {
    Respuesta(r.estado, null, r.motivo)
}

private fun Transaction.validarElVinculo(uid: String, eventoId: String, body: LiquidacionDelEspacio) {
    val fila = Events.selectAll().where { (Events.id eq eventoId) and (Events.userId eq uid) }.firstOrNull()
        ?: throw Rechazo(HttpStatusCode.NotFound, "Movimiento no encontrado")
    if (eventoId in anuladosDe(uid)) throw Rechazo(HttpStatusCode.NotFound, "Ese movimiento está anulado")
    val esperado = when (MIEMBRO_YO) {
        body.a -> TransactionType.INCOME
        body.de -> TransactionType.EXPENSE
        else -> throw Rechazo(HttpStatusCode.BadRequest, "Solo se vincula un movimiento tuyo si tú pagas o recibes")
    }
    if (fila[Events.type] != esperado.name) {
        throw Rechazo(
            HttpStatusCode.BadRequest,
            if (esperado == TransactionType.INCOME) "Vincula la plata que te entró" else "Vincula la plata que enviaste",
        )
    }
    val yaSalda = LiquidacionesDelEspacio.selectAll()
        .where { (LiquidacionesDelEspacio.userId eq uid) and (LiquidacionesDelEspacio.eventoId eq eventoId) }
        .any()
    val repartido = GastosDelEspacio.selectAll()
        .where { (GastosDelEspacio.userId eq uid) and (GastosDelEspacio.eventoId eq eventoId) }
        .any()
    if (yaSalda || repartido) throw Rechazo(HttpStatusCode.Conflict, "Ese movimiento ya está en un grupo")
}

/**
 * Vuelta 97 · **lo que Movi propone repartir** a [uid], derivado en cada lectura con
 * [propuestasDeRepartir]: sus movimientos de los últimos días (sin anulados, con lo que dicen los
 * grupos), lo repartido en los últimos [DIAS_DE_MEMORIA_DEL_REPARTO] días con su movimiento, sus
 * recurrentes con la marca del hogar y lo que descartó. Cada propuesta lleva su grupo con los nombres.
 */
internal fun Transaction.propuestasDeRepartirDe(uid: String, ahora: Long): List<PropuestaDeRepartir> {
    val espacios = espaciosDe(uid)
    if (espacios.isEmpty()) return emptyList()
    val anulados = anuladosDe(uid)
    val desdeLaMemoria = ahora - DIAS_DE_MEMORIA_DEL_REPARTO * UN_DIA
    val gastos = gastosDe(uid, espacios)
        .filterNot { it.eventoId != null && it.eventoId in anulados }
        .filter { it.fecha >= desdeLaMemoria }
    val ids = gastos.mapNotNull { it.eventoId }
    val suyos = if (ids.isEmpty()) emptyMap() else Events.selectAll()
        .where { (Events.userId eq uid) and (Events.id inList ids) }
        .associate { it[Events.id] to it.toFinancialEvent() }
    val reglas = RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
    return propuestasDeRepartir(
        eventos = loadEventsBetween(uid, ahora - DIAS_PARA_PROPONER_REPARTIR * UN_DIA, ahora + 1),
        repartidos = gastos.map { GastoRepartido(it, it.eventoId?.let(suyos::get)) },
        espacios = espacios,
        reglas = reglas,
        delHogar = recurrentesDelHogarDe(uid),
        descartadas = descartesDeRepartir(uid),
        ahora = ahora,
    )
}

/** Las huellas de «No es compartido» y «Nunca para este comercio»; vacío si la tabla no existe. */
private fun Transaction.descartesDeRepartir(uid: String): Set<String> =
    if (!runCatching { AnomaliasDescartadas.exists() }.getOrDefault(false)) emptySet()
    else AnomaliasDescartadas.selectAll().where { AnomaliasDescartadas.userId eq uid }
        .map { it[AnomaliasDescartadas.huella] }.filter { it.startsWith("repartir") }.toSet()

/**
 * Vuelta 97 · **«Se reparte en …»**: marca la regla [reglaId] como del hogar en [espacioId]. Una regla
 * va a un grupo o a ninguno: marcarla en otro grupo la mueve. Solo un recurrente de gasto del dueño.
 */
internal fun Transaction.marcarDelHogar(uid: String, espacioId: String, reglaId: String): Respuesta<RecurrenteDelHogar> = try {
    espacioDelUsuario(uid, espacioId) ?: throw Rechazo(HttpStatusCode.NotFound, "Grupo no encontrado")
    val regla = RecurringRules.selectAll().where { (RecurringRules.id eq reglaId) and (RecurringRules.userId eq uid) }
        .firstOrNull() ?: throw Rechazo(HttpStatusCode.NotFound, "Recurrente no encontrado")
    if (regla[RecurringRules.type] != TransactionType.EXPENSE.name) {
        throw Rechazo(HttpStatusCode.BadRequest, "Solo se reparte un recurrente de gasto")
    }
    RecurrentesDelHogar.upsert {
        it[userId] = uid
        it[RecurrentesDelHogar.reglaId] = reglaId
        it[RecurrentesDelHogar.espacioId] = espacioId
        it[createdAt] = System.currentTimeMillis()
    }
    Respuesta(HttpStatusCode.OK, RecurrenteDelHogar(reglaId, espacioId))
} catch (r: Rechazo) {
    Respuesta(r.estado, null, r.motivo)
}
