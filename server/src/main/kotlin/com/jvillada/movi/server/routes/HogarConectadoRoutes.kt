package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.db.Users
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
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
 *   base queda su HMAC-SHA256 con pimienta. Generar otro cancela el pendiente.
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
            call.responder(r)
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
            val intento = IntentosDeCodigo.reservar(clavesDelIntento(uid, ipDelPedido(call)))
                ?: return@post call.respond(HttpStatusCode.TooManyRequests, DEMASIADOS_INTENTOS)
            var fallo = false
            try {
                val r = dbQuery { verLaInvitacion(uid, codigo, System.currentTimeMillis()) }
                fallo = r.respuesta.estado == HttpStatusCode.NotFound && r.cuentaComoFallo
                val v = r.respuesta
                if (v.valor != null) call.respond(v.estado, v.valor) else call.respond(v.estado, v.motivo)
            } finally {
                if (!fallo) IntentosDeCodigo.soltar(intento)
            }
        }

        post("/invitaciones/aceptar") {
            val uid = call.userId()
            val codigo = codigoLimpio(call.receive<CodigoDeInvitacion>().codigo)
            val intento = IntentosDeCodigo.reservar(clavesDelIntento(uid, ipDelPedido(call)))
                ?: return@post call.respond(HttpStatusCode.TooManyRequests, DEMASIADOS_INTENTOS)
            var fallo = false
            try {
                val r = dbQuery { aceptarLaInvitacion(uid, codigo, System.currentTimeMillis()) }
                fallo = r.respuesta.estado == HttpStatusCode.NotFound && r.cuentaComoFallo
                val v = r.respuesta
                if (v.valor != null) call.respond(v.estado, v.valor) else call.respond(v.estado, v.motivo)
            } finally {
                if (!fallo) IntentosDeCodigo.soltar(intento)
            }
        }
    }
}

/** El mismo 404 para un código inválido, vencido, usado o cancelado: no se dice cuál. */
internal const val CODIGO_QUE_NO_SIRVE: String = "Ese código no sirve. Revisa que esté bien escrito o pide uno nuevo."

internal const val DEMASIADOS_INTENTOS: String = "Probaste muchos códigos. Espera un rato y vuelve a intentarlo."

private const val UNA_HORA_MS: Long = 60L * 60 * 1000
private const val UN_DIA_MS: Long = 24L * 60 * 60 * 1000

/** Cuántos códigos fallidos se aceptan por dirección IP en una hora (varias personas pueden compartirla). */
internal const val INTENTOS_DE_CODIGO_POR_IP_POR_HORA: Int = 30

/** Las dos cuentas de un intento: la de la persona y la de su IP, cada una con su tope. */
internal fun clavesDelIntento(uid: String, ip: String): Map<String, Int> =
    mapOf("usuario:$uid" to INTENTOS_DE_CODIGO_POR_HORA, "ip:${ip.take(80)}" to INTENTOS_DE_CODIGO_POR_IP_POR_HORA)

/**
 * **Los códigos fallidos de la última hora, por persona y por IP.** En memoria, como `RateLimiter`: con un
 * solo proceso alcanza, y un reinicio solo regala otra hora a quien adivina (31⁶ ≈ 887 millones de códigos a
 * 10 por hora no se recorren). **El conteo es atómico** (revisión de la vuelta 131): cada pedido **reserva**
 * su intento antes de mirar la base —con las dos cuentas bloqueadas a la vez, en orden— y lo **suelta** si no
 * resultó un fallo (una invitación válida, el propio código, la carrera de aceptar que se perdió). Así diez
 * pedidos simultáneos no se cuelan entre la pregunta y la anotación.
 */
internal object IntentosDeCodigo {
    /** Un intento: se suelta por identidad, no por hora (dos en el mismo milisegundo son dos). */
    class Intento internal constructor(val en: Long, internal val listas: List<MutableList<Intento>>)

    private val porClave = ConcurrentHashMap<String, MutableList<Intento>>()
    private val llamadas = java.util.concurrent.atomic.AtomicInteger(0)

    fun reservar(topes: Map<String, Int>, ahora: Long = System.currentTimeMillis()): Intento? {
        // Revisión 2 (N-8): cada tanto se sacan las claves sin intentos vivos (las IP se acumulaban).
        if (llamadas.incrementAndGet() % CADA_CUANTO_SE_BARRE == 0) barrer(ahora)
        while (true) {
            val listas = topes.keys.sorted().map { it to porClave.getOrPut(it) { mutableListOf() } }
            fun conTodas(i: Int, bloque: () -> Intento?): Intento? =
                if (i == listas.size) bloque() else synchronized(listas[i].second) { conTodas(i + 1, bloque) }
            var vigentes = true
            val r = conTodas(0) {
                // Si el barrido sacó una lista mientras se tomaban, se vuelve a empezar: así ningún intento queda
                // anotado en una lista que ya nadie cuenta.
                vigentes = listas.all { (clave, l) -> porClave[clave] === l }
                if (!vigentes) return@conTodas null
                listas.forEach { (_, l) -> l.removeAll { it.en < ahora - UNA_HORA_MS } }
                if (listas.any { (clave, l) -> l.size >= topes.getValue(clave) }) {
                    null
                } else {
                    Intento(ahora, listas.map { it.second }).also { intento -> listas.forEach { (_, l) -> l += intento } }
                }
            }
            if (vigentes) return r
        }
    }

    /** Saca las claves vacías o con todos sus intentos vencidos. */
    fun barrer(ahora: Long = System.currentTimeMillis()) {
        porClave.entries.removeIf { (_, l) -> synchronized(l) { l.removeAll { it.en < ahora - UNA_HORA_MS }; l.isEmpty() } }
    }

    /** Cuántas claves lleva el contador (para las pruebas). */
    fun claves(): Int = porClave.size

    private const val CADA_CUANTO_SE_BARRE = 256

    /** El intento no fue un fallo: deja de contar. */
    fun soltar(intento: Intento) {
        intento.listas.forEach { l -> synchronized(l) { l.removeAll { it === intento } } }
        // Una clave que quedó vacía no se guarda (una IP que no falló nunca no ocupa lugar).
        porClave.entries.removeIf { (_, l) -> intento.listas.any { it === l } && synchronized(l) { l.isEmpty() } }
    }

    /** Cuántos fallos cuenta [clave] ahora (para las pruebas). */
    fun cuantos(clave: String): Int = porClave[clave]?.let { synchronized(it) { it.size } } ?: 0

    /** Para las pruebas. */
    fun reset() = porClave.clear()
}

/** Lo que contesta ver o aceptar, y si cuenta como un código fallido. */
internal class ConElFallo<T : Any>(val respuesta: Respuesta<T>, val cuentaComoFallo: Boolean)

private val azar = SecureRandom()

/** Un código nuevo, de [ALFABETO_DEL_CODIGO]. */
internal fun codigoNuevo(): String =
    buildString { repeat(LARGO_DEL_CODIGO) { append(ALFABETO_DEL_CODIGO[azar.nextInt(ALFABETO_DEL_CODIGO.length)]) } }

/** De dónde salen los códigos; una prueba lo cambia para forzar una colisión. */
internal var generadorDeCodigos: () -> String = ::codigoNuevo

private val pimientaDeLasInvitaciones: ByteArray by lazy { JwtConfig.pimienta("invitaciones-a-espacio") }

/**
 * **Lo único que se guarda de un código**: su HMAC-SHA256 con una pimienta del server (revisión de la vuelta
 * 131; derivada de `JWT_SECRET`, ver `JwtConfig.pimienta`). Un SHA-256 a secas de 31⁶ códigos se recorre
 * entero en segundos si la tabla se filtra; sin la pimienta, no.
 */
internal fun huellaDelCodigo(codigo: String): String =
    Mac.getInstance("HmacSHA256")
        .apply { init(SecretKeySpec(pimientaDeLasInvitaciones, "HmacSHA256")) }
        .doFinal(codigoLimpio(codigo).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** ¿Hay una invitación vigente (sin usar, sin cancelar, sin vencer) con [huella]? */
private fun Transaction.hayVigenteCon(huella: String, ahora: Long): Boolean =
    InvitacionesAEspacio.selectAll().where {
        (InvitacionesAEspacio.codigoHash eq huella) and InvitacionesAEspacio.usadaEn.isNull() and InvitacionesAEspacio.canceladaEn.isNull()
    }.any { it[InvitacionesAEspacio.venceEn] > ahora }

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
    // Revisión de la vuelta 131: el grupo bloqueado mientras se crea, para que dos «Mandar» a la vez dejen
    // una sola invitación pendiente.
    Espacios.selectAll().where { (Espacios.id eq espacioId) and (Espacios.userId eq uid) }.forUpdate().firstOrNull()
        ?: throw Rechazo(HttpStatusCode.NotFound, "Grupo no encontrado")
    val espacio = espaciosSinNombres(uid).firstOrNull { it.id == espacioId } ?: throw Rechazo(HttpStatusCode.NotFound, "Grupo no encontrado")
    porQueNoSeInvita(uid, espacio)?.let { throw it }
    cancelarLaInvitacionPendiente(uid, espacioId, ahora)
    // Un código que ya tiene otra invitación vigente se cambia por otro (nunca dos vigentes con el mismo).
    val codigo = generateSequence { generadorDeCodigos() }.take(20).firstOrNull { !hayVigenteCon(huellaDelCodigo(it), ahora) }
        ?: throw Rechazo(HttpStatusCode.ServiceUnavailable, "No pudimos crear el código. Vuelve a intentarlo.")
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

/** `POST /api/espacios/invitaciones/ver`. Un 404 cuenta como código fallido. */
internal fun Transaction.verLaInvitacion(uid: String, codigo: String, ahora: Long): ConElFallo<InvitacionVista> = try {
    val fila = invitacionVigente(codigo, ahora, paraUsarla = false) ?: throw Rechazo(HttpStatusCode.NotFound, CODIGO_QUE_NO_SIRVE)
    val quienInvita = fila[InvitacionesAEspacio.userId]
    if (quienInvita == uid) throw Rechazo(HttpStatusCode.BadRequest, "Esa es tu propia invitación: mándasela a la otra persona")
    val espacio = grupoDeLaInvitacion(fila)
    ConElFallo(Respuesta(HttpStatusCode.OK, InvitacionVista(nombreDelGrupo = espacio.nombre, quienInvita = nombreDelPerfil(quienInvita))), false)
} catch (r: Rechazo) {
    ConElFallo(Respuesta(r.estado, null, r.motivo), cuentaComoFallo = true)
}

/** La carrera de aceptar: el código existía y lo acaba de usar otro pedido (hace menos de un minuto). */
private fun Transaction.seAcabaDeUsar(codigo: String, ahora: Long): Boolean =
    pareceUnCodigo(codigo) && InvitacionesAEspacio.selectAll()
        .where { InvitacionesAEspacio.codigoHash eq huellaDelCodigo(codigo) }
        .any { f -> f[InvitacionesAEspacio.usadaEn]?.let { kotlin.math.abs(ahora - it) <= 60_000L } == true }

/**
 * `POST /api/espacios/invitaciones/aceptar`: todo en una transacción, con la invitación bloqueada. La marca de
 * usada se pone con una condición (`usada_en IS NULL`): de dos aceptaciones a la vez, una la toma y la otra no
 * encuentra nada (404).
 */
internal fun Transaction.aceptarLaInvitacion(uid: String, codigo: String, ahora: Long): ConElFallo<Espacio> = try {
    // Revisión 2: quien ya la aceptó y reintenta (el teléfono reintenta) recibe su grupo, no «no sirve».
    yaLaAcepto(uid, codigo)?.let { throw YaLaAcepto(it) }
    // Revisión 2 (N-6): el mismo orden de bloqueo que `invitar`: primero el grupo, después la invitación.
    // Se lee sin bloquear para saber de qué grupo es, y bajo bloqueo se vuelve a validar.
    val vista = invitacionVigente(codigo, ahora, paraUsarla = false)
        ?: throw if (seAcabaDeUsar(codigo, ahora)) PerdioLaCarrera() else Rechazo(HttpStatusCode.NotFound, CODIGO_QUE_NO_SIRVE)
    val quienInvita = vista[InvitacionesAEspacio.userId]
    if (quienInvita == uid) throw Rechazo(HttpStatusCode.BadRequest, "Esa es tu propia invitación: mándasela a la otra persona")
    // Bajo bloqueo: el grupo de quien invita (si lo borró mientras tanto, 404 y no queda ninguna conexión
    // colgando), la invitación, y las dos personas en orden fijo, para que «un hogar por pareja» no se cuele
    // con dos aceptaciones de invitaciones distintas a la vez.
    Espacios.selectAll().where { (Espacios.id eq vista[InvitacionesAEspacio.espacioId]) and (Espacios.userId eq quienInvita) }
        .forUpdate().firstOrNull() ?: throw Rechazo(HttpStatusCode.NotFound, CODIGO_QUE_NO_SIRVE)
    val fila = invitacionVigente(codigo, ahora, paraUsarla = true)?.takeIf { it[InvitacionesAEspacio.id] == vista[InvitacionesAEspacio.id] }
        ?: throw if (seAcabaDeUsar(codigo, ahora)) PerdioLaCarrera() else Rechazo(HttpStatusCode.NotFound, CODIGO_QUE_NO_SIRVE)
    Users.selectAll().where { Users.id inList listOf(quienInvita, uid).sorted() }.orderBy(Users.id).forUpdate().toList()
    val suGrupo = grupoDeLaInvitacion(fila)
    // Un hogar por pareja (v1): si ya están conectados en otro grupo, no se conecta otro.
    if (ladosConectadosDe(quienInvita).any { it.otroUid == uid }) {
        throw Rechazo(HttpStatusCode.Conflict, "Ya están conectados en otro grupo")
    }
    val tomada = InvitacionesAEspacio.update({
        (InvitacionesAEspacio.id eq fila[InvitacionesAEspacio.id]) and
            InvitacionesAEspacio.usadaEn.isNull() and InvitacionesAEspacio.canceladaEn.isNull()
    }) {
        it[usadaEn] = ahora
        it[usadaPor] = uid
    }
    if (tomada == 0) throw PerdioLaCarrera()

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
    ConElFallo(Respuesta(HttpStatusCode.OK, espaciosDe(uid).first { it.id == nuevoId }), false)
} catch (r: YaLaAcepto) {
    ConElFallo(Respuesta(HttpStatusCode.OK, r.grupo), false)
} catch (r: PerdioLaCarrera) {
    // La perdió contra otra aceptación del mismo código: el mismo 404, pero no es adivinar.
    ConElFallo(Respuesta(HttpStatusCode.NotFound, null, CODIGO_QUE_NO_SIRVE), cuentaComoFallo = false)
} catch (r: Rechazo) {
    ConElFallo(Respuesta(r.estado, null, r.motivo), cuentaComoFallo = true)
}

/**
 * Si [uid] ya aceptó la invitación de [codigo] y su conexión sigue vigente, su grupo (el reintento de quien
 * ya aceptó contesta 200 con él). `null` si no.
 */
private fun Transaction.yaLaAcepto(uid: String, codigo: String): Espacio? {
    if (!pareceUnCodigo(codigo)) return null
    val usada = InvitacionesAEspacio.selectAll()
        .where { (InvitacionesAEspacio.codigoHash eq huellaDelCodigo(codigo)) and (InvitacionesAEspacio.usadaPor eq uid) }
        .firstOrNull() ?: return null
    val suGrupo = ladosConectadosDe(uid).firstOrNull {
        it.otroUid == usada[InvitacionesAEspacio.userId] && it.suEspacioId == usada[InvitacionesAEspacio.espacioId]
    } ?: return null
    return espaciosDe(uid).firstOrNull { it.id == suGrupo.lado.miEspacioId }
}

/** Quien acepta ya la había aceptado (ver [yaLaAcepto]). */
private class YaLaAcepto(val grupo: Espacio) : RuntimeException()

/** Otra aceptación del mismo código ganó (ver [aceptarLaInvitacion]). */
private class PerdioLaCarrera : RuntimeException()

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
