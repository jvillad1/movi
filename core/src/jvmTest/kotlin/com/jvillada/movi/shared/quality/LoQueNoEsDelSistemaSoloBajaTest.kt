package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # Lo que no es del sistema solo puede bajar
 *
 * Vuelta de diseño 18 (27-oct-2026). Un revisor independiente auditó master buscando lo que todavía
 * no usa el sistema de diseño y **ninguna guarda cuenta**: los `.dp`, los radios y los `fontSize`
 * ya tenían su trinquete ([LosValoresSueltosSoloBajanTest], [LosTamanosSueltosSoloBajanTest]), pero
 * un botón de Material, un `Color.White` o un monto pintado con `Text(formatCOP(…))` podían volver
 * a entrar sin que nada avisara. Esta prueba es el mismo trinquete para seis cosas más, contadas en
 * `ui/` fuera de `ui/components/ds/` (el sistema mismo), con la misma lectura sin comentarios
 * ([LecturaDelCodigo]) y la misma regla que `scripts/auditoria-de-diseno.py --resumen`:
 *
 * | clave | qué cuenta | qué va en su lugar |
 * |---|---|---|
 * | `botones_de_material` | `Button(` / `OutlinedButton(` / `TextButton(` | `Boton` (`Principal`, `Secundario`, `Fantasma`, `Peligro`) |
 * | `blanco_o_negro` | `Color.White` / `Color.Black` | un token de `Movi.colores` (`sobreMarca`, `sobreAvatar`…) |
 * | `hairline` | `Hairline(` | `Separador` |
 * | `mincard` | `MinCard(` | `Superficie` |
 * | `cifra_en_text` | un `Text(` cuyo primer argumento empieza con `formatCOP(` o `formatMoney(` | `Cifra` |
 * | `font_weight` | `fontWeight =` | el estilo de `Movi.textos` que ya trae su peso, o un componente |
 *
 * `cifra_en_text` es **una aproximación por regex**: ve `Text(formatCOP(x))` y
 * `Text(\n text = formatMoney(x, m),` aunque el argumento baje de renglón, pero no un monto armado
 * con algo delante (`Text("+" + formatMoney(…))`) ni uno que llega en una variable. Lo que no ve no
 * lo deja pasar: solo no lo cuenta. La definición de una función (`fun Hairline(`) no cuenta.
 *
 * **Excepciones, con nombre** (y la prueba falla si una deja de hacer falta):
 *
 * - `blanco_o_negro` en `ui/components/MarcoDeHoja.kt`: el velo detrás de una hoja es negro con
 *   alfa en los dos temas —oscurece lo de abajo, no es un color del tema—.
 *
 * Las líneas base viven en `docs/diseno/lineas-base.txt`, junto a las de los valores sueltos.
 *
 * **Si te falló porque el número subió**: usa el componente o el token de la tabla.
 * **Si falló porque bajó** —migraste algo—: baja la línea base al número nuevo en el mismo cambio.
 */
class LoQueNoEsDelSistemaSoloBajaTest {

    private val detectores: Map<String, Regex> = linkedMapOf(
        "botones_de_material" to Regex("""(?<![\w.])(?:Button|OutlinedButton|TextButton)\("""),
        "blanco_o_negro" to Regex("""\bColor\.(?:White|Black)\b"""),
        "hairline" to Regex("""(?<![\w.])Hairline\("""),
        "mincard" to Regex("""(?<![\w.])MinCard\("""),
        "cifra_en_text" to Regex("""(?<![\w.])Text\(\s*(?:text\s*=\s*)?(?:formatCOP|formatMoney)\("""),
        "font_weight" to Regex("""\bfontWeight\s*="""),
    )

    /** Por clave, los archivos (relativos a `ui/`) que no se cuentan. Ver el KDoc. */
    private val excepciones: Map<String, Set<String>> = mapOf(
        "blanco_o_negro" to setOf("components/MarcoDeHoja.kt"),
    )

    private val definicion = Regex("""fun\s+$""")

    /** Las coincidencias de [regex] en [texto] como números de renglón (1…), sin las definiciones. */
    private fun renglones(texto: String, regex: Regex): List<Int> =
        regex.findAll(texto)
            .filterNot { definicion.containsMatchIn(texto.substring(maxOf(0, it.range.first - 8), it.range.first)) }
            .map { m -> texto.substring(0, m.range.first).count { it == '\n' } + 1 }
            .toList()

    private fun conteos(archivos: Map<String, String>): Map<String, List<String>> =
        detectores.mapValues { (clave, regex) ->
            val fuera = excepciones[clave].orEmpty()
            archivos
                .filterKeys { ruta -> fuera.none { ruta.endsWith("ui/$it") } }
                .flatMap { (ruta, texto) -> renglones(texto, regex).map { "$ruta:$it" } }
        }

    private fun conteos(): Map<String, List<String>> =
        conteos(LecturaDelCodigo.archivos(LecturaDelCodigo.UI, LecturaDelCodigo.DS))

    private fun lineasBase(): Map<String, Int> {
        val archivo = File(LecturaDelCodigo.raiz(), "docs/diseno/lineas-base.txt")
        if (!archivo.isFile) fail("No encontré docs/diseno/lineas-base.txt")
        return archivo.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .associate { linea ->
                val (clave, valor) = linea.split('=').map { it.trim() }
                clave to valor.toInt()
            }
    }

    private fun peores(donde: List<String>): String =
        donde.groupingBy { it.substringBeforeLast(':') }.eachCount()
            .entries.sortedByDescending { it.value }.take(8)
            .joinToString("\n") { "  ${it.value} en ${it.key}" }

    @Test
    fun `no sube lo que no es del sistema`() {
        val base = lineasBase()
        val fallas = conteos().mapNotNull { (clave, donde) ->
            val tope = base[clave] ?: fail("docs/diseno/lineas-base.txt no tiene «$clave»")
            if (donde.size > tope) {
                "«$clave» subió a ${donde.size} (la línea base es $tope). Usa el componente o el token " +
                    "del sistema (ver el KDoc de esta prueba). Donde más hay:\n" + peores(donde)
            } else {
                null
            }
        }
        if (fallas.isNotEmpty()) fail(fallas.joinToString("\n\n"))
    }

    @Test
    fun `la linea base no queda floja cuando se migra`() {
        val base = lineasBase()
        val flojas = conteos().mapNotNull { (clave, donde) ->
            val tope = base[clave] ?: return@mapNotNull null
            if (donde.size < tope) "«$clave»: quedan ${donde.size} y la línea base dice $tope — bájala a ${donde.size}" else null
        }
        assertTrue(flojas.isEmpty(), "Migraste algo al sistema: baja la línea base en docs/diseno/lineas-base.txt.\n" + flojas.joinToString("\n"))
    }

    /** Una excepción que ya no tiene nada que exceptuar es una puerta abierta: se borra. */
    @Test
    fun `cada excepcion sigue haciendo falta`() {
        val archivos = LecturaDelCodigo.archivos(LecturaDelCodigo.UI, LecturaDelCodigo.DS)
        val sobran = excepciones.flatMap { (clave, rutas) ->
            rutas.filter { ruta ->
                val texto = archivos.entries.firstOrNull { it.key.endsWith("ui/$ruta") }?.value
                texto == null || renglones(texto, detectores.getValue(clave)).isEmpty()
            }.map { "$clave: $it" }
        }
        assertTrue(sobran.isEmpty(), "Excepciones que ya no hacen falta (bórralas de la prueba):\n" + sobran.joinToString("\n"))
    }

    /** Un detector que siempre da cero no se distingue de uno roto: esto le muestra lo que debe ver. */
    @Test
    fun `los detectores ven lo que tienen que ver y no ven los comentarios`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            Button(onClick = {}) { Text("Ok") }  // un TextButton( en un comentario no cuenta
            OutlinedButton(onClick = {}) {}
            TextButton(onClick = {}) {}
            IconButton(onClick = {}) {}
            Boton("Guardar", onClick = {})
            Text("a", color = Color.White, fontWeight = FontWeight.Bold)
            drawRect(Color.Black)
            /* ni Color.White
               ni Hairline() acá */
            Hairline()
            fun Hairline() {}
            MinCard(modifier = Modifier) { }
            Text(formatCOP(1))
            Text(
                text = formatMoney(1, "USD"),
                style = Movi.textos.monto,
            )
            Text("+" + formatMoney(1, "COP"))
            Cifra(1)
            Text(formatCOPSinOcultar(1))
            """.trimIndent(),
        )
        val c = conteos(mapOf("shared/ui/Prueba.kt" to codigo))
        assertEquals(3, c.getValue("botones_de_material").size, "botones")
        assertEquals(2, c.getValue("blanco_o_negro").size, "blanco o negro")
        assertEquals(1, c.getValue("hairline").size, "hairline")
        assertEquals(1, c.getValue("mincard").size, "mincard")
        assertEquals(listOf("shared/ui/Prueba.kt:13", "shared/ui/Prueba.kt:14"), c.getValue("cifra_en_text"), "cifra en text")
        assertEquals(1, c.getValue("font_weight").size, "fontWeight")
    }

    /** La excepción del velo solo cubre su archivo, y solo su clave. */
    @Test
    fun `una excepcion cubre su archivo y su clave, nada mas`() {
        val velo = "drawRect(Color.Black)\nText(\"a\", fontWeight = FontWeight.Bold)"
        val c = conteos(
            mapOf(
                "shared/src/commonMain/kotlin/com/jvillada/movi/ui/components/MarcoDeHoja.kt" to velo,
                "shared/src/commonMain/kotlin/com/jvillada/movi/ui/otra/Pantalla.kt" to velo,
            ),
        )
        assertEquals(1, c.getValue("blanco_o_negro").size, "el velo no cuenta; el mismo negro en otra pantalla sí")
        assertEquals(2, c.getValue("font_weight").size, "la excepción no tapa otras claves")
    }
}
