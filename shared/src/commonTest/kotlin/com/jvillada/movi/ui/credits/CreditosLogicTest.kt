package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Una deuda en $0 significa dos cosas opuestas, y la tarjeta tiene que decir cuál.**
 *
 * Estos tests blindan el arreglo del hallazgo bloqueante de la revisión de la Ola 14: un crédito
 * creado en $0 —el paso 1 del flujo de dos pasos que registra un desembolso— anunciaba
 * «100% pagado», con la barra llena, sobre un crédito de $257.000.000 al que todavía no se le
 * había registrado nada. Ver [progresoDeCredito] para el porqué completo.
 */
class CreditosLogicTest {

    private val terminos = CreditTerms(
        accountId = "acc_loan",
        bank = "Bancolombia",
        principal = 257_000_000L,
        rateEa = 12.5,
        termMonths = 120,
        installment = 3_500_000L,
        dayOfMonth = 5,
        startDate = "2026-08-28",
    )

    private fun credito(
        deuda: Long,
        paidPct: Double?,
        hasMovements: Boolean,
        terms: CreditTerms? = terminos,
    ) = CreditSummary(
        account = Account("acc_loan", "Libranza", AccountType.LOAN, balance = deuda),
        terms = terms,
        paidPct = paidPct,
        hasMovements = hasMovements,
    )

    /** El caso que motivó todo esto. */
    @Test
    fun `un credito recien creado en cero no dice pagado, dice que falta el desembolso`() {
        val progreso = progresoDeCredito(credito(deuda = 0L, paidPct = 1.0, hasMovements = false))

        assertEquals("Falta registrar el desembolso", progreso.etiqueta)
        assertEquals(0f, progreso.fraccion, "la barra vacía, no llena")
        assertEquals(true, progreso.esAviso, "es una frase, no una cifra")
    }

    /**
     * Y el contracaso, que es la razón por la que la distinción no puede salir de la deuda sola:
     * un crédito **de verdad** pagado también está en $0, y ahí «100% pagado» es cierto.
     */
    @Test
    fun `un credito de verdad pagado sigue diciendo cien por ciento`() {
        val progreso = progresoDeCredito(credito(deuda = 0L, paidPct = 1.0, hasMovements = true))

        assertEquals("100% pagado", progreso.etiqueta)
        assertEquals(1f, progreso.fraccion)
        assertEquals(false, progreso.esAviso)
    }

    @Test
    fun `un credito a medio pagar no cambia en nada`() {
        val progreso = progresoDeCredito(credito(deuda = 65_000_000L, paidPct = 0.18, hasMovements = true))

        assertEquals("18% pagado", progreso.etiqueta)
    }

    /**
     * Anular el desembolso devuelve la tarjeta al aviso: la deuda vuelve a $0 y no queda ningún
     * movimiento vivo (`hasMovements` sale de los eventos NO anulados). Sin esto, deshacer un
     * desembolso dejaba la tarjeta diciendo «pagado».
     */
    @Test
    fun `anular el desembolso vuelve a dejar el aviso, no un pagado`() {
        val progreso = progresoDeCredito(credito(deuda = 0L, paidPct = 1.0, hasMovements = false))

        assertEquals("Falta registrar el desembolso", progreso.etiqueta)
    }

    /** Sin términos no hay capital original: no hay porcentaje que calcular ni desembolso que reclamar. */
    @Test
    fun `un credito sin terminos se comporta como siempre`() {
        val progreso = progresoDeCredito(
            credito(deuda = 0L, paidPct = null, hasMovements = false, terms = null),
        )

        assertEquals("0% pagado", progreso.etiqueta)
        assertEquals(0f, progreso.fraccion)
        assertEquals(false, progreso.esAviso, "«0% pagado» es una cifra aunque no haya movimientos")
    }

    /**
     * El default del campo nuevo es `true` a propósito: contra un server viejo que no lo mande, la
     * tarjeta muestra el porcentaje, que es exactamente lo que mostraba antes. Un cliente nuevo no
     * puede empezar a reclamar desembolsos que un server viejo no sabe informar.
     */
    @Test
    fun `contra un server que no manda el campo se muestra el porcentaje de siempre`() {
        val sinElCampo = CreditSummary(
            account = Account("acc_loan", "Libranza", AccountType.LOAN, balance = 65_000_000L),
            terms = terminos,
            paidPct = 0.18,
        )

        assertEquals("18% pagado", progresoDeCredito(sinElCampo).etiqueta)
    }
}
