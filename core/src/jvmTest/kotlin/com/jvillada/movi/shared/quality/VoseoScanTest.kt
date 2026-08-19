package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * F4 — barrera contra el voseo rioplatense en los textos que ve el usuario.
 *
 * Escanea `shared/src/commonMain/.../ui` buscando formas voseantes DENTRO de literales de
 * string ("...") y falla si encuentra alguna. Solo mira literales, no el archivo entero, para
 * no tropezar con comentarios ni KDoc — esos pueden seguir en rioplatense (ver CLAUDE.md §
 * Key conventions: "Los comentarios de código pueden seguir en rioplatense").
 *
 * **Por qué es un test JVM en `:core` y no un `commonTest` en `:shared`.** `java.io.File` no
 * existe en `commonMain`/`commonTest` — es JVM-only, y `:shared` compila a wasmJs/iOS además
 * de JVM/Android, así que un test `commonTest` no puede recorrer el árbol de archivos del
 * repo. `:core` sí tiene un target `jvm()` con su propio `jvmTest`, que corre en una JVM
 * normal con acceso a disco: por eso el test vive acá, no en `:shared`, aunque lo que
 * inspecciona sea código de `:shared`.
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
     * inequívocas del voseo (pronombre "vos", "sos" de "ser"). Cada entrada se busca como
     * PALABRA completa (con límites `\b`) e insensible a mayúsculas, así que no colisiona con
     * "está", "aquí" o "Análisis" — los falsos positivos legítimos que aparecieron en el
     * barrido manual.
     *
     * Deliberadamente NO incluye "acá": es una preferencia léxica de "aquí", no una marca
     * gramatical exclusiva del voseo, y aparece como neutro en otras variantes latinoamericanas.
     */
    private val voseoBlacklist = listOf(
        "vos", "sos",
        "tenés", "tenes", "podés", "podes", "querés", "queres", "sabés", "hacés", "decís", "decis",
        "registrate", "repetila",
        "ingresá", "escribí", "probá", "esperá", "registrá", "cargá", "cargalos",
        "elegí", "anotá", "mirá", "armá", "volvé", "tocá", "entrá", "creá",
        "copiá", "revisá", "subí", "conectá", "dejá", "abrilo", "concedé", "concedelo",
    )

    /** Literales de string simples ("..."), con soporte básico de escapes (\", \\, etc). */
    private val stringLiteralRegex = Regex("\"(?:\\\\.|[^\"\\\\])*\"")

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

    @Test
    fun `ninguna forma voseante dentro de literales de string en shared ui`() {
        val uiDir = File(findRepoRoot(), "shared/src/commonMain/kotlin/com/jvillada/movi/ui")
        check(uiDir.isDirectory) { "No existe $uiDir" }

        val violations = mutableListOf<String>()

        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    stringLiteralRegex.findAll(line).forEach { match ->
                        val literal = match.value
                        for (word in voseoBlacklist) {
                            val wordRegex = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
                            if (wordRegex.containsMatchIn(literal)) {
                                violations += "${file.relativeTo(uiDir)}:${index + 1}: «$word» en $literal"
                            }
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "Se encontraron ${violations.size} forma(s) voseante(s) en textos de usuario " +
                    "(shared/src/commonMain/.../ui). movi habla en español neutro " +
                    "latinoamericano, de tú, sin voseo (ver CLAUDE.md § Key conventions):\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}
