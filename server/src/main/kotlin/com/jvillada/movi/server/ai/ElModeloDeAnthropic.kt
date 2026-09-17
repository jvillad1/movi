package com.jvillada.movi.server.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolChoiceNone
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * **El traductor entre el bucle y el SDK.** No toma ninguna decisión: quién pregunta, cuántas
 * veces y qué se le contesta lo decide [conversarConHerramientas], que se prueba sin red. Acá solo
 * se arma la llamada, se lee lo que vino y se guarda la conversación para la vuelta siguiente.
 *
 * Esa división es a propósito. Y de lo que queda acá, lo único que no se puede probar sin gastar
 * una llamada de verdad es el `POST` en sí: está izado a [llamar] para que la forma de la
 * conversación —que es donde estaría el error caro— sí se pueda mirar en una prueba.
 */
internal class ElModeloDeAnthropic(
    private val modelo: String,
    private val persona: String,
    private val contexto: String,
    mensajesDelDueno: List<MessageParam>,
    private val maxTokens: Long = MAX_TOKENS_DE_RESPUESTA,
    /**
     * **Pensar cuesta como salida, que es lo caro.** Para «¿cuánto gasté en Comida?» —donde la
     * cuenta la hace la consulta y no el modelo— no compra nada. Se enciende solo en el camino
     * caro, el de los consejos.
     */
    private val piensa: Boolean = false,
    /**
     * A qué modelo reintentar **una vez** si el primero falla. Existe por una razón concreta: el
     * id de un modelo es un texto que viaja a la API, y si alguno dejara de estar disponible en
     * esta cuenta el asistente se caería entero. Con esto, el peor caso es una respuesta más cara,
     * no una pantalla de error.
     */
    private val modeloDeRespaldo: String? = null,
    /**
     * **La única línea que de verdad llama a la red**, izada a parámetro para poder probar todo lo
     * demás: que las herramientas se ofrezcan (y se dejen de ofrecer en la última vuelta), y sobre
     * todo que el turno del asistente y sus resultados vuelvan con la forma que la API exige.
     *
     * Esa forma es donde estaría el error caro y silencioso —un `tool_result` atado al id
     * equivocado, un turno sin el bloque de pensamiento— y sin esto no se podía probar sin gastar
     * una llamada de verdad.
     */
    private val llamar: suspend (MessageCreateParams) -> Message,
) : ElModeloConHerramientas {

    constructor(
        client: AnthropicClient,
        modelo: String,
        persona: String,
        contexto: String,
        mensajesDelDueno: List<MessageParam>,
        maxTokens: Long = MAX_TOKENS_DE_RESPUESTA,
        piensa: Boolean = false,
        modeloDeRespaldo: String? = null,
    ) : this(modelo, persona, contexto, mensajesDelDueno, maxTokens, piensa, modeloDeRespaldo, { params ->
        withContext(Dispatchers.IO) { client.messages().create(params) }
    })

    private val turnos: MutableList<MessageParam> = mensajesDelDueno.toMutableList()

    /**
     * La última respuesta, con sus bloques **originales**. Hace falta tal cual: un turno del
     * asistente que pidió herramientas se le devuelve al modelo completo —pensamiento incluido— o
     * la API rechaza la conversación.
     */
    private var ultima: Message? = null

    /** Las fichas que consumió la conversación, para poder mirar el costo real y no estimarlo. */
    var fichasDeEntrada: Long = 0L
        private set
    var fichasDeSalida: Long = 0L
        private set
    var fichasLeidasDeCache: Long = 0L
        private set

    override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo {
        val respuesta = try {
            llamar(armarLlamada(puedeUsarHerramientas))
        } catch (falla: Exception) {
            val respaldo = modeloDeRespaldo ?: throw falla
            llamar(armarLlamada(puedeUsarHerramientas, conEsteModelo = respaldo))
        }
        ultima = respuesta
        respuesta.usage().let { uso ->
            fichasDeEntrada += uso.inputTokens()
            fichasDeSalida += uso.outputTokens()
            fichasLeidasDeCache += uso.cacheReadInputTokens().orElse(0L)
        }

        val pedidos = respuesta.content().mapNotNull { it.toolUse().orElse(null) }
        return if (pedidos.isEmpty()) {
            RespuestaDelModelo.Texto(
                respuesta.content()
                    .mapNotNull { bloque -> bloque.text().orElse(null)?.text() }
                    .joinToString("\n")
                    .ifBlank { "(sin respuesta)" },
            )
        } else {
            RespuestaDelModelo.PideHerramientas(pedidos.map { it.comoLlamada() })
        }
    }

    /** Lo que se le manda a la API en esta vuelta. Aparte para poder mirarlo en una prueba. */
    internal fun armarLlamada(
        puedeUsarHerramientas: Boolean,
        conEsteModelo: String = modelo,
    ): MessageCreateParams =
        MessageCreateParams.builder()
            .model(conEsteModelo)
            .maxTokens(maxTokens)
            .apply { if (piensa) thinking(ThinkingConfigAdaptive.builder().build()) }
            .systemOfTextBlockParams(
                listOf(
                    // **Las dos partes se cachean, y en este orden.** La PERSONA no cambia nunca y
                    // el contexto cambia cuando cambian los datos: lo estable primero, para que un
                    // movimiento nuevo no invalide también las instrucciones. Sin esto, una
                    // conversación de tres vueltas paga el prefijo entero tres veces.
                    TextBlockParam.builder()
                        .text(persona)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                    TextBlockParam.builder()
                        .text(contexto)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                ),
            )
            .messages(turnos)
            // Las herramientas van SIEMPRE, incluso cuando ya no puede usarlas: son parte del
            // prefijo cacheado, y quitarlas tiraría la caché de la última llamada entera. Lo que
            // cambia es el permiso, que no toca el prefijo.
            .apply { LAS_HERRAMIENTAS.forEach { addTool(it) } }
            .apply { if (!puedeUsarHerramientas) toolChoice(ToolChoiceNone.builder().build()) }
            .build()

    /** Los turnos acumulados, para que una prueba pueda mirar cómo quedó la conversación. */
    internal val conversacion: List<MessageParam> get() = turnos

    override fun anotarResultados(resultados: List<Pair<String, String>>) {
        val respuesta = ultima ?: return
        turnos += MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT)
            .contentOfBlockParams(respuesta.content().map { it.toParam() })
            .build()
        turnos += MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(
                resultados.map { (id, texto) ->
                    ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder().toolUseId(id).content(texto).build(),
                    )
                },
            )
            .build()
    }
}

/**
 * Los argumentos que mandó el modelo, aplanados a texto. **Todo a `String` a propósito**: lo que
 * llega es JSON libre escrito por un modelo —un límite puede venir `25` o `"25"`— y cada
 * herramienta ya sabe leer lo suyo (ver `HerramientasDelAsistente`). Un `Map<String, Any?>` acá
 * solo movería el `toString` diez líneas más abajo.
 */
private fun ToolUseBlock.comoLlamada(): LlamadaDeHerramienta {
    val crudos = runCatching {
        _input().convert(object : TypeReference<Map<String, Any?>>() {}) ?: emptyMap()
    }.getOrDefault(emptyMap())
    return LlamadaDeHerramienta(
        id = id(),
        nombre = name(),
        argumentos = crudos.mapNotNull { (clave, valor) -> valor?.let { clave to it.toString() } }.toMap(),
    )
}

/**
 * **Cuánto puede escribir de respuesta.** La PERSONA ya pide cuatro o cinco frases; esto es el
 * techo, y estaba en 1024 sin ninguna razón. Una respuesta de finanzas personales que necesita más
 * de esto es una respuesta que el dueño no va a leer.
 */
internal const val MAX_TOKENS_DE_RESPUESTA = 700L

private fun texto(descripcion: String) =
    JsonValue.from(mapOf("type" to "string", "description" to descripcion))

/**
 * **Las preguntas que el asistente puede hacerle a la base.** Las descripciones son para el modelo,
 * no para el dueño, y dicen cosas que no se deducen del nombre: que las fechas son del calendario
 * (no del período del dueño, que ya viene resuelto en el contexto) y que la búsqueda tiene tope
 * mientras que los totales suman todo.
 *
 * **Esta lista es parte del prefijo que se cachea**, así que va igual en todas las vueltas de una
 * conversación — incluso en la última, donde el modelo ya no puede usarlas: quitarlas cambiaría el
 * prefijo y tiraría la caché de esa llamada entera. Lo que se hace en la última vuelta es
 * prohibirle elegirlas (`tool_choice: none`), que no toca el prefijo.
 */
internal val LAS_HERRAMIENTAS: List<Tool> = listOf(
    Tool.builder()
        .name(BUSCAR_MOVIMIENTOS)
        .description(
            "Busca movimientos concretos del usuario: qué compró, dónde y cuándo. Úsala cuando la " +
                "pregunta sea por hechos («¿qué compré en Zelo Group?», «¿qué hubo la semana " +
                "pasada?»). Devuelve como máximo $TOPE_DE_RESULTADOS movimientos y avisa si hubo " +
                "más; para una cifra total usa $TOTALES_POR_CATEGORIA, que suma todos. Sin fechas " +
                "mira los últimos $MESES_HACIA_ATRAS_POR_DEFECTO meses.",
        )
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder()
                        .putAdditionalProperty("desde", texto("Fecha de calendario AAAA-MM-DD, inclusive."))
                        .putAdditionalProperty("hasta", texto("Fecha de calendario AAAA-MM-DD, inclusive."))
                        .putAdditionalProperty("categoria", texto("Nombre exacto de una categoría del usuario, por ejemplo «Comida»."))
                        .putAdditionalProperty("texto", texto("Parte del nombre del movimiento; no distingue mayúsculas ni tildes."))
                        .putAdditionalProperty("tipo", texto("«gasto» o «ingreso». Sin esto vienen los dos."))
                        .putAdditionalProperty("limite", texto("Cuántos devolver, de 1 a $TOPE_DE_RESULTADOS."))
                        .build(),
                )
                // Ninguno obligatorio: una pregunta suelta («¿qué he comprado en Rappi?») no trae
                // fechas, y exigirlas obligaría al modelo a inventarlas.
                .required(emptyList())
                .build(),
        )
        .build(),
    Tool.builder()
        .name(TOTALES_POR_CATEGORIA)
        .description(
            "Cuánto entró y cuánto salió entre dos fechas, y el gasto sumado por categoría. Úsala " +
                "para cualquier pregunta de cuánto («¿cuánto gasté en Comida en agosto?», «¿gasté " +
                "más que el mes pasado?»). Suma TODOS los movimientos del rango, sin tope. Las " +
                "monedas nunca se suman entre sí: cada una viene aparte.",
        )
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder()
                        .putAdditionalProperty("desde", texto("Fecha de calendario AAAA-MM-DD, inclusive."))
                        .putAdditionalProperty("hasta", texto("Fecha de calendario AAAA-MM-DD, inclusive."))
                        .build(),
                )
                .required(listOf("desde", "hasta"))
                .build(),
        )
        .build(),
    Tool.builder()
        .name(BUSCAR_DOCUMENTOS)
        .description(
            "Lee los documentos que el usuario guardó en Movi —extractos, pólizas, recibos— con " +
                "las notas que él mismo escribió al subirlos. Úsala cuando la pregunta pueda " +
                "contestarse con un papel («¿qué seguro paga la cuenta 2334?», «¿tengo el extracto " +
                "de agosto?»). Del archivo solo hay nombre, tipo, período y esas notas: nunca el " +
                "texto de adentro del PDF, así que no describas lo que dice un documento que no " +
                "esté en su nota. Cuando una cifra salga de aquí, di de qué documento sale.",
        )
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder()
                        .putAdditionalProperty(
                            "texto",
                            texto("Parte del nombre del documento o de sus notas. Sin esto vienen todos."),
                        )
                        .build(),
                )
                .required(emptyList())
                .build(),
        )
        .build(),
)
