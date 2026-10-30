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
import com.jvillada.movi.ui.dashboard.MovimientosDelDueno
import com.jvillada.movi.ui.dashboard.VIGENCIA_SIN_SUBIR_MS
import com.jvillada.movi.ui.dashboard.cargaBuenaEmpezadaEn
import com.jvillada.movi.ui.components.ds.VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS
import com.jvillada.movi.ui.components.ds.partesDelAviso
import com.jvillada.movi.ui.components.ds.PartesDelAviso
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
        // Revisión de la vuelta 82: sin «+» (debajo de una cifra negativa se leía raro).
        assertEquals("Falta Vehículo 4083, sin saldo (unos \$35,8M según el plan)", lineaDeLasDeudasSinSaldo(listOf(vehiculo), mapOf("v4083" to plan)))
        assertEquals("Falta Vehículo 4083, sin saldo", lineaDeLasDeudasSinSaldo(listOf(vehiculo), emptyMap()))
        assertEquals("Falta Nu, sin saldo", lineaDeLasDeudasSinSaldo(listOf(nu), emptyMap()))
        assertEquals("Faltan 2 préstamos, sin saldo", lineaDeLasDeudasSinSaldo(listOf(vehiculo, otro), mapOf("v4083" to plan)))
        assertEquals(
            "Faltan 2 préstamos, sin saldo (unos \$71,6M según el plan)",
            lineaDeLasDeudasSinSaldo(listOf(vehiculo, otro), mapOf("v4083" to plan, "l9695" to plan)),
        )
        assertEquals("Faltan 2 deudas, sin saldo", lineaDeLasDeudasSinSaldo(listOf(vehiculo, nu), emptyMap()))
        assertFalse(lineaDeLasDeudasSinSaldo(listOf(vehiculo), emptyMap())!!.startsWith("+"))
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

    // ── Revisión · lo que el server no adelantaría ───────────────────────────────────────────────

    @Test
    fun `un gasto desde una cuenta que no es de Tu plata espera al server`() {
        val nuAhorro = Account("nu-ah", "Nu ahorro", AccountType.SAVINGS, 2_000_000L, condicionadaA = "El viaje")
        val datos = inicio.copy(accounts = listOf(ahorros, tarjeta, nuAhorro))
        assertNull(datosConElMovimiento(datos, gasto("nu-ah", 50_000), 1, hoy), "variable +X y pagado desde fuera −X: no cambia «Te quedan»")
    }

    @Test
    fun `un gasto en la categoria de un apartado de esa cuenta espera al server`() {
        val colegio = com.jvillada.movi.shared.model.Apartado(
            id = "ap1", accountId = "ah", proposito = "Colegio", monto = 8_000_000L, categoria = "Educación", desde = 0L,
        )
        val datos = inicio.copy(apartados = listOf(colegio))
        assertNull(datosConElMovimiento(datos, gasto("ah", 400_000, categoria = "educacion"), 1, hoy), "baja el apartado, no las cifras")
        // En otra cuenta, o en otra categoría, sí se adelanta.
        assertNotNull(datosConElMovimiento(datos, gasto("tc", 400_000, categoria = "Educación"), 1, hoy))
        assertNotNull(datosConElMovimiento(datos, gasto("ah", 40_000), 1, hoy))
    }

    @Test
    fun `lo que se empareja por nombre con un pago del periodo espera al server`() {
        val netflix = UpcomingPayment(
            rule = RecurringRule(id = "nf", name = "Netflix", category = "Suscripciones", amount = 44_900, dayOfMonth = 3, type = TransactionType.EXPENSE),
            dueDate = "2026-10-03", daysUntil = -12, status = PaymentStatus.OVERDUE,
        )
        val datos = inicio.copy(upcoming = inicio.upcoming!! + netflix)
        val aviso = gasto("ah", 44_900, categoria = "Entretenimiento").copy(description = "NETFLIX.COM", merchant = "Netflix")
        assertNull(datosConElMovimiento(datos, aviso, -1, hoy), "por nombre puede ser el fijo de Netflix")
        // Sellado a una regla (aunque no se parezca): también.
        val sellado = inicio.copy(ocurrencias = listOf(com.jvillada.movi.shared.model.OccurrenceState(ruleId = "x", period = "2026-10", dueDate = "2026-10-03", occurred = true, eventId = "g-ah-12300")))
        assertNull(datosConElMovimiento(sellado, gasto("ah", 12_300), -1, hoy))
        // Anular lo que no está en el gasto del día lo dejaría en negativo: dudoso.
        assertNull(datosConElMovimiento(inicio, gasto("ah", 12_300), -1, hoy))
    }

    @Test
    fun `una carga que termina sin el gasto no suelta, una caida tampoco, y una posterior si`() {
        MovimientosDelDueno.limpiar()
        try {
            val id = MovimientosDelDueno.registrar(gasto("ah", 12_300), signo = 1, ahora = 1_000)
            // Sin listo (en el teléfono, sin subir): ninguna carga lo suelta.
            assertEquals(1, MovimientosDelDueno.pendientes(cargaBuenaEmpezadaEn = 5_000, ahora = 6_000).size)
            MovimientosDelDueno.marcarListo(id, en = 2_000)
            // La carga que arrancó al guardar (antes de que el server lo tuviera) termina sin el gasto: no suelta.
            assertEquals(1, MovimientosDelDueno.pendientes(cargaBuenaEmpezadaEn = 1_500, ahora = 3_000).size)
            // Una carga que empezó después pero falló no cuenta como buena: el sello sigue en la anterior.
            val sello = cargaBuenaEmpezadaEn(anterior = 1_500, empezo = 2_500, resumenLlego = true, cuentasLlegaron = true, fallo = true)
            assertEquals(1_500L, sello)
            assertEquals(1_500L, cargaBuenaEmpezadaEn(anterior = 1_500, empezo = 2_500, resumenLlego = false, cuentasLlegaron = true, fallo = false))
            assertEquals(1, MovimientosDelDueno.pendientes(sello, ahora = 3_000).size)
            // Una carga buena que empezó después: suelta.
            val buena = cargaBuenaEmpezadaEn(anterior = 1_500, empezo = 2_500, resumenLlego = true, cuentasLlegaron = true, fallo = false)
            assertEquals(0, MovimientosDelDueno.pendientes(buena, ahora = 3_000).size)
        } finally {
            MovimientosDelDueno.limpiar()
        }
    }

    @Test
    fun `una carga que termina en medio de deshacer no suelta la ida y la vuelta juntas`() {
        MovimientosDelDueno.limpiar()
        try {
            val ida = MovimientosDelDueno.registrar(gasto("ah", 12_300), signo = 1, ahora = 1_000)
            MovimientosDelDueno.marcarListo(ida, en = 1_000)
            val vuelta = MovimientosDelDueno.registrar(gasto("ah", 12_300), signo = -1, ahora = 1_200)
            // L1 empezó a 1.100 y terminó a 1.300, antes de que la anulación terminara (1.400).
            val quedan = MovimientosDelDueno.pendientes(cargaBuenaEmpezadaEn = 1_100, ahora = 1_300)
            assertEquals(listOf(vuelta), quedan.map { it.id }, "suelta la ida (L1 la trae) pero no la vuelta")
            MovimientosDelDueno.marcarListo(vuelta, en = 1_400)
            assertEquals(listOf(vuelta), MovimientosDelDueno.pendientes(cargaBuenaEmpezadaEn = 1_100, ahora = 1_500).map { it.id })
            assertTrue(MovimientosDelDueno.pendientes(cargaBuenaEmpezadaEn = 1_450, ahora = 1_600).isEmpty())
        } finally {
            MovimientosDelDueno.limpiar()
        }
    }

    @Test
    fun `el optimista vence, y dice cuando`() {
        MovimientosDelDueno.limpiar()
        try {
            val id = MovimientosDelDueno.registrar(gasto("ah", 1), signo = 1, ahora = 1_000)
            MovimientosDelDueno.marcarListo(id, en = 1_000)
            val vence = 1_000 + VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS
            assertEquals(VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS, MovimientosDelDueno.proximoVencimiento(MovimientosDelDueno.pendientes(null, 1_000), 1_000))
            assertTrue(MovimientosDelDueno.pendientes(null, vence + 1).isEmpty())
            // Lo que no subió dura más (sin señal), pero no para siempre.
            MovimientosDelDueno.registrar(gasto("ah", 2), signo = 1, ahora = 2_000)
            assertEquals(1, MovimientosDelDueno.pendientes(null, 2_000 + VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS + 1).size)
            assertTrue(MovimientosDelDueno.pendientes(null, 2_000 + VIGENCIA_SIN_SUBIR_MS + 1).isEmpty())
        } finally {
            MovimientosDelDueno.limpiar()
        }
    }

    @Test
    fun `el aviso se parte en el monto, que no se corta, y lo demas`() {
        assertEquals(PartesDelAviso("Anotado", "\$12.300", "· Restaurantes"), partesDelAviso("Anotado \$12.300 · Restaurantes"))
        assertEquals(PartesDelAviso("Ya estaba anotado", "US\$45", "· Netflix"), partesDelAviso("Ya estaba anotado US\$45 · Netflix"))
        assertEquals(PartesDelAviso("Tu parte", "\$16.000", "· repartido con Caro"), partesDelAviso("Tu parte \$16.000 · repartido con Caro"))
        assertEquals(PartesDelAviso("Anotado", "\$12.300", null), partesDelAviso("Anotado \$12.300"))
        assertEquals(PartesDelAviso("Deshecho", null, null), partesDelAviso("Deshecho"))
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
