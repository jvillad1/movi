package com.jvillada.movi.server.routes

import com.jvillada.movi.server.conector.AccesoDelConector
import com.jvillada.movi.server.conector.ConfigDelConector
import com.jvillada.movi.server.conector.ServidorMcp
import com.jvillada.movi.server.conector.accesoDelToken
import com.jvillada.movi.server.conector.anotarUso
import com.jvillada.movi.server.conector.limitadorDelConector
import com.jvillada.movi.server.conector.claveDeIp
import com.jvillada.movi.server.conector.avisosDelConector
import com.jvillada.movi.server.conector.rangoDeAnthropic
import com.jvillada.movi.server.conector.ejecutarHerramientaDelConector
import com.jvillada.movi.server.db.LlamadasDelConector
import com.jvillada.movi.server.db.dbQuery
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.jetbrains.exposed.sql.insert
import java.util.UUID

/**
 * # Vuelta 135 · `/mcp`: el servidor MCP de Movi, solo lectura
 *
 * **Cada pedido trae su token y se verifica** (no hay sesiones): `Authorization: Bearer <token de acceso del
 * conector>`. Sin él, o con uno que no sirve, 401 con el `WWW-Authenticate` que le dice a Claude dónde está la
 * metadata del recurso (RFC 9728) — así empieza el OAuth. **El JWT de la app no entra** (no es un token del
 * conector) y el token del conector no entra a `/api/…` (no es un JWT): ver `LasConcesiones`.
 *
 * Una conexión hace hasta `LLAMADAS_POR_MINUTO` pedidos por minuto; pasado eso, 429 con `Retry-After` y un
 * mensaje que dice qué hacer.
 */
fun Route.conectorMcpRoutes(ahora: () -> Long = System::currentTimeMillis) {
    route("/mcp") {
        handle { call.atenderMcp(ahora()) }
    }
}

/** Lo más que se lee de un mensaje. Un `tools/call` de verdad pesa unos cientos de bytes. */
private const val MAX_BYTES_DEL_MENSAJE = 64 * 1024

private val jsonDelMcp = Json { ignoreUnknownKeys = true }

/** El 401 que abre el OAuth (Claude ignora el encabezado en un 200). */
private suspend fun ApplicationCall.pedirToken(conToken: Boolean) {
    val partes = buildList {
        add("resource_metadata=\"${ConfigDelConector.metadataDelRecurso}\"")
        add("scope=\"${ConfigDelConector.SCOPE_LEER}\"")
        if (conToken) add("error=\"invalid_token\"")
    }
    response.header(HttpHeaders.WWWAuthenticate, "Bearer " + partes.joinToString(", "))
    respondText(
        """{"error":"${if (conToken) "invalid_token" else "unauthorized"}","error_description":"Conecta Movi en Claude para leer tus datos."}""",
        ContentType.Application.Json,
        HttpStatusCode.Unauthorized,
    )
}

/** El token del encabezado, o `null`. Nunca de la URL: un `?access_token=` se ignora a propósito. */
private fun tokenDelEncabezado(valor: String?): String? {
    val v = valor?.trim() ?: return null
    if (!v.startsWith("Bearer ", ignoreCase = true)) return null
    return v.substring(7).trim().takeIf { it.isNotEmpty() && it.length <= 200 }
}

/**
 * M-a de la tercera revisión: cuántos 401 por minuto puede recibir una IP (o `/64`) antes de que `/mcp` deje de mirar la
 * base por ella. Cuenta **solo los 401**: el tráfico bueno de Claude, que sale de unas pocas IP, no lo toca. Las IP de
 * Anthropic **nunca se cortan** (H1 de la cuarta revisión): sus 401 se cuentan contra [MAX_401_POR_MINUTO_DE_ANTHROPIC]
 * solo para dejar un WARN con tope al pasarse.
 */
internal const val MAX_401_POR_MINUTO_POR_IP = 60
internal const val MAX_401_POR_MINUTO_DE_ANTHROPIC = 1_200

private suspend fun ApplicationCall.atenderMcp(ahora: Long) {
    val ip = ipDelPedido(this)
    val balde401 = "mcp401:ip:${claveDeIp(ip)}"
    // H1 de la cuarta revisión: por una IP de Anthropic pasa `/mcp` de TODOS los usuarios de Claude, y cualquiera con
    // una cuenta de la API manda tokens falsos desde ahí (el conector MCP de la API de mensajes). A esas IP no se las
    // corta nunca antes de la base: un token válido siempre pasa, y sus 401 solo se cuentan y dejan un WARN con tope.
    val rango = rangoDeAnthropic(ip)
    if (rango == null && limitadorDelConector.lleno(balde401, MAX_401_POR_MINUTO_POR_IP, 60_000L, ahora)) {
        response.header(HttpHeaders.RetryAfter, "60")
        return respondText(
            ServidorMcp.error(JsonNull, -32000, "Demasiados pedidos sin un token válido desde esta dirección. Espera un minuto y vuelve a conectar.").toString(),
            ContentType.Application.Json,
            HttpStatusCode.TooManyRequests,
        )
    }
    fun anotar401() {
        if (rango == null) {
            limitadorDelConector.anotar(balde401, ahora)
        } else if (!limitadorDelConector.permitir(balde401, MAX_401_POR_MINUTO_DE_ANTHROPIC, 60_000L, ahora)) {
            avisosDelConector.avisar("mcp401:$rango", ahora) {
                "Conector: /mcp recibe más de $MAX_401_POR_MINUTO_DE_ANTHROPIC respuestas 401 por minuto desde una IP de Anthropic ($rango); se siguen contestando 401, sin cortar"
            }
        }
    }
    val token = tokenDelEncabezado(request.headers[HttpHeaders.Authorization])
        ?: return anotar401().let { pedirToken(conToken = false) }
    val acceso: AccesoDelConector = dbQuery { accesoDelToken(token, ahora) }
        ?: return anotar401().let { pedirToken(conToken = true) }

    if (!limitadorDelConector.permitir("mcp:${acceso.concesionId}", ConfigDelConector.LLAMADAS_POR_MINUTO, 60_000L)) {
        response.header(HttpHeaders.RetryAfter, "60")
        return respondText(
            ServidorMcp.error(
                JsonNull,
                -32000,
                "Demasiadas consultas a Movi en un minuto (el tope es ${ConfigDelConector.LLAMADAS_POR_MINUTO}). Espera un minuto y vuelve a intentarlo.",
            ).toString(),
            ContentType.Application.Json,
            HttpStatusCode.TooManyRequests,
        )
    }
    if (request.httpMethod != HttpMethod.Post) {
        // Sin estado: no hay flujo SSE que abrir con GET ni sesión que cerrar con DELETE.
        response.header(HttpHeaders.Allow, "POST")
        return respond(HttpStatusCode.MethodNotAllowed)
    }
    if (request.contentType().withoutParameters() != ContentType.Application.Json) {
        return respondText(
            ServidorMcp.error(JsonNull, ServidorMcp.INVALID_REQUEST, "El mensaje va como application/json.").toString(),
            ContentType.Application.Json,
            HttpStatusCode.UnsupportedMediaType,
        )
    }
    val crudo = when (val leido = leerConTope(MAX_BYTES_DEL_MENSAJE)) {
        is CuerpoLeido.Texto -> leido.texto
        CuerpoLeido.Grande -> return respondText(
            ServidorMcp.error(JsonNull, ServidorMcp.INVALID_REQUEST, "El mensaje es demasiado grande.").toString(),
            ContentType.Application.Json,
            HttpStatusCode.PayloadTooLarge,
        )
        CuerpoLeido.Ilegible -> ""
    }
    val mensaje = runCatching { jsonDelMcp.parseToJsonElement(crudo) }.getOrNull()
        ?: return respondText(
            ServidorMcp.error(JsonNull, ServidorMcp.PARSE_ERROR, "El cuerpo no es JSON.").toString(),
            ContentType.Application.Json,
            HttpStatusCode.BadRequest,
        )

    dbQuery { anotarUso(acceso.concesionId, ahora) }
    val respuesta = ServidorMcp.responder(
        mensaje,
        ejecutar = { nombre, argumentos -> ejecutarHerramientaDelConector(acceso.userId, nombre, argumentos) },
        alLlamar = { herramienta, bytes, ok ->
            runCatching {
                dbQuery {
                    LlamadasDelConector.insert {
                        it[id] = "llm_" + UUID.randomUUID()
                        it[userId] = acceso.userId
                        it[concesionId] = acceso.concesionId
                        it[LlamadasDelConector.herramienta] = herramienta
                        it[en] = ahora
                        it[LlamadasDelConector.bytes] = bytes
                        it[LlamadasDelConector.ok] = ok
                    }
                }
            }
        },
    )
    response.header(HttpHeaders.CacheControl, "no-store")
    if (respuesta == null) return respond(HttpStatusCode.Accepted)
    respondText(respuesta.toString(), ContentType.Application.Json)
}
