package com.jvillada.movi.server.reminders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Minimal Resend email client using the JDK built-in java.net.http.HttpClient.
 * No new Gradle dependencies required.
 *
 * Reads RESEND_API_KEY and REMINDER_FROM from the environment (or server/.env / .env files).
 * Returns false (and logs) on non-2xx or exception; never throws.
 */
object ResendClient {

    private val log = LoggerFactory.getLogger(ResendClient::class.java)

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    /**
     * Send an HTML email via Resend.
     *
     * @param to      recipient email address
     * @param subject email subject
     * @param html    HTML body content
     * @param apiKey  Resend API key (caller obtains from env)
     * @param from    sender address, e.g. "movi <reminders@yourdomain.com>"
     * @return true if the API returned 2xx, false otherwise
     */
    suspend fun sendEmail(
        to: String,
        subject: String,
        html: String,
        apiKey: String,
        from: String,
    ): Boolean = try {
        val body = buildJsonBody(from, to, subject, html)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() in 200..299) {
            log.info("ResendClient: email sent (status=${response.statusCode()})")
            true
        } else {
            log.warn("ResendClient: non-2xx send: status=${response.statusCode()}")
            false
        }
    } catch (e: Exception) {
        log.error("ResendClient: exception sending email: ${e.message}", e)
        false
    }

    /**
     * Build the Resend JSON payload, safely escaping string values.
     * Using manual construction here to avoid adding a dependency; all user-controlled
     * strings are passed through [jsonEscape] to prevent JSON injection.
     */
    private fun buildJsonBody(from: String, to: String, subject: String, html: String): String {
        return """{"from":"${jsonEscape(from)}","to":["${jsonEscape(to)}"],"subject":"${jsonEscape(subject)}","html":"${jsonEscape(html)}"}"""
    }

    /** Escape a string value for safe embedding in a JSON string literal. */
    internal fun jsonEscape(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '"'  -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '' -> append("\\f")  // form feed
                else -> if (c.code < 0x20) {
                    append("\\u%04x".format(c.code))
                } else {
                    append(c)
                }
            }
        }
    }
}
