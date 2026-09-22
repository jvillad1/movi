package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # Los tamaños de letra sueltos solo pueden bajar
 *
 * El rediseño puso una escala de texto (`Movi.textos.*` en `theme/Tokens.kt`: titular, título,
 * cuerpo, monto, apoyo, rótulo…) y se fue pasando pantalla por pantalla. Al 19-sep-2026 quedaban
 * **184** `fontSize = N.sp` escritos a mano en 40 archivos de `ui/` — pantallas secundarias y hojas.
 *
 * Nada impedía que volvieran a crecer: cada pantalla nueva que alguien escribía copiando una vieja
 * traía sus tamaños sueltos, y el número de arriba se movía para el lado equivocado sin que nadie
 * lo viera. Esta prueba es un **trinquete**: falla si hay MÁS que [TOPE], y cuando se migran
 * algunos, el tope se baja a mano en el mismo PR. Así el número solo puede ir a cero.
 *
 * ### El punto ciego que tenía
 *
 * Solo contaba `fontSize = N`. Un componente que recibía el tamaño crudo —`Cifra(texto, 14.5f)`,
 * que por dentro hacía `fontSize.sp`— escondía sus tamaños de la cuenta: al 21-sep-2026 había
 * **14** llamadas así, con seis tamaños distintos para tres papeles. Ahora también cuenta, en cada
 * archivo, las llamadas que le pasan un número (`12f`, `14.5f`) a un componente de `ui/` que tenga
 * un parámetro `Float` y lo convierta a `sp`. `Cifra` ya recibe un estilo de la escala y no queda
 * ninguno de esos componentes; si alguien escribe otro, sus llamadas entran solas al tope.
 *
 * **Si esta prueba te falló** porque escribiste un `fontSize` nuevo, o un componente que recibe
 * el tamaño como `Float`: usa un estilo de la escala (`style = Movi.textos.cuerpo`, o un
 * `estilo: TextStyle` como parámetro, como hace `Cifra`). Si de verdad no hay ninguno que sirva
 * —una cifra gigante de una ilustración, por ejemplo—, sube el tope y explica en el PR por qué.
 *
 * **Si migraste tamaños y ahora hay menos**, baja [TOPE] al número nuevo: la prueba te lo pide,
 * porque un tope flojo deja que el siguiente vuelva a subir hasta él sin que nada avise.
 */
class LosTamanosSueltosSoloBajanTest {

    private companion object {
        /** Cuántos quedan. Bájalo cuando migres; nunca lo subas sin explicarlo en el PR. */
        const val TOPE = 17
    }

    private val sueltos = Regex("""fontSize\s*=\s*[0-9]""")

    /**
     * Un parámetro `Float` (no un `val`/`var` local). Si además el archivo lo convierte a `sp`
     * —`nombre.sp` o `(nombre * 1.33f).sp`—, la función que lo declara es un componente que recibe
     * el tamaño crudo.
     */
    private val parametroFloat = Regex("""(?<![\w.])(?<!val )(?<!var )(\w+)\s*:\s*Float\b""")

    /** Un número de coma flotante escrito a mano: `12f`, `14.5f`. */
    private val literalFloat = Regex("""(?<![\w.])[0-9]+(\.[0-9]+)?f\b""")

    private val declaracionDeFuncion = Regex("""\bfun\s+(?:[\w<>]+\.)?(\w+)\s*\(""")

    private fun raiz(): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "shared/src/commonMain/kotlin/com/jvillada/movi/ui").isDirectory }
            ?: fail("No encontré shared/src/commonMain/kotlin/com/jvillada/movi/ui subiendo desde user.dir")

    /** Los componentes que reciben el tamaño de letra como un `Float` crudo y lo pasan a `sp`. */
    private fun componentesConTamanoCrudo(fuentes: Collection<String>): Set<String> =
        fuentes.flatMap { texto ->
            parametroFloat.findAll(texto).mapNotNull { parametro ->
                val nombre = Regex.escape(parametro.groupValues[1])
                val llegaASp = Regex("""\b$nombre\s*\.sp\b|\(\s*$nombre\b[^()]*\)\s*\.sp\b""")
                    .containsMatchIn(texto)
                if (!llegaASp) {
                    null
                } else {
                    declaracionDeFuncion.findAll(texto.substring(0, parametro.range.first))
                        .lastOrNull()?.groupValues?.get(1)
                }
            }.toList()
        }.toSet()

    /**
     * Los argumentos de primer nivel de la llamada que abre en [abre] (el índice del `(`), sin
     * comentarios de línea: lo que va dentro de un paréntesis anidado —`Modifier.weight(1f)`— no es
     * un tamaño que se le pase al componente.
     */
    private fun argumentosDePrimerNivel(texto: String, abre: Int): String {
        val salida = StringBuilder()
        var profundidad = 0
        var i = abre
        while (i < texto.length) {
            val c = texto[i]
            if (c == '/' && texto.getOrNull(i + 1) == '/') {
                while (i < texto.length && texto[i] != '\n') i++
                continue
            }
            when (c) {
                '(', '{', '[' -> profundidad++
                ')', '}', ']' -> {
                    profundidad--
                    if (profundidad == 0) return salida.toString()
                }
                else -> if (profundidad == 1) salida.append(c)
            }
            i++
        }
        return salida.toString()
    }

    /** Cuántas llamadas en [texto] le pasan un número a alguno de los [componentes]. */
    private fun llamadasConTamanoCrudo(texto: String, componentes: Set<String>): Int =
        componentes.sumOf { componente ->
            Regex("""(?<![\w.])(fun\s+)?${Regex.escape(componente)}\s*\(""").findAll(texto).count { llamada ->
                llamada.groups[1] == null &&
                    literalFloat.containsMatchIn(argumentosDePrimerNivel(texto, llamada.range.last))
            }
        }

    private fun contar(): Map<String, Int> {
        val raiz = raiz()
        val archivos = File(raiz, "shared/src/commonMain/kotlin/com/jvillada/movi/ui")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(raiz).path to it.readText() }
        val componentes = componentesConTamanoCrudo(archivos.values)
        return archivos
            .mapValues { (_, texto) ->
                texto.lines().count { sueltos.containsMatchIn(it) } + llamadasConTamanoCrudo(texto, componentes)
            }
            .filterValues { it > 0 }
    }

    @Test
    fun `no aparecen tamanos de letra sueltos nuevos`() {
        val porArchivo = contar()
        val total = porArchivo.values.sum()
        assertTrue(
            total <= TOPE,
            "Hay $total tamaños de letra escritos a mano en ui/ (`fontSize = N.sp`, o un número " +
                "pasado a un componente que recibe el tamaño como Float) y el tope es $TOPE. Usa un " +
                "estilo de la escala (Movi.textos.*) en vez de un tamaño suelto.\n" +
                porArchivo.entries.sortedByDescending { it.value }.take(10)
                    .joinToString("\n") { "  ${it.value} en ${it.key}" },
        )
    }

    /** El trinquete solo sirve si el tope sigue al número: flojo, deja volver a subir sin avisar. */
    @Test
    fun `el tope no queda flojo cuando se migran tamanos`() {
        val total = contar().values.sum()
        assertTrue(
            total >= TOPE,
            "Quedan $total tamaños sueltos y el tope sigue en $TOPE: bájalo a $total en " +
                "LosTamanosSueltosSoloBajanTest, o el próximo que agregue uno no se entera.",
        )
    }

    /**
     * Hoy no hay ningún componente con tamaño crudo, así que la cuenta de llamadas da cero en el
     * código real — y un detector que siempre da cero no se distingue de uno roto. Esta prueba le
     * pasa la `Cifra` de antes y comprueba que la ve.
     */
    @Test
    fun `el detector ve un componente que recibe el tamano como Float`() {
        val componente = """
            @Composable
            fun Cifra(
                text: String,
                fontSize: Float,
                onClick: () -> Unit = {},
            ) {
                val ancho: Float = 3f
                Text(text, style = TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.33f).sp))
            }
            fun Barra(delta: Float): Float = delta
        """.trimIndent()
        val pantalla = """
            Cifra(formatCOP(total), 13.5f, color = Movi.colores.texto)
            Cifra(
                textoDelMonto(pago),
                // un comentario que dice 12f no cuenta
                if (pago.montoEsSaldo) 12.5f else 13.5f,
                color = Movi.colores.textoApagado,
            )
            Cifra(texto, Movi.textos.monto, modifier = Modifier.weight(1f))
            Barra(2f)
        """.trimIndent()

        val componentes = componentesConTamanoCrudo(listOf(componente, pantalla))
        assertEquals(setOf("Cifra"), componentes)
        assertEquals(2, llamadasConTamanoCrudo(pantalla, componentes))
        assertEquals(0, llamadasConTamanoCrudo(componente, componentes), "la declaración no es una llamada")
    }
}
