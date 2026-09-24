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

    // ── Ola B, tarea 7: Metas salió de la navegación ────────────────────────────

    /**
     * Un acceso «Metas» (QUICK_LINKS_WITH_TOTALS) que quedó guardado de antes de esta tanda no se
     * pinta más: se saca la FILA entera, no solo su acción — a diferencia de "settings" arriba,
     * que sigue mostrando su tarjeta sin poder tocarla.
     */
    @Test
    fun un_acceso_a_metas_guardado_no_se_pinta() {
        val def = ScreenDefinition("dashboard", 1, listOf(
            ScreenSection(type = "QUICK_LINKS_WITH_TOTALS", cards = listOf(
                ScreenCard(title = "Metas", action = ScreenAction("NAVIGATE", "goals")),
                ScreenCard(title = "Créditos", action = ScreenAction("NAVIGATE", "credits")),
            )),
        ))
        val cards = renderableSections(def)[0].cards
        assertEquals(1, cards.size)
        assertEquals("Créditos", cards[0].title)
    }

    /**
     * La poda es solo de los accesos con cifra. Un BANNER (o cualquier otro tipo) que use
     * "goals" por otro motivo —el target sigue siendo válido en `NAVIGATE_TARGETS`— no se toca:
     * lo que se saca es la puerta a Metas, no el target en general.
     */
    @Test
    fun la_poda_de_metas_es_solo_en_los_accesos_con_cifra() {
        val def = ScreenDefinition("dashboard", 1, listOf(
            ScreenSection(
                type = "BANNER", text = "Mira",
                cards = listOf(ScreenCard(title = "", action = ScreenAction("NAVIGATE", "goals"))),
            ),
        ))
        val renderable = renderableSections(def)
        assertEquals(1, renderable.size)
        assertEquals(ScreenAction("NAVIGATE", "goals"), renderable[0].cards[0].action)
    }
}
