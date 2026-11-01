package com.jvillada.movi.ui.espacios

import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FormaDeRepartir
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.MotivoDeReparto
import com.jvillada.movi.shared.model.PropuestaDeRepartir
import com.jvillada.movi.shared.model.RepartoDelGasto
import com.jvillada.movi.shared.model.huellaDeNoEsCompartido
import com.jvillada.movi.shared.model.huellaDeNuncaRepartir
import com.jvillada.movi.ui.dashboard.AccionDeLaPropuesta
import com.jvillada.movi.ui.dashboard.AnomaliasDescartadasEnLaSesion
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.TipoDePropuesta
import com.jvillada.movi.ui.dashboard.conteoPorRevisarDelInicio
import com.jvillada.movi.ui.dashboard.propuestasDelInicio
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 97 · el reparto por gasto y la propuesta de repartir, en palabras
 *
 * Lo que dice el selector («Te toca $X · a Caro $Y», «Faltan $X»), con qué arranca cada forma, y cómo se
 * lee la propuesta en «Movi propone hoy» (hasta dos, con sus tres acciones).
 */
class RepartoPorGastoEnLaAppTest {

    private val caro = MiembroDelEspacio("dst_caro", "Caro")
    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), caro))
    private val viaje = Espacio(id = "esp_viaje", nombre = "Viaje", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), caro, MiembroDelEspacio("dst_andres", "Andrés")))

    @AfterTest
    fun limpiar() {
        AnomaliasDescartadasEnLaSesion.huellas.forEach { AnomaliasDescartadasEnLaSesion.deshacer(it) }
    }

    // ── El selector ─────────────────────────────────────────────────────────

    @Test
    fun `la linea en vivo dice lo que le toca a cada uno, o lo que falta`() {
        assertEquals("Te toca $50.000 · a Caro $50.000", lineaDelReparto(100_000, casa, MIEMBRO_YO, RepartoDelGasto.DEL_GRUPO)?.texto)
        val faltan = lineaDelReparto(100_000, casa, MIEMBRO_YO, RepartoDelGasto(FormaDeRepartir.MONTOS, montos = mapOf(MIEMBRO_YO to 30_000L, caro.id to 50_000L)))!!
        assertEquals("Faltan $20.000", faltan.texto)
        assertFalse(faltan.cuadra)
        assertFalse(faltan.sobra)
        val sobra = lineaDelReparto(100_000, casa, MIEMBRO_YO, RepartoDelGasto(FormaDeRepartir.PORCENTAJES, porcentajes = mapOf(MIEMBRO_YO to 80, caro.id to 30)))!!
        assertEquals("Te pasaste por 10 %", sobra.texto)
        assertTrue(sobra.sobra)
        assertEquals("Te toca $0 · a Caro $90.000", lineaDelReparto(90_000, casa, MIEMBRO_YO, RepartoDelGasto(FormaDeRepartir.SOLO_DE, soloDe = caro.id))?.texto)
        assertNull(lineaDelReparto(0, casa, MIEMBRO_YO, null), "sin monto no hay nada que repartir")
    }

    @Test
    fun `cada forma arranca cuadrada`() {
        assertEquals(mapOf(MIEMBRO_YO to 50, caro.id to 50), repartoAlElegir(FormaDeRepartir.PORCENTAJES, 100_000, casa, MIEMBRO_YO).porcentajes)
        assertEquals(mapOf(MIEMBRO_YO to 34, caro.id to 33, "dst_andres" to 33), repartoAlElegir(FormaDeRepartir.PORCENTAJES, 1, viaje, MIEMBRO_YO).porcentajes)
        assertEquals(mapOf(MIEMBRO_YO to 50_001L, caro.id to 50_000L), repartoAlElegir(FormaDeRepartir.MONTOS, 100_001, casa, MIEMBRO_YO).montos)
        assertEquals(caro.id, repartoAlElegir(FormaDeRepartir.SOLO_DE, 1, casa, MIEMBRO_YO).soloDe)
        FormaDeRepartir.entries.forEach { forma ->
            assertTrue(lineaDelReparto(100_001, viaje, MIEMBRO_YO, repartoAlElegir(forma, 100_001, viaje, MIEMBRO_YO))!!.cuadra, "$forma")
        }
    }

    @Test
    fun `los rotulos de las pildoras`() {
        assertEquals(listOf("Partes iguales", "Porcentajes", "Montos exactos", "Solo de Caro"), FORMAS_DE_REPARTIR.map { rotuloDeLaForma(it, casa) })
        assertEquals("Solo de una persona", rotuloDeLaForma(FormaDeRepartir.SOLO_DE, viaje))
    }

    // ── La propuesta ────────────────────────────────────────────────────────

    private fun propuesta(id: String, reparto: RepartoDelGasto? = null, motivo: MotivoDeReparto = MotivoDeReparto.RECURRENTE) = PropuestaDeRepartir(
        eventoId = id, descripcion = "Arriendo", monto = 2_400_000, fecha = 1L, espacio = casa, reparto = reparto,
        motivo = motivo, clave = if (motivo == MotivoDeReparto.RECURRENTE) "regla:rr_arriendo" else "comercio:exito",
    )

    @Test
    fun `la propuesta se lee como una pregunta, con el grupo y el reparto`() {
        val p = propuesta("e_1")
        assertEquals("¿Repartes Arriendo $2.400.000 con Caro?", tituloDeLaPropuestaDeRepartir(p))
        assertEquals("Casa con Caro, 50/50", cuerpoDeLaPropuestaDeRepartir(p))
        val setenta = propuesta("e_1", RepartoDelGasto(FormaDeRepartir.PORCENTAJES, porcentajes = mapOf(MIEMBRO_YO to 70, caro.id to 30)))
        assertEquals("Casa con Caro, 70/30, como la última vez", cuerpoDeLaPropuestaDeRepartir(setenta))
        assertEquals("Nunca para este recurrente", botonDeNuncaRepartir(p))
        assertEquals("Nunca para este comercio", botonDeNuncaRepartir(propuesta("e_2", motivo = MotivoDeReparto.COMERCIO)))
    }

    @Test
    fun `en Movi propone hoy van hasta dos, con sus tres acciones, y cuentan en Por revisar`() {
        val data = DashboardData(propuestasDeRepartir = listOf(propuesta("e_1"), propuesta("e_2", motivo = MotivoDeReparto.COMERCIO)))
        val tarjetas = propuestasDelInicio(data).filter { it.tipo == TipoDePropuesta.REPARTO }
        assertEquals(2, tarjetas.size)
        val primera = tarjetas.first()
        assertEquals("Repartir", primera.accion)
        assertTrue(primera.alTocar is AccionDeLaPropuesta.Repartir)
        assertEquals("No es compartido", primera.accionSecundaria)
        assertEquals(AccionDeLaPropuesta.DescartarAnomalia(huellaDeNoEsCompartido("e_1")), primera.alTocarSecundaria)
        assertEquals(AccionDeLaPropuesta.DescartarAnomalia(huellaDeNuncaRepartir("regla:rr_arriendo")), primera.alTocarTerciaria)
        assertEquals(2, conteoPorRevisarDelInicio(data).repartir)

        // «No es compartido» la saca en el acto.
        AnomaliasDescartadasEnLaSesion.descartar(huellaDeNoEsCompartido("e_1"))
        assertEquals(listOf("e_2"), propuestasDelInicio(data).filter { it.tipo == TipoDePropuesta.REPARTO }.map { (it.alTocar as AccionDeLaPropuesta.Repartir).propuesta.eventoId })
        assertEquals(1, conteoPorRevisarDelInicio(data).repartir)
    }
}
