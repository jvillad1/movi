package com.jvillada.movi.shared.quality

import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * # Vuelta 71 (revisión) · ningún texto con un glifo que Figtree no tiene
 *
 * «≈ $400.000 al mes» se veía como «□ $400.000 al mes»: Figtree, la única letra de Movi desde la vuelta
 * 29, no trae «≈» (ni «✓»), y Compose dibuja un cuadro vacío. Esta guarda abre el `figtree_regular.ttf`
 * del repo y revisa cada carácter de cada literal de string de `shared/src/commonMain/.../ui` contra él
 * (`Font.canDisplay`). Para decir «aproximadamente», con palabras: «unos $400.000».
 *
 * No mira comentarios ni KDoc, ni las líneas que arman una `Regex` (sus caracteres no se dibujan). La
 * única excepción es [NO_SE_DIBUJAN]: caracteres que van en un literal como clave y que en pantalla son
 * un ícono.
 */
class GlifosDeFigtreeScanTest {

    /** «⌫» es la clave de la tecla de borrar en Agregar y Presupuestos: se dibuja con un ícono. */
    private val NO_SE_DIBUJAN = setOf('⌫'.code)

    private val stringLiteralRegex = Regex("\"(?:\\\\.|[^\"\\\\])*\"")

    private fun findRepoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var hops = 0
        while (dir != null && hops < 8) {
            if (File(dir, "shared/src/commonMain/kotlin/com/jvillada/movi/ui").isDirectory) return dir
            dir = dir.parentFile
            hops++
        }
        fail("No se encontró shared/src/commonMain/kotlin/com/jvillada/movi/ui subiendo desde user.dir=${System.getProperty("user.dir")}.")
    }

    @Test
    fun `los textos de la app solo usan glifos que Figtree tiene`() {
        System.setProperty("java.awt.headless", "true")
        val root = findRepoRoot()
        val figtree = File(root, "shared/src/commonMain/composeResources/font/figtree_regular.ttf")
            .inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }
        val violaciones = mutableListOf<String>()
        File(root, "shared/src/commonMain/kotlin/com/jvillada/movi/ui").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .forEach { file ->
                file.readLines().forEachIndexed { i, linea ->
                    val sinSangria = linea.trimStart()
                    if (sinSangria.startsWith("//") || sinSangria.startsWith("*") || sinSangria.startsWith("/*")) return@forEachIndexed
                    if ("Regex(" in linea) return@forEachIndexed
                    stringLiteralRegex.findAll(linea).forEach { literal ->
                        literal.value.codePoints().filter { it > 127 && it !in NO_SE_DIBUJAN && !figtree.canDisplay(it) }
                            .forEach { cp ->
                                val hex = Integer.toHexString(cp).uppercase().padStart(4, '0')
                                violaciones += "${file.relativeTo(root)}:${i + 1}: «${String(Character.toChars(cp))}» (U+$hex) en ${literal.value}"
                            }
                    }
                }
            }
        if (violaciones.isNotEmpty()) {
            fail("Hay ${violaciones.size} glifo(s) que Figtree no tiene (se ven como un cuadro vacío):\n" + violaciones.joinToString("\n"))
        }
    }
}
