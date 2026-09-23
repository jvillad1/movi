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
import com.jvillada.movi.server.ai.conSaldos
import com.jvillada.movi.server.ai.render
import com.jvillada.movi.server.ai.BUSCAR_DOCUMENTOS
import com.jvillada.movi.server.balance.cuentasConSaldo
import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.Patrimonio
import com.jvillada.movi.shared.model.claseDeBien
import com.jvillada.movi.shared.model.deudaDelBien
import com.jvillada.movi.shared.model.esCuentaDeDeuda
import com.jvillada.movi.shared.model.estadoDePresupuesto
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.model.valorEnPesosDe
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.server.ai.ContextoDelPeriodo
import com.jvillada.movi.server.ai.DatosParaLosHechos
import com.jvillada.movi.server.ai.ElModeloDeAnthropic
import com.jvillada.movi.server.ai.cifrasTrampa
import com.jvillada.movi.server.ai.hechosParaLaPregunta
import com.jvillada.movi.server.ai.responderSinInventar
import com.jvillada.movi.server.ai.ejecutarHerramienta
import com.jvillada.movi.server.ai.guardarLaConversacion
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

/**
 * Las instrucciones del asistente. `internal` y no `private` por una razón concreta: hay una prueba
 * que las compara contra las herramientas que existen de verdad. Ver `LasInstruccionesNombranSusHerramientasTest`.
 */
internal val PERSONA = """Eres Movi AI, un copiloto financiero personal y familiar para usuarios en Colombia.

Hablas en español relajado y directo, sin jerga financiera innecesaria. Tuteas al usuario, no uses "usted".
Habla en español neutro latinoamericano, de tú, SIN VOSEO. Esto no es un matiz de estilo: conjuga siempre en tú — "tienes", "puedes", "quieres", "sabes", "dijiste", "tu plata" — y trátalo siempre de "tú". El rioplatense se cuela sobre todo cuando el usuario TE escribe así: aunque él te hable de esa forma, respóndele de tú.
Montos siempre en pesos colombianos con formato ${'$'}X.XXX.XXX.
Vocabulario de la app: di "gasto"/"gastos", nunca "egreso"/"egresos". La interfaz habla así y tú también.

Cuando el usuario te pregunte sobre su plata, básate ÚNICAMENTE en los datos del bloque "DATOS DEL USUARIO", en el bloque "DATOS EXACTOS PARA ESTA PREGUNTA" (si su mensaje lo trae) y en lo que devuelvan tus herramientas. Nunca estimes ni completes de memoria una cifra que no viniera de ahí.

CIFRAS — NO CALCULES: toda cifra de plata o porcentaje que escribas tiene que estar en esos datos. Si los datos traen la cuenta hecha (lo que queda de la cuota después de los seguros, cuánto baja o crece una deuda, cuánto falta o cuánto se pasó de un presupuesto), usa ESA cifra con ESE significado: no la rehagas con tu propia resta. Lo único que puedes calcular es una suma o una resta de DOS cifras de los datos, y entonces escribe la operación con las dos ("${'$'}2.613.714 − ${'$'}209.219 = ${'$'}2.404.495"). Si necesitas una cifra que no está, di cuál falta y consúltala con una herramienta o pídesela al usuario. Movi revisa cada cifra de tu respuesta contra los datos.
El bloque "DATOS EXACTOS PARA ESTA PREGUNTA" lo calcula Movi con las mismas cuentas que sus pantallas: cuando exista, sus cifras mandan sobre cualquier cuenta tuya.
NO SUPONGAS: si un nombre (una entidad, una cuenta, un tercero) no está explicado en los datos, no le inventes qué es ni para qué sirve, y no supongas de dónde sale su plata más allá de lo que dicen los datos. Está bien decir "no lo sé con estos datos".

Tienes TRES herramientas, y son la única forma de saber algo que no esté en el bloque:
- buscar_movimientos: hechos concretos. "¿Qué compré en X?", "¿qué hubo entre estas fechas?", "¿esto ya lo había comprado?".
- totales_por_categoria: cuánto. "¿Cuánto gasté en Comida en agosto?", "¿gasté más que el mes pasado?".
- buscar_documentos: lo que dicen sus papeles. "¿Qué seguro paga la cuenta X?", "¿qué tasa tiene ese crédito?", "¿tengo el extracto de agosto?".

CONSULTA ANTES DE RESPONDER —nunca después de haber dicho una cifra— siempre que la pregunta nombre: un mes o una fecha, un comercio, un documento, una póliza, un extracto, o cualquier cosa que no encuentres literalmente en el bloque. Ante la duda, consulta: una consulta de más cuesta segundos, una cifra inventada le desordena la plata.

OJO CON LOS MESES: el período del usuario NO es el mes de calendario —el bloque dice de qué día a qué día va—, así que "agosto" y "su período" son ventanas distintas aunque se superpongan. Si te nombra un mes, NO contestes con las cifras del bloque: consulta con las fechas de calendario de ese mes (2026-08-01 a 2026-08-31) y dilo en la respuesta ("en agosto de calendario…"). Contestar con la cifra del período a una pregunta por un mes es dar un número equivocado con cara de exacto.
No las uses para lo que ya está en el bloque, que es el período en curso completo.
Si necesitas dos consultas, pídelas EN EL MISMO TURNO: dos juntas cuestan lo mismo que una, y dos seguidas cuestan el doble.
Si una consulta vuelve vacía, dilo: "no encuentro nada" es una respuesta correcta y "creo que gastaste como" no lo es.
Si la pregunta no se puede contestar ni con los datos ni consultando, dilo claramente y sugiere qué información faltaría.

Cuando el bloque ya traiga un total (gastos del período, total de suscripciones, deuda total, intereses del mes, cuotas al mes, lo que se pasó de un presupuesto, gastos recurrentes que faltan), usa ESE número tal cual: no vuelvas a sumar los renglones ni corrijas el total con tu propia cuenta.

Tono: directo, empático, accionable. No moralices sobre el gasto.
Estructura de una pregunta de DATOS (cuánto, cuándo, qué): responde en máximo 4-5 frases cortas. Si la respuesta tiene un cálculo, muéstralo en una línea separada.

Cuando te pida CRITERIO (qué le conviene, qué hacer, si le alcanza, cómo bajar algo, qué priorizar), responde con esta forma y nada más:
Separa siempre lo que DICEN SUS DATOS de lo que TÚ LE RECOMIENDAS.
1. Diagnóstico en UNA frase, con la cifra de sus datos que lo sostiene.
2. Dos o tres acciones concretas, una por línea, cada una con números de SUS datos (qué deuda, qué categoría, cuánto, cuándo). Nada de consejos que servirían para cualquiera ("haz un presupuesto", "ahorra más").
3. Una última línea con el riesgo o lo que habría que confirmar antes de actuar.
Corto: la respuesta entera cabe en unas 8 líneas.

Antes de recomendar algo sobre una deuda, mira en su renglón QUIÉN PAGA LA CUOTA, y di quién es tal como lo dice el renglón (una cuenta suya no es "un seguro" ni "un tercero"). Si la descuenta la nómina, la paga otra cuenta suya o un tercero, esa cuota NO sale de su cuenta del día a día: no le propongas recortar gastos para cubrirla ni la restes de su plata disponible. Abonarle a esa deuda sí sale de su plata. Para comparar deudas usa la tasa EA, el interés del mes y lo que baja la deuda que trae cada renglón; no los recalcules.
Si la decisión depende de algo que no está en sus datos (impuestos, una inversión puntual, un trámite legal), da tu lectura con lo que ves y dile qué conviene confirmar con un asesor certificado, en una frase.
No uses emojis ni símbolos decorativos: la interfaz no los renderiza.

F32: si el usuario te manda una foto de un recibo, un extracto o una oferta del banco, extrae lo relevante (montos, fechas, comercio o condiciones) y opina usando los datos del usuario en "DATOS DEL USUARIO".

Documentos: los papeles que el usuario subió a Movi NO están en el bloque — se piden con buscar_documentos, pásale una parte del nombre o del tema. Lo que esa herramienta devuelve son las notas que él mismo escribió al guardar cada papel: eso es lo que leyó en él el día que lo subió —cada renglón dice de cuándo es—, así que pueden haber quedado viejas. Úsalas para contestar y para contrastar contra los movimientos, pero si una nota no cuadra con los movimientos no des por hecho que manda la nota: di de cuándo es y que los movimientos pueden ser posteriores. Cuando una cifra tuya salga de ahí, DI DE QUÉ DOCUMENTO SALE, nombrándolo tal cual aparece (por ejemplo: "según TC_Master_3684_09_2026.pdf"). De los documentos solo tienes el nombre, el tipo, el período, la fecha en que se subió y esas notas: nunca el texto de adentro del archivo, así que no describas lo que dice un PDF ni inventes cifras que no estén ni en los movimientos ni en las notas. Si la herramienta avisa que hay más documentos de los que te mostró, vuelve a pedirla con un filtro más preciso antes de decir que algo no existe.
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

        val datos = cargarDatosDelUsuario(call.userId())
        val context = datos.comoContexto()
        // **Solo el final del hilo.** El teléfono manda la conversación entera en cada pregunta,
        // así que sin este recorte una charla larga se paga completa cada vez. El `dropWhile` de
        // `mensajesParaElModelo` va DESPUÉS del recorte: si al cortar queda un turno del asistente
        // al principio, la API lo rechaza.
        val paraElModelo = mensajesParaElModelo(body.messages.takeLast(ULTIMOS_MENSAJES_QUE_VIAJAN))
        // **Los datos exactos para ESTA pregunta** van pegados a ella, en el último mensaje del
        // dueño: DESPUÉS de todo lo cacheado (PERSONA, contexto, herramientas), así que cambian con
        // cada pregunta sin tirar la caché. Ver `hechosParaLaPregunta`.
        val hechos = paraElModelo.lastOrNull()?.let { hechosParaLaPregunta(it.content, datos.paraLosHechos()) }
        val messageParams = paraElModelo.mapIndexed { i, m ->
            toMessageParam(m, anexo = hechos.takeIf { i == paraElModelo.lastIndex })
        }
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
        val hayImagen = ultima.imageBase64 != null
        val reply = runCatching {
            responderSinInventar(
                modelo = elModelo,
                ejecutar = { llamada -> ejecutarHerramienta(uid, llamada) },
                // Todo lo que el modelo tenía delante en este turno: contra esto se revisa cada
                // cifra. La conversación entra entera —la pregunta y lo que ya se contestó—, porque
                // repetir una cifra que el dueño escribió no es inventarla.
                fuentes = listOfNotNull(context, hechos) + paraElModelo.map { it.content },
                trampas = cifrasTrampa(datos.periodo.creditos),
                // Con una foto, los montos salen de la imagen y el verificador no la puede leer.
                verificar = !hayImagen,
            )
        }
        // Lo que costó, en el log. Sin esto el costo se estima; con esto se mira.
        call.application.log.info(
            "movi-ai uid=$uid criterio=$pideCriterio hechos=${hechos != null} " +
                "reintento=${reply.getOrNull()?.huboReintento == true} entrada=${elModelo.fichasDeEntrada} " +
                "cache=${elModelo.fichasLeidasDeCache} salida=${elModelo.fichasDeSalida}",
        )
        // Y la conversación queda guardada, que es lo que hace diagnosticable «el asistente no
        // supo»: sin la pregunta, lo que consultó y lo que contestó, del lado del server solo
        // quedaban las fichas. Guardar NUNCA puede romper la respuesta — ver
        // `guardarLaConversacion`, que no lanza.
        reply.getOrNull()?.let { paso ->
            val guardado = guardarLaConversacion(
                uid = uid,
                pregunta = ultima.content,
                respuesta = paso.texto,
                consultas = paso.consultas,
                modelo = if (pideCriterio) MODELO_PARA_CONSEJOS else MODELO_DE_TODOS_LOS_DIAS,
                criterio = pideCriterio,
                fichasEntrada = elModelo.fichasDeEntrada,
                fichasCache = elModelo.fichasLeidasDeCache,
                fichasSalida = elModelo.fichasDeSalida,
                hayImagen = hayImagen,
                cifrasSinRespaldo = paso.sinRespaldo,
                cifrasCorregidas = paso.corregidas,
            )
            if (!guardado) call.application.log.warn("movi-ai: no pude guardar la conversación de $uid")
        }
        reply.onSuccess { call.respond(AiChatResponse(text = stripEmojis(it.texto))) }
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
internal fun toMessageParam(m: ChatMessage, anexo: String? = null): MessageParam {
    val role = when (m.role) {
        ChatRole.USER -> MessageParam.Role.USER
        ChatRole.ASSISTANT -> MessageParam.Role.ASSISTANT
    }
    val builder = MessageParam.builder().role(role)
    val mime = m.imageMime?.let { ClaudeStatementParser.supportedImageMime(it, "") }
    val b64 = m.imageBase64
    // El anexo (los datos exactos de la pregunta) va como bloque aparte DESPUÉS de lo que escribió
    // el dueño: así el modelo lee primero la pregunta y después los datos para contestarla.
    val bloqueDelAnexo = anexo?.let { ContentBlockParam.ofText(TextBlockParam.builder().text(it).build()) }
    if (b64 == null || mime == null) {
        if (bloqueDelAnexo == null) return builder.content(m.content).build()
        val bloques = buildList {
            if (m.content.isNotBlank()) add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.content).build()))
            add(bloqueDelAnexo)
        }
        return builder.contentOfBlockParams(bloques).build()
    }
    val imageSource = Base64ImageSource.builder()
        .data(b64)
        .mediaType(Base64ImageSource.MediaType.of(mime))
        .build()
    val blocks = buildList {
        add(ContentBlockParam.ofImage(ImageBlockParam.builder().source(imageSource).build()))
        if (m.content.isNotBlank()) add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.content).build()))
        bloqueDelAnexo?.let(::add)
    }
    return builder.contentOfBlockParams(blocks).build()
}

/**
 * `internal` y no `private`: hay un test que fija que una cuenta condicionada llegue MARCADA al
 * asistente. Sin la marca, «Skandia (INVESTMENT): saldo 106.000.000» es plata que el modelo suma
 * al contestar «¿cuánta plata disponible tengo?» — el mismo error que el Inicio dejó de cometer,
 * ahora en la boca del asistente.
 */
internal suspend fun buildUserContext(uid: String): String = cargarDatosDelUsuario(uid).comoContexto()

/**
 * **Lo que Movi sabe del dueño, leído UNA vez por pregunta.** Antes `buildUserContext` leía y
 * escribía el texto en la misma pasada; ahora los mismos datos sirven para dos cosas —el contexto
 * general y los hechos exactos de la pregunta ([hechosParaLaPregunta])— y leerlos dos veces sería
 * pagar dos veces la base, y arriesgar que las dos lecturas no coincidan.
 */
internal data class DatosDelUsuario(
    val cuentas: List<Account>,
    val patrimonio: Patrimonio,
    val ingresos: Long,
    val egresos: Long,
    val periodo: ContextoDelPeriodo,
    val presupuestos: List<Pair<String, Long>>,
    val cuantosDocumentos: Int,
) {
    fun paraLosHechos() = DatosParaLosHechos(cuentas, patrimonio, periodo, presupuestos)
}

internal suspend fun cargarDatosDelUsuario(uid: String): DatosDelUsuario {
    val rate = FxRateService.usdToCop()

    // Las cuentas con su saldo derivado —sumado en SQL, los mismos saldos que la lista de
    // Cuentas— y el patrimonio partido con `patrimonioDe`, la regla única de :core. Antes el
    // asistente recibía los saldos sueltos y sumaba él: sin bienes y sin la regla, contestaba
    // «¿cuánto tengo?» con la media foto (−$2.074M) que el Inicio tenía hasta esta entrega.
    val cuentas = dbQuery {
        val anulados = VoidEvents.selectAll()
            .where { VoidEvents.userId eq uid }
            .map { it[VoidEvents.originalEventId] }
            .toSet()
        cuentasConSaldo(uid, anulados, rate)
    }
    val patrimonio = patrimonioDe(cuentas)

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
    // Con el saldo de cada crédito puesto: sin él no hay interés del mes ni «cuánto baja», y esas
    // son las dos cifras con que se contesta «¿qué deuda abono primero?». Ver `conSaldos`.
    val delPeriodo = contextoDelPeriodoDe(uid).conSaldos(cuentas)

    // Budgets
    val budgets = dbQuery {
        Budgets.selectAll().where { Budgets.userId eq uid }
            .map { it[Budgets.category] to it[Budgets.monthlyLimit] }
    }

    return DatosDelUsuario(cuentas, patrimonio, ingresos, egresos, delPeriodo, budgets, cuantosDocumentos)
}

/** El texto del contexto general: lo que viaja cacheado en el sistema en cada pregunta. */
internal fun DatosDelUsuario.comoContexto(): String {
    val budgets = presupuestos
    val delPeriodo = periodo
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
        if (cuentas.isEmpty()) {
            appendLine("- (sin cuentas registradas)")
        } else {
            cuentas.forEach { cuenta ->
                val value = valorEnPesosDe(cuenta)
                val bien = cuenta.bien
                if (bien != null) {
                    appendLine(lineaDelBien(cuenta, bien, cuentas))
                    return@forEach
                }
                val kind = if (esCuentaDeDeuda(cuenta.type)) "deuda" else "saldo"
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
        append(renderizarPatrimonio(patrimonio))
        appendLine()
        append(renderizarPresupuestos(budgets, delPeriodo.gastoPorCategoria))

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

/**
 * **Los presupuestos con lo gastado y lo que se pasó**, no solo el límite.
 *
 * Con el límite solo, «¿cómo hago para no pasarme en Fútbol?» obligaba al modelo a cruzar este
 * bloque con «En qué se fue la plata» y restar — dos cosas que hace mal, y justo sobre la cifra
 * por la que se pregunta. Acá la resta va hecha, con la MISMA regla de «pasado» que el Inicio y la
 * pantalla de Presupuestos ([estadoDePresupuesto]): gastar justo el límite no es pasarse.
 *
 * Lo gastado sale de `gastoPorCategoria` del período, que ya aplica los filtros del Inicio
 * (anulados, «Por confirmar» y pagos de tarjeta afuera).
 */
internal fun renderizarPresupuestos(budgets: List<Pair<String, Long>>, gastoPorCategoria: Map<String, Long>): String =
    buildString {
        appendLine("== Presupuestos de este período ==")
        if (budgets.isEmpty()) {
            appendLine("- (sin presupuestos)")
            return@buildString
        }
        budgets.forEach { (cat, limite) ->
            val gastado = gastoPorCategoria[cat] ?: 0L
            val como = if (estadoDePresupuesto(gastado, limite).estaSuperado) {
                "SE PASÓ por \$${gastado - limite}"
            } else {
                "le quedan \$${limite - gastado}"
            }
            appendLine("- $cat: límite \$$limite, gastado \$$gastado — $como")
        }
    }

/**
 * El renglón de un **bien** en el contexto del asistente: qué es, cuánto vale, de cuándo es ese
 * valor, y —si hay una deuda que lo financia— cuánto se debe y cuánto es suyo de verdad.
 *
 * Dice en palabras que NO es plata, por lo mismo que la condición de Skandia: un modelo que lee
 * «Casa (INVESTMENT): saldo $1.411.903.920» la suma a «¿cuánta plata tengo?». Ver `Account.bien`.
 */
internal fun lineaDelBien(cuenta: Account, bien: Bien, cuentas: List<Account>): String = buildString {
    append("- ${cuenta.name} (BIEN · ${claseDeBien(bien.clase).nombre}): vale \$${bien.valor}")
    bien.valorAl?.let { append(" según el avalúo del $it") }
    append(" — es un bien, NO plata disponible ni de uso condicionado (suma al patrimonio)")
    deudaDelBien(cuenta, cuentas)?.let { d ->
        append("; lo financia «${d.deuda.name}», que debe \$${d.debes}: lo suyo de verdad son \$${d.tuyo}")
    }
}

/**
 * **El patrimonio honesto, ya partido**, para que el asistente no tenga que sumar (y no sume
 * distinto que el Inicio): la plata que se puede usar, la condicionada, los bienes y las deudas,
 * con la resta escrita. Mismas cifras que [patrimonioDe] le da a la pantalla.
 */
internal fun renderizarPatrimonio(p: Patrimonio): String = buildString {
    appendLine("== Patrimonio ==")
    appendLine("- Tu plata (disponible para usar): \$${p.tuPlata}")
    if (p.condicionado != 0L) {
        val para = p.condicionadoA?.let { "solo para $it" } ?: "de uso condicionado"
        appendLine("- Plata con destino ($para; suya, pero NO disponible): \$${p.condicionado}")
    }
    if (p.bienes != 0L) appendLine("- Bienes (inmuebles, vehículos; no es plata): \$${p.bienes}")
    appendLine("- Deudas: \$${p.deudas}")
    appendLine("- Patrimonio neto (tu plata + plata con destino + bienes − deudas): \$${p.neto}")
}
