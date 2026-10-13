package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jvillada.movi.theme.COLORES_OSCUROS
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **El widget «Te quedan hoy» habla los colores de `Tokens.kt`.** En pantalla los pone el código
 * desde la paleta (`WidgetTeQuedan`), pero la vista previa del selector de widgets y el primer cuadro
 * salen del XML de `:androidApp`, que no puede leer Kotlin: tiene su copia en `colors.xml`. Esta
 * prueba la compara con el tema oscuro, y que el layout no pinte con ningún color suelto.
 */
class ElWidgetHablaLosTokensTest {

    private fun archivo(ruta: String): String {
        val candidatos = listOf("../$ruta", ruta)
        return candidatos.map(::File).firstOrNull { it.exists() }?.readText()
            ?: fail("No encuentro $ruta desde ${File(".").absolutePath}")
    }

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    @Test
    fun `los colores de la vista previa son los del tema oscuro`() {
        val colores = Regex("""<color name="(widget_\w+)">(#[0-9A-Fa-f]{6})</color>""")
            .findAll(archivo("androidApp/src/main/res/values/colors.xml"))
            .associate { it.groupValues[1] to it.groupValues[2].uppercase() }
        assertEquals(
            mapOf(
                "widget_tarjeta" to hex(COLORES_OSCUROS.tarjeta),
                "widget_texto" to hex(COLORES_OSCUROS.texto),
                "widget_texto_medio" to hex(COLORES_OSCUROS.textoMedio),
                "widget_texto_apagado" to hex(COLORES_OSCUROS.textoApagado),
            ),
            colores,
        )
    }

    @Test
    fun `el layout del widget no pinta con colores sueltos`() {
        val xml = archivo("androidApp/src/main/res/layout/widget_te_quedan.xml")
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        val sueltos = Regex("""="#[0-9A-Fa-f]{3,8}"""").findAll(xml).map { it.value }.toList()
        assertTrue(sueltos.isEmpty(), "colores escritos a mano en el layout: $sueltos")
    }
}
