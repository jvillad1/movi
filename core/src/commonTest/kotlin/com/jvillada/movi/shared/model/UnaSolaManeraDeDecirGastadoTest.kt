package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # Una sola manera de decir «gastado» (vuelta 60)
 *
 * Tres cifras de «gastado» en pantallas vecinas: Presupuestos «$810.550», «Cuánto puedes gastar»
 * «$2,1M» y «En qué se va» «$4.888.850». La prueba de consistencia: con los MISMOS movimientos,
 *
 * - **día a día + fijos = el gasto del período** de «En qué se va» ([analisisDelPeriodo]), y el día a
 *   día es exactamente la suma del gasto variable que mide el Disponible ([gastoVariablePorDia]);
 * - **lo de los presupuestos + lo de afuera = el mismo gasto del período**.
 */
class UnaSolaManeraDeDecirGastadoTest {

    private val ajustes = PeriodSettings(cutoffDay = 25)
    private val periodo = PeriodoFinanciero(2026, 10) // 25 sep → 24 oct
    private val hoy = LocalDate(2026, 10, 20)

    private fun el(dia: LocalDate): Long = dia.atTime(LocalTime(12, 0)).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private fun gasto(id: String, monto: Long, categoria: String, dia: LocalDate, descripcion: String = categoria) = FinancialEvent(
        id = id, accountId = "acc-1", type = TransactionType.EXPENSE, amount = monto, category = categoria,
        description = descripcion, timestamp = el(dia), reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    // El período del dueño: el arriendo (un fijo, emparejado con su regla), la cuota del carro (fija
    // entera), el colegio pagado con un movimiento más grande que la regla (sale solo la parte de la
    // regla), y el día a día: mercado, Rappi, ropa. Más uno en «Por confirmar» y un traspaso, que no
    // son gasto en ningún lado.
    private val eventos = listOf(
        gasto("arriendo", 1_800_000, "Hogar", LocalDate(2026, 9, 26), "Arriendo"),
        gasto("cuota", 980_000, CUOTA_CATEGORY, LocalDate(2026, 10, 5), "Cuota Vehículo 4083"),
        gasto("colegio", 650_000, "Educación", LocalDate(2026, 10, 1), "Colegio"),
        gasto("mercado", 392_100, "Mercado", LocalDate(2026, 10, 2)),
        gasto("rappi", 45_900, "Restaurantes", LocalDate(2026, 10, 10), "Rappi"),
        gasto("ropa", 210_000, "Ropa", LocalDate(2026, 10, 18)),
        gasto("sin-confirmar", 99_000, "Mercado", LocalDate(2026, 10, 19))
            .copy(reconciliationStatus = ReconciliationStatus.UNCONFIRMED),
        gasto("traspaso", 500_000, TRANSFER_CATEGORY, LocalDate(2026, 10, 3)).copy(countsAsCashFlow = false),
    )

    /** Lo que el server saca como fijo de cada movimiento (`PagosDelChecklist.kt`). */
    private val parteFija = mapOf("arriendo" to 1_800_000L, "colegio" to 600_000L)

    @Test
    fun `dia a dia mas fijos es el gasto del periodo, y el dia a dia es el del Disponible`() {
        val analisis = analisisDelPeriodo(eventos, periodo, ajustes, hoy)
        val variable = gastoVariablePorDia(eventos, parteFija) { ts -> com.jvillada.movi.shared.time.epochMillisToAppDate(ts).toString() }

        val desglose = assertNotNull(
            desgloseDelGastado(analisis.total, variable, analisis.desde.toString(), analisis.hasta.toString()),
        )
        assertEquals(4_078_000L, analisis.total, "el gasto del período, con la regla de siempre")
        assertEquals(variable.values.sum(), desglose.diaADia, "el día a día es el gasto variable del Disponible")
        assertEquals(50_000L + 392_100L + 45_900L + 210_000L, desglose.diaADia)
        assertEquals(1_800_000L + 980_000L + 600_000L, desglose.fijos, "el arriendo, la cuota y lo del colegio")
        assertEquals(analisis.total, desglose.diaADia + desglose.fijos)
    }

    @Test
    fun `lo de los presupuestos mas lo de afuera es el gasto del periodo`() {
        val analisis = analisisDelPeriodo(eventos, periodo, ajustes, hoy)
        // El `spentByCategory` del server: la misma regla de «Gastos», por categoría.
        val porCategoria = analisis.gastos.groupBy { it.category }.mapValues { (_, es) -> es.sumOf { montoContado(it) } }
        val presupuestos = listOf(
            Budget("Mercado", 1_000_000), Budget("Restaurantes", 300_000), Budget("Hogar", 1_850_000), Budget("Mascotas", 200_000),
        )

        val partes = gastoEnPresupuestos(presupuestos, porCategoria)

        assertEquals(4, partes.presupuestos)
        assertEquals(392_100L + 45_900L + 1_800_000L, partes.enPresupuestos)
        assertEquals(3_350_000L, partes.limite)
        assertEquals(980_000L + 650_000L + 210_000L, partes.fueraDePresupuesto)
        assertEquals(analisis.total, partes.gastadoDelPeriodo)
    }

    @Test
    fun `sin el gasto variable, o si no cuadra con el total, no hay desglose`() {
        assertNull(desgloseDelGastado(1_000_000, null, "2026-09-25", "2026-10-24"), "un server viejo")
        assertNull(
            desgloseDelGastado(100_000, mapOf("2026-10-01" to 300_000L), "2026-09-25", "2026-10-24"),
            "el día a día no puede pasar el total: la historia de la pantalla no es la del server",
        )
        val fuera = assertNotNull(desgloseDelGastado(500_000, mapOf("2026-09-01" to 300_000L), "2026-09-25", "2026-10-24"))
        assertEquals(0L, fuera.diaADia, "un día de otro período no cuenta")
        assertEquals(500_000L, fuera.fijos)
    }

    @Test
    fun `un presupuesto repetido o un reembolso no descuadran la suma`() {
        val partes = gastoEnPresupuestos(
            listOf(Budget("Mercado", 500_000), Budget("Mercado", 500_000)),
            mapOf("Mercado" to 300_000L, "Ropa" to -20_000L, "Hogar" to 100_000L),
        )
        assertEquals(1, partes.presupuestos)
        assertEquals(300_000L, partes.enPresupuestos)
        assertEquals(500_000L, partes.limite)
        assertEquals(100_000L, partes.fueraDePresupuesto)
    }
}
