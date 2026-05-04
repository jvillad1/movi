package com.jvillada.movi.server.parsing

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

object ClaudeStatementParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: AnthropicClient? by lazy {
        val key = resolveApiKey() ?: return@lazy null
        runCatching { AnthropicOkHttpClient.builder().apiKey(key).build() }.getOrNull()
    }

    private fun resolveApiKey(): String? {
        System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() && it != "x" }?.let { return it }
        val envFile = File(System.getProperty("user.dir"), "server/.env")
            .takeIf { it.exists() } ?: File(System.getProperty("user.dir"), ".env")
        return envFile.takeIf { it.exists() }
            ?.readLines()
            ?.firstOrNull { it.startsWith("ANTHROPIC_API_KEY=") }
            ?.substringAfter("=")?.trim()
            ?.takeIf { it.isNotBlank() && it != "x" }
    }

    private fun buildSystemPrompt(rules: List<MerchantRule>): String {
        val rulesJson = if (rules.isEmpty()) "[]"
        else json.encodeToString(ListSerializer(MerchantRule.serializer()), rules)
        return """
Sos un parser de extractos bancarios colombianos. Tu trabajo es extraer todas las transacciones de un extracto bancario y devolver JSON válido.

Reglas del usuario (aprendidas de correcciones anteriores):
$rulesJson

Devolvé ÚNICAMENTE un array JSON con este formato exacto, sin explicaciones:
[{"date":"YYYY-MM-DD","merchant":"nombre limpio","amount":123456,"type":"EXPENSE|INCOME","category":"categoría","description":"descripción corta","rawText":"línea original"}]

- amount: entero en pesos colombianos (sin puntos ni comas), siempre positivo
- type: EXPENSE para débitos/compras/pagos, INCOME para créditos/abonos/nómina
- Aplicá las reglas del usuario cuando el merchant coincida
""".trimIndent()
    }

    suspend fun parse(text: String, rules: List<MerchantRule>): List<ParsedTransaction> {
        val c = client ?: return emptyList()
        val params = MessageCreateParams.builder()
            .model("claude-opus-4-7")
            .maxTokens(4096L)
            .systemOfTextBlockParams(listOf(TextBlockParam.builder().text(buildSystemPrompt(rules)).build()))
            .messages(listOf(MessageParam.builder().role(MessageParam.Role.USER).content(text).build()))
            .build()
        val rawText = withContext(Dispatchers.IO) {
            val response = c.messages().create(params)
            response.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString("")
        }
        return parseJson(rawText)
    }

    fun parseJson(rawText: String): List<ParsedTransaction> {
        val start = rawText.indexOf('[')
        val end = rawText.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return emptyList()
        val arrayJson = rawText.substring(start, end + 1)
        return runCatching {
            json.decodeFromString(ListSerializer(ClaudeRow.serializer()), arrayJson)
                .map { row ->
                    ParsedTransaction(
                        id = UUID.randomUUID().toString(),
                        date = row.date,
                        merchant = row.merchant,
                        amount = row.amount,
                        type = runCatching { TransactionType.valueOf(row.type) }.getOrDefault(TransactionType.EXPENSE),
                        category = row.category,
                        description = row.description,
                        rawText = row.rawText,
                    )
                }
        }.getOrDefault(emptyList())
    }

    @Serializable
    private data class ClaudeRow(
        val date: String,
        val merchant: String,
        val amount: Long,
        val type: String,
        val category: String,
        val description: String = "",
        val rawText: String = "",
    )
}
