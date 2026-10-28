package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Las hojas de «Tú» no llevan «Cancelar»
 *
 * Vuelta 41. «Ahorro de cada período» pintaba «Cancelar» + «Guardar» y las demás hojas de Ajustes solo
 * «Guardar» y la ×: dos formas de cerrar la misma hoja. Una hoja ya se cancela con la ×, el arrastre, el
 * velo, Escape y el «atrás» del sistema; su pie lleva **una** acción (`PieDeHoja` sin `secundaria`).
 *
 * La excepción es la confirmación de algo que no tiene vuelta («¿Cerrar sesión en todos los
 * aparatos?»): ahí «Cancelar» es la respuesta segura a una pregunta, no una forma de cerrar. Esas van
 * por `ConfirmarEnHoja` / `ConfirmacionEnLinea` (`ui/components/ConfirmarAntesDeBorrar.kt`), que
 * escriben su «Cancelar» por dentro: en `ui/profile` y `ui/mas` solo aparece su `onCancelar =`.
 *
 * Falla si en esas carpetas vuelve un «Cancelar» escrito a mano o una acción secundaria cuyo único
 * trabajo es cerrar la hoja (`onSecundaria = onDismiss`).
 */
class LasHojasDeTuNoCancelanTest {

    private val carpetas = listOf("${LecturaDelCodigo.UI}/profile", "${LecturaDelCodigo.UI}/mas")

    private val cancelarAMano = Regex(""""Cancelar"|onSecundaria\s*=\s*onDismiss""")

    @Test
    fun `ninguna hoja de profile ni de mas monta un Cancelar fuera de una confirmacion`() {
        val donde = carpetas.flatMap { LecturaDelCodigo.dondeAparece(LecturaDelCodigo.archivos(it), cancelarAMano) }
        assertTrue(
            donde.isEmpty(),
            "Una hoja de «Tú» con «Cancelar» (la × y el arrastre ya cancelan; una confirmación destructiva " +
                "va por ConfirmarEnHoja):\n" + donde.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `el detector ve el Cancelar a mano y deja pasar la confirmacion`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            PieDeHoja("Guardar", ::guardar, secundaria = "Cancelar", onSecundaria = onDismiss)
            PieDeHoja("Guardar", ::guardar, secundaria = "Quitar", onSecundaria = onDismiss)
            ConfirmarEnHoja(pregunta = "¿Cerrar?", onConfirmar = {}, onCancelar = { abierta = false })
            // PieDeHoja("Guardar", secundaria = "Cancelar") en un comentario no cuenta
            """.trimIndent(),
        )
        assertEquals(
            listOf("x:1", "x:1", "x:2"),
            LecturaDelCodigo.dondeAparece(mapOf("x" to codigo), cancelarAMano),
        )
    }
}
