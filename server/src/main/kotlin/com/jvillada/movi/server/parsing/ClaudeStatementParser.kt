package com.jvillada.movi.server.parsing

import com.jvillada.movi.server.observabilidad.crearMensaje
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

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
     * Cuánto texto de extracto va, como mucho, en cada pedido. No es el límite del modelo —la
     * ventana de entrada es enorme—: es el límite de lo que su RESPUESTA puede tener sin pasarse de
     * [MAX_TOKENS_DE_SALIDA]. Un extracto de tarjeta en PDF ronda los 150 caracteres por movimiento
     * y cada movimiento vuelve como ~90 tokens de JSON, así que con este tope un pedido devuelve
     * unas 80 filas y le sobra la mitad del presupuesto.
     *
     * Vuelta 128: ese supuesto falla en un extracto de ahorros denso (~60 caracteres por fila: doce
     * mil caracteres son ~200 filas y más de 16.000 fichas). Por eso el tamaño de arranque lo estima
     * [tamanoInicialDelPedazo] y, si aun así un pedazo vuelve cortado, se parte en dos
     * ([leerPorPedazos]).
     */
    internal const val MAX_CARACTERES_POR_PEDAZO = 12_000

    /**
     * Cuántas filas se le piden, como mucho, a un pedido cuando el tamaño se estima por filas
     * ([tamanoInicialDelPedazo]). 100 filas son ~9.000 fichas de JSON: queda lugar para pensar
     * dentro de [MAX_TOKENS_DE_SALIDA]. Elegido, no medido; con 120 caracteres por fila o más
     * (las tarjetas, Nu) da el mismo [MAX_CARACTERES_POR_PEDAZO] de siempre.
     */
    internal const val FILAS_POR_PEDAZO = 100

    /**
     * Lo más chico que puede quedar una mitad al partir un pedazo cortado: un pedazo de menos del
     * doble ya no se parte. Junto con [PROFUNDIDAD_MAXIMA] es el tope de la recursión —12.000
     * caracteres se parten a lo sumo en 8 de 1.500— y lo que pone el piso al costo de un extracto
     * que nunca va a entrar.
     */
    internal const val MINIMO_DE_UN_PEDAZO = 1_500

    /** Cuántas veces, como mucho, se parte en dos un mismo pedazo que vuelve cortado. */
    internal const val PROFUNDIDAD_MAXIMA = 4

    /**
     * Cuántos pedidos al modelo van a la vez en una lectura. Tres: un extracto de cinco pedazos
     * tarda lo que dos y no uno detrás de otro. Más no: los pedidos comparten el límite de fichas
     * por minuto de la cuenta de Anthropic, y el SDK reintenta solo un 429 (con espera), pero una
     * lectura que se come el límite la paga el resto de Movi (el chat, los papeles).
     */
    internal const val PEDIDOS_A_LA_VEZ = 3

    /** Cuántas líneas del principio del documento viajan como contexto en cada pedazo. */
    private const val LINEAS_DE_ENCABEZADO = 15

    private val log = LoggerFactory.getLogger("LecturaDeExtractos")

    /**
     * **Dónde empieza una fecha de movimiento**, para partir una línea larga sin romper una fila y
     * para contar filas probables. Reconoce, sin otra letra o cifra pegada antes:
     * - ISO: `2026-08-01`;
     * - día y mes con barra: `01/08`, `1/8/26`, `01/08/2026`;
     * - día y mes en letras, en español: `04 OCT`, `3 ene.`, `12-dic-2026` (sin el «de» de
     *   «5 de septiembre»: así lo escriben los bancos en la fila).
     *
     * El mes va entero o abreviado y no puede seguir otra letra: «1 MARCA» no es una fecha.
     */
    private val INICIO_DE_FECHA = Regex(
        """(?<![\p{L}\p{N}/.\-])(?:\d{4}-\d{2}-\d{2}|\d{1,2}/\d{1,2}(?:/\d{2,4})?|\d{1,2}[ \-]""" +
            """(?:ene(?:ro)?|feb(?:rero)?|mar(?:zo)?|abr(?:il)?|may(?:o)?|jun(?:io)?|jul(?:io)?|""" +
            """ago(?:sto)?|sep(?:t(?:iembre)?)?|oct(?:ubre)?|nov(?:iembre)?|dic(?:iembre)?)\.?(?!\p{L}))(?!\p{N})""",
        RegexOption.IGNORE_CASE,
    )

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
     * inventado en un pedazo y otro mutilado en el siguiente. Una línea más larga que el tope (un PDF
     * cuyo texto salió en un solo renglón) se parte antes con [partirLineaLarga], que corta antes de
     * una fecha y nunca a mitad de una palabra.
     */
    internal fun dividirEnPedazos(texto: String, maxCaracteres: Int = MAX_CARACTERES_POR_PEDAZO): List<String> {
        if (texto.length <= maxCaracteres) return listOf(texto)
        val pedazos = mutableListOf<String>()
        val actual = StringBuilder()
        for (linea in texto.lineSequence().flatMap { partirLineaLarga(it, maxCaracteres) }) {
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
     * **Una línea más larga que [max], en trozos que no rompen una fila** (vuelta 128).
     *
     * La regla, en orden:
     * 1. se corta **antes de la última fecha** ([INICIO_DE_FECHA]) que deje el trozo dentro del tope:
     *    en un extracto cada fila empieza por su fecha, así que ahí termina una fila y empieza otra;
     * 2. si en ese tramo no hay fecha, **antes de la última palabra** que quepa (después de un
     *    espacio): una fila puede quedar en dos trozos, pero ninguna cifra ni nombre se parte;
     * 3. si no hay ni fecha ni espacio dentro del tope, el trozo se alarga **hasta la próxima fecha o
     *    el próximo espacio**: se pasa del tope antes que cortar a mitad de una palabra;
     * 4. sin ninguno de los dos en el resto, el resto va entero.
     *
     * Los trozos son subcadenas seguidas: unidos sin nada en medio dan la línea de nuevo.
     */
    internal fun partirLineaLarga(linea: String, max: Int): List<String> {
        if (linea.length <= max || max <= 0) return listOf(linea)
        val fechas = INICIO_DE_FECHA.findAll(linea).map { it.range.first }.toList()
        val trozos = mutableListOf<String>()
        var inicio = 0
        while (linea.length - inicio > max) {
            val limite = inicio + max
            val corte = fechas.lastOrNull { it in (inicio + 1)..limite }
                ?: ultimoInicioDePalabra(linea, inicio + 1, limite)
                ?: fechas.firstOrNull { it > limite }?.let { fecha ->
                    val palabra = primerInicioDePalabra(linea, limite + 1)
                    if (palabra != null) minOf(fecha, palabra) else fecha
                }
                ?: primerInicioDePalabra(linea, limite + 1)
                ?: break
            trozos += linea.substring(inicio, corte)
            inicio = corte
        }
        trozos += linea.substring(inicio)
        return trozos
    }

    /** ¿Empieza una palabra en [i]? (un carácter visible justo después de un espacio). */
    private fun empiezaPalabra(linea: String, i: Int): Boolean =
        i in 1 until linea.length && linea[i - 1].isWhitespace() && !linea[i].isWhitespace()

    private fun ultimoInicioDePalabra(linea: String, desde: Int, hasta: Int): Int? =
        (minOf(hasta, linea.length - 1) downTo desde).firstOrNull { empiezaPalabra(linea, it) }

    private fun primerInicioDePalabra(linea: String, desde: Int): Int? =
        (desde until linea.length).firstOrNull { empiezaPalabra(linea, it) }

    /**
     * **Un pedazo que volvió cortado, en dos mitades** (vuelta 128), o `null` si ya no se puede
     * partir. Por líneas, en la línea que deja la primera mitad más cerca de la mitad del texto; si
     * el pedazo es una sola línea, con [partirLineaLarga] (antes de una fecha, o de una palabra).
     * Las dos mitades, una detrás de la otra, son el pedazo entero.
     */
    internal fun partirEnDos(pedazo: String): Pair<String, String>? {
        val lineas = pedazo.lines()
        val (unidades, separador) =
            if (lineas.size >= 2) lineas to "\n"
            else partirLineaLarga(pedazo, (pedazo.length + 1) / 2) to ""
        if (unidades.size < 2) return null
        val mitad = pedazo.length / 2
        var acumulado = 0
        var corte = unidades.size - 1
        for (i in unidades.indices) {
            acumulado += unidades[i].length + separador.length
            if (acumulado >= mitad) {
                corte = i + 1
                break
            }
        }
        corte = corte.coerceIn(1, unidades.size - 1)
        return unidades.subList(0, corte).joinToString(separador) to
            unidades.subList(corte, unidades.size).joinToString(separador)
    }

    /**
     * **De qué tamaño arrancan los pedazos** (vuelta 128): por filas probables, no solo por
     * caracteres. Cuenta los renglones que empiezan con una fecha ([INICIO_DE_FECHA]), saca cuántos
     * caracteres ocupa una fila en este documento y deja [FILAS_POR_PEDAZO] filas por pedido, entre
     * el doble de [MINIMO_DE_UN_PEDAZO] y [MAX_CARACTERES_POR_PEDAZO]. Sin renglones con fecha, el
     * tope de siempre.
     *
     * No es lo que evita la falla —eso es partir el pedazo cortado—: evita el primer pedido perdido
     * (el de Caro tardó 84 s en volver cortado) en los extractos densos, que son los que se cortan.
     */
    internal fun tamanoInicialDelPedazo(texto: String): Int {
        val filas = texto.lineSequence().count { INICIO_DE_FECHA.find(it.trimStart())?.range?.first == 0 }
        if (filas == 0) return MAX_CARACTERES_POR_PEDAZO
        val porFila = (texto.length / filas).coerceAtLeast(1)
        return (porFila.toLong() * FILAS_POR_PEDAZO)
            .coerceIn(2L * MINIMO_DE_UN_PEDAZO, MAX_CARACTERES_POR_PEDAZO.toLong())
            .toInt()
    }

    /**
     * Las primeras líneas con contenido del documento — el encabezado con el banco, la cuenta y el
     * período.
     *
     * Viaja pegado a cada pedazo que no arranca el documento porque el prompt le pide al modelo que
     * saque el AÑO de ahí cuando las fechas vienen como «15/04». Sin esto, trocear un extracto le
     * cambiaba el año a todos los movimientos de la segunda mitad.
     */
    internal fun encabezadoDe(texto: String, lineas: Int = LINEAS_DE_ENCABEZADO): String =
        texto.lineSequence().filter { it.isNotBlank() }.take(lineas).joinToString("\n")

    /**
     * El cuerpo del pedido de un pedazo. El que arranca el documento va tal cual (ya trae el
     * encabezado); todos los demás —también las mitades de un pedazo partido— llevan el encabezado
     * de contexto y su [etiqueta] («2 DE 4», «2.1 DE 4»: la parte y, si se partió, cuál mitad).
     */
    private fun cuerpoDelPedido(pedazo: String, etiqueta: String, empiezaElDocumento: Boolean, encabezado: String): String =
        if (empiezaElDocumento || encabezado.isBlank()) pedazo
        // El «de ahí» no es un capricho: el escáner de voseo mira los literales de este módulo, y
        // «de acá» —que es como lo diría el resto del archivo— lo marca como texto rioplatense.
        else "ENCABEZADO DEL DOCUMENTO (contexto: de ahí sale el año, no tiene movimientos):\n" +
            encabezado + "\n\nPARTE $etiqueta DEL EXTRACTO:\n" + pedazo

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
     * concatenan — cada pedazo trae su propio array JSON completo. Ver [leerPorPedazos].
     */
    suspend fun leer(text: String, rules: List<MerchantRule>): Lectura {
        val c = client ?: return Lectura.SinLlave
        return leerPorPedazos(text) { cuerpo ->
            val params = MessageCreateParams.builder()
                .conLoDeSiempre(rules)
                .messages(listOf(MessageParam.builder().role(MessageParam.Role.USER).content(cuerpo).build()))
                .build()
            val response = withContext(Dispatchers.IO) { crearMensaje(c, params) }
            RespuestaDelPedazo(
                texto = textoDe(response),
                stopReason = response.stopReason().orElse(null)?.asString(),
                fichasDeSalida = runCatching { response.usage().outputTokens() }.getOrNull(),
            )
        }
    }

    /** Lo que contestó el modelo a un pedazo: el texto, por qué paró y cuántas fichas escribió. */
    internal data class RespuestaDelPedazo(
        val texto: String,
        val stopReason: String?,
        val fichasDeSalida: Long? = null,
    )

    /**
     * **La lectura de un extracto por pedazos que se parten solos** (vuelta 128).
     *
     * 1. El texto se parte con [dividirEnPedazos] en pedazos de [maxCaracteres] (por defecto,
     *    [tamanoInicialDelPedazo]).
     * 2. Cada pedazo va al modelo por [pedir], hasta [aLaVez] pedidos al mismo tiempo.
     * 3. **Si un pedazo vuelve cortado, se parte en dos** ([partirEnDos]) y se lee cada mitad, en
     *    recursión, hasta [PROFUNDIDAD_MAXIMA] veces y sin mitades de menos de ~[MINIMO_DE_UN_PEDAZO]
     *    caracteres. Las filas que el modelo devolvió en el pedido cortado se tiran: las vuelven a
     *    traer sus mitades.
     * 4. **Solo si un pedazo que ya no se puede partir sigue cortado, la lectura entera es
     *    [Lectura.Incompleta]** —la regla de oro: nunca una lista a medias— y los pedidos que
     *    faltan se cancelan.
     *
     * Las filas salen en el orden del documento aunque los pedidos terminen en otro, y el
     * encabezado de contexto ([encabezadoDe]) viaja en todo pedazo que no arranca el documento,
     * también en las mitades. Al terminar deja una línea de log ([DiagnosticoDeLaLectura]) sin una
     * sola letra del extracto.
     */
    internal suspend fun leerPorPedazos(
        texto: String,
        maxCaracteres: Int = tamanoInicialDelPedazo(texto),
        aLaVez: Int = PEDIDOS_A_LA_VEZ,
        anotar: (DiagnosticoDeLaLectura) -> Unit = { log.info(it.lineaDelLog()) },
        pedir: suspend (cuerpo: String) -> RespuestaDelPedazo,
    ): Lectura {
        val inicio = System.nanoTime()
        val pedazos = dividirEnPedazos(texto, maxCaracteres)
        val lectura = LecturaEnCurso(encabezadoDe(texto), pedazos.size, Semaphore(aLaVez.coerceAtLeast(1)), pedir)
        var resultado = "falla"
        var filas: Int? = null
        try {
            val leidas = try {
                coroutineScope {
                    pedazos.mapIndexed { i, pedazo ->
                        async { lectura.leerUnPedazo(pedazo, listOf(i + 1), empiezaElDocumento = i == 0) }
                    }.awaitAll().flatten()
                }
            } catch (_: PedazoQueNoCabe) {
                resultado = "incompleta"
                return Lectura.Incompleta
            }
            resultado = "ok"
            filas = leidas.size
            return Lectura.Ok(leidas)
        } finally {
            val lineas = texto.lines()
            anotar(
                DiagnosticoDeLaLectura(
                    resultado = resultado,
                    filas = filas,
                    caracteres = texto.length,
                    lineas = lineas.size,
                    lineaMasLarga = lineas.maxOfOrNull { it.length } ?: 0,
                    tamanoDelPedazo = maxCaracteres,
                    pedazosIniciales = pedazos.size,
                    pedidos = lectura.pedidos.get(),
                    cortes = lectura.cortes.get(),
                    fichasPorPedazo = lectura.fichasEnOrden(),
                    milisegundos = (System.nanoTime() - inicio) / 1_000_000,
                ),
            )
        }
    }

    /** Un pedazo mínimo que siguió cortado: corta la lectura entera (y cancela lo que falta). */
    private class PedazoQueNoCabe : RuntimeException("un pedazo mínimo del extracto siguió cortado")

    /** Lo que comparte una lectura entre sus pedidos: el encabezado, el semáforo y las cuentas. */
    private class LecturaEnCurso(
        val encabezado: String,
        val total: Int,
        val semaforo: Semaphore,
        val pedir: suspend (String) -> RespuestaDelPedazo,
    ) {
        val pedidos = AtomicInteger()
        val cortes = AtomicInteger()
        private val fichas = ConcurrentLinkedQueue<Pair<List<Int>, String>>()

        /** Las fichas de salida de cada pedido, en el orden del documento («*» = volvió cortado). */
        fun fichasEnOrden(): List<String> = fichas.sortedWith { a, b -> compararRutas(a.first, b.first) }.map { it.second }

        suspend fun leerUnPedazo(pedazo: String, ruta: List<Int>, empiezaElDocumento: Boolean, profundidad: Int = 0): List<ParsedTransaction> {
            val etiqueta = ruta.joinToString(".") + " DE $total"
            val cuerpo = cuerpoDelPedido(pedazo, etiqueta, empiezaElDocumento, encabezado)
            val respuesta = semaforo.withPermit { pedir(cuerpo) }
            pedidos.incrementAndGet()
            val cortada = quedoCortada(respuesta.texto, respuesta.stopReason)
            fichas += ruta to ((respuesta.fichasDeSalida?.toString() ?: "?") + if (cortada) "*" else "")
            if (!cortada) return parseJson(respuesta.texto)
            cortes.incrementAndGet()
            val mitades =
                if (profundidad < PROFUNDIDAD_MAXIMA && pedazo.length >= 2 * MINIMO_DE_UN_PEDAZO) partirEnDos(pedazo)
                else null
            mitades ?: throw PedazoQueNoCabe()
            return coroutineScope {
                val primera = async { leerUnPedazo(mitades.first, ruta + 1, empiezaElDocumento, profundidad + 1) }
                val segunda = async { leerUnPedazo(mitades.second, ruta + 2, empiezaElDocumento = false, profundidad + 1) }
                primera.await() + segunda.await()
            }
        }
    }

    /** Orden del documento entre rutas («1» < «1.1» < «1.2» < «2»). */
    private fun compararRutas(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val c = a[i].compareTo(b[i])
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }

    /**
     * **Una línea de log por lectura, sin datos** (vuelta 128): cuántos pedazos, cuántos cortes,
     * de qué tamaño era el texto y cuántas fichas escribió cada pedido. Nunca una letra del
     * extracto: con esto se diagnostica un «quedó incompleta» sin mirar el papel de nadie.
     */
    internal data class DiagnosticoDeLaLectura(
        val resultado: String,
        val filas: Int?,
        val caracteres: Int,
        val lineas: Int,
        val lineaMasLarga: Int,
        val tamanoDelPedazo: Int,
        val pedazosIniciales: Int,
        val pedidos: Int,
        val cortes: Int,
        val fichasPorPedazo: List<String>,
        val milisegundos: Long,
    ) {
        fun lineaDelLog(): String =
            "Lectura de extracto: resultado=$resultado filas=${filas ?: "-"} caracteres=$caracteres " +
                "lineas=$lineas lineaMasLarga=$lineaMasLarga tamanoDelPedazo=$tamanoDelPedazo " +
                "pedazos=$pedazosIniciales pedidos=$pedidos cortes=$cortes " +
                "fichasDeSalida=${fichasPorPedazo.joinToString(",", "[", "]")} ms=$milisegundos"
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
        val response = withContext(Dispatchers.IO) { crearMensaje(c, params) }
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
            // Vuelta 106 · Haiku 5.5: piensa aunque no se lo pidan y el pensamiento sale del mismo
            // techo, así que el techo reserva lugar para pensar además del JSON; con esfuerzo
            // «bajo» casi no piensa. Y sin `temperature`: Haiku 5.5 da 400 con cualquier valor
            // que no sea el de fábrica (el JSON lo fija el prompt, no el azar).
            .maxTokens(MAX_TOKENS_DEL_PAPEL + RESERVA_PARA_PENSAR_EL_PAPEL)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .systemOfTextBlockParams(listOf(TextBlockParam.builder().text(promptDelPapel()).build()))
            .addUserMessageOfBlockParams(bloques)
            .build()
        val response = withContext(Dispatchers.IO) { crearMensaje(c, params) }
        return queDiceLaRespuesta(textoDe(response))
    }

    /** La respuesta de [queEsElPapel] es un objeto chico; 600 fichas sobran con holgura. */
    private const val MAX_TOKENS_DEL_PAPEL = 600L

    /** Lo que se le deja a Haiku 5.5 para pensar antes del JSON (ver [queEsElPapel]). */
    private const val RESERVA_PARA_PENSAR_EL_PAPEL = 1_500L

    /** Un comprobante en PDF tiene una página; más que esto ya es un extracto y lo decide la heurística. */
    private const val MAX_CARACTERES_DEL_PAPEL = 8_000

    /**
     * El prompt del clasificador. Las tres reglas del final salieron del benchmark del 2026-10-04:
     * el extracto del crédito del vehículo (cuota, saldo y tasa, sin movimientos) se iba a extracto
     * y la lectura cara volvía vacía; el pantallazo del desembolso de la hipoteca (~$200 M) salía
     * como un GASTO, y un desembolso es plata que entró (regla del dueño).
     */
    internal fun promptDelPapel(): String {
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
- El extracto o la pantalla de un CRÉDITO (hipoteca, vehículo, libre inversión, libranza) que muestra saldo, cuota a pagar, fecha límite o el desglose del último abono, sin una lista de movimientos, es {"tipo":"NADA"}: lo que dice es lo que se DEBE, no un movimiento hecho.
- Una factura o cuenta de cobro que todavía no se ha pagado es {"tipo":"NADA"}.
- Un desembolso de crédito es plata que ENTRÓ al titular: "movimiento":"INCOME".
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

    /**
     * El array JSON de la respuesta, en movimientos. **Una fila de $0 no es un movimiento**: el
     * prompt pide montos enteros, así que lo de menos de una unidad (los intereses diarios de $0,15,
     * un ajuste de USD 0,06) volvía como `amount: 0` y entraba a la revisión como una fila sin
     * plata. Se descarta acá, no en el prompt: es la red que no depende de que el modelo obedezca.
     */
    fun parseJson(rawText: String): List<ParsedTransaction> {
        val start = rawText.indexOf('[')
        val end = rawText.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return emptyList()
        val arrayJson = rawText.substring(start, end + 1)
        return runCatching {
            json.decodeFromString(ListSerializer(ClaudeRow.serializer()), arrayJson)
                .filter { it.amount != 0L }
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
