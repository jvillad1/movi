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
        // Ola 8: "subscriptions" → "recurrentes" — el acceso lleva el nombre y la cifra de la
        // pantalla a la que de verdad va.
        assertEquals(listOf("accounts", "credits", "budgets", "goals", "recurrentes"), links)
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
        // El acceso por defecto ya NO usa este target (generación 4 lo cambió por "recurrentes"),
        // pero el target sigue siendo válido: cualquier Inicio guardado de otra instalación —o de
        // una que no haya recibido todavía el seed nuevo— lo tiene en su fila. Si saliera de la
        // taxonomía, `isValidAction` le arrancaría la acción (tarjeta muerta) y `ScreenValidation`
        // rechazaría con 422 el guardado. Mismo trato que "investments" en F61: se queda, y el
        // cliente lo redirige (ver `SduiRenderer.screenForTarget`).
        assertTrue("subscriptions" in ScreenTaxonomy.NAVIGATE_TARGETS)
        assertTrue("investments" in ScreenTaxonomy.NAVIGATE_TARGETS)
        val stored = ScreenDefinition("dashboard", 3, listOf(
            ScreenSection(type = "QUICK_LINKS_WITH_TOTALS", cards = listOf(
                ScreenCard("Suscripciones", action = ScreenAction("NAVIGATE", "subscriptions")),
            )),
        ))
        assertEquals(stored.sections, renderableSections(stored), "el acceso guardado conserva su acción")
    }

    /** La generación tiene que subir cada vez que cambia la lista, o el seed no reemplaza la fila. */
    @Test
    fun ola8_bumped_the_layout_generation() {
        assertTrue(DASHBOARD_LAYOUT_VERSION >= 4, "la Ola 8 cambió los accesos: la generación sube")
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
