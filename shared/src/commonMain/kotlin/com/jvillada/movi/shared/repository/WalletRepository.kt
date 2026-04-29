package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Credit
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.Transaction
import com.jvillada.movi.shared.model.TransactionDay
import com.jvillada.movi.shared.model.Wallet

interface WalletRepository {
    suspend fun getWallets(): List<Wallet>
    suspend fun getWallet(id: String): Wallet
    suspend fun getTransactions(walletId: String): List<Transaction>
    suspend fun getTransactionsByDay(): List<TransactionDay>
    suspend fun addTransaction(transaction: Transaction): Transaction
    suspend fun getHoldings(): List<Holding>
    suspend fun getCredits(): List<Credit>
    suspend fun getGoals(): List<Goal>
    suspend fun getSmsMessages(): List<SmsMessage>
    suspend fun getFinanceSummary(scope: Scope): FinanceSummary
}
