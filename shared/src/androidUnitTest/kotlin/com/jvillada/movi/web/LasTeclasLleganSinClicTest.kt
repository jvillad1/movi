package com.jvillada.movi.web

import org.junit.Test
import kotlin.test.assertTrue

/**
 * # Las teclas le llegan a Movi sin un clic antes (vuelta 118)
 *
 * Compose 1.8 escucha el teclado en el `<canvas>`, y el navegador solo le manda las teclas a lo que
 * tiene el foco. Recién cargada la página (con sesión, o al esconder el ingreso) el foco quedaba en el
 * `<body>`: ↓ y Av Pág no hacían nada hasta hacer clic. `enfocarMovi` le da el foco al lienzo sin
 * desplazar la página y sin quitárselo a un campo.
 *
 * La lógica es JavaScript del `index.html` y estas pruebas no corren un navegador: se vigila el
 * archivo. Lo de punta a punta se miró con Playwright sobre la web armada en Actions (ver el reporte de
 * la vuelta 118).
 */
class LasTeclasLleganSinClicTest {

    private val html = indexDelWebApp()

    private fun cuerpoDe(firma: String): String {
        val inicio = html.indexOf(firma)
        assertTrue(inicio >= 0, "index.html no tiene $firma")
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
        return html.substring(desde, i + 1)
    }

    @Test
    fun `el lienzo se puede enfocar desde el HTML, sin esperar al wasm`() {
        val canvas = Regex("""<canvas id="ComposeTarget"[^>]*>""").find(html)?.value ?: error("no está el canvas")
        assertTrue("""tabindex="0"""" in canvas, canvas)
        assertTrue("#ComposeTarget:focus { outline: none; }" in html, "el navegador no dibuja un marco alrededor de la ventana")
    }

    @Test
    fun `enfocarMovi no desplaza la pagina ni le quita el foco a nadie`() {
        val f = cuerpoDe("window.enfocarMovi = function () {")
        assertTrue("preventScroll: true" in f, f)
        // Solo con el foco en ningún lado, en el body o en el ingreso ya escondido.
        assertTrue("a === document.body" in f && "if (!libre) return;" in f, f)
        assertTrue("o.classList.contains('hidden')" in f, f)
    }

    @Test
    fun `se llama al cargar con sesion y al esconder el ingreso`() {
        val esconder = cuerpoDe("window.ponerElIngreso = function (visible) {").substringAfter("} else {")
        assertTrue("enfocarMovi();" in esconder, "al esconder el ingreso: $esconder")
        // El arranque: con ?reset=… o sin sesión enfoca el campo del ingreso; si no, Movi.
        val arranque = html.substringAfter("var hasToken = !!localStorage.getItem('auth_token');").substringBefore("form.addEventListener('submit'")
        val conSesion = arranque.substringAfter("(remembered ? passEl : emailEl).focus(); }, 50);")
        assertTrue("} else {" in conSesion && "enfocarMovi();" in conSesion, "con sesión: $conSesion")
    }
}
