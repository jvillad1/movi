package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.fail

/**
 * Lo que comparten las guardas del sistema de diseño (ola R0): encontrar la raíz del repo, recorrer
 * los `.kt` de una carpeta y leerlos **sin comentarios**.
 *
 * Sin comentarios porque un KDoc que cuenta la historia de un color («era un `Color(0x…)` a mano»)
 * no es un color a mano, y la regla tiene que coincidir con la del script de la auditoría
 * (`scripts/auditoria-de-diseno.py`), que hace lo mismo. Los textos entre comillas se respetan: un
 * `//` dentro de una URL no abre un comentario.
 */
internal object LecturaDelCodigo {

    const val UI = "shared/src/commonMain/kotlin/com/jvillada/movi/ui"
    const val COMMON_MAIN = "shared/src/commonMain/kotlin/com/jvillada/movi"
    const val DS = "$UI/components/ds"
    const val THEME = "$COMMON_MAIN/theme"

    fun raiz(): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, UI).isDirectory }
            ?: fail("No encontré $UI subiendo desde user.dir")

    /** Los `.kt` de [carpeta] (relativa a la raíz), sin los que están debajo de [fuera]. */
    fun archivos(carpeta: String, vararg fuera: String): Map<String, String> {
        val raiz = raiz()
        val excluidas = fuera.map { File(raiz, it).absoluteFile }
        return File(raiz, carpeta).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { archivo -> excluidas.none { archivo.absoluteFile.startsWith(it) } }
            .sortedBy { it.path }
            .associate { it.relativeTo(raiz).path to sinComentarios(it.readText()) }
    }

    /** [texto] sin comentarios `//` ni `/* */`, con los renglones en su lugar. */
    fun sinComentarios(texto: String): String {
        val salida = StringBuilder()
        var enBloque = false
        for (linea in texto.lines()) {
            var enCadena = false
            var i = 0
            while (i < linea.length) {
                if (enBloque) {
                    val fin = linea.indexOf("*/", i)
                    if (fin < 0) {
                        i = linea.length
                        continue
                    }
                    i = fin + 2
                    enBloque = false
                    continue
                }
                val c = linea[i]
                if (c == '"' && (i == 0 || linea[i - 1] != '\\')) enCadena = !enCadena
                if (!enCadena && linea.startsWith("//", i)) break
                if (!enCadena && linea.startsWith("/*", i)) {
                    enBloque = true
                    i += 2
                    continue
                }
                salida.append(c)
                i++
            }
            salida.append('\n')
        }
        return salida.toString()
    }

    /** Las coincidencias de [regex] en cada archivo, como `archivo:línea`. */
    fun dondeAparece(archivos: Map<String, String>, regex: Regex): List<String> =
        archivos.flatMap { (ruta, texto) ->
            texto.lines().flatMapIndexed { n, linea -> regex.findAll(linea).map { "$ruta:${n + 1}" }.toList() }
        }
}
