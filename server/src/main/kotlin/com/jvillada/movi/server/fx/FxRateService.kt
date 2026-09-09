package com.jvillada.movi.server.fx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * **La TRM con la que se convirtió algo, y de dónde salió.**
 *
 * `esRespaldo = true` significa que [valor] es la **constante del código**
 * (`FxRateService.FALLBACK_RATE`, $4.000), o sea el último eslabón de la cadena: ni la fuente
 * oficial, ni la caché del día, ni `USD_COP_RATE`. Es un número plausible y **no es una tasa**:
 * nadie lo eligió para hoy.
 *
 * La distinción existe porque [FxRateService.usdToCop] **nunca falla** —siempre devuelve un
 * `Double` positivo—, así que quien convierta con ella no puede saber si el resultado es un dato o
 * un relleno. Para un total estimado de saldos eso está bien (mejor una estimación que un hueco);
 * para un mínimo de tarjeta que se **resta del disponible del mes** no, y por eso
 * `virtualRuleForCard` prefiere no convertir antes que convertir con esto: la pantalla ya sabe
 * decir «este total está incompleto», y no sabe decir «este total está inventado».
 *
 * `USD_COP_RATE` cuenta como tasa real y no como respaldo: alguien la escribió a propósito en la
 * configuración del despliegue. La constante no la eligió nadie.
 */
data class TasaUsdCop(val valor: Double, val esRespaldo: Boolean)

/**
 * Official Colombian TRM (USD→COP) from datos.gov.co, cached one calendar day,
 * with a fallback chain so a failed/implausible fetch never produces a wrong estimate.
 */
object FxRateService {

    private const val SOCRATA_DATASET = "32sa-8pi3" // TRM — Superfinanciera (datos.gov.co)
    private const val URL =
        "https://www.datos.gov.co/resource/$SOCRATA_DATASET.json?%24order=vigenciadesde%20DESC&%24limit=1"
    private const val FALLBACK_RATE = 4000.0
    private const val MIN_PLAUSIBLE_RATE = 100.0
    private const val MAX_PLAUSIBLE_RATE = 20_000.0
    private val TIMEOUT = Duration.ofSeconds(5)

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private data class Cached(val rate: Double, val day: Long)
    @Volatile private var cache: Cached? = null

    /**
     * Latest USD→COP rate. Cached per day; falls back to last value, then [envRate], then constant.
     *
     * **Nunca devuelve un valor no positivo**, y por eso quien necesite saber si la tasa es real
     * tiene que usar [tasaUsdCop] — un `<= 0.0` como señal de «no hay tasa» es una rama muerta.
     */
    suspend fun usdToCop(): Double = tasaUsdCop().valor

    /**
     * La misma tasa que [usdToCop], **diciendo de dónde salió**. Ver [TasaUsdCop].
     *
     * Los tres primeros eslabones son tasas de verdad: la del día, la del último día que se pudo
     * consultar (que envejece, pero la escribió la Superfinanciera) y la que alguien configuró en
     * `USD_COP_RATE`. El cuarto es la constante del código, y ese se marca.
     */
    suspend fun tasaUsdCop(): TasaUsdCop {
        val today = System.currentTimeMillis() / 86_400_000L
        cache?.let { if (it.day == today) return TasaUsdCop(it.rate, esRespaldo = false) }
        val fetched = withContext(Dispatchers.IO) { runCatching { fetchTrm() }.getOrNull() }
        if (fetched != null) cache = Cached(fetched, today)
        return resolverTasa(fetched = fetched, cacheada = cache?.rate, delEntorno = envRate())
    }

    /**
     * **La cadena de respaldo, sin red y sin reloj**, para poder probarla: cuál de los cuatro
     * eslabones ganó decide si el número es una tasa o un relleno, y esa es la única parte de
     * [tasaUsdCop] que tiene una decisión adentro.
     */
    internal fun resolverTasa(fetched: Double?, cacheada: Double?, delEntorno: Double?): TasaUsdCop = when {
        fetched != null -> TasaUsdCop(fetched, esRespaldo = false)
        cacheada != null -> TasaUsdCop(cacheada, esRespaldo = false)
        delEntorno != null -> TasaUsdCop(delEntorno, esRespaldo = false)
        else -> TasaUsdCop(FALLBACK_RATE, esRespaldo = true)
    }

    private fun fetchTrm(): Double? {
        val req = HttpRequest.newBuilder(URI.create(URL)).timeout(TIMEOUT).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) return null
        return parseTrm(res.body())
    }

    /** Pure parser for a Socrata TRM JSON array. Returns the newest plausible `valor`, or null. */
    fun parseTrm(body: String): Double? = runCatching {
        val arr = json.parseToJsonElement(body).jsonArray
        val rate = arr.maxByOrNull { it.jsonObject["vigenciadesde"]?.jsonPrimitive?.content ?: "" }
            ?.jsonObject?.get("valor")?.jsonPrimitive?.content?.toDouble()
        rate?.takeIf { it in MIN_PLAUSIBLE_RATE..MAX_PLAUSIBLE_RATE }
    }.getOrNull()

    private fun envRate(): Double? = System.getenv("USD_COP_RATE")?.toDoubleOrNull()
}
