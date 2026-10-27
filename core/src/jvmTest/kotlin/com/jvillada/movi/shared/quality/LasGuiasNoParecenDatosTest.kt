package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Un texto guía numérico no parece un dato
 *
 * Vuelta de diseño 17. En la hoja de la tarjeta, «Corte (opcional)» mostraba un «20» gris, «Día de
 * pago» un «5» y la tasa un «29,6»; un monto vacío, «$ 0». Eran textos guía, pero parecían datos ya
 * cargados. La regla (`docs/diseno/sistema.md` §15): **un ejemplo numérico va con «Ej:»**, y se pasa
 * con `ejemplo = "20"` —el campo antepone «Ej: »—, no con `guia = "20"`. Un monto vacío muestra la
 * moneda y nada más.
 *
 * El campo ya corrige una guía numérica suelta (`textoGuiaDelCampo`), pero esta prueba no deja que
 * aparezcan en el código: el que lee la llamada tiene que ver lo mismo que ve el dueño.
 *
 * **Si te falló**: cambia `guia = "20"` por `ejemplo = "20"` (o `placeholder = "$ 0"` por nada). Y
 * `ejemplo` va sin el «Ej:», que lo pone el campo.
 */
class LasGuiasNoParecenDatosTest {

    /** `guia = "20"`, `placeholder = "$ 0"`, `guia = "29,6"`, `guia = "1.250.000"`. */
    private val guiaNumerica = Regex("""\b(?:guia|placeholder)\s*=\s*"(?:US)?\$?\s*\d[\d.,%\s]*"""")

    /** `ejemplo = "Ej: 20"`: el campo ya antepone «Ej: », saldría dos veces. */
    private val ejemploConPrefijo = Regex("""\bejemplo\s*=\s*"\s*Ej""")

    private fun ui() = LecturaDelCodigo.archivos(LecturaDelCodigo.UI)

    @Test
    fun `ningun campo lleva una guia puramente numerica`() {
        val vistos = LecturaDelCodigo.dondeAparece(ui(), guiaNumerica)
        assertTrue(
            vistos.isEmpty(),
            "Guías numéricas sueltas (usa ejemplo = \"…\", que pinta «Ej: …»):\n" + vistos.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `ningun ejemplo trae su propio Ej`() {
        val vistos = LecturaDelCodigo.dondeAparece(ui(), ejemploConPrefijo)
        assertTrue(vistos.isEmpty(), "El «Ej: » lo pone el campo:\n" + vistos.joinToString("\n") { "  $it" })
    }

    @Test
    fun `el detector ve las guias numericas y deja pasar las que son ejemplos con palabras`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            Campo(dia, {}, rotulo = "Día de pago", guia = "5")
            Campo(corte, {}, guia = "20")
            CampoDeTasa(tasa, {}, rotulo = "Tasa", guia = "29,6")
            MoneyField(monto, {}, placeholder = "$ 0")
            CampoDeMonto(monto, {}, guia = "1.250.000")
            Campo(banco, {}, guia = "Ej: Bancolombia")
            Campo(dia, {}, ejemplo = "5")
            Campo(fecha, {}, guia = "AAAA-MM-DD")
            MoneyField(abono, {}, placeholder = "Abono extra al mes, por ejemplo ${'$'}500.000")
            // Campo(dia, {}, guia = "5") en un comentario no cuenta
            """.trimIndent(),
        )
        val vistos = LecturaDelCodigo.dondeAparece(mapOf("Prueba.kt" to codigo), guiaNumerica)
        assertEquals(listOf("Prueba.kt:1", "Prueba.kt:2", "Prueba.kt:3", "Prueba.kt:4", "Prueba.kt:5"), vistos)
    }
}
