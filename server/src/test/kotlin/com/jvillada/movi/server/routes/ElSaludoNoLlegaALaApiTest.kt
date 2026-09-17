package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Movi AI no contestaba NADA, y el server lo dejaba pasar.**
 *
 * La pantalla del chat siembra el historial con un saludo del asistente y mandaba la lista entera,
 * así que `messages[0]` era un mensaje `assistant`. La Messages API exige que el primero sea del
 * usuario: cada turno volvía con «Error llamando a Claude». La única guarda que había miraba el
 * ÚLTIMO mensaje —que sí era del usuario—, así que desde acá se veía todo bien.
 *
 * [mensajesParaElModelo] es la red del server: descarta todo lo que venga antes del primer mensaje
 * del usuario. Tiene que seguir existiendo aunque el cliente ya no mande el saludo, porque el APK
 * que el dueño tiene instalado va a seguir mandándolo por meses.
 */
class ElSaludoNoLlegaALaApiTest {

    private fun saludo() = ChatMessage(ChatRole.ASSISTANT, "¡Hola Camilo! Pregúntame lo que quieras sobre tu plata.")
    private fun usuario(texto: String) = ChatMessage(ChatRole.USER, texto)
    private fun asistente(texto: String) = ChatMessage(ChatRole.ASSISTANT, texto)

    @Test
    fun `un historial que arranca con el asistente sale arrancando con el usuario`() {
        val entrada = listOf(saludo(), usuario("¿cuánto gasté este mes?"))

        val paraLaApi = mensajesParaElModelo(entrada)

        assertEquals(1, paraLaApi.size)
        assertEquals(ChatRole.USER, paraLaApi.first().role, "la API rechaza un historial que empieza en assistant")
        assertEquals("¿cuánto gasté este mes?", paraLaApi.first().content)
    }

    @Test
    fun `una conversación de varios turnos conserva todo desde el primer mensaje del usuario`() {
        val entrada = listOf(
            saludo(),
            usuario("¿cuánto gasté?"),
            asistente("Gastaste \$1.200.000."),
            usuario("¿y en qué?"),
        )

        val paraLaApi = mensajesParaElModelo(entrada)

        assertEquals(3, paraLaApi.size)
        assertEquals(ChatRole.USER, paraLaApi.first().role)
        assertEquals(ChatRole.USER, paraLaApi.last().role)
        assertEquals(listOf("¿cuánto gasté?", "Gastaste \$1.200.000.", "¿y en qué?"), paraLaApi.map { it.content })
    }

    @Test
    fun `un historial que ya arranca con el usuario no se toca`() {
        val entrada = listOf(usuario("hola"), asistente("hola"), usuario("¿cuánto tengo?"))
        assertEquals(entrada, mensajesParaElModelo(entrada))
    }

    /**
     * Sin ningún mensaje del usuario no queda nada que mandar — y eso es lo correcto: cae en la
     * guarda de «Último mensaje debe ser del usuario» que ya existía, en vez de llegarle a la API
     * una conversación que no puede aceptar.
     */
    @Test
    fun `un historial solo de asistente queda vacío`() {
        assertTrue(mensajesParaElModelo(listOf(saludo(), asistente("…"))).isEmpty())
        assertTrue(mensajesParaElModelo(emptyList()).isEmpty())
    }
}
