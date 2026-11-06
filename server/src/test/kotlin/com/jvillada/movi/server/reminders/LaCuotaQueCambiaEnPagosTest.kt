package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.CuotaPagada
import com.jvillada.movi.shared.model.TipoDeCuota
import com.jvillada.movi.shared.model.desglosarCuota
import com.jvillada.movi.shared.model.pagoDeCuotaLegs
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # Vuelta 133 · la cuota que cambia, en la regla sintética de Pagos
 *
 * La regla `credit_<id>` es la que restan «Te quedan», Pagos, la caja y «Falta por pagar». Un crédito de
 * cuota fija (y uno viejo, sin el campo) sigue con `installment`; uno de cuota que cambia, con la última
 * cuota pagada de verdad —la pata del dinero, seguros incluidos— y la marca «unos $X».
 */
class LaCuotaQueCambiaEnPagosTest {

    private val ahorros = Account("acc_ahorros", "Ahorros 8133", AccountType.SAVINGS, 5_000_000)
    private val libre = Account("acc_libre", "Libre inversión 9695", AccountType.LOAN, 40_000_000)
    private val terms = CreditTerms(
        accountId = libre.id, bank = "Bancolombia", principal = 50_000_000, rateEa = 18.0, termMonths = 72,
        installment = 1_200_000, dayOfMonth = 5, startDate = "2025-01-10",
    )

    private fun pago(dia: LocalDate, monto: Long, id: String) = pagoDeCuotaLegs(
        CreatePagoDeCuotaRequest(
            fromAccountId = ahorros.id, debtAccountId = libre.id, amount = monto,
            timestamp = appDateToEpochMillis(dia), transferId = "tr_$id", fromEventId = "ev_sale_$id", toEventId = "ev_baja_$id",
        ),
        ahorros, libre,
        desglosarCuota(
            cuota = monto, tipoDeLaDeuda = libre.type, saldoDeLaDeuda = libre.balance, rateEa = terms.rateEa,
            seguroMensual = null, otrosCargosMensuales = null, yaCobradoEnElMes = 0L, sinIntereses = false,
        ),
    ).toList()

    private val pagos = pago(LocalDate.of(2026, 9, 5), 1_240_000, "sep") + pago(LocalDate.of(2026, 10, 5), 1_262_300, "oct")

    @Test
    fun `un credito fijo, o uno viejo sin el campo, sigue con su cuota de siempre`() {
        val regla = virtualRuleFor(terms, libre.name)
        assertEquals(1_200_000L, regla.amount)
        assertNull(regla.cuotaQueCambia, "el wire de siempre")
        // Aunque alguien le pase la última pagada: la cuota fija no cambia.
        assertEquals(1_200_000L, virtualRuleFor(terms, libre.name, CuotaPagada(1_262_300, "2026-10")).amount)
    }

    @Test
    fun `la ultima cuota pagada es la pata del dinero del ultimo pago`() {
        val ultima = ultimaCuotaPagadaDe(virtualRuleFor(terms, libre.name), pagos)
        assertEquals(CuotaPagada(1_262_300, "2026-10"), ultima, "la plata que salió de la cuenta, no lo que bajó la deuda")
        assertNull(ultimaCuotaPagadaDe(virtualRuleFor(terms, libre.name), emptyList()))
    }

    @Test
    fun `un credito de tasa variable resta la ultima cuota pagada y lo dice`() {
        val variable = terms.copy(tipoDeCuota = TipoDeCuota.TASA_VARIABLE)
        val ultima = ultimaCuotaPagadaDe(virtualRuleFor(variable, libre.name), pagos)
        val regla = virtualRuleFor(variable, libre.name, ultima)
        assertEquals(1_262_300L, regla.amount)
        assertEquals(CuotaPagada(1_262_300, "2026-10"), assertNotNull(regla.cuotaQueCambia).anterior)
        // Sin pagos todavía, la cuota de las condiciones, igual marcada como estimada.
        val sinPagos = virtualRuleFor(variable.copy(tipoDeCuota = TipoDeCuota.UVR), libre.name, null)
        assertEquals(1_200_000L, sinPagos.amount)
        assertNull(assertNotNull(sinPagos.cuotaQueCambia).anterior)
    }

    @Test
    fun `una cuota pagada en dos partes cuenta entera`() {
        val enPartes = pago(LocalDate.of(2026, 9, 5), 1_240_000, "sep") +
            pago(LocalDate.of(2026, 10, 3), 600_000, "oct1") + pago(LocalDate.of(2026, 10, 5), 662_300, "oct2")
        val ultima = ultimaCuotaPagadaDe(virtualRuleFor(terms.copy(tipoDeCuota = TipoDeCuota.UVR), libre.name), enPartes)
        assertEquals(CuotaPagada(1_262_300, "2026-10"), ultima, "las dos partes de octubre, no solo la última")
    }

    /**
     * Vuelta 134 (N-3) · un giro repartido en dos partes que pagan las cuotas atrasadas de septiembre y
     * octubre del mismo crédito: las dos partes llevan la misma fecha y se escribieron juntas (el mismo
     * `createdAt`, como las escribe `escribirLasPartes`), así que caen en el mismo período. Sumarlas daba una
     * «cuota» de $2.524.600; es una cuota de $1.262.300 pagada dos veces.
     */
    @Test
    fun `un giro que paga dos cuotas atrasadas del mismo credito no duplica la estimacion`() {
        val dia = LocalDate.of(2026, 10, 20)
        val escritas = 1_793_000_000_000L
        val giro = (pago(dia, 1_262_300, "parte1") + pago(dia, 1_262_300, "parte2")).map { it.copy(createdAt = escritas) }
        val uvr = terms.copy(tipoDeCuota = TipoDeCuota.UVR)
        val ultima = ultimaCuotaPagadaDe(virtualRuleFor(uvr, libre.name), pago(LocalDate.of(2026, 9, 5), 1_240_000, "sep") + giro)
        assertEquals(CuotaPagada(1_262_300, "2026-10"), ultima, "dos partes del mismo pago son dos cuotas, no una del doble")

        // Lo mismo con las partes de un movimiento dividido (`repartoDe`), aunque no compartan el instante.
        val dividido = pago(dia, 1_262_300, "d1").map { it.copy(repartoDe = "ev_original") } +
            pago(dia, 1_262_300, "d2").map { it.copy(repartoDe = "ev_original", createdAt = escritas + 5) }
        assertEquals(CuotaPagada(1_262_300, "2026-10"), ultimaCuotaPagadaDe(virtualRuleFor(uvr, libre.name), dividido))

        // Y dos pagos de verdad (anotados en momentos distintos) siguen sumando: es la cuota pagada en partes.
        val enDosPagos = pago(dia, 600_000, "a").map { it.copy(createdAt = escritas) } +
            pago(dia, 662_300, "b").map { it.copy(createdAt = escritas + 60_000) }
        assertEquals(CuotaPagada(1_262_300, "2026-10"), ultimaCuotaPagadaDe(virtualRuleFor(uvr, libre.name), enDosPagos))
    }
}
