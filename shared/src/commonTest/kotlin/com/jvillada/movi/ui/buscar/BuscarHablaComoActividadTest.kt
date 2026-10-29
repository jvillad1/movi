package com.jvillada.movi.ui.buscar

import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.transactions.debajoDeLaCifra
import com.jvillada.movi.ui.transactions.montoPrincipalDelMovimiento
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 67 · Buscar suma lo que sus filas muestran
 *
 * La revisión de la vuelta 66: con «exito», el comercio decía −$129.300 (tu parte de Laureles) mientras
 * la fila de Laureles mostraba −$88.600 sin «de $88.600 · con Caro», y el comercio se llamaba «Éxito
 * Suba» aunque juntaba también Laureles.
 */
class BuscarHablaComoActividadTest {

    private val periodo = RangoDelPeriodo(desde = 1_000_000L, hasta = 2_000_000L)
    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), MiembroDelEspacio("dst_caro", "Caro")))

    private fun ev(id: String, descripcion: String, monto: Long, cuando: Long, parteAjena: Long = 0L) = FinancialEvent(
        id = id, accountId = "nu", type = TransactionType.EXPENSE, amount = monto, category = "Mercado", description = descripcion,
        timestamp = cuando, parteAjena = parteAjena, espacioId = if (parteAjena > 0) casa.id else null, createdAt = cuando,
    )

    private val datos = DatosDeBusqueda(
        eventos = listOf(
            ev("suba", "ÉXITO SUBA", 50_000, cuando = 1_700_000L),
            ev("laureles", "Éxito Laureles", 88_600, cuando = 1_500_000L, parteAjena = 44_300),
        ),
    )

    @Test
    fun `el comercio se llama por su marca y suma lo que muestran sus filas`() {
        val grupos = buscarEnMovi("exito", datos, periodo = periodo)
        val comercio = grupos.single { it.tipo == TipoDeResultado.COMERCIO }.resultados.single() as ResultadoDeBusqueda.Comercio
        assertEquals("Éxito", comercio.nombre)
        val filas = grupos.single { it.tipo == TipoDeResultado.MOVIMIENTO }.resultados.map { (it as ResultadoDeBusqueda.Movimiento).evento }
        assertEquals(filas.sumOf { montoPrincipalDelMovimiento(it) }, comercio.totalDelPeriodo, "la suma del comercio sale de las filas")
    }

    @Test
    fun `la fila de un gasto repartido dice tu parte y de cuanto y con quien`() {
        val laureles = datos.eventos.single { it.id == "laureles" }
        assertEquals(44_300, montoPrincipalDelMovimiento(laureles))
        assertEquals("de \$88.600 · con Caro", debajoDeLaCifra(laureles, casa))
    }

    @Test
    fun `el subtitulo no repite la categoria si ya es el titulo`() {
        val mercado = ev("m", "Mercado", 20_000, 1_500_000L)
        assertEquals("27 de octubre · Nu", subtituloDeUnMovimientoEnBuscar(mercado, "Nu", "27 de octubre"))
        assertEquals("Mercado · 27 de octubre · Nu", subtituloDeUnMovimientoEnBuscar(datos.eventos.first(), "Nu", "27 de octubre"))
    }
}
