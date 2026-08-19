package com.jvillada.movi.server.routes

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.jvillada.movi.server.balance.accountCopValue
import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

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

private val PERSONA = """Eres Movi AI, un copiloto financiero personal y familiar para usuarios en Colombia.

Hablas en español relajado y directo, sin jerga financiera innecesaria. Tuteas al usuario, no uses "usted".
Habla en español neutro latinoamericano, de tú, sin voseo.
Montos siempre en pesos colombianos con formato ${'$'}X.XXX.XXX.

Cuando el usuario te pregunte sobre su plata, básate ÚNICAMENTE en los datos del bloque "DATOS DEL USUARIO".
Si la pregunta no se puede contestar con esos datos, dilo claramente y sugiere qué información faltaría.

Tono: directo, empático, accionable. No moralices sobre el gasto.
Estructura: responde en máximo 4-5 frases cortas. Si la respuesta tiene un cálculo, muéstralo en una línea separada.
No uses emojis ni símbolos decorativos: la interfaz no los renderiza.
"""

/**
 * Red de seguridad de F31: el [PERSONA] ya le pide al modelo que no mande emojis, pero esto
 * filtra lo que se cuele antes de que llegue al cliente — la fuente que usa la web no los
 * renderiza (cuadrados vacíos).
 *
 * Dos categorías, deliberadamente angostas para no tocar el resto de Unicode "normal"
 * (tildes, ñ, signos de puntuación en español, comillas «»):
 *  - Todo lo fuera del BMP (code point > 0xFFFF): ahí vive la gran mayoría del emoji moderno
 *    (🎉 💰 🚀…), los modificadores de tono de piel y las banderas.
 *  - Los bloques BMP de símbolos misceláneos que sí caben dentro del BMP (☀ ✨ ✅ ❤ ⌚ ⭐…),
 *    más el selector de variación U+FE0F y el ZWJ U+200D que arman emoji compuestos.
 */
internal fun stripEmojis(text: String): String {
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        val charCount = Character.charCount(codePoint)
        val isBmpMiscSymbol = codePoint in 0x2600..0x27BF || // Misc Symbols + Dingbats (☀✨✅❤️✂…)
            codePoint in 0x2300..0x23FF || // Misc Technical (⌚⌛⏰…)
            codePoint in 0x2B00..0x2BFF || // Misc Symbols and Arrows (⭐⬛…)
            codePoint == 0xFE0F || // variation selector-16 (fuerza presentación emoji)
            codePoint == 0x200D // zero-width joiner (arma emoji compuestos)
        if (codePoint <= 0xFFFF && !isBmpMiscSymbol) {
            sb.appendCodePoint(codePoint)
        }
        i += charCount
    }
    return sb.toString()
}

fun Route.aiRoutes() {
    post("/api/ai/chat") {
        val body = call.receive<AiChatRequest>()
        val client = anthropicClient
        if (client == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                AiChatResponse(
                    text = "ANTHROPIC_API_KEY no está configurada en el server. Configúrala y reinicia: export ANTHROPIC_API_KEY=sk-ant-... && ./gradlew :server:run",
                ),
            )
            return@post
        }

        val context = buildUserContext(call.userId())
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
        reply.onSuccess { call.respond(AiChatResponse(text = stripEmojis(it))) }
            .onFailure {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiChatResponse(text = "Error llamando a Claude: ${it.message ?: "desconocido"}"),
                )
            }
    }
}

private suspend fun buildUserContext(uid: String): String {
    val rate = FxRateService.usdToCop()

    // Accounts with their computed COP value
    val accountRows = dbQuery {
        Accounts.selectAll().where { Accounts.userId eq uid }
            .map { Triple(it[Accounts.id], it[Accounts.name], AccountType.valueOf(it[Accounts.type])) }
    }
    val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }

    // Month window (UTC), same approach as finance-summary
    val now = ZonedDateTime.now(ZoneOffset.UTC)
    val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        .toInstant().toEpochMilli()
    val monthEnd = now.withDayOfMonth(1).plusMonths(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        .toInstant().toEpochMilli()

    // Income and expense sums — filter ResultRows directly, same as finance-summary
    val (ingresos, egresos) = dbQuery {
        val voidedIds = VoidEvents.selectAll()
            .where { VoidEvents.userId eq uid }
            .map { it[VoidEvents.originalEventId] }
            .toSet()

        val monthEvents = Events.selectAll().where {
            (Events.userId eq uid) and
            (Events.timestamp greaterEq monthStart) and
            (Events.timestamp less monthEnd)
        }.filterNot { it[Events.id] in voidedIds }

        // Mismo filtro que /api/finance-summary: los movimientos de cuentas de deuda no son
        // ingreso ni gasto del mes (ver isCashFlow). Sin esto, un ajuste de deuda de $60M
        // entraba al contexto del asistente rotulado como "Ingresos" y razonaba sobre él.
        val accountTypeById = accountTypesFor(uid)
        val cashFlow = monthEvents.filter { row ->
            val accountType = accountTypeById[row[Events.accountId]]
            accountType == null ||
                isCashFlow(accountType, TransactionType.valueOf(row[Events.type]), row[Events.category])
        }

        val inc = cashFlow
            .filter { it[Events.type] == TransactionType.INCOME.name && it[Events.currency] == "COP" }
            .sumOf { it[Events.amount] }
        val exp = cashFlow
            .filter { it[Events.type] == TransactionType.EXPENSE.name && it[Events.currency] == "COP" }
            .sumOf { it[Events.amount] }
        inc to exp
    }

    // Budgets
    val budgets = dbQuery {
        Budgets.selectAll().where { Budgets.userId eq uid }
            .map { it[Budgets.category] to it[Budgets.monthlyLimit] }
    }

    return buildString {
        appendLine("DATOS DEL USUARIO (Colombia)")
        appendLine()
        appendLine("== Resumen del mes en curso ==")
        appendLine("- Ingresos: \$$ingresos")
        appendLine("- Egresos: \$$egresos")
        appendLine("- Flujo: \$${ingresos - egresos}")
        appendLine()
        appendLine("== Cuentas ==")
        if (accountRows.isEmpty()) {
            appendLine("- (sin cuentas registradas)")
        } else {
            accountRows.forEach { (id, name, type) ->
                val value = accountCopValue(type, eventsByAccount[id] ?: emptyList(), rate)
                val kind = if (type == AccountType.CREDIT_CARD || type == AccountType.LOAN) "deuda" else "saldo"
                appendLine("- $name ($type): $kind \$$value")
            }
        }
        appendLine()
        appendLine("== Presupuestos ==")
        if (budgets.isEmpty()) {
            appendLine("- (sin presupuestos)")
        } else {
            budgets.forEach { (cat, limit) -> appendLine("- $cat: límite \$$limit") }
        }
    }
}
