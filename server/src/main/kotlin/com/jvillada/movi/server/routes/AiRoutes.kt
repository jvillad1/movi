package com.jvillada.movi.server.routes

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
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
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import com.jvillada.movi.server.time.currentMonthWindow

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
Vocabulario de la app: di "gasto"/"gastos", nunca "egreso"/"egresos". La interfaz habla así y tú también.

Cuando el usuario te pregunte sobre su plata, básate ÚNICAMENTE en los datos del bloque "DATOS DEL USUARIO".
Si la pregunta no se puede contestar con esos datos, dilo claramente y sugiere qué información faltaría.

Tono: directo, empático, accionable. No moralices sobre el gasto.
Estructura: responde en máximo 4-5 frases cortas. Si la respuesta tiene un cálculo, muéstralo en una línea separada.
No uses emojis ni símbolos decorativos: la interfaz no los renderiza.

F32: si el usuario te manda una foto de un recibo, un extracto o una oferta del banco, extrae lo relevante (montos, fechas, comercio o condiciones) y opina usando los datos del usuario en "DATOS DEL USUARIO".
"""

/** F32: tope de peso decodificado de una imagen adjunta al chat (Claude cobra por tokens de imagen). */
private const val MAX_CHAT_IMAGE_BYTES = 5 * 1024 * 1024

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

        // F32: valida cualquier imagen adjunta ANTES de tocar el cliente de Claude o la DB —
        // así el camino de error (mime malo, imagen muy grande) es testeable sin red ni
        // ANTHROPIC_API_KEY, y el usuario se entera al toque en vez de esperar la llamada.
        validateChatImages(body.messages)?.let { message ->
            call.respond(HttpStatusCode.UnprocessableEntity, AiChatResponse(text = message))
            return@post
        }

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
        val messageParams = body.messages.map(::toMessageParam)
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

/**
 * F32: recorre los mensajes buscando adjuntos y devuelve el primer problema encontrado (o
 * null si todo está bien). Mismos límites que /api/statements/upload: png/jpeg/webp/gif,
 * máx. 5 MB ya decodificados — ahí Claude también cobra por tokens de imagen, así que el
 * tope evita una llamada carísima además de un adjunto ilegible.
 */
internal fun validateChatImages(messages: List<ChatMessage>): String? {
    for (m in messages) {
        val b64 = m.imageBase64 ?: continue
        val mime = m.imageMime
        if (mime.isNullOrBlank()) return "Falta el tipo de la imagen adjunta."
        if (ClaudeStatementParser.supportedImageMime(mime, "") == null) {
            return "Formato de imagen no soportado. Sube PNG, JPG, GIF o WEBP."
        }
        val bytes = runCatching { java.util.Base64.getDecoder().decode(b64) }.getOrNull()
            ?: return "No pude leer la imagen adjunta."
        if (bytes.size > MAX_CHAT_IMAGE_BYTES) {
            return "La imagen pesa más de 5 MB. Sube una más liviana."
        }
    }
    return null
}

/**
 * Arma el [MessageParam] para un [ChatMessage]: si trae imagen (ya validada por
 * [validateChatImages]), el bloque de imagen va primero y el texto después solo si el
 * usuario escribió algo — mismo patrón que [ClaudeStatementParser.parseImage]. Sin imagen,
 * el comportamiento es idéntico al de antes de F32 (contenido de solo texto).
 */
internal fun toMessageParam(m: ChatMessage): MessageParam {
    val role = when (m.role) {
        ChatRole.USER -> MessageParam.Role.USER
        ChatRole.ASSISTANT -> MessageParam.Role.ASSISTANT
    }
    val builder = MessageParam.builder().role(role)
    val mime = m.imageMime?.let { ClaudeStatementParser.supportedImageMime(it, "") }
    val b64 = m.imageBase64
    if (b64 == null || mime == null) {
        return builder.content(m.content).build()
    }
    val imageSource = Base64ImageSource.builder()
        .data(b64)
        .mediaType(Base64ImageSource.MediaType.of(mime))
        .build()
    val blocks = buildList {
        add(ContentBlockParam.ofImage(ImageBlockParam.builder().source(imageSource).build()))
        if (m.content.isNotBlank()) add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.content).build()))
    }
    return builder.contentOfBlockParams(blocks).build()
}

private suspend fun buildUserContext(uid: String): String {
    val rate = FxRateService.usdToCop()

    // Accounts with their computed COP value
    val accountRows = dbQuery {
        Accounts.selectAll().where { Accounts.userId eq uid }
            .map { Triple(it[Accounts.id], it[Accounts.name], AccountType.valueOf(it[Accounts.type])) }
    }
    val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }

    // Ventana del mes en la zona de la app (Bogotá), la misma que usa finance-summary.
    val (monthStart, monthEnd) = currentMonthWindow()

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
        appendLine("- Gastos: \$$egresos")
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
