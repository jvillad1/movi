package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Todo lo que se toca en el sistema dibuja el anillo de foco (vuelta 55, accesibilidad)
 *
 * La revisión de accesibilidad del 28-oct navegó la web con Tab y el foco casi no se veía. Desde la
 * vuelta 55 cada componente de `ui/components/ds/` que se toca —y las pestañas, el avatar y el
 * segmentado, que viven al lado— lleva `Modifier.anilloDeFoco` en la misma cadena que su `clickable`,
 * `toggleable` o `selectable`. Esta guarda está en **cero**: un toque nuevo sin anillo en esos archivos
 * la rompe. El anillo va en la misma cadena que el toque, antes del `clip`, que es donde tiene que ir
 * (ver `sinAnillo`).
 */
class TodoLoEnfocableDibujaElAnilloTest {

    private val TOPE = 30

    private val toque = Regex("""\.(?:clickable|toggleable|selectable|combinedClickable)\s*[({]""")

    private val vecinos = listOf(
        "MinBottomNav.kt",
        "MinNavRail.kt",
        "AvatarButton.kt",
        "SelectorSegmentado.kt",
    ).map { "${LecturaDelCodigo.UI}/components/$it" }

    /**
     * Los toques que no tienen un `anilloDeFoco(` en **su misma cadena** de modifiers, como
     * `archivo:línea`. La cadena se recorre hacia arriba desde el toque: los renglones que empiezan
     * con `.`, lo que está dentro de un `.then(…)` de varios renglones (se cuentan paréntesis y
     * llaves) y, al final, la cabeza (`modifier = modifier`, `Modifier`).
     */
    private fun sinAnillo(archivos: Map<String, String>): List<String> =
        archivos.flatMap { (ruta, texto) ->
            val lineas = texto.lines()
            lineas.mapIndexedNotNull { n, linea ->
                // `clickable(enabled = false) {}` es un tapón (no se enfoca): no lleva anillo.
                if (!toque.containsMatchIn(linea) || "enabled = false)" in linea) return@mapIndexedNotNull null
                if (cadenaDe(lineas, n).any { "anilloDeFoco(" in it }) null else "$ruta:${n + 1}"
            }
        }

    private fun cadenaDe(lineas: List<String>, n: Int): List<String> {
        val antesDelToque = lineas[n].substringBefore(".clickable").substringBefore(".toggleable").substringBefore(".selectable")
        val cadena = mutableListOf(antesDelToque)
        // `Modifier.size(…).toggleable(…)`: el toque está en la cabeza misma; la cadena es ese renglón.
        if (!lineas[n].trim().startsWith(".")) return cadena
        var abiertos = 0
        var i = n - 1
        while (i >= 0 && n - i <= TOPE) {
            val l = lineas[i]
            cadena += l
            abiertos += l.count { it == ')' || it == '}' } - l.count { it == '(' || it == '{' }
            val t = l.trim()
            if (abiertos <= 0 && t.isNotEmpty() && !t.startsWith(".")) break
            i--
        }
        return cadena
    }

    @Test
    fun `cada toque de ds y de las pestanas lleva el anillo de foco`() {
        val raiz = LecturaDelCodigo.raiz()
        val archivos = LecturaDelCodigo.archivos(LecturaDelCodigo.DS) +
            vecinos.associateWith { LecturaDelCodigo.sinComentarios(raiz.resolve(it).readText()) }
        assertTrue(archivos.size > vecinos.size, "No encontré ${LecturaDelCodigo.DS}")
        val fallas = sinAnillo(archivos)
        assertTrue(
            fallas.isEmpty(),
            "Componentes que se tocan sin anillo de foco (pon `.anilloDeFoco(forma)` antes del clip y " +
                "del toque, ver ds/AnilloDeFoco.kt):\n" + fallas.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `el detector ve un toque sin anillo y deja pasar uno con anillo`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            Modifier
                .anilloDeFoco(forma)
                .clip(forma)
                .clickable(onClick = onClick)
            Modifier.size(Icono.m).toggleable(value = v, onValueChange = f)
            Modifier.clickable(enabled = false) {}
            // .clickable(en un comentario no cuenta)
            """.trimIndent(),
        )
        assertEquals(listOf("Prueba.kt:5"), sinAnillo(mapOf("Prueba.kt" to codigo)))
    }
}
