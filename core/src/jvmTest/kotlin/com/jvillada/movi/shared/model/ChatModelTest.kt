package com.jvillada.movi.shared.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * F32 (imágenes en el chat): [ChatMessage] gana `imageBase64`/`imageMime`, opcionales. Estos
 * tests protegen la compatibilidad de red con lo que había antes de la Ola 6 — un mensaje
 * sin imagen tiene que decodificar y comportarse igual que hoy.
 */
class ChatModelTest {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun `JSON viejo sin imageBase64 ni imageMime decodifica con ambos en null`() {
        // Forma exacta del wire de antes de esta ola: solo role y content.
        val old = """{"role":"USER","content":"¿Cuánto gasté en mercado?"}"""
        val decoded = lenientJson.decodeFromString<ChatMessage>(old)
        assertEquals(ChatRole.USER, decoded.role)
        assertEquals("¿Cuánto gasté en mercado?", decoded.content)
        assertNull(decoded.imageBase64)
        assertNull(decoded.imageMime)
    }

    @Test
    fun `un mensaje sin imagen hace round-trip igual que antes`() {
        val original = ChatMessage(role = ChatRole.ASSISTANT, content = "Tu flujo del mes es de \$300.000.")
        val json = lenientJson.encodeToString(original)
        val decoded = lenientJson.decodeFromString<ChatMessage>(json)
        assertEquals(original, decoded)
        assertNull(decoded.imageBase64)
        assertNull(decoded.imageMime)
    }

    @Test
    fun `un mensaje con imagen hace round-trip completo`() {
        val original = ChatMessage(
            role = ChatRole.USER,
            content = "¿Qué opinas de este recibo?",
            imageBase64 = "aGVsbG8=",
            imageMime = "image/png",
        )
        val json = lenientJson.encodeToString(original)
        val decoded = lenientJson.decodeFromString<ChatMessage>(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `AiChatRequest con mensajes mixtos round-trips`() {
        val request = AiChatRequest(
            messages = listOf(
                ChatMessage(ChatRole.USER, "hola"),
                ChatMessage(ChatRole.USER, "mira esto", imageBase64 = "aGVsbG8=", imageMime = "image/jpeg"),
            ),
        )
        val json = lenientJson.encodeToString(request)
        val decoded = lenientJson.decodeFromString<AiChatRequest>(json)
        assertEquals(request, decoded)
    }
}
