package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Las pantallas se llaman como se llaman (vuelta 61)
 *
 * La vuelta 38 cambió las pestañas a **Inicio · Actividad · Pagos · Tú** (y la 42, Espacios →
 * **Grupos**), pero quedaban textos que nombraban las viejas: «Qué secciones ves en Hoy», «Te avisa en
 * Hoy», «tócala para verla en Plan», «desde su detalle en Movimientos», y Movi AI decía «márcalo en
 * Plan» y «la tarjeta Disponible de Plan».
 *
 * Esta guarda recorre los **literales** (lo que el dueño lee) de `ui/` y de las respuestas sin IA del
 * server (`server/.../ai/`), sin comentarios, y falla si uno nombra una pestaña vieja **como lugar**:
 * «en Hoy», «de Plan», «desde Movimientos», «a Espacios», ««Plan»», «pestaña Patrimonio». «Hoy» como
 * día («Hoy · martes 28», «Hoy te pasaste…») y «Movimientos» como título de una lista no son lugares y
 * pasan. Los nombres internos (`Screen.Plan`, `NavTab.HOY`, `Espacio*`) no se tocan.
 */
class LosNombresViejosDeLasPestanasTest {

    private val VIEJAS = "Hoy|Plan|Movimientos|Espacios"

    /** Una pestaña vieja nombrada como lugar dentro de un texto. */
    private val COMO_LUGAR = Regex(
        """\b(?:en|de|desde|a|al|del)\s+«?($VIEJAS)\b(?!\s*:)|«($VIEJAS)»|\b[Pp]estaña\s+«?($VIEJAS|Patrimonio)\b""",
    )

    private val LITERAL = Regex(""""(?:[^"\\]|\\.)*"""")

    private val SERVER_AI = "server/src/main/kotlin/com/jvillada/movi/server/ai"

    private fun nombre(ruta: String) = ruta.substringAfterLast("/kotlin/com/jvillada/movi/")

    /** Cada literal de [archivos] que nombra una pestaña vieja como lugar, con su archivo y renglón. */
    private fun nombresViejos(archivos: Map<String, String>): List<String> =
        archivos.flatMap { (ruta, codigo) ->
            codigo.lines().flatMapIndexed { n, renglon ->
                LITERAL.findAll(renglon)
                    .filter { COMO_LUGAR.containsMatchIn(it.value) }
                    .map { "${nombre(ruta)}:${n + 1}: ${it.value}" }
                    .toList()
            }
        }

    @Test
    fun `ningun texto de ui nombra una pestana vieja`() {
        val donde = nombresViejos(LecturaDelCodigo.archivos(LecturaDelCodigo.UI))
        assertTrue(
            donde.isEmpty(),
            "Un texto nombra una pestaña con su nombre viejo. Hoy → Inicio, Movimientos → Actividad, " +
                "Plan → Pagos, Patrimonio (pestaña) → Tú, Espacios → Grupos:\n" + donde.joinToString("\n"),
        )
    }

    @Test
    fun `ninguna respuesta sin IA nombra una pestana vieja`() {
        val donde = nombresViejos(LecturaDelCodigo.archivos(SERVER_AI))
        assertTrue(donde.isEmpty(), "Movi AI nombra una pantalla con su nombre viejo:\n" + donde.joinToString("\n"))
    }

    @Test
    fun `el detector ve los lugares y deja pasar el dia y los titulos`() {
        val codigo = """
            Text("Qué secciones ves en Hoy y en qué orden")
            Text("Míralo en la tarjeta Disponible de Plan.")
            Text("lo pendiente se revisa desde Movimientos.")
            Text("Ábrelo en «Plan»")
            Text("Hoy · martes 28")
            Text("Hoy te pasaste de la meta")
            Seccion(titulo = "Movimientos")
            Text("Plan de salida de deudas")
            Text("Qué secciones ves en Inicio")
        """.trimIndent()
        val donde = nombresViejos(mapOf("/kotlin/com/jvillada/movi/ui/x.kt" to codigo)).map { it.substringBefore(": ") }
        assertEquals(listOf("ui/x.kt:1", "ui/x.kt:2", "ui/x.kt:3", "ui/x.kt:4"), donde)
    }
}
