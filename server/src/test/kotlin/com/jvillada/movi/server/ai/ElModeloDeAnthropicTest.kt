package com.jvillada.movi.server.ai

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.DirectCaller
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.TextBlock
import com.anthropic.models.messages.ToolUseBlock
import com.anthropic.models.messages.Usage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `cuando puede usar herramientas van las dos, y cuando no, ninguna`() = runBlocking {
        val (elModelo, conHerramientas) = modelo(respuestaConTexto("Listo."))

        elModelo.siguienteVuelta(puedeUsarHerramientas = true)
        elModelo.siguienteVuelta(puedeUsarHerramientas = false)

        assertEquals(listOf(true, false), conHerramientas)
        assertEquals(
            LAS_HERRAMIENTAS.size,
            elModelo.armarLlamada(puedeUsarHerramientas = true).tools().orElse(emptyList()).size,
        )
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

    @Test
    fun `una respuesta en blanco no llega vacia al dueno`() = runBlocking {
        val (elModelo, _) = modelo(base().content(emptyList()).build())

        val respuesta = elModelo.siguienteVuelta(puedeUsarHerramientas = false)

        assertEquals("(sin respuesta)", (respuesta as RespuestaDelModelo.Texto).texto)
    }

    private companion object {
        const val MODELO_DE_PRUEBA = "claude-opus-4-7"
    }
}
