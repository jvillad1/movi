package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ola 16 — el desembolso que nace junto con el crédito.
 *
 * Las reglas que se prueban acá son las que impiden que el alta de un crédito termine en un estado
 * que miente sobre la plata. Cada una tiene su mentira asociada anotada en el nombre del test.
 */
class CreditDisbursementTest {

    private fun cuenta(
        id: String = "acc-corriente",
        tipo: AccountType = AccountType.CHECKING,
        moneda: String = "COP",
    ) = Account(id = id, name = "Bancolombia", type = tipo, balance = 12_400_000L, currency = moneda)

    // ── validateCreditDisbursement ────────────────────────────────────────────

    @Test
    fun `un desembolso completo a una cuenta en pesos es valido`() {
        assertNull(validateCreditDisbursement(257_000_000L, cuenta(), 257_000_000L))
    }

    @Test
    fun `un desembolso neto de costos tambien es valido`() {
        // $250M de un capital de $257M: las dos cifras son ciertas y distintas.
        assertNull(validateCreditDisbursement(257_000_000L, cuenta(), 250_000_000L))
    }

    @Test
    fun `sin cuenta destino no hay desembolso`() {
        assertEquals("Elige a qué cuenta te entró la plata", validateCreditDisbursement(1_000L, null, 1_000L))
    }

    @Test
    fun `la plata de un credito no puede entrar a otra deuda`() {
        assertEquals(
            DISBURSEMENT_TARGET_NOT_MONEY,
            validateCreditDisbursement(1_000L, cuenta(tipo = AccountType.LOAN), 1_000L),
        )
        assertEquals(
            DISBURSEMENT_TARGET_NOT_MONEY,
            validateCreditDisbursement(1_000L, cuenta(tipo = AccountType.CREDIT_CARD), 1_000L),
        )
    }

    @Test
    fun `una cuenta de inversion si puede recibir el desembolso`() {
        assertNull(validateCreditDisbursement(1_000L, cuenta(tipo = AccountType.INVESTMENT), 1_000L))
    }

    @Test
    fun `una cuenta en otra moneda no puede recibirlo todavia`() {
        assertEquals(
            DISBURSEMENT_ONLY_COP,
            validateCreditDisbursement(1_000L, cuenta(moneda = "USD"), 1_000L),
        )
    }

    /**
     * La mentira que evita: un desembolso de $0 crea el crédito sin ningún movimiento, y con eso
     * la tarjeta vuelve a decir «Falta registrar el desembolso» justo después de que el dueño dijo
     * que la plata sí le entró.
     */
    @Test
    fun `un desembolso en cero no se guarda`() {
        assertEquals("Escribe cuánto te entró a la cuenta", validateCreditDisbursement(1_000L, cuenta(), 0L))
    }

    /**
     * La mentira que evita: efectivo sin deuda que lo respalde. Si entra más plata que el capital,
     * uno de los dos números está mal tecleado, y guardarlo infla el patrimonio en silencio.
     */
    @Test
    fun `no puede entrar mas plata que el capital del credito`() {
        assertEquals(
            DISBURSEMENT_OVER_PRINCIPAL,
            validateCreditDisbursement(257_000_000L, cuenta(), 260_000_000L),
        )
    }

    // ── aperturaDeCreditoDesembolsado ─────────────────────────────────────────

    @Test
    fun `un desembolso completo no deja nada de apertura`() {
        assertEquals(0L, aperturaDeCreditoDesembolsado(257_000_000L, 257_000_000L))
    }

    /**
     * El caso que hace falta que exista: si el crédito naciera debiendo solo lo que entró
     * ($250M de $257M), `paidPct` daría 1 − 250/257 ≈ 0.027 y la tarjeta anunciaría «2% pagado»
     * sobre un crédito que nadie pagó todavía. Con la apertura de los costos financiados, la deuda
     * arranca valiendo exactamente el capital.
     */
    @Test
    fun `los costos financiados quedan como deuda de apertura`() {
        assertEquals(7_000_000L, aperturaDeCreditoDesembolsado(257_000_000L, 250_000_000L))
        assertEquals(257_000_000L, 250_000_000L + aperturaDeCreditoDesembolsado(257_000_000L, 250_000_000L))
    }

    @Test
    fun `una apertura nunca queda negativa`() {
        // `validateCreditDisbursement` ya rechaza este caso; el piso es el cinturón sobre los
        // tirantes — una cuenta de deuda que arranca a favor no tiene lectura posible.
        assertEquals(0L, aperturaDeCreditoDesembolsado(100L, 500L))
    }
}
