package com.jvillada.movi.server.conector

import com.jvillada.movi.server.db.ClientesDelConector
import com.jvillada.movi.server.db.dbQuery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
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
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Flow

/**
 * # Vuelta 135 · Quién pide permiso: los clientes del conector
 *
 * **El conector es solo para Claude** (revisión de seguridad de la vuelta 135, C1 e I1). Claude se presenta de
 * dos maneras, y Movi acepta las dos, pero solo si el cliente es de Claude:
 *
 * - **CIMD** (Client ID Metadata Document): el `client_id` es una URL https **en `claude.ai` o `claude.com`**
 *   ([HOSTS_DE_CIMD], más los de `CONECTOR_HOSTS_DE_CIMD`), que se baja y dice a qué direcciones se le puede
 *   devolver el código. Nunca se descarga nada de un host cualquiera.
 * - **DCR** (RFC 7591): el cliente se registra en `POST /oauth/register` y recibe un `client_id` nuestro.
 *
 * En los dos casos las únicas direcciones de retorno que valen son las de Claude: [RETORNOS_DE_CLAUDE] (la web,
 * el escritorio y el teléfono) y un loopback `http://localhost|127.0.0.1:<puerto>/callback` (Claude Code). Así el
 * código de autorización nunca puede ir a otro host, aunque un cliente se llame «Claude».
 *
 * El cliente es **público** (sin secreto): lo que lo ata es PKCE y la dirección de retorno.
 */
internal data class ClienteDelConector(
    val clientId: String,
    /** Lo que el cliente dice que se llama. Dato de afuera: se guarda limpio y **nunca se muestra** (ver [nombreDelCliente]). */
    val nombreDeclarado: String,
    val redirectUris: List<String>,
    val esCimd: Boolean,
)

/** Los hosts cuyos documentos CIMD se aceptan. `CONECTOR_HOSTS_DE_CIMD` (separados por comas) agrega otros. */
internal val HOSTS_DE_CIMD: Set<String> = setOf("claude.ai", "claude.com")

/**
 * **Las direcciones de retorno de Claude** que no son loopback. La documentación de conectores de Claude da una
 * sola, la de claude.ai (sirve a la web, el escritorio, el teléfono y Cowork); no documenta una en claude.com, así
 * que no se agrega.
 */
internal val RETORNOS_DE_CLAUDE: List<String> = listOf("https://claude.ai/api/mcp/auth_callback")

/** El camino del retorno de Claude Code por loopback. */
internal const val CAMINO_DEL_LOOPBACK = "/callback"

/** Lo más largo que puede ser un retorno, pedido o declarado. Las de Claude pesan menos de 50. */
internal const val LARGO_MAXIMO_DEL_RETORNO = 300

internal val hostsDeCimdPermitidos: Set<String> by lazy {
    HOSTS_DE_CIMD + System.getenv("CONECTOR_HOSTS_DE_CIMD").orEmpty()
        .split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}

/**
 * **Lee el documento de un cliente CIMD.** Inyectable para las pruebas: ninguna toca la red (como
 * `LectorDeCorreosRecibidos`). `null` = no se pudo leer (no existe, demasiado grande, host privado). El tope de
 * tiempo no depende del lector: lo pone [resolverCliente] alrededor de la llamada.
 */
fun interface LectorDeCimd {
    suspend fun leer(url: String): String?
}

/** Lo más que se acepta de un documento CIMD. Uno de verdad pesa menos de 1 KB. */
internal const val MAX_BYTES_DEL_CIMD = 16 * 1024

/** El tope de toda la lectura de un CIMD: conexión, cabeceras **y cuerpo**. */
internal const val TOPE_DEL_CIMD_MS = 5_000L

/**
 * **Los hilos del lector de CIMD**: cuatro, propios. Nunca `Dispatchers.IO`, que es de donde vive `dbQuery`: un
 * documento que llega de a un byte por minuto no puede dejar al resto del server sin base.
 */
private val hilosDelCimd = Executors.newFixedThreadPool(4) { r ->
    Thread(r, "movi-cimd").apply { isDaemon = true }
}
internal val dispatcherDelCimd: CoroutineDispatcher = hilosDelCimd.asCoroutineDispatcher()

/**
 * El lector de verdad:
 *
 * - **Asíncrono y con tope de todo**: `sendAsync` no bloquea ningún hilo mientras espera, y [bajarConTope] corta
 *   la operación entera (cabeceras y cuerpo) a los [TOPE_DEL_CIMD_MS]. `HttpRequest.timeout` solo cubre hasta las
 *   cabeceras: con él solo, un cuerpo que gotea dejaba un hilo bloqueado horas (C1 de la revisión).
 * - **Solo https al puerto 443 y nunca un host privado**. El host ya es uno de [hostsDeCimdPermitidos]; además se
 *   resuelve y se rechaza si alguna dirección es privada. Queda la ventana del DNS entre la resolución y la
 *   conexión, mitigada por https con validación del certificado.
 * - **Sin redirecciones** y con [MAX_BYTES_DEL_CIMD] de tope: el cuerpo se lee por partes y se corta al pasarse.
 */
internal object LectorDeCimdPorRed : LectorDeCimd {
    private val cliente: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofMillis(TOPE_DEL_CIMD_MS))
        .executor(hilosDelCimd)
        .build()

    override suspend fun leer(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || (uri.port != -1 && uri.port != 443)) return null
        if (uri.host?.lowercase() !in hostsDeCimdPermitidos) return null
        // La resolución del DNS corre en los hilos propios y se espera sin bloquear: si el DNS no contesta, el
        // tope de `resolverCliente` corta la espera (el hilo queda ocupado, pero es uno de cuatro, no de la base).
        val publico = CompletableFuture.supplyAsync({ hostEsPublico(uri.host) }, hilosDelCimd).await()
        if (!publico) return null
        return bajarConTope(cliente, uri, TOPE_DEL_CIMD_MS, MAX_BYTES_DEL_CIMD)
    }

    internal fun hostEsPublico(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val direcciones = runCatching { InetAddress.getAllByName(host).toList() }.getOrNull() ?: return false
        return direcciones.isNotEmpty() && direcciones.none { esPrivada(it) }
    }

    private fun esPrivada(a: InetAddress): Boolean {
        if (a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress || a.isMulticastAddress) return true
        val b = a.address.map { it.toInt() and 0xFF }
        if (b.size == 4) {
            return (b[0] == 100 && (b[1] and 0xC0) == 64) || // 100.64.0.0/10 (CGNAT)
                (b[0] == 198 && (b[1] and 0xFE) == 18) || // 198.18.0.0/15
                (b[0] == 192 && b[1] == 0 && b[2] == 0) || // 192.0.0.0/24
                (b[0] and 0xF0) == 240 // 240.0.0.0/4
        }
        return (b[0] and 0xFE) == 0xFC || // fc00::/7 (ULA)
            (b[0] == 0xFE && (b[1] and 0xC0) == 0xC0) || // fec0::/10
            (b[0] == 0x00 && b[1] == 0x64 && b[2] == 0xFF && b[3] == 0x9B) // 64:ff9b::/96 (NAT64)
    }
}

/**
 * **Baja [uri] con un tope de tiempo para todo y un tope de tamaño para el cuerpo.** `null` si no llega a tiempo,
 * no es 200 o pesa más de [maxBytes]. Al vencer cancela el pedido. No bloquea hilos: el cuerpo llega por partes a
 * un suscriptor que corta al pasarse.
 */
internal suspend fun bajarConTope(cliente: HttpClient, uri: URI, topeMs: Long, maxBytes: Int): String? {
    val pedido = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(topeMs))
        .header("Accept", "application/json")
        .GET()
        .build()
    val manejador = HttpResponse.BodyHandler<String?> { info ->
        if (info.statusCode() != 200) HttpResponse.BodySubscribers.replacing<String?>(null) else CuerpoConTope(maxBytes)
    }
    val futuro = cliente.sendAsync(pedido, manejador)
    return try {
        withTimeout(topeMs) { futuro.await() }.body()
    } catch (_: TimeoutCancellationException) {
        futuro.cancel(true)
        null
    } catch (e: kotlinx.coroutines.CancellationException) {
        futuro.cancel(true)
        throw e
    } catch (_: Exception) {
        null
    }
}

/** El cuerpo, por partes, cortado al pasar de [maxBytes] (devuelve `null`). */
private class CuerpoConTope(private val maxBytes: Int) : HttpResponse.BodySubscriber<String?> {
    private val resultado = CompletableFuture<String?>()
    private val partes = java.io.ByteArrayOutputStream()
    private var suscripcion: Flow.Subscription? = null

    override fun getBody(): CompletionStage<String?> = resultado
    override fun onSubscribe(subscription: Flow.Subscription) {
        suscripcion = subscription
        subscription.request(Long.MAX_VALUE)
    }
    override fun onNext(item: List<ByteBuffer>) {
        for (b in item) {
            val bytes = ByteArray(b.remaining()).also { b.get(it) }
            partes.write(bytes)
            if (partes.size() > maxBytes) {
                suscripcion?.cancel()
                resultado.complete(null)
                return
            }
        }
    }
    override fun onError(throwable: Throwable) { resultado.complete(null) }
    override fun onComplete() { resultado.complete(partes.toByteArray().decodeToString()) }
}

/** El lector que usan las rutas; las pruebas lo cambian por uno que no toca la red. */
@Volatile
internal var lectorDeCimd: LectorDeCimd = LectorDeCimdPorRed

/** Un documento bueno se recuerda 10 minutos; una falla, 5 (así repetir un `client_id` malo no vuelve a bajar nada). */
private const val VIDA_DEL_CIMD_EN_MEMORIA_MS = 10L * 60 * 1000
private const val VIDA_DE_UNA_FALLA_EN_MEMORIA_MS = 5L * 60 * 1000
private const val MAX_CIMD_EN_MEMORIA = 500

/** `null` como valor = una falla recordada. */
private val cimdEnMemoria = ConcurrentHashMap<String, Pair<Long, ClienteDelConector?>>()

/** Una sola lectura a la vez por `client_id`: los que llegan mientras tanto esperan la misma. */
private val cimdEnCurso = ConcurrentHashMap<String, CompletableDeferred<ClienteDelConector?>>()

internal fun olvidarLosCimdEnMemoria() {
    cimdEnMemoria.clear()
    cimdEnCurso.clear()
}

private val jsonDelCimd = Json { ignoreUnknownKeys = true }

/**
 * ¿Es un `client_id` CIMD que Movi acepta? https al puerto 443, en uno de [hostsDeCimdPermitidos], con camino
 * (la spec lo exige), sin fragmento, sin usuario, sin `.`/`..` y de un largo razonable.
 */
internal fun esClientIdCimd(clientId: String): Boolean {
    if (clientId.length > 500 || !clientId.startsWith("https://")) return false
    val uri = runCatching { URI(clientId) }.getOrNull() ?: return false
    val camino = uri.rawPath.orEmpty()
    return uri.host?.lowercase() in hostsDeCimdPermitidos && (uri.port == -1 || uri.port == 443) &&
        uri.rawFragment == null && uri.rawUserInfo == null &&
        camino.length > 1 && camino.split('/').none { it == "." || it == ".." }
}

/**
 * **El cliente de [clientId]**, o `null` si no existe o no sirve. Un `client_id` https es CIMD (solo de Claude);
 * lo demás se busca entre los registrados por DCR. La lectura del CIMD tiene tope de [TOPE_DEL_CIMD_MS] pase lo
 * que pase con el lector, se hace una sola vez a la vez por `client_id`, y su resultado (bueno o malo) se recuerda.
 */
internal suspend fun resolverCliente(clientId: String, ahora: Long = System.currentTimeMillis()): ClienteDelConector? {
    if (clientId.isBlank() || clientId.length > 500) return null
    if (clientId.startsWith("https://")) {
        if (!esClientIdCimd(clientId)) return null
        cimdEnMemoria[clientId]?.let { (cuando, cliente) ->
            val vida = if (cliente != null) VIDA_DEL_CIMD_EN_MEMORIA_MS else VIDA_DE_UNA_FALLA_EN_MEMORIA_MS
            if (ahora - cuando < vida) return cliente
        }
        val nuevo = CompletableDeferred<ClienteDelConector?>()
        val enCurso = cimdEnCurso.putIfAbsent(clientId, nuevo)
        if (enCurso != null) return runCatching { withTimeout(TOPE_DEL_CIMD_MS) { enCurso.await() } }.getOrNull()
        val cliente = try {
            val documento = runCatching { withTimeout(TOPE_DEL_CIMD_MS) { lectorDeCimd.leer(clientId) } }.getOrNull()
            documento?.let { clienteDelDocumentoCimd(clientId, it) }
        } finally {
            cimdEnCurso.remove(clientId, nuevo)
        }
        // Un tope para que una lluvia de `client_id` distintos no llene la memoria.
        if (cimdEnMemoria.size >= MAX_CIMD_EN_MEMORIA) cimdEnMemoria.clear()
        cimdEnMemoria[clientId] = ahora to cliente
        nuevo.complete(cliente)
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
 * servir el documento de otro), traer al menos una `redirect_uri` de Claude, y ser de un cliente público: con
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

internal const val MAX_REDIRECT_URIS = 5

/** ¿Es un loopback de Claude Code? `http://localhost|127.0.0.1[:puerto]/callback`, sin consulta ni fragmento. */
internal fun esRetornoLoopback(uri: String): Boolean {
    if (uri.length > LARGO_MAXIMO_DEL_RETORNO) return false
    val u = runCatching { URI(uri) }.getOrNull() ?: return false
    val host = u.host?.lowercase()
    return u.scheme == "http" && (host == "localhost" || host == "127.0.0.1") && u.rawUserInfo == null &&
        u.rawPath == CAMINO_DEL_LOOPBACK && u.rawQuery == null && u.rawFragment == null
}

/**
 * **¿Es una dirección de retorno de Claude?** Una de [RETORNOS_DE_CLAUDE] tal cual, o un loopback de Claude Code
 * ([esRetornoLoopback]). Es lo único que se puede declarar (DCR y CIMD) y lo único a lo que se devuelve un código.
 */
internal fun redirectValidoParaDeclarar(uri: String): Boolean =
    uri.length <= LARGO_MAXIMO_DEL_RETORNO && (uri in RETORNOS_DE_CLAUDE || esRetornoLoopback(uri))

/**
 * **¿Se le puede devolver el código a [pedido]?** Tiene que ser una dirección de Claude ([redirectValidoParaDeclarar])
 * y coincidir con una declarada: exacta, salvo el puerto de un loopback (RFC 8252 §7.3; Claude Code abre uno libre
 * cada vez), con el mismo host y el mismo camino.
 */
internal fun redirectPermitido(declaradas: List<String>, pedido: String): Boolean {
    if (!redirectValidoParaDeclarar(pedido)) return false
    if (pedido in declaradas) return true
    if (!esRetornoLoopback(pedido)) return false
    val p = URI(pedido)
    return declaradas.any { d ->
        esRetornoLoopback(d) && URI(d).host.equals(p.host, ignoreCase = true)
    }
}

/** Un nombre que viene de afuera, para guardarlo: sin caracteres de control, colapsado y cortado. */
internal fun nombreLimpio(crudo: String?): String =
    crudo.orEmpty()
        .filter { it.category != CharCategory.FORMAT }
        .map { if (it.isISOControl()) ' ' else it }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)

/**
 * **Cómo se llama el cliente en la pantalla de consentimiento y en «Movi en Claude»**: por la dirección a la que
 * vuelve el código, **nunca por el nombre que el cliente dice tener** (I1 de la revisión: un DCR «Claude» con su
 * propio retorno se llevaba el código). `https://claude.ai/…` es «Claude»; un loopback, «Claude Code».
 */
internal fun nombreDelCliente(redirectUri: String): String =
    if (esRetornoLoopback(redirectUri)) "Claude Code" else "Claude"
