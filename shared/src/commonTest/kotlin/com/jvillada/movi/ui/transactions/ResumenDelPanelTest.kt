package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.VistaDelAnalisis
import com.jvillada.movi.shared.model.analisisDelPeriodo
import com.jvillada.movi.shared.model.filasDelAnalisis
import com.jvillada.movi.shared.model.movimientosDelAnalisis
import com.jvillada.movi.shared.model.tuParteComoEventos
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

    // ── Las mismas cifras que «En qué se va» (revisión de la vuelta 9) ─────────────────────

    /**
     * El defecto que vio el coordinador: con «Mercado del sábado · Pagó Caro · Tu parte $120.000» en
     * el período, el panel decía Mercado $196.050 y «En qué se va» (y el widget del Inicio)
     * $316.050. Ahora el panel cuenta los MISMOS movimientos ([movimientosDelAnalisis]): tu parte de
     * lo que pagó otro suma, y de lo que pagaste tú y repartiste, solo tu parte.
     */
    @Test
    fun `con un gasto que pago otro el panel da las mismas cifras por categoria que En que se va`() {
        val dias = dias(
            mov("2026-08-01", 1_000_000L, OPENING_CATEGORY, TransactionType.INCOME),
            mov("2026-09-26", 4_000_000L, "Salario", TransactionType.INCOME),
            // Período pasado, a la misma altura.
            mov("2026-08-27", 150_000L, "Mercado"),
            mov("2026-08-28", 90_000L, "Comida"),
            // Este período: un mercado tuyo, y una comida que pagaste tú y repartiste (Caro pone la mitad).
            mov("2026-09-27", 196_050L, "Mercado"),
            mov("2026-10-01", 152_100L, "Comida").copy(parteAjena = 76_050L),
        )
        // «Mercado del sábado · Pagó Caro · Tu parte $120.000».
        val pagoCaro = GastoDelEspacio(
            id = "g1", espacioId = "casa", pagadoPor = "caro", monto = 240_000L, categoria = "Mercado",
            descripcion = "Mercado del sábado",
            fecha = LocalDate.parse("2026-10-04").atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds(),
            partes = mapOf(MIEMBRO_YO to 120_000L, "caro" to 120_000L),
        )
        val tuParte = tuParteComoEventos(listOf(pagoCaro))

        val panel = resumenDelPanel(dias, octubre, corte25, hoy, tipos, tuParteDeOtros = tuParte)
        // Lo que pinta «En qué se va» (EnQueSeVaScreen), con la misma lectura de tu parte de otros.
        val enQueSeVa = analisisDelPeriodo(movimientosDelAnalisis(dias, tuParte), octubre, corte25, hoy, tipos)
        val filasDeEnQueSeVa = filasDelAnalisis(enQueSeVa, VistaDelAnalisis.CATEGORIA)

        assertEquals(
            filasDeEnQueSeVa.map { Triple(it.nombre, it.gasto, it.anterior) },
            panel.categorias.map { Triple(it.nombre, it.gasto, it.anterior) },
            "categoría por categoría, lo mismo que «En qué se va»",
        )
        assertEquals(enQueSeVa.total, panel.salio, "lo que salió es el «Llevas gastado» de «En qué se va»")
        val porNombre = panel.categorias.associateBy { it.nombre }
        assertEquals(316_050L, porNombre.getValue("Mercado").gasto, "tu mercado + tu parte del de Caro")
        assertEquals(76_050L, porNombre.getValue("Comida").gasto, "de la comida repartida, solo tu parte")
        assertEquals("\$166.050 más", diferenciaDeLaFila(porNombre.getValue("Mercado"))!!.texto)
        assertEquals(392_100L, panel.salio)
        assertEquals(4_000_000L - 392_100L, panel.neto)
    }

    /**
     * **El neto del héroe de Hoy y el del panel dan lo mismo** (vuelta 10). El héroe no suma nada: su
     * «+$800.000 este período» es `ingresos − egresos` de `/api/finance-summary`, y esa ruta ahora
     * cuenta con `movimientosDeFlujo` —lo fija `EspacioRoutesTest` con estos MISMOS tres casos: un
     * gasto tuyo 50/50, un gasto que pagó Caro (tu parte) y su transferencia que salda, que da
     * 1.000.000 / 200.000—. Acá, el panel con los movimientos como los baja el server (la parte
     * ajena y lo que salda ya derivados por `conLoCompartido`) tiene que dar ese mismo neto.
     */
    @Test
    fun `con un gasto repartido, uno que pago otro y la transferencia que salda, el neto es el del heroe`() {
        val dias = dias(
            mov("2026-09-26", 1_000_000L, "Salario", TransactionType.INCOME),
            mov("2026-10-01", 240_000L, "Mercado").copy(parteAjena = 120_000L, espacioId = "casa"),
            mov("2026-10-02", 30_000L, "Comida"),
            // La transferencia de Caro que salda la casa: fuera de «Entró».
            mov("2026-10-03", 390_000L, "Ingreso", TransactionType.INCOME)
                .copy(saldaEspacioId = "casa", countsAsCashFlow = false),
        )
        val pagoCaro = GastoDelEspacio(
            id = "g_caro", espacioId = "casa", pagadoPor = "caro", monto = 100_000L, categoria = "Mercado",
            fecha = LocalDate.parse("2026-10-04").atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds(),
            partes = mapOf(MIEMBRO_YO to 50_000L, "caro" to 50_000L),
        )
        // Lo que `/api/finance-summary` contesta con estos datos (ver `EspacioRoutesTest`).
        val resumenDelServer = com.jvillada.movi.shared.model.FinanceSummary(
            scope = com.jvillada.movi.shared.model.Scope.SELF, balance = 0L, ingresos = 1_000_000L, egresos = 200_000L,
        )

        val panel = resumenDelPanel(dias, octubre, corte25, hoy, tipos, tuParteDeOtros = tuParteComoEventos(listOf(pagoCaro)))

        assertEquals(resumenDelServer.ingresos, panel.entro, "la transferencia que salda no entró")
        assertEquals(resumenDelServer.egresos, panel.salio, "120.000 + 30.000 + 50.000: solo tu parte")
        assertEquals(resumenDelServer.ingresos - resumenDelServer.egresos, panel.neto)
        val heroe = com.jvillada.movi.ui.dashboard.veredictoCortoDelPeriodo(resumenDelServer.ingresos, resumenDelServer.egresos)!!
        assertTrue(com.jvillada.movi.ui.components.formatMoneyCompact(panel.neto) in heroe, heroe)
    }
}
