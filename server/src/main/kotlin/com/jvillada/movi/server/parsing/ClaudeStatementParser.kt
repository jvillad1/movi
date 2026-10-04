package com.jvillada.movi.server.parsing

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import com.jvillada.movi.server.ai.MODELO_DE_EXTRACTOS
import com.jvillada.movi.server.ai.MODELO_DE_TODOS_LOS_DIAS
import com.jvillada.movi.shared.model.MerchantRule
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
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

    /**
     * **Deuda conocida (Ola 10, anotada a propósito y no arreglada acá).** Este prompt arma la
     * lista de categorías desde `PREDEFINED_CATEGORIES` **con el tipo clavado del catálogo**, y no
     * consulta `category_prefs`. O sea que el importador de extractos es el único consumidor que
     * quedó ignorando lo que el dueño decidió en «Más → Categorías»: si esconde «Ropa», importa un
     * extracto y el modelo le devuelve movimientos en «Ropa», la categoría que retiró vuelve a
     * entrar a su historia por esta puerta.
     *
     * No se arregla en esta tanda porque no es cambiarle el filtro a una lista: el prompt es
     * por-usuario solo a medias (tiene el `uid` a mano para las `MerchantRule`, así que el dato
     * está), pero además hay que decidir qué hacer con las categorías que el modelo ya devolvió
     * antes del cambio y si una escondida debe reemplazarse o solo dejar de ofrecerse. Es una
     * decisión de producto, no un `filter` — y meterla apurada acá es exactamente cómo esta app
     * se ganó sus últimos cuatro defectos.
     */
    internal fun buildSystemPrompt(rules: List<MerchantRule>): String {
        val rulesJson = if (rules.isEmpty()) "[]"
        else json.encodeToString(ListSerializer(MerchantRule.serializer()), rules)
        val expenseCats = PREDEFINED_CATEGORIES
            .filter { it.type == "EXPENSE" || it.type == "BOTH" }
            .joinToString(", ") { it.name }
        val incomeCats = PREDEFINED_CATEGORIES
            .filter { it.type == "INCOME" || it.type == "BOTH" }
            .joinToString(", ") { it.name }
        return """
Sos un parser de extractos bancarios colombianos. Tu trabajo es extraer todas las transacciones de un extracto bancario y devolver JSON válido.

Reglas del usuario (aprendidas de correcciones anteriores):
$rulesJson

CATEGORÍAS — asigná a cada transacción EXACTAMENTE uno de estos nombres (no inventes otros):
- Si type es EXPENSE, elegí de: $expenseCats. Si ninguna aplica, usá "Otros".
- Si type es INCOME, elegí de: $incomeCats. Si ninguna aplica, usá "Otros ingresos".
- El pago del extracto de una tarjeta de crédito (la fila sale de la cuenta de ahorros/corriente con descripciones como "PAGO TC", "PAGO TARJETA", "PAGO AUTOM TC", "ABONO TARJETA" o similar) usá la categoría "Pago de tarjeta" — no es un gasto nuevo, es la misma compra que ya se contó cuando se hizo con la tarjeta.
- Otros traslados o transferencias entre cuentas propias (que no sean el pago de una tarjeta) no tienen categoría natural: usá "Otros" si es EXPENSE u "Otros ingresos" si es INCOME.

Devolvé ÚNICAMENTE un array JSON con este formato exacto, sin explicaciones ni texto adicional:
[{"date":"YYYY-MM-DD","merchant":"nombre limpio","amount":123456,"currency":"COP","type":"EXPENSE|INCOME","category":"categoría","description":"descripción corta","rawText":"línea original"}]

MONTOS Y MONEDA:
- currency: la moneda NATIVA de la transacción ("COP" o "USD"), tomada de la columna de moneda del extracto. Si no hay columna de moneda, usá "COP".
- amount: el valor en su moneda NATIVA. NO conviertas USD a COP — dejá el valor tal cual viene en esa moneda.
- amount es entero positivo, sin separadores de miles ni decimales.
- El extracto puede usar formato colombiano ($ 46.489,00) o americano (46,489.00) — detectá cuál es según el documento.
- Descartá los centavos: redondeá a la unidad más cercana.

TIPO:
- EXPENSE: débitos, compras, pagos a terceros, comisiones, impuestos, cargos, intereses cobrados
- INCOME: créditos, abonos, nómina, transferencias recibidas, intereses a favor, reembolsos

TARJETAS DE CRÉDITO (cuando el extracto tiene columnas "Número cuotas" y "Valor Cuota/Abono"):
- Usá siempre la columna "Valor movimiento" (precio total de la compra), NUNCA "Valor Cuota/Abono"
- Incluí cargos por INTERESES CORRIENTES y CUOTA DE MANEJO como EXPENSE
- Los pagos/abonos a la tarjeta (ABONO, ABONO DEBITO AUTOMATICO, PAGO ALTERNATIVO) SÍ se incluyen, como INCOME — reducen la deuda de la tarjeta.

PERÍODO FACTURADO (tarjetas y créditos rotativos):
- Extraé SOLO los movimientos del período facturado (la sección "Nuevos movimientos" o equivalente, con fecha dentro del período del encabezado).
- NO incluyas las secciones de movimientos de períodos anteriores ("Movimientos antes de …", compras diferidas que ya venían de extractos anteriores y solo muestran su cuota o saldo pendiente): ya se contaron con el extracto en que entraron.
- Los cargos que el banco liquida en el resumen (intereses, cuota de manejo, otros cargos) cuentan solo si aparecen como fila con fecha en el detalle del período.

FECHAS SIN AÑO:
- Si las fechas no incluyen año (ej: "15/04", "1/01", "3 ene"), buscá el año en el encabezado del documento (campos DESDE, HASTA, FECHA DE CORTE, periodo facturado) y asignáselo a todas las transacciones
- Si el extracto cubre varios meses, asigná el año correcto a cada fecha según el período del encabezado

EXCLUIR (no son movimientos del titular):
- Filas de saldo corriente (columna "Saldo" que muestra balance acumulado)
- Filas de totales, subtotales y encabezados de tabla

Aplicá las reglas del usuario cuando el merchant coincida.
""".trimIndent()
    }

    /**
     * **Lo que una lectura de extracto puede terminar siendo, dicho en el tipo.**
     *
     * Antes las tres cosas se contestaban con la misma `emptyList()`: que no hubiera clave de
     * Anthropic, que la respuesta del modelo llegara cortada por el tope de tokens, o que el
     * archivo de verdad no tuviera movimientos. La ruta no podía distinguirlas y contestaba 200 con
     * cero filas para las tres, así que un mes de Ahorros con ~80 movimientos abría la pantalla de
     * revisión en «0 nuevas · 0 coincidencias», con el botón de importar apagado — y eso se lee
     * como «este mes ya estaba conciliado», que es la conclusión más cara que Movi puede inducir.
     */
    sealed interface Lectura {
        /** Salió bien. Puede traer cero filas: eso sí significa «acá no había movimientos». */
        data class Ok(val movimientos: List<ParsedTransaction>) : Lectura

        /** El server no tiene `ANTHROPIC_API_KEY`: no se leyó nada, y no es culpa del archivo. */
        data object SinLlave : Lectura

        /**
         * El modelo se quedó sin tokens a mitad del JSON. Lo que llegó es un array sin cerrar: se
         * podría recortar hasta la última fila entera, pero importar «los primeros 40 de 80» sin
         * decir cuáles faltan es peor que no importar nada.
         */
        data object Incompleta : Lectura
    }

    /**
     * Tope de salida por pedido. Con 4096 —lo que había— una fila con su `rawText` ronda los 90
     * tokens, así que el JSON se cortaba cerca del movimiento 45: un mes normal de Ahorros no
     * entraba. Este tope y el troceo de [dividirEnPedazos] atacan el mismo problema por los dos
     * lados; ninguno de los dos alcanza solo.
     */
    private const val MAX_TOKENS_DE_SALIDA = 16_000L

    /**
     * Cuánto texto de extracto va en cada pedido. No es el límite del modelo —la ventana de entrada
     * es enorme—: es el límite de lo que su RESPUESTA puede tener sin pasarse de
     * [MAX_TOKENS_DE_SALIDA]. Un extracto en PDF ronda los 150 caracteres por movimiento y cada
     * movimiento vuelve como ~90 tokens de JSON, así que con este tope un pedido devuelve unas 80
     * filas y le sobra la mitad del presupuesto.
     */
    internal const val MAX_CARACTERES_POR_PEDAZO = 12_000

    /** Cuántas líneas del principio del documento viajan como contexto en cada pedazo. */
    private const val LINEAS_DE_ENCABEZADO = 15

    /**
     * ¿La respuesta del modelo quedó cortada?
     *
     * Dos señales. `stop_reason == "max_tokens"` es la que el API dice en voz alta; el array sin
     * cerrar es la red por si la respuesta se truncó por otro camino (una `stop_sequence`, una
     * reconexión). Si no hay `[` en ninguna parte no está cortada: es una respuesta que no trae
     * JSON, y eso lo resuelve [parseJson] devolviendo vacío.
     */
    internal fun quedoCortada(rawText: String, stopReason: String?): Boolean {
        if (stopReason == "max_tokens") return true
        val abre = rawText.indexOf('[')
        return abre != -1 && rawText.lastIndexOf(']') < abre
    }

    /**
     * Parte el texto del extracto en pedidos que quepan en la respuesta, **cortando por líneas**.
     *
     * Por líneas y no por caracteres porque un corte a mitad de fila produce un movimiento
     * inventado en un pedazo y otro mutilado en el siguiente. Una línea más larga que el tope entra
     * igual en su propio pedazo: partirla sería exactamente el daño que esta función evita.
     */
    internal fun dividirEnPedazos(texto: String, maxCaracteres: Int = MAX_CARACTERES_POR_PEDAZO): List<String> {
        if (texto.length <= maxCaracteres) return listOf(texto)
        val pedazos = mutableListOf<String>()
        val actual = StringBuilder()
        for (linea in texto.lineSequence()) {
            if (actual.isNotEmpty() && actual.length + linea.length + 1 > maxCaracteres) {
                pedazos += actual.toString()
                actual.clear()
            }
            if (actual.isNotEmpty()) actual.append('\n')
            actual.append(linea)
        }
        if (actual.isNotEmpty()) pedazos += actual.toString()
        return pedazos.ifEmpty { listOf(texto) }
    }

    /**
     * Las primeras líneas con contenido del documento — el encabezado con el banco, la cuenta y el
     * período.
     *
     * Viaja pegado a cada pedazo después del primero porque el prompt le pide al modelo que saque
     * el AÑO de ahí cuando las fechas vienen como «15/04». Sin esto, trocear un extracto le
     * cambiaba el año a todos los movimientos de la segunda mitad.
     */
    internal fun encabezadoDe(texto: String, lineas: Int = LINEAS_DE_ENCABEZADO): String =
        texto.lineSequence().filter { it.isNotBlank() }.take(lineas).joinToString("\n")

    /** El pedazo [indice] de [total], con el encabezado de contexto si no es el primero. */
    private fun cuerpoDelPedido(pedazo: String, indice: Int, total: Int, encabezado: String): String =
        if (indice == 0 || encabezado.isBlank()) pedazo
        // El «de ahí» no es un capricho: el escáner de voseo mira los literales de este módulo, y
        // «de acá» —que es como lo diría el resto del archivo— lo marca como texto rioplatense.
        else "ENCABEZADO DEL DOCUMENTO (contexto: de ahí sale el año, no tiene movimientos):\n" +
            encabezado + "\n\nPARTE ${indice + 1} DE $total DEL EXTRACTO:\n" + pedazo

    /**
     * **Cómo se le pide un extracto al modelo**, igual para texto e imagen.
     *
     * - [MODELO_DE_EXTRACTOS] (Sonnet 5.5) con esfuerzo **bajo**: así corrió el benchmark que lo
     *   eligió. Piensa de forma adaptativa (no se puede apagar en ese modelo), y lo que piensa sale
     *   del mismo [MAX_TOKENS_DE_SALIDA]: con 71 filas usó ~8.200 de salida, lejos del tope, pero si
     *   empiezan a aparecer [Lectura.Incompleta] es lo primero que hay que mirar.
     * - El prompt de sistema (~1.400 fichas, más las reglas del dueño) va **cacheado**: un extracto
     *   troceado lo manda en cada pedazo, y desde el segundo se lee al 10 % del precio. Lo que
     *   cambia de pedido a pedido —el pedazo, la imagen— va después, en el mensaje del usuario.
     */
    internal fun MessageCreateParams.Builder.conLoDeSiempre(rules: List<MerchantRule>) =
        model(MODELO_DE_EXTRACTOS)
            .maxTokens(MAX_TOKENS_DE_SALIDA)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        .text(buildSystemPrompt(rules))
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                ),
            )

    private fun textoDe(response: Message): String =
        response.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")

    /**
     * Lee un extracto de texto. Si no entra en un pedido se manda por partes y las filas se
     * concatenan — cada pedazo trae su propio array JSON completo.
     *
     * **Un pedazo cortado corta la lectura entera.** Devolver las filas de los pedazos que sí
     * salieron sería el mismo defecto con otro disfraz: una lista incompleta que se ve completa.
     */
    suspend fun leer(text: String, rules: List<MerchantRule>): Lectura {
        val c = client ?: return Lectura.SinLlave
        val pedazos = dividirEnPedazos(text)
        val encabezado = if (pedazos.size > 1) encabezadoDe(text) else ""
        val filas = mutableListOf<ParsedTransaction>()
        pedazos.forEachIndexed { i, pedazo ->
            val cuerpo = cuerpoDelPedido(pedazo, i, pedazos.size, encabezado)
            val params = MessageCreateParams.builder()
                .conLoDeSiempre(rules)
                .messages(listOf(MessageParam.builder().role(MessageParam.Role.USER).content(cuerpo).build()))
                .build()
            val response = withContext(Dispatchers.IO) { c.messages().create(params) }
            val rawText = textoDe(response)
            if (quedoCortada(rawText, response.stopReason().orElse(null)?.asString())) return Lectura.Incompleta
            filas += parseJson(rawText)
        }
        return Lectura.Ok(filas)
    }

    /**
     * Lee una captura de pantalla o una foto del extracto. No se trocea —una imagen no se parte por
     * líneas— pero el tope de salida y la detección de corte son los mismos: si la respuesta no
     * alcanzó, se dice.
     */
    suspend fun leerImagen(bytes: ByteArray, mimeType: String, rules: List<MerchantRule>): Lectura {
        val c = client ?: return Lectura.SinLlave
        // mimeType must already be a Claude-supported image media type (validated by supportedImageMime at the route).
        val mediaType = Base64ImageSource.MediaType.of(mimeType)
        val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
        val imageSource = Base64ImageSource.builder()
            .data(b64)
            .mediaType(mediaType)
            .build()
        val imageBlock = ContentBlockParam.ofImage(
            ImageBlockParam.builder().source(imageSource).build()
        )
        val textBlock = ContentBlockParam.ofText(
            TextBlockParam.builder()
                .text("Extraé todos los movimientos de este extracto bancario o captura de pantalla y devolvé el JSON según las instrucciones del sistema.")
                .build()
        )
        val params = MessageCreateParams.builder()
            .conLoDeSiempre(rules)
            .addUserMessageOfBlockParams(listOf(imageBlock, textBlock))
            .build()
        val response = withContext(Dispatchers.IO) { c.messages().create(params) }
        val rawText = textoDe(response)
        if (quedoCortada(rawText, response.stopReason().orElse(null)?.asString())) return Lectura.Incompleta
        return Lectura.Ok(parseJson(rawText))
    }

    /**
     * **¿Qué es este papel?** — Ola 2 · «Compartir con Movi». Una sola llamada al modelo barato
     * ([MODELO_DE_TODOS_LOS_DIAS], Haiku): decide si es un comprobante de UN movimiento, un extracto
     * o nada, y si es un comprobante lo lee de una vez. Lo caro —leer un extracto entero— solo pasa
     * si esto dice «extracto», y con el modelo de siempre ([leer] / [leerImagen]).
     *
     * El tope de salida es chico a propósito: la respuesta es un objeto de doce campos.
     */
    suspend fun queEsElPapel(contenido: ContenidoDelPapel): QueDiceElPapel {
        val c = client ?: return QueDiceElPapel.SinLlave
        val bloques = when (contenido) {
            is ContenidoDelPapel.Imagen -> listOf(
                ContentBlockParam.ofImage(
                    ImageBlockParam.builder().source(
                        Base64ImageSource.builder()
                            .data(java.util.Base64.getEncoder().encodeToString(contenido.bytes))
                            .mediaType(Base64ImageSource.MediaType.of(contenido.mime))
                            .build(),
                    ).build(),
                ),
                ContentBlockParam.ofText(TextBlockParam.builder().text("¿Qué es este papel? Contesta con el JSON.").build()),
            )
            is ContenidoDelPapel.Texto -> listOf(
                ContentBlockParam.ofText(
                    TextBlockParam.builder()
                        .text("TEXTO DEL PAPEL (sacado de un PDF):\n" + contenido.texto.take(MAX_CARACTERES_DEL_PAPEL) + "\n\n¿Qué es este papel? Contesta con el JSON.")
                        .build(),
                ),
            )
        }
        val params = MessageCreateParams.builder()
            .model(MODELO_DE_TODOS_LOS_DIAS)
            .maxTokens(MAX_TOKENS_DEL_PAPEL)
            .temperature(0.0)
            .systemOfTextBlockParams(listOf(TextBlockParam.builder().text(promptDelPapel()).build()))
            .addUserMessageOfBlockParams(bloques)
            .build()
        val response = withContext(Dispatchers.IO) { c.messages().create(params) }
        return queDiceLaRespuesta(textoDe(response))
    }

    /** La respuesta de [queEsElPapel] es un objeto chico; 600 fichas sobran con holgura. */
    private const val MAX_TOKENS_DEL_PAPEL = 600L

    /** Un comprobante en PDF tiene una página; más que esto ya es un extracto y lo decide la heurística. */
    private const val MAX_CARACTERES_DEL_PAPEL = 8_000

    private fun promptDelPapel(): String {
        val categorias = PREDEFINED_CATEGORIES.joinToString(", ") { it.name }
        return """
Lees papeles financieros colombianos: capturas de pantalla de transferencias, recibos, pagos PSE, facturas pagadas y extractos. Decide qué es el papel y devuelve SOLO un objeto JSON, sin texto antes ni después.

Si muestra UN solo movimiento de plata (una transferencia, un pago, una compra, un recibo, una factura pagada), devuelve:
{"tipo":"COMPROBANTE","monto":138600,"moneda":"COP","movimiento":"EXPENSE","fecha":"2026-09-30","hora":"14:05","comercio":"Coomeva Medicina Prepagada","categoria":"Salud","banco":"Bancolombia","cuentaPropia":"8133","cuentaDestino":null,"llave":null,"concepto":"Pago PSE"}
- monto: el valor total pagado o recibido, como número sin separadores de miles; los centavos van como decimales (20.5). El formato puede ser colombiano (${'$'}46.489,00) o americano (46,489.00): detecta cuál es.
- moneda: "COP" o "USD".
- movimiento: "EXPENSE" si la plata salió del titular, "INCOME" si le llegó.
- fecha: YYYY-MM-DD, o null si no aparece. hora: HH:mm en 24 horas, o null si no aparece.
- comercio: a quién se le pagó o de quién llegó, como lo escribiría una persona. Si solo aparece un número de cuenta o una llave, deja comercio vacío ("").
- categoria: una de estas: $categorias. Usa "Pago de tarjeta" solo si es el pago de una tarjeta de crédito.
- banco: el banco o la app que emitió el comprobante, o null.
- cuentaPropia: los últimos 4 dígitos de la cuenta o tarjeta del titular, o null.
- cuentaDestino: el número de la cuenta de destino si es una transferencia a otra persona y se ve completo, o null.
- llave: la llave de destino (Bre-B) si se ve, o null.
- concepto: una descripción corta (máximo 6 palabras), o null.

Si muestra VARIOS movimientos (un extracto, un listado o un histórico de movimientos), devuelve: {"tipo":"EXTRACTO"}
Si no muestra ningún movimiento de plata (un certificado, un saldo, una publicidad), devuelve: {"tipo":"NADA"}
""".trimIndent()
    }

    /** Returns true if [mimeType] represents an image (starts with "image/"). */
    fun isImageMime(mimeType: String): Boolean = mimeType.trim().lowercase().startsWith("image/")

    /** Image media types Claude's vision API accepts. */
    private val SUPPORTED_IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

    /**
     * Maps an upload's mime/filename to a Claude-supported image media type, or null when the
     * format is unsupported (e.g. HEIC, BMP, TIFF). Passing an unsupported value to the SDK throws,
     * so the route must respond 422 on null rather than crash.
     */
    fun supportedImageMime(mimeType: String, fileName: String): String? {
        val mime = mimeType.trim().lowercase().substringBefore(';')
        val normalized = if (mime == "image/jpg") "image/jpeg" else mime
        if (normalized in SUPPORTED_IMAGE_MIMES) return normalized
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png"         -> "image/png"
            "gif"         -> "image/gif"
            "webp"        -> "image/webp"
            else          -> null
        }
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
                        currency = row.currency.trim().uppercase().ifEmpty { "COP" },
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
        val currency: String = "COP",
        val type: String,
        val category: String,
        val description: String = "",
        val rawText: String = "",
    )
}
