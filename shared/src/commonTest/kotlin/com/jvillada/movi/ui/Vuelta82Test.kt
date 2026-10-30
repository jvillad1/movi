package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.ui.components.ds.colorDelBordeAlEncima
import com.jvillada.movi.ui.credits.ElSaldoDelPlanEnLaFicha
import com.jvillada.movi.ui.credits.lineaDeLasDeudasSinSaldo
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.MovimientoDelDueno
import com.jvillada.movi.ui.dashboard.datosConElMovimiento
import com.jvillada.movi.ui.dashboard.datosConLosMovimientosDelDueno
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.dashboard.heroBalance
import com.jvillada.movi.ui.dashboard.pendientesPorRevisar
import com.jvillada.movi.ui.dashboard.teQuedanDeHoy
import com.jvillada.movi.ui.espacios.textoDeLoAnotado
import com.jvillada.movi.ui.espacios.textoDeLoQueYaEstaba
import com.jvillada.movi.ui.porrevisar.ConteoPorRevisar
import com.jvillada.movi.ui.porrevisar.textoDePorRevisar
import com.jvillada.movi.ui.sms.laTarjetaLlevaElAmarillo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 82 · lo puro: un amarillo por vista en Por revisar, la línea de la deuda total, el optimista del
 * héroe (las dos cifras se mueven con la regla de siempre), el snackbar con el monto primero, el mismo
 * conteo en Inicio y Actividad, y el borde de hover de las superficies.
 */
class Vuelta82Test {

    // ── 1 · El amarillo se pide a propósito ──────────────────────────────────────────────────────

    @Test
    fun `en Por revisar la accion de cada tarjeta va amarilla solo si es la unica y no hay lote`() {
        assertTrue(laTarjetaLlevaElAmarillo(tarjetas = 1, conLote = false), "una sola tarjeta: su «Confirmar» es el amarillo")
        assertTrue(laTarjetaLlevaElAmarillo(tarjetas = 0, conLote = false))
        assertFalse(laTarjetaLlevaElAmarillo(tarjetas = 2, conLote = false), "dos «Confirmar» amarillos no dicen cuál")
        assertFalse(laTarjetaLlevaElAmarillo(tarjetas = 1, conLote = true), "con el lote, el lote es el amarillo")
        assertFalse(laTarjetaLlevaElAmarillo(tarjetas = 5, conLote = true))
    }

    // ── 2 · La deuda total, honesta ──────────────────────────────────────────────────────────────

    private val vehiculo = Account("v4083", "Vehículo 4083", AccountType.LOAN, -980_000L)
    private val otro = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 0L)
    private val nu = Account("nu", "Nu", AccountType.CREDIT_CARD, -50_000L)
    private val plan = ElSaldoDelPlanEnLaFicha(saldo = 35_810_084L, ultimaCuota = null)

    @Test
    fun `debajo de la deuda total va el prestamo sin saldo con lo que dice su plan`() {
        assertEquals("+ Vehículo 4083 sin saldo (unos \$35,8M según el plan)", lineaDeLasDeudasSinSaldo(listOf(vehiculo), mapOf("v4083" to plan)))
        assertEquals("+ 1 préstamo sin saldo", lineaDeLasDeudasSinSaldo(listOf(vehiculo), emptyMap()))
        assertEquals("+ 1 tarjeta sin saldo", lineaDeLasDeudasSinSaldo(listOf(nu), emptyMap()))
        assertEquals("+ 2 préstamos sin saldo", lineaDeLasDeudasSinSaldo(listOf(vehiculo, otro), mapOf("v4083" to plan)))
        assertEquals(
            "+ 2 préstamos sin saldo (unos \$71,6M según el plan)",
            lineaDeLasDeudasSinSaldo(listOf(vehiculo, otro), mapOf("v4083" to plan, "l9695" to plan)),
        )
        assertEquals("+ 2 deudas sin saldo", lineaDeLasDeudasSinSaldo(listOf(vehiculo, nu), emptyMap()))
        assertNull(lineaDeLasDeudasSinSaldo(emptyList(), emptyMap()))
        // Sin «≈»: Figtree no tiene ese glifo.
        assertFalse(lineaDeLasDeudasSinSaldo(listOf(vehiculo), mapOf("v4083" to plan))!!.contains('≈'))
    }

    // ── 3 · «Te quedan» y «Tu plata» cambian juntas ──────────────────────────────────────────────

    private val hoy = LocalDate(2026, 10, 15)
    private val mediodia = hoy.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L
    private val ahorros = Account("ah", "Bancolombia Ahorros", AccountType.SAVINGS, 10_000_000L)
    private val tarjeta = Account("tc", "Master Black", AccountType.CREDIT_CARD, 500_000L)

    private val inicio = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 8_000_000, egresos = 2_000_000),
        accounts = listOf(ahorros, tarjeta),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(id = "arr", name = "Arriendo", category = "Vivienda", amount = 1_800_000, dayOfMonth = 20, type = TransactionType.EXPENSE),
                dueDate = "2026-10-20", daysUntil = 5, status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(2026, 10),
        ajustesDePeriodo = PeriodSettings(),
        gastoVariablePorDia = mapOf("2026-10-02" to 300_000L),
    )

    private fun gasto(cuenta: String, monto: Long, categoria: String = "Restaurantes", tipo: TransactionType = TransactionType.EXPENSE) = FinancialEvent(
        id = "g-$cuenta-$monto", accountId = cuenta, type = tipo, amount = monto, category = categoria,
        description = "Almuerzo", timestamp = mediodia, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private fun teQuedan(d: DashboardData): Long = teQuedanDeHoy(disponibleDelInicio(d, hoy)!!).let { if (it.enRojo) -it.monto else it.monto }
    private fun tuPlata(d: DashboardData): Long = heroBalance(d.accounts!!).tuPlata

    @Test
    fun `un gasto de una cuenta de Tu plata mueve las dos cifras el mismo monto`() {
        val despues = assertNotNull(datosConElMovimiento(inicio, gasto("ah", 12_300), signo = 1, hoy))
        assertEquals(-12_300L, teQuedan(despues) - teQuedan(inicio), "«Te quedan» baja el gasto")
        assertEquals(-12_300L, tuPlata(despues) - tuPlata(inicio), "«Tu plata» baja lo mismo")
    }

    @Test
    fun `un gasto con tarjeta mueve solo Te quedan`() {
        val despues = assertNotNull(datosConElMovimiento(inicio, gasto("tc", 40_000), signo = 1, hoy))
        assertEquals(-40_000L, teQuedan(despues) - teQuedan(inicio))
        assertEquals(0L, tuPlata(despues) - tuPlata(inicio), "la tarjeta no es Tu plata")
    }

    @Test
    fun `anotar y deshacer deja las dos cifras donde estaban`() {
        val g = gasto("ah", 12_300)
        val ida = MovimientoDelDueno(1, g, 1, 0)
        val vuelta = MovimientoDelDueno(2, g, -1, 1)
        val despues = assertNotNull(datosConLosMovimientosDelDueno(inicio, listOf(ida, vuelta), hoy))
        assertEquals(teQuedan(inicio), teQuedan(despues))
        assertEquals(tuPlata(inicio), tuPlata(despues))
    }

    @Test
    fun `lo dudoso no se adelanta y se espera al server`() {
        assertNull(datosConElMovimiento(inicio, gasto("ah", 1_800_000, categoria = "Vivienda"), 1, hoy), "puede ser el arriendo, ya restado")
        assertNull(datosConElMovimiento(inicio, gasto("ah", 100_000, tipo = TransactionType.INCOME), 1, hoy), "un ingreso puede ser el salario esperado")
        assertNull(datosConElMovimiento(inicio, gasto("ah", 10_000).copy(currency = "USD"), 1, hoy), "otra moneda")
        assertNull(datosConElMovimiento(inicio, gasto("ah", 10_000).copy(transferId = "t1"), 1, hoy), "un traspaso")
        assertNull(datosConElMovimiento(inicio, gasto("ah", 10_000).copy(timestamp = mediodia - 40L * 86_400_000L), 1, hoy), "otro período")
        assertNull(datosConElMovimiento(inicio, gasto("ah", 10_000).copy(reconciliationStatus = ReconciliationStatus.UNCONFIRMED), 1, hoy), "por confirmar no cuenta")
        assertNull(datosConElMovimiento(inicio, gasto("no-esta", 10_000), 1, hoy), "una cuenta que no se leyó")
        // Uno dudoso entre varios: nada se adelanta.
        assertNull(datosConLosMovimientosDelDueno(inicio, listOf(MovimientoDelDueno(1, gasto("ah", 1), 1, 0), MovimientoDelDueno(2, gasto("ah", 5, categoria = "Vivienda"), 1, 0)), hoy))
    }

    // ── 4 · El snackbar con el monto ─────────────────────────────────────────────────────────────

    @Test
    fun `el aviso dice el monto antes que la categoria, para que se corte la categoria`() {
        assertEquals("Anotado \$12.300 · Restaurantes", textoDeLoAnotado("Restaurantes", 12_300, "COP"))
        assertEquals("Anotado \$12.300 · Restaurantes", textoDeLoAnotado(gasto("ah", 12_300)))
        assertEquals("Ya estaba anotado \$12.300 · Restaurantes", textoDeLoQueYaEstaba(gasto("ah", 12_300)))
        assertEquals("Anotado \$12.300", textoDeLoAnotado("", 12_300, "COP"))
    }

    // ── 6 · El mismo conteo en todos lados ───────────────────────────────────────────────────────

    @Test
    fun `Inicio y Actividad dicen el mismo numero`() {
        val delInicio = inicio.copy(pendingSms = 5, movimientosPorConfirmar = 1)
        val deActividad = ConteoPorRevisar(avisos = 5, movimientos = 1)
        assertEquals(pendientesPorRevisar(delInicio), deActividad.total)
        assertEquals("6 por revisar", textoDePorRevisar(deActividad.total))
    }

    // ── 7 · El hover de las superficies ──────────────────────────────────────────────────────────

    @Test
    fun `el borde de hover de una superficie grande es la tinta al 20 por ciento`() {
        val c = colorDelBordeAlEncima(COLORES_CLAROS)
        assertEquals(COLORES_CLAROS.texto.copy(alpha = 0.2f), c)
    }
}
