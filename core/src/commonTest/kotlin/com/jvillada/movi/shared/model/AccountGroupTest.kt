package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * F56 — mapeo completo de [AccountType] a [AccountGroup]: CASH/CHECKING/SAVINGS son Dinero
 * (se tratan idéntico en todos los cálculos, ver [Balance.signedDelta]), INVESTMENT es
 * Inversión, y CREDIT_CARD/LOAN son Deuda (ya no se crean como cuenta, pero el grupo cubre lo
 * que ya exista en la base).
 */
class AccountGroupTest {

    @Test
    fun `cada tipo cae en el grupo correcto`() {
        assertEquals(AccountGroup.DINERO, AccountType.CASH.group)
        assertEquals(AccountGroup.DINERO, AccountType.CHECKING.group)
        assertEquals(AccountGroup.DINERO, AccountType.SAVINGS.group)
        assertEquals(AccountGroup.INVERSION, AccountType.INVESTMENT.group)
        assertEquals(AccountGroup.DEUDA, AccountType.CREDIT_CARD.group)
        assertEquals(AccountGroup.DEUDA, AccountType.LOAN.group)
    }

    @Test
    fun `groupLabel en espanol neutro`() {
        assertEquals("Dinero", AccountType.CASH.groupLabel)
        assertEquals("Dinero", AccountType.CHECKING.groupLabel)
        assertEquals("Dinero", AccountType.SAVINGS.groupLabel)
        assertEquals("Inversión", AccountType.INVESTMENT.groupLabel)
        assertEquals("Deuda", AccountType.CREDIT_CARD.groupLabel)
        assertEquals("Deuda", AccountType.LOAN.groupLabel)
    }
}
