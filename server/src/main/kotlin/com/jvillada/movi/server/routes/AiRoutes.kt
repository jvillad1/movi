package com.jvillada.movi.server.routes

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.jvillada.movi.server.ai.cargarDocumentosParaContexto
import com.jvillada.movi.server.ai.contextoDelPeriodoDe
import com.jvillada.movi.server.ai.render
import com.jvillada.movi.server.ai.BUSCAR_DOCUMENTOS
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
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.normalizarCondicion
import com.jvillada.movi.server.ai.ElModeloDeAnthropic
import com.jvillada.movi.server.ai.conversarConHerramientas
import com.jvillada.movi.server.ai.ejecutarHerramienta
import io.ktor.server.application.log
import com.jvillada.movi.server.ai.laPreguntaPideCriterio
import com.jvillada.movi.server.ai.MODELO_DE_RESPALDO
import com.jvillada.movi.server.ai.MODELO_DE_TODOS_LOS_DIAS
import com.jvillada.movi.server.ai.MODELO_PARA_CONSEJOS
import com.jvillada.movi.server.ai.ULTIMOS_MENSAJES_QUE_VIAJAN
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.io.File
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import com.jvillada.movi.server.time.currentPeriodWindow
import com.jvillada.movi.server.time.cutoffDayOf
import com.jvillada.movi.server.time.ajustesDePeriodoDe

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

Cuando el usuario te pregunte sobre su plata, básate ÚNICAMENTE en los datos del bloque "DATOS DEL USUARIO" y en lo que devuelvan tus herramientas. Nunca estimes ni completes de memoria una cifra que no viniera de ahí.

Tienes dos herramientas para consultar sus movimientos más allá del período que ya ves: buscar_movimientos (hechos concretos) y totales_por_categoria (cuánto). Úsalas cuando la pregunta hable de otro mes, de otro período o de algo que el bloque no trae; no las uses para lo que ya está ahí, que es el período en curso completo. Consulta antes de responder, nunca después de haber dicho una cifra.
Si necesitas dos consultas, pídelas EN EL MISMO TURNO: dos juntas cuestan lo mismo que una, y dos seguidas cuestan el doble.
Si una consulta vuelve vacía, dilo: "no encuentro nada" es una respuesta correcta y "creo que gastaste como" no lo es.
Si la pregunta no se puede contestar ni con los datos ni consultando, dilo claramente y sugiere qué información faltaría.

Cuando el bloque ya traiga un total (gastos del período, total de suscripciones, deuda total), usa ESE número tal cual: no vuelvas a sumar los renglones ni corrijas el total con tu propia cuenta. Si te piden algo que no viene sumado, suma solo lo que haga falta y muestra la operación.

Tono: directo, empático, accionable. No moralices sobre el gasto.
Estructura: responde en máximo 4-5 frases cortas. Si la respuesta tiene un cálculo, muéstralo en una línea separada.
No uses emojis ni símbolos decorativos: la interfaz no los renderiza.

F32: si el usuario te manda una foto de un recibo, un extracto o una oferta del banco, extrae lo relevante (montos, fechas, comercio o condiciones) y opina usando los datos del usuario en "DATOS DEL USUARIO".

Documentos: el bloque "Documentos guardados" lista los papeles que el usuario subió a Movi, con las notas que él mismo escribió al guardarlos. Esas notas son lo que él leyó en el papel el día que lo subió —cada renglón dice de cuándo es—, así que pueden haber quedado viejas: úsalas para contestar y para contrastar contra los movimientos, pero si una nota no cuadra con los movimientos no des por hecho que manda la nota, di de cuándo es y que los movimientos pueden ser posteriores. Cuando una cifra tuya salga de ahí, DI DE QUÉ DOCUMENTO SALE, nombrándolo tal cual aparece en la lista (por ejemplo: "según TC_Master_3684_09_2026.pdf"). De los documentos solo tienes el nombre, el tipo, el período, la fecha en que se subió y esas notas: nunca el texto de adentro del archivo, así que no describas lo que dice un PDF ni inventes cifras que no estén ni en los movimientos ni en las notas.
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
        // **Solo el final del hilo.** El teléfono manda la conversación entera en cada pregunta,
        // así que sin este recorte una charla larga se paga completa cada vez. El `dropWhile` de
        // `mensajesParaElModelo` va DESPUÉS del recorte: si al cortar queda un turno del asistente
        // al principio, la API lo rechaza.
        val paraElModelo = mensajesParaElModelo(body.messages.takeLast(ULTIMOS_MENSAJES_QUE_VIAJAN))
        val messageParams = paraElModelo.map(::toMessageParam)
        if (messageParams.isEmpty() || messageParams.last().role() != MessageParam.Role.USER) {
            call.respond(HttpStatusCode.BadRequest, AiChatResponse(text = "Último mensaje debe ser del usuario"))
            return@post
        }

        // **Ya no es una llamada, es una conversación.** El asistente puede consultar los
        // movimientos del dueño antes de contestar (ver `conversarConHerramientas`, que decide
        // cuándo y cuántas veces, y se prueba sin red). El `uid` sale del token y nunca del texto
        // que escribe el modelo: las herramientas solo leen, y solo lo de este dueño.
        val uid = call.userId()
        // El modelo lo decide la pregunta, no una constante: ver `laPreguntaPideCriterio`. Casi
        // todo lo que él pregunta es un dato y lo contesta el chico; el grande es para el criterio.
        val ultima = paraElModelo.last()
        val pideCriterio = laPreguntaPideCriterio(ultima.content, hayImagen = ultima.imageBase64 != null)
        val elModelo = ElModeloDeAnthropic(
            client = client,
            modelo = if (pideCriterio) MODELO_PARA_CONSEJOS else MODELO_DE_TODOS_LOS_DIAS,
            persona = PERSONA,
            contexto = context,
            mensajesDelDueno = messageParams,
            // Pensar se cobra como salida. Se enciende solo cuando de verdad hay algo que pensar.
            piensa = pideCriterio,
            modeloDeRespaldo = MODELO_DE_RESPALDO,
        )
        val reply = runCatching {
            conversarConHerramientas(
                modelo = elModelo,
                ejecutar = { llamada -> ejecutarHerramienta(uid, llamada) },
            )
        }
        // Lo que costó, en el log. Sin esto el costo se estima; con esto se mira.
        call.application.log.info(
            "movi-ai uid=$uid criterio=$pideCriterio entrada=${elModelo.fichasDeEntrada} " +
                "cache=${elModelo.fichasLeidasDeCache} salida=${elModelo.fichasDeSalida}",
        )
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
 * **La conversación tiene que EMPEZAR con el usuario, o la API la rechaza entera.**
 *
 * La Messages API de Anthropic exige que el primer mensaje sea `user`. La pantalla del chat siembra
 * el historial con un saludo del asistente («¡Hola! Pregúntame lo que quieras sobre tu plata»), y
 * ese saludo viajaba como `messages[0]`: **todos** los turnos volvían con «Error llamando a
 * Claude», o sea que Movi AI no contestaba nada. Acá se veía sano porque la única guarda miraba el
 * ÚLTIMO mensaje, que sí era del usuario.
 *
 * El arreglo de fondo está en el cliente —el saludo es de pantalla y no se manda (ver
 * `mensajesParaEnviar` en `AIChatScreen.kt`)—, pero el server no puede depender de eso: el dueño
 * tiene un APK viejo instalado, y ese APK va a seguir mandando el saludo por meses. Así que acá se
 * descarta todo lo que venga antes del primer mensaje del usuario, que es exactamente lo que la
 * API no acepta. Un historial sin ningún mensaje del usuario queda vacío y cae en la guarda de
 * «último mensaje debe ser del usuario», que ya existía.
 */
internal fun mensajesParaElModelo(messages: List<ChatMessage>): List<ChatMessage> =
    messages.dropWhile { it.role != ChatRole.USER }

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

/**
 * Lo que el asistente necesita saber de una cuenta. Era un `Triple`, y no cabía un cuarto dato
 * sin volverlo ilegible — que es justo lo que hizo falta cuando apareció [condicionadaA].
 */
private data class AccountForContext(
    val id: String,
    val name: String,
    val type: AccountType,
    val condicionadaA: String?,
)

/**
 * `internal` y no `private`: hay un test que fija que una cuenta condicionada llegue MARCADA al
 * asistente. Sin la marca, «Skandia (INVESTMENT): saldo 106.000.000» es plata que el modelo suma
 * al contestar «¿cuánta plata disponible tengo?» — el mismo error que el Inicio dejó de cometer,
 * ahora en la boca del asistente.
 */
internal suspend fun buildUserContext(uid: String): String {
    val rate = FxRateService.usdToCop()

    // Accounts with their computed COP value
    val accountRows = dbQuery {
        Accounts.selectAll().where { Accounts.userId eq uid }
            .map {
                AccountForContext(
                    id = it[Accounts.id],
                    name = it[Accounts.name],
                    type = AccountType.valueOf(it[Accounts.type]),
                    condicionadaA = normalizarCondicion(it[Accounts.conditionedTo]),
                )
            }
    }
    val eventsByAccount = loadNonVoidedEvents(uid).groupBy { it.accountId }

    // Ventana del PERÍODO del usuario (ver PeriodSettings en :core), la misma que usa
    // finance-summary. Con corte 1 —el default— es el mes de calendario de siempre.
    val (monthStart, monthEnd) = currentPeriodWindow(ajustesDePeriodoDe(uid))

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
            // Tampoco lo que espera en «Por confirmar»: el asistente dice los mismos Ingresos y
            // Gastos que el Inicio.
            .filterNot { esperaEnPorConfirmar(it[Events.reconciliationStatus]) }

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

    // Cuántos papeles tiene, nada más: los renglones se piden con `buscar_documentos`.
    val cuantosDocumentos = cargarDocumentosParaContexto(uid).size
    // Todo lo que el asistente no veía hasta acá: en qué se fue la plata, los recurrentes con su
    // estado en este período, los créditos con tasa y cuota, las suscripciones y las metas. Ver
    // `ContextoDelPeriodo.kt` — usa las MISMAS reglas que el Inicio, para que los dos digan lo
    // mismo.
    val delPeriodo = contextoDelPeriodoDe(uid)

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
        append(delPeriodo.render())
        appendLine("== Cuentas ==")
        if (accountRows.isEmpty()) {
            appendLine("- (sin cuentas registradas)")
        } else {
            accountRows.forEach { cuenta ->
                val value = accountCopValue(cuenta.type, eventsByAccount[cuenta.id] ?: emptyList(), rate)
                val kind = if (cuenta.type == AccountType.CREDIT_CARD || cuenta.type == AccountType.LOAN) "deuda" else "saldo"
                // **La condición viaja en el contexto o el asistente contesta mal.** «Skandia
                // (INVESTMENT): saldo $106.000.000» sin marca es plata que el modelo suma al
                // contestar «¿cuánta plata disponible tengo?» — el mismo error que el Inicio
                // acaba de dejar de cometer, ahora en la boca del asistente. Ver
                // `Account.condicionadaA`.
                val condicion = cuenta.condicionadaA
                    ?.let { " — NO disponible: solo se puede usar para $it (cuenta en el patrimonio, no en la plata disponible)" }
                    .orEmpty()
                appendLine("- ${cuenta.name} (${cuenta.type}): $kind \$$value$condicion")
            }
        }
        appendLine()
        appendLine("== Presupuestos ==")
        if (budgets.isEmpty()) {
            appendLine("- (sin presupuestos)")
        } else {
            budgets.forEach { (cat, limit) -> appendLine("- $cat: límite \$$limit") }
        }

        // **Los documentos ya no viajan acá.** Eran 33 papeles con sus notas —casi seis mil
        // caracteres— en CADA mensaje, para una pregunta cada tantas. Ahora se consultan con
        // `buscar_documentos`, que trae los mismos renglones y solo cuando hacen falta. Lo único
        // que queda en el contexto es la línea de abajo, que le dice que existen.
        if (cuantosDocumentos > 0) {
            appendLine()
            appendLine(
                "== Documentos ==\n- El dueño tiene $cuantosDocumentos documentos guardados " +
                    "(extractos, pólizas, recibos) con notas suyas. Si una pregunta puede " +
                    "contestarse con ellos, consúltalos con la herramienta $BUSCAR_DOCUMENTOS.",
            )
        }
    }
}
