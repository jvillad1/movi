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

/**
 * Cuánto cambió el saldo de **esta cuenta** en un día, para el detalle de una sola cuenta
 * ([com.jvillada.movi.ui.accounts.AccountDetailScreen]) — no confundir con el total "flujo de
 * caja" que usan Movimientos ([com.jvillada.movi.ui.transactions.TransactionsScreen]) y
 * `/api/events/by-day`, que agregan **varias** cuentas para responder "¿cuánta plata entró o
 * salió del bolsillo este día?" y por eso filtran por `countsAsCashFlow`.
 *
 * Dentro del detalle de UNA cuenta esa pregunta no aplica igual: ya se sabe de qué cuenta se
 * trata, así que lo que no miente es "¿cuánto se movió el saldo de esta cuenta hoy?" — la misma
 * cifra que ya resume la tarjeta de arriba (DEUDA ACTUAL / SALDO ACTUAL). Usar `countsAsCashFlow`
 * acá habría dado dos resultados igual de engañosos: en una cuenta LOAN siempre da `false` (la
 * regla es "LOAN nunca es flujo de caja"), así que el total del día habría sido $0 todos los
 * días, incluso el día de un ajuste de $60.000.000; y en CREDIT_CARD solo la compra cuenta,
 * así que el abono desaparecería del total aunque sí mueva la deuda. `signedDelta` no tiene ese
 * problema: usa la convención real de la cuenta (para LOAN/CREDIT_CARD, INCOME baja la deuda),
 * así que un ajuste de $100.000.000 a $40.000.000 —un INCOME de $60.000.000— da `-60.000.000`:
 * la deuda bajó, tal como pasó. Antes daba `+$60.000.000` porque sumaba con la convención de
 * cuenta de activo (INCOME siempre suma) sin mirar el tipo de cuenta.
 *
 * Solo suma eventos en COP, igual que el resto de los totales de esta pantalla — un evento en
 * otra moneda no es comparable en la misma cifra sin pasar por una tasa.
 */
fun accountDayTotal(accountType: AccountType, items: List<FinancialEvent>): Long =
    items.filter { it.currency == "COP" }.sumOf { signedDelta(accountType, it.type, it.amount) }
