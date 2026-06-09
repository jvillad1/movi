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

    /** Latest USD→COP rate. Cached per day; falls back to last value, then [envRate], then constant. */
    suspend fun usdToCop(): Double {
        val today = System.currentTimeMillis() / 86_400_000L
        cache?.let { if (it.day == today) return it.rate }
        val fetched = withContext(Dispatchers.IO) { runCatching { fetchTrm() }.getOrNull() }
        if (fetched != null) {
            cache = Cached(fetched, today)
            return fetched
        }
        return cache?.rate ?: envRate() ?: FALLBACK_RATE
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
