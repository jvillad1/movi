package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.RecuerdoDeCategoria
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Task 5 — «Escribir el nombre sugiere la categoría».
 *
 * El porqué de cada paso está en el KDoc de [sugerenciaPorNombre]; acá quedan clavados los casos
 * que lo motivaron.
 */
class SugerenciaDeCategoriaTest {

    private fun recuerdo(huella: String, categoria: String, nombre: String = huella, cuantos: Int = 1) =
        RecuerdoDeCategoria(huella = huella, categoria = categoria, nombre = nombre, cuantos = cuantos)

    @Test
    fun `coincidencia exacta de huella`() {
        val recuerdos = listOf(
            recuerdo("nombre:morasoccer", "Fútbol", nombre = "Mora Soccer"),
            recuerdo("nombre:mcdonalds", "Comida", nombre = "McDonald's"),
        )
        val sugerencia = sugerenciaPorNombre("Pago QR MORA SOCCER", recuerdos)
        assertEquals("Fútbol", sugerencia?.categoria)
    }

    @Test
    fun `una llave o una cuenta tambien matchean exacto, no solo un nombre`() {
        val recuerdos = listOf(recuerdo("llave:0092184713", "Comida", nombre = "Panadería de la 33"))
        val sugerencia = sugerenciaPorNombre("Pagaste \$9.000 por codigo QR a la llave 0092184713", recuerdos)
        assertEquals("Comida", sugerencia?.categoria)
    }

    @Test
    fun `prefijo unico- todos los que empiezan igual apuntan a la misma categoria`() {
        val recuerdos = listOf(
            recuerdo("nombre:morasoccer", "Fútbol", cuantos = 3),
            recuerdo("nombre:moralocura", "Fútbol", cuantos = 1),
        )
        // «Mora» (huella nombre:mora) todavía no es igual a ninguna huella guardada, pero es
        // prefijo de las dos — y las dos son Fútbol, así que no es ambiguo.
        val sugerencia = sugerenciaPorNombre("Mora", recuerdos)
        assertEquals("Fútbol", sugerencia?.categoria)
        assertEquals("nombre:morasoccer", sugerencia?.huella)
    }

    @Test
    fun `prefijo ambiguo entre dos categorias distintas no adivina`() {
        val recuerdos = listOf(
            recuerdo("nombre:morasoccer", "Fútbol"),
            recuerdo("nombre:moralinda", "Comida"),
        )
        // Las dos huellas empiezan con "mora" (4 letras, ya alcanza el mínimo) pero apuntan a
        // categorías distintas — ambiguo de verdad, no un prefijo corto.
        assertNull(sugerenciaPorNombre("Mora", recuerdos))
    }

    @Test
    fun `un prefijo ambiguo con mas usos en un lado no rompe el empate`() {
        // La regla es "todos la misma categoría", no "la que más usos tenga" — con categorías
        // distintas no hay forma correcta de adivinar cuál quiso decir.
        val recuerdos = listOf(
            recuerdo("nombre:morasoccer", "Fútbol", cuantos = 50),
            recuerdo("nombre:moralinda", "Comida", cuantos = 1),
        )
        assertNull(sugerenciaPorNombre("Mora", recuerdos))
    }

    @Test
    fun `clave de prefijo corta no sugiere aunque coincida`() {
        // "Mor" (3 letras) queda por debajo del mínimo de 4 — ver LARGO_MINIMO_DEL_PREFIJO.
        val recuerdos = listOf(recuerdo("nombre:morasoccer", "Fútbol"))
        assertNull(sugerenciaPorNombre("Mor", recuerdos))
        // Con la cuarta letra ya alcanza.
        assertEquals("Fútbol", sugerenciaPorNombre("Mora", recuerdos)?.categoria)
    }

    @Test
    fun `una categoria reservada nunca se sugiere, aunque la huella matchee exacto`() {
        val recuerdos = listOf(recuerdo("nombre:techogardenera", TRANSFER_CATEGORY))
        assertNull(sugerenciaPorNombre("Techo Gardenera", recuerdos))
    }

    @Test
    fun `nota vacia no sugiere nada`() {
        val recuerdos = listOf(recuerdo("nombre:morasoccer", "Fútbol"))
        assertNull(sugerenciaPorNombre("", recuerdos))
    }

    @Test
    fun `una nota que no identifica a nadie no sugiere nada`() {
        val recuerdos = listOf(recuerdo("nombre:morasoccer", "Fútbol"))
        assertNull(sugerenciaPorNombre("Pago QR", recuerdos))
    }

    @Test
    fun `sin memoria no hay sugerencia`() {
        assertNull(sugerenciaPorNombre("Mora Soccer", emptyList()))
    }
}
