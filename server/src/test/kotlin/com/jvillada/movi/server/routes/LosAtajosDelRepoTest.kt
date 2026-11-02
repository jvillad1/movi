package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.RUTA_DE_LA_CAPTURA_POR_ATAJO
import java.io.File
import kotlin.test.Test
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
        listOf("movi-mensaje", "movi-apple-pay").forEach { nombre ->
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
        assertTrue("<string>transaccion</string>" in plist("movi-apple-pay"))
    }

    @Test
    fun `el atajo de mensajes firmado esta en los recursos del server`() {
        val firmado = listOf(File("src/main/resources/atajos/movi-mensaje.shortcut"), File("server/src/main/resources/atajos/movi-mensaje.shortcut"))
            .first { it.exists() }
        assertTrue(firmado.readBytes().copyOfRange(0, 4).contentEquals("AEA1".toByteArray()))
    }
}
