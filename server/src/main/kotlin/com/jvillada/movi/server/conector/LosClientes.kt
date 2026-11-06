package com.jvillada.movi.server.conector

import com.jvillada.movi.server.db.ClientesDelConector
import com.jvillada.movi.server.db.dbQuery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertIgnore
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
 * **El conector es solo para Claude** (revisiones de seguridad de la vuelta 135). Claude se presenta de dos
 * maneras, y Movi acepta las dos, pero solo si el cliente es de Claude:
 *
 * - **CIMD** (Client ID Metadata Document): el `client_id` es **exactamente** una de las URL de [CIMD_DE_CLAUDE]
 *   (más las de `CONECTOR_CIMD_PERMITIDOS`), que se baja y dice a qué direcciones se le puede devolver el código.
 *   Ni otra URL del mismo host (N3 de la re-revisión): nunca se le pide a claude.ai una ruta inventada.
 * - **DCR** (RFC 7591): el cliente se registra en `POST /oauth/register` y recibe un `client_id` nuestro, que sale
 *   de sus retornos (ver [idDelClienteDcr]).
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

/**
 * **Los CIMD de Claude**, exactos. Comprobados el 6-nov-2026 con el `HttpClient` de Java: los dos contestan 200
 * con su JSON (sin desafío de Cloudflare). El de Claude Code lo nombra la doc de conectores
 * (claude.com/docs/connectors/building/authentication); el de la web, el escritorio y el teléfono es el que esa
 * doc llama «el Client ID Metadata Document que Anthropic hospeda», y declara `https://claude.ai/api/mcp/auth_callback`.
 */
internal val CIMD_DE_CLAUDE: List<String> = listOf(
    "https://claude.ai/oauth/mcp-oauth-client-metadata",
    "https://claude.ai/oauth/claude-code-client-metadata",
)

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

/** Los CIMD aceptados: los de Claude y los de `CONECTOR_CIMD_PERMITIDOS` (URL exactas, https, separadas por comas). */
private val cimdDelEntorno: Set<String> by lazy {
    (CIMD_DE_CLAUDE + System.getenv("CONECTOR_CIMD_PERMITIDOS").orEmpty().split(',').map { it.trim() })
        .filter { it.startsWith("https://") && it.length <= 500 }
        .toSet()
}

/** Para las pruebas: URL de CIMD de más (para probar la validación del documento sin tocar la lista de Claude). */
@Volatile
internal var cimdDePrueba: Set<String> = emptySet()

internal val cimdPermitidos: Set<String> get() = cimdDelEntorno + cimdDePrueba

/** Lo que pasó al leer un CIMD. Solo [NoExiste] se recuerda como falla; una [Pasajera] no borra el último bueno. */
sealed interface LecturaDelCimd {
    data class Documento(val texto: String) : LecturaDelCimd
    /** 404/410, o un cuerpo que no se acepta (demasiado grande): la URL no sirve. */
    data object NoExiste : LecturaDelCimd
    /** Timeout, red caída, un 5xx, un 429: se vuelve a intentar la próxima vez. */
    data object Pasajera : LecturaDelCimd
}

/**
 * **Lee el documento de un cliente CIMD.** Inyectable para las pruebas: ninguna toca la red (como
 * `LectorDeCorreosRecibidos`). El tope de tiempo no depende del lector: lo pone [resolverCliente] alrededor.
 */
fun interface LectorDeCimd {
    suspend fun leer(url: String): LecturaDelCimd
}

/** Lo más que se acepta de un documento CIMD. Uno de verdad pesa menos de 1 KB. */
internal const val MAX_BYTES_DEL_CIMD = 16 * 1024

/** El tope de toda la lectura de un CIMD: conexión, cabeceras **y cuerpo**. */
internal const val TOPE_DEL_CIMD_MS = 5_000L

/**
 * **Los hilos del lector de CIMD**: cuatro, propios, como ejecutor del `HttpClient` y para el DNS. Nunca
 * `Dispatchers.IO`, que es de donde vive `dbQuery`.
 */
private val hilosDelCimd = Executors.newFixedThreadPool(4) { r ->
    Thread(r, "movi-cimd").apply { isDaemon = true }
}

/**
 * El lector de verdad:
 *
 * - **Asíncrono y con tope de todo**: `sendAsync` no bloquea ningún hilo mientras espera, y [bajarConTope] corta
 *   la operación entera (cabeceras y cuerpo) a los [TOPE_DEL_CIMD_MS]. `HttpRequest.timeout` solo cubre hasta las
 *   cabeceras: con él solo, un cuerpo que gotea dejaba un hilo bloqueado horas (C1 de la revisión).
 * - **Solo las URL de [cimdPermitidos]**, por https al 443, y nunca un host privado (se resuelve en los hilos
 *   propios y se rechaza si alguna dirección es privada).
 * - **Sin redirecciones** y con [MAX_BYTES_DEL_CIMD] de tope: el cuerpo se lee por partes y se corta al pasarse.
 */
internal object LectorDeCimdPorRed : LectorDeCimd {
    private val cliente: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofMillis(TOPE_DEL_CIMD_MS))
        .executor(hilosDelCimd)
        .build()

    override suspend fun leer(url: String): LecturaDelCimd {
        if (url !in cimdPermitidos) return LecturaDelCimd.NoExiste
        val uri = runCatching { URI(url) }.getOrNull() ?: return LecturaDelCimd.NoExiste
        if (uri.scheme != "https" || (uri.port != -1 && uri.port != 443)) return LecturaDelCimd.NoExiste
        // El DNS corre en los hilos propios y se espera sin bloquear: si no contesta, el tope de `resolverCliente`
        // corta la espera (el hilo queda ocupado, pero es uno de cuatro, no de la base).
        val publico = CompletableFuture.supplyAsync({ hostEsPublico(uri.host) }, hilosDelCimd).await()
        if (!publico) return LecturaDelCimd.Pasajera
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
 * **Baja [uri] con un tope de tiempo para todo y un tope de tamaño para el cuerpo.** 200 con un cuerpo que cabe →
 * [LecturaDelCimd.Documento]; 404/410 o un cuerpo de más → [LecturaDelCimd.NoExiste]; lo demás (otro estado, el
 * tope, la red) → [LecturaDelCimd.Pasajera]. Al vencer cancela el pedido. No bloquea hilos.
 */
internal suspend fun bajarConTope(cliente: HttpClient, uri: URI, topeMs: Long, maxBytes: Int): LecturaDelCimd {
    val pedido = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(topeMs))
        .header("Accept", "application/json")
        .GET()
        .build()
    val manejador = HttpResponse.BodyHandler<LecturaDelCimd> { info ->
        when (info.statusCode()) {
            200 -> CuerpoConTope(maxBytes)
            404, 410 -> HttpResponse.BodySubscribers.replacing<LecturaDelCimd>(LecturaDelCimd.NoExiste)
            else -> HttpResponse.BodySubscribers.replacing<LecturaDelCimd>(LecturaDelCimd.Pasajera)
        }
    }
    val futuro = cliente.sendAsync(pedido, manejador)
    return try {
        withTimeout(topeMs) { futuro.await() }.body()
    } catch (_: TimeoutCancellationException) {
        futuro.cancel(true)
        LecturaDelCimd.Pasajera
    } catch (e: kotlinx.coroutines.CancellationException) {
        futuro.cancel(true)
        throw e
    } catch (_: Exception) {
        LecturaDelCimd.Pasajera
    }
}

/** El cuerpo, por partes, cortado al pasar de [maxBytes] ([LecturaDelCimd.NoExiste]). */
private class CuerpoConTope(private val maxBytes: Int) : HttpResponse.BodySubscriber<LecturaDelCimd> {
    private val resultado = CompletableFuture<LecturaDelCimd>()
    private val partes = java.io.ByteArrayOutputStream()
    private var suscripcion: Flow.Subscription? = null

    override fun getBody(): CompletionStage<LecturaDelCimd> = resultado
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
                resultado.complete(LecturaDelCimd.NoExiste)
                return
            }
        }
    }
    override fun onError(throwable: Throwable) { resultado.complete(LecturaDelCimd.Pasajera) }
    override fun onComplete() { resultado.complete(LecturaDelCimd.Documento(partes.toByteArray().decodeToString())) }
}

/** El lector que usan las rutas; las pruebas lo cambian por uno que no toca la red. */
@Volatile
internal var lectorDeCimd: LectorDeCimd = LectorDeCimdPorRed

/** Un documento bueno se vuelve a pedir a los 10 minutos; un documento que no sirve se recuerda 5. */
private const val VIDA_DEL_CIMD_EN_MEMORIA_MS = 10L * 60 * 1000
private const val VIDA_DE_UNA_FALLA_EN_MEMORIA_MS = 5L * 60 * 1000

/** El último documento bueno sirve, ante una falla, como mucho 7 días desde que se leyó (H5 de la cuarta revisión). */
internal const val VIDA_MAXIMA_DEL_ULTIMO_BUENO_MS = 7L * 24 * 60 * 60 * 1000

/** Lo último que se supo de cada CIMD: el último bueno (que no se borra por una falla pasajera) y una falla. */
private data class LoQueSeSabeDelCimd(val bueno: ClienteDelConector?, val buenoEn: Long, val fallaEn: Long?)

private val cimdEnMemoria = ConcurrentHashMap<String, LoQueSeSabeDelCimd>()

/** Una sola lectura a la vez por `client_id`: los que llegan mientras tanto esperan la misma. */
private val cimdEnCurso = ConcurrentHashMap<String, CompletableDeferred<ClienteDelConector?>>()

internal fun olvidarLosCimdEnMemoria() {
    cimdEnMemoria.clear()
    cimdEnCurso.clear()
}

private val jsonDelCimd = Json { ignoreUnknownKeys = true }

/** ¿Es un `client_id` CIMD que Movi acepta? Exactamente una de [cimdPermitidos]. */
internal fun esClientIdCimd(clientId: String): Boolean = clientId in cimdPermitidos

/** La forma de un `client_id` de DCR: `mvc_cli_` + 32 hex (ver [idDelClienteDcr]). */
private val FORMA_DEL_ID_DCR = Regex("^mvc_cli_[0-9a-f]{32}$")

/**
 * ¿Tiene [clientId] la forma de un cliente nuestro? Un CIMD aceptado o un id de DCR. Lo que no la tiene se contesta
 * `invalid_client` **antes** de tocar cualquier balde del limitador (N1 de la re-revisión).
 */
internal fun clientIdConForma(clientId: String): Boolean = esClientIdCimd(clientId) || FORMA_DEL_ID_DCR.matches(clientId)

/**
 * **El `client_id` de un registro DCR**, derivado de sus retornos (N2 de la re-revisión): el loopback sin puerto,
 * sin repetidos y en orden. Como los retornos solo pueden ser los de Claude, hay a lo sumo siete conjuntos
 * posibles, y registrar dos veces lo mismo da el mismo cliente. Compartirlo entre usuarios es lo que ya pasa con el
 * CIMD: lo que ata cada conexión es PKCE y la concesión.
 */
internal fun idDelClienteDcr(retornosNormalizados: List<String>): String =
    "mvc_cli_" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(retornosNormalizados.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(32)

/**
 * **Guarda un cliente DCR si no estaba** (M-c de la tercera revisión): un `INSERT … ON CONFLICT DO NOTHING`
 * (`insertIgnore`), así dos registros iguales al mismo tiempo dan el mismo cliente y ninguno un 500 por la clave
 * primaria. Corre dentro de la transacción de quien llama.
 */
internal fun org.jetbrains.exposed.sql.Transaction.guardarClienteDcr(id: String, retornos: List<String>, ahora: Long) {
    ClientesDelConector.insertIgnore {
        it[ClientesDelConector.id] = id
        it[ClientesDelConector.nombre] = ""
        it[redirectUris] = Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), retornos)
        it[creadoEn] = ahora
    }
}

/** Un retorno declarado, sin el puerto si es un loopback (el puerto se ignora igual al compararlo). */
internal fun retornoNormalizado(uri: String): String =
    if (esRetornoLoopback(uri)) URI(uri).let { "http://${it.host.lowercase()}$CAMINO_DEL_LOOPBACK" } else uri

/**
 * **El cliente de [clientId]**, o `null` si no existe o no sirve. Un CIMD de [cimdPermitidos] se baja (con el tope
 * de [TOPE_DEL_CIMD_MS] pase lo que pase con el lector, una sola lectura a la vez); lo demás se busca entre los
 * registrados por DCR.
 *
 * Lo que se recuerda (N3 de la re-revisión): un documento bueno, 10 minutos, y después se pide de nuevo; si en ese
 * pedido hay una **falla pasajera**, se sigue usando el último bueno. Solo un documento que no sirve (o un 404) se
 * recuerda como falla, 5 minutos. Una cancelación de quien pide se relanza **sin guardar nada**, y los que
 * esperaban reciben el resultado en el `finally`.
 */
internal suspend fun resolverCliente(clientId: String, ahora: Long = System.currentTimeMillis()): ClienteDelConector? {
    if (clientId.isBlank() || clientId.length > 500) return null
    if (clientId.startsWith("https://")) {
        if (!esClientIdCimd(clientId)) return null
        // H5 · el último bueno se conserva como mucho 7 días desde que se leyó; después, una falla es falla.
        val sabido = cimdEnMemoria[clientId]?.let { if (it.bueno != null && ahora - it.buenoEn > VIDA_MAXIMA_DEL_ULTIMO_BUENO_MS) it.copy(bueno = null) else it }
        val fallaReciente = sabido?.fallaEn != null && ahora - sabido.fallaEn < VIDA_DE_UNA_FALLA_EN_MEMORIA_MS
        if (sabido?.bueno != null && (ahora - sabido.buenoEn < VIDA_DEL_CIMD_EN_MEMORIA_MS || fallaReciente)) return sabido.bueno
        if (fallaReciente) return null
        val nuevo = CompletableDeferred<ClienteDelConector?>()
        val enCurso = cimdEnCurso.putIfAbsent(clientId, nuevo)
        if (enCurso != null) return withTimeoutOrNull(TOPE_DEL_CIMD_MS) { enCurso.await() } ?: sabido?.bueno
        var resultado: ClienteDelConector? = sabido?.bueno
        try {
            val lectura = withTimeoutOrNull(TOPE_DEL_CIMD_MS) {
                try {
                    lectorDeCimd.leer(clientId)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    LecturaDelCimd.Pasajera
                }
            } ?: LecturaDelCimd.Pasajera
            when (lectura) {
                is LecturaDelCimd.Documento -> {
                    val cliente = clienteDelDocumentoCimd(clientId, lectura.texto)
                    if (cliente != null) {
                        cimdEnMemoria[clientId] = LoQueSeSabeDelCimd(cliente, ahora, null)
                        resultado = cliente
                    } else {
                        resultado = sinElDocumento(clientId, sabido, ahora, "un documento que no se acepta (¿HTML?)")
                    }
                }
                LecturaDelCimd.NoExiste -> resultado = sinElDocumento(clientId, sabido, ahora, "un 404 o un cuerpo de más")
                // El último bueno sigue (si hay); no se anota nada nuevo.
                LecturaDelCimd.Pasajera -> Unit
            }
            return resultado
        } finally {
            cimdEnCurso.remove(clientId, nuevo)
            nuevo.complete(resultado)
        }
    }
    if (!clientIdConForma(clientId)) return null
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

private val logDeClientes = org.slf4j.LoggerFactory.getLogger("ConectorDeClaude")

/**
 * **El CIMD contestó algo que no sirve** (M-d de la tercera revisión). Si ya había un documento bueno, se sigue usando
 * —las URL son las fijas de Claude: un 404 o una página HTML suyos durante un despliegue no son «este cliente no
 * existe»—, con un WARN, y no se vuelve a pedir en 5 minutos. Sin uno bueno, se recuerda la falla 5 minutos.
 */
private fun sinElDocumento(clientId: String, sabido: LoQueSeSabeDelCimd?, ahora: Long, que: String): ClienteDelConector? {
    val bueno = sabido?.bueno
    cimdEnMemoria[clientId] = if (bueno != null) sabido.copy(fallaEn = ahora) else LoQueSeSabeDelCimd(null, 0L, ahora)
    if (bueno != null) logDeClientes.warn("Conector: el CIMD $clientId devolvió $que; se sigue con el último documento bueno")
    return bueno
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
