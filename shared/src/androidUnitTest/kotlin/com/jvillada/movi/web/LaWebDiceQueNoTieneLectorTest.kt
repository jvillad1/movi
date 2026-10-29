package com.jvillada.movi.web

import com.jvillada.movi.platform.LECTOR_DE_PANTALLA_EN_LA_WEB
import com.jvillada.movi.platform.elLectorDePantallaLeeLaApp
import com.jvillada.movi.platform.notaDelLectorDePantalla
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # La web dice que un lector de pantalla no la lee (vuelta 55, accesibilidad)
 *
 * La revisión del 28-oct encontró que la web no tiene árbol de accesibilidad: toda la app es un
 * `<canvas>` sin `role` ni `aria-label`. Compose Multiplatform 1.8 (la de Movi) no pasa la semántica
 * al DOM; la 1.9 la trae en experimental, pero con `ComposeViewport` en vez de `CanvasBasedWindow` y
 * todo Compose de versión nueva: no es un cambio menor. Mientras tanto, la web **lo dice**:
 *
 * - el canvas tiene `role="application"` y `aria-label="Movi"`, y lo describe el aviso;
 * - el aviso, oculto a la vista (no `display: none`, que también lo callaría), es lo primero del
 *   `<body>` y dice lo mismo que la nota de Ajustes → «Cómo se ve»;
 * - esa nota solo aparece donde el lector no lee la app (la web; en Android sí lo lee).
 *
 * Las pruebas no ven la web: lo que se vigila es el `index.html` y la regla.
 */
class LaWebDiceQueNoTieneLectorTest {

    private val html = indexDelWebApp()

    @Test
    fun `el canvas tiene nombre, rol y el aviso como descripcion`() {
        val canvas = Regex("""<canvas id="ComposeTarget"[^>]*>""").find(html)?.value
            ?: error("index.html no tiene el canvas ComposeTarget")
        assertTrue("""role="application"""" in canvas, "el canvas no tiene role=application: $canvas")
        assertTrue("""aria-label="Movi"""" in canvas, "el canvas no se llama Movi: $canvas")
        assertTrue("""aria-describedby="aviso-lector"""" in canvas, "el canvas no apunta al aviso: $canvas")
    }

    @Test
    fun `el aviso para el lector dice lo mismo que Ajustes y va antes del canvas`() {
        val aviso = Regex("""<p id="aviso-lector" class="solo-lector">([^<]*)</p>""").find(html)
            ?: error("index.html no tiene el aviso para el lector de pantalla")
        assertEquals(LECTOR_DE_PANTALLA_EN_LA_WEB, aviso.groupValues[1])
        assertTrue(aviso.range.first < html.indexOf("<canvas id="), "el aviso tiene que ir antes del canvas")
    }

    @Test
    fun `el aviso esta oculto a la vista pero no al lector`() {
        val inicio = html.indexOf(".solo-lector {")
        assertTrue(inicio >= 0, "index.html no define .solo-lector")
        val bloque = html.substring(inicio, html.indexOf('}', inicio))
        assertTrue("display: none" !in bloque && "visibility: hidden" !in bloque, "el aviso quedó callado para el lector: $bloque")
        assertTrue("clip: rect(0, 0, 0, 0)" in bloque && "width: 1px" in bloque, "el aviso no está fuera de la vista: $bloque")
    }

    @Test
    fun `la nota de Ajustes sale solo donde el lector no lee la app`() {
        assertEquals(LECTOR_DE_PANTALLA_EN_LA_WEB, notaDelLectorDePantalla(leeLaApp = false))
        assertNull(notaDelLectorDePantalla(leeLaApp = true))
        // Estas pruebas corren en Android, donde TalkBack sí lee la app: ahí no hay nota.
        assertTrue(elLectorDePantallaLeeLaApp)
    }
}
