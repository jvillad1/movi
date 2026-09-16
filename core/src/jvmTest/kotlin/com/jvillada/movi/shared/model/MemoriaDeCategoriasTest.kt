package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **La memoria, con los movimientos de verdad del dueño.**
 *
 * Los textos de estas pruebas no son inventados: son los que estaban en producción el 16-sep-2026,
 * incluidos los cinco que quedaron en «Otro» y que fueron el motivo de todo esto.
 */
class MemoriaDeCategoriasTest {

    private fun anotacion(
        texto: String,
        categoria: String,
        cuando: Long = 0,
        nombre: String = texto,
    ) = AnotacionPasada(comoLlego = texto, nombre = nombre, categoria = categoria, cuando = cuando)

    // ── La huella ────────────────────────────────────────────────────────────

    @Test
    fun `dos pagos QR distintos no comparten huella, y el mismo si`() {
        val uno = huellaDeUnMovimiento("Pago QR (llave 0092184713)")
        val otro = huellaDeUnMovimiento("Pago QR (llave 0087551200)")
        val elMismo = huellaDeUnMovimiento("Pagaste \$18.500 por codigo QR a la llave 0092184713")

        assertEquals("llave:0092184713", uno)
        assertEquals("llave:0087551200", otro)
        assertEquals(uno, elMismo, "la misma llave es el mismo destinatario, lo diga como lo diga")
    }

    @Test
    fun `una transferencia se reconoce por el numero de cuenta`() {
        assertEquals(
            "cuenta:41279033068",
            huellaDeUnMovimiento("Transferencia a la cuenta *41279033068"),
        )
    }

    @Test
    fun `un retiro se reconoce por el cajero`() {
        assertEquals("cajero:sucvivapal1", huellaDeUnMovimiento("Retiro en cajero SUCVIVAPAL1"))
    }

    /** Esto es lo que hace que la memoria sirva: el arranque del banco no es parte del nombre. */
    @Test
    fun `el mismo comercio se reconoce venga con el arranque del banco o sin el`() {
        val conArranque = huellaDeUnMovimiento("Pago QR Mora Soccer")
        assertEquals("nombre:morasoccer", conArranque)
        assertEquals(conArranque, huellaDeUnMovimiento("Mora Soccer"))
        assertEquals(conArranque, huellaDeUnMovimiento("Compra en MORA SOCCER"))
    }

    @Test
    fun `las tildes y la puntuacion no parten un comercio en dos`() {
        assertEquals(
            huellaDeUnMovimiento("Cafe 9 3 Bookstore"),
            huellaDeUnMovimiento("Café 9 3 Bookstore"),
        )
        assertEquals(huellaDeUnMovimiento("McDonalds"), huellaDeUnMovimiento("McDonald's"))
    }

    /**
     * **Lo más importante de todo el archivo.** Si «Pago QR» a secas tuviera huella, la primera
     * categoría que el dueño eligiera para un pago por QR se le propondría a todos los demás — y
     * un pago por QR puede ser el almuerzo o el arriendo.
     */
    @Test
    fun `un texto que no identifica a nadie no tiene huella`() {
        assertNull(huellaDeUnMovimiento("Pago QR"))
        assertNull(huellaDeUnMovimiento("Transferencia"))
        assertNull(huellaDeUnMovimiento("Movimiento"))
        assertNull(huellaDeUnMovimiento("Retiro"))
        assertNull(huellaDeUnMovimiento(""))
    }

    @Test
    fun `la huella dice si el banco la escribio con numero o con nombre`() {
        assertTrue(laHuellaEsUnNumero(assertNotNull(huellaDeUnMovimiento("Pago QR (llave 0092184713)"))))
        assertTrue(!laHuellaEsUnNumero(assertNotNull(huellaDeUnMovimiento("Zelo Group"))))
    }

    // ── Lo que Movi recuerda ─────────────────────────────────────────────────

    @Test
    fun `propone la categoria con la que el dueno anoto ese mismo comercio`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                anotacion("Pago QR Mora Soccer", "Fútbol"),
                anotacion("McDonald's", "Comida"),
            ),
        )

        val recuerdo = assertNotNull(memoria.recuerdoDe("Pago QR MORA SOCCER"))
        assertEquals("Fútbol", recuerdo.categoria)
        assertEquals(1, recuerdo.cuantos)
    }

    @Test
    fun `entre dos historias gana la que mas veces uso, y el empate lo rompe la mas reciente`() {
        val porCantidad = MemoriaDeCategorias.de(
            listOf(
                anotacion("Café 9 3 Bookstore", "Comida", cuando = 30),
                anotacion("Café 9 3 Bookstore", "Hija", cuando = 10),
                anotacion("Café 9 3 Bookstore", "Hija", cuando = 20),
            ),
        )
        assertEquals("Hija", assertNotNull(porCantidad.recuerdoDe("Café 9 3 Bookstore")).categoria)

        val porFecha = MemoriaDeCategorias.de(
            listOf(
                anotacion("Café 9 3 Bookstore", "Comida", cuando = 10),
                anotacion("Café 9 3 Bookstore", "Hija", cuando = 20),
            ),
        )
        assertEquals("Hija", assertNotNull(porFecha.recuerdoDe("Café 9 3 Bookstore")).categoria)
    }

    /**
     * El dueño no puede leer «llave 0092184713». Si él ya le puso nombre a ese destinatario una
     * vez, el nombre es suyo y es el que tiene que volver.
     */
    @Test
    fun `se acuerda del nombre que el dueno le puso a una llave`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                AnotacionPasada(
                    comoLlego = "Pago QR (llave 0092184713)",
                    nombre = "Panadería de la 33",
                    categoria = "Comida",
                    cuando = 10,
                ),
            ),
        )

        val recuerdo = assertNotNull(memoria.recuerdoDe("Pagaste \$9.000 por codigo QR a la llave 0092184713"))
        assertEquals("Panadería de la 33", recuerdo.nombre)
        assertEquals("Comida", recuerdo.categoria)
    }

    /** Un ajuste de saldo o un traspaso no dicen en qué se fue la plata: proponerlos sería peor. */
    @Test
    fun `no aprende de las categorias que pone Movi sola`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                anotacion("Ajuste al saldo del banco", ADJUSTMENT_CATEGORY),
                anotacion("Techo Gardenera", TRANSFER_CATEGORY),
                anotacion("Saldo con el que arrancó", OPENING_CATEGORY),
            ),
        )
        assertEquals(0, memoria.cuantasHuellas)
    }

    /** «Otro» es la ausencia de categoría. Aprenderla la volvería permanente. */
    @Test
    fun `no aprende Otro ni Otros`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(anotacion("Zelo Group", "Otro"), anotacion("Retiro en cajero SUCVIVAPAL1", "Otros")),
        )
        assertNull(memoria.recuerdoDe("Zelo Group"))
        assertNull(memoria.recuerdoDe("Retiro en cajero SUCVIVAPAL1"))
    }

    @Test
    fun `sin historia no recuerda nada, y eso no es un error`() {
        assertNull(MemoriaDeCategorias.vacia.recuerdoDe("Zelo Group"))
        assertNull(MemoriaDeCategorias.de(emptyList()).recuerdoDe("Pago QR Mora Soccer"))
    }

    // ── La red de seguridad ──────────────────────────────────────────────────

    /**
     * La tabla vieja devolvía «Restaurantes», «Mercado» y «Suscripción»: tres categorías que no
     * están en ningún selector de la app ni en los datos del dueño. Cada SMS confirmado abría una
     * categoría nueva de un solo movimiento.
     */
    @Test
    fun `la red de seguridad solo habla el vocabulario de la app`() {
        val nombresDeLaApp = PREDEFINED_CATEGORIES.map { it.name }.toSet()
        val ejemplos = listOf(
            "Uber BV", "Crepes & Waffles", "Éxito Poblado", "Drogas La Rebaja",
            "Netflix", "Claro Colombia", "Anthropic",
        )
        ejemplos.forEach { comercio ->
            val categoria = assertNotNull(categoriaProbablePorElNombre(comercio), "sin categoría: $comercio")
            assertTrue(categoria in nombresDeLaApp, "«$categoria» no existe en la app")
        }
    }

    @Test
    fun `la red de seguridad no adivina cuando no sabe`() {
        assertNull(categoriaProbablePorElNombre("Zelo Group"))
        assertNull(categoriaProbablePorElNombre("Mora Soccer"))
    }
}
