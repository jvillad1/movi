package com.jvillada.movi.observabilidad

import com.jvillada.movi.fallos.CapturaDeFallos
import com.jvillada.movi.shared.model.FalloDeLaApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Vuelta 100 · los fallos de Android: el handler guarda en disco y deja seguir al del sistema, y la
 * subida manda cada reporte una vez (lo subido se borra; lo que no subió espera). Ver [CapturaDeFallos].
 */
class CapturaDeFallosTest {

    private lateinit var dir: File
    private var previoOriginal: Thread.UncaughtExceptionHandler? = null

    @Before fun preparar() {
        dir = Files.createTempDirectory("fallos").toFile()
        previoOriginal = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After fun limpiar() {
        Thread.setDefaultUncaughtExceptionHandler(previoOriginal)
        dir.deleteRecursively()
    }

    @Test
    fun elHandlerGuardaLimpioYDejaSeguirAlDelSistema() {
        var alSistema: Throwable? = null
        CapturaDeFallos.instalar(dir, previo = { _, e -> alSistema = e })
        val error = NumberFormatException("For input string: \"27500000\" en la cuenta 0123456789")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)

        assertTrue("el handler del sistema no recibió el fallo", alSistema === error)
        val archivo = CapturaDeFallos.guardados(dir).single()
        val texto = archivo.readText()
        assertFalse("el monto quedó en disco", "27500000" in texto)
        assertFalse("la cuenta quedó en disco", "0123456789" in texto)
        assertTrue(texto.contains("NumberFormatException"))
        assertTrue("el componente es la clase propia de arriba", texto.contains("\"componente\":\"CapturaDeFallosTest\""))
    }

    @Test
    fun siGuardarFallaElSistemaIgualSeEntera() {
        var alSistema = false
        val noEsCarpeta = File(dir, "archivo").apply { writeText("x") }
        CapturaDeFallos.instalar(File(noEsCarpeta, "adentro"), previo = { _, _ -> alSistema = true })
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), IllegalStateException("x"))
        assertTrue(alSistema)
    }

    private fun guardarUno(): FalloDeLaApp =
        CapturaDeFallos.armar(IllegalStateException("x"), "main", atrapado = false).also { CapturaDeFallos.guardar(dir, it) }

    @Test
    fun laSubidaMandaCadaReporteUnaVez() = runBlocking {
        val uno = guardarUno()
        val mandados = mutableListOf<String>()
        val enviar: suspend (FalloDeLaApp) -> CapturaDeFallos.Envio = { mandados += it.id; CapturaDeFallos.Envio.SUBIDO }
        assertEquals(0, CapturaDeFallos.subirLosFallosGuardados(dir, enviar))
        assertEquals(0, CapturaDeFallos.subirLosFallosGuardados(dir, enviar))
        assertEquals("el segundo arranque no vuelve a mandarlo", listOf(uno.id), mandados)
    }

    @Test
    fun loQueNoSubeEsperaYLoRechazadoSeBorra() = runBlocking {
        guardarUno()
        assertEquals(1, CapturaDeFallos.subirLosFallosGuardados(dir) { CapturaDeFallos.Envio.REINTENTAR })
        assertEquals(1, CapturaDeFallos.guardados(dir).size)
        assertEquals(1, CapturaDeFallos.subirLosFallosGuardados(dir) { throw IOException("sin red") })
        assertEquals(0, CapturaDeFallos.subirLosFallosGuardados(dir) { CapturaDeFallos.Envio.RECHAZADO })
        assertTrue(CapturaDeFallos.guardados(dir).isEmpty())
    }

    @Test
    fun noGuardaMasQueElTope() {
        repeat(CapturaDeFallos.MAX_GUARDADOS + 5) { guardarUno() }
        assertEquals(CapturaDeFallos.MAX_GUARDADOS, CapturaDeFallos.guardados(dir).size)
    }

    @Test
    fun loAtrapadoSeReportaSalvoLaRed() {
        assertTrue(CapturaDeFallos.vaAlReporte(IllegalStateException("x")))
        assertFalse(CapturaDeFallos.vaAlReporte(IOException("sin señal")))
        assertFalse(CapturaDeFallos.vaAlReporte(kotlinx.coroutines.CancellationException("x")))
        val atrapado = CapturaDeFallos.armar(IllegalStateException("x"), "w", atrapado = true, componente = "AvisoDeCierreWorker")
        assertEquals("AvisoDeCierreWorker", atrapado.componente)
        assertTrue(atrapado.atrapado)
    }
}
