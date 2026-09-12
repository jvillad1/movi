package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AvatarPalette
import com.jvillada.movi.shared.model.ChangePasswordRequest
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import com.jvillada.movi.server.reminders.ReminderConfig
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * F42 · F46 — editar perfil. Hasta la Ola 6 el usuario tenía id, correo, nombre y contraseña, y
 * NINGÚN endpoint para leer ni cambiar nada de eso fuera de register/login. Perfil parecía
 * editable pero no había nada detrás. Acá está lo que faltaba: leer el perfil, cambiar
 * alias/color, y cambiar contraseña estando adentro (distinto del reset de `AuthRoutes.kt`, que
 * es para cuando no se puede entrar).
 */

private const val MAX_NAME_LENGTH = 100

// Balde propio por usuario, no por correo/IP: quien llama YA está autenticado (tiene un JWT
// válido), así que la clave por usuario aísla de verdad — no hay "cuenta ajena" que golpear
// con esto, es la propia. 5 intentos / 15 min alcanza para errores de tipeo reales sin abrir
// la puerta a que alguien con un token robado martille contraseñas.
private const val MAX_PASSWORD_ATTEMPTS = 5
private const val WINDOW_PASSWORD_MS = 15 * 60_000L

/** Mensaje único para "la actual no coincide" — no distingue de ningún otro caso; no hace falta,
 *  quien llama ya está autenticado, así que no hay oráculo de enumeración que cuidar acá. */
private const val CURRENT_PASSWORD_REJECTED = "La contraseña actual no coincide"

fun Route.userRoutes() {
    route("/api/users/me") {

        get {
            val uid = call.userId()
            val row = dbQuery {
                Users.selectAll().where { Users.id eq uid }.firstOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(row.toProfile())
        }

        put {
            val uid = call.userId()
            val req = call.receive<UpdateProfileRequest>()

            // `name` opcional: solo se valida y se toca si vino en el body. Recortado (una
            // persona que escribe "  Juan  " no debería terminar con espacios en su alias).
            val trimmedName = req.name?.trim()
            if (req.name != null && (trimmedName.isNullOrBlank() || trimmedName.length > MAX_NAME_LENGTH)) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    "El nombre no puede estar vacío ni superar los $MAX_NAME_LENGTH caracteres",
                )
            }
            // AvatarPalette.isValid: NUNCA hex libre — ver el porqué en AvatarPalette.kt (:core).
            if (req.avatarColor != null && !AvatarPalette.isValid(req.avatarColor)) {
                return@put call.respond(HttpStatusCode.BadRequest, "Ese color no está en la paleta disponible")
            }
            // El corte del período: 1..31. El recorte a los meses cortos lo hace :core al
            // calcular la ventana (un 31 vale en febrero y se usa el 28), así que acá solo se
            // valida el rango — rechazar el 31 sería impedirle al dueño el corte que sí tiene.
            if (req.periodCutoffDay != null && req.periodCutoffDay !in 1..31) {
                return@put call.respond(HttpStatusCode.BadRequest, "El día de corte va de 1 a 31")
            }
            // 0 es válido: «avísame el mismo día». El tope de 30 evita un aviso que llegue antes
            // de que el mes anterior haya cerrado.
            if (req.reminderLeadDays != null && req.reminderLeadDays !in 0..30) {
                return@put call.respond(HttpStatusCode.BadRequest, "Los días de aviso van de 0 a 30")
            }
            // Los arranques propios: cada clave un período «AAAA-MM» y cada valor una fecha ISO
            // real. **No se valida que la fecha caiga en el mes correcto acá**, y es a propósito:
            // esa regla vive en `inicioDelPeriodo` (:core), que ante un valor imposible vuelve al
            // corte en vez de romper. Duplicarla acá obligaría a mantener dos copias de la misma
            // decisión — y la del cliente ya la respeta.
            val periodosMalFormados = req.periodStarts.orEmpty().filterNot { (periodo, inicio) ->
                PERIODO_VALIDO.matches(periodo) && FECHA_VALIDA.matches(inicio)
            }
            if (periodosMalFormados.isNotEmpty()) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    "Un período se escribe «AAAA-MM» y su arranque «AAAA-MM-DD»: " +
                        periodosMalFormados.keys.joinToString(", "),
                )
            }
            // Un tope, porque esto se guarda entero en una columna y crece solo si alguien lo
            // llena: diez años de excepciones mensuales son 120, y nadie declara más que eso.
            val iniciosPedidos = req.periodStarts
            if (iniciosPedidos != null && iniciosPedidos.size > MAX_INICIOS_PROPIOS) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    "Son demasiados períodos con arranque propio (máximo $MAX_INICIOS_PROPIOS)",
                )
            }
            if (req.name == null && req.avatarColor == null && req.periodCutoffDay == null &&
                req.reminderLeadDays == null && req.smsAlertMuted == null && req.periodStarts == null
            ) {
                return@put call.respond(HttpStatusCode.BadRequest, "Nada para actualizar")
            }

            val updated = dbQuery {
                Users.update({ Users.id eq uid }) { stmt ->
                    trimmedName?.let { stmt[Users.name] = it }
                    req.avatarColor?.let { stmt[Users.avatarColor] = it }
                    req.periodCutoffDay?.let { stmt[Users.periodCutoffDay] = it }
                    // Reemplaza el mapa entero, incluido el vacío: mandar `{}` es cómo se dice
                    // «ninguno arranca distinto». Ver [UpdateProfileRequest.periodStarts].
                    iniciosPedidos?.let {
                        stmt[Users.periodStarts] = Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), it)
                    }
                    req.reminderLeadDays?.let { stmt[Users.reminderLeadDays] = it }
                    // Sin rango que validar: es un sí o un no. Mandar `false` es tan válido como
                    // mandar `true` — así se vuelve a mostrar el aviso del Inicio.
                    req.smsAlertMuted?.let { stmt[Users.smsAlertMuted] = it }
                }
                Users.selectAll().where { Users.id eq uid }.firstOrNull()
            } ?: return@put call.respond(HttpStatusCode.NotFound)

            call.respond(updated.toProfile())
        }

        put("/password") {
            val uid = call.userId()
            if (!RateLimiter.allow("change-password:$uid", maxAttempts = MAX_PASSWORD_ATTEMPTS, windowMs = WINDOW_PASSWORD_MS)) {
                return@put call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos, espera unos minutos")
            }

            val req = call.receive<ChangePasswordRequest>()

            // Mismo techo que en login (ver AuthRoutes.kt): `at.favre.lib:bcrypt` lanza sobre 72
            // bytes UTF-8 en vez de truncar. Va ANTES de tocar la base — una `current` gigante
            // no puede ser la correcta de todas formas (nunca se pudo haber registrado así).
            if (PasswordPolicy.byteLength(req.current) > PasswordPolicy.MAX_BYTES) {
                return@put call.respond(HttpStatusCode.Forbidden, CURRENT_PASSWORD_REJECTED)
            }

            // La política SÍ se aplica acá (a diferencia del login) porque acá SÍ estamos
            // fijando una contraseña nueva — el servidor es la autoridad, la del cliente es
            // cortesía. Antes de tocar la base: una `new` que no cumple no debería ni gastar
            // el BCrypt.verifyer() de abajo.
            PasswordPolicy.problemWith(req.new)?.let { problem ->
                return@put call.respond(HttpStatusCode.BadRequest, PasswordPolicy.messageFor(problem))
            }

            val row = dbQuery {
                Users.selectAll().where { Users.id eq uid }.firstOrNull()
            } ?: return@put call.respond(HttpStatusCode.NotFound)

            val verified = BCrypt.verifyer().verify(req.current.toCharArray(), row[Users.passwordHash]).verified
            if (!verified) {
                return@put call.respond(HttpStatusCode.Forbidden, CURRENT_PASSWORD_REJECTED)
            }

            val newHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, req.new.toCharArray())
            dbQuery { Users.update({ Users.id eq uid }) { it[passwordHash] = newHash } }

            call.respond(HttpStatusCode.OK, "Listo, tu contraseña quedó actualizada.")
        }
    }
}

private fun ResultRow.toProfile() = UserProfile(
    id = this[Users.id],
    email = this[Users.email],
    name = this[Users.name],
    avatarColor = this[Users.avatarColor] ?: AvatarPalette.DEFAULT,
    // Sin elegir = mes de calendario, que es como se comportó Movi siempre.
    periodCutoffDay = this[Users.periodCutoffDay] ?: 1,
    // Sin excepciones declaradas = todos los períodos salen del día de corte. Un JSON que no se
    // pueda leer se trata igual que uno vacío: el perfil tiene que poder responderse siempre, y
    // un mapa roto no puede dejar al dueño sin app.
    periodStarts = this[Users.periodStarts]?.let { leerIniciosPropios(it) } ?: emptyMap(),
    // Sin elegir = lo que el server tenga configurado, que a su vez cae al default de :core.
    reminderLeadDays = this[Users.reminderLeadDays] ?: ReminderConfig.leadDays(),
    // Sin tocar = no silenciado: el aviso de captura se muestra hasta que alguien pida callarlo.
    smsAlertMuted = this[Users.smsAlertMuted] ?: false,
)

/** Un período se escribe «AAAA-MM». */
private val PERIODO_VALIDO = Regex("""\d{4}-(0[1-9]|1[0-2])""")

/** Y su arranque, «AAAA-MM-DD». El día real lo valida :core al calcular la ventana. */
private val FECHA_VALIDA = Regex("""\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])""")

/** Diez años de excepciones mensuales. Nadie declara más, y el tope evita que la columna crezca sola. */
private const val MAX_INICIOS_PROPIOS = 120

/**
 * El JSON de la columna, o un mapa vacío si no se entiende.
 *
 * **Nunca lanza**, y ese es el punto: este valor se lee en cada respuesta de perfil, y un JSON roto
 * —escrito a mano, migrado a medias— dejaría al dueño sin poder abrir la app por una excepción que
 * se puede escribir como «no hay ninguno».
 */
private fun leerIniciosPropios(json: String): Map<String, String> = runCatching {
    Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json)
}.getOrElse { emptyMap() }
