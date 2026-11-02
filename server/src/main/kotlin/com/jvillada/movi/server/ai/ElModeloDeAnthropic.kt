package com.jvillada.movi.server.ai

import com.jvillada.movi.server.observabilidad.Servicio
import com.jvillada.movi.server.observabilidad.TipoDeError
import com.jvillada.movi.server.observabilidad.crearMensaje
import com.jvillada.movi.server.observabilidad.medirLlamada
import com.jvillada.movi.server.observabilidad.tipoDeErrorDe
import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolChoiceNone
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

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
     * **El camino del criterio** (ver `laPreguntaPideCriterio`): piensa con esfuerzo «medio» y el
     * techo reserva [PRESUPUESTO_DE_PENSAMIENTO] además del texto. Apagado es el camino de datos,
     * que desde Haiku 5.5 **también piensa** —en los modelos 5.x el pensamiento adaptativo viene
     * prendido—, pero con esfuerzo «bajo» y la reserva chica de [PRESUPUESTO_DE_PENSAMIENTO_DEL_DATO].
     *
     * Ojo con lo que esto arrastra: el techo de la llamada nunca es solo [maxTokens]. La razón está
     * en el KDoc de [PRESUPUESTO_DE_PENSAMIENTO], y es exactamente el error que tuvo rota esta rama
     * entera durante días.
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
    /**
     * «Lo que Movi sabe de ti» (Ola 3): lo que el dueño confirmó que se recuerde, ya armado como
     * bloque (ver `memoriaParaElContexto`). `null` = no hay nada, y no viaja ningún bloque.
     */
    private val memoria: String? = null,
) : ElModeloQueSeCorrige {

    constructor(
        client: AnthropicClient,
        modelo: String,
        persona: String,
        contexto: String,
        mensajesDelDueno: List<MessageParam>,
        maxTokens: Long = MAX_TOKENS_DE_RESPUESTA,
        piensa: Boolean = false,
        modeloDeRespaldo: String? = null,
        memoria: String? = null,
    ) : this(modelo, persona, contexto, mensajesDelDueno, maxTokens, piensa, modeloDeRespaldo, { params ->
        withContext(Dispatchers.IO) { crearMensaje(client, params) }
    }, memoria)

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
    /**
     * Vuelta 106 · las que **se escribieron** en la caché (`cache_creation_input_tokens`). Se cobran
     * a 1,25× la entrada y antes no se guardaban en ningún lado: sin ellas el costo de un turno salía
     * corto justo en la primera pregunta de cada conversación, la que escribe la caché.
     */
    var fichasEscritasEnCache: Long = 0L
        private set

    /**
     * El modelo que de verdad contestó la última llamada: [modelo], o el de respaldo si el primero
     * falló. Es lo que se anota en `ai_turns.modelo`, para que el costo del turno se calcule con el
     * precio del que se usó y no del que se pidió.
     */
    var modeloQueContesto: String = modelo
        private set

    override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo {
        var respuesta = pedirle { conModelo -> armarLlamada(puedeUsarHerramientas, modeloDeEstaLlamada = conModelo) }

        // **El reintento sin pensar.** Si el modelo se quedó sin techo antes de escribir una sola
        // palabra, lo único que hay para devolverle al dueño es una burbuja vacía. Una respuesta
        // más simple —sin pensamiento, con los mismos ~700 de texto— es peor que la que iba a
        // salir, pero es infinitamente mejor que ninguna.
        //
        // No duplica el costo del caso normal: para entrar acá tienen que darse las dos cosas a
        // la vez —que la API haya cortado por `max_tokens` y que no haya venido ni un bloque de
        // texto—, y cuando se dan, lo que se «duplica» es una llamada que ya se pagó entera y no
        // sirvió para nada.
        //
        // Vuelta 106: vale para los dos caminos. Con Haiku 5.5 el de datos también piensa (poco,
        // pero piensa), así que también puede quedarse sin techo. Donde el modelo no deja apagar
        // el pensamiento (Sonnet 5.5), el reintento piensa con esfuerzo «bajo».
        var penso = true
        if (seCortoSinTexto(respuesta)) {
            avisarQueNoHuboTexto(respuesta, penso, "reintento sin pensar")
            penso = false
            respuesta = pedirle { conModelo -> armarLlamada(puedeUsarHerramientas, pensando = false, modeloDeEstaLlamada = conModelo) }
        }

        // **Una negativa no es una respuesta vacía.** Los modelos 5.x pueden declinar con
        // `stop_reason: refusal` (HTTP 200), y Haiku 5.5 no tiene respaldo del lado del servidor. Sin
        // esto la burbuja diría «no alcancé a terminar», que invita a repetir lo mismo.
        if (respuesta.stopReason().orElse(null) == StopReason.REFUSAL && respuesta.textoJunto().isBlank()) {
            log.warn("movi-ai: el modelo declinó la pregunta — modelo={}", respuesta.model())
            return RespuestaDelModelo.Texto(NO_PUEDO_CON_ESA)
        }

        val pedidos = respuesta.content().mapNotNull { it.toolUse().orElse(null) }
        return if (pedidos.isEmpty()) {
            val texto = respuesta.textoJunto()
            if (texto.isBlank()) avisarQueNoHuboTexto(respuesta, penso, "se le contesta al dueño")
            RespuestaDelModelo.Texto(texto.ifBlank { NO_ALCANCE_A_TERMINAR })
        } else {
            RespuestaDelModelo.PideHerramientas(pedidos.map { it.comoLlamada() }, texto = respuesta.textoJunto())
        }
    }

    /**
     * Una llamada, con su reintento al modelo de respaldo y las fichas ya contadas.
     *
     * Recibe **cómo armar** la llamada y no la llamada armada, porque el respaldo no es solo otro
     * id: es otro modelo con otras reglas (qué acepta para apagar el pensamiento, por ejemplo), así
     * que copiar los params cambiando el modelo convertía el reintento en un segundo error seguro.
     */
    private suspend fun pedirle(armar: (String) -> MessageCreateParams): Message {
        var usado = modelo
        val respuesta = try {
            llamar(armar(modelo))
        } catch (falla: Exception) {
            val respaldo = modeloDeRespaldo ?: throw falla
            // **Solo el servidor o la capacidad** (vuelta 106): un 5xx, un 429/529, un timeout o la
            // red. Un 400 de parámetros es un error nuestro y tiene que fallar visible; sin saldo o con
            // la clave rechazada el respaldo falla igual (es la misma cuenta) y solo duplica la espera.
            val tipo = tipoDeErrorDe(falla)
            if (tipo !in ERRORES_QUE_PRUEBAN_EL_RESPALDO) throw falla
            val motivo = tipo.name.lowercase()
            log.warn("movi-ai: {} falló ({}: {}), pruebo con el respaldo {}", modelo, motivo, falla.javaClass.simpleName, respaldo)
            usado = respaldo
            medirLlamada(Servicio.RESPALDO_DE_LA_IA, "$respaldo · $motivo") { llamar(armar(respaldo)) }
        }
        ultima = respuesta
        modeloQueContesto = usado
        respuesta.usage().let { uso ->
            fichasDeEntrada += uso.inputTokens()
            fichasDeSalida += uso.outputTokens()
            fichasLeidasDeCache += uso.cacheReadInputTokens().orElse(0L)
            fichasEscritasEnCache += uso.cacheCreationInputTokens().orElse(0L)
        }
        return respuesta
    }

    /** ¿La API cortó por techo sin dejar ni una palabra escrita? */
    private fun seCortoSinTexto(respuesta: Message): Boolean =
        respuesta.stopReason().orElse(null) == StopReason.MAX_TOKENS &&
            respuesta.content().none { it.toolUse().isPresent } &&
            respuesta.textoJunto().isBlank()

    /**
     * **Lo que el bug de septiembre no dejó ver.** Una respuesta sin texto salía como el literal
     * «(sin respuesta)» en la pantalla y no dejaba una sola línea en el log: del lado del server
     * parecía una conversación normal, y del lado del dueño una burbuja vacía. El `stop_reason` es
     * el dato que lo explica en una línea (`max_tokens` era el techo comiéndose el pensamiento;
     * `refusal` sería otra cosa completamente), así que va con el modelo y las fichas al lado.
     */
    private fun avisarQueNoHuboTexto(respuesta: Message, penso: Boolean, queSeHizo: String) {
        log.warn(
            "movi-ai: respuesta sin texto — modelo={} piensa={} stop_reason={} salida={} techo={} → {}",
            respuesta.model(),
            penso,
            respuesta.stopReason().map { it.asString() }.orElse("desconocido"),
            respuesta.usage().outputTokens(),
            techoPara(penso, respuesta.model().asString()),
            queSeHizo,
        )
    }

    /**
     * **La invariante que se rompió: el techo tiene que alcanzar para las dos cosas.**
     *
     * `max_tokens` acota el pensamiento **más** el texto, no solo el texto. Con los 700 de siempre
     * y el pensar encendido, el pensamiento se comía el presupuesto entero, la API cortaba con
     * `stop_reason: max_tokens` y volvían cero bloques de texto: el camino de los consejos estuvo
     * roto al 100 % desde que se encendió el pensar.
     *
     * Y no hay una tercera perilla: en los modelos 5.x el pensamiento es adaptativo y
     * `budget_tokens` fue removido (mandarlo da un 400), así que el presupuesto de pensamiento no
     * se declara — **se reserva**, sumándolo al techo. De ahí que esto sea una suma y no dos
     * parámetros. Vuelta 106: el camino de datos también reserva (menos), porque en Haiku 5.5
     * también piensa; solo el reintento con el pensamiento apagado se queda con [maxTokens] pelado.
     */
    private fun techoPara(pensando: Boolean, modeloDeEstaLlamada: String = modelo): Long = when {
        pensando && piensa -> maxTokens + PRESUPUESTO_DE_PENSAMIENTO
        pensando || !sePuedeApagarElPensamiento(modeloDeEstaLlamada) -> maxTokens + PRESUPUESTO_DE_PENSAMIENTO_DEL_DATO
        else -> maxTokens
    }

    /** Lo que el modelo escribió para el dueño, sin los bloques de pensamiento ni de herramienta. */
    private fun Message.textoJunto(): String =
        content().mapNotNull { bloque -> bloque.text().orElse(null)?.text() }.joinToString("\n")

    /** Lo que se le manda a la API en esta vuelta. Aparte para poder mirarlo en una prueba. */
    internal fun armarLlamada(
        puedeUsarHerramientas: Boolean,
        /** `false` solo en el reintento por techo (ver [siguienteVuelta]): piensa lo menos posible. */
        pensando: Boolean = true,
        modeloDeEstaLlamada: String = modelo,
    ): MessageCreateParams =
        MessageCreateParams.builder()
            .model(modeloDeEstaLlamada)
            .maxTokens(techoPara(pensando, modeloDeEstaLlamada))
            // **Sin `temperature`, en ningún modelo** (vuelta 106). Era la perilla de determinismo del
            // camino de datos en Haiku 4.5, pero Haiku 5.5 da 400 con cualquier valor que no sea el
            // de fábrica, y desde la versión 2026-10-21 de la API también todos los demás. La
            // determinación de las cifras nunca vino de ahí: viene de los datos exactos de la
            // pregunta y del verificador de cifras (ver `responderSinInventar`).
            .apply {
                if (pensando || !sePuedeApagarElPensamiento(modeloDeEstaLlamada)) {
                    // Explícito aunque en los 5.x sea el default: el respaldo (Opus 4.7) sin esto
                    // NO piensa, y el mismo pedido tiene que significar lo mismo en los dos.
                    thinking(ThinkingConfigAdaptive.builder().build())
                } else {
                    // Solo en el reintento, y solo donde se puede: Haiku 5.5 lo acepta con
                    // esfuerzo «alto» o menos; Sonnet 5.5 y Opus 5.5 dan 400 (ahí el reintento
                    // sigue adaptativo, con esfuerzo «bajo»).
                    thinking(ThinkingConfigDisabled.builder().build())
                }
                // **El esfuerzo es la perilla de costo**, y la única que queda ahora que
                // `budget_tokens` no existe. En «medio» el modelo piensa lo suficiente para un
                // consejo de finanzas del hogar; en «bajo» —el de datos y el reintento— casi no
                // piensa, que es lo que la guía de Haiku 5.5 recomienda para el chat. Va explícito:
                // en Haiku 5.5 el default es «medio», y el camino de datos no lo necesita.
                val esfuerzo = if (pensando && piensa) OutputConfig.Effort.MEDIUM else OutputConfig.Effort.LOW
                outputConfig(OutputConfig.builder().effort(esfuerzo).build())
            }
            .systemOfTextBlockParams(
                listOfNotNull(
                    // **Las partes se cachean, y en este orden.** La PERSONA no cambia nunca y
                    // el contexto cambia cuando cambian los datos: lo estable primero, para que un
                    // movimiento nuevo no invalide también las instrucciones. Sin esto, una
                    // conversación de tres vueltas paga el prefijo entero tres veces.
                    TextBlockParam.builder()
                        .text(persona)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                    // La memoria va en el medio: cambia mucho menos que los datos (un recuerdo
                    // cada tanto contra un movimiento cada día), así que un movimiento nuevo no
                    // la invalida, y un recuerdo nuevo no invalida la PERSONA. Tres puntos de
                    // caché de los cuatro que permite la API.
                    memoria?.let {
                        TextBlockParam.builder()
                            .text(it)
                            .cacheControl(CacheControlEphemeral.builder().build())
                            .build()
                    },
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

    /**
     * **El único reintento del verificador de cifras.** La respuesta que se va a corregir vuelve
     * como turno del asistente —con sus bloques originales, pensamiento incluido, igual que en
     * [anotarResultados]— y la corrección va como turno del usuario. Todo va DESPUÉS del prefijo
     * cacheado (PERSONA, contexto, herramientas), así que el reintento lee la caché entera y paga
     * solo la respuesta vieja, la corrección y la nueva.
     */
    override fun anotarCorreccion(correccion: String) {
        val respuesta = ultima ?: return
        turnos += MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT)
            .contentOfBlockParams(respuesta.content().map { it.toParam() })
            .build()
        turnos += MessageParam.builder()
            .role(MessageParam.Role.USER)
            .content(correccion)
            .build()
    }

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
 *
 * Ojo: esto es el techo del **texto**, no el de la llamada. Cuando el modelo piensa, la llamada
 * lleva además [PRESUPUESTO_DE_PENSAMIENTO] — ver ahí por qué.
 */
internal const val MAX_TOKENS_DE_RESPUESTA = 700L

/**
 * **Lo que se le reserva al pensamiento, ADEMÁS del texto.**
 *
 * `max_tokens` no acota la respuesta: acota el pensamiento **más** la respuesta. Esa es la
 * invariante, y romperla no da una respuesta corta —da una respuesta **vacía**: el modelo gasta el
 * techo entero pensando, la API corta con `stop_reason: max_tokens` y no manda ni un bloque de
 * texto. Así estuvo el camino de los consejos desde que se encendió el pensar, con la burbuja en
 * blanco en la pantalla y `fichas_salida = 700` clavado en `ai_turns`.
 *
 * La regla, entonces, es una sola: **`max_tokens` = presupuesto de pensamiento + techo del texto**.
 * No hay forma de declarar el presupuesto por separado —en Sonnet 5 el pensamiento es adaptativo y
 * `budget_tokens` fue removido, mandarlo devuelve un 400—, así que se reserva sumándolo. Lo que sí
 * se puede regular es cuánto piensa, y eso es `output_config.effort` (en «medio», ver `armarLlamada`).
 *
 * Cuatro mil alcanza con holgura para lo que se le pide acá —mirar unos saldos y decidir qué
 * conviene primero— sin volverlo un gasto abierto.
 *
 * **Esto NO encarece el camino de datos.** El techo grande viaja solo cuando `piensa` está
 * prendido, o sea solo en el camino de los consejos; «¿cuánto gasté en Comida?» sigue pagando
 * exactamente lo mismo que antes. Y un techo no es un cobro: se paga lo que el modelo escribe,
 * no lo que se le autorizó a escribir.
 */
internal const val PRESUPUESTO_DE_PENSAMIENTO = 4_000L

/**
 * **Lo que se le reserva al pensamiento en el camino de datos** (vuelta 106). Haiku 5.5 piensa
 * aunque no se lo pidan; con esfuerzo «bajo» piensa poco o nada en «¿cuánto gasté en Comida?», pero
 * un techo de solo [MAX_TOKENS_DE_RESPUESTA] repetiría el bug de septiembre en el camino barato. Mil
 * quinientas alcanzan para mirar una herramienta y decidir; un techo no es un cobro.
 */
internal const val PRESUPUESTO_DE_PENSAMIENTO_DEL_DATO = 1_500L

/**
 * Vuelta 106 · los errores que justifican probar con el modelo de respaldo: los del servidor de
 * Anthropic (5xx), los de capacidad (429/529) y los de la red (timeout, conexión). Ver [TipoDeError].
 */
internal val ERRORES_QUE_PRUEBAN_EL_RESPALDO = setOf(TipoDeError.SERVIDOR, TipoDeError.LIMITE, TipoDeError.RED)

/**
 * ¿Este modelo deja apagar el pensamiento con `thinking: {type: "disabled"}`? Haiku 5.5 sí (con
 * esfuerzo «alto» o menos), Opus 4.7 y los 4.x también. **Sonnet 5.5 y Opus 5.5 no**: dan 400, y en
 * esos el reintento sigue pensando con esfuerzo «bajo». Ver la guía de migración de cada uno.
 */
internal fun sePuedeApagarElPensamiento(modelo: String): Boolean =
    !modelo.startsWith("claude-sonnet-5-5") && !modelo.startsWith("claude-opus-5-5")

/**
 * Lo que ve el dueño si el modelo declinó la pregunta (`stop_reason: refusal`). Pasa poco con
 * preguntas de plata, pero Haiku 5.5 no tiene respaldo del lado del servidor y una burbuja que dice
 * «no alcancé a terminar» invita a repetir lo mismo.
 */
internal const val NO_PUEDO_CON_ESA =
    "No puedo ayudarte con esa pregunta. Pregúntame algo sobre tu plata y te contesto con tus números."

/**
 * **Lo que ve el dueño cuando la respuesta vino sin texto.** El literal viejo —«(sin respuesta)»—
 * es lo que tuvo el bug vivo sin que nadie lo notara: no dice qué pasó ni qué hacer, y parece un
 * error de la pantalla más que del asistente. Este dice las dos cosas, y es cierto: no alcanzó a
 * terminar.
 */
internal const val NO_ALCANCE_A_TERMINAR =
    "No alcancé a terminar la respuesta. Vuelve a preguntarme, por favor."

private val log = LoggerFactory.getLogger("ElModeloDeAnthropic")

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
internal val LAS_HERRAMIENTAS: List<Tool> by lazy { HERRAMIENTAS_DE_LECTURA + HERRAMIENTAS_QUE_PROPONEN }

/**
 * Las de lectura: las tres de siempre y las que miran adelante (Ola 4). Las que proponen (Ola 3)
 * están en `LoQueMoviPropone.kt`.
 */
private val HERRAMIENTAS_DE_LECTURA: List<Tool> = listOf(
    Tool.builder()
        .name(SIMULAR_ABONO)
        .description(
            "Calcula el plan de salida de sus deudas: en qué orden conviene abonar (avalancha: mayor tasa " +
                "primero; bola de nieve: menor saldo primero), y con un abono extra al mes cuándo sale de cada " +
                "una y cuánto interés se ahorra, con amortización mes a mes. Úsala SIEMPRE para «¿qué deuda me " +
                "conviene abonar primero?», «¿y si abono X al mes?», «¿cuándo salgo de mis deudas?». Separa las " +
                "deudas a las que les falta la tasa o el mínimo y las que paga la nómina o un tercero.",
        )
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder()
                        .putAdditionalProperty("abono_mensual", texto("Pesos extra al mes para abonar, solo dígitos («500000»). Sin esto, 0: solo el orden."))
                        .putAdditionalProperty("estrategia", texto("«avalancha» (por defecto) o «bola_de_nieve»."))
                        .build(),
                )
                .required(emptyList())
                .build(),
        )
        .build(),
    Tool.builder()
        .name(PROYECTAR_CAJA)
        .description(
            "Proyecta cuánta plata libre va a tener el usuario cada día hasta que le paguen (el día del " +
                "próximo salario esperado, ese día incluido; sin salario, el día que arranca su período " +
                "siguiente, o 30 días si nunca eligió su período) y cuál es el día más " +
                "justo. Parte de lo que hay en sus cuentas, resta los pagos pendientes de su período y los " +
                "del siguiente que vencen antes (una tarjeta, su pago mínimo), suma los ingresos que faltan " +
                "y resta un gasto del día a día estimado de sus períodos cerrados. Lo libre es el saldo menos " +
                "su ahorro del período y lo apartado. Úsala para «¿me alcanza hasta que me paguen?», «¿cuánto " +
                "voy a tener el 17?», «¿qué día ando más corto?». Trae los supuestos y lo que no entró: dilos.",
        )
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(Tool.InputSchema.Properties.builder().build())
                .required(emptyList())
                .build(),
        )
        .build(),
    Tool.builder()
        .name(CONSULTAR_PERSONA_O_COMERCIO)
        .description(
            "Lo que el usuario le envió y lo que recibió de una persona o comercio que guardó en «Personas y " +
                "comercios» (su esposa, su papá, la cancha, el parqueadero), con el total por período y sus " +
                "movimientos. Sin nombre, la lista de todos los guardados con lo de cada uno. Úsala SIEMPRE para " +
                "«¿cuánto le he mandado a X?», «¿cuánto me envió X?», «¿a quién le mando más plata?»: reconoce los " +
                "movimientos por el número de cuenta o la llave que nombró el banco aunque les hayan cambiado el " +
                "nombre, cosa que $BUSCAR_MOVIMIENTOS por texto no hace. Sin fechas mira los últimos " +
                "$MESES_HACIA_ATRAS_POR_DEFECTO meses. Lo enviado y lo recibido nunca se suman.",
        )
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder()
                        .putAdditionalProperty("nombre", texto("Cómo la guardó el usuario («Caro») o parte del nombre. Sin esto, la lista de todos."))
                        .putAdditionalProperty("desde", texto("Fecha de calendario AAAA-MM-DD, inclusive."))
                        .putAdditionalProperty("hasta", texto("Fecha de calendario AAAA-MM-DD, inclusive."))
                        .build(),
                )
                .required(emptyList())
                .build(),
        )
        .build(),
    Tool.builder()
        .name(BUSCAR_MOVIMIENTOS)
        .description(
            "Busca movimientos concretos del usuario: qué compró, dónde y cuándo. Úsala cuando la " +
                "pregunta sea por hechos («¿qué compré en Zelo Group?», «¿qué hubo la semana " +
                "pasada?»). Devuelve como máximo $TOPE_DE_RESULTADOS movimientos y avisa si hubo " +
                "más; para una cifra total usa $TOTALES_POR_CATEGORIA, que suma todos. Sin fechas " +
                "mira los últimos $MESES_HACIA_ATRAS_POR_DEFECTO meses. Trae también los pagos de " +
                "tarjeta y los traspasos entre sus cuentas, rotulados porque NO cuentan como gasto: " +
                "úsala para «¿cuánto le pagué a la Master Black?» (filtra por cuenta o por texto).",
        )
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder()
                        .putAdditionalProperty("desde", texto("Fecha de calendario AAAA-MM-DD, inclusive."))
                        .putAdditionalProperty("hasta", texto("Fecha de calendario AAAA-MM-DD, inclusive."))
                        .putAdditionalProperty("categoria", texto("Nombre exacto de una categoría del usuario, por ejemplo «Comida»."))
                        .putAdditionalProperty(
                            "cuenta",
                            texto("Parte del nombre de una cuenta suya, «Bancolombia» o «Nu». Para «¿qué gasté desde tal cuenta?»."),
                        )
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
            "Cuánto entró y cuánto salió entre dos fechas, el gasto sumado por categoría y DE QUÉ " +
                "CUENTA salió cada peso. Úsala para cualquier pregunta de cuánto («¿cuánto gasté " +
                "en Comida en agosto?», «¿gasté más que el mes pasado?», «¿qué gasté desde " +
                "Bancolombia este período?»). Suma TODOS los movimientos del rango, sin tope. Las " +
                "monedas nunca se suman entre sí: cada una viene aparte. Al final trae, en un " +
                "bloque aparte que NO suma en Entró ni en Salió, los pagos de tarjeta y los traspasos.",
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
                            texto(
                                "Parte del nombre del documento o de sus notas. Pásalo casi siempre: " +
                                    "sin filtro solo vienen los primeros y te dice cuántos quedaron fuera.",
                            ),
                        )
                        .build(),
                )
                .required(emptyList())
                .build(),
        )
        .build(),
)
