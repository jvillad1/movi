package com.jvillada.movi.ui.ai

import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Lo que el chat manda no es lo que el chat muestra.**
 *
 * Dos errores que dejaban a Movi AI mudo y que ninguna prueba de pantalla iba a ver, porque los dos
 * están en la lista que viaja y no en la que se pinta:
 *
 *  - el saludo del asistente iba como primer mensaje, y la API exige que el primero sea del
 *    usuario: fallaban TODOS los turnos, no el primero;
 *  - cada turno re-mandaba el base64 de todas las fotos adjuntadas antes, así que la misma imagen
 *    se volvía a subir —y a cobrar— en cada pregunta, hasta pasar el techo de 32 MB de la API y
 *    romper el chat hasta reabrirlo.
 */
class MensajesQueViajanTest {

    private val saludo = ChatMessage(ChatRole.ASSISTANT, "¡Hola Camilo! Pregúntame lo que quieras sobre tu plata.")
    private fun usuario(texto: String, imagen: String? = null) =
        ChatMessage(ChatRole.USER, texto, imageBase64 = imagen, imageMime = imagen?.let { "image/png" })
    private fun asistente(texto: String) = ChatMessage(ChatRole.ASSISTANT, texto)

    @Test
    fun `el saludo se pinta pero no viaja`() {
        val viajan = mensajesParaEnviar(listOf(saludo, usuario("¿cuánto gasté?")))

        assertEquals(1, viajan.size)
        assertEquals(ChatRole.USER, viajan.first().role, "la API rechaza un historial que empieza en assistant")
    }

    @Test
    fun `una conversación larga sigue empezando por el usuario`() {
        val viajan = mensajesParaEnviar(
            listOf(saludo, usuario("¿cuánto gasté?"), asistente("\$1.200.000"), usuario("¿en qué?")),
        )

        assertEquals(ChatRole.USER, viajan.first().role)
        assertEquals(listOf("¿cuánto gasté?", "\$1.200.000", "¿en qué?"), viajan.map { it.content })
    }

    @Test
    fun `la imagen viaja solo en el turno al que pertenece`() {
        val historial = listOf(
            saludo,
            usuario("¿qué opinas de este recibo?", imagen = "AAAAfoto1"),
            asistente("Es un mercado de \$212.000."),
            usuario("¿y de este otro?", imagen = "AAAAfoto2"),
        )

        val viajan = mensajesParaEnviar(historial)

        assertEquals(1, viajan.count { it.imageBase64 != null }, "solo una imagen por petición")
        assertEquals("AAAAfoto2", viajan.last().imageBase64, "la del turno actual es la que va")
        val vieja = viajan.first { it.content.startsWith("¿qué opinas") }
        assertNull(vieja.imageBase64, "la foto anterior ya se mandó una vez; re-mandarla se paga de nuevo")
        assertNull(vieja.imageMime)
    }

    /**
     * Una foto sin texto no puede quedar como un mensaje vacío: la API rechaza un contenido vacío,
     * así que en su lugar queda dicho que ahí hubo una imagen.
     */
    @Test
    fun `una foto sin texto deja una nota en vez de un mensaje vacío`() {
        val viajan = mensajesParaEnviar(
            listOf(saludo, usuario("", imagen = "AAAAfoto"), asistente("Listo."), usuario("¿y entonces?")),
        )

        val vieja = viajan.first()
        assertEquals(TEXTO_DE_IMAGEN_QUE_YA_VIAJO, vieja.content)
        assertNull(vieja.imageBase64)
        assertTrue(viajan.none { it.content.isBlank() && it.imageBase64 == null })
    }

    /**
     * **La petición no puede crecer sin techo.** Con el tope, un chat abierto todo el día manda
     * siempre lo mismo de grande — y sigue empezando por un mensaje del usuario, que es lo único
     * que la API no perdona.
     */
    @Test
    fun `el historial que viaja tiene tope, y el recorte no rompe el arranque`() {
        val largo = buildList {
            add(saludo)
            repeat(60) { i ->
                add(usuario("pregunta $i"))
                add(asistente("respuesta $i"))
            }
            add(usuario("la última"))
        }

        val viajan = mensajesParaEnviar(largo)

        assertTrue(viajan.size <= CUANTOS_MENSAJES_VIAJAN, "viajaron ${viajan.size}")
        assertEquals(ChatRole.USER, viajan.first().role)
        assertEquals("la última", viajan.last().content)
    }
}
