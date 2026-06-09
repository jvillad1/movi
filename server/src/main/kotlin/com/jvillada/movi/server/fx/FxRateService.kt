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

/**
 * Official Colombian TRM (USD→COP) from datos.gov.co, cached one calendar day,
 * with a fallback chain so a failed fetch never produces a wrong estimate.
 */
object FxRateService {

    // Most-recent TRM row, newest first.
    private const val URL =
        "https://www.datos.gov.co/resource/32sa-8pi3.json?%24order=vigenciadesde%20DESC&%24limit=1"
    private const val FALLBACK_RATE = 4000.0

    private val http: HttpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var cachedRate: Double? = null
    @Volatile private var cachedDay: Long = -1

    /** Latest USD→COP rate. Cached per day; falls back to last value, then [envRate], then constant. */
    suspend fun usdToCop(): Double {
        val today = System.currentTimeMillis() / 86_400_000L
        cachedRate?.let { if (cachedDay == today) return it }
        val fetched = withContext(Dispatchers.IO) { runCatching { fetchTrm() }.getOrNull() }
        if (fetched != null) {
            cachedRate = fetched
            cachedDay = today
            return fetched
        }
        return cachedRate ?: envRate() ?: FALLBACK_RATE
    }

    private fun fetchTrm(): Double? {
        val req = HttpRequest.newBuilder(URI.create(URL)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) return null
        return parseTrm(res.body())
    }

    /** Pure parser for a Socrata TRM JSON array. Returns the newest row's `valor`, or null. */
    fun parseTrm(body: String): Double? = runCatching {
        val arr = json.parseToJsonElement(body).jsonArray
        arr.maxByOrNull { it.jsonObject["vigenciadesde"]?.jsonPrimitive?.content ?: "" }
            ?.jsonObject?.get("valor")?.jsonPrimitive?.content?.toDouble()
    }.getOrNull()

    private fun envRate(): Double? =
        System.getenv("USD_COP_RATE")?.toDoubleOrNull()
}
