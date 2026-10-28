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
    fun `el orden elegido se aplica y el héroe, las acciones y las propuestas siguen arriba`() {
        val elegido = PreferenciasDelInicio(orden = listOf("GASTO_POR_CATEGORIA", "CUENTAS", "CHECKLIST_DEL_PERIODO"))
        val hoy = inicioConPreferencias(porDefecto, elegido)
        assertEquals(
            listOf(
                "HERO_BALANCE", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS", "GASTO_POR_CATEGORIA", "CUENTAS", "CHECKLIST_DEL_PERIODO",
                "ALERTS", "BANNER",
            ),
            tipos(hoy),
        )
        // Nombrar al héroe o a las propuestas no los mueve: no son configurables.
        val conFijos = inicioConPreferencias(porDefecto, elegido.copy(orden = listOf("PROPUESTAS", "CUENTAS", "HERO_BALANCE")))
        assertEquals(listOf("HERO_BALANCE", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS"), tipos(conFijos).take(5))
    }

    @Test
    fun `las ocultas salen, y lo que ya no es configurable no se puede esconder`() {
        val hoy = inicioConPreferencias(porDefecto, PreferenciasDelInicio(ocultas = listOf("CUENTAS", "ALERTS", "PROPUESTAS")))
        assertEquals(
            listOf("HERO_BALANCE", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS", "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA", "ALERTS", "BANNER"),
            tipos(hoy),
        )
        // La línea compacta de «Para revisar» sigue siendo un tipo personal: se pinta si llega, y
        // nunca va en la definición global (el Editor de pantallas no la conoce).
        val conCompacta = porDefecto.copy(sections = porDefecto.sections + ScreenSection(type = PARA_REVISAR_COMPACTO))
        assertTrue(renderableSections(conCompacta).any { it.type == PARA_REVISAR_COMPACTO })
        assertTrue(PARA_REVISAR_COMPACTO !in ScreenTaxonomy.SECTION_TYPES)
    }

    /**
     * Ola R1: «Disponible» y «Tu patrimonio» salieron del Inicio por defecto (repetían Plan y
     * Patrimonio), pero el dueño las puede prender. El server las agrega después de la última sección
     * configurable —antes de lo que queda solo para los APK viejos—, y el orden elegido las mueve como
     * a cualquier otra.
     */
    @Test
    fun `una opcional prendida se agrega después de los widgets y se puede mover`() {
        assertTrue(SECCIONES_OPCIONALES.none { it in tipos(porDefecto) }, "no vienen en el Inicio de Movi")
        val conDisponible = inicioConPreferencias(porDefecto, PreferenciasDelInicio(visibles = listOf("DISPONIBLE_DEL_PERIODO")))
        assertEquals(
            listOf(
                "HERO_BALANCE", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS", "CUENTAS", "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA",
                "DISPONIBLE_DEL_PERIODO", "ALERTS", "BANNER",
            ),
            tipos(conDisponible),
        )
        assertEquals("Disponible", conDisponible.sections.first { it.type == "DISPONIBLE_DEL_PERIODO" }.title)
        val lasDosArriba = inicioConPreferencias(
            porDefecto,
            PreferenciasDelInicio(
                visibles = listOf("PATRIMONIO", "DISPONIBLE_DEL_PERIODO"),
                orden = listOf("PATRIMONIO", "CUENTAS", "DISPONIBLE_DEL_PERIODO", "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA"),
            ),
        )
        assertEquals(
            listOf(
                "HERO_BALANCE", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS", "PATRIMONIO", "CUENTAS", "DISPONIBLE_DEL_PERIODO",
                "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA", "ALERTS", "BANNER",
            ),
            tipos(lasDosArriba),
        )
        // Una opcional que la definición ya trae (el Editor de pantallas la puso para todos) no se duplica.
        val conPatrimonioGlobal = porDefecto.copy(sections = porDefecto.sections + ScreenSection(type = "PATRIMONIO"))
        assertEquals(1, tipos(inicioConPreferencias(conPatrimonioGlobal, PreferenciasDelInicio(visibles = listOf("PATRIMONIO")))).count { it == "PATRIMONIO" })
    }

    @Test
    fun `una sección que la preferencia no nombra queda en su lugar por defecto`() {
        // El dueño eligió su orden cuando Hoy no tenía el carrusel de cuentas; después Movi lo agregó.
        val elegido = PreferenciasDelInicio(orden = listOf("GASTO_POR_CATEGORIA", "CHECKLIST_DEL_PERIODO"))
        val hoy = inicioConPreferencias(porDefecto, elegido)
        // CUENTAS conserva su lugar, y las nombradas se reparten los demás lugares.
        assertEquals(
            listOf("HERO_BALANCE", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS", "CUENTAS", "GASTO_POR_CATEGORIA", "CHECKLIST_DEL_PERIODO", "ALERTS", "BANNER"),
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
        // Prender una opcional también la cambia, y sin opcionales la huella es la de antes de la R1.
        val conOpcional = a.copy(visibles = listOf("PATRIMONIO"))
        assertNotEquals(va, versionConPreferencias(porDefecto.version, conOpcional))
        assertNotEquals(porDefecto.version, versionConPreferencias(porDefecto.version, PreferenciasDelInicio(visibles = listOf("PATRIMONIO"))))
    }

    @Test
    fun `limpias saca lo que no es configurable y los repetidos`() {
        val sucia = PreferenciasDelInicio(
            orden = listOf("PATRIMONIO", "PATRIMONIO", "HERO_BALANCE", "BANNER", "ALERTS", "CUENTAS"),
            ocultas = listOf("CUENTAS", "CUENTAS", "NO_EXISTE", "PREGUNTALE_A_MOVI"),
            visibles = listOf("PATRIMONIO", "CUENTAS", "PATRIMONIO"),
            ordenDeMovi = listOf("CUENTAS"),
        )
        assertEquals(
            PreferenciasDelInicio(orden = listOf("PATRIMONIO", "CUENTAS"), ocultas = listOf("CUENTAS"), visibles = listOf("PATRIMONIO")),
            sucia.limpias(),
        )
    }

    @Test
    fun `las configurables son las del rediseño, y las que no son opcionales vienen en el Inicio por defecto`() {
        assertEquals(listOf("CUENTAS", "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA"), seccionesConfigurablesDe(porDefecto))
        assertEquals(SECCIONES_CONFIGURABLES.keys.toList() - SECCIONES_OPCIONALES.toSet(), seccionesConfigurablesDe(porDefecto))
        assertTrue(SECCIONES_OPCIONALES.all { it in SECCIONES_CONFIGURABLES })
        // Lo fijo arriba no se configura: el héroe, la cuadrícula y las propuestas.
        assertTrue(listOf("HERO_BALANCE", "ACCIONES", "PROXIMO_PAGO", "PROPUESTAS", "ULTIMOS_MOVIMIENTOS").none { it in SECCIONES_CONFIGURABLES })
    }
}
