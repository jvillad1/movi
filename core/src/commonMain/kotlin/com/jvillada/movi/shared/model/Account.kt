package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, LOAN, INVESTMENT }

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val balance: Long,      // COP component (derived on read)
    val currency: String = "COP",
    val balancesByCurrency: Map<String, Long> = emptyMap(),  // derived: per-currency balance
    val estimatedTotalCop: Long? = null,                     // derived: COP + foreign × TRM
)

/**
 * F56 — [AccountType] se queda igual (compat de DB y wire: filas viejas, eventos guardados,
 * el `POST /api/accounts` del server), pero la UI ya no distingue entre CASH/CHECKING/SAVINGS
 * (verificado: se tratan idéntico en todos los cálculos de balance) ni promete que una cuenta
 * es un lugar distinto de una deuda cuando en realidad es lo mismo con otro nombre. Este agrupador
 * es la superficie que la UI muestra: **Dinero** (plata disponible), **Inversión** (plata
 * guardada) y **Deuda** (tarjetas y préstamos — ya no se crean como cuenta, viven en Créditos,
 * pero el grupo existe para lo que ya haya en la base).
 */
enum class AccountGroup { DINERO, INVERSION, DEUDA }

val AccountType.group: AccountGroup
    get() = when (this) {
        AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS -> AccountGroup.DINERO
        AccountType.INVESTMENT -> AccountGroup.INVERSION
        AccountType.CREDIT_CARD, AccountType.LOAN -> AccountGroup.DEUDA
    }

val AccountType.groupLabel: String
    get() = when (group) {
        AccountGroup.DINERO -> "Dinero"
        AccountGroup.INVERSION -> "Inversión"
        AccountGroup.DEUDA -> "Deuda"
    }
