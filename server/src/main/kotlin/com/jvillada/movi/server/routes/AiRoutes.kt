package com.jvillada.movi.server.routes

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun resolveApiKey(): String? {
    System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() && it != "x" }?.let { return it }
    val envFile = File(System.getProperty("user.dir"), "server/.env")
        .takeIf { it.exists() } ?: File(System.getProperty("user.dir"), ".env")
    return envFile.takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("ANTHROPIC_API_KEY=") }
        ?.substringAfter("=")
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "x" }
}

private val anthropicClient: AnthropicClient? by lazy {
    val key = resolveApiKey() ?: return@lazy null
    runCatching { AnthropicOkHttpClient.builder().apiKey(key).build() }.getOrNull()
}

private val PERSONA = """Sos Movi AI, un copiloto financiero personal y familiar para usuarios en Colombia.

Hablás en español relajado y directo, sin jerga financiera innecesaria. Tuteás al usuario, no uses "usted".
Montos siempre en pesos colombianos con formato ${'$'}X.XXX.XXX.

Cuando el usuario te pregunte sobre su plata, basate ÚNICAMENTE en los datos del bloque "DATOS DEL USUARIO".
Si la pregunta no se puede contestar con esos datos, decilo claramente y sugerí qué información faltaría.

Tono: directo, empático, accionable. No moralices sobre el gasto.
Estructura: respondé en máximo 4-5 frases cortas. Si la respuesta tiene un cálculo, mostralo en una línea separada.
"""

fun Route.aiRoutes() {
    post("/api/ai/chat") {
        val body = call.receive<AiChatRequest>()
        val client = anthropicClient
        if (client == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                AiChatResponse(
                    text = "ANTHROPIC_API_KEY no está configurada en el server. Setealo y reiniciá: export ANTHROPIC_API_KEY=sk-ant-... && ./gradlew :server:run",
                ),
            )
            return@post
        }

        val context = buildUserContext()
        val messageParams = body.messages.map { m ->
            val role = when (m.role) {
                ChatRole.USER -> MessageParam.Role.USER
                ChatRole.ASSISTANT -> MessageParam.Role.ASSISTANT
            }
            MessageParam.builder().role(role).content(m.content).build()
        }
        if (messageParams.isEmpty() || messageParams.last().role() != MessageParam.Role.USER) {
            call.respond(HttpStatusCode.BadRequest, AiChatResponse(text = "Último mensaje debe ser del usuario"))
            return@post
        }

        val params = MessageCreateParams.builder()
            .model("claude-opus-4-7")
            .maxTokens(1024L)
            .thinking(ThinkingConfigAdaptive.builder().build())
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder().text(PERSONA).build(),
                    TextBlockParam.builder()
                        .text(context)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                ),
            )
            .messages(messageParams)
            .build()

        val reply = runCatching {
            withContext(Dispatchers.IO) {
                val response = client.messages().create(params)
                response.content()
                    .mapNotNull { block -> block.text().orElse(null)?.text() }
                    .joinToString("\n")
                    .ifBlank { "(sin respuesta)" }
            }
        }
        reply.onSuccess { call.respond(AiChatResponse(text = it)) }
            .onFailure {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiChatResponse(text = "Error llamando a Claude: ${it.message ?: "desconocido"}"),
                )
            }
    }
}

private fun buildUserContext(): String = buildString {
    appendLine("DATOS DEL USUARIO (Camilo, Colombia)")
    appendLine()
    appendLine("== Resumen mensual (abril) ==")
    appendLine("- Balance: \$1.840.000")
    appendLine("- Ingresos: \$4.500.000")
    appendLine("- Egresos: \$2.660.000")
    appendLine("- Flujo libre: \$1.840.000")
    appendLine()
    appendLine("== Egresos por categoría (abril) ==")
    appendLine("- Mercado: \$312.400 (limite \$350.000, 89% — cerca del límite)")
    appendLine("- Salud: \$47.200 (limite \$200.000, 23%)")
    appendLine("- Restaurantes: \$42.300 (limite \$50.000, 84% — cerca del límite)")
    appendLine("- Suscripción: \$28.900 (limite \$35.000, 82% — cerca del límite)")
    appendLine("- Transporte: \$28.500 (limite \$25.000, 114% — sobrepasado)")
    appendLine()
    appendLine("== Recurrentes (mensual) ==")
    appendLine("Ingresos fijos:")
    appendLine("- Salario Globant: \$4.500.000 (día 25)")
    appendLine("Egresos fijos:")
    appendLine("- Arriendo apartamento: \$1.500.000 (día 5)")
    appendLine("- Internet Claro: \$89.000 (día 10)")
    appendLine("- Netflix: \$28.900 (día 1)")
    appendLine("- Spotify Family: \$19.900 (día 15)")
    appendLine("- Total egresos fijos: \$1.637.800")
    appendLine("- Flujo libre después de fijos: \$2.862.200")
    appendLine()
    appendLine("== Inversiones (patrimonio) ==")
    appendLine("- CDT Bancolombia: \$5.000.000 (12 meses, 11.8% E.A.)")
    appendLine("- Acciones Globales (Skandia): \$4.280.000 (renta variable, +12.4%)")
    appendLine("- Renta Fija COL (Fiduciaria): \$2.100.000 (bajo riesgo, +4.2%)")
    appendLine("- Bitcoin (Binance): \$1.100.000 (cripto, -8.6%)")
    appendLine("- Total invertido: \$12.480.000")
    appendLine()
    appendLine("== Créditos / deudas ==")
    appendLine("- Crédito de vivienda Bancolombia: \$240.000.000 total, \$86.400.000 pagado, 11.2% E.A., próxima cuota \$1.860.000 el 30 abr")
    appendLine("- Tarjeta Falabella CMR: \$4.320.000 total, \$2.680.000 pagado, 24.5% E.A., próxima cuota \$580.000 el 5 may")
    appendLine("- Libre inversión Davivienda: \$12.000.000 total, \$7.200.000 pagado, 18.9% E.A., próxima cuota \$420.000 el 15 may")
    appendLine("- Total deuda pendiente: \$159.040.000")
    appendLine()
    appendLine("== Metas de ahorro ==")
    appendLine("- Viaje a Cartagena: meta \$5.000.000, ahorrado \$3.400.000 (Junio 2026, aporte mensual \$320.000)")
    appendLine("- Cuota inicial apto: meta \$30.000.000, ahorrado \$8.600.000 (Diciembre 2027, aporte \$1.200.000)")
    appendLine("- Fondo de emergencia: meta \$12.000.000, ahorrado \$12.000.000 (COMPLETADA)")
    appendLine("- Cumpleaños Mateo: meta \$800.000, ahorrado \$220.000 (Agosto 2026, aporte \$145.000)")
}
