package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * **Buscar funciona igual en toda la app.**
 *
 * Había tres copias privadas de esta regla —Movimientos, el campo de categoría y la pantalla de
 * categorías— y la de Movimientos se había quedado sin pasar la `ü` a `u`: «pinguino» encontraba
 * «Pingüino» al elegir una categoría y no en la lista de movimientos.
 */
class NormalizarParaBuscarTest {

    @Test
    fun `sin tildes y en minusculas`() {
        assertEquals("credito", normalizarParaBuscar("Crédito"))
        assertEquals("vehiculo 8761", normalizarParaBuscar("Vehículo 8761"))
    }

    /** **El que se había separado.** La búsqueda de Movimientos no pasaba la `ü`. */
    @Test
    fun `la dieresis tambien se va`() {
        assertEquals("pinguino", normalizarParaBuscar("Pingüino"))
    }

    @Test
    fun `los acentos que no son del espanol tambien`() {
        assertEquals("cafe creme", normalizarParaBuscar("Café Crème"))
    }

    /**
     * La `ñ` pasa a `n`: en una búsqueda la colisión «año»/«ano» cuesta un resultado de más, y a
     * cambio encuentra lo que se escribió desde un teclado sin `ñ`.
     */
    @Test
    fun `la enie se busca como n`() {
        assertEquals(normalizarParaBuscar("Compañía"), normalizarParaBuscar("compania"))
    }

    @Test
    fun `los espacios se aprietan y los bordes se recortan`() {
        assertEquals("carnes y legumbres", normalizarParaBuscar("  Carnes  y   Legumbres "))
    }

    /**
     * **No es una clave de identidad**, y no tiene que parecerse a una: conserva la puntuación y
     * los espacios, que [claveComparableDeNombre] tira. Si alguien las unificara, la búsqueda
     * empezaría a encontrar «Pago-Nu» escribiendo «pagonu», y peor, las claves guardadas cambiarían.
     */
    @Test
    fun `no se confunde con la clave de identidad`() {
        assertNotEquals(claveComparableDeNombre("Pago de Nu"), normalizarParaBuscar("Pago de Nu"))
    }
}
