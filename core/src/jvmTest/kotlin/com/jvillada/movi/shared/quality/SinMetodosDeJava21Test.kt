package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * # Vuelta 101 · Nada de los métodos de lista que Android agregó en la versión 15
 *
 * La flecha ← de la barra de arriba tumbaba la app y el atrás del sistema no. La flecha hacía
 * `backStack.removeLast()`; el atrás, `removeAt(lastIndex)`. Compilado contra el SDK 35, un
 * `removeLast()` sobre un `MutableList` no es la extensión de Kotlin: es el método de Java 21
 * `java.util.List.removeLast()` (el `SnapshotStateList` de Compose no lo declara, así que la JVM lo busca
 * en la interfaz). Android lo agregó en la versión 15 (API 35): en un teléfono con Android 14 o anterior
 * la llamada lanza `NoSuchMethodError`. Las pruebas no lo ven —corren en la JVM 21 del computador, que
 * sí lo tiene—, por eso esta guarda mira el código.
 *
 * Lo mismo pasa con `removeFirst()`, `addFirst()`, `addLast()`, `getFirst()` y `getLast()`. En su lugar:
 * `removeAt(lastIndex)` / `removeAt(0)`, `removeLastOrNull()`, `add(0, x)`, `add(x)`, `first()`, `last()`.
 *
 * Mira todo lo que se compila para Android: `:shared` (commonMain, androidMain), `:core` (commonMain,
 * androidMain, nonWasmMain) y `:androidApp`, sin comentarios.
 */
class SinMetodosDeJava21Test {

    private val carpetas = listOf(
        "shared/src/commonMain",
        "shared/src/androidMain",
        "core/src/commonMain",
        "core/src/androidMain",
        "core/src/nonWasmMain",
        "androidApp/src/main",
    )

    private val llamada = Regex("""\.(removeFirst|removeLast|getFirst|getLast)\(\s*\)|\.(addFirst|addLast)\(""")

    @Test
    fun `nada que se compila para Android llama los metodos de lista de Java 21`() {
        val raiz = LecturaDelCodigo.raiz()
        val violaciones = carpetas
            .filter { File(raiz, it).isDirectory }
            .flatMap { carpeta ->
                LecturaDelCodigo.archivos(carpeta).flatMap { (ruta, codigo) ->
                    codigo.lines().mapIndexedNotNull { i, linea ->
                        llamada.find(linea)?.let { "$ruta:${i + 1}: ${linea.trim()}" }
                    }
                }
            }
        if (violaciones.isNotEmpty()) {
            fail(
                "En Android 14 o anterior estas llamadas lanzan NoSuchMethodError (son de java.util.List, " +
                    "API 35). Usa removeAt(lastIndex), removeLastOrNull(), add(0, x), first() o last():\n" +
                    violaciones.joinToString("\n"),
            )
        }
    }

    @Test
    fun `la guarda reconoce la llamada que tumbaba la flecha`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            NavStack.BackResult.Pop -> backStack.removeLast()
            // un comentario que nombra removeLast() no cuenta
            pila.removeAt(pila.lastIndex)
            """.trimIndent(),
        )
        val encontradas = codigo.lines().filter { llamada.containsMatchIn(it) }
        if (encontradas != listOf("NavStack.BackResult.Pop -> backStack.removeLast()")) {
            fail("La guarda tiene que encontrar exactamente la llamada de la flecha: $encontradas")
        }
    }
}
