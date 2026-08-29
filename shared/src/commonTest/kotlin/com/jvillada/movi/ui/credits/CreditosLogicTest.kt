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
        porMoneda: Map<String, Long> = emptyMap(),
    ) = CreditSummary(
        account = Account(
            "acc_loan", "Libranza", AccountType.LOAN,
            balance = deuda, balancesByCurrency = porMoneda,
        ),
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

    // ── Las otras dos puertas a «100% pagado», de la re-revisión ──────────────

    /**
     * **El caso que reabrió el bloqueante.** Crédito creado en $0 y después un abono
     * extraordinario —la operación que esta ola estrena—: la deuda queda NEGATIVA, así que sí hubo
     * un movimiento (`hasMovements = true`, la guarda de arriba no dispara) y `paidPctFor` clampa
     * a 1.0. La tarjeta decía «100% pagado» sobre un crédito que nunca recibió su desembolso.
     */
    @Test
    fun `una deuda negativa no es un credito pagado`() {
        val progreso = progresoDeCredito(
            credito(deuda = -1_500_000L, paidPct = 1.0, hasMovements = true),
        )

        assertEquals("Deuda en negativo — revísala", progreso.etiqueta)
        assertEquals(0f, progreso.fraccion)
        assertEquals(true, progreso.esAviso)
    }

    /**
     * El primo hermano, preexistente: `account.balance` es el componente **COP** del saldo, así
     * que un préstamo cuyos movimientos son todos en dólares tiene `balance = 0` con movimientos,
     * y daba «100% pagado» sobre una deuda intacta. El porcentaje se calcula contra un `principal`
     * en COP: sobre un saldo que no está en COP no hay nada que comparar.
     */
    @Test
    fun `una deuda en otra moneda no se anuncia como pagada`() {
        val progreso = progresoDeCredito(
            credito(
                deuda = 0L, paidPct = 1.0, hasMovements = true,
                porMoneda = mapOf("COP" to 0L, "USD" to 12_000L),
            ),
        )

        assertEquals("Deuda en otra moneda", progreso.etiqueta)
        assertEquals(0f, progreso.fraccion)
    }

    /** Un préstamo COP con el mapa por moneda cargado sigue mostrando su porcentaje. */
    @Test
    fun `un prestamo en pesos con el mapa por moneda no cambia`() {
        val progreso = progresoDeCredito(
            credito(
                deuda = 65_000_000L, paidPct = 0.18, hasMovements = true,
                porMoneda = mapOf("COP" to 65_000_000L),
            ),
        )

        assertEquals("18% pagado", progreso.etiqueta)
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
