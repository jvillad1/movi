package com.jvillada.movi.ui.ai

import com.jvillada.movi.ui.ai.BloqueDeTexto.Lista
import com.jvillada.movi.ui.ai.BloqueDeTexto.Parrafo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El formato de las respuestas de Movi AI (vuelta de diseño 12)
 *
 * Párrafos, listas, negrita y cifras; y lo raro, tal cual. Los textos de las respuestas sin IA son
 * los que arma el server (`LasPreguntasSugeridas.kt`), con su forma de verdad.
 */
class TextoConFormatoTest {

    private fun texto(bloque: BloqueDeTexto): String = when (bloque) {
        is Parrafo -> textoDe(bloque.tramos)
        is Lista -> bloque.items.joinToString("|") { "${it.marca} ${textoDe(it.tramos)}" }
    }

    @Test
    fun `parrafos separados por un renglon vacio`() {
        val bloques = bloquesDeTexto("Este período entró \$5.000.000.\n\nTe quedan \$1.200.000 para 9 días.")
        assertEquals(2, bloques.size)
        assertTrue(bloques.all { it is Parrafo })
        assertEquals("Te quedan \$1.200.000 para 9 días.", texto(bloques[1]))
    }

    @Test
    fun `un salto simple se respeta dentro del parrafo`() {
        val bloques = bloquesDeTexto("Primera línea\nSegunda línea")
        assertEquals(listOf("Primera línea\nSegunda línea"), bloques.map(::texto))
    }

    @Test
    fun `la lista del server pegada a su frase es una lista con vinetas`() {
        val respuesta = "Te faltan 2 pagos del período por \$2.000.000:\n" +
            "- Arriendo \$1.800.000 · venció hace 22 días\n" +
            "- Internet \$200.000 · vence mañana"
        val bloques = bloquesDeTexto(respuesta)
        assertEquals(2, bloques.size)
        assertEquals("Te faltan 2 pagos del período por \$2.000.000:", texto(bloques[0]))
        val lista = bloques[1] as Lista
        assertEquals(false, lista.numerada)
        assertEquals(listOf(VINETA, VINETA), lista.items.map { it.marca })
        assertEquals("Arriendo \$1.800.000 · venció hace 22 días", textoDe(lista.items[0].tramos))
        assertTrue(lista.items.none { textoDe(it.tramos).startsWith("-") }, "sin el guion crudo")
    }

    @Test
    fun `la lista numerada conserva sus numeros`() {
        val bloques = bloquesDeTexto("Lo que más pesa:\n1. Vivienda \$1.500.000\n2. Mercado \$392.100")
        val lista = bloques[1] as Lista
        assertTrue(lista.numerada)
        assertEquals(listOf("1.", "2."), lista.items.map { it.marca })
        assertEquals("Mercado \$392.100", textoDe(lista.items[1].tramos))
    }

    @Test
    fun `asterisco y punto tambien son vinetas, y una sublista lleva nivel 1`() {
        val bloques = bloquesDeTexto("* Uno\n• Dos\n  - Dos a\n- Tres")
        val lista = bloques.single() as Lista
        assertEquals(listOf(0, 0, 1, 0), lista.items.map { it.nivel })
        assertEquals(listOf("Uno", "Dos", "Dos a", "Tres"), lista.items.map { textoDe(it.tramos) })
    }

    @Test
    fun `un renglon con sangria continua el item de arriba`() {
        val lista = bloquesDeTexto("- Arriendo\n  vence mañana").single() as Lista
        assertEquals("Arriendo vence mañana", textoDe(lista.items.single().tramos))
    }

    @Test
    fun `una lista con vinetas despues de una numerada es otra lista`() {
        val bloques = bloquesDeTexto("1. Uno\n2. Dos\n- Suelto")
        assertEquals(listOf(true, false), bloques.map { (it as Lista).numerada })
    }

    @Test
    fun `la negrita sale sin asteriscos`() {
        val tramos = tramosDe("Vas **bien** este período")
        assertEquals("Vas bien este período", textoDe(tramos))
        assertEquals(listOf(false, true, false), tramos.map { it.negrita })
    }

    @Test
    fun `un titulo con almohadillas es un parrafo en negrita`() {
        val bloque = bloquesDeTexto("## Resumen").single() as Parrafo
        assertEquals("Resumen", textoDe(bloque.tramos))
        assertTrue(bloque.tramos.all { it.negrita })
    }

    @Test
    fun `las cifras en pesos se reconocen y lo demas no`() {
        val tramos = tramosDe("Pagaste \$1.800.000 el 25 de 2026, US\$1.200 y -\$23.388; quedan \$11,7M y \$500")
        assertEquals(listOf("\$1.800.000", "US\$1.200", "-\$23.388", "\$11,7M", "\$500"), tramos.filter { it.cifra }.map { it.texto })
        assertTrue(tramos.filter { it.cifra }.none { "2026" in it.texto || "25" == it.texto }, "un día o un año no son plata")
        assertEquals("Pagaste \$1.800.000 el 25 de 2026, US\$1.200 y -\$23.388; quedan \$11,7M y \$500", textoDe(tramos))
    }

    @Test
    fun `una cifra en negrita es las dos cosas`() {
        val tramo = tramosDe("**\$2.877.000**").single()
        assertTrue(tramo.negrita && tramo.cifra)
    }

    @Test
    fun `lo raro se ve tal cual`() {
        listOf(
            "Esto **no cierra",
            "Un ** suelto ** y nada más",
            "<b>hola</b> y [un enlace](https://example.com) y ![foto](x.png)",
            "| a | b |\n|---|---|",
            "---",
            "-\$23.388 de ajuste",
        ).forEach { raro ->
            val bloques = bloquesDeTexto(raro)
            val todo = bloques.joinToString("\n") { texto(it) }
            assertEquals(raro, todo, "«$raro» tiene que verse igual")
            assertTrue(bloques.all { it is Parrafo }, "«$raro» no es una lista")
        }
    }

    @Test
    fun `nada vacio, y un texto en blanco no da bloques`() {
        assertEquals(emptyList(), bloquesDeTexto(""))
        assertEquals(emptyList(), bloquesDeTexto("\n\n  \n"))
        assertTrue(tramosDe("**Hola**").none { it.texto.isEmpty() })
    }

    @Test
    fun `la respuesta de que revisar del server`() {
        val respuesta = "Revisa esto, en este orden:\n" +
            "1. «Arriendo» venció y no está marcado. Si ya lo pagaste, anótalo y Movi lo tilda solo en Plan.\n" +
            "2. Tienes 2 pagos del banco por revisar. Hasta confirmarlos no cuentan en el gasto del período: ábrelos en Por revisar.\n" +
            "\nY 1 cosa más en «Para revisar», en Hoy."
        val bloques = bloquesDeTexto(respuesta)
        assertEquals(listOf("Parrafo", "Lista", "Parrafo"), bloques.map { it::class.simpleName })
        assertEquals(2, (bloques[1] as Lista).items.size)
    }
}
