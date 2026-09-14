package com.jvillada.movi.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **El formulario de ingreso de la web usa los mismos colores que `Tokens.kt`.**
 *
 * Ese formulario vive en HTML dentro de `index.html` —el administrador de contraseñas del navegador
 * no ve un canvas— y por eso no puede leer los tokens: tiene una copia en variables de CSS. Una
 * copia se separa sola el día que alguien retoca un color de un lado. Hasta este cambio ya estaba
 * separada del todo: #121212 y un lavanda propio, la única pantalla de la app que no parecía Movi.
 *
 * Se lee el archivo de verdad y se compara cada variable, en los dos temas, contra la paleta.
 * Corre en la JVM de las pruebas de Android porque es la que puede abrir un archivo del repo.
 */
class ElIngresoHablaLosTokensTest {

    private val html: String by lazy {
        val candidatos = listOf("../webApp/src/wasmJsMain/resources/index.html", "webApp/src/wasmJsMain/resources/index.html")
        val archivo = candidatos.map(::File).firstOrNull { it.exists() }
            ?: fail("No encuentro index.html del webApp desde ${File(".").absolutePath}")
        archivo.readText()
    }

    /** Las variables `--nombre: #RRGGBB;` del primer bloque que empieza con [selector]. */
    private fun variablesDe(selector: String): Map<String, String> {
        val inicio = html.indexOf("$selector {")
        assertTrue(inicio >= 0, "index.html no tiene el bloque «$selector»")
        val bloque = html.substring(inicio, html.indexOf('}', inicio))
        return Regex("""--(\w+):\s*(#[0-9A-Fa-f]{6})\s*;""").findAll(bloque)
            .associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private fun paletaEnCss(c: ColoresDeMovi) = mapOf(
        "fondo" to c.fondo, "tarjeta" to c.tarjeta, "borde" to c.borde,
        "texto" to c.texto, "textoMedio" to c.textoMedio, "textoApagado" to c.textoApagado,
        "marca" to c.marca, "sobreMarca" to c.sobreMarca, "entra" to c.entra, "sale" to c.sale,
    ).mapValues { hex(it.value) }

    @Test
    fun `el tema oscuro del ingreso es el de los tokens`() {
        assertEquals(paletaEnCss(COLORES_OSCUROS), variablesDe(":root"))
    }

    @Test
    fun `el tema claro del ingreso es el de los tokens`() {
        assertEquals(paletaEnCss(COLORES_CLAROS), variablesDe(""":root[data-tema="claro"]"""))
    }

    @Test
    fun `el CSS del ingreso no pinta con colores sueltos`() {
        val css = html.substring(html.indexOf("<style>"), html.indexOf("</style>"))
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        val sueltos = Regex("""(?<!--\w{1,20}:\s{0,4})#[0-9A-Fa-f]{3,6}\b""").findAll(css).map { it.value }.toList()
        assertTrue(sueltos.isEmpty(), "Colores fuera de las variables: $sueltos. Usá var(--…).")
    }
}
