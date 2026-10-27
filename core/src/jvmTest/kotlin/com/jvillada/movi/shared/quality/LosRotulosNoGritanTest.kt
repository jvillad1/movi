package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Los rótulos de un formulario van en frase
 *
 * Vuelta de diseño 6. Las hojas de los créditos titulaban sus bloques con un `SectionLabel` en
 * mayúsculas («CRÉDITO», «TÉRMINOS», «LA PLATA QUE TE ENTRÓ»), la de un recurrente con
 * `SheetSectionLabel` («NOMBRE», «CADA CUÁNTO»), y cada una tenía su copia privada. Se borraron
 * todas: un bloque de un formulario se titula con `GrupoDeCampos` (o `Seccion`), un campo con el
 * `rotulo` de `Campo`, y un control que no es una caja con `RotuloDeCampo`, todos en frase.
 *
 * Falla si vuelve a aparecer una función `…SectionLabel` en `ui/`, o si un título o rótulo del
 * sistema recibe un texto escrito entero en mayúsculas.
 */
class LosRotulosNoGritanTest {

    private val definicionDeSectionLabel = Regex("""fun\s+(?:\w+\.)?\w*SectionLabel\s*\(""")

    /** El texto literal que recibe un título o un rótulo del sistema. */
    private val rotuloLiteral = Regex(
        """(?:\b(?:Seccion|GrupoDeCampos|RotuloDeCampo)\(\s*(?:titulo\s*=\s*|texto\s*=\s*)?|\b(?:rotulo|label)\s*=\s*)"([^"$]*)"""",
    )

    private fun gritan(texto: String): Boolean {
        val letras = texto.filter { it.isLetter() }
        return letras.length >= 2 && letras.none { it.isLowerCase() }
    }

    private fun rotulosQueGritan(archivos: Map<String, String>): List<String> =
        archivos.flatMap { (ruta, codigo) ->
            codigo.lines().flatMapIndexed { n, linea ->
                rotuloLiteral.findAll(linea)
                    .filter { gritan(it.groupValues[1]) }
                    .map { "$ruta:${n + 1}: «${it.groupValues[1]}»" }
                    .toList()
            }
        }

    @Test
    fun `no hay SectionLabel en ui`() {
        val ui = LecturaDelCodigo.archivos(LecturaDelCodigo.UI)
        val donde = LecturaDelCodigo.dondeAparece(ui, definicionDeSectionLabel)
        assertTrue(
            donde.isEmpty(),
            "Volvió un SectionLabel (rótulo en mayúsculas). Titula el bloque con GrupoDeCampos o " +
                "Seccion, y el campo con el `rotulo` de Campo:\n" + donde.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `ningun titulo ni rotulo del sistema va en mayusculas`() {
        val ui = LecturaDelCodigo.archivos(LecturaDelCodigo.UI)
        val donde = rotulosQueGritan(ui)
        assertTrue(
            donde.isEmpty(),
            "Rótulos en mayúsculas (van en frase: «Términos», no «TÉRMINOS»):\n" + donde.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `el detector ve lo que grita y deja pasar lo que va en frase`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            private fun SectionLabel(text: String) {}
            internal fun SheetSectionLabel(text: String) {}
            GrupoDeCampos("TÉRMINOS") {}
            Seccion(titulo = "LA PLATA QUE TE ENTRÓ")
            Campo(v, {}, rotulo = "NOMBRE")
            CategoryField(label = "CATEGORÍA")
            RotuloDeCampo("Moneda")
            Campo(v, {}, rotulo = "Tasa E.A.")
            Seccion("Cómo lo reconoce Movi")
            Campo(v, {}, rotulo = "Escribe ${'$'}PALABRA para confirmar")
            // GrupoDeCampos("EN UN COMENTARIO") no cuenta
            """.trimIndent(),
        )
        val archivo = mapOf("Prueba.kt" to codigo)
        assertEquals(2, LecturaDelCodigo.dondeAparece(archivo, definicionDeSectionLabel).size)
        assertEquals(4, rotulosQueGritan(archivo).size, rotulosQueGritan(archivo).toString())
    }
}
