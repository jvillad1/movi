package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * # Vuelta 71 · sin comillas rectas en los textos de la app
 *
 * «Crear "Mascotas"» decía la pastilla de crear categoría: comillas rectas de máquina de escribir en
 * medio de una app que cita con «». La vuelta 71 cambió las catorce que había en `ui/` (crear y usar
 * una categoría, «¿… se repite todos los meses?», borrar una cuenta, los avisos de presupuestos) y esta
 * guarda no deja volver ninguna: un `\"` **dentro de un literal de string** de
 * `shared/src/commonMain/.../ui` falla. Para citar, «».
 *
 * Mismo andamio que [VoseoScanTest] (de donde sale la regex de literales y la forma de ubicar la raíz):
 * no mira comentarios ni KDoc. Fuera de `ui/` no mira nada (los mensajes del server tienen su propia
 * historia).
 */
class ComillasRectasScanTest {

    /** Literales de string simples ("..."), con escapes. La misma de [VoseoScanTest]. */
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
        fail("No se encontró shared/src/commonMain/kotlin/com/jvillada/movi/ui subiendo desde user.dir=${System.getProperty("user.dir")}.")
    }

    @Test
    fun `ningun texto de la app cita con comillas rectas`() {
        val root = findRepoRoot()
        val ui = File(root, "shared/src/commonMain/kotlin/com/jvillada/movi/ui")
        val violaciones = mutableListOf<String>()
        ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .forEach { file ->
                file.readLines().forEachIndexed { i, linea ->
                    val sinSangria = linea.trimStart()
                    if (sinSangria.startsWith("//") || sinSangria.startsWith("*") || sinSangria.startsWith("/*")) return@forEachIndexed
                    stringLiteralRegex.findAll(linea)
                        .filter { "\\\"" in it.value }
                        .forEach { violaciones += "${file.relativeTo(root)}:${i + 1}: ${it.value}" }
                }
            }
        if (violaciones.isNotEmpty()) {
            fail(
                "Se encontraron ${violaciones.size} texto(s) con comillas rectas (\\\"). Movi cita con «»:\n" +
                    violaciones.joinToString("\n"),
            )
        }
    }
}
