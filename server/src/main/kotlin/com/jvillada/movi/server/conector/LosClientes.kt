package com.jvillada.movi.server.conector

import com.jvillada.movi.server.db.ClientesDelConector
import com.jvillada.movi.server.db.dbQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * # Vuelta 135 · Quién pide permiso: los clientes del conector
 *
 * Claude se presenta de dos maneras, y Movi acepta las dos:
 *
 * - **CIMD** (Client ID Metadata Document): el `client_id` es una URL https —la de Claude es
 *   `https://claude.ai/oauth/…`— que se baja y dice cómo se llama el cliente y a qué direcciones se le puede
 *   devolver el código. Es lo que Claude prefiere y no deja nada en la base.
 * - **DCR** (RFC 7591): el cliente se registra en `POST /oauth/register` y recibe un `client_id` nuestro.
 *
 * En los dos casos el cliente es **público** (sin secreto): lo que lo ata es PKCE y que el código solo se
 * devuelve a una dirección declarada, con coincidencia exacta (salvo el puerto de un loopback de Claude Code).
 */
internal data class ClienteDelConector(
    val clientId: String,
    /** Lo que el cliente dice que se llama. Dato de afuera: limpio, recortado, y nunca la única señal. */
    val nombreDeclarado: String,
    val redirectUris: List<String>,
    val esCimd: Boolean,
)

/**
 * **Lee el documento de un cliente CIMD.** Inyectable para las pruebas: ninguna toca la red (como
 * `LectorDeCorreosRecibidos`). `null` = no se pudo leer (no existe, timeout, demasiado grande, host privado).
 */
fun interface LectorDeCimd {
    suspend fun leer(url: String): String?
}

/** Lo más que se acepta de un documento CIMD. Uno de verdad pesa menos de 1 KB. */
internal const val MAX_BYTES_DEL_CIMD = 16 * 1024

/**
 * El lector de verdad. Tres cuidados, porque es una URL que manda un desconocido y el server la va a buscar:
 *
 * - **Solo https y nunca un host privado** (loopback, red local, link-local, multicast, ULA): sin esto, el
 *   `client_id` sería una forma de que el server de Movi le pegue a `http://169.254.169.254` o a la base. El host
 *   se resuelve antes y se rechaza si **alguna** de sus direcciones es privada. Queda una ventana (el DNS podría
 *   cambiar entre la resolución y la conexión); es aceptable para un GET sin credenciales cuyo cuerpo solo se
 *   lee como JSON.
 * - **Sin redirecciones**: una redirección es otra URL que nadie validó.
 * - **Tope de tiempo y de tamaño**: 5 s y [MAX_BYTES_DEL_CIMD].
 */
internal object LectorDeCimdPorRed : LectorDeCimd {
    private val cliente: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override suspend fun leer(url: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val uri = URI(url)
            if (uri.scheme != "https" || !hostEsPublico(uri.host)) return@runCatching null
            val pedido = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build()
            val respuesta = cliente.send(pedido, HttpResponse.BodyHandlers.ofInputStream())
            if (respuesta.statusCode() != 200) {
                respuesta.body().close()
                return@runCatching null
            }
            respuesta.body().use { cuerpo ->
                val bytes = cuerpo.readNBytes(MAX_BYTES_DEL_CIMD + 1)
                if (bytes.size > MAX_BYTES_DEL_CIMD) null else bytes.decodeToString()
            }
        }.getOrNull()
    }

    internal fun hostEsPublico(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val direcciones = runCatching { InetAddress.getAllByName(host).toList() }.getOrNull() ?: return false
        return direcciones.isNotEmpty() && direcciones.none { esPrivada(it) }
    }

    private fun esPrivada(a: InetAddress): Boolean {
        if (a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress || a.isMulticastAddress) return true
        val b = a.address
        // IPv6 ULA (fc00::/7) y IPv4 de CGNAT (100.64.0.0/10), que `isSiteLocalAddress` no cubre.
        if (b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC) return true
        if (b.size == 4 && (b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 64) return true
        return false
    }
}

/** El lector que usan las rutas; las pruebas lo cambian por uno que no toca la red. */
@Volatile
internal var lectorDeCimd: LectorDeCimd = LectorDeCimdPorRed

/** Diez minutos de memoria por documento: Claude lo pide en `/authorize` y otra vez nunca. */
private const val VIDA_DEL_CIMD_EN_MEMORIA_MS = 10L * 60 * 1000
private val cimdEnMemoria = ConcurrentHashMap<String, Pair<Long, ClienteDelConector>>()

private const val MAX_CIMD_EN_MEMORIA = 500

internal fun olvidarLosCimdEnMemoria() = cimdEnMemoria.clear()

private val jsonDelCimd = Json { ignoreUnknownKeys = true }

/**
 * ¿Es un `client_id` con forma de URL CIMD? https, con host y con camino (la spec lo exige), sin fragmento, sin
 * usuario, sin `.`/`..` en el camino, y de un largo razonable.
 */
internal fun esClientIdCimd(clientId: String): Boolean {
    if (clientId.length > 500 || !clientId.startsWith("https://")) return false
    val uri = runCatching { URI(clientId) }.getOrNull() ?: return false
    val camino = uri.rawPath.orEmpty()
    return !uri.host.isNullOrBlank() && uri.rawFragment == null && uri.rawUserInfo == null &&
        camino.length > 1 && camino.split('/').none { it == "." || it == ".." }
}

/**
 * **El cliente de [clientId]**, o `null` si no existe o su documento no sirve. Un `client_id` https es CIMD; lo
 * demás se busca entre los registrados por DCR.
 */
internal suspend fun resolverCliente(clientId: String, ahora: Long = System.currentTimeMillis()): ClienteDelConector? {
    if (clientId.isBlank() || clientId.length > 500) return null
    if (clientId.startsWith("https://")) {
        if (!esClientIdCimd(clientId)) return null
        cimdEnMemoria[clientId]?.let { (cuando, cliente) -> if (ahora - cuando < VIDA_DEL_CIMD_EN_MEMORIA_MS) return cliente }
        val documento = lectorDeCimd.leer(clientId) ?: return null
        val cliente = clienteDelDocumentoCimd(clientId, documento) ?: return null
        // Un tope para que una lluvia de `client_id` distintos no llene la memoria (cada uno costó un GET válido).
        if (cimdEnMemoria.size >= MAX_CIMD_EN_MEMORIA) cimdEnMemoria.clear()
        cimdEnMemoria[clientId] = ahora to cliente
        return cliente
    }
    return dbQuery {
        ClientesDelConector.selectAll().where { ClientesDelConector.id eq clientId }.firstOrNull()?.let { fila ->
            ClienteDelConector(
                clientId = fila[ClientesDelConector.id],
                nombreDeclarado = fila[ClientesDelConector.nombre],
                redirectUris = runCatching { jsonDelCimd.decodeFromString<List<String>>(fila[ClientesDelConector.redirectUris]) }
                    .getOrDefault(emptyList()),
                esCimd = false,
            )
        }
    }
}

/**
 * **Lee y valida un documento CIMD.** Tiene que decir que es ese mismo `client_id` (si no, cualquiera podría
 * servir el documento de otro), traer al menos una `redirect_uri` válida, y ser de un cliente público: con
 * `token_endpoint_auth_method` distinto de `none` no se acepta, porque Movi no verifica secretos ni firmas.
 */
internal fun clienteDelDocumentoCimd(clientId: String, documento: String): ClienteDelConector? {
    val objeto = runCatching { jsonDelCimd.parseToJsonElement(documento) as? JsonObject }.getOrNull() ?: return null
    val dice = (objeto["client_id"] as? JsonPrimitive)?.contentOrNull ?: return null
    if (dice != clientId) return null
    val metodo = (objeto["token_endpoint_auth_method"] as? JsonPrimitive)?.contentOrNull
    if (metodo != null && metodo != "none") return null
    val uris = (objeto["redirect_uris"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: return null
    if (uris.isEmpty() || uris.size > MAX_REDIRECT_URIS || uris.any { !redirectValidoParaDeclarar(it) }) return null
    val nombre = (objeto["client_name"] as? JsonPrimitive)?.contentOrNull
    return ClienteDelConector(clientId, nombreLimpio(nombre), uris, esCimd = true)
}

internal const val MAX_REDIRECT_URIS = 10

/**
 * ¿Se puede declarar esta dirección de retorno? Absoluta, sin fragmento ni usuario, de largo razonable, y https
 * —o http solo a un loopback, que es lo que usa Claude Code—. Nada de esquemas propios (`movi://`): ningún cliente
 * de Claude los usa y son la puerta clásica de un robo de código en el teléfono.
 */
internal fun redirectValidoParaDeclarar(uri: String): Boolean {
    if (uri.length > 500) return false
    val u = runCatching { URI(uri) }.getOrNull() ?: return false
    if (!u.isAbsolute || u.rawFragment != null || u.rawUserInfo != null || u.host.isNullOrBlank()) return false
    return u.scheme == "https" || (u.scheme == "http" && esLoopback(u.host))
}

/**
 * **¿Se le puede devolver el código a [pedido]?** Coincidencia exacta con una declarada. La única excepción es la
 * de RFC 8252 §7.3 para Claude Code: un `http://localhost/callback` (o 127.0.0.1) declarado acepta cualquier
 * puerto, porque Claude Code abre uno libre cada vez. Mismo esquema, mismo host, mismo camino y misma consulta.
 */
internal fun redirectPermitido(declaradas: List<String>, pedido: String): Boolean {
    if (pedido in declaradas) return true
    val p = runCatching { URI(pedido) }.getOrNull() ?: return false
    if (p.scheme != "http" || !esLoopback(p.host) || p.rawFragment != null || p.rawUserInfo != null) return false
    return declaradas.any { d ->
        val u = runCatching { URI(d) }.getOrNull() ?: return@any false
        u.scheme == "http" && u.host.equals(p.host, ignoreCase = true) &&
            u.rawPath.orEmpty() == p.rawPath.orEmpty() && u.rawQuery == p.rawQuery
    }
}

/** Un nombre que viene de afuera, para guardarlo y mostrarlo: sin caracteres de control, colapsado y cortado. */
internal fun nombreLimpio(crudo: String?): String =
    crudo.orEmpty()
        .filter { it.category != CharCategory.FORMAT }
        .map { if (it.isISOControl()) ' ' else it }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)

/**
 * **Cómo se llama el cliente en la pantalla de consentimiento y en «Movi en Claude».** El nombre que declara un
 * cliente lo escribe él, así que solo «Claude» y «Claude Code» se dicen por el **dueño de la dirección**: un CIMD
 * servido desde `claude.ai`, o un retorno a `https://claude.ai/…` (el código solo puede llegar ahí). Lo demás va
 * con su nombre limpio, o con el host si no dijo ninguno; la pantalla de consentimiento muestra siempre, además,
 * el host al que vuelve.
 */
internal fun nombreDelCliente(cliente: ClienteDelConector, redirectUri: String): String {
    val hostDelId = if (cliente.esCimd) runCatching { URI(cliente.clientId).host?.lowercase() }.getOrNull() else null
    val hostDelRetorno = runCatching { URI(redirectUri).host?.lowercase() }.getOrNull()
    fun esDeAnthropic(host: String?) = host != null && (host == "claude.ai" || host == "claude.com" || host.endsWith(".claude.ai"))
    val dice = cliente.nombreDeclarado
    return when {
        esDeAnthropic(hostDelId) && cliente.clientId.contains("claude-code", ignoreCase = true) -> "Claude Code"
        esDeAnthropic(hostDelId) -> "Claude"
        esDeAnthropic(hostDelRetorno) -> "Claude"
        dice.isNotBlank() -> dice
        else -> hostDelRetorno ?: "Un cliente de MCP"
    }.take(60)
}
