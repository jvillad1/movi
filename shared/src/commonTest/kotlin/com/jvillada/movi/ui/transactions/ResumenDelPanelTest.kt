package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.dashboard.CATEGORIA_NUEVA
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El resumen del panel de Movimientos (vuelta de diseño 9)
 *
 * Sin un movimiento elegido, el panel de la derecha resume el período de la lista. **Ninguna cuenta
 * nueva**: lo que salió y las categorías son `analisisDelPeriodo`/`filasDelAnalisis` (la regla de
 * «Gastos»), lo que entró es la del chip «Ingresos» en pesos, y la diferencia de cada categoría es la
 * del widget «En qué se va» del Inicio.
 */
class ResumenDelPanelTest {

    /** Corte 25: octubre va del 25-sep al 24-oct; septiembre, del 25-ago al 24-sep. */
    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val octubre = PeriodoFinanciero(2026, 10)
    private val hoy = LocalDate.parse("2026-10-10")
    private val tipos = mapOf("ahorros" to AccountType.SAVINGS)

    private var n = 0

    private fun mov(
        dia: String,
        monto: Long,
        categoria: String = "Comida",
        tipo: TransactionType = TransactionType.EXPENSE,
        moneda: String = "COP",
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
        transferId: String? = null,
    ) = FinancialEvent(
        id = "e${n++}",
        accountId = "ahorros",
        type = tipo,
        amount = monto,
        category = categoria,
        description = "Movimiento $n",
        timestamp = LocalDate.parse(dia).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L,
        reconciliationStatus = estado,
        currency = moneda,
        transferId = transferId,
    )

    /** La historia como la manda `/api/events/by-day`: un día por fecha. */
    private fun dias(vararg eventos: FinancialEvent): List<EventDay> =
        eventos.groupBy { epochMillisToAppDate(it.timestamp).toString() }.map { (dia, del) -> EventDay(dia, 0L, del) }

    @Test
    fun `entro salio y el neto salen de las reglas de los chips en los dias del periodo`() {
        val r = resumenDelPanel(
            dias(
                mov("2026-09-26", 4_000_000L, "Salario", TransactionType.INCOME),
                mov("2026-09-27", 300_000L, "Mercado"),
                mov("2026-10-02", 100_000L, "Comida"),
                // No entran: un ingreso en dólares, uno por confirmar, un traspaso y el período pasado.
                mov("2026-09-28", 50L, "Salario", TransactionType.INCOME, moneda = "USD"),
                mov("2026-09-29", 70_000L, "Comida", estado = ReconciliationStatus.UNCONFIRMED),
                mov("2026-09-30", 500_000L, TRANSFER_CATEGORY, TransactionType.INCOME, transferId = "t1"),
                mov("2026-09-20", 900_000L, "Mercado"),
            ),
            octubre, corte25, hoy, tipos,
        )

        assertEquals(4_000_000L, r.entro)
        assertEquals(400_000L, r.salio)
        assertEquals(3_600_000L, r.neto)
        assertEquals(listOf("Mercado", "Comida"), r.categorias.map { it.nombre })
        assertEquals(r.salio, r.categorias.sumOf { it.gasto }, "las categorías suman lo que salió")
    }

    @Test
    fun `sin cubrir el periodo pasado no hay comparacion`() {
        val r = resumenDelPanel(dias(mov("2026-10-01", 80_000L, "Comida")), octubre, corte25, hoy, tipos)
        assertFalse(r.conComparacion)
        assertNull(diferenciaDeLaFila(r.categorias.single()))
    }

    @Test
    fun `cada categoria dice su diferencia como el widget del Inicio`() {
        val r = resumenDelPanel(
            dias(
                // El primer movimiento, antes del período pasado: Movi lo cubrió entero.
                mov("2026-08-01", 1_000_000L, OPENING_CATEGORY, TransactionType.INCOME),
                // Período pasado, a la misma altura (los primeros 16 días: 25-ago al 9-sep).
                mov("2026-08-27", 180_000L, "Mercado"),
                mov("2026-08-28", 100_000L, "Comida"),
                // Este período.
                mov("2026-09-27", 300_000L, "Mercado"),
                mov("2026-10-01", 50_000L, "Comida"),
                mov("2026-10-02", 80_000L, "Gimnasio"),
            ),
            octubre, corte25, hoy, tipos,
        )

        assertTrue(r.conComparacion)
        val porNombre = r.categorias.associateBy { it.nombre }
        assertEquals("\$120.000 más", diferenciaDeLaFila(porNombre.getValue("Mercado"))!!.texto)
        val comida = diferenciaDeLaFila(porNombre.getValue("Comida"))!!
        assertEquals("\$50.000 menos", comida.texto)
        assertEquals(TonoDeCifra.Apagado, comida.tono)
        assertEquals(CATEGORIA_NUEVA, diferenciaDeLaFila(porNombre.getValue("Gimnasio"))!!.texto)
    }

    @Test
    fun `el neto en entra si quedo plata y en sale si se paso`() {
        assertEquals(TonoDeCifra.Ingreso, tonoDelNeto(1))
        assertEquals(TonoDeCifra.Alerta, tonoDelNeto(-1))
        assertEquals(TonoDeCifra.Apagado, tonoDelNeto(0))
    }
}
