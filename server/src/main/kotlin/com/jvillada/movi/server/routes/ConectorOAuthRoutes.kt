package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.conector.ConfigDelConector
import com.jvillada.movi.server.conector.SolicitudFirmada
import com.jvillada.movi.server.conector.idDelClienteDcr
import com.jvillada.movi.server.conector.retornoNormalizado
import io.ktor.server.response.respond
import com.jvillada.movi.server.conector.revocarPorToken
import com.jvillada.movi.server.conector.credencialDe
import com.jvillada.movi.server.conector.claveDeIp
import com.jvillada.movi.server.conector.clientIdConForma
import com.jvillada.movi.server.conector.limitadorDelConector
import com.jvillada.movi.server.conector.FORMA_DEL_VERIFICADOR
import com.jvillada.movi.server.conector.MAX_REDIRECT_URIS
import com.jvillada.movi.server.conector.PantallaDeConsentimiento
import com.jvillada.movi.server.conector.ParDeTokens
import com.jvillada.movi.server.conector.SolicitudDelConector
import com.jvillada.movi.server.conector.canjearCodigo
import com.jvillada.movi.server.conector.concederYEmitirCodigo
import com.jvillada.movi.server.conector.nombreDelCliente
import com.jvillada.movi.server.conector.nombreLimpio
import com.jvillada.movi.server.conector.redirectPermitido
import com.jvillada.movi.server.conector.redirectValidoParaDeclarar
import com.jvillada.movi.server.conector.refrescar
import com.jvillada.movi.server.conector.resolverCliente
import com.jvillada.movi.server.db.ClientesDelConector
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.net.URLEncoder

/**
 * # Vuelta 135 · El conector de Movi para Claude: OAuth 2.1
 *
 * Movi es su propio servidor de autorización. Lo que Claude necesita para conectarse, y nada más:
 *
 * | Ruta | Qué |
 * |---|---|
 * | `GET /.well-known/oauth-protected-resource` (y `/mcp` al final) | RFC 9728: el recurso es la URL del MCP |
 * | `GET /.well-known/oauth-authorization-server` | RFC 8414: dónde autorizar, canjear y registrarse |
 * | `GET /oauth/authorize` | valida y muestra «Permitir que Claude lea tus datos de Movi» |
 * | `POST /oauth/authorize` | el formulario: correo, contraseña y «Permitir» / «Cancelar» |
 * | `POST /oauth/token` | `authorization_code` (con PKCE) y `refresh_token` (rotado) |
 * | `POST /oauth/register` | DCR (RFC 7591), para clientes que no usan CIMD |
 *
 * Fuera de `/api/` a propósito: no son rutas de la app, y el comodín de `/api/{...}` no las toca.
 * Ninguna recibe un token por la URL: el código va en la redirección, que es como OAuth lo define, y es de un uso
 * y cinco minutos.
 */
fun Route.conectorOAuthRoutes() {
    get("/.well-known/oauth-protected-resource") { call.responderJson(metadataDelRecurso()) }
    // El sondeo con el camino del recurso (RFC 9728 §3.1): Claude lo prueba si no leyó el 401.
    get("/.well-known/oauth-protected-resource/mcp") { call.responderJson(metadataDelRecurso()) }
    get("/.well-known/oauth-authorization-server") { call.responderJson(metadataDelServidor()) }

    get("/oauth/authorize") { call.autorizar(call.request.queryParameters) }
    post("/oauth/authorize") { call.resolverElConsentimiento() }
    post("/oauth/token") { call.canjear() }
    post("/oauth/register") { call.registrar() }
    post("/oauth/revoke") { call.revocar() }
}

// ── Descubrimiento ─────────────────────────────────────────────────────────────────────────────

internal fun metadataDelRecurso(): JsonObject = buildJsonObject {
    put("resource", ConfigDelConector.urlDelMcp)
    putJsonArray("authorization_servers") { add(ConfigDelConector.emisor) }
    putJsonArray("scopes_supported") { add(ConfigDelConector.SCOPE_LEER) }
    putJsonArray("bearer_methods_supported") { add("header") }
    put("resource_name", "Movi")
}

internal fun metadataDelServidor(): JsonObject {
    val base = ConfigDelConector.base
    return buildJsonObject {
        put("issuer", ConfigDelConector.emisor)
        put("authorization_endpoint", "$base/oauth/authorize")
        put("token_endpoint", "$base/oauth/token")
        put("registration_endpoint", "$base/oauth/register")
        put("revocation_endpoint", "$base/oauth/revoke")
        putJsonArray("revocation_endpoint_auth_methods_supported") { add("none") }
        putJsonArray("response_types_supported") { add("code") }
        putJsonArray("grant_types_supported") { add("authorization_code"); add("refresh_token") }
        putJsonArray("code_challenge_methods_supported") { add("S256") }
        putJsonArray("token_endpoint_auth_methods_supported") { add("none") }
        putJsonArray("scopes_supported") { add(ConfigDelConector.SCOPE_LEER); add(ConfigDelConector.SCOPE_OFFLINE) }
        put("client_id_metadata_document_supported", true)
        put("authorization_response_iss_parameter_supported", true)
    }
}

private suspend fun ApplicationCall.responderJson(cuerpo: JsonObject, estado: HttpStatusCode = HttpStatusCode.OK) {
    response.header(HttpHeaders.CacheControl, "no-store")
    respondText(cuerpo.toString(), ContentType.Application.Json, estado)
}

// ── /oauth/authorize ───────────────────────────────────────────────────────────────────────────

/** Los encabezados de toda página del consentimiento: sin iframes, sin caché, sin `Referer`. */
private fun ApplicationCall.encabezadosDeLaPantalla() {
    response.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'")
    response.header("X-Frame-Options", "DENY")
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header("Referrer-Policy", "no-referrer")
    response.header("X-Content-Type-Options", "nosniff")
}

private suspend fun ApplicationCall.pantalla(html: String, estado: HttpStatusCode = HttpStatusCode.OK) {
    encabezadosDeLaPantalla()
    respondText(html, ContentType.Text.Html.withParameter("charset", "utf-8"), estado)
}

/** `redirect_uri?code=…&state=…&iss=…` (o con `error=`). El `iss` es RFC 9207. */
internal fun retornoCon(redirectUri: String, parametros: List<Pair<String, String?>>): String {
    val consulta = (parametros + ("iss" to ConfigDelConector.emisor))
        .filter { it.second != null }
        .joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }
    return redirectUri + (if ('?' in redirectUri) "&" else "?") + consulta
}

private val FORMA_DEL_DESAFIO = Regex("^[A-Za-z0-9._~-]{43,128}$")

/**
 * **`GET /oauth/authorize`.** Primero el cliente y su `redirect_uri`: si alguno no sirve **no se redirige a
 * ningún lado** (RFC 6749 §4.1.2.1: sería un redirector abierto) y se muestra el error acá. Con esos dos bien,
 * todo otro error vuelve al cliente con `error=`. Si todo está bien, se muestra la pantalla con la solicitud
 * firmada (N2 de la re-revisión): pedir permiso no escribe nada en la base.
 */
private suspend fun ApplicationCall.autorizar(p: Parameters) {
    val clientId = p["client_id"].orEmpty()
    val redirectUri = p["redirect_uri"].orEmpty()
    if (!limitadorDelConector.permitir("authorize:ip:${claveDeIp(ipDelPedido(this))}", 60, 60_000L)) {
        return pantalla(PantallaDeConsentimiento.noSePuede(PantallaDeConsentimiento.DEMASIADOS_INTENTOS), HttpStatusCode.TooManyRequests)
    }
    val cliente = resolverCliente(clientId)
        ?: return pantalla(PantallaDeConsentimiento.noSePuede("No reconocemos la aplicación que pide permiso."), HttpStatusCode.BadRequest)
    if (redirectUri.isEmpty() || !redirectPermitido(cliente.redirectUris, redirectUri)) {
        return pantalla(PantallaDeConsentimiento.noSePuede("La dirección de regreso no es una de las que esa aplicación declaró."), HttpStatusCode.BadRequest)
    }
    val state = p["state"]
    suspend fun volverCon(error: String, descripcion: String) =
        respondRedirect(retornoCon(redirectUri, listOf("error" to error, "error_description" to descripcion, "state" to state)))

    if (state != null && state.length > 500) return volverCon("invalid_request", "state demasiado largo")
    if (p["response_type"] != "code") return volverCon("unsupported_response_type", "solo response_type=code")
    val desafio = p["code_challenge"].orEmpty()
    if (p["code_challenge_method"] != "S256" || !FORMA_DEL_DESAFIO.matches(desafio)) {
        return volverCon("invalid_request", "PKCE con code_challenge_method=S256 es obligatorio")
    }
    val pedidos = p["scope"]?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
    if (pedidos.any { it != ConfigDelConector.SCOPE_LEER && it != ConfigDelConector.SCOPE_OFFLINE }) {
        return volverCon("invalid_scope", "el único permiso es ${ConfigDelConector.SCOPE_LEER}")
    }
    p["resource"]?.let { if (it.trimEnd('/') != ConfigDelConector.urlDelMcp) return volverCon("invalid_target", "el recurso es ${ConfigDelConector.urlDelMcp}") }

    val solicitud = SolicitudDelConector(
        clientId = clientId,
        redirectUri = redirectUri,
        codeChallenge = desafio,
        state = state,
        scope = ConfigDelConector.SCOPE_LEER,
        venceEn = System.currentTimeMillis() + ConfigDelConector.VIDA_DE_LA_SOLICITUD_MS,
    )
    pantalla(PantallaDeConsentimiento.pedirPermiso(SolicitudFirmada.firmar(solicitud), solicitud.nombreDelCliente, hostDe(redirectUri)))
}

private fun hostDe(uri: String): String = runCatching { java.net.URI(uri).host }.getOrNull().orEmpty()

/** Lo más que se lee del formulario: un correo, una contraseña y un identificador. */
private const val MAX_BYTES_DEL_FORMULARIO = 8 * 1024

/**
 * **`POST /oauth/authorize`**: el dueño entró y permitió (o canceló). Las credenciales se verifican igual que en
 * `/api/auth/login` —con el mismo trabajo de BCrypt si el correo no existe, para no delatar quién tiene cuenta— y
 * con sus propios límites por IP y por correo.
 */
private suspend fun ApplicationCall.resolverElConsentimiento() {
    request.contentLength()?.let { if (it > MAX_BYTES_DEL_FORMULARIO) return pantalla(PantallaDeConsentimiento.noSePuede("El formulario es demasiado grande."), HttpStatusCode.PayloadTooLarge) }
    val form = runCatching { receiveParameters() }.getOrNull()
        ?: return pantalla(PantallaDeConsentimiento.noSePuede("No pudimos leer el formulario."), HttpStatusCode.BadRequest)
    val id = form["solicitud"].orEmpty()
    val ahora = System.currentTimeMillis()
    // La firma es nuestra y no venció; si no, nada (ni se mira la contraseña).
    val s = SolicitudFirmada.leer(id, ahora)
        ?: return pantalla(PantallaDeConsentimiento.noSePuede("Esta solicitud venció o no es de Movi."), HttpStatusCode.BadRequest)
    val host = hostDe(s.redirectUri)

    if (form["decision"] == "cancelar") {
        return respondRedirect(retornoCon(s.redirectUri, listOf("error" to "access_denied", "error_description" to "el dueño no lo permitió", "state" to s.state)))
    }

    val correo = form["correo"].orEmpty().trim().lowercase()
    val contrasena = form["contrasena"].orEmpty()
    fun deNuevo(error: String) = PantallaDeConsentimiento.pedirPermiso(id, s.nombreDelCliente, host, error, correo.take(MAX_EMAIL_LENGTH))

    if (correo.length > MAX_EMAIL_LENGTH) return pantalla(deNuevo(PantallaDeConsentimiento.CREDENCIALES_MALAS), HttpStatusCode.Unauthorized)
    if (!limitadorDelConector.permitir("login:ip:${claveDeIp(ipDelPedido(this))}", 30, 5 * 60_000L) ||
        // M2 · el MISMO balde por correo que `/api/auth/login`: entre los dos, 10 intentos por cuenta cada 5 minutos.
        !RateLimiter.allow("login:email:$correo", 10, 5 * 60_000L)
    ) {
        return pantalla(deNuevo(PantallaDeConsentimiento.DEMASIADOS_INTENTOS), HttpStatusCode.TooManyRequests)
    }
    // El techo de BCrypt (72 bytes), como en el login: una más larga nunca pudo ser la correcta.
    if (contrasena.isEmpty() || PasswordPolicy.byteLength(contrasena) > PasswordPolicy.MAX_BYTES) {
        return pantalla(deNuevo(PantallaDeConsentimiento.CREDENCIALES_MALAS), HttpStatusCode.Unauthorized)
    }
    val fila = dbQuery { Users.selectAll().where { Users.email eq correo }.firstOrNull() }
    val bien = if (fila == null) {
        BCrypt.verifyer().verify(contrasena.toCharArray(), DUMMY_PASSWORD_HASH)
        false
    } else {
        BCrypt.verifyer().verify(contrasena.toCharArray(), fila[Users.passwordHash]).verified
    }
    if (!bien || fila == null) return pantalla(deNuevo(PantallaDeConsentimiento.CREDENCIALES_MALAS), HttpStatusCode.Unauthorized)

    val uid = fila[Users.id]
    // Cada «Permitir» con la contraseña da un código nuevo, de un solo uso y atado al mismo desafío PKCE.
    val codigo = dbQuery { concederYEmitirCodigo(uid, s, ahora) }
    respondRedirect(retornoCon(s.redirectUri, listOf("code" to codigo, "state" to s.state)))
}

// ── /oauth/token ───────────────────────────────────────────────────────────────────────────────

private suspend fun ApplicationCall.errorDeToken(error: String, descripcion: String, estado: HttpStatusCode = HttpStatusCode.BadRequest) {
    response.header("Pragma", "no-cache")
    responderJson(buildJsonObject { put("error", error); put("error_description", descripcion) }, estado)
}

/**
 * **`POST /oauth/token`** (form-urlencoded). Todo lo que no sirve —código vencido, gastado o ajeno, PKCE malo,
 * retorno distinto, refresco ya cambiado— es `invalid_grant`, con la misma frase: el cliente vuelve a pedir
 * permiso, que es lo único que puede hacer.
 */
private suspend fun ApplicationCall.canjear() {
    // N1 · un balde por IP (el /64 en IPv6) delante de todo: ningún pedido llega a la base ni a otro balde sin pasarlo.
    if (!limitadorDelConector.permitir("token:ip:${claveDeIp(ipDelPedido(this))}", LIMITE_DE_TOKEN_POR_IP, 60_000L)) {
        return errorDeToken("slow_down", "demasiados pedidos; espera un minuto", HttpStatusCode.TooManyRequests)
    }
    if (request.contentType().withoutParameters() != ContentType.Application.FormUrlEncoded) {
        return errorDeToken("invalid_request", "el cuerpo va como application/x-www-form-urlencoded")
    }
    request.contentLength()?.let { if (it > MAX_BYTES_DEL_FORMULARIO) return errorDeToken("invalid_request", "cuerpo demasiado grande") }
    val p = runCatching { receiveParameters() }.getOrNull() ?: return errorDeToken("invalid_request", "cuerpo ilegible")
    val clientId = p["client_id"].orEmpty()
    // N1 · la forma del cliente antes de usarlo como clave de nada.
    if (!clientIdConForma(clientId)) return errorDeToken("invalid_client", "cliente desconocido", HttpStatusCode.Unauthorized)
    p["resource"]?.let { if (it.trimEnd('/') != ConfigDelConector.urlDelMcp) return errorDeToken("invalid_target", "el recurso es ${ConfigDelConector.urlDelMcp}") }
    // M3 y N1 · el balde del cliente y el de la concesión cuentan SOLO pedidos con una credencial que existe y es de
    // ese cliente: un atacante sin un código o un refresco de verdad no puede llenar el balde de Claude.
    if (!permitirCredencial(p["code"] ?: p["refresh_token"], clientId, "token")) {
        return errorDeToken("slow_down", "demasiados pedidos para esta conexión; espera un minuto", HttpStatusCode.TooManyRequests)
    }
    val ahora = System.currentTimeMillis()
    val resultado: Result<ParDeTokens> = when (p["grant_type"]) {
        "authorization_code" -> {
            val verificador = p["code_verifier"].orEmpty()
            if (!FORMA_DEL_VERIFICADOR.matches(verificador)) return errorDeToken("invalid_grant", "code_verifier inválido")
            dbQuery { canjearCodigo(p["code"].orEmpty(), clientId, p["redirect_uri"].orEmpty(), verificador, ahora) }
        }
        "refresh_token" -> dbQuery { refrescar(p["refresh_token"].orEmpty(), clientId, ahora) }
        else -> return errorDeToken("unsupported_grant_type", "solo authorization_code y refresh_token")
    }
    resultado
        .onSuccess { par ->
            response.header("Pragma", "no-cache")
            responderJson(
                buildJsonObject {
                    put("access_token", par.acceso)
                    put("token_type", "Bearer")
                    put("expires_in", ConfigDelConector.VIDA_DEL_ACCESO_MS / 1000)
                    put("refresh_token", par.refresco)
                    put("scope", par.scope)
                },
            )
        }
        .onFailure { errorDeToken("invalid_grant", "el código o el token ya no sirve; vuelve a conectar") }
}

// ── /oauth/register (DCR) ──────────────────────────────────────────────────────────────────────

private val jsonDelRegistro = Json { ignoreUnknownKeys = true }

/**
 * `/oauth/token` y `/oauth/revoke`: pedidos por minuto por IP (o `/64`), por `client_id` (compartido por todos los
 * usuarios de Claude; solo cuenta credenciales que existen) y por concesión. Elegidos, no medidos.
 */
internal const val LIMITE_DE_TOKEN_POR_IP = 120
internal const val LIMITE_DE_TOKEN_POR_CLIENTE = 1_000
internal const val LIMITE_DE_TOKEN_POR_CONCESION = 20

/**
 * Los baldes de quien trae una credencial: si [credencial] existe y es de [clientId], cuenta contra el balde del
 * cliente y el de su concesión. Una credencial que no existe no cuenta en ninguno (la para el balde por IP).
 */
private suspend fun permitirCredencial(credencial: String?, clientId: String, ruta: String): Boolean {
    val dueno = credencial?.takeIf { it.length <= 200 }?.let { dbQuery { credencialDe(it) } } ?: return true
    if (dueno.second != clientId) return true
    return limitadorDelConector.permitir("$ruta:cliente:$clientId", LIMITE_DE_TOKEN_POR_CLIENTE, 60_000L) &&
        limitadorDelConector.permitir("$ruta:concesion:${dueno.first}", LIMITE_DE_TOKEN_POR_CONCESION, 60_000L)
}


/**
 * **`POST /oauth/register`** (RFC 7591). Solo clientes públicos (`token_endpoint_auth_method: none`), con retornos
 * https o de loopback. Lo que declara el cliente se guarda limpio y no se cree: el nombre no decide nada (ver
 * `nombreDelCliente`).
 */
private suspend fun ApplicationCall.registrar() {
    suspend fun mal(error: String, descripcion: String) =
        responderJson(buildJsonObject { put("error", error); put("error_description", descripcion) }, HttpStatusCode.BadRequest)

    // N2 · sin límite global (un registro ya no agrega filas: ver idDelClienteDcr); solo un balde por IP (o /64).
    if (!limitadorDelConector.permitir("register:ip:${claveDeIp(ipDelPedido(this))}", 30, 60_000L)) {
        return responderJson(buildJsonObject { put("error", "slow_down"); put("error_description", "demasiados registros; espera") }, HttpStatusCode.TooManyRequests)
    }
    val crudo = when (val leido = leerConTope(MAX_BYTES_DEL_FORMULARIO)) {
        is CuerpoLeido.Texto -> leido.texto
        else -> return mal("invalid_client_metadata", "cuerpo demasiado grande o ilegible")
    }
    val objeto = runCatching { jsonDelRegistro.parseToJsonElement(crudo) as? JsonObject }.getOrNull()
        ?: return mal("invalid_client_metadata", "el cuerpo es un objeto JSON")
    val uris = (objeto["redirect_uris"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    if (uris.isEmpty() || uris.size > MAX_REDIRECT_URIS || uris.any { !redirectValidoParaDeclarar(it) }) {
        return mal("invalid_redirect_uri", "redirect_uris: solo https://claude.ai/api/mcp/auth_callback o http://localhost|127.0.0.1:<puerto>/callback")
    }
    val metodo = (objeto["token_endpoint_auth_method"] as? JsonPrimitive)?.contentOrNull
    if (metodo != null && metodo != "none") return mal("invalid_client_metadata", "solo clientes públicos (token_endpoint_auth_method=none)")
    val nombre = nombreLimpio((objeto["client_name"] as? JsonPrimitive)?.contentOrNull)
    val ahora = System.currentTimeMillis()
    // N2 · idempotente: el `client_id` sale de los retornos (sin el puerto del loopback, sin repetidos, en orden), y
    // registrar lo mismo otra vez no escribe nada. A lo sumo siete filas en total, así que no hace falta ningún tope.
    val retornos = uris.map(::retornoNormalizado).distinct().sorted()
    val id = idDelClienteDcr(retornos)
    dbQuery {
        if (ClientesDelConector.selectAll().where { ClientesDelConector.id eq id }.empty()) {
            ClientesDelConector.insert {
                it[ClientesDelConector.id] = id
                it[ClientesDelConector.nombre] = ""
                it[redirectUris] = Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), retornos)
                it[creadoEn] = ahora
            }
        }
    }
    responderJson(
        buildJsonObject {
            put("client_id", id)
            put("client_id_issued_at", ahora / 1000)
            put("client_name", nombre)
            putJsonArray("redirect_uris") { uris.forEach { add(it) } }
            put("token_endpoint_auth_method", "none")
            putJsonArray("grant_types") { add("authorization_code"); add("refresh_token") }
            putJsonArray("response_types") { add("code") }
            put("scope", ConfigDelConector.SCOPE_LEER)
        },
        HttpStatusCode.Created,
    )
}

// ── /oauth/revoke (RFC 7009) ───────────────────────────────────────────────────────────────────

/**
 * **`POST /oauth/revoke`** (form: `token`, `client_id`). Quitar el conector en Claude corta la conexión en Movi
 * también. Siempre 200, exista o no el token (RFC 7009 §2.2): no se le dice a nadie qué tokens existen.
 */
private suspend fun ApplicationCall.revocar() {
    if (!limitadorDelConector.permitir("revoke:ip:${claveDeIp(ipDelPedido(this))}", LIMITE_DE_TOKEN_POR_IP, 60_000L)) {
        return errorDeToken("slow_down", "demasiados pedidos; espera un minuto", HttpStatusCode.TooManyRequests)
    }
    if (request.contentType().withoutParameters() != ContentType.Application.FormUrlEncoded) {
        return errorDeToken("invalid_request", "el cuerpo va como application/x-www-form-urlencoded")
    }
    request.contentLength()?.let { if (it > MAX_BYTES_DEL_FORMULARIO) return errorDeToken("invalid_request", "cuerpo demasiado grande") }
    val p = runCatching { receiveParameters() }.getOrNull() ?: return errorDeToken("invalid_request", "cuerpo ilegible")
    val token = p["token"].orEmpty()
    val clientId = p["client_id"].orEmpty()
    if (token.isEmpty() || clientId.isEmpty()) return errorDeToken("invalid_request", "faltan token y client_id")
    if (!clientIdConForma(clientId)) return errorDeToken("invalid_client", "cliente desconocido", HttpStatusCode.Unauthorized)
    if (!permitirCredencial(token, clientId, "revoke")) {
        return errorDeToken("slow_down", "demasiados pedidos de este cliente; espera un minuto", HttpStatusCode.TooManyRequests)
    }
    dbQuery { revocarPorToken(token, clientId, System.currentTimeMillis()) }
    response.header(HttpHeaders.CacheControl, "no-store")
    respond(HttpStatusCode.OK)
}
