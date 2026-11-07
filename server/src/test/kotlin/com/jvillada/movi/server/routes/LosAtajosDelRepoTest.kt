package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.ARCHIVO_DEL_ATAJO_DE_APPLE_PAY
import com.jvillada.movi.shared.model.ARCHIVO_DEL_ATAJO_DE_MENSAJES
import com.jvillada.movi.shared.model.ARCHIVO_DEL_ATAJO_DE_NOTIFICACIONES
import com.jvillada.movi.shared.model.NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES
import com.jvillada.movi.shared.model.RUTA_DEL_ATAJO_DE_NOTIFICACIONES
import com.jvillada.movi.shared.model.TIPO_DE_NOTIFICACION
import com.jvillada.movi.shared.model.NOMBRE_DEL_ATAJO_DE_APPLE_PAY
import com.jvillada.movi.shared.model.NOMBRE_DEL_ATAJO_DE_MENSAJES
import com.jvillada.movi.shared.model.RUTA_DEL_ATAJO_DE_APPLE_PAY
import com.jvillada.movi.shared.model.RUTA_DEL_ATAJO_DE_MENSAJES
import com.jvillada.movi.shared.model.RUTA_DE_LA_CAPTURA_POR_ATAJO
import com.jvillada.movi.shared.model.rutaDelArchivoDeAtajo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vuelta 111 · los plist que escribe `scripts/generar-atajos.py` (y que van al repo) apuntan a producción,
 * mandan el código en la cabecera y preguntan por él al importar. Si alguien cambia la ruta pública sin
 * volver a generar los atajos, esto se cae. El script además pasa `plutil -lint` y la firma.
 */
class LosAtajosDelRepoTest {

    private val carpeta = listOf(File("../scripts/atajos"), File("scripts/atajos")).first { it.isDirectory }

    private fun plist(nombre: String) = File(carpeta, "$nombre.plist").readText()

    @Test
    fun `los dos atajos mandan a la ruta de produccion con el codigo en la cabecera`() {
        listOf("movi-mensaje", "movi-notificacion", "movi-apple-pay").forEach { nombre ->
            val texto = plist(nombre)
            assertTrue(texto.startsWith("<?xml"), "$nombre es un plist XML")
            assertTrue(
                "<string>https://movi-project-production.up.railway.app$RUTA_DE_LA_CAPTURA_POR_ATAJO</string>" in texto,
                "$nombre apunta a producción",
            )
            assertTrue("is.workflow.actions.downloadurl" in texto, "$nombre usa «Obtener contenido de URL»")
            assertTrue("<string>Authorization</string>" in texto && "<string>Bearer ￼</string>" in texto, "$nombre manda Bearer <código>")
            assertTrue("<key>WFWorkflowImportQuestions</key>" in texto && "WFTextActionText" in texto, "$nombre pide el código al importarlo")
            assertTrue("<string>POST</string>" in texto && "<string>JSON</string>" in texto)
            // El código nunca viaja en la URL.
            assertTrue(Regex("""captura/atajo\?""").find(texto) == null)
        }
        assertTrue("<string>mensaje</string>" in plist("movi-mensaje"))
        assertTrue("<string>$TIPO_DE_NOTIFICACION</string>" in plist("movi-notificacion"))
        assertFalse("<string>mensaje</string>" in plist("movi-notificacion"))
        assertTrue("<string>transaccion</string>" in plist("movi-apple-pay"))
    }

    private val recursos = listOf(File("src/main/resources/atajos"), File("server/src/main/resources/atajos")).first { it.isDirectory }

    @Test
    fun `el atajo de mensajes firmado esta en los recursos del server, con el nombre del atajo`() {
        val firmado = File(recursos, ARCHIVO_DEL_ATAJO_DE_MENSAJES)
        assertTrue(firmado.exists(), "el firmado se llama «$ARCHIVO_DEL_ATAJO_DE_MENSAJES»")
        assertTrue(firmado.readBytes().copyOfRange(0, 4).contentEquals("AEA1".toByteArray()))
        // Ni el de la vuelta 111 con el nombre viejo ni el de Apple Pay (que no se firma).
        assertFalse(File(recursos, "movi-mensaje.shortcut").exists())
        assertFalse(File(recursos, ARCHIVO_DEL_ATAJO_DE_APPLE_PAY).exists())
    }

    /**
     * Vuelta 137 · el atajo de notificaciones: firmado, con el nombre de la constante (ASCII, sin la tilde de
     * «notificación»), el mismo del script, y manda `tipo` = `notificacion`.
     */
    @Test
    fun `el atajo de notificaciones esta firmado, se llama como la constante y manda tipo notificacion`() {
        val firmado = File(recursos, ARCHIVO_DEL_ATAJO_DE_NOTIFICACIONES)
        assertTrue(firmado.exists(), "el firmado se llama «$ARCHIVO_DEL_ATAJO_DE_NOTIFICACIONES»")
        assertTrue(firmado.readBytes().copyOfRange(0, 4).contentEquals("AEA1".toByteArray()))
        assertEquals("Movi - Avisar una notificacion", NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES)
        assertEquals("$NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES.shortcut", ARCHIVO_DEL_ATAJO_DE_NOTIFICACIONES)
        assertEquals(rutaDelArchivoDeAtajo(ARCHIVO_DEL_ATAJO_DE_NOTIFICACIONES), RUTA_DEL_ATAJO_DE_NOTIFICACIONES)
        assertTrue(NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES.all { it.code in 0x20..0x7E } && '/' !in NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES)
        val script = listOf(File("../scripts/generar-atajos.py"), File("scripts/generar-atajos.py")).first { it.exists() }.readText()
        assertEquals(
            NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES,
            Regex("""^NOMBRE_DE_NOTIFICACIONES = "([^"]+)"$""", RegexOption.MULTILINE).find(script)?.groupValues?.get(1),
        )
        val texto = plist("movi-notificacion")
        assertTrue("<string>$NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES. Manda a Movi" in texto)
        // El cuerpo: «tipo» = «notificacion» y «texto» = la entrada del atajo.
        assertTrue(Regex("""<string>tipo</string>[\s\S]{0,400}?<string>notificacion</string>""").containsMatchIn(texto))
        assertTrue("<string>texto</string>" in texto && "ExtensionInput" in texto)
    }

    /**
     * Vuelta 114 · Atajos instala el atajo con el nombre del archivo: el nombre que dice la guía de la app
     * (`NOMBRE_DEL_ATAJO_DE_*`), el del generador y el del archivo firmado tienen que ser el mismo.
     */
    @Test
    fun `el nombre del atajo en la guia es el del archivo y el del generador`() {
        val script = listOf(File("../scripts/generar-atajos.py"), File("scripts/generar-atajos.py")).first { it.exists() }.readText()
        fun delScript(constante: String) =
            Regex("""^$constante = "([^"]+)"$""", RegexOption.MULTILINE).find(script)?.groupValues?.get(1)
        assertEquals(NOMBRE_DEL_ATAJO_DE_MENSAJES, delScript("NOMBRE_DE_MENSAJES"))
        assertEquals(NOMBRE_DEL_ATAJO_DE_APPLE_PAY, delScript("NOMBRE_DE_APPLE_PAY"))
        assertEquals("$NOMBRE_DEL_ATAJO_DE_MENSAJES.shortcut", ARCHIVO_DEL_ATAJO_DE_MENSAJES)
        assertEquals(rutaDelArchivoDeAtajo(ARCHIVO_DEL_ATAJO_DE_MENSAJES), RUTA_DEL_ATAJO_DE_MENSAJES)
        assertEquals(rutaDelArchivoDeAtajo(ARCHIVO_DEL_ATAJO_DE_APPLE_PAY), RUTA_DEL_ATAJO_DE_APPLE_PAY)
        // Un nombre de archivo que sobrevive a cualquier navegador: ASCII, sin «·» ni barras.
        listOf(NOMBRE_DEL_ATAJO_DE_MENSAJES, NOMBRE_DEL_ATAJO_DE_APPLE_PAY).forEach { nombre ->
            assertTrue(nombre.all { it.code in 0x20..0x7E } && '/' !in nombre && '"' !in nombre, nombre)
        }
        // El comentario del atajo firmado lo nombra igual.
        assertTrue("<string>$NOMBRE_DEL_ATAJO_DE_MENSAJES. Manda a Movi" in plist("movi-mensaje"))
    }
}
