package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 141 · la cuota que se paga desde una cuenta que no es «Tu plata» (issue #701)
 *
 * La Hipoteca 1254 se paga desde la AFC Davibank 9497 (condicionada a vivienda). La fila sale en Pagos,
 * con «Anotar pago», pero no suma en lo que te queda: ni en los fijos, ni en «Falta por pagar», ni en la
 * cola, ni en la caja. Con la cuenta dentro de «Tu plata», todo como siempre.
 */
class LaCuotaQueSaleDeOtraCuentaTest {

    private val afc = Account("acc_afc_9497", "AFC Davibank 9497", AccountType.SAVINGS, 30_000_000L, condicionadaA = "vivienda")
    private val bancolombia = Account("acc_8133", "Bancolombia 8133", AccountType.SAVINGS, 5_000_000L)
    private val skandia = Account("acc_skandia", "Skandia", AccountType.INVESTMENT, 100_000_000L)
    private val hipoteca = Account("acc_1254", "Hipoteca 1254", AccountType.LOAN, -300_000_000L)
    private val tarjeta = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, -100_000L)

    /** La regla sintética de la cuota, como la arma el server (`virtualRuleFor`). */
    private fun cuota(fuera: Boolean?) = RecurringRule(
        id = "${CREDIT_RULE_PREFIX}acc_1254", name = "Cuota Hipoteca 1254", category = CUOTA_CATEGORY,
        amount = 9_145_256L, dayOfMonth = 5, type = TransactionType.EXPENSE,
        sePagaDesde = "acc_afc_9497", saleDeFueraDeTuPlata = fuera,
    )

    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 1_800_000L, dayOfMonth = 10,
        type = TransactionType.EXPENSE,
    )

    /** El checklist del período del 25-oct al 24-nov, con «hoy» el 1-nov. */
    private fun checklist(fuera: Boolean?, ocurrencias: List<OccurrenceState> = emptyList()) = checklistDelPeriodo(
        upcoming = listOf(
            UpcomingPayment(cuota(fuera), "2026-11-05", 4, PaymentStatus.UPCOMING),
            UpcomingPayment(arriendo, "2026-11-10", 9, PaymentStatus.UPCOMING),
        ),
        ocurrencias = ocurrencias,
        periodo = PeriodoFinanciero(2026, 11),
        settings = PeriodSettings(cutoffDay = 25),
    )

    @Test
    fun `la regla de fuera de Tu plata es la del patrimonio`() {
        assertTrue(laCuotaSaleDeFueraDeTuPlata(afc), "una cuenta condicionada")
        assertFalse(laCuotaSaleDeFueraDeTuPlata(bancolombia))
        assertFalse(laCuotaSaleDeFueraDeTuPlata(skandia), "una inversión sin condición es Tu plata (invertido)")
        assertTrue(laCuotaSaleDeFueraDeTuPlata(hipoteca), "una deuda")
        // Lo mismo que dice `patrimonioDe`: la AFC va a «condicionado», no a Tu plata.
        val p = patrimonioDe(listOf(afc, bancolombia))
        assertEquals(5_000_000L, p.tuPlata)
        assertEquals(30_000_000L, p.condicionado)
    }

    @Test
    fun `la fila sale en el checklist con la marca y la cuenta`() {
        val fila = checklist(fuera = true).single { it.ruleId.startsWith(CREDIT_RULE_PREFIX) }
        assertTrue(fila.saleDeFueraDeTuPlata)
        assertEquals("acc_afc_9497", fila.sePagaDesde)
        assertEquals("2026-11-05", fila.vence)
        assertFalse(fila.hecho)
        assertEquals("Sale de AFC Davibank 9497: no toca Tu plata", lineaDeLaCuotaFueraDeTuPlata(afc.name))
    }

    @Test
    fun `pendiente no suma en falta por pagar, en los fijos, en la cola ni en la caja`() {
        val lista = checklist(fuera = true)
        val fila = lista.single { it.saleDeFueraDeTuPlata }
        assertEquals(1_800_000L, faltaPorPagar(lista), "solo el arriendo")
        assertEquals(1_800_000L, fijosDelPeriodo(lista))
        assertEquals(listOf("rr_arriendo"), pagosPendientes(lista).map { it.ruleId })
        assertNull(urgenciaDelPago(fila), "no apura")
        assertNull(urgenciaDelPago(fila.copy(diasParaVencer = -2)), "ni vencida")
        assertTrue(colaDePendientes(lista, diasPorVencer = 30).none { (it as? Pendiente.Pago)?.pago?.saleDeFueraDeTuPlata == true })
        assertEquals(listOf("rr_arriendo"), pagosQueFaltanDelPeriodo(lista).map { it.ruleId })
        val caja = cajaProyectada(tuPlataHoy = 5_000_000L, pagos = lista, gastoDiario = null, hoy = "2026-11-01", ultimoDia = "2026-11-24")!!
        assertEquals(5_000_000L - 1_800_000L, caja.alCierre.saldo, "la caja solo resta el arriendo")
        assertEquals(1_800_000L, loQueSaleDe(lista))
    }

    @Test
    fun `pagada tampoco suma en lo pagado ni en los fijos`() {
        val pagada = OccurrenceState(
            "${CREDIT_RULE_PREFIX}acc_1254", "2026-11", "2026-11-05", occurred = true, eventId = "ev_afc",
            derivadaDeUnMovimiento = true, montoDelPago = 9_145_256L,
        )
        val lista = checklist(fuera = true, ocurrencias = listOf(pagada))
        assertTrue(lista.single { it.saleDeFueraDeTuPlata }.hecho, "se tilda como cualquier cuota")
        assertEquals(1_800_000L, fijosDelPeriodo(lista))
        assertEquals(0L, yaPagadoEnElPeriodo(lista))
        assertEquals(1_800_000L, faltaPorPagar(lista))
    }

    @Test
    fun `en Por categoria el grupo de creditos no dice pagado con la cuota de la AFC pendiente`() {
        val creditos = facturasPorCategoria(checklist(fuera = true)).single { it.clase == ClaseDeGrupoDeFacturas.CREDITOS }
        assertEquals(0L, creditos.falta, "no suma")
        assertFalse(creditos.todoPagado, "pero tampoco está pagada: «Pendiente · no suma»")
        val pagada = OccurrenceState(
            "${CREDIT_RULE_PREFIX}acc_1254", "2026-11", "2026-11-05", occurred = true, eventId = "ev_afc",
            derivadaDeUnMovimiento = true, montoDelPago = 9_145_256L,
        )
        val despues = facturasPorCategoria(checklist(fuera = true, ocurrencias = listOf(pagada)))
            .single { it.clase == ClaseDeGrupoDeFacturas.CREDITOS }
        assertTrue(despues.todoPagado, "pagada, sí")
    }

    @Test
    fun `con la cuenta dentro de Tu plata todo es como siempre`() {
        for (marca in listOf(null, false)) {
            val lista = checklist(fuera = marca)
            val fila = lista.single { it.ruleId.startsWith(CREDIT_RULE_PREFIX) }
            assertFalse(fila.saleDeFueraDeTuPlata)
            assertEquals(1_800_000L + 9_145_256L, faltaPorPagar(lista))
            assertEquals(1_800_000L + 9_145_256L, fijosDelPeriodo(lista))
            assertEquals(2, pagosPendientes(lista).size)
            assertEquals(UrgenciaDelPendiente.POR_VENCER, urgenciaDelPago(fila))
            val caja = cajaProyectada(tuPlataHoy = 20_000_000L, pagos = lista, gastoDiario = null, hoy = "2026-11-01", ultimoDia = "2026-11-24")!!
            assertEquals(20_000_000L - 1_800_000L - 9_145_256L, caja.alCierre.saldo)
        }
    }

    @Test
    fun `pagar desde la AFC no cambia lo que queda del periodo`() {
        val cuentas = listOf(afc, bancolombia, hipoteca).associate {
            it.id to CuentaDelDisponible(it.type, it.condicionadaA)
        }
        fun pata(id: String, cuenta: String, tipo: TransactionType, flujo: Boolean) = FinancialEvent(
            id = id, accountId = cuenta, type = tipo, amount = 9_145_256L, category = CUOTA_CATEGORY,
            description = "Cuota Hipoteca 1254", timestamp = 0L, transferId = "t_cuota",
            reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = flujo,
        )
        // Las dos patas de siempre (`pagoDeCuotaLegs`): sale de la AFC, baja la hipoteca.
        val patas = listOf(
            pata("ev_afc", "acc_afc_9497", TransactionType.EXPENSE, flujo = true),
            pata("ev_hip", "acc_1254", TransactionType.INCOME, flujo = false),
        )
        /** saldo al inicio + entradas − guardado − otros pagos de deuda − fijos (sin gasto variable: la cuota no es). */
        fun loQueQueda(eventos: List<FinancialEvent>, lista: List<PagoDelPeriodo>): Long {
            val plata = plataDelPeriodo(saldoAlInicio = 5_000_000L, eventos = eventos, cuentas = cuentas)
            // El server no le reconoce este pago a los fijos (`cuotasDelChecklistPagadas` deja afuera la regla).
            val otros = pagosDeDeudaFueraDelChecklist(eventos, cuentas, enLosFijos = emptyMap())
            return plata.total - otros - fijosDelPeriodo(lista)
        }
        val antes = loQueQueda(emptyList(), checklist(fuera = true))
        val pagada = OccurrenceState(
            "${CREDIT_RULE_PREFIX}acc_1254", "2026-11", "2026-11-05", occurred = true, eventId = "ev_afc",
            derivadaDeUnMovimiento = true, montoDelPago = 9_145_256L,
        )
        val despues = loQueQueda(patas, checklist(fuera = true, ocurrencias = listOf(pagada)))
        assertEquals(antes, despues, "la plata salió de afuera: «Te quedan» no se mueve")
        assertEquals(5_000_000L - 1_800_000L, despues)
        // Y el pago de la cuota no es gasto variable (sale entero por su categoría).
        assertTrue(gastoVariablePorDia(patas, emptyMap()) { "2026-11-05" }.isEmpty())
    }

    @Test
    fun `validar la cuenta de la cuota`() {
        val base = CreditTerms(
            accountId = "acc_1254", bank = "Davibank", principal = 400_000_000L, rateEa = 12.0, termMonths = 240,
            installment = 9_145_256L, dayOfMonth = 5, startDate = "2020-01-01", sePagaDesde = "acc_afc_9497",
        )
        assertNull(validarCuentaDeLaCuota(base, afc, "COP"))
        assertNull(validarCuentaDeLaCuota(base.copy(sePagaDesde = null), null, "COP"), "sin cuenta, nada que validar")
        assertEquals(CUOTA_DESDE_CON_TERCERO, validarCuentaDeLaCuota(base.copy(paidBy = "Skandia"), afc, "COP"))
        assertEquals(CUOTA_DESDE_CON_LIBRANZA, validarCuentaDeLaCuota(base.copy(payrollDeduction = true), afc, "COP"))
        assertEquals(CUOTA_DESDE_CON_DEBITO, validarCuentaDeLaCuota(base.copy(debitoAutomaticoDesde = "acc_8133"), afc, "COP"))
        assertEquals(CUOTA_DESDE_SIN_CUENTA, validarCuentaDeLaCuota(base, null, "COP"), "una cuenta ajena no existe")
        assertEquals(CUOTA_DESDE_UNA_DEUDA, validarCuentaDeLaCuota(base, tarjeta, "COP"))
        assertEquals(CUOTA_DESDE_OTRA_MONEDA, validarCuentaDeLaCuota(base, afc.copy(currency = "USD"), "COP"))
        assertNull(validarCuentaDeLaCuota(base, skandia, "COP"), "una inversión sirve")
        assertEquals(listOf(afc, bancolombia, skandia), cuentasParaLaCuota(listOf(afc, bancolombia, skandia, hipoteca, tarjeta)))
    }
}
