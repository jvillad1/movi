package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 79 · un solo presionado
 *
 * La revisión visual 7: al presionar una acción de Inicio aparecía una caja gris de esquinas rectas sobre
 * el círculo y el rótulo, las pestañas mostraban un rectángulo en hover y al presionar, y el botón
 * amarillo se volvía mostaza. Todo era el ripple de Material, que no sabe de la forma de lo que se toca.
 *
 * Desde la vuelta 79, en `ds/`, la barra de pestañas y el rail **todo toque pasa por `alPresionar`** (o
 * `alAlternar`, o el par `hundimientoAlPresionar` + `toqueSinRipple`), que van sin ripple y se hunden. Esta
 * guarda está en **cero**: falla si en esos archivos aparece un `clickable` / `combinedClickable` /
 * `toggleable` / `selectable` suelto, un `ripple(`, un `rememberRipple` o un `LocalIndication`. Solo
 * `ds/AlPresionar.kt`, que es donde vive el toque, puede nombrarlos.
 */
class ElToqueEsDelSistemaTest {

    private val prohibido = Regex(
        """\.(?:clickable|combinedClickable|toggleable|selectable)\s*[({]|\bripple\s*\(|\brememberRipple\b|\bLocalIndication\b""",
    )

    private val vecinos = listOf("MinBottomNav.kt", "MinNavRail.kt").map { "${LecturaDelCodigo.UI}/components/$it" }

    private fun archivos(): Map<String, String> {
        val raiz = LecturaDelCodigo.raiz()
        return LecturaDelCodigo.archivos(LecturaDelCodigo.DS).filterKeys { !it.endsWith("/AlPresionar.kt") } +
            vecinos.associateWith { LecturaDelCodigo.sinComentarios(raiz.resolve(it).readText()) }
    }

    @Test
    fun `en ds, la barra y el rail ningun toque va suelto`() {
        val donde = LecturaDelCodigo.dondeAparece(archivos(), prohibido)
        assertTrue(
            donde.isEmpty(),
            "Un toque con el ripple de Material (o sin pasar por el componente). Usa `alPresionar` " +
                "(ds/AlPresionar.kt):\n" + donde.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `el toque del sistema existe y no lleva ripple`() {
        val toque = LecturaDelCodigo.archivos(LecturaDelCodigo.DS).entries.single { it.key.endsWith("/AlPresionar.kt") }.value
        assertTrue("fun Modifier.alPresionar(" in toque)
        val indicaciones = Regex("""indication\s*=\s*([^,\n)]+)""").findAll(toque).map { it.groupValues[1].trim() }.toList()
        assertTrue(indicaciones.isNotEmpty(), "no encontré el `indication =` del toque")
        assertEquals(setOf("null"), indicaciones.toSet(), "el toque del sistema va sin indicación")
    }

    @Test
    fun `el detector ve un toque suelto y deja pasar el del sistema`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            Modifier.clickable(onClick = f)
            Modifier.alPresionar(onClick = f)
            Modifier.clickable { f() }
            val r = ripple(bounded = false)
            // .clickable(en un comentario no cuenta)
            """.trimIndent(),
        )
        assertEquals(listOf("x.kt:1", "x.kt:3", "x.kt:4"), LecturaDelCodigo.dondeAparece(mapOf("x.kt" to codigo), prohibido))
    }
}
