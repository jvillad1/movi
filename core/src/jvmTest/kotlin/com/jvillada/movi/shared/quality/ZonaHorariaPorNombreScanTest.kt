package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * **Nadie pide la zona de Bogotá por nombre fuera de `AppTimeZone`.**
 *
 * En la web (wasm) kotlinx-datetime no trae la base de zonas IANA, así que
 * `TimeZone.of("America/Bogota")` **lanza** `IllegalTimeZoneException`. `AppTimeZone` existe
 * justamente para eso: resuelve la zona o cae a UTC-5 fijo (exacto, Colombia no tiene horario de
 * verano). El 19-sep una sola llamada directa en `ResumenDelPeriodo.kt` congeló el Inicio de la web
 * del dueño —se veía, pero no respondía a un clic— y el teléfono no lo delató porque en Android la
 * zona sí existe. Las pruebas corren en la JVM, donde tampoco lanza: por eso esto es un escaneo del
 * código y no una prueba de comportamiento.
 *
 * Además de la llamada directa, atrapa los dos atajos que la esconderían del texto `TimeZone.of(`:
 * pedirla por `TimeZone.Companion.of` (con o sin `import ... as`) y renombrar la clase con
 * `import kotlinx.datetime.TimeZone as Otro`. Un alias de la clase es raro y no hay ninguno: se
 * prohíbe entero en vez de seguir el nombre nuevo por todo el archivo.
 */
class ZonaHorariaPorNombreScanTest {

    private val permitidos = setOf("AppTimeZone.kt")

    private val prohibidos = listOf(
        "TimeZone.of(",
        "TimeZone.Companion.of",
        "import kotlinx.datetime.TimeZone as ",
    )

    @Test
    fun `el codigo compartido no llama TimeZone of fuera de AppTimeZone`() {
        val raiz = raizDelRepo()
        val violaciones = mutableListOf<String>()
        listOf("core/src/commonMain", "shared/src/commonMain", "core/src/wasmJsMain", "shared/src/wasmJsMain")
            .map { File(raiz, it) }
            .filter { it.isDirectory }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" && it.name !in permitidos }
                    .forEach { archivo ->
                        archivo.readLines().forEachIndexed { i, linea ->
                            val codigo = linea.substringBefore("//").trim()
                            if (!codigo.startsWith("*") && prohibidos.any { it in codigo }) {
                                violaciones += "${archivo.relativeTo(raiz)}:${i + 1}: $codigo"
                            }
                        }
                    }
            }
        if (violaciones.isNotEmpty()) {
            fail(
                "TimeZone.of(...) lanza en la web. Usa AppTimeZone.zone / AppTimeZone.resolve(id):\n" +
                    violaciones.joinToString("\n"),
            )
        }
    }

    private fun raizDelRepo(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "shared/src/commonMain").isDirectory && File(dir, "core/src/commonMain").isDirectory) return dir
            dir = dir.parentFile
        }
        fail("No encontré la raíz del repo desde user.dir=${System.getProperty("user.dir")}")
    }
}
