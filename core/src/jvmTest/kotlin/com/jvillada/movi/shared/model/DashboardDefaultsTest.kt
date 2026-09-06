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
    fun default_definition_has_current_order_and_version() {
        val def = defaultDashboardDefinition()
        assertEquals("dashboard", def.slug)
        assertEquals(DASHBOARD_LAYOUT_VERSION, def.version)
        // Generación 5: sin QUICK_LINKS_WITH_TOTALS ("Explora") — sus cinco accesos duplicaban
        // navegación que ya existe en el rail/bottom-nav y en «Más» (ver el KDoc de
        // DASHBOARD_LAYOUT_VERSION).
        assertEquals(
            listOf("HERO_BALANCE", "UPCOMING_PAYMENTS", "ALERTS", "BANNER"),
            def.sections.map { it.type },
        )
    }

    /**
     * El dueño: «no le veo mucho sentido a la sección de Explora si es lo mismo que veo en el
     * menú». La generación 5 le hace caso: la sección sale de la definición por defecto, y la
     * generación sube para que su fila ya sembrada (generación 4) se reemplace después del
     * deploy — ver el KDoc largo sobre `DASHBOARD_LAYOUT_VERSION`.
     */
    @Test
    fun explora_section_is_gone_and_the_generation_bumped() {
        val def = defaultDashboardDefinition()
        assertTrue("QUICK_LINKS_WITH_TOTALS" !in def.sections.map { it.type }, "Explora ya no viene en el default")
        assertTrue(DASHBOARD_LAYOUT_VERSION >= 5, "quitar una sección entera exige subir la generación")
        // QUICK_LINKS_WITH_TOTALS se queda en la taxonomía: sigue siendo un tipo de sección
        // válido que el Editor puede volver a agregar, y las filas de otras instalaciones que
        // todavía lo traigan no deben romperse.
        assertTrue("QUICK_LINKS_WITH_TOTALS" in ScreenTaxonomy.SECTION_TYPES)
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

    /**
     * Ola 9: el Inicio cambió de portada («Tu plata» arriba, patrimonio debajo) y aun así el
     * título GUARDADO del hero no se tocó.
     *
     * No es un olvido, es la decisión. El rótulo del hero se cableó en el renderer
     * (`HERO_BALANCE_TITLE`, en `:shared`) justamente para no tener que tocar este título:
     * subirlo habría empujado `title = "Tu plata"` a TODOS los clientes en el instante del
     * deploy, incluido el APK ya instalado, que sigue pintando el patrimonio — y lo habría
     * titulado «Tu plata −$1.492.710.542», la lectura exacta que la ola vino a evitar.
     *
     * La generación SÍ subió después (a 5, por la Ola de «Explora» — ver
     * `explora_section_is_gone_and_the_generation_bumped`), pero por una razón sin relación con
     * el hero. Este test es el recordatorio de la otra mitad: si alguien cambia el título de
     * HERO_BALANCE en la semilla, está reabriendo la ventana de desalineación que la Ola 9
     * cerró. El título es dato inerte; el rótulo vive en el binario.
     */
    @Test
    fun ola9_kept_the_inert_hero_title() {
        val hero = defaultDashboardDefinition().sections.first { it.type == "HERO_BALANCE" }
        assertEquals("Balance neto", hero.title, "el título del hero es inerte y se queda como está desplegado")
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
