package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
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
 * **Si esta prueba te falló** porque escribiste un `fontSize` nuevo: usa un estilo de la escala
 * (`style = Movi.textos.cuerpo`, etc.). Si de verdad no hay ninguno que sirva —una cifra gigante de
 * una ilustración, por ejemplo—, sube el tope y explica en el PR por qué.
 *
 * **Si migraste tamaños y ahora hay menos**, baja [TOPE] al número nuevo: la prueba te lo pide,
 * porque un tope flojo deja que el siguiente vuelva a subir hasta él sin que nada avise.
 */
class LosTamanosSueltosSoloBajanTest {

    private companion object {
        /** Cuántos quedan. Bájalo cuando migres; nunca lo subas sin explicarlo en el PR. */
        const val TOPE = 81
    }

    private val sueltos = Regex("""fontSize\s*=\s*[0-9]""")

    private fun raiz(): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "shared/src/commonMain/kotlin/com/jvillada/movi/ui").isDirectory }
            ?: fail("No encontré shared/src/commonMain/kotlin/com/jvillada/movi/ui subiendo desde user.dir")

    private fun contar(): Map<String, Int> {
        val raiz = raiz()
        return File(raiz, "shared/src/commonMain/kotlin/com/jvillada/movi/ui")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { archivo ->
                archivo.relativeTo(raiz).path to archivo.readLines().count { sueltos.containsMatchIn(it) }
            }
            .filterValues { it > 0 }
    }

    @Test
    fun `no aparecen tamanos de letra sueltos nuevos`() {
        val porArchivo = contar()
        val total = porArchivo.values.sum()
        assertTrue(
            total <= TOPE,
            "Hay $total `fontSize = N.sp` escritos a mano en ui/ y el tope es $TOPE. Usa un estilo " +
                "de la escala (Movi.textos.*) en vez de un tamaño suelto.\n" +
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
}
