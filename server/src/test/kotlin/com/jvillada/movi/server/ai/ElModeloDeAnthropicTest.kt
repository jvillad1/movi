package com.jvillada.movi.server.ai

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.DirectCaller
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlock
import com.anthropic.models.messages.ToolUseBlock
import com.anthropic.models.messages.Usage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # La forma de la conversación, sin gastar una llamada
 *
 * Lo que se le manda a la API cuando hay herramientas de por medio tiene una forma exacta: el turno
 * del asistente vuelve **completo** (con su bloque de uso de herramienta) y cada resultado va atado
 * al **id de su propia llamada**. Equivocarse ahí no da un número mal: da una conversación que la
 * API rechaza, o peor, un resultado contestado a la pregunta equivocada.
 *
 * Por eso el `POST` está izado a un parámetro y acá se le da uno de mentira: se prueba todo menos
 * la red. Lo que esta prueba NO cubre —y no puede— es que la API acepte estos params; eso se ve la
 * primera vez que se le pregunta algo de verdad.
 */
class ElModeloDeAnthropicTest {

    private fun respuestaConTexto(texto: String): Message =
        base().addContent(TextBlock.builder().text(texto).citations(listOf()).build()).build()

    private fun respuestaQuePide(id: String, herramienta: String, argumentos: Map<String, Any>): Message =
        base().addContent(
            ToolUseBlock.builder()
                .id(id)
                .name(herramienta)
                .input(JsonValue.from(argumentos))
                // `caller` es obligatorio al construir: «directo» es lo que manda la API cuando
                // la herramienta la ejecuta quien llama, que es exactamente nuestro caso.
                .caller(ToolUseBlock.Caller.ofDirect(DirectCaller.builder().build()))
                .build(),
        ).build()

    /**
     * Una respuesta de la API, a mano. Todos los campos de `Usage` van puestos porque el SDK los
     * exige al construir; ninguno de ellos significa nada para esta prueba.
     */
    private fun base(): Message.Builder = Message.builder()
        .id("msg_1")
        .model(Model.of(MODELO_DE_PRUEBA))
        .stopReason(null)
        .stopSequence(null)
        .usage(
            Usage.builder()
                .cacheCreation(null)
                .cacheCreationInputTokens(null as Long?)
                .cacheReadInputTokens(null as Long?)
                .inferenceGeo(null as String?)
                .inputTokens(10)
                .outputTokens(5)
                .serverToolUse(null)
                .serviceTier(null)
                .build(),
        )

    private fun modelo(vararg guion: Message): Pair<ElModeloDeAnthropic, MutableList<Boolean>> {
        val conHerramientas = mutableListOf<Boolean>()
        var i = 0
        val elModelo = ElModeloDeAnthropic(
            modelo = MODELO_DE_PRUEBA,
            persona = "Eres Movi AI",
            contexto = "DATOS DEL USUARIO",
            mensajesDelDueno = listOf(
                MessageParam.builder().role(MessageParam.Role.USER).content("cuanto gaste en agosto").build(),
            ),
            llamar = { params ->
                conHerramientas += params.tools().orElse(emptyList()).isNotEmpty()
                guion[i++.coerceAtMost(guion.lastIndex)]
            },
        )
        return elModelo to conHerramientas
    }

    /**
     * **Las herramientas van SIEMPRE, y lo que cambia es el permiso.** Son parte del prefijo que se
     * cachea: quitarlas en la última vuelta —como se hacía al principio— cambiaba el prefijo y
     * tiraba la caché de esa llamada entera, que es justo la más larga de la conversación.
     */
    @Test
    fun `las herramientas viajan en todas las vueltas, y en la ultima se le prohibe usarlas`() = runBlocking {
        val (elModelo, _) = modelo(respuestaConTexto("Listo."))

        val conPermiso = elModelo.armarLlamada(puedeUsarHerramientas = true)
        val sinPermiso = elModelo.armarLlamada(puedeUsarHerramientas = false)

        assertEquals(LAS_HERRAMIENTAS.size, conPermiso.tools().orElse(emptyList()).size)
        assertEquals(
            LAS_HERRAMIENTAS.size,
            sinPermiso.tools().orElse(emptyList()).size,
            "el prefijo tiene que ser el mismo o la caché no pega",
        )
        assertTrue(conPermiso.toolChoice().isEmpty, "con permiso, que elija él")
        assertTrue(sinPermiso.toolChoice().orElseThrow().isNone(), "sin permiso, tool_choice: none")
    }

    /** Pensar se cobra como salida: solo se enciende en el camino caro, el de los consejos. */
    @Test
    fun `no piensa salvo que se lo pidan`() {
        val (barato, _) = modelo(respuestaConTexto("Listo."))
        assertTrue(barato.armarLlamada(puedeUsarHerramientas = true).thinking().isEmpty)

        val caro = queOpina(respuestaConTexto("Listo."))
        assertTrue(caro.armarLlamada(puedeUsarHerramientas = true).thinking().isPresent)
    }

    /**
     * **La guarda del bug.** `max_tokens` acota el pensamiento MÁS el texto. Con los 700 de
     * siempre y el pensar encendido, el pensamiento se comía el presupuesto entero: la API cortaba
     * por techo y volvían cero bloques de texto. El camino de los consejos estuvo roto al 100 %,
     * con la burbuja en blanco en la pantalla y `fichas_salida = 700` clavado en `ai_turns`.
     *
     * Si alguien vuelve a dejar el techo sin lugar para el texto, esta prueba se cae acá y no en
     * producción.
     */
    @Test
    fun `cuando piensa, el techo deja lugar para el texto ademas del pensamiento`() {
        val caro = queOpina(respuestaConTexto("Listo."))

        val techo = caro.armarLlamada(puedeUsarHerramientas = true).maxTokens()

        assertEquals(PRESUPUESTO_DE_PENSAMIENTO + MAX_TOKENS_DE_RESPUESTA, techo)
        assertTrue(
            techo - PRESUPUESTO_DE_PENSAMIENTO >= MAX_TOKENS_DE_RESPUESTA,
            "el pensamiento no puede quedarse con todo el techo: sin lugar para el texto la " +
                "respuesta vuelve vacía",
        )
    }

    /**
     * El techo grande es del camino de los consejos y de nadie más. El dueño pidió que esto salga
     * barato y «¿cuánto gasté en Comida?» —que es casi todo lo que pregunta— no piensa: tiene que
     * seguir pagando exactamente lo mismo que antes de este arreglo.
     */
    @Test
    fun `el camino de datos no paga nada del techo del pensar`() {
        val (barato, _) = modelo(respuestaConTexto("Listo."))

        val llamada = barato.armarLlamada(puedeUsarHerramientas = true)

        assertEquals(MAX_TOKENS_DE_RESPUESTA, llamada.maxTokens())
        assertTrue(
            llamada.outputConfig().isEmpty,
            "`effort` es la perilla del pensar, y además en Haiku da error",
        )
    }

    /** Las dos partes del sistema se cachean: la PERSONA es lo más estable que hay. */
    @Test
    fun `la persona y los datos viajan cacheados`() {
        val (elModelo, _) = modelo(respuestaConTexto("Listo."))

        val bloques = elModelo.armarLlamada(puedeUsarHerramientas = true).system().orElseThrow()
            .textBlockParams().orElseThrow()

        assertEquals(2, bloques.size)
        assertTrue(bloques.all { it.cacheControl().isPresent }, "las dos partes tienen que cachearse")
    }

    /**
     * Un id de modelo es un texto que viaja a la API. Si uno dejara de estar disponible en esta
     * cuenta, sin respaldo el asistente se caería entero; con respaldo, el peor caso es una
     * respuesta más cara.
     */
    @Test
    fun `si el modelo falla, reintenta una vez con el de respaldo`() = runBlocking {
        val modelosPedidos = mutableListOf<String>()
        val elModelo = ElModeloDeAnthropic(
            modelo = "modelo-que-no-existe",
            persona = "p",
            contexto = "c",
            mensajesDelDueno = listOf(MessageParam.builder().role(MessageParam.Role.USER).content("hola").build()),
            modeloDeRespaldo = MODELO_DE_PRUEBA,
            llamar = { params ->
                modelosPedidos += params.model().toString()
                if (modelosPedidos.size == 1) error("404 model not found")
                respuestaConTexto("Listo.")
            },
        )

        val respuesta = elModelo.siguienteVuelta(puedeUsarHerramientas = true)

        assertEquals("Listo.", (respuesta as RespuestaDelModelo.Texto).texto)
        assertEquals(listOf("modelo-que-no-existe", MODELO_DE_PRUEBA), modelosPedidos)
    }

    @Test
    fun `cuenta las fichas que se gastaron`() = runBlocking {
        val (elModelo, _) = modelo(respuestaConTexto("Listo."))

        elModelo.siguienteVuelta(puedeUsarHerramientas = true)
        elModelo.siguienteVuelta(puedeUsarHerramientas = false)

        assertEquals(20, elModelo.fichasDeEntrada, "diez por llamada, dos llamadas")
        assertEquals(10, elModelo.fichasDeSalida)
    }

    @Test
    fun `lee lo que el modelo pidio, con sus argumentos`() = runBlocking {
        val (elModelo, _) = modelo(
            respuestaQuePide("toolu_abc", TOTALES_POR_CATEGORIA, mapOf("desde" to "2026-08-01", "limite" to 25)),
        )

        val respuesta = elModelo.siguienteVuelta(puedeUsarHerramientas = true)

        val llamada = (respuesta as RespuestaDelModelo.PideHerramientas).llamadas.single()
        assertEquals("toolu_abc", llamada.id)
        assertEquals(TOTALES_POR_CATEGORIA, llamada.nombre)
        assertEquals("2026-08-01", llamada.argumentos["desde"])
        // Un número llega como número y la herramienta lo lee como texto: por eso todo va a String.
        assertEquals("25", llamada.argumentos["limite"])
    }

    /**
     * **El error caro de esta feature, fijado acá.** El turno del asistente vuelve completo y el
     * resultado va atado al id de SU llamada; si se soltara el turno, o se atara el resultado a
     * otro id, la API rechaza la conversación entera.
     */
    @Test
    fun `el turno del asistente vuelve completo y el resultado atado a su llamada`() = runBlocking {
        val (elModelo, _) = modelo(respuestaQuePide("toolu_abc", BUSCAR_MOVIMIENTOS, mapOf("texto" to "Rappi")))

        elModelo.siguienteVuelta(puedeUsarHerramientas = true)
        elModelo.anotarResultados(listOf("toolu_abc" to "Sin movimientos."))

        val conversacion = elModelo.conversacion
        assertEquals(3, conversacion.size, "la pregunta del dueño, el turno del asistente y el resultado")

        val delAsistente = conversacion[1]
        assertEquals(MessageParam.Role.ASSISTANT, delAsistente.role())
        val bloquesDelAsistente = delAsistente.content().blockParams().orElse(emptyList())
        assertTrue(
            bloquesDelAsistente.any { it.toolUse().isPresent },
            "el turno del asistente tiene que volver con su bloque de uso de herramienta",
        )

        val conElResultado = conversacion[2]
        assertEquals(MessageParam.Role.USER, conElResultado.role())
        val resultado = conElResultado.content().blockParams().orElse(emptyList())
            .single().toolResult().orElseThrow()
        assertEquals("toolu_abc", resultado.toolUseId())
    }

    /**
     * **El literal viejo, fijado como lo que NO puede volver.** «(sin respuesta)» es lo que tuvo
     * el bug del pensar vivo sin que nadie lo notara: una burbuja vacía en la pantalla que no dice
     * qué pasó ni qué hacer. Lo que sale ahora dice las dos cosas.
     */
    @Test
    fun `una respuesta sin bloques de texto no produce el literal viejo`() = runBlocking {
        val (elModelo, _) = modelo(base().content(emptyList()).build())

        val respuesta = (elModelo.siguienteVuelta(puedeUsarHerramientas = false) as RespuestaDelModelo.Texto)

        assertEquals(NO_ALCANCE_A_TERMINAR, respuesta.texto)
        assertFalse("(sin respuesta)" in respuesta.texto, "ese literal es el que dejó el bug vivo")
        assertTrue(respuesta.texto.isNotBlank(), "al dueño nunca le llega una burbuja vacía")
    }

    /**
     * **Una respuesta más simple es mejor que ninguna.** Si el pensamiento se comió el techo, la
     * segunda vuelta va sin pensar: sale peor de lo que iba a salir, pero sale.
     */
    @Test
    fun `si se corto por techo sin escribir nada, reintenta una vez sin pensar`() = runBlocking {
        val pensaronLasLlamadas = mutableListOf<Boolean>()
        var i = 0
        val elModelo = ElModeloDeAnthropic(
            modelo = MODELO_DE_PRUEBA,
            persona = "p",
            contexto = "c",
            mensajesDelDueno = listOf(MessageParam.builder().role(MessageParam.Role.USER).content("que me recomiendas").build()),
            piensa = true,
            llamar = { params ->
                pensaronLasLlamadas += params.thinking().orElseThrow().isAdaptive()
                if (i++ == 0) cortadaPorTecho() else respuestaConTexto("Abona al vehículo.")
            },
        )

        val respuesta = elModelo.siguienteVuelta(puedeUsarHerramientas = false)

        assertEquals("Abona al vehículo.", (respuesta as RespuestaDelModelo.Texto).texto)
        assertEquals(
            listOf(true, false),
            pensaronLasLlamadas,
            "primero piensa; si no le alcanzó el techo, la segunda va sin pensar",
        )
    }

    /**
     * El reintento es del caso roto, no del normal: una respuesta que llegó bien se paga una sola
     * vez. El dueño pidió que esto salga barato.
     */
    @Test
    fun `una respuesta normal del camino que piensa se pide una sola vez`() = runBlocking {
        var llamadas = 0
        val elModelo = ElModeloDeAnthropic(
            modelo = MODELO_DE_PRUEBA,
            persona = "p",
            contexto = "c",
            mensajesDelDueno = listOf(MessageParam.builder().role(MessageParam.Role.USER).content("que me recomiendas").build()),
            piensa = true,
            llamar = { llamadas++; respuestaConTexto("Abona al vehículo.") },
        )

        elModelo.siguienteVuelta(puedeUsarHerramientas = false)

        assertEquals(1, llamadas)
    }

    /** El camino que piensa, con el guion que se le quiera dar. */
    private fun queOpina(vararg guion: Message): ElModeloDeAnthropic {
        var i = 0
        return ElModeloDeAnthropic(
            modelo = MODELO_DE_PRUEBA,
            persona = "p",
            contexto = "c",
            mensajesDelDueno = listOf(MessageParam.builder().role(MessageParam.Role.USER).content("hola").build()),
            piensa = true,
            llamar = { guion[i++.coerceAtMost(guion.lastIndex)] },
        )
    }

    /** Lo que contestó la API en producción: cortada por techo, sin un solo bloque de texto. */
    private fun cortadaPorTecho(): Message =
        base().content(emptyList()).stopReason(StopReason.MAX_TOKENS).build()

    private companion object {
        const val MODELO_DE_PRUEBA = "claude-opus-4-7"
    }
}
