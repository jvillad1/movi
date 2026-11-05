package com.jvillada.movi.server.routes

import com.jvillada.movi.server.correo.sha256Hex
import com.jvillada.movi.server.db.Espacios
import com.jvillada.movi.server.db.EspaciosConectados
import com.jvillada.movi.server.db.InvitacionesAEspacio
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.espacios.conexionDelGrupo
import com.jvillada.movi.server.espacios.cancelarLaInvitacionPendiente
import com.jvillada.movi.server.espacios.desconectarElGrupo
import com.jvillada.movi.server.espacios.espaciosDe
import com.jvillada.movi.server.espacios.espaciosSinNombres
import com.jvillada.movi.server.espacios.ladosConectadosDe
import com.jvillada.movi.server.espacios.miembrosComoJson
import com.jvillada.movi.server.espacios.nombreDelPerfil
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.ALFABETO_DEL_CODIGO
import com.jvillada.movi.shared.model.CodigoDeInvitacion
import com.jvillada.movi.shared.model.DIAS_QUE_VALE_LA_INVITACION
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.INTENTOS_DE_CODIGO_POR_HORA
import com.jvillada.movi.shared.model.InvitacionCreada
import com.jvillada.movi.shared.model.InvitacionPendiente
import com.jvillada.movi.shared.model.InvitacionVista
import com.jvillada.movi.shared.model.LARGO_DEL_CODIGO
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.RepartoDelEspacio
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.claveComparableDeNombre
import com.jvillada.movi.shared.model.codigoLimpio
import com.jvillada.movi.shared.model.pareceUnCodigo
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * # El hogar conectado: la invitación por código (vuelta 131)
 *
 * Juan invita a Caro a «Casa con Caro» con un código de [LARGO_DEL_CODIGO] caracteres que le manda por
 * mensaje; Caro lo escribe en su Movi (Tú → Grupos → «Tengo un código») y se une. Al aceptar se crea **un
 * grupo nuevo en la cuenta de Caro** (mismo nombre y regla de reparto, ella y un tercero para Juan) y la
 * conexión entre los dos grupos. Lo anotado antes por cada uno sigue siendo solo suyo.
 *
 * - `POST /api/espacios/{id}/invitacion` → [InvitacionCreada]. El código en claro sale **solo aquí**; en la
 *   base queda su SHA-256. Generar otro cancela el pendiente.
 * - `GET /api/espacios/{id}/invitacion` → [InvitacionPendiente] o 204 · `DELETE` la cancela.
 * - `POST /api/espacios/invitaciones/ver` → [InvitacionVista]; inválido, vencido o usado: el mismo 404.
 * - `POST /api/espacios/invitaciones/aceptar` → el [Espacio] nuevo de quien acepta.
 * - `DELETE /api/espacios/{id}/conexion`: cualquiera de los dos desconecta.
 *
 * El código viaja siempre en el **cuerpo**, nunca en la URL (`CallLogging` imprime la ruta). Ver y aceptar
 * con un código que no sirve cuenta como intento fallido: más de [INTENTOS_DE_CODIGO_POR_HORA] por persona
 * en una hora contesta 429.
 */
fun Route.hogarConectadoRoutes() {
    route("/api/espacios") {
        post("/{id}/invitacion") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val r = dbQuery { invitar(uid, espacioId, System.currentTimeMillis()) }
            if (r.valor != null) call.respond(r.estado, r.valor) else call.respond(r.estado, r.motivo)
        }

        get("/{id}/invitacion") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val ahora = System.currentTimeMillis()
            val r = dbQuery {
                if (espaciosSinNombres(uid).none { it.id == espacioId }) return@dbQuery null to false
                pendienteDe(uid, espacioId, ahora)?.let { InvitacionPendiente(it[InvitacionesAEspacio.venceEn]) } to true
            }
            when {
                !r.second -> call.respond(HttpStatusCode.NotFound)
                r.first == null -> call.respond(HttpStatusCode.NoContent)
                else -> call.respond(r.first!!)
            }
        }

        delete("/{id}/invitacion") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val esSuyo = dbQuery {
                val suyo = espaciosSinNombres(uid).any { it.id == espacioId }
                if (suyo) cancelarLaInvitacionPendiente(uid, espacioId)
                suyo
            }
            // Idempotente: cancelar lo que ya no está pendiente es cancelarlo.
            call.respond(if (esSuyo) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }

        delete("/{id}/conexion") {
            val uid = call.userId()
            val espacioId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val hecho = dbQuery { espaciosSinNombres(uid).any { it.id == espacioId } && desconectarElGrupo(uid, espacioId) }
            call.respond(if (hecho) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }

        post("/invitaciones/ver") {
            val uid = call.userId()
            val codigo = codigoLimpio(call.receive<CodigoDeInvitacion>().codigo)
            if (IntentosDeCodigo.agotados(uid)) return@post call.respond(HttpStatusCode.TooManyRequests, DEMASIADOS_INTENTOS)
            val r = dbQuery { verLaInvitacion(uid, codigo, System.currentTimeMillis()) }
            if (r.estado == HttpStatusCode.NotFound) IntentosDeCodigo.fallo(uid)
            if (r.valor != null) call.respond(r.estado, r.valor) else call.respond(r.estado, r.motivo)
        }

        post("/invitaciones/aceptar") {
            val uid = call.userId()
            val codigo = codigoLimpio(call.receive<CodigoDeInvitacion>().codigo)
            if (IntentosDeCodigo.agotados(uid)) return@post call.respond(HttpStatusCode.TooManyRequests, DEMASIADOS_INTENTOS)
            val r = dbQuery { aceptarLaInvitacion(uid, codigo, System.currentTimeMillis()) }
            if (r.estado == HttpStatusCode.NotFound) IntentosDeCodigo.fallo(uid)
            if (r.valor != null) call.respond(r.estado, r.valor) else call.respond(r.estado, r.motivo)
        }
    }
}

/** El mismo 404 para un código inválido, vencido, usado o cancelado: no se dice cuál. */
internal const val CODIGO_QUE_NO_SIRVE: String = "Ese código no sirve. Revisa que esté bien escrito o pide uno nuevo."

internal const val DEMASIADOS_INTENTOS: String = "Probaste muchos códigos. Espera un rato y vuelve a intentarlo."

private const val UNA_HORA_MS: Long = 60L * 60 * 1000
private const val UN_DIA_MS: Long = 24L * 60 * 60 * 1000

/**
 * **Los códigos fallidos de cada persona en la última hora.** En memoria, como `RateLimiter`: con un solo
 * proceso alcanza, y un reinicio solo regala otra hora a quien adivina (un espacio de 31⁶ ≈ 887 millones de
 * códigos a 10 por hora no se recorre). Solo cuentan los fallos: ver una invitación válida no gasta nada.
 */
internal object IntentosDeCodigo {
    private val fallos = ConcurrentHashMap<String, MutableList<Long>>()

    fun agotados(uid: String, ahora: Long = System.currentTimeMillis()): Boolean {
        val lista = fallos[uid] ?: return false
        synchronized(lista) {
            lista.removeAll { it < ahora - UNA_HORA_MS }
            return lista.size >= INTENTOS_DE_CODIGO_POR_HORA
        }
    }

    fun fallo(uid: String, ahora: Long = System.currentTimeMillis()) {
        val lista = fallos.getOrPut(uid) { mutableListOf() }
        synchronized(lista) { lista.add(ahora) }
    }

    /** Para las pruebas. */
    fun reset() = fallos.clear()
}

private val azar = SecureRandom()

/** Un código nuevo, de [ALFABETO_DEL_CODIGO]. */
internal fun codigoNuevo(): String =
    buildString { repeat(LARGO_DEL_CODIGO) { append(ALFABETO_DEL_CODIGO[azar.nextInt(ALFABETO_DEL_CODIGO.length)]) } }

/** Lo único que se guarda de un código. */
internal fun huellaDelCodigo(codigo: String): String = sha256Hex("invitacion:" + codigoLimpio(codigo))

/** La invitación pendiente del grupo [espacioId] de [uid]: sin usar, sin cancelar y sin vencer. */
private fun Transaction.pendienteDe(uid: String, espacioId: String, ahora: Long): ResultRow? =
    InvitacionesAEspacio.selectAll()
        .where {
            (InvitacionesAEspacio.userId eq uid) and (InvitacionesAEspacio.espacioId eq espacioId) and
                InvitacionesAEspacio.usadaEn.isNull() and InvitacionesAEspacio.canceladaEn.isNull()
        }
        .filter { it[InvitacionesAEspacio.venceEn] > ahora }
        .maxByOrNull { it[InvitacionesAEspacio.creadaEn] }

/** Por qué no se puede invitar desde [espacio], o `null`. */
private fun Transaction.porQueNoSeInvita(uid: String, espacio: Espacio): Rechazo? = when {
    conexionDelGrupo(uid, espacio.id) != null -> Rechazo(HttpStatusCode.Conflict, "Este grupo ya está conectado")
    espacio.miembros.count { it.id != MIEMBRO_YO } != 1 -> Rechazo(HttpStatusCode.BadRequest, POR_AHORA_GRUPOS_DE_DOS)
    else -> null
}

internal const val POR_AHORA_GRUPOS_DE_DOS: String = "Por ahora se conectan los grupos de dos"

/** `POST /api/espacios/{id}/invitacion`. */
internal fun Transaction.invitar(uid: String, espacioId: String, ahora: Long): Respuesta<InvitacionCreada> = try {
    val espacio = espaciosSinNombres(uid).firstOrNull { it.id == espacioId } ?: throw Rechazo(HttpStatusCode.NotFound, "Grupo no encontrado")
    porQueNoSeInvita(uid, espacio)?.let { throw it }
    cancelarLaInvitacionPendiente(uid, espacioId, ahora)
    val codigo = codigoNuevo()
    val vence = ahora + DIAS_QUE_VALE_LA_INVITACION * UN_DIA_MS
    InvitacionesAEspacio.insert {
        it[id] = "inv_${UUID.randomUUID()}"
        it[userId] = uid
        it[InvitacionesAEspacio.espacioId] = espacioId
        it[miembroId] = espacio.miembros.single { m -> m.id != MIEMBRO_YO }.id
        it[codigoHash] = huellaDelCodigo(codigo)
        it[creadaEn] = ahora
        it[venceEn] = vence
    }
    Respuesta(HttpStatusCode.Created, InvitacionCreada(codigo = codigo, venceEn = vence))
} catch (r: Rechazo) {
    Respuesta(r.estado, null, r.motivo)
}

/** La invitación vigente de [codigo] (con bloqueo si [paraUsarla]), o `null` si no sirve. */
private fun Transaction.invitacionVigente(codigo: String, ahora: Long, paraUsarla: Boolean): ResultRow? {
    if (!pareceUnCodigo(codigo)) return null
    val consulta = InvitacionesAEspacio.selectAll().where {
        (InvitacionesAEspacio.codigoHash eq huellaDelCodigo(codigo)) and
            InvitacionesAEspacio.usadaEn.isNull() and InvitacionesAEspacio.canceladaEn.isNull()
    }
    return (if (paraUsarla) consulta.forUpdate() else consulta).firstOrNull { it[InvitacionesAEspacio.venceEn] > ahora }
}

/**
 * El grupo de quien invita, si la invitación todavía sirve: existe, sigue siendo de dos con el mismo tercero
 * y no está conectado. Si no, el rechazo.
 */
private fun Transaction.grupoDeLaInvitacion(fila: ResultRow): Espacio {
    val quienInvita = fila[InvitacionesAEspacio.userId]
    val espacio = espaciosDe(quienInvita).firstOrNull { it.id == fila[InvitacionesAEspacio.espacioId] }
        ?: throw Rechazo(HttpStatusCode.NotFound, CODIGO_QUE_NO_SIRVE)
    if (espacio.miembros.filter { it.id != MIEMBRO_YO }.map { it.id } != listOf(fila[InvitacionesAEspacio.miembroId])) {
        throw Rechazo(HttpStatusCode.Conflict, POR_AHORA_GRUPOS_DE_DOS)
    }
    if (espacio.conexion != null) throw Rechazo(HttpStatusCode.Conflict, "Ese grupo ya está conectado")
    return espacio
}

/** `POST /api/espacios/invitaciones/ver`. */
internal fun Transaction.verLaInvitacion(uid: String, codigo: String, ahora: Long): Respuesta<InvitacionVista> = try {
    val fila = invitacionVigente(codigo, ahora, paraUsarla = false) ?: throw Rechazo(HttpStatusCode.NotFound, CODIGO_QUE_NO_SIRVE)
    val quienInvita = fila[InvitacionesAEspacio.userId]
    if (quienInvita == uid) throw Rechazo(HttpStatusCode.BadRequest, "Esa es tu propia invitación: mándasela a la otra persona")
    val espacio = grupoDeLaInvitacion(fila)
    Respuesta(HttpStatusCode.OK, InvitacionVista(nombreDelGrupo = espacio.nombre, quienInvita = nombreDelPerfil(quienInvita)))
} catch (r: Rechazo) {
    Respuesta(r.estado, null, r.motivo)
}

/**
 * `POST /api/espacios/invitaciones/aceptar`: todo en una transacción, con la invitación bloqueada. La marca de
 * usada se pone con una condición (`usada_en IS NULL`): de dos aceptaciones a la vez, una la toma y la otra no
 * encuentra nada (404).
 */
internal fun Transaction.aceptarLaInvitacion(uid: String, codigo: String, ahora: Long): Respuesta<Espacio> = try {
    val fila = invitacionVigente(codigo, ahora, paraUsarla = true) ?: throw Rechazo(HttpStatusCode.NotFound, CODIGO_QUE_NO_SIRVE)
    val quienInvita = fila[InvitacionesAEspacio.userId]
    if (quienInvita == uid) throw Rechazo(HttpStatusCode.BadRequest, "Esa es tu propia invitación: mándasela a la otra persona")
    val suGrupo = grupoDeLaInvitacion(fila)
    // Un hogar por pareja (v1): si ya están conectados en otro grupo, no se conecta otro.
    if (ladosConectadosDe(quienInvita).any { it.otroUid == uid }) {
        throw Rechazo(HttpStatusCode.Conflict, "Ya están conectados en otro grupo")
    }
    val tomada = InvitacionesAEspacio.update({
        (InvitacionesAEspacio.id eq fila[InvitacionesAEspacio.id]) and
            InvitacionesAEspacio.usadaEn.isNull() and InvitacionesAEspacio.canceladaEn.isNull()
    }) { it[usadaEn] = ahora }
    if (tomada == 0) throw Rechazo(HttpStatusCode.NotFound, CODIGO_QUE_NO_SIRVE)

    val tercero = terceroParaQuienInvita(uid, nombreDelPerfil(quienInvita), ahora)
    val nuevoId = "esp_${UUID.randomUUID()}"
    // La misma regla de reparto, vista desde quien acepta: su porcentaje es el del tercero de quien invita, y
    // el del tercero nuevo (quien invita) es el «yo» de quien invita.
    val miembros = when (suGrupo.reparto) {
        RepartoDelEspacio.PARTES_IGUALES -> listOf(MiembroDelEspacio(tercero))
        RepartoDelEspacio.PORCENTAJES -> listOf(
            MiembroDelEspacio(MIEMBRO_YO, porcentaje = suGrupo.miembros.firstOrNull { it.id == fila[InvitacionesAEspacio.miembroId] }?.porcentaje),
            MiembroDelEspacio(tercero, porcentaje = suGrupo.miembros.firstOrNull { it.id == MIEMBRO_YO }?.porcentaje),
        )
    }
    Espacios.insert {
        it[id] = nuevoId
        it[userId] = uid
        it[nombre] = suGrupo.nombre
        it[Espacios.miembros] = miembrosComoJson(miembros, suGrupo.reparto)
        it[reparto] = suGrupo.reparto.name
        it[createdAt] = ahora
    }
    EspaciosConectados.insert {
        it[id] = "con_${UUID.randomUUID()}"
        it[userA] = quienInvita
        it[espacioA] = suGrupo.id
        it[miembroA] = fila[InvitacionesAEspacio.miembroId]
        it[userB] = uid
        it[espacioB] = nuevoId
        it[miembroB] = tercero
        it[desde] = ahora
    }
    Respuesta(HttpStatusCode.OK, espaciosDe(uid).first { it.id == nuevoId })
} catch (r: Rechazo) {
    Respuesta(r.estado, null, r.motivo)
}

/**
 * El tercero de [uid] que es quien invita: uno suyo con el mismo nombre normalizado ([claveComparableDeNombre];
 * el más antiguo si hay varios) o, si no tiene, uno nuevo con [nombre], tipo persona y sin número.
 */
private fun Transaction.terceroParaQuienInvita(uid: String, nombre: String, ahora: Long): String {
    val clave = claveComparableDeNombre(nombre)
    KnownDestinations.selectAll().where { KnownDestinations.userId eq uid }
        .filter { claveComparableDeNombre(it[KnownDestinations.nombre]) == clave }
        .minByOrNull { it[KnownDestinations.createdAt] }
        ?.let { return it[KnownDestinations.id] }
    val id = "dst_${UUID.randomUUID()}".take(50)
    KnownDestinations.insert {
        it[KnownDestinations.id] = id
        it[userId] = uid
        it[KnownDestinations.nombre] = nombre.take(60)
        it[numero] = ""
        it[tipo] = TipoDeTercero.PERSONA.name
        it[createdAt] = ahora
    }
    return id
}
