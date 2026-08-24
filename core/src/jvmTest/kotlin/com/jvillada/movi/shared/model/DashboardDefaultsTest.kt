package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La definición por defecto es la que se pinta si el server no responde: tiene que ser
 * 100% renderizable (nada filtrado por tipo o acción) y traer el orden acordado en la Ola 4.
 */
class DashboardDefaultsTest {

    @Test
    fun default_definition_is_fully_renderable() {
        val def = defaultDashboardDefinition()
        val renderable = renderableSections(def)
        assertEquals(def.sections, renderable, "ninguna sección ni acción del default debe filtrarse")
        assertTrue(def.sections.flatMap { it.cards }.all { it.action != null }, "cada acceso lleva acción")
    }

    @Test
    fun default_definition_has_ola4_order_and_version() {
        val def = defaultDashboardDefinition()
        assertEquals("dashboard", def.slug)
        assertEquals(DASHBOARD_LAYOUT_VERSION, def.version)
        assertEquals(
            listOf("HERO_BALANCE", "UPCOMING_PAYMENTS", "ALERTS", "QUICK_LINKS_WITH_TOTALS", "BANNER"),
            def.sections.map { it.type },
        )
        val links = def.sections.first { it.type == "QUICK_LINKS_WITH_TOTALS" }.cards.map { it.action!!.target }
        // F61: sin "investments" — Inversiones ya no es pantalla.
        assertEquals(listOf("accounts", "credits", "budgets", "goals", "subscriptions"), links)
    }

    /**
     * Ola 8: Suscripciones se plegó dentro de Recurrentes, pero el target NO se borra de la
     * taxonomía — el acceso «Suscripciones» ya está guardado en el Inicio de cada instalación.
     * Si saliera de la lista, `isValidAction` le arrancaría la acción (una tarjeta muerta que no
     * lleva a ningún lado) y `ScreenValidation` rechazaría con 422 cualquier guardado del Editor
     * que todavía lo traiga. Mismo trato que "investments" en F61: se queda y el cliente lo
     * redirige (ver `SduiRenderer.screenForTarget`).
     */
    @Test
    fun subscriptions_target_survives_the_screen_merge() {
        assertTrue("subscriptions" in ScreenTaxonomy.NAVIGATE_TARGETS)
        assertTrue("investments" in ScreenTaxonomy.NAVIGATE_TARGETS)
        val stored = ScreenDefinition("dashboard", DASHBOARD_LAYOUT_VERSION, listOf(
            ScreenSection(type = "QUICK_LINKS_WITH_TOTALS", cards = listOf(
                ScreenCard("Suscripciones", action = ScreenAction("NAVIGATE", "subscriptions")),
            )),
        ))
        assertEquals(stored.sections, renderableSections(stored), "el acceso guardado conserva su acción")
    }

    @Test
    fun accounts_summary_and_analisis_are_gone() {
        assertTrue("ACCOUNTS_SUMMARY" !in ScreenTaxonomy.SECTION_TYPES)
        assertTrue("analisis" !in ScreenTaxonomy.NAVIGATE_TARGETS)
        // Una definición vieja que aún los traiga no rompe: se omite la sección y se limpia la acción.
        val old = ScreenDefinition("dashboard", 1, listOf(
            ScreenSection(type = "ACCOUNTS_SUMMARY"),
            ScreenSection(type = "LINK_LIST", cards = listOf(ScreenCard("Análisis", action = ScreenAction("NAVIGATE", "analisis")))),
        ))
        val r = renderableSections(old)
        assertEquals(1, r.size)
        assertEquals(null, r[0].cards[0].action)
    }
}
