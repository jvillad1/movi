package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * F4 — barrera contra el voseo rioplatense en los textos que ve el usuario.
 *
 * Escanea varios árboles del repo buscando formas voseantes DENTRO de literales de string
 * ("...") o, en HTML, dentro del texto entre etiquetas y de los atributos `placeholder`/`value`.
 * Nunca mira comentarios ni KDoc — esos pueden seguir en rioplatense (ver CLAUDE.md § Key
 * conventions: "Los comentarios de código pueden seguir en rioplatense").
 *
 * Ola 2 #4 amplió el alcance original (solo `shared/src/commonMain/.../ui`) a:
 * - `webApp/src/wasmJsMain/resources/index.html` (login/registro web, fuera del árbol de UI de
 *   Compose).
 * - `server/src/main/kotlin` (mensajes de error/éxito en las rutas y los correos que arma
 *   `PasswordResetMailer`; el usuario los ve igual que un texto de la UI).
 * - `androidApp/src/main/kotlin` (receivers/workers de la captura de SMS).
 * - `shared/src/androidMain/kotlin` (la sección «Captura de SMS» y los textos del backfill,
 *   que se mudaron ahí desde androidApp cuando la app completa pasó a correr en el teléfono).
 * - `core/.../PasswordPolicy.kt` (el único mensaje de política de contraseña, compartido por
 *   servidor y clientes).
 *
 * **Por qué es un test JVM en `:core` y no un `commonTest` en `:shared`.** `java.io.File` no
 * existe en `commonMain`/`commonTest` — es JVM-only, y `:shared` compila a wasmJs/iOS además
 * de JVM/Android, así que un test `commonTest` no puede recorrer el árbol de archivos del
 * repo. `:core` sí tiene un target `jvm()` con su propio `jvmTest`, que corre en una JVM
 * normal con acceso a disco: por eso el test vive acá, no en `:shared`, aunque la mayoría de lo
 * que inspecciona sea código de otros módulos (`shared`, `webApp`, `server`, `androidApp`).
 *
 * **Cómo ubica la raíz del repo.** Gradle ejecuta el proceso de `:core:jvmTest` con el
 * working directory del subproyecto (`core/`), no el de la raíz del repo — `user.dir` en ese
 * proceso apunta a `.../movi/core`. Este test sube desde ahí, ancestro por ancestro, hasta
 * encontrar el primero que contenga `shared/src/commonMain/kotlin/com/jvillada/movi/ui`. Con
 * el layout actual eso pasa en el primer salto (`core/` → `movi/`), pero subir en vez de
 * asumir un número fijo de niveles hace que el test no se rompa si algún día cambia el working
 * directory por defecto de la tarea, o si alguien lo corre desde otro lado.
 */
class VoseoScanTest {

    /**
     * Formas voseantes conocidas — las que trajo el barrido de F4 más las marcas gramaticales
     * inequívocas del voseo (pronombre "vos", "sos" de "ser") y "acá" (Ola 2 #4: F4 lo cambió a
     * "aquí" en todos los textos de usuario que tocó; "acá" queda en la lista negra como
     * preferencia léxica a evitar en textos NUEVOS, aunque como preferencia y no como marca
     * gramatical no bloquea nada retroactivo que ya haya quedado afuera del alcance de F4).
     *
     * Cada entrada se busca como PALABRA completa e insensible a mayúsculas, con
     * [wordBoundaryRegex] — no `\b` de Java, que en JDK ≥ 19 es ASCII-only y no ve límite de
     * palabra después de una vocal acentuada final ("Creá|" no tiene `\b` entre "á" y el fin de
     * la palabra bajo esa definición, así que "Creá", "Registrá", "Subí" pasaban sin marcarse).
     * Con límites correctos no colisiona con "está", "aquí" o "Análisis" — los falsos positivos
     * legítimos que aparecieron en el barrido manual.
     */
    private val voseoBlacklist = listOf(
        "vos", "sos",
        "tenés", "tenes", "podés", "podes", "querés", "queres", "sabés", "hacés", "decís", "decis",
        "registrate", "repetila",
        "ingresá", "escribí", "probá", "esperá", "registrá", "cargá", "cargalos",
        "elegí", "anotá", "mirá", "armá", "volvé", "tocá", "entrá", "creá",
        "copiá", "revisá", "subí", "conectá", "dejá", "abrilo", "concedé", "concedelo",
        "acá",
    )

    /** `\b` de Java es ASCII-only desde JDK 19 — ver KDoc de [voseoBlacklist]. `\p{L}` sí conoce
     *  las vocales acentuadas, así que "no hay letra antes/después" funciona en todo Unicode. */
    private fun wordBoundaryRegex(word: String) =
        Regex("(?<!\\p{L})${Regex.escape(word)}(?!\\p{L})", RegexOption.IGNORE_CASE)

    private val wordRegexes = voseoBlacklist.associateWith(::wordBoundaryRegex)

    /** Literales de string simples ("..."), con soporte básico de escapes (\", \\, etc). Sirve
     *  tanto para Kotlin como para el `<script>` embebido en HTML — misma sintaxis de comillas. */
    private val stringLiteralRegex = Regex("\"(?:\\\\.|[^\"\\\\])*\"")

    /** Texto entre etiquetas HTML: `>texto<`. No cruza líneas — un comentario HTML/JS de varias
     *  líneas sin `>`/`<` propios en una línea dada no genera match, así que los comentarios
     *  quedan afuera sin necesidad de detectarlos aparte (ver Dudas en el reporte). */
    private val htmlTagTextRegex = Regex(">([^<>]+)<")

    /** `placeholder="..."` / `value="..."` — los dos atributos que pidió el ítem. */
    private val htmlAttrRegex = Regex("(?:placeholder|value)\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    private fun findRepoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var hops = 0
        while (dir != null && hops < 8) {
            val marker = File(dir, "shared/src/commonMain/kotlin/com/jvillada/movi/ui")
            if (marker.isDirectory) return dir
            dir = dir.parentFile
            hops++
        }
        fail(
            "No se encontró shared/src/commonMain/kotlin/com/jvillada/movi/ui subiendo desde " +
                "user.dir=${System.getProperty("user.dir")}. ¿Cambió el working directory de " +
                "la tarea :core:jvmTest?",
        )
    }

    private fun scanLiteral(literal: String, location: String, violations: MutableList<String>) {
        for ((word, regex) in wordRegexes) {
            if (regex.containsMatchIn(literal)) {
                violations += "$location: «$word» en $literal"
            }
        }
    }

    /** Todos los `.kt` bajo [dir], literal de string por literal de string. */
    private fun scanKotlinTree(dir: File, root: File, violations: MutableList<String>) {
        if (!dir.isDirectory) return
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .forEach { file -> scanKotlinFile(file, root, violations) }
    }

    private fun scanKotlinFile(file: File, root: File, violations: MutableList<String>) {
        file.readLines().forEachIndexed { index, line ->
            stringLiteralRegex.findAll(line).forEach { match ->
                scanLiteral(match.value, "${file.relativeTo(root)}:${index + 1}", violations)
            }
        }
    }

    private fun scanHtmlFile(file: File, root: File, violations: MutableList<String>) {
        if (!file.isFile) return
        file.readLines().forEachIndexed { index, line ->
            val location = "${file.relativeTo(root)}:${index + 1}"
            htmlTagTextRegex.findAll(line).forEach { scanLiteral(it.groupValues[1], location, violations) }
            htmlAttrRegex.findAll(line).forEach { scanLiteral(it.groupValues[1], location, violations) }
        }
    }

    @Test
    fun `ninguna forma voseante en los textos que ve el usuario`() {
        val root = findRepoRoot()
        val violations = mutableListOf<String>()

        // F4 original: pantallas de Compose.
        scanKotlinTree(File(root, "shared/src/commonMain/kotlin/com/jvillada/movi/ui"), root, violations)
        // Ola 2 #4: ampliación de alcance.
        scanHtmlFile(File(root, "webApp/src/wasmJsMain/resources/index.html"), root, violations)
        scanKotlinTree(File(root, "server/src/main/kotlin"), root, violations)
        scanKotlinTree(File(root, "androidApp/src/main/kotlin"), root, violations)
        scanKotlinTree(File(root, "shared/src/androidMain/kotlin"), root, violations)
        scanKotlinFile(
            File(root, "core/src/commonMain/kotlin/com/jvillada/movi/shared/model/PasswordPolicy.kt"),
            root,
            violations,
        )

        if (violations.isNotEmpty()) {
            fail(
                "Se encontraron ${violations.size} forma(s) voseante(s) en textos de usuario. " +
                    "movi habla en español neutro latinoamericano, de tú, sin voseo (ver " +
                    "CLAUDE.md § Key conventions):\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}
