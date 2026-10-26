package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Los componentes del sistema no tienen valores sueltos
 *
 * Ola R0. `ui/components/ds/` es lo que todas las pantallas van a usar: si un componente del
 * sistema decide un número por su cuenta, ese número se copia a cien lugares. Por eso acá adentro
 * la regla no es un trinquete sino **cero**: ningún `N.dp`, `N.sp` o `N.em`, ningún `Color(0x…)`,
 * ningún `RoundedCornerShape` con número, ningún `fontSize =` y ningún `alpha =` escrito a mano.
 * Todo sale de `theme/` (`Escalas.kt`, `Tokens.kt`).
 *
 * Los `Float` de un `weight(1f)` o una fracción de ancho no son valores de diseño y quedan afuera.
 */
class ElSistemaNoTieneValoresSueltosTest {

    private val prohibidos = mapOf(
        "dimensiones" to Regex("""(?<![\w.])\d+(?:\.\d+)?\.(?:dp|sp|em)\b"""),
        "colores" to Regex("""Color\(0x"""),
        "radios" to Regex("""RoundedCornerShape\(\s*(?:\w+\s*=\s*)?\d"""),
        "tamaños de letra" to Regex("""fontSize\s*=\s*\d"""),
        "opacidades" to Regex("""alpha\s*=\s*\d"""),
    )

    @Test
    fun `ui components ds no tiene ni un valor suelto`() {
        val ds = LecturaDelCodigo.archivos(LecturaDelCodigo.DS)
        assertTrue(ds.isNotEmpty(), "No encontré ${LecturaDelCodigo.DS}: ¿se movió el paquete?")
        val fallas = prohibidos.flatMap { (que, regex) ->
            LecturaDelCodigo.dondeAparece(ds, regex).map { "  $que en $it" }
        }
        assertTrue(
            fallas.isEmpty(),
            "Valores sueltos dentro del sistema de diseño (usa Espacio, Medida, Radio, Icono o un " +
                "token de Movi.colores / Movi.textos):\n" + fallas.joinToString("\n"),
        )
    }

    @Test
    fun `el detector ve cada cosa prohibida`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            Modifier.padding(16.dp).size(Medida.avatar)
            Text("a", fontSize = 14.sp, color = Color(0xFF000000).copy(alpha = 0.5f))
            val forma = RoundedCornerShape(12.dp)
            Modifier.weight(1f).fillMaxWidth(0.6f) // un 8.dp en un comentario no cuenta
            """.trimIndent(),
        )
        val archivo = mapOf("Prueba.kt" to codigo)
        val vistos = prohibidos.mapValues { (_, regex) -> LecturaDelCodigo.dondeAparece(archivo, regex).size }
        assertEquals(
            mapOf("dimensiones" to 3, "colores" to 1, "radios" to 1, "tamaños de letra" to 1, "opacidades" to 1),
            vistos,
        )
    }
}
