package com.jvillada.movi.web

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Los formularios ocultos del ingreso no estorban (vuelta 55, accesibilidad)
 *
 * El ingreso de la web es HTML (el gestor de contraseñas no ve un canvas) y vive en el DOM toda la
 * sesión: `login-form`, `forgot-form`, `reset-form`, `register-form` y sus botones de «Mostrar
 * contraseña». La revisión del 28-oct los encontró sin `inert` ni `aria-hidden` con el dueño en
 * Inicio. Desde la vuelta 55:
 *
 * - el overlay **arranca** escondido, inerte y oculto al lector (el marcado);
 * - lo muestra y lo esconde **una sola función**, `ponerElIngreso`, que pone y quita la clase
 *   `hidden`, `inert` y `aria-hidden` juntos;
 * - las dos pantallas de entrada (sin sesión, y con `?reset=…`) lo muestran con ella, y nadie más
 *   toca la clase del overlay a mano.
 *
 * La lógica es JavaScript dentro del `index.html` y las pruebas no corren un navegador: se vigila el
 * archivo.
 */
class LosFormulariosOcultosNoEstorbanTest {

    private val html = indexDelWebApp()

    /** El cuerpo de `ponerElIngreso`, de la llave que abre a la que cierra. */
    private val laFuncion: String by lazy {
        val inicio = html.indexOf("window.ponerElIngreso = function (visible) {")
        assertTrue(inicio >= 0, "index.html no tiene window.ponerElIngreso")
        var profundidad = 0
        var i = html.indexOf('{', inicio)
        val desde = i
        while (true) {
            when (html[i]) {
                '{' -> profundidad++
                '}' -> if (--profundidad == 0) break
            }
            i++
        }
        html.substring(desde, i + 1)
    }

    @Test
    fun `el overlay arranca escondido, inerte y oculto al lector`() {
        val div = Regex("""<div id="login-overlay"[^>]*>""").find(html)?.value ?: error("no está el login-overlay")
        assertTrue("""class="hidden"""" in div, div)
        assertTrue(Regex("""\sinert[\s>]""").containsMatchIn(div), "sin inert: $div")
        assertTrue("""aria-hidden="true"""" in div, "sin aria-hidden: $div")
        // Los cuatro formularios viven adentro del overlay: su inert los cubre a todos.
        val dentro = html.substring(html.indexOf(div), html.indexOf("<!-- Aviso de navegador sin WasmGC"))
        listOf("login-form", "forgot-form", "reset-form", "register-form").forEach { form ->
            assertTrue("""<form id="$form"""" in dentro, "$form no está dentro del overlay")
        }
    }

    @Test
    fun `ponerElIngreso pone y quita la clase, inert y aria-hidden juntos`() {
        val f = laFuncion
        assertTrue("o.classList.toggle('hidden', !visible)" in f, f)
        val mostrar = f.substringAfter("if (visible) {").substringBefore("} else {")
        val esconder = f.substringAfter("} else {")
        assertTrue("removeAttribute('inert')" in mostrar && "removeAttribute('aria-hidden')" in mostrar, "al mostrarlo: $mostrar")
        assertTrue("setAttribute('inert', '')" in esconder && "setAttribute('aria-hidden', 'true')" in esconder, "al esconderlo: $esconder")
    }

    @Test
    fun `las pantallas de entrada lo muestran con ponerElIngreso y nadie toca su clase a mano`() {
        // Sin sesión y con ?reset=…: las dos entradas.
        assertEquals(2, Regex("""ponerElIngreso\(true\)""").findAll(html).count(), "las dos entradas lo muestran")
        // La red de seguridad y el navegador sin WasmGC lo esconden por la misma función.
        assertEquals(2, Regex("""ponerElIngreso\(false\)""").findAll(html).count(), "los dos que lo esconden")
        // Fuera de la función, ninguna variable del overlay le cambia la clase.
        val resto = html.replace(laFuncion, "")
        val nombres = Regex("""var (\w+) = document\.getElementById\('login-overlay'\)""").findAll(resto).map { it.groupValues[1] }.toList()
        val aMano = nombres.filter { Regex("""\b$it\.classList\.(?:add|remove|toggle)\(""").containsMatchIn(resto) }
        assertTrue(aMano.isEmpty(), "tocan la clase del overlay sin ponerElIngreso: $aMano")
        assertTrue(
            !Regex("""getElementById\('login-overlay'\)\.classList\.(?:add|remove|toggle)""").containsMatchIn(resto),
            "alguien toca la clase del overlay sin ponerElIngreso",
        )
    }
}
