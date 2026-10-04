package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Que la categoría aprenda de verdad (auditoría de la ingesta, 4-oct-2026, arreglo 7)
 *
 * La propuesta coincidía con la categoría final del dueño en 22 de 114 avisos (19 %). Dos causas:
 * los nombres que el banco recorta no se reconocían contra los que él escribió entero, y las palabras
 * clave no conocían lo que él compra. Los comercios de estas pruebas son los de sus avisos reales.
 */
class LaCategoriaAprendeTest {

    private fun anotacion(texto: String, categoria: String, cuando: Long = 0, nombre: String = texto) =
        AnotacionPasada(comoLlego = texto, nombre = nombre, categoria = categoria, cuando = cuando)

    // ── El nombre recortado por el banco ─────────────────────────────────────

    @Test
    fun `el nombre que el banco recorto se reconoce contra el que el dueno escribio entero`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                anotacion("Criminal Taqueria", "Comida"),
                anotacion("Il Capuccino Cafetería", "Comida"),
            ),
        )
        assertEquals("Comida", memoria.recuerdoDe("CRIMINAL TAQUERIA PR")?.categoria, "el banco le pegó la sucursal")
        assertEquals("Comida", memoria.recuerdoDe("IL CAPUCCINO CAFETER")?.categoria, "el banco cortó a 20 letras")
    }

    @Test
    fun `entre dos prefijos gana el que mas comparte`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                anotacion("Tostao Cafe", "Comida"),
                anotacion("TOSTAO CAFE Y PAN", "Hija"),
            ),
        )
        assertEquals("Hija", memoria.recuerdoDe("TOSTAO CAFE Y PAN VISC")?.categoria)
    }

    @Test
    fun `un nombre corto no vale por otro`() {
        val memoria = MemoriaDeCategorias.de(listOf(anotacion("Cafe", "Hija")))
        assertNull(memoria.recuerdoDe("CAFE PERGAMINO"), "«cafe» es una palabra, no un comercio")
    }

    @Test
    fun `un numero a medias es otro numero`() {
        assertNull(huellaPorPrefijo("llave:0047142708", listOf("llave:004714270")))
        assertNull(huellaPorPrefijo("cuenta:41279033068", listOf("cuenta:412790")))
    }

    @Test
    fun `la huella exacta le gana al prefijo`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                anotacion("CRIMINAL TAQUERIA PR", "Regalos"),
                anotacion("Criminal Taqueria", "Comida"),
            ),
        )
        assertEquals("Regalos", memoria.recuerdoDe("CRIMINAL TAQUERIA PR")?.categoria)
    }

    // ── Las palabras clave, con lo que él compra ─────────────────────────────

    private val delDueno = setOf("Comida", "Salud", "Transporte", "Mercado", "Mercado extra", "Fútbol", "Hija", "Gimnasio", "Cuidado personal", "Entretenimiento", "Tecnología")

    @Test
    fun `lo que compra el dueno ya no sale en Otros`() {
        val esperado = mapOf(
            "DROGUERIA ALEMANA 518" to "Salud",
            "DROGUERIA POM MED OV" to "Salud",
            "TOSTAO CAFE Y PAN VI" to "Comida",
            "SUBWAY VIZCAYA" to "Comida",
            "KOKORIKO AEROPUERTO" to "Comida",
            "CRIMINAL TAQUERIA PR" to "Comida",
            "IL CAPUCCINO CAFETER" to "Comida",
            "MCDONAL D  EGA" to "Comida",
            "CAFE PERGAMINO" to "Comida",
            "UBER*RIDES" to "Transporte",
            "PARQUEADERO CENTRO" to "Transporte",
            "TERPEL LA 33" to "Transporte",
            "CARULLA RINCON OVIED" to "Mercado",
            "EXITO POBLADO" to "Mercado",
            "ACTION BLACK VNP" to "Gimnasio",
            "CELEBRITY BARBER SHO" to "Cuidado personal",
            "CINE COLOMBIA" to "Entretenimiento",
            "GOOGLE Google One" to "Tecnología",
        )
        esperado.forEach { (comercio, categoria) ->
            assertEquals(categoria, categoriaProbablePorElNombre(comercio, delDueno), comercio)
        }
    }

    @Test
    fun `sin saber sus categorias habla el catalogo de la app, como antes`() {
        val delCatalogo = PREDEFINED_CATEGORIES.map { it.name }.toSet()
        assertEquals("Comida", categoriaProbablePorElNombre("CARULLA RINCON OVIED"))
        assertEquals("Salud", categoriaProbablePorElNombre("ACTION BLACK VNP"))
        listOf("DROGUERIA ALEMANA 518", "SUBWAY VIZCAYA", "UBER RIDES", "NETFLIX.COM").forEach {
            assertTrue(categoriaProbablePorElNombre(it) in delCatalogo, it)
        }
    }

    @Test
    fun `una categoria que el dueno no tiene no se inventa, y nunca es Otros`() {
        assertNull(categoriaProbablePorElNombre("CELEBRITY BARBER SHO", setOf("Comida")), "«Cuidado personal» no es de la app ni suya")
        assertNull(categoriaProbablePorElNombre("ZELO GROUP", delDueno))
        val todas = listOf("DROGUERIA ALEMANA", "KOKORIKO", "ZELO", "BARBER", "CLARO", "HOLAFLY")
            .map { categoriaProbablePorElNombre(it, delDueno) }
        assertTrue(todas.none { it == "Otros" || it == "Otro" })
    }

    @Test
    fun `las marcas cortas son palabra completa`() {
        assertNull(categoriaProbablePorElNombre("CAMARA DE COMERCIO", delDueno), "«ara» adentro de «camara» no es la tienda")
        assertEquals("Mercado", categoriaProbablePorElNombre("TIENDAS ARA 1234", delDueno))
    }

    // ── El vocabulario del dueño ─────────────────────────────────────────────

    @Test
    fun `la memoria sabe que categorias usa el dueno, sin las que pone Movi`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                anotacion("Las Doce", "Comida"),
                anotacion("Carulla", "Mercado extra"),
                anotacion("Saldo inicial", OPENING_CATEGORY),
                anotacion("Cosa", "Otros"),
            ),
        )
        assertEquals(setOf("Comida", "Mercado extra"), memoria.categoriasDelDueno)
        assertNotNull(memoria.recuerdoDe("LAS DOCE"))
    }
}
