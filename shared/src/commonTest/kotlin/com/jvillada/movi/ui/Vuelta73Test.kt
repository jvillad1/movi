package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.creditoSinSaldo
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.ui.accounts.textoDelCreditoSinSaldoEnElPatrimonio
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.bloquesDelInicio
import com.jvillada.movi.ui.dashboard.elWidgetDeFaltaSoloRepiteElProximoPago
import com.jvillada.movi.ui.dashboard.visibleSections
import com.jvillada.movi.ui.destinos.elTerceroEnLosGrupos
import com.jvillada.movi.ui.porrevisar.ConteoPorRevisar
import com.jvillada.movi.ui.quickadd.creditoSinSaldoEnMovi
import com.jvillada.movi.ui.quickadd.deudaDespuesDelTraspaso
import com.jvillada.movi.ui.sdui.pastillaDeLaFranja
import com.jvillada.movi.ui.sdui.textosDeLaFranjaPorConfirmar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 73: Inicio sin cortes, Tú ordenado, los terceros que cuadran con los grupos — lo puro
 *
 * Lo que se monta (la franja a 360, el widget oculto, la ficha del tercero, los avisos en Tú) está en
 * `Vuelta73EnPantallaTest`.
 */
class Vuelta73Test {

    // ── (1) La franja «Por confirmar» ────────────────────────────────────────

    @Test
    fun `la franja lleva la pastilla solo cuando el renglon no dice que espera`() {
        val (_, conCifra) = textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 5, movimientos = 1), 1_100_200L)
        // La cifra sigue pegada a lo que nombra (vuelta 68).
        assertEquals("\$1.100.200 en 5 avisos · y 1 movimiento", conCifra)
        assertFalse(pastillaDeLaFranja(conCifra), "con el desglose a la vista, «6 por revisar» lo repetía")
        val (_, sinRenglon) = textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 3), null)
        assertTrue(pastillaDeLaFranja(sinRenglon), "sin renglón, la pastilla es lo único que dice cuántos son")
    }

    // ── (2) El widget «Falta por pagar» ──────────────────────────────────────

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        RecurringRule(id = id, name = nombre, category = "Servicios", amount = monto, dayOfMonth = 25, type = TransactionType.EXPENSE),
        "2026-10-25",
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    private val soloElCarro = DashboardData(
        upcoming = listOf(pago("rr_carro", "Carro", 9_000, -3)),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(2026, 10),
        ajustesDePeriodo = PeriodSettings(),
        spentByCategory = mapOf("Mercado" to 499_050L),
    )

    private val dosPendientes = soloElCarro.copy(upcoming = soloElCarro.upcoming!! + pago("rr_internet", "Internet", 3_000, 2))

    @Test
    fun `con un solo pendiente que ya esta en Proximo pago, el widget no se pinta`() {
        assertTrue(elWidgetDeFaltaSoloRepiteElProximoPago(soloElCarro, sinElProximoPago = true))
        val tipos = visibleSections(defaultDashboardDefinition(), soloElCarro).map { it.type }
        assertTrue("PROXIMO_PAGO" in tipos)
        assertFalse("CHECKLIST_DEL_PERIODO" in tipos, "«Es el de «Próximo pago»» ocupaba una tarjeta entera")
        // «En qué se va» queda solo en su bloque: a todo el ancho, sin hueco al lado.
        val bloque = bloquesDelInicio(visibleSections(defaultDashboardDefinition(), soloElCarro)).single { b -> b.any { it.type == "GASTO_POR_CATEGORIA" } }
        assertEquals(listOf("GASTO_POR_CATEGORIA"), bloque.map { it.type })
    }

    @Test
    fun `con mas de un pendiente, o sin Proximo pago arriba, el widget sigue`() {
        assertFalse(elWidgetDeFaltaSoloRepiteElProximoPago(dosPendientes, sinElProximoPago = true))
        assertTrue("CHECKLIST_DEL_PERIODO" in visibleSections(defaultDashboardDefinition(), dosPendientes).map { it.type })
        assertFalse(elWidgetDeFaltaSoloRepiteElProximoPago(soloElCarro, sinElProximoPago = false))
    }

    // ── (3) El tercero cuadra con los grupos ─────────────────────────────────

    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio("dst_caro", "Caro")))

    /** Caro puso $240.000 y tú $576.100, mitad y mitad: Caro te debe $168.050. */
    private val gastos = listOf(
        GastoDelEspacio(id = "g1", espacioId = casa.id, pagadoPor = "dst_caro", monto = 240_000, partes = mapOf(MIEMBRO_YO to 120_000L, "dst_caro" to 120_000L)),
        GastoDelEspacio(id = "g2", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = 576_100, partes = mapOf(MIEMBRO_YO to 288_050L, "dst_caro" to 288_050L)),
    )

    private fun detalle(g: List<GastoDelEspacio>): DetalleDelEspacio {
        val saldo = saldoDelEspacio(casa, g, emptyList())
        return DetalleDelEspacio(espacio = casa, saldo = saldo, delPeriodo = saldo, gastos = g)
    }

    @Test
    fun `la ficha del tercero dice lo que dice el grupo, con el saldo de saldoDelEspacio`() {
        val lineas = elTerceroEnLosGrupos("dst_caro", listOf(detalle(gastos)))
        assertEquals(listOf("En Casa con Caro: puso \$240.000 · te debe \$168.050"), lineas.map { it.texto })
        assertEquals("esp_casa", lineas.single().espacioId)
        // Quien no es del grupo no tiene línea.
        assertTrue(elTerceroEnLosGrupos("dst_papa", listOf(detalle(gastos))).isEmpty())
    }

    @Test
    fun `le debes, y estan a mano`() {
        val alReves = gastos.map { it.copy(pagadoPor = if (it.pagadoPor == MIEMBRO_YO) "dst_caro" else MIEMBRO_YO) }
        assertEquals(
            "En Casa con Caro: puso \$576.100 · le debes \$168.050",
            elTerceroEnLosGrupos("dst_caro", listOf(detalle(alReves))).single().texto,
        )
        val parejo = listOf(GastoDelEspacio(id = "g", espacioId = casa.id, pagadoPor = "dst_caro", monto = 0, partes = emptyMap()))
        assertEquals("En Casa con Caro: están a mano", elTerceroEnLosGrupos("dst_caro", listOf(detalle(parejo))).single().texto)
    }

    // ── (4) El crédito sin saldo ─────────────────────────────────────────────

    private val terminos = CreditTerms(
        accountId = "acc_4083", bank = "Banco", principal = 40_000_000L, rateEa = 18.0,
        termMonths = 60, installment = 980_000L, dayOfMonth = 5, startDate = "2026-01-10",
    )
    private val ahorros = Account("acc_ah", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 10_581_200L)

    private fun vehiculo(deuda: Long, movimientos: Boolean = true, conTerminos: Boolean = true) = CreditSummary(
        account = Account("acc_4083", "Vehículo 4083", AccountType.LOAN, deuda),
        terms = terminos.takeIf { conTerminos },
        paidPct = null,
        hasMovements = movimientos,
    )

    @Test
    fun `el abono se dice con palabras de deuda positiva, y baja la deuda`() {
        assertEquals(
            "Debes \$980.000 · después del abono, \$480.000",
            deudaDespuesDelTraspaso(ahorros, vehiculo(980_000).account, 500_000L),
        )
        // El desembolso sube la deuda: el signo de la cuenta no cambió, solo las palabras.
        assertEquals(
            "Debes \$980.000 · después del desembolso, \$1.480.000",
            deudaDespuesDelTraspaso(vehiculo(980_000).account, ahorros, 500_000L),
        )
        // El caso de la revisión (deuda en −$980.000) no hace la cuenta: le falta el saldo.
        assertTrue(creditoSinSaldoEnMovi(vehiculo(-980_000).account, bajaLaDeuda = 500_000L))
    }

    @Test
    fun `un credito con condiciones y sin saldo se nombra, uno sano no`() {
        assertTrue(creditoSinSaldo(vehiculo(-980_000)), "la demo: capital $40M y la primera cuota sin desembolso")
        assertTrue(creditoSinSaldo(vehiculo(0, movimientos = false)), "en cero y sin un movimiento: no se sabe")
        assertFalse(creditoSinSaldo(vehiculo(0, movimientos = true)), "en cero con movimientos: pagado")
        assertFalse(creditoSinSaldo(vehiculo(34_000_000)), "con su deuda: sano")
        assertFalse(creditoSinSaldo(vehiculo(-980_000, conTerminos = false)), "sin condiciones no hay capital que reclamar")
        val tarjeta = CreditSummary(Account("acc_nu", "Nu", AccountType.CREDIT_CARD, -50_000), terms = terminos, paidPct = null)
        assertFalse(creditoSinSaldo(tarjeta), "una tarjeta a favor es plata a favor, no un crédito sin saldo")
        assertEquals("Falta el saldo de Vehículo 4083", textoDelCreditoSinSaldoEnElPatrimonio("Vehículo 4083"))
    }

    /** Sin regla de plata nueva: con créditos sanos, el patrimonio da lo de siempre. */
    @Test
    fun `patrimonioDe no cambia para un dueno con creditos sanos`() {
        val nu = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, 1_583_000L)
        val hipoteca = Account("acc_2334", "Hipotecario 2334", AccountType.LOAN, 204_183_376L)
        val p = patrimonioDe(listOf(ahorros, vehiculo(34_000_000).account, hipoteca, nu))
        assertEquals(10_581_200L, p.tuPlata)
        assertEquals(34_000_000L + 204_183_376L + 1_583_000L, p.deudas)
        assertEquals(10_581_200L - 34_000_000L - 204_183_376L - 1_583_000L, p.neto)
    }
}
