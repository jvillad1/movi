package com.jvillada.movi.observabilidad

import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vuelta 104 · **los eventos SISTEMA del cliente**: lo de fondo no abre una sesión, la sincronización
 * dice cuánto subió, bajó o con qué código falló, las propuestas se anotan una vez por cambio y el atrás
 * del sistema se distingue de la flecha.
 */
@RunWith(RobolectricTestRunner::class)
class LoQueLaAppHaceSolaTest {

    private var ahora = 1_800_000_000_000L

    @Before
    fun preparar() {
        Uso.almacenDePrueba = mutableMapOf()
        Uso.releerElInterruptor()
        Uso.reloj = { ahora }
    }

    @Test
    fun loDeFondoNoAbreUnaSesion() {
        Uso.eventoDeFondo(EventoDeUso.WORKER, mapOf(ClaveDeUso.WORKER to "SmsSyncWorker", ClaveDeUso.RESULTADO to "OK", ClaveDeUso.MS to "120"))
        assertEquals(listOf("WORKER"), Uso.pendientes.map { it.nombre }, "sin SESION_EMPEZADA")
        assertEquals("", Uso.pendientes.single().sesionId)
        assertEquals("", Uso.sesionId)
        // Con la app abierta, va a la sesión en curso.
        Uso.evento(EventoDeUso.PANTALLA, mapOf(ClaveDeUso.PANTALLA to "Dashboard"))
        Uso.eventoDeFondo(EventoDeUso.NOTIFICACION_MOSTRADA, mapOf(ClaveDeUso.CANAL to "MOVIMIENTOS", ClaveDeUso.CUANTOS to "1"))
        assertEquals(Uso.sesionId, Uso.pendientes.last().sesionId)
        assertTrue(Uso.sesionId.isNotEmpty())
    }

    @Test
    fun laSincronizacionDiceCuantoSubioBajoOConQueFallo() {
        anotarLaSincronizacion(3, null)
        anotarLaSincronizacion(0, 503)
        anotarLoQueBajo(0)
        anotarLoQueBajo(7)
        val sync = Uso.pendientes.filter { it.nombre == "SINCRONIZACION" }.map { it.propiedades }
        assertEquals(
            listOf(
                mapOf("resultado" to "OK", "subidos" to "3"),
                mapOf("resultado" to "FALLO", "subidos" to "0", "codigo" to "503"),
                mapOf("resultado" to "OK", "bajados" to "7"),
            ),
            sync,
        )
    }

    @Test
    fun lasPropuestasSeAnotanUnaVezPorCambio() {
        val hoy = mapOf("PAGO" to 2, "CUADRE" to 1)
        repeat(3) { anotarLasPropuestas(hoy) }
        assertEquals(2, Uso.pendientes.count { it.nombre == "PROPUESTAS_GENERADAS" })
        anotarLasPropuestas(mapOf("PAGO" to 1, "CUADRE" to 1))
        assertEquals(4, Uso.pendientes.count { it.nombre == "PROPUESTAS_GENERADAS" })
        assertEquals(mapOf("propuesta" to "PAGO", "cuantos" to "1"), Uso.pendientes.first { it.nombre == "PROPUESTAS_GENERADAS" && it.propiedades["cuantos"] == "1" && it.propiedades["propuesta"] == "PAGO" }.propiedades)
    }

    @Test
    fun elAtrasDelSistemaNoEsLaFlecha() {
        Uso.atrasDelSistema()
        val atras = Uso.pendientes.last()
        assertEquals("toque:atras", atras.nombre)
        assertEquals("SISTEMA", atras.propiedades["como"])
        // Una hoja que se cierra enseguida sabe que fue por el atrás; un rato después, desde adentro.
        val hoja = HojaDeUso()
        assertEquals("ATRAS", hoja.como(ahora + 300))
        assertEquals("ADENTRO", hoja.como(ahora + 10_000))
        hoja.seCierra("VELO")
        assertEquals("VELO", hoja.como(ahora + 300))
    }

    @Test
    fun conElInterruptorApagadoNadaDeFondo() {
        Uso.poner(false)
        assertFalse(Uso.eventoDeFondo(EventoDeUso.WORKER, mapOf(ClaveDeUso.WORKER to "SmsSyncWorker")))
        Uso.atrasDelSistema()
        anotarLaSincronizacion(1, null)
        assertTrue(Uso.pendientes.isEmpty())
    }

    @Test
    fun unWorkerNoSePegaAUnaSesionVieja() {
        Uso.evento(EventoDeUso.PANTALLA, mapOf(ClaveDeUso.PANTALLA to "Dashboard"))
        val sesion = Uso.sesionId
        Uso.alIrASegundoPlano(ahora)
        // A los 10 min en segundo plano la sesión sigue: lo de fondo va a ella.
        ahora += 10 * 60_000L
        Uso.eventoDeFondo(EventoDeUso.WORKER, mapOf(ClaveDeUso.WORKER to "SmsSyncWorker"))
        assertEquals(sesion, Uso.pendientes.last().sesionId)
        // A los 31 min ya venció: va sin sesión, y no abre otra.
        ahora += 21 * 60_000L
        Uso.eventoDeFondo(EventoDeUso.WORKER, mapOf(ClaveDeUso.WORKER to "AvisoDeCierreWorker"))
        assertEquals("", Uso.pendientes.last().sesionId)
        assertEquals(1, Uso.pendientes.count { it.nombre == "SESION_EMPEZADA" })
        // Al volver, sesión nueva.
        Uso.alVolverAPrimerPlano(ahora)
        assertTrue(Uso.sesionId != sesion)
    }
}
