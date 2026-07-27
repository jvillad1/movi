package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * Definición server-driven de una pantalla (SDUI). Persistida en `screen_definitions`
 * (server) como `sections_json`; servida vía `GET /api/screens/{slug}` con soporte de
 * `If-None-Match`/304 por versión. Ver docs/superpowers/specs/2026-07-26-sdui-movi-design.md.
 */
@Serializable
data class ScreenDefinition(
    val slug: String,
    val version: Int,
    val sections: List<ScreenSection>,
)

@Serializable
data class ScreenSection(
    val type: String,                          // uno de ScreenTaxonomy.SECTION_TYPES
    val title: String? = null,
    val cards: List<ScreenCard> = emptyList(), // CARD_ROW / CARD_LIST / LINK_LIST
    val text: String? = null,                  // BANNER
)

@Serializable
data class ScreenCard(
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val badge: String? = null,
    val action: ScreenAction? = null,
)

@Serializable
data class ScreenAction(val type: String, val target: String) // NAVIGATE | OPEN_URL

object ScreenTaxonomy {
    val SECTION_TYPES = listOf(
        "HERO_BALANCE", "ACCOUNTS_SUMMARY", "CARD_ROW", "CARD_LIST", "LINK_LIST", "BANNER",
    )
    val ACTION_TYPES = listOf("NAVIGATE", "OPEN_URL")
    val NAVIGATE_TARGETS = listOf(
        "dashboard", "transactions", "quickadd", "budgets", "mas", "accounts", "credits",
        "goals", "investments", "subscriptions", "recurrentes", "analisis", "extractos",
        "aichat", "profile",
    )
}

/**
 * Secciones que un renderer de esta versión sabe dibujar, con acciones inválidas strippeadas.
 * Tolerancia vive aquí (en el filtro), NO en la deserialización: un tipo/target desconocido
 * no debe romper la pantalla, solo omitirse/limpiarse.
 */
fun renderableSections(def: ScreenDefinition): List<ScreenSection> =
    def.sections
        .filter { it.type in ScreenTaxonomy.SECTION_TYPES }
        .map { section ->
            section.copy(cards = section.cards.map { card ->
                if (card.action != null && !isValidAction(card.action)) card.copy(action = null) else card
            })
        }

private fun isValidAction(a: ScreenAction): Boolean = when (a.type) {
    "NAVIGATE" -> a.target in ScreenTaxonomy.NAVIGATE_TARGETS
    "OPEN_URL" -> a.target.startsWith("https://")
    else -> false
}
