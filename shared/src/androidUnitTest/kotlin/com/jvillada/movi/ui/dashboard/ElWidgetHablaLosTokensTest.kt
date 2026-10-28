package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jvillada.movi.theme.COLORES_CLAROS
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **El widget «Te quedan hoy» habla los colores de `Tokens.kt`.** En pantalla los pone el código
 * desde la paleta (`WidgetTeQuedan`), pero la vista previa del selector de widgets y el primer cuadro
 * salen del XML de `:androidApp`, que no puede leer Kotlin: tiene su copia en `colors.xml`. Esta
 * prueba la compara con el único tema que hay (el claro de la opción 3, vuelta 29), y que el layout
 * no pinte con ningún color suelto. También el fondo de la ventana de arranque (`movi_fondo`).
 */
class ElWidgetHablaLosTokensTest {

    private fun archivo(ruta: String): String {
        val candidatos = listOf("../$ruta", ruta)
        return candidatos.map(::File).firstOrNull { it.exists() }?.readText()
            ?: fail("No encuentro $ruta desde ${File(".").absolutePath}")
    }

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private val colores: Map<String, String> by lazy {
        Regex("""<color name="(\w+)">(#[0-9A-Fa-f]{6})</color>""")
            .findAll(archivo("androidApp/src/main/res/values/colors.xml"))
            .associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    @Test
    fun `los colores de la vista previa son los del tema de Movi`() {
        assertEquals(
            mapOf(
                "widget_tarjeta" to hex(COLORES_CLAROS.tarjeta),
                "widget_texto" to hex(COLORES_CLAROS.texto),
                "widget_texto_medio" to hex(COLORES_CLAROS.textoMedio),
                "widget_texto_apagado" to hex(COLORES_CLAROS.textoApagado),
            ),
            colores.filterKeys { it.startsWith("widget_") },
        )
    }

    @Test
    fun `la ventana de arranque es del fondo de Movi`() {
        assertEquals(hex(COLORES_CLAROS.fondo), colores["movi_fondo"])
    }

    @Test
    fun `el layout del widget no pinta con colores sueltos`() {
        val xml = archivo("androidApp/src/main/res/layout/widget_te_quedan.xml")
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        val sueltos = Regex("""="#[0-9A-Fa-f]{3,8}"""").findAll(xml).map { it.value }.toList()
        assertTrue(sueltos.isEmpty(), "colores escritos a mano en el layout: $sueltos")
    }
}
