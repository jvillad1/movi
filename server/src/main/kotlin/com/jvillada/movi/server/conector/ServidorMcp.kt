package com.jvillada.movi.server.conector

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * # Vuelta 135 · El protocolo MCP, escrito a mano
 *
 * **Por qué no el SDK oficial** (`io.modelcontextprotocol:kotlin-sdk-server` 0.16.0): su POM pide Ktor 3.5.2 y
 * `kotlin-stdlib` 2.4.21. Movi compila con Kotlin 2.2.21 y Ktor 3.0.3 en los cinco módulos (el cliente de Ktor
 * también va en Android, iOS y la web); subir el compilador a 2.4 y Ktor a 3.5 en toda la app para cuatro métodos de
 * JSON-RPC era mucho más riesgo que esto. Lo que Claude necesita de un servidor de solo herramientas son cuatro
 * métodos —`initialize`, `ping`, `tools/list`, `tools/call`— y las notificaciones, sobre **Streamable HTTP sin
 * estado**: cada `POST /mcp` trae un mensaje JSON-RPC y recibe su respuesta en JSON (la spec deja contestar con
 * `application/json` en vez de un flujo SSE). Sin sesión (`Mcp-Session-Id`): cada pedido se autoriza por su token.
 *
 * Un lote (un arreglo) no se acepta: la spec 2025-06-18 lo quitó.
 */
internal object ServidorMcp {
    /** Las versiones del protocolo que Movi habla, de la más nueva a la más vieja. */
    val VERSIONES: List<String> = listOf("2025-11-25", "2025-06-18", "2025-03-26")

    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    private val log = LoggerFactory.getLogger("ConectorDeClaude")

    fun error(id: JsonElement, codigo: Int, mensaje: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") { put("code", codigo); put("message", mensaje) }
    }

    private fun resultado(id: JsonElement, r: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", r)
    }

    /** Lo que pasó al llamar una herramienta, para el registro de llamadas (nunca el contenido). */
    fun interface AlLlamar {
        suspend fun anotar(herramienta: String, bytes: Int, ok: Boolean)
    }

    /**
     * **Contesta un mensaje.** `null` = era una notificación o una respuesta del cliente: no lleva respuesta (la ruta
     * contesta 202). [ejecutar] corre una herramienta para el dueño del token (el usuario no viaja en el mensaje).
     */
    suspend fun responder(
        mensaje: JsonElement,
        ejecutar: suspend (String, JsonObject) -> RespuestaDeHerramienta,
        alLlamar: AlLlamar,
    ): JsonObject? {
        if (mensaje is JsonArray) return error(JsonNull, INVALID_REQUEST, "Movi no acepta lotes: manda un mensaje por pedido.")
        val m = mensaje as? JsonObject ?: return error(JsonNull, INVALID_REQUEST, "El mensaje es un objeto JSON-RPC.")
        val id = m["id"]
        val metodo = (m["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (metodo == null) {
            // Una respuesta del cliente (a nada que Movi haya pedido): se acepta y se ignora.
            return if (m.containsKey("result") || m.containsKey("error")) null
            else error(id ?: JsonNull, INVALID_REQUEST, "Falta `method`.")
        }
        if ((m["jsonrpc"] as? JsonPrimitive)?.contentOrNull != "2.0") return error(id ?: JsonNull, INVALID_REQUEST, "`jsonrpc` es \"2.0\".")
        // Una notificación no lleva `id` ni respuesta.
        if (id == null) return null
        if (id !is JsonPrimitive || (!id.isString && id.contentOrNull?.toLongOrNull() == null && id.contentOrNull?.toDoubleOrNull() == null)) {
            return error(JsonNull, INVALID_REQUEST, "`id` es un texto o un número.")
        }
        val params = m["params"] as? JsonObject ?: JsonObject(emptyMap())
        return when (metodo) {
            "initialize" -> resultado(id, inicializar(params))
            "ping" -> resultado(id, JsonObject(emptyMap()))
            "tools/list" -> resultado(id, listaDeHerramientas())
            "tools/call" -> llamar(id, params, ejecutar, alLlamar)
            else -> error(id, METHOD_NOT_FOUND, "Movi no tiene el método «$metodo».")
        }
    }

    private fun inicializar(params: JsonObject): JsonObject {
        val pedida = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
        return buildJsonObject {
            put("protocolVersion", if (pedida in VERSIONES) pedida else VERSIONES.first())
            putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
            putJsonObject("serverInfo") {
                put("name", "movi")
                put("title", "Movi")
                put("version", System.getenv("MOVI_COMMIT_SHA")?.take(12) ?: "dev")
            }
        }
    }

    fun listaDeHerramientas(): JsonObject = buildJsonObject {
        putJsonArray("tools") {
            HERRAMIENTAS_DEL_CONECTOR.forEach { h ->
                addJsonObject {
                    put("name", h.nombre)
                    put("title", h.titulo)
                    put("description", h.descripcion)
                    put("inputSchema", h.entrada)
                    putJsonObject("annotations") {
                        put("title", h.titulo)
                        put("readOnlyHint", true)
                        put("destructiveHint", false)
                        put("idempotentHint", true)
                        put("openWorldHint", false)
                    }
                }
            }
        }
    }

    private suspend fun llamar(
        id: JsonElement,
        params: JsonObject,
        ejecutar: suspend (String, JsonObject) -> RespuestaDeHerramienta,
        alLlamar: AlLlamar,
    ): JsonObject {
        val nombre = (params["name"] as? JsonPrimitive)?.contentOrNull
        if (nombre == null || nombre !in NOMBRES_DE_LAS_HERRAMIENTAS) {
            return error(id, INVALID_PARAMS, "No existe la herramienta «${textoSeguro(nombre, 64)}». Las de Movi: ${NOMBRES_DE_LAS_HERRAMIENTAS.joinToString()}.")
        }
        val argumentos = when (val a = params["arguments"]) {
            null, JsonNull -> JsonObject(emptyMap())
            is JsonObject -> a
            else -> return error(id, INVALID_PARAMS, "`arguments` es un objeto.")
        }
        val r = runCatching { ejecutar(nombre, argumentos) }.getOrElse { e ->
            log.warn("Falló la herramienta $nombre del conector: ${e::class.simpleName}")
            RespuestaDeHerramienta(
                "No pude leer tus datos de Movi ahora. Inténtalo de nuevo en un momento.",
                buildJsonObject { put("error", "No pude leer tus datos de Movi ahora.") },
                esError = true,
            )
        }
        val datosEnTexto = r.datos.toString()
        val cuerpo = if (datosEnTexto.length > TOPE_DE_LA_RESPUESTA * 2) {
            RespuestaDeHerramienta("La respuesta es demasiado grande: pide un rango más corto.", buildJsonObject { put("error", "Respuesta demasiado grande.") }, esError = true)
        } else {
            r
        }
        val json = cuerpo.datos.toString()
        val result = buildJsonObject {
            putJsonArray("content") {
                // El texto lleva también el JSON: hay clientes que solo le pasan `content` al modelo
                // (la spec lo recomienda para compatibilidad). La primera línea es el resumen.
                addJsonObject { put("type", "text"); put("text", cuerpo.resumen + "\n" + json) }
            }
            if (cuerpo.datos is JsonObject) put("structuredContent", cuerpo.datos)
            put("isError", cuerpo.esError)
        }
        alLlamar.anotar(nombre, json.length, !cuerpo.esError)
        return resultado(id, result)
    }
}

