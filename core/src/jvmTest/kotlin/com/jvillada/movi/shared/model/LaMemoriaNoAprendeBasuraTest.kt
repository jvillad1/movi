package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 85 · la memoria no aprende basura, no cruza lo que entra con lo que sale, y conoce marcas
 *
 * El caso de la revisión 9: «Recibiste $539.625 de la cuenta *31234567890 en tu cuenta *4057 el
 * 30/10/2026 a las 13:05» (Andrés) se confirmó con la cola del aviso como nombre y «Transferencia» como
 * categoría; la memoria lo guardó bajo `cuenta:31234567890`, y el envío a Andrés («Transferencia a
 * Andrés Rojas · Otros») pasó a llamarse igual, con «Transferencia» («Así lo anotaste la última vez»).
 */
class LaMemoriaNoAprendeBasuraTest {

    private val colaDelAviso = "la cuenta *31234567890 en tu cuenta *4057 el 30/10/2026 a las 13:05"

    private fun anotacion(
        comoLlego: String,
        categoria: String,
        nombre: String = comoLlego,
        tipo: TransactionType? = TransactionType.EXPENSE,
        cuando: Long = 0,
    ) = AnotacionPasada(comoLlego = comoLlego, nombre = nombre, categoria = categoria, cuando = cuando, tipo = tipo)

    // ── Lo que no se aprende ─────────────────────────────────────────────────────

    @Test
    fun `un texto con tu cuenta o con una fecha no se aprende`() {
        assertTrue(textoQueNoSeAprende(colaDelAviso))
        assertTrue(textoQueNoSeAprende("PERSONA DE PRUEBA en tu cuenta *3333"))
        assertTrue(textoQueNoSeAprende("Pago el 2026/10/01"))
        assertTrue(textoQueNoSeAprende("Mercado 30/10/26"))
        assertFalse(textoQueNoSeAprende("Transferencia a la cuenta *41279033068"), "el texto del banco de un envío sí deja huella")
        assertFalse(textoQueNoSeAprende("Mora Soccer"))
    }

    @Test
    fun `cuenta seguida de digitos no se propone como nombre, pero un nombre de verdad si`() {
        assertTrue(nombreQueNoSePropone("Transferencia a la cuenta *41279033068"))
        assertTrue(nombreQueNoSePropone("La Cuenta 31234567890"))
        assertTrue(nombreQueNoSePropone(colaDelAviso))
        assertFalse(nombreQueNoSePropone("Transferencia a Andrés Rojas"))
        assertFalse(nombreQueNoSePropone("Panadería de la 33"))
    }

    @Test
    fun `lo ya contaminado no vuelve a proponerse`() {
        // Un gasto que se confirmó con el nombre contaminado: ni su nombre ni su categoría quedan.
        val memoria = MemoriaDeCategorias.de(
            listOf(anotacion("Transferencia a la cuenta *31234567890", "Transferencia", nombre = colaDelAviso)),
        )
        assertNull(memoria.recuerdoDe("Transferencia a la cuenta *31234567890"))
        assertEquals(0, memoria.cuantasHuellas)
    }

    @Test
    fun `una cuenta sin nombre aprende la categoria pero no propone su numero como nombre`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                anotacion("Transferencia a la cuenta *41279033068", "Arriendo", cuando = 1),
                anotacion("Transferencia a la cuenta *41279033068", "Arriendo", nombre = "Arriendo apto", cuando = 2),
                anotacion("Transferencia a la cuenta *41279033068", "Arriendo", cuando = 3),
            ),
        )
        val recuerdo = assertNotNull(memoria.recuerdoDe("Transferencia a la cuenta *41279033068"))
        assertEquals("Arriendo", recuerdo.categoria)
        assertEquals("Arriendo apto", recuerdo.nombre, "el más reciente que se puede proponer, no el número")
    }

    // ── Lo que entra y lo que sale ───────────────────────────────────────────────

    @Test
    fun `un ingreso de Andres no le ensena nada al envio a Andres`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                // Lo que entró de Andrés, ya con un nombre limpio: misma huella de nombre que lo que sale.
                anotacion("Transferencia de Andrés Rojas", "Transferencia", tipo = TransactionType.INCOME, cuando = 5),
                anotacion("Transferencia de *7890", "Transferencia", tipo = TransactionType.INCOME, cuando = 5),
                // Lo que salió hacia él.
                anotacion("Transferencia a Andrés Rojas", "Otros", cuando = 1),
                anotacion("Transferencia a la cuenta *31234567890", "Vivienda", nombre = "Transferencia a Andrés Rojas", cuando = 2),
            ),
        )
        val salida = assertNotNull(memoria.recuerdoDe("Transferencia a la cuenta *31234567890", TransactionType.EXPENSE))
        assertEquals("Vivienda", salida.categoria)
        assertEquals("Transferencia a Andrés Rojas", salida.nombre)
        assertNull(memoria.recuerdoDe("Transferencia a Andrés Rojas"), "«Otros» no se aprende, y el ingreso no la llena")

        val entrada = assertNotNull(memoria.recuerdoDe("Transferencia de Andrés Rojas", TransactionType.INCOME))
        assertEquals("Transferencia", entrada.categoria)
        assertNull(memoria.recuerdoDe("Transferencia de *1111", TransactionType.INCOME))
    }

    @Test
    fun `sin decir el tipo se busca en lo que sale, como antes`() {
        val memoria = MemoriaDeCategorias.de(listOf(anotacion("Mora Soccer", "Fútbol", tipo = null)))
        assertEquals("Fútbol", memoria.recuerdoDe("Pago QR Mora Soccer")?.categoria)
        assertNull(memoria.recuerdoDe("Pago QR Mora Soccer", TransactionType.INCOME))
    }

    @Test
    fun `las entradas que viajan dicen cuales son de ingresos, y un cliente viejo las lee`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                anotacion("Mora Soccer", "Fútbol"),
                anotacion("Transferencia de Caro", "Salario", tipo = TransactionType.INCOME),
            ),
        )
        val entradas = memoria.entradas()
        assertEquals(listOf(false, true), entradas.map { it.ingreso }, "primero lo que sale")
        val json = Json { ignoreUnknownKeys = true }
        val viejo = json.decodeFromString(RecuerdoDeCategoria.serializer(), """{"huella":"nombre:x","categoria":"C","nombre":"X","cuantos":1}""")
        assertFalse(viejo.ingreso)
    }

    // ── La marca ────────────────────────────────────────────────────────────────

    @Test
    fun `RAPPI COLOMBIA toma la categoria de los Rappi anotados`() {
        val rappis = (1..25).map { anotacion("RAPPI", "Restaurantes", nombre = "Rappi", cuando = it.toLong()) }
        val memoria = MemoriaDeCategorias.de(rappis + anotacion("Uber", "Transporte"))
        assertEquals("nombre:rappicolombia", huellaDeUnMovimiento("RAPPI COLOMBIA"), "la huella exacta no es la de «Rappi»")

        val recuerdo = assertNotNull(memoria.recuerdoDe("RAPPI COLOMBIA"))
        assertEquals("Restaurantes", recuerdo.categoria)
        assertEquals(25, recuerdo.cuantos)
    }

    @Test
    fun `la huella exacta le gana a la marca`() {
        val memoria = MemoriaDeCategorias.de(
            (1..3).map { anotacion("RAPPI", "Restaurantes", cuando = it.toLong()) } + anotacion("RAPPI COLOMBIA", "Mercado", cuando = 9),
        )
        assertEquals("Mercado", memoria.recuerdoDe("RAPPI COLOMBIA")?.categoria)
    }

    @Test
    fun `un banco no es una marca para la memoria, ni un ingreso busca por marca`() {
        assertNull(marcaDeComercioDe("Bancolombia"))
        assertEquals("rappi", marcaDeComercioDe("RAPPI COLOMBIA"))
        val memoria = MemoriaDeCategorias.de(listOf(anotacion("RAPPI", "Restaurantes", tipo = TransactionType.INCOME)))
        assertNull(memoria.recuerdoDe("RAPPI COLOMBIA", TransactionType.INCOME), "lo que entra solo por huella")
        assertNull(memoria.recuerdoDe("RAPPI COLOMBIA"), "y lo que entró no le enseña a lo que sale")
    }
}
