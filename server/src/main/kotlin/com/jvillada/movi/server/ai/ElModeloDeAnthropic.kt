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
    private val maxTokens: Long = 1024L,
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
        maxTokens: Long = 1024L,
    ) : this(modelo, persona, contexto, mensajesDelDueno, maxTokens, { params ->
        withContext(Dispatchers.IO) { client.messages().create(params) }
    })

    private val turnos: MutableList<MessageParam> = mensajesDelDueno.toMutableList()

    /**
     * La última respuesta, con sus bloques **originales**. Hace falta tal cual: un turno del
     * asistente que pidió herramientas se le devuelve al modelo completo —pensamiento incluido— o
     * la API rechaza la conversación.
     */
    private var ultima: Message? = null

    override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo {
        val respuesta = llamar(armarLlamada(puedeUsarHerramientas))
        ultima = respuesta

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
    internal fun armarLlamada(puedeUsarHerramientas: Boolean): MessageCreateParams =
        MessageCreateParams.builder()
            .model(modelo)
            .maxTokens(maxTokens)
            .thinking(ThinkingConfigAdaptive.builder().build())
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder().text(persona).build(),
                    TextBlockParam.builder()
                        .text(contexto)
                        // El contexto es lo más largo y no cambia entre vueltas: sin caché, una
                        // conversación de tres consultas lo paga tres veces.
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                ),
            )
            .messages(turnos)
            .apply { if (puedeUsarHerramientas) LAS_HERRAMIENTAS.forEach { addTool(it) } }
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

private fun texto(descripcion: String) =
    JsonValue.from(mapOf("type" to "string", "description" to descripcion))

/**
 * **Las dos preguntas que el asistente puede hacerle a la base.** Las descripciones son para el
 * modelo, no para el dueño, y dicen dos cosas que no se deducen del nombre: que las fechas son del
 * calendario (no del período del dueño, que ya viene resuelto en el contexto) y que la búsqueda
 * tiene tope mientras que los totales suman todo.
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
)
