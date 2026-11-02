package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.auth.subirVersionDeSesiones
import com.jvillada.movi.server.db.PasswordResetTokens
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AvatarPalette
import com.jvillada.movi.shared.model.DIAS_PARA_BANCO_MUDO_POR_DEFECTO
import com.jvillada.movi.shared.model.MAX_DIAS_PARA_BANCO_MUDO
import com.jvillada.movi.shared.model.ChangePasswordRequest
import com.jvillada.movi.shared.model.MAX_INICIOS_PROPIOS
import com.jvillada.movi.shared.model.FrecuenciaDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.esPeriodoDe
import com.jvillada.movi.shared.model.frecuenciaDelPeriodoDe
import com.jvillada.movi.shared.model.periodoDelId
import com.jvillada.movi.shared.model.problemaDeLaQuincena
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.siElCorteCaeEnFinDeSemanaDe
import com.jvillada.movi.shared.model.TipoDeAhorro
import com.jvillada.movi.shared.model.problemaDelAhorro
import com.jvillada.movi.shared.model.tipoDeAhorroDe
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
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

/**
 * Ancho de `Users.name varchar(100)`. `internal` porque el registro (`AuthRoutes.kt`) usa ESTA
 * misma constante: crear y editar no pueden discrepar sobre la misma columna — hasta hace poco sí
 * lo hacían, y un nombre de 101 caracteres era un 400 al editar y un 500 al registrarse.
 */
internal const val MAX_NAME_LENGTH = 100

// Balde propio por usuario, no por correo/IP: quien llama YA está autenticado (tiene un JWT
// válido), así que la clave por usuario aísla de verdad — no hay "cuenta ajena" que golpear
// con esto, es la propia. 5 intentos / 15 min alcanza para errores de tipeo reales sin abrir
// la puerta a que alguien con un token robado martille contraseñas.
private const val MAX_PASSWORD_ATTEMPTS = 5
private const val WINDOW_PASSWORD_MS = 15 * 60_000L

/** Mensaje único para "la actual no coincide" — no distingue de ningún otro caso; no hace falta,
 *  quien llama ya está autenticado, así que no hay oráculo de enumeración que cuidar acá. */
private const val CURRENT_PASSWORD_REJECTED = "La contraseña actual no coincide"

/**
 * Encabezado con el token nuevo que devuelve el cambio de contraseña. Cambiarla cierra todas las
 * sesiones (sube `users.token_version`), incluida la del aparato desde el que se cambió; este
 * token —ya con la versión nueva— es lo que deja a ESE aparato adentro. Encabezado y no cuerpo
 * para no romper al cliente viejo, que lee el cuerpo como texto: ese simplemente no lo toma y
 * vuelve a pedir entrar, que es lo mismo que les pasa a los demás aparatos.
 */
const val ENCABEZADO_TOKEN_NUEVO = "X-Movi-Token"

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
            // Banco mudo (Ola 2): 0 lo apaga; más de un mes sin avisar ya no es un aviso.
            if (req.diasParaBancoMudo != null && req.diasParaBancoMudo !in 0..MAX_DIAS_PARA_BANCO_MUDO) {
                return@put call.respond(HttpStatusCode.BadRequest, "Los días del aviso van de 0 a $MAX_DIAS_PARA_BANCO_MUDO")
            }
            // Los arranques propios: cada clave un período «AAAA-MM» y cada valor una fecha ISO
            // real. **No se valida que la fecha caiga en el mes correcto acá**, y es a propósito:
            // esa regla vive en `inicioDelPeriodo` (:core), que ante un valor imposible vuelve al
            // corte en vez de romper. Duplicarla acá obligaría a mantener dos copias de la misma
            // decisión — y la del cliente ya la respeta.
            //
            // Vuelta 105 · **la frecuencia del período** (mes o quincena). Se valida contra lo que
            // queda guardado después de este PUT, no solo contra lo que viene: un APK viejo que no sabe
            // de quincenas puede mandar solo `periodCutoffDay`, y ese corte no puede dejar una quincena
            // imposible (se rechaza con la misma frase que ve la hoja nueva).
            val frecuenciaPedida = req.periodFrequency?.let { nombre ->
                FrecuenciaDelPeriodo.entries.firstOrNull { it.name == nombre }
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Esa frecuencia del período no existe")
            }
            if (req.periodSecondDay != null && req.periodSecondDay !in 1..31) {
                return@put call.respond(HttpStatusCode.BadRequest, "Los días de la quincena van de 1 a 31")
            }
            val guardado = dbQuery { Users.selectAll().where { Users.id eq uid }.firstOrNull() }
                ?: return@put call.respond(HttpStatusCode.NotFound)
            val frecuenciaAntes = frecuenciaDelPeriodoDe(guardado[Users.periodFrequency])
            val frecuencia = frecuenciaPedida ?: frecuenciaAntes
            val ajustesNuevos = PeriodSettings(
                cutoffDay = (req.periodCutoffDay ?: guardado[Users.periodCutoffDay] ?: 1).coerceIn(1, 31),
                frecuencia = frecuencia,
                segundoDia = req.periodSecondDay ?: guardado[Users.periodSecondDay],
            )
            if (frecuencia == FrecuenciaDelPeriodo.QUINCENAL) {
                problemaDeLaQuincena(ajustesNuevos.cutoffDay, ajustesNuevos.segundoDia)?.let {
                    return@put call.respond(HttpStatusCode.BadRequest, it)
                }
            }
            // Cambiar de frecuencia vacía las excepciones a mano: sus claves serían de la otra forma
            // («2026-10» contra «2026-10-15»). Salvo que el mismo PUT traiga las nuevas.
            val cambiaLaFrecuencia = frecuenciaPedida != null && frecuenciaPedida != frecuenciaAntes
            // Una clave de quincena bien escrita pero que ya no arranca ninguna quincena (cambiaron los días
            // A o B) no es un error de quien escribe: es una excepción vieja. Se poda en vez de rechazar
            // todo el mapa — si no, «Empezar la quincena hoy», que manda el mapa entero, quedaría en 400
            // para siempre (revisión de la 105).
            fun esQuincenaVigente(clave: String) =
                periodoDelId(clave)?.takeIf { it.dia != 0 }?.let { esPeriodoDe(it, ajustesNuevos) } == true
            val periodosMalFormados = req.periodStarts.orEmpty().filterNot { (periodo, inicio) ->
                FECHA_VALIDA.matches(inicio) && if (ajustesNuevos.esQuincenal) {
                    FECHA_VALIDA.matches(periodo)
                } else {
                    PERIODO_VALIDO.matches(periodo)
                }
            }
            if (periodosMalFormados.isNotEmpty()) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    (
                        if (ajustesNuevos.esQuincenal) "Una quincena se escribe con el día en que arranca, «AAAA-MM-DD», y su arranque también: "
                        else "Un período se escribe «AAAA-MM» y su arranque «AAAA-MM-DD»: "
                        ) + periodosMalFormados.keys.joinToString(", "),
                )
            }
            // Un tope, porque esto se guarda entero en una columna y crece solo si alguien lo
            // llena: diez años de excepciones mensuales son 120, y nadie declara más que eso.
            val iniciosPedidos = req.periodStarts?.let { pedidos ->
                if (ajustesNuevos.esQuincenal) pedidos.filterKeys(::esQuincenaVigente) else pedidos
            }
            // Y las guardadas: si este PUT cambia los días de la quincena (también un APK viejo que solo
            // manda `periodCutoffDay`), las que ya no arrancan ninguna quincena se podan al guardar.
            val iniciosGuardados = guardado[Users.periodStarts]?.let { leerIniciosPropios(it) } ?: emptyMap()
            val iniciosPodados = if (ajustesNuevos.esQuincenal && !cambiaLaFrecuencia && iniciosPedidos == null) {
                iniciosGuardados.filterKeys(::esQuincenaVigente).takeIf { it.size != iniciosGuardados.size }
            } else {
                null
            }
            if (iniciosPedidos != null && iniciosPedidos.size > MAX_INICIOS_PROPIOS) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    "Son demasiados períodos con arranque propio (máximo $MAX_INICIOS_PROPIOS)",
                )
            }
            // La regla del fin de semana: uno de los valores de :core y ningún otro. Un valor que no
            // existe se rechaza en vez de guardarse: leído después, caería callado a «ese día».
            if (req.periodWeekendRule != null &&
                SiElCorteCaeEnFinDeSemana.entries.none { it.name == req.periodWeekendRule }
            ) {
                return@put call.respond(HttpStatusCode.BadRequest, "Esa regla de fin de semana no existe")
            }
            // El ahorro de cada período: el tipo manda y el valor va con él (ver `problemaDelAhorro`,
            // la misma regla que apaga «Guardar» en la hoja). Un tipo que no existe se rechaza en vez
            // de guardarse: leído después, caería callado a «no apartas nada». Un valor sin tipo
            // tampoco: no se sabría si son pesos o un porcentaje.
            val tipoDeAhorro = req.ahorroDelPeriodoTipo?.let { nombre ->
                TipoDeAhorro.entries.firstOrNull { it.name == nombre }
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Ese tipo de ahorro no existe")
            }
            if (tipoDeAhorro == null && req.ahorroDelPeriodoValor != null) {
                return@put call.respond(HttpStatusCode.BadRequest, "Falta decir si el ahorro es un monto o un porcentaje")
            }
            if (tipoDeAhorro != null) {
                problemaDelAhorro(tipoDeAhorro, req.ahorroDelPeriodoValor)?.let {
                    return@put call.respond(HttpStatusCode.BadRequest, it)
                }
            }
            if (req.name == null && req.avatarColor == null && req.periodCutoffDay == null &&
                req.reminderLeadDays == null && req.smsAlertMuted == null && req.periodStarts == null &&
                req.diasParaBancoMudo == null && req.cierrePorCorreo == null && req.periodWeekendRule == null &&
                req.mandarUso == null && req.periodFrequency == null && req.periodSecondDay == null &&
                tipoDeAhorro == null
            ) {
                return@put call.respond(HttpStatusCode.BadRequest, "Nada para actualizar")
            }

            val updated = dbQuery {
                Users.update({ Users.id eq uid }) { stmt ->
                    trimmedName?.let { stmt[Users.name] = it }
                    req.avatarColor?.let { stmt[Users.avatarColor] = it }
                    req.periodCutoffDay?.let { stmt[Users.periodCutoffDay] = it }
                    req.periodWeekendRule?.let { stmt[Users.periodWeekendRule] = it }
                    req.periodFrequency?.let { stmt[Users.periodFrequency] = it }
                    req.periodSecondDay?.let { stmt[Users.periodSecondDay] = it }
                    if (cambiaLaFrecuencia && iniciosPedidos == null) stmt[Users.periodStarts] = "{}"
                    iniciosPodados?.let {
                        stmt[Users.periodStarts] = Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), it)
                    }
                    // Reemplaza el mapa entero, incluido el vacío: mandar `{}` es cómo se dice
                    // «ninguno arranca distinto». Ver [UpdateProfileRequest.periodStarts].
                    iniciosPedidos?.let {
                        stmt[Users.periodStarts] = Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), it)
                    }
                    req.reminderLeadDays?.let { stmt[Users.reminderLeadDays] = it }
                    // Sin rango que validar: es un sí o un no. Mandar `false` es tan válido como
                    // mandar `true` — así se vuelve a mostrar el aviso del Inicio.
                    req.smsAlertMuted?.let { stmt[Users.smsAlertMuted] = it }
                    req.diasParaBancoMudo?.let { stmt[Users.diasParaBancoMudo] = it }
                    req.cierrePorCorreo?.let { stmt[Users.cierrePorCorreo] = it }
                    req.mandarUso?.let { stmt[Users.mandarUso] = it }
                    tipoDeAhorro?.let {
                        stmt[Users.ahorroDelPeriodoTipo] = it.name
                        // «No apartar nada» guarda 0: así un valor viejo no reaparece al volver a elegir.
                        stmt[Users.ahorroDelPeriodoValor] = if (it == TipoDeAhorro.NINGUNO) 0L else req.ahorroDelPeriodoValor ?: 0L
                    }
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
            val ahora = System.currentTimeMillis()
            // Las tres cosas en la MISMA transacción: o cambia todo, o no cambia nada. Una
            // contraseña nueva con los enlaces viejos todavía vivos es peor que no haber hecho
            // nada, porque la persona se queda creyendo que cerró la puerta.
            val versionNueva = dbQuery {
                Users.update({ Users.id eq uid }) { it[passwordHash] = newHash }
                // **Cambiar la contraseña desde adentro sella los enlaces de recuperación
                // pendientes.** El camino del reset ya lo hacía (ver AuthRoutes.kt) y este no:
                // el confirm solo mira `usedAt` y `expiresAt`, así que un enlace que alguien se
                // llevó del correo seguía sirviendo DESPUÉS de que el dueño cambiara la
                // contraseña — y cambiarla es justo lo único que él puede hacer al darse cuenta.
                // La remediación no cerraba la ventana que venía a cerrar.
                PasswordResetTokens.update({
                    (PasswordResetTokens.userId eq uid) and (PasswordResetTokens.usedAt.isNull())
                }) { it[usedAt] = ahora }
                // Y las suscripciones push, por lo mismo que en el reset: si el motivo del cambio
                // es que alguien más tuvo la cuenta, ese navegador ajeno seguiría recibiendo en
                // la pantalla de bloqueo el nombre de la tarjeta y el monto de cada vencimiento.
                // El navegador propio se vuelve a suscribir con el interruptor de Perfil.
                PushSubscriptions.deleteWhere { PushSubscriptions.userId eq uid }
                // Y las sesiones abiertas en otros aparatos: si alguien más tuvo la cuenta, su
                // token deja de servir en el pedido siguiente. Ver `Users.tokenVersion`.
                subirVersionDeSesiones(uid)
            }

            // Este aparato sigue adentro con un token de la versión nueva. Ver ENCABEZADO_TOKEN_NUEVO.
            if (versionNueva != null) {
                call.response.header(
                    ENCABEZADO_TOKEN_NUEVO,
                    JwtConfig.makeToken(uid, row[Users.email], versionNueva),
                )
            }
            call.respond(
                HttpStatusCode.OK,
                "Listo, tu contraseña quedó actualizada. Los enlaces de recuperación que hayas " +
                    "pedido ya no sirven, las sesiones en tus otros aparatos se cerraron, y si " +
                    "tenías avisos en el navegador vuelve a activarlos.",
            )
        }

        /**
         * **«Cerrar sesión en todos los aparatos».** Sube `users.token_version`: desde el pedido
         * siguiente, todo token firmado antes —el del teléfono perdido, el de un navegador
         * prestado, y también el de quien lo pide— contesta 401. No devuelve token nuevo a
         * propósito: la app cierra también la sesión local y se vuelve a entrar con la contraseña.
         *
         * Suelta además las suscripciones push, por lo mismo que el cambio de contraseña: un
         * navegador ajeno seguiría recibiendo en la pantalla de bloqueo los vencimientos.
         */
        /**
         * **«Primer uso» terminada o descartada** (la guía por pasos de una cuenta nueva). Sella
         * `users.primer_uso_en` con la hora del server **solo la primera vez**: llamarlo de nuevo
         * —el «Ir a Inicio» de otro aparato, un reintento— no mueve la fecha. Sin cuerpo, y
         * devuelve el perfil como el `PUT`, para que la app no tenga que volver a pedirlo.
         */
        post("/primer-uso") {
            val uid = call.userId()
            val perfil = dbQuery {
                Users.update({ (Users.id eq uid) and Users.primerUsoEn.isNull() }) {
                    it[Users.primerUsoEn] = System.currentTimeMillis()
                }
                Users.selectAll().where { Users.id eq uid }.firstOrNull()
            } ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respond(perfil.toProfile())
        }

        /**
         * **«Qué ve la IA» visto** (vuelta 95): la hoja que explica qué se manda a Claude y qué no.
         * Sella `users.aviso_ia_visto_en` **solo la primera vez**: llamarlo de nuevo —otro aparato, un
         * reintento— no mueve la fecha. Sin cuerpo; devuelve el perfil como el `PUT`.
         */
        post("/aviso-ia") {
            val uid = call.userId()
            val perfil = dbQuery {
                Users.update({ (Users.id eq uid) and Users.avisoIaVistoEn.isNull() }) {
                    it[Users.avisoIaVistoEn] = System.currentTimeMillis()
                }
                Users.selectAll().where { Users.id eq uid }.firstOrNull()
            } ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respond(perfil.toProfile())
        }

        post("/cerrar-sesiones") {
            val uid = call.userId()
            val versionNueva = dbQuery {
                val v = subirVersionDeSesiones(uid)
                if (v != null) PushSubscriptions.deleteWhere { PushSubscriptions.userId eq uid }
                v
            } ?: return@post call.respond(HttpStatusCode.NotFound)
            call.application.log.info("cerrar-sesiones: $uid pasó a la versión $versionNueva")
            call.respond(HttpStatusCode.NoContent)
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
    // Sin elegir = el corte no se corre en fin de semana, que es como se comportó Movi siempre.
    periodWeekendRule = siElCorteCaeEnFinDeSemanaDe(this[Users.periodWeekendRule]).name,
    // Vuelta 105 · sin elegir = MENSUAL. Un APK viejo ignora los dos campos y sigue contando por mes.
    periodFrequency = frecuenciaDelPeriodoDe(this[Users.periodFrequency]).name,
    periodSecondDay = this[Users.periodSecondDay],
    // Sin elegir = lo que el server tenga configurado, que a su vez cae al default de :core.
    reminderLeadDays = this[Users.reminderLeadDays] ?: ReminderConfig.leadDays(),
    // Sin tocar = no silenciado: el aviso de captura se muestra hasta que alguien pida callarlo.
    smsAlertMuted = this[Users.smsAlertMuted] ?: false,
    // Sin elegir = el default de :core. 0 = el dueño lo apagó.
    diasParaBancoMudo = this[Users.diasParaBancoMudo] ?: DIAS_PARA_BANCO_MUDO_POR_DEFECTO,
    // Sin tocar = encendido: el resumen de cierre llega hasta que alguien lo apague.
    cierrePorCorreo = this[Users.cierrePorCorreo] ?: true,
    // Sin tocar = encendido (vuelta 100).
    mandarUso = this[Users.mandarUso] ?: true,
    // Sin tocar = la guía nunca se terminó ni se descartó.
    primerUsoEn = this[Users.primerUsoEn],
    // Sin ver = la hoja «Qué ve la IA» se abre la primera vez que algo va a Claude.
    avisoIaVistoEn = this[Users.avisoIaVistoEn],
    // Sin elegir = no aparta nada: «Te quedan» da lo de siempre.
    ahorroDelPeriodoTipo = tipoDeAhorroDe(this[Users.ahorroDelPeriodoTipo]).name,
    ahorroDelPeriodoValor = if (tipoDeAhorroDe(this[Users.ahorroDelPeriodoTipo]) == TipoDeAhorro.NINGUNO) 0L
    else this[Users.ahorroDelPeriodoValor] ?: 0L,
)

/** Un período se escribe «AAAA-MM». */
private val PERIODO_VALIDO = Regex("""\d{4}-(0[1-9]|1[0-2])""")

/** Y su arranque, «AAAA-MM-DD». El día real lo valida :core al calcular la ventana. */
private val FECHA_VALIDA = Regex("""\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])""")

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
