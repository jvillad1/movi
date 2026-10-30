package com.jvillada.movi.ui.buscar

import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.VistaDelAnalisis
import com.jvillada.movi.shared.model.analisisDelPeriodo
import com.jvillada.movi.shared.model.filasDelAnalisis
import com.jvillada.movi.shared.model.movimientosDelAnalisis
import com.jvillada.movi.shared.model.tuParteComoEventos
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.transactions.montoPrincipalDelMovimiento
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 78 · «mercado» en Buscar suma lo mismo que En qué se va
 *
 * La revisión 6: Buscar daba $379.050 de mercado en octubre y En qué se va, el widget y Movi AI
 * $499.050. Faltaban los $120.000 de tu parte del «Mercado del sábado», que pagó Caro. Ahora esa parte
 * es una fila de Buscar —«Mercado del sábado · Pagó Caro», con «Tu parte» debajo de la cifra— y la suma
 * de las filas del período es la de la categoría.
 */
class BuscarCuentaComoEnQueSeVaTest {

    private val ajustes = PeriodSettings()
    private val octubre = PeriodoFinanciero(2026, 10)
    private val hoy = LocalDate(2026, 10, 30)
    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), MiembroDelEspacio("dst_caro", "Caro")))

    private fun a(dia: Int, mes: Int = 10): Long = LocalDateTime(2026, mes, dia, 12, 0).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private fun gasto(id: String, descripcion: String, monto: Long, dia: Int, mes: Int = 10, parteAjena: Long = 0L, categoria: String = "Mercado") =
        FinancialEvent(
            id = id, accountId = "nu", type = TransactionType.EXPENSE, amount = monto, category = categoria,
            description = descripcion, timestamp = a(dia, mes), parteAjena = parteAjena,
            espacioId = if (parteAjena > 0) casa.id else null, createdAt = a(dia, mes), countsAsCashFlow = true,
            reconciliationStatus = com.jvillada.movi.shared.model.ReconciliationStatus.RECONCILED,
        )

    private val propios = listOf(
        gasto("d1", "Tienda D1 Laureles", 41_000, 28),
        gasto("exito", "Éxito Suba", 50_000, 27),
        gasto("carulla", "Carulla", 142_800, 14, parteAjena = 71_400),
        gasto("uber", "Uber Rides", 8_400, 26, categoria = "Transporte"),
        // De septiembre: no entra en el período.
        gasto("sep", "Éxito Laureles", 184_300, 26, mes = 9),
    )

    /** Lo que pagó Caro: el mercado del sábado, $240.000 a medias. */
    private val tuParte = tuParteComoEventos(
        listOf(
            GastoDelEspacio(
                id = "g1", espacioId = casa.id, pagadoPor = "dst_caro", monto = 240_000, categoria = "Mercado",
                descripcion = "Mercado del sábado", fecha = a(25), partes = mapOf(MIEMBRO_YO to 120_000L, "dst_caro" to 120_000L),
            ),
        ),
    )

    private val dias: List<EventDay> = propios.groupBy { it.timestamp }.map { (_, items) ->
        EventDay(date = com.jvillada.movi.shared.time.epochMillisToAppDate(items.first().timestamp).toString(), total = 0L, items = items)
    }.sortedByDescending { it.date }

    @Test
    fun `el total de mercado en Buscar es el de la categoria en En que se va`() {
        val ventana = ventanaDe(octubre, ajustes)
        val periodo = RangoDelPeriodo(ventana.first, ventana.last + 1)
        val grupos = buscarEnMovi(
            "mercado",
            DatosDeBusqueda(eventos = eventosParaBuscar(dias, tuParte)),
            FiltrosDeBusqueda(tipo = FiltroDeTipo.GASTOS, soloEstePeriodo = true),
            periodo,
        )
        val filas = grupos.single { it.tipo == TipoDeResultado.MOVIMIENTO }.resultados.map { (it as ResultadoDeBusqueda.Movimiento).evento }
        val enBuscar = filas.sumOf { montoPrincipalDelMovimiento(it) }

        val analisis = analisisDelPeriodo(movimientosDelAnalisis(dias, tuParte), octubre, ajustes, hoy)
        val enQueSeVa = filasDelAnalisis(analisis, VistaDelAnalisis.CATEGORIA).single { it.nombre == "Mercado" }.gasto

        assertEquals(enQueSeVa, enBuscar)
        assertEquals(41_000L + 50_000L + 71_400L + 120_000L, enBuscar)
    }

    @Test
    fun `la fila de tu parte dice quien pago`() {
        val grupos = buscarEnMovi("mercado del", DatosDeBusqueda(eventos = eventosParaBuscar(dias, tuParte)))
        val fila = grupos.single { it.tipo == TipoDeResultado.MOVIMIENTO }.resultados.single() as ResultadoDeBusqueda.Movimiento
        assertEquals("Mercado del sábado", fila.titulo)
        assertEquals(120_000L, montoPrincipalDelMovimiento(fila.evento))
        assertEquals("Mercado · 25 de octubre", subtituloDeTuParteEnBuscar(fila.evento, "25 de octubre"))
        // Revisión de la 78: debajo de tu parte, de cuánto fue el gasto del grupo y quién lo pagó.
        assertEquals("de \$240.000 · pagó Caro", debajoDeTuParteEnBuscar(fila.evento, casa))
        assertEquals("Tu parte · pagó Caro", debajoDeTuParteEnBuscar(fila.evento.copy(montoDelGrupo = null), casa))
    }
}
