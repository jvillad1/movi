package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenDefinitionTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundtrip_serializes_and_deserializes() {
        val def = ScreenDefinition(
            slug = "dashboard", version = 3,
            sections = listOf(
                ScreenSection(type = "CARD_ROW", title = "Explora", cards = listOf(
                    ScreenCard(title = "Créditos", subtitle = "2 activos", imageUrl = "https://x/y.jpg",
                        badge = "Nuevo",
                        action = ScreenAction("NAVIGATE", "credits")),
                )),
                ScreenSection(type = "BANNER", text = "Sin alertas por ahora"),
            ),
        )
        val decoded = json.decodeFromString<ScreenDefinition>(json.encodeToString(ScreenDefinition.serializer(), def))
        assertEquals(def, decoded)
    }

    @Test
    fun unknown_section_type_deserializes_and_is_filtered() {
        val raw = """{"slug":"dashboard","version":1,"sections":[
            {"type":"HOLOGRAM_3D","title":"Futuro"},
            {"type":"BANNER","text":"hola"}]}"""
        val def = json.decodeFromString<ScreenDefinition>(raw)
        assertEquals(2, def.sections.size)              // deserializa sin explotar
        val renderable = renderableSections(def)
        assertEquals(1, renderable.size)                // el desconocido se salta
        assertEquals("BANNER", renderable[0].type)
    }

    @Test
    fun invalid_actions_are_stripped_not_fatal() {
        val def = ScreenDefinition("s", 1, listOf(
            ScreenSection(type = "CARD_LIST", cards = listOf(
                ScreenCard(title = "a", action = ScreenAction("NAVIGATE", "settings")),      // target fuera de whitelist
                ScreenCard(title = "b", action = ScreenAction("EXPLODE", "x")),                // tipo desconocido
                ScreenCard(title = "c", action = ScreenAction("OPEN_URL", "http://insecure")), // no https
                ScreenCard(title = "d", action = ScreenAction("NAVIGATE", "credits")),         // válida
            )),
        ))
        val cards = renderableSections(def)[0].cards
        assertNull(cards[0].action); assertNull(cards[1].action); assertNull(cards[2].action)
        assertEquals(ScreenAction("NAVIGATE", "credits"), cards[3].action)
    }
}
