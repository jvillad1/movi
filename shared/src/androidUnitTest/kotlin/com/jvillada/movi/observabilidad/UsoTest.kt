package com.jvillada.movi.observabilidad

import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.LoteDeUso
import com.jvillada.movi.shared.model.rutaBienFormada
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.primeruso.PasoDePrimerUso
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Vuelta 100 · [Uso]: la lista cerrada, la sesión, la ruta, el lote y el interruptor. */
@RunWith(RobolectricTestRunner::class)
class UsoTest {

    private var ahora = 1_800_000_000_000L
    private val lotes = mutableListOf<LoteDeUso>()

    @Before
    fun preparar() {
        Uso.almacenDePrueba = mutableMapOf()
        Uso.releerElInterruptor()
        Uso.reloj = { ahora }
        Uso.enviadorDePrueba = { lotes += it; true }
        SessionManager.save(token = "t", userId = "u1", name = "U", email = "u@x.co")
    }

    private fun nombres() = Uso.pendientes.map { it.nombre }

    @Test
    fun rechazaLoQueNoTieneLaFormaDeSuClave() {
        assertTrue(Uso.evento(EventoDeUso.PANTALLA, mapOf(ClaveDeUso.PANTALLA to "PorRevisar")))
        assertFalse("un monto en una duración", Uso.evento(EventoDeUso.SEGUNDO_PLANO, mapOf(ClaveDeUso.SEGUNDOS to "27.500")))
        assertFalse("un comercio como pantalla", Uso.evento(EventoDeUso.PANTALLA, mapOf(ClaveDeUso.PANTALLA to "Rappi Colombia")))
        assertFalse("un número de cuenta como id", Uso.evento(EventoDeUso.POR_REVISAR, mapOf(ClaveDeUso.CUENTA_ID to "0123456789")))
        assertEquals(listOf("SESION_EMPEZADA", "PANTALLA"), nombres())
    }

    @Test
    fun laSesionEmpiezaEnFrioSigueAntesDe30MinYCambiaDespues() {
        Uso.alVolverAPrimerPlano()
        val primera = Uso.sesionId
        assertEquals(mapOf("origen" to "FRIO"), Uso.pendientes.first().propiedades)
        ahora += 60_000
        Uso.alIrASegundoPlano()
        assertEquals("60", Uso.pendientes.last().propiedades["segundos"])
        ahora += 10 * 60_000
        Uso.alVolverAPrimerPlano()
        assertEquals("volvió a los 10 min: la misma sesión", primera, Uso.sesionId)
        assertEquals("PRIMER_PLANO", nombres().last())
        Uso.alIrASegundoPlano()
        ahora += 31 * 60_000
        Uso.alVolverAPrimerPlano()
        assertNotEquals("volvió a los 31 min: sesión nueva", primera, Uso.sesionId)
        val ultimo = Uso.pendientes.last()
        assertEquals("SESION_EMPEZADA", ultimo.nombre)
        assertEquals("VOLVIO", ultimo.propiedades["origen"])
        assertEquals("la secuencia arranca de nuevo", 1, ultimo.secuencia)
    }

    @Test
    fun laSecuenciaYLaRutaVanEnCadaEvento() {
        Uso.ponerLaPila(tramosDeLaPila(listOf(Screen.Dashboard, Screen.PorRevisar, Screen.SMSReconcile("sms_rt_9c1d2e3f4a5b6c7d"))))
        Uso.evento(EventoDeUso.TOQUE, mapOf(ClaveDeUso.ACCION to "CONFIRMAR"))
        val e = Uso.pendientes.last()
        assertEquals("Dashboard>PorRevisar>SMSReconcile(sms_rt_9c1d2e3f4a5b6c7d)", e.ruta)
        assertEquals(listOf(1, 2), Uso.pendientes.map { it.secuencia })
        assertEquals("android", e.plataforma)
        Uso.ponerLaPila(listOf("PrimerUso"))
        Uso.ponerElDetalleDeArriba("paso 4")
        assertEquals("PrimerUso(paso 4)", Uso.ruta)
    }

    @Test
    fun todaPantallaDaUnTramoBienFormado() {
        val pantallas = listOf(
            Screen.Dashboard, Screen.PorRevisar, Screen.FallosYUso, Screen.Tu, Screen.PrimerUso(),
            Screen.SMSReconcile("un id que no es de Movi 27.500"), Screen.Espacio("esp_1a2b3c4d5e6f"),
            Screen.DetalleDePeriodo("2026-09"),
        )
        for (p in pantallas) assertTrue("$p → ${tramoDeRuta(p)}", rutaBienFormada(tramoDeRuta(p)))
        assertEquals("SMSReconcile", tramoDeRuta(Screen.SMSReconcile("un id que no es de Movi 27.500")))
        assertEquals("DetalleDePeriodo(2026-09)", tramoDeRuta(Screen.DetalleDePeriodo("2026-09")))
    }

    @Test
    fun losPasosDeLaGuiaEstanEnLaListaCerrada() {
        for (p in PasoDePrimerUso.entries) assertTrue(p.name, ClaveDeUso.PASO.admite(p.name))
    }

    @Test
    fun subeEnLoteYNoRepite() = runBlocking {
        repeat(3) { Uso.evento(EventoDeUso.CUENTA_CREADA) }
        assertEquals(4, Uso.enviar())
        assertEquals(1, lotes.size)
        assertEquals(4, lotes.single().eventos.size)
        assertTrue("lo subido se va de la cola", Uso.pendientes.isEmpty())
        assertEquals(0, Uso.enviar())
        assertEquals(1, lotes.size)
    }

    @Test
    fun loQueNoSubeVuelveAdelanteEnOrden() = runBlocking {
        Uso.enviadorDePrueba = { false }
        Uso.evento(EventoDeUso.CUENTA_CREADA)
        Uso.enviar()
        Uso.evento(EventoDeUso.RECURRENTE_CREADO)
        assertEquals(listOf("SESION_EMPEZADA", "CUENTA_CREADA", "RECURRENTE_CREADO"), nombres())
    }

    @Test
    fun sinSesionNoSaleNada() = runBlocking {
        Uso.evento(EventoDeUso.CUENTA_CREADA)
        SessionManager.clear()
        assertTrue("cerrar la sesión tira lo pendiente", Uso.pendientes.isEmpty())
        Uso.evento(EventoDeUso.CUENTA_CREADA)
        assertEquals(0, Uso.enviar())
        assertTrue(lotes.isEmpty())
    }

    @Test
    fun elInterruptorApagadoNoAnotaNiManda() = runBlocking {
        Uso.evento(EventoDeUso.CUENTA_CREADA)
        Uso.poner(false)
        assertTrue("apagarlo tira lo pendiente", Uso.pendientes.isEmpty())
        assertFalse(Uso.evento(EventoDeUso.CUENTA_CREADA))
        Uso.alVolverAPrimerPlano()
        assertEquals(0, Uso.enviar())
        assertTrue("con el interruptor apagado no sale ningún lote", lotes.isEmpty())
        assertEquals(false, Uso.almacenDePrueba!!["uso_activo"])
        Uso.poner(true)
        assertTrue(Uso.evento(EventoDeUso.CUENTA_CREADA))
    }

    @Test
    fun losErroresDeRedSeAnotanUnaVezPorMinutoYSoloElRecurso() {
        val url = Url("https://movi-project-production.up.railway.app/api/events/ev_9c1d2e3f-0000/parecidos?x=1")
        anotarLaRespuestaParaElUso(422, "POST", url)
        anotarLaRespuestaParaElUso(422, "POST", url)
        anotarLaRespuestaParaElUso(200, "POST", url)
        anotarLaRespuestaParaElUso(500, "GET", url)
        anotarLaRespuestaParaElUso(500, "POST", Url("https://x.co/api/uso"))
        val fallidas = Uso.pendientes.filter { it.nombre == "ESCRITURA_FALLIDA" }
        assertEquals(1, fallidas.size)
        assertEquals(mapOf("codigo" to "422", "metodo" to "POST", "recurso" to "events"), fallidas.single().propiedades)
        ahora += 61_000
        anotarElErrorDeRedParaElUso("POST", url)
        anotarLaRespuestaParaElUso(422, "POST", url)
        assertEquals(listOf("ESCRITURA_FALLIDA", "ERROR_DE_RED", "ESCRITURA_FALLIDA"), nombres().drop(1))
    }
}
