package com.jvillada.movi.web

import org.junit.Test
import kotlin.test.assertTrue

/**
 * # «Algo falló» deja rastro y deja salir (vuelta 96)
 *
 * El 1-nov-2026 una cuenta nueva no podía entrar desde un iPhone: la web cargaba y mostraba el aviso
 * «Algo falló», cuyo único botón recargaba y volvía a caer en lo mismo. No había forma de saber qué
 * excepción era, ni de salir de la sesión sin borrar los datos del sitio desde los Ajustes de Safari.
 *
 * Ahora el aviso (todo en `index.html`, porque el wasm es lo que se cayó):
 * - muestra el texto del primer error, chico, para que una captura lo traiga;
 * - lo manda a `POST /api/errores-del-cliente`, que lo deja en el log del server;
 * - ofrece «Salir y entrar de nuevo», que borra la sesión de este navegador y recarga.
 *
 * Las pruebas no ven la web: se vigila el `index.html`.
 */
class AlgoFalloDejaRastroTest {

    private val html = indexDelWebApp()

    private val aviso: String
        get() {
            val inicio = html.indexOf("<div id=\"crash-overlay\"")
            assertTrue(inicio >= 0, "index.html no tiene el aviso de «Algo falló»")
            return html.substring(inicio, html.indexOf("</div>\n    </div>", inicio))
        }

    @Test
    fun `el aviso tiene la salida y el lugar para el texto del error`() {
        assertTrue("id=\"crash-reload\"" in aviso, "se perdió el botón de recargar")
        assertTrue("id=\"crash-salir\"" in aviso && "Salir y entrar de nuevo" in aviso, "falta «Salir y entrar de nuevo»")
        assertTrue("id=\"crash-detalle\"" in aviso, "falta el lugar del texto del error")
    }

    @Test
    fun `el aviso manda el error al server y lo muestra`() {
        assertTrue("'/api/errores-del-cliente'" in html, "el aviso no reporta el error al server")
        assertTrue("getElementById('crash-detalle')" in html && "d.textContent = primerError" in html, "el aviso no muestra el error")
        // Las tres puertas por las que llega una caída pasan su texto.
        assertTrue("avisar(textoDe(arguments[0]))" in html, "console.error no pasa su texto")
        assertTrue("avisar(textoDe(ev.error || ev.message))" in html, "window.error no pasa su texto")
        assertTrue("avisar(textoDe(ev.reason))" in html, "unhandledrejection no pasa su texto")
    }

    @Test
    fun `salir borra la sesion de este navegador pero no el correo recordado`() {
        val inicio = html.indexOf("function salir()")
        assertTrue(inicio >= 0, "falta la función salir")
        val cuerpo = html.substring(inicio, html.indexOf("function avisar(", inicio))
        for (clave in listOf("auth_token", "user_id", "user_email", "user_name")) {
            assertTrue("'$clave'" in cuerpo, "salir no borra $clave")
        }
        assertTrue("remembered_email" !in cuerpo, "salir no debe tocar el correo recordado")
        assertTrue("window.location.reload()" in cuerpo, "salir tiene que recargar para que aparezca el ingreso")
    }

    @Test
    fun `el reporte nunca lleva el token`() {
        val inicio = html.indexOf("function reportar(")
        val cuerpo = html.substring(inicio, html.indexOf("function salir()", inicio))
        assertTrue("auth_token" !in cuerpo, "el reporte de errores no puede llevar el token")
    }
}
