package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * **El código que corre en la web no atrapa `Exception` a secas: atrapa `Throwable`.**
 *
 * En la web (wasm) el motor JS de Ktor reporta un `fetch` caído como
 * `kotlin.Error("Fail to fetch", JsError)` —un `Throwable` que no es `Exception`—; en Android la
 * misma caída es una `IOException`. Un `catch (e: Exception)` alrededor de una llamada al server
 * la atrapa en el teléfono y la deja pasar en la web, donde sube al Recomposer y congela el canvas
 * en su último cuadro. El cliente HTTP de la web ya la traduce a `IOException`
 * (`comoFalloDeRed`, en `shared/.../data/FalloDeRed.kt`), pero una pantalla no puede depender de
 * que ningún motor futuro vuelva a lanzar algo raro: donde se atrapa, se atrapa todo, con el
 * `catch (e: CancellationException) { throw e }` primero.
 *
 * **Qué cubre, exactamente.** Distinguir «un catch alrededor de una llamada de red» de cualquier
 * otro catch pide entender el código, y un escaneo que adivina da falsos positivos. La regla que
 * se escanea es más simple y más ancha: **ningún `catch (x: Exception)` —`Exception` a secas, o
 * `kotlin.Exception`— en los source sets que terminan en la web** (`commonMain` y `wasmJsMain` de
 * `:core` y `:shared`, y `:webApp`). Hoy no hay ninguno: los cinco que había eran justamente los
 * de red, en `ui/accounts`. No toca los catch de excepciones concretas
 * (`NumberFormatException`, `CancellationException`, `ApiException`...), que no tienen este
 * problema, ni `runCatching`, que ya atrapa `Throwable`. Tampoco mira `nonWasmMain`
 * (`SyncEngine`, `LocalRepository`): no corre en la web.
 *
 * Es un escaneo y no una prueba de comportamiento porque las pruebas corren en la JVM, donde el
 * error de red sí es una `Exception`: ahí el bug es invisible.
 */
class ExcepcionesDeRedScanTest {

    private val catchDeException = Regex("""catch\s*\(\s*\w+\s*:\s*(kotlin\.)?Exception\s*\)""")

    @Test
    fun `el codigo que corre en la web no atrapa Exception a secas`() {
        val raiz = raizDelRepo()
        val violaciones = mutableListOf<String>()
        listOf(
            "core/src/commonMain", "core/src/wasmJsMain",
            "shared/src/commonMain", "shared/src/wasmJsMain",
            "webApp/src",
        )
            .map { File(raiz, it) }
            .filter { it.isDirectory }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { archivo ->
                        archivo.readLines().forEachIndexed { i, linea ->
                            val codigo = linea.substringBefore("//").trim()
                            if (!codigo.startsWith("*") && catchDeException.containsMatchIn(codigo)) {
                                violaciones += "${archivo.relativeTo(raiz)}:${i + 1}: $codigo"
                            }
                        }
                    }
            }
        if (violaciones.isNotEmpty()) {
            fail(
                "En la web un fetch caído es un kotlin.Error, no una Exception: este catch lo deja " +
                    "pasar y congela la app. Atrapa Throwable (con el rethrow de " +
                    "CancellationException primero):\n" + violaciones.joinToString("\n"),
            )
        }
    }

    @Test
    fun `la regla reconoce las formas que prohibe y deja pasar las demas`() {
        val prohibidas = listOf(
            "} catch (e: Exception) {",
            "} catch (_: Exception) {}",
            "catch(e:kotlin.Exception){",
        )
        val permitidas = listOf(
            "} catch (e: Throwable) {",
            "} catch (e: CancellationException) {",
            "} catch (e: NumberFormatException) {",
            "} catch (e: ApiException) {",
        )
        prohibidas.filterNot { catchDeException.containsMatchIn(it) }
            .takeIf { it.isNotEmpty() }?.let { fail("No las atrapa: $it") }
        permitidas.filter { catchDeException.containsMatchIn(it) }
            .takeIf { it.isNotEmpty() }?.let { fail("Falso positivo: $it") }
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
