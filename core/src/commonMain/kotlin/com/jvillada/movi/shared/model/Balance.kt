package com.jvillada.movi.shared.model

/**
 * Lo que un evento le aporta al saldo de su cuenta.
 * Cuentas de activo: INCOME suma, EXPENSE resta.
 * CREDIT_CARD / LOAN (el saldo es deuda positiva): EXPENSE sube la deuda, INCOME (abono) la baja.
 *
 * Vive en `:core` (y no en `:server`, donde nació) para que el cliente offline-first y el server
 * compartan una sola definición del signo. Antes estaba duplicada de hecho: `LocalRepository`
 * aplicaba el delta local con la convención de cuenta de activo sin mirar el tipo de cuenta, así
 * que un abono a una cuenta LOAN subía la deuda en vez de bajarla. Con una sola función, ese tipo
 * de divergencia deja de ser posible — cambiar el signo para un tipo de cuenta lo cambia en los
 * dos lados a la vez.
 */
fun signedDelta(accountType: AccountType, type: TransactionType, amount: Long): Long =
    when (accountType) {
        AccountType.CREDIT_CARD,
        AccountType.LOAN -> if (type == TransactionType.EXPENSE) amount else -amount
        else             -> if (type == TransactionType.INCOME) amount else -amount
    }
