package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * # Vuelta 53 · un crédito nunca dice «A favor» en «¿De cuál crédito?»
 *
 * En una deuda el saldo positivo es lo que se debe. Con deuda, «Debes $X»; un crédito en 0 o por
 * debajo (la demo: un crédito con condiciones y sin movimientos) dice «Movi no tiene el saldo», como la
 * hoja de Cuota de la vuelta 49; una tarjeta en 0 o pagada de más, «Sin deuda». Nunca «A favor».
 */
class ElSaldoDeUnCreditoEnElSelectorTest {

    private fun credito(saldo: Long) = Account("acc-vehiculo", "Vehículo 4083", AccountType.LOAN, saldo, "COP")
    private fun tarjeta(saldo: Long) = Account("acc-nu", "Nu", AccountType.CREDIT_CARD, saldo, "COP")

    @Test
    fun conDeudaDiceDebes() {
        assertEquals("Vehículo 4083 · COP · Debes \$177.200.000", textoDeCuentaDeDeuda(credito(177_200_000L)))
        assertEquals("Nu · COP · Debes \$1.683.000", textoDeCuentaDeDeuda(tarjeta(1_683_000L)))
    }

    @Test
    fun unCreditoSinDeudaDiceQueMoviNoTieneElSaldo() {
        assertEquals("Vehículo 4083 · COP · $SIN_EL_SALDO_EN_EL_SELECTOR", textoDeCuentaDeDeuda(credito(-980_000L)))
        assertEquals("Vehículo 4083 · COP · $SIN_EL_SALDO_EN_EL_SELECTOR", textoDeCuentaDeDeuda(credito(0L)))
    }

    @Test
    fun nuncaAFavorNiEnUnaTarjeta() {
        listOf(credito(-980_000L), credito(0L), tarjeta(-50_000L), tarjeta(0L)).forEach {
            assertFalse("A favor" in textoDeCuentaDeDeuda(it), textoDeCuentaDeDeuda(it))
        }
        assertEquals("Nu · COP · $SIN_DEUDA_EN_EL_SELECTOR", textoDeCuentaDeDeuda(tarjeta(-50_000L)))
    }
}
