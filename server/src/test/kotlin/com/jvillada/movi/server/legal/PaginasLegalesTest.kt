package com.jvillada.movi.server.legal

import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.server.routes.paginasLegalesRoutes
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vuelta 109 · `/privacidad`, `/terminos` y `/borrar-cuenta`: públicas, en HTML, con los colores de la
 * app, y con el responsable y el correo que digan las variables de entorno — o un marcador visible, nunca
 * un correo inventado.
 */
class PaginasLegalesTest {

    private val rutas = listOf(PaginasLegales.RUTA_PRIVACIDAD, PaginasLegales.RUTA_TERMINOS, PaginasLegales.RUTA_BORRAR_CUENTA)

    @BeforeTest @AfterTest
    fun limpiar() {
        System.clearProperty("movi.legal.responsable")
        System.clearProperty("movi.legal.correo")
    }

    @Test
    fun `las tres contestan 200 en HTML sin sesion y sin scripts`() = testApplication {
        application { routing { paginasLegalesRoutes() } }
        rutas.forEach { ruta ->
            val res = client.get(ruta)
            assertEquals(HttpStatusCode.OK, res.status, ruta)
            assertTrue(res.contentType()!!.match(ContentType.Text.Html), "$ruta no es HTML")
            val csp = res.headers["Content-Security-Policy"].orEmpty()
            assertTrue("default-src 'none'" in csp && "script-src" !in csp, "$ruta deja correr scripts: $csp")
            assertEquals("no-cache", res.headers[HttpHeaders.CacheControl], ruta)
            val html = res.bodyAsText()
            assertTrue(html.startsWith("<!doctype html>"), ruta)
            assertFalse("<script" in html, "$ruta trae un script")
            // Las tres se enlazan entre sí.
            rutas.forEach { otra -> assertTrue("href=\"$otra\"" in html, "$ruta no enlaza $otra") }
        }
    }

    @Test
    fun `sin las variables de entorno muestran el marcador y ningun correo`() = testApplication {
        application { routing { paginasLegalesRoutes() } }
        rutas.forEach { ruta ->
            val html = client.get(ruta).bodyAsText()
            assertTrue(PaginasLegales.SIN_CORREO in html, "$ruta sin el marcador del correo")
            assertFalse("mailto:" in html, "$ruta inventa un correo")
        }
        assertTrue(PaginasLegales.SIN_RESPONSABLE in client.get(PaginasLegales.RUTA_PRIVACIDAD).bodyAsText())
    }

    @Test
    fun `con las variables de entorno dicen el responsable y el correo, escapados`() = testApplication {
        System.setProperty("movi.legal.responsable", "Ana <Pérez> & Cía")
        System.setProperty("movi.legal.correo", "hola@movi.test")
        application { routing { paginasLegalesRoutes() } }
        val privacidad = client.get(PaginasLegales.RUTA_PRIVACIDAD).bodyAsText()
        assertTrue("Ana &lt;Pérez&gt; &amp; Cía" in privacidad)
        assertFalse("<Pérez>" in privacidad)
        assertTrue("mailto:hola@movi.test" in privacidad)
        assertFalse(PaginasLegales.SIN_CORREO in privacidad)
        assertFalse(PaginasLegales.SIN_RESPONSABLE in privacidad)
        assertTrue("mailto:hola@movi.test" in client.get(PaginasLegales.RUTA_BORRAR_CUENTA).bodyAsText())
        assertTrue("Ana &lt;Pérez&gt; &amp; Cía" in client.get(PaginasLegales.RUTA_TERMINOS).bodyAsText())
    }

    @Test
    fun `un correo sin forma de correo se ignora`() {
        assertTrue(ResponsableDeMovi.pareceCorreo("hola@movi.test"))
        assertFalse(ResponsableDeMovi.pareceCorreo("javascript:alert(1)"))
        assertFalse(ResponsableDeMovi.pareceCorreo("hola movi@x.co"))
        System.setProperty("movi.legal.correo", "no-es-un-correo")
        assertEquals(null, ResponsableDeMovi.leer().correo)
    }

    /** Lo que la política afirma tiene que seguir siendo verdad en el código. */
    @Test
    fun `la politica dice lo que Movi hace`() {
        val html = PaginasLegales.privacidad(ResponsableDeMovi.Datos(null, null))
        assertTrue("Ley 1581 de 2012" in html)
        assertTrue(
            "${Observabilidad.DIAS_DE_RETENCION} días" in html,
            "la retención del uso y los fallos cambió (Observabilidad.DIAS_DE_RETENCION): actualiza /privacidad",
        )
        listOf("Railway", "Anthropic", "Resend", "no vende", "publicidad", "Borrar mi cuenta", "Descargar tus datos")
            .forEach { assertTrue(it in html, "la política no menciona «$it»") }
        // Lo que va a Anthropic, como lo dice el aviso «Qué ve la IA» (vuelta 95) de la app.
        listOf("los mensajes anteriores de esa conversación", "Lo que Movi sabe de ti", "la foto que adjuntes", "ese archivo")
            .forEach { assertTrue(it in html, "la política no dice que a Anthropic le llega «$it»") }
        val aviso = File("../shared/src/commonMain/kotlin/com/jvillada/movi/ui/ai/AvisoDeLaIa.kt").readText()
        listOf("los anteriores de la conversación", "Lo que Movi sabe de ti", "La foto que adjuntes", "el archivo que elegiste")
            .forEach { assertTrue(it in aviso, "el aviso «Qué ve la IA» ya no dice «$it»: alinea la política") }
        // Los SMS solo en la versión que se instala por fuera de la tienda.
        assertTrue("La versión de Google Play no lee SMS" in html)
        // El filtro del teléfono (`BankSenderFilter.matches`) deja pasar por el remitente O por el texto:
        // la política no puede decir que solo cuenta el remitente.
        assertTrue("cuyo remitente es un código bancario conocido o cuyo texto nombra un banco" in html)
        assertFalse("de remitentes bancarios de una lista cerrada; los demás" in html)
        val borrar = PaginasLegales.borrarCuenta(ResponsableDeMovi.Datos(null, null))
        // Lo que borrar no alcanza (copias de los proveedores y respaldos), dicho en las dos páginas.
        assertTrue(PaginasLegales.LO_QUE_MOVI_NO_CONTROLA in borrar)
        assertTrue("copias que no controla" in html && "Resend" in html)
        assertTrue("«Borrar mi cuenta»" in borrar && "BORRAR" in borrar && "No se puede deshacer" in borrar)
        val terminos = PaginasLegales.terminos(ResponsableDeMovi.Datos(null, null))
        listOf("No es un banco", "no mueve plata", "No es asesoría financiera", "puede equivocarse", "ley de Colombia")
            .forEach { assertTrue(it in terminos, "los términos no dicen «$it»") }
    }

    /** «Descargar tus datos» se describe por lo que trae de verdad, no como «todo lo tuyo». */
    @Test
    fun `lo que dice la descarga es lo que exporta el server`() {
        val html = PaginasLegales.privacidad(ResponsableDeMovi.Datos(null, null))
        assertFalse("todo lo tuyo" in html.substringBefore("Borrar tu cuenta y todo lo tuyo"), "la descarga no trae todo")
        assertTrue(PaginasLegales.LO_QUE_TRAE_LA_DESCARGA in html)
        val exportadas = com.jvillada.movi.server.exportar.TABLAS_EXPORTADAS.map { it.nombre }
        // Lo que la frase dice que NO trae no está en el export; lo que dice que trae, sí.
        listOf("conversaciones", "espacios", "gastos_del_espacio").forEach { assertFalse(it in exportadas, it) }
        listOf("cuentas", "movimientos", "creditos", "tarjetas", "mensajes_del_banco", "lo_que_movi_sabe_de_ti", "documentos")
            .forEach { assertTrue(it in exportadas, "$it ya no se exporta: corrige LO_QUE_TRAE_LA_DESCARGA") }
    }

    /** Como la página compartida: la copia de los colores no se puede quedar vieja. */
    @Test
    fun `los colores son los de Tokens kt`() {
        val tokens = File("../shared/src/commonMain/kotlin/com/jvillada/movi/theme/Tokens.kt").readText()
        val bloque = tokens.substringAfter("val COLORES_CLAROS = ColoresDeMovi(").substringBefore("\n)")
        val html = PaginasLegales.privacidad(ResponsableDeMovi.Datos(null, null))
        assertFalse("prefers-color-scheme" in html)
        mapOf(
            "fondo" to "--fondo", "tarjeta" to "--tarjeta", "borde" to "--borde", "hilo" to "--hilo",
            "texto" to "--texto", "textoMedio" to "--texto-medio", "textoApagado" to "--texto-apagado",
            "marca" to "--marca", "enlace" to "--enlace",
        ).forEach { (rol, variable) ->
            val hex = Regex("""\b$rol\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)""").find(bloque)?.groupValues?.get(1)
                ?: error("no encontré $rol en Tokens.kt")
            val enLaPagina = Regex("""$variable:\s*#([0-9A-Fa-f]{6})""").find(html)?.groupValues?.get(1)
                ?: error("la página no define $variable")
            assertEquals(hex.uppercase(), enLaPagina.uppercase(), variable)
        }
    }
}
