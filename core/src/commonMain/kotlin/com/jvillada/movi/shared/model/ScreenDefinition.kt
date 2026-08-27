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
    val cards: List<ScreenCard> = emptyList(), // CARD_ROW / CARD_LIST / LINK_LIST / QUICK_LINKS_WITH_TOTALS
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
    /**
     * Tipos que el renderer sabe dibujar. Los tres primeros "de datos" (HERO_BALANCE,
     * UPCOMING_PAYMENTS, ALERTS) no llevan cards: el cliente los llena con lo que carga del
     * server. QUICK_LINKS_WITH_TOTALS lleva cards con acción NAVIGATE y el cliente le pone a
     * cada una la cifra que corresponde a su destino (total de cuentas, deuda, etc.).
     *
     * Ola 4 (F9/F40): ACCOUNTS_SUMMARY ("Mis cuentas") se fue — la lista de cuentas vive en
     * la pestaña Cuentas; una definición vieja que todavía lo traiga simplemente lo omite.
     */
    val SECTION_TYPES = listOf(
        "HERO_BALANCE", "UPCOMING_PAYMENTS", "ALERTS", "QUICK_LINKS_WITH_TOTALS",
        "CARD_ROW", "CARD_LIST", "LINK_LIST", "BANNER",
    )
    val ACTION_TYPES = listOf("NAVIGATE", "OPEN_URL")
    // "analisis" salió en la Ola 4 (F40): la pantalla Análisis se fundió en el Inicio.
    // "investments" se queda (F61): ya no hay pantalla Inversiones, pero una definición guardada
    // puede traerlo todavía — el cliente lo manda a Cuentas en vez de strippearlo.
    // "subscriptions" idem (Ola 8): Suscripciones se plegó dentro de Recurrentes. Sacarlo de esta
    // lista haría que `isValidAction` le arrancara la acción al acceso «Suscripciones» que YA está
    // guardado en el Inicio de cada instalación (y que ScreenValidation rechazara con 422 cualquier
    // guardado del Editor que todavía lo traiga). Se queda, y el cliente lo redirige.
    val NAVIGATE_TARGETS = listOf(
        "dashboard", "transactions", "quickadd", "budgets", "mas", "accounts", "credits",
        "goals", "investments", "subscriptions", "recurrentes", "extractos",
        "aichat", "profile",
        // Ola 10 — «Más → Categorías». Entra a la taxonomía para que el Editor de pantallas la
        // pueda enlazar desde el Inicio; el seed no la usa (ver seedScreens), así que agregarla
        // NO obliga a subir DASHBOARD_LAYOUT_VERSION ni pisa ninguna edición del dueño.
        "categorias",
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
