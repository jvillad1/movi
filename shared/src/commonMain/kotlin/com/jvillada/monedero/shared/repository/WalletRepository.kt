package com.jvillada.monedero.shared.repository

import com.jvillada.monedero.shared.model.Transaction
import com.jvillada.monedero.shared.model.Wallet

interface WalletRepository {
    suspend fun getWallets(): List<Wallet>
    suspend fun getWallet(id: String): Wallet
    suspend fun getTransactions(walletId: String): List<Transaction>
    suspend fun addTransaction(transaction: Transaction): Transaction
}
