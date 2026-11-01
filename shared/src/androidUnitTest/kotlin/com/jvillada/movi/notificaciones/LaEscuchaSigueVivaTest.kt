package com.jvillada.movi.notificaciones

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 92 · **Que la captura de notificaciones no se caiga callada.** La regla de la vigía, la de
 * «¿está conectado?» y la frase que lo dice en Captura del banco, sin Android de por medio.
 */
class LaEscuchaSigueVivaTest {

    private val conexion = 1_761_000_000_000L
    private val hora = 60L * 60 * 1000

    // ── ¿Está conectado? ──────────────────────────────────────────────────────

    @Test
    fun `conectado en este proceso y sin desconexion posterior esta conectado`() {
        assertTrue(laEscuchaEstaConectada(conexion, 0L, procesoDeLaConexion = 42, procesoActual = 42))
    }

    @Test
    fun `una desconexion posterior lo deja desconectado`() {
        assertFalse(laEscuchaEstaConectada(conexion, conexion + 1, 42, 42))
    }

    @Test
    fun `una conexion anotada por otro proceso no vale - el proceso murio sin avisar`() {
        // Tras una actualización de la app, el proceso viejo no alcanzó a llamar onListenerDisconnected.
        assertFalse(laEscuchaEstaConectada(conexion, 0L, procesoDeLaConexion = 41, procesoActual = 42))
    }

    @Test
    fun `nunca conectado no esta conectado`() {
        assertFalse(laEscuchaEstaConectada(0L, 0L, -1, 42))
    }

    // ── La vigía ──────────────────────────────────────────────────────────────

    @Test
    fun `concedido y conectado - nada`() {
        assertEquals(LoQueHaceLaVigia.NADA, queHaceLaVigia(concedido = true, conectada = true, ultimaConexion = conexion, huellaYaAvisada = null))
    }

    @Test
    fun `concedido y desconectado - reconectar, sin avisar`() {
        assertEquals(LoQueHaceLaVigia.RECONECTAR, queHaceLaVigia(concedido = true, conectada = false, ultimaConexion = conexion, huellaYaAvisada = null))
        // Aunque nunca se haya conectado (recién concedido y Android no lo enchufó).
        assertEquals(LoQueHaceLaVigia.RECONECTAR, queHaceLaVigia(concedido = true, conectada = false, ultimaConexion = 0L, huellaYaAvisada = null))
    }

    @Test
    fun `revocado despues de haber funcionado - avisa`() {
        assertEquals(
            LoQueHaceLaVigia.AVISAR_QUE_SE_REVOCO,
            queHaceLaVigia(concedido = false, conectada = false, ultimaConexion = conexion, huellaYaAvisada = null),
        )
    }

    @Test
    fun `la misma huella no suena dos veces`() {
        val huella = huellaDelAccesoRevocado(conexion)
        assertEquals(LoQueHaceLaVigia.NADA, queHaceLaVigia(false, false, conexion, huellaYaAvisada = huella))
    }

    @Test
    fun `una revocacion nueva, despues de volver a concederlo, si suena`() {
        val huellaVieja = huellaDelAccesoRevocado(conexion)
        // Lo volvió a conceder, el listener se conectó (otra marca) y después se revocó otra vez.
        assertEquals(
            LoQueHaceLaVigia.AVISAR_QUE_SE_REVOCO,
            queHaceLaVigia(false, false, ultimaConexion = conexion + 3 * hora, huellaYaAvisada = huellaVieja),
        )
    }

    @Test
    fun `nunca concedido - no hay notificacion, para eso esta la tarjeta`() {
        assertEquals(LoQueHaceLaVigia.NADA, queHaceLaVigia(false, false, ultimaConexion = 0L, huellaYaAvisada = null))
    }

    @Test
    fun `la notificacion dice lo que paso y ofrece Activar`() {
        assertEquals("Movi dejó de leer las notificaciones del banco", TITULO_DEL_ACCESO_REVOCADO)
        assertEquals("Activar", BOTON_ACTIVAR_EL_ACCESO)
    }

    // ── La frase de la sección ───────────────────────────────────────────────

    @Test
    fun `sin acceso no hay frase - la tarjeta ya dice Falta`() {
        assertNull(textoDelEstadoDeLaCaptura(concedido = false, conectada = false, ultimaDelBanco = 0L, ahora = conexion))
    }

    @Test
    fun `conectada con la ultima notificacion del banco hace 2 horas`() {
        val estado = textoDelEstadoDeLaCaptura(true, true, ultimaDelBanco = conexion - 2 * hora, ahora = conexion)!!
        assertTrue(estado.conectada)
        assertEquals("Conectada · última notificación del banco hace 2 horas", estado.texto)
    }

    @Test
    fun `conectada sin ninguna notificacion todavia`() {
        val estado = textoDelEstadoDeLaCaptura(true, true, ultimaDelBanco = 0L, ahora = conexion)!!
        assertEquals("Conectada · todavía no llega ninguna notificación del banco", estado.texto)
    }

    @Test
    fun `desconectada lo dice y no habla de la ultima notificacion`() {
        val estado = textoDelEstadoDeLaCaptura(true, false, ultimaDelBanco = conexion - hora, ahora = conexion)!!
        assertFalse(estado.conectada)
        assertTrue(estado.texto.startsWith("Desconectada · "), estado.texto)
        assertEquals("Reconectar", BOTON_RECONECTAR)
    }

    // ── El ahorro de batería ─────────────────────────────────────────────────

    @Test
    fun `la exencion de bateria se ofrece solo si falta, existe y hay captura`() {
        assertTrue(debeOfrecerLaExencionDeBateria(sdkInt = 34, exenta = false, hayCaptura = true))
        assertFalse(debeOfrecerLaExencionDeBateria(34, exenta = true, hayCaptura = true))
        assertFalse(debeOfrecerLaExencionDeBateria(34, exenta = false, hayCaptura = false))
        assertFalse(debeOfrecerLaExencionDeBateria(22, exenta = false, hayCaptura = true))
    }
}
