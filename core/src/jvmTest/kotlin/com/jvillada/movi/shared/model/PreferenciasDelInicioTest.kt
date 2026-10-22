package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * «Editar inicio» (Ola 3): la regla con la que el server arma Hoy con la preferencia del dueño.
 */
class PreferenciasDelInicioTest {

    private val porDefecto = defaultDashboardDefinition()

    private fun tipos(def: ScreenDefinition) = def.sections.map { it.type }

    @Test
    fun `sin preferencia Hoy sale idéntico, misma versión`() {
        assertSame(porDefecto, inicioConPreferencias(porDefecto, PreferenciasDelInicio()))
        // Lo que no es configurable no cuenta como preferencia: el héroe, un tipo inventado.
        val soloRuido = PreferenciasDelInicio(orden = listOf("HERO_BALANCE", "NO_EXISTE"), ocultas = listOf("HERO_BALANCE"))
        assertEquals(porDefecto, inicioConPreferencias(porDefecto, soloRuido))
    }

    @Test
    fun `el orden elegido se aplica y el héroe sigue primero`() {
        val elegido = PreferenciasDelInicio(
            orden = listOf("GASTO_POR_CATEGORIA", "PATRIMONIO", "DISPONIBLE_DEL_PERIODO", "ALERTS", "PREGUNTALE_A_MOVI", "CHECKLIST_DEL_PERIODO"),
        )
        val hoy = inicioConPreferencias(porDefecto, elegido)
        assertEquals(
            listOf(
                "HERO_BALANCE", "GASTO_POR_CATEGORIA", "PATRIMONIO", "DISPONIBLE_DEL_PERIODO", "ALERTS",
                "PREGUNTALE_A_MOVI", "CHECKLIST_DEL_PERIODO", "BANNER",
            ),
            tipos(hoy),
        )
        // Nombrar al héroe no lo mueve: no es configurable.
        val conHeroe = inicioConPreferencias(porDefecto, elegido.copy(orden = listOf("PATRIMONIO", "HERO_BALANCE")))
        assertEquals("HERO_BALANCE", tipos(conHeroe).first())
    }

    @Test
    fun `las ocultas salen, y Para revisar deja su línea compacta en el mismo lugar`() {
        val hoy = inicioConPreferencias(porDefecto, PreferenciasDelInicio(ocultas = listOf("DISPONIBLE_DEL_PERIODO", "ALERTS")))
        assertEquals(
            listOf("HERO_BALANCE", "PREGUNTALE_A_MOVI", "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA", "PATRIMONIO", PARA_REVISAR_COMPACTO, "BANNER"),
            tipos(hoy),
        )
        // El tipo personal se pinta en este cliente…
        assertTrue(renderableSections(hoy).any { it.type == PARA_REVISAR_COMPACTO })
        // …y un APK anterior (sin el tipo en su taxonomía) no ve ni «Para revisar» ni la línea.
        val tiposDeUnApkViejo = ScreenTaxonomy.SECTION_TYPES
        assertTrue(hoy.sections.filter { it.type in tiposDeUnApkViejo }.none { it.type == "ALERTS" || it.type == PARA_REVISAR_COMPACTO })
        // Nunca va en la definición global: el Editor de pantallas no lo conoce.
        assertTrue(PARA_REVISAR_COMPACTO !in ScreenTaxonomy.SECTION_TYPES)
    }

    @Test
    fun `una sección que la preferencia no nombra queda en su lugar por defecto`() {
        // El dueño eligió su orden cuando Hoy no tenía «Tu patrimonio»; después Movi la agregó.
        val elegido = PreferenciasDelInicio(orden = listOf("ALERTS", "GASTO_POR_CATEGORIA", "DISPONIBLE_DEL_PERIODO", "CHECKLIST_DEL_PERIODO", "PREGUNTALE_A_MOVI"))
        val hoy = inicioConPreferencias(porDefecto, elegido)
        // PATRIMONIO conserva su lugar (el sexto), y las nombradas se reparten los demás lugares.
        assertEquals(
            listOf("HERO_BALANCE", "ALERTS", "GASTO_POR_CATEGORIA", "DISPONIBLE_DEL_PERIODO", "CHECKLIST_DEL_PERIODO", "PATRIMONIO", "PREGUNTALE_A_MOVI", "BANNER"),
            tipos(hoy),
        )
        // Lo mismo con un tipo del Editor que la preferencia nunca nombra.
        val conTarjetas = porDefecto.copy(sections = porDefecto.sections + ScreenSection(type = "CARD_LIST", title = "Enlaces"))
        assertEquals("CARD_LIST", tipos(inicioConPreferencias(conTarjetas, elegido)).last())
    }

    @Test
    fun `la versión cambia con la preferencia y vuelve con el orden de Movi`() {
        val a = PreferenciasDelInicio(ocultas = listOf("PATRIMONIO"))
        val b = PreferenciasDelInicio(ocultas = listOf("DISPONIBLE_DEL_PERIODO"))
        val va = versionConPreferencias(porDefecto.version, a)
        assertNotEquals(porDefecto.version, va)
        assertNotEquals(va, versionConPreferencias(porDefecto.version, b))
        assertEquals(va, versionConPreferencias(porDefecto.version, a), "determinista")
        assertNotEquals(va, versionConPreferencias(porDefecto.version + 1, a), "una edición global también la cambia")
        assertTrue(va >= 0x4000_0000, "nunca choca con una versión global")
        assertEquals(porDefecto.version, versionConPreferencias(porDefecto.version, PreferenciasDelInicio()))
        assertEquals(va, inicioConPreferencias(porDefecto, a).version)
    }

    @Test
    fun `limpias saca lo que no es configurable y los repetidos`() {
        val sucia = PreferenciasDelInicio(
            orden = listOf("PATRIMONIO", "PATRIMONIO", "HERO_BALANCE", "BANNER", "ALERTS"),
            ocultas = listOf("ALERTS", "ALERTS", "NO_EXISTE"),
            ordenDeMovi = listOf("ALERTS"),
        )
        assertEquals(PreferenciasDelInicio(orden = listOf("PATRIMONIO", "ALERTS"), ocultas = listOf("ALERTS")), sucia.limpias())
    }

    @Test
    fun `las configurables son las seis de la revisión y existen en el Hoy por defecto`() {
        assertEquals(
            listOf("PREGUNTALE_A_MOVI", "CHECKLIST_DEL_PERIODO", "DISPONIBLE_DEL_PERIODO", "GASTO_POR_CATEGORIA", "PATRIMONIO", "ALERTS"),
            seccionesConfigurablesDe(porDefecto),
        )
        assertEquals(SECCIONES_CONFIGURABLES.keys.toList(), seccionesConfigurablesDe(porDefecto))
        assertTrue("HERO_BALANCE" !in SECCIONES_CONFIGURABLES)
    }
}
