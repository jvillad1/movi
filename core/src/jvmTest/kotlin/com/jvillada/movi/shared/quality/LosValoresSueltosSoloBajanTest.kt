package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # Los valores sueltos solo pueden bajar
 *
 * Ola R0 del rediseño (26-oct-2026). La auditoría (`docs/diseno/auditoria-2026-10.md`) contó en
 * `ui/` **2.674 `.dp` escritos a mano con 76 valores distintos**, 247 radios con un número y 18
 * colores literales. El sistema de diseño (`theme/Escalas.kt`, `ui/components/ds/`) es la lista
 * corta de la que se elige; esta prueba es el **trinquete** que impide que el número vuelva a subir
 * mientras las olas siguientes migran pantalla por pantalla.
 *
 * Cuenta tres cosas, con la misma regla que `scripts/auditoria-de-diseno.py` (comentarios fuera):
 *
 * - **`.dp` sueltos**: `N.dp` en `ui/`, fuera de `ui/components/ds/` (que no puede tener ninguno, ver
 *   `ElSistemaNoTieneValoresSueltosTest`).
 * - **Radios con número**: `RoundedCornerShape(` con un número adentro, en `ui/` fuera de `ds/`.
 * - **Colores literales**: `Color(0x…)` en todo `commonMain` fuera de `theme/`. Está en **cero**: un
 *   color nuevo va a `Tokens.kt`, donde la prueba de contraste lo mide.
 *
 * Las líneas base viven en `docs/diseno/lineas-base.txt`.
 *
 * **Si esta prueba te falló porque el número subió**: usa un token (`Espacio`, `Medida`, `Radio`,
 * `Icono` de `theme/Escalas.kt`) o un componente de `ui/components/ds/` en vez del número.
 *
 * **Si falló porque bajó** —migraste algo—: baja la línea base al número nuevo en el mismo cambio.
 * Un tope flojo deja que el siguiente vuelva a subir hasta él sin que nada avise.
 */
class LosValoresSueltosSoloBajanTest {

    private val dpSuelto = Regex("""(?<![\w.])\d+(?:\.\d+)?\.dp\b""")
    private val radioConNumero = Regex("""RoundedCornerShape\(\s*(?:\w+\s*=\s*)?\d""")
    private val colorLiteral = Regex("""Color\(0x""")

    private fun lineasBase(): Map<String, Int> {
        val archivo = File(LecturaDelCodigo.raiz(), "docs/diseno/lineas-base.txt")
        if (!archivo.isFile) fail("No encontré docs/diseno/lineas-base.txt")
        return archivo.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .associate { linea ->
                val (clave, valor) = linea.split('=').map { it.trim() }
                clave to valor.toInt()
            }
    }

    private fun conteos(): Map<String, List<String>> {
        val ui = LecturaDelCodigo.archivos(LecturaDelCodigo.UI, LecturaDelCodigo.DS)
        val todo = LecturaDelCodigo.archivos(LecturaDelCodigo.COMMON_MAIN, LecturaDelCodigo.THEME)
        return mapOf(
            "dp_sueltos" to LecturaDelCodigo.dondeAparece(ui, dpSuelto),
            "radios_con_numero" to LecturaDelCodigo.dondeAparece(ui, radioConNumero),
            "colores_literales" to LecturaDelCodigo.dondeAparece(todo, colorLiteral),
        )
    }

    private fun peores(donde: List<String>): String =
        donde.groupingBy { it.substringBeforeLast(':') }.eachCount()
            .entries.sortedByDescending { it.value }.take(8)
            .joinToString("\n") { "  ${it.value} en ${it.key}" }

    @Test
    fun `no suben los valores sueltos`() {
        val base = lineasBase()
        val fallas = conteos().mapNotNull { (clave, donde) ->
            val tope = base[clave] ?: fail("docs/diseno/lineas-base.txt no tiene «$clave»")
            if (donde.size > tope) {
                "«$clave» subió a ${donde.size} (la línea base es $tope). Usa un token de " +
                    "theme/Escalas.kt o un componente de ui/components/ds/. Donde más hay:\n" + peores(donde)
            } else {
                null
            }
        }
        if (fallas.isNotEmpty()) fail(fallas.joinToString("\n\n"))
    }

    @Test
    fun `la linea base no queda floja cuando se migra`() {
        val base = lineasBase()
        val flojas = conteos().mapNotNull { (clave, donde) ->
            val tope = base[clave] ?: return@mapNotNull null
            if (donde.size < tope) "«$clave»: quedan ${donde.size} y la línea base dice $tope — bájala a ${donde.size}" else null
        }
        assertTrue(flojas.isEmpty(), "Migraste valores sueltos: baja la línea base en docs/diseno/lineas-base.txt.\n" + flojas.joinToString("\n"))
    }

    /** Los colores literales ya están en cero, y ahí se quedan: no hay «bajar» que esperar. */
    @Test
    fun `no hay colores literales fuera de theme`() {
        val donde = conteos().getValue("colores_literales")
        assertTrue(donde.isEmpty(), "Colores escritos a mano fuera de theme/ (van a Tokens.kt):\n" + donde.joinToString("\n"))
    }

    /** Un detector que siempre da cero no se distingue de uno roto: esto le muestra lo que debe ver. */
    @Test
    fun `los detectores ven lo que tienen que ver y no ven los comentarios`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            Modifier.padding(16.dp).size(Medida.avatar) // un 12.dp en un comentario no cuenta
            /* ni 8.dp acá,
               ni Color(0xFF000000) acá */
            val forma = RoundedCornerShape(12.dp)
            val otra = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            val token = RoundedCornerShape(Radio.l)
            val url = "https://movi.example/12.dp" // la URL no abre un comentario
            Text("a", color = Color(0xFF123456))
            Modifier.weight(1.5f).offset(x = 2.5.dp)
            """.trimIndent(),
        )
        val archivo = mapOf("Prueba.kt" to codigo)
        assertEquals(6, LecturaDelCodigo.dondeAparece(archivo, dpSuelto).size, "dp")
        assertEquals(2, LecturaDelCodigo.dondeAparece(archivo, radioConNumero).size, "radios")
        assertEquals(1, LecturaDelCodigo.dondeAparece(archivo, colorLiteral).size, "colores")
    }
}
