package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.Credit
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.Transaction
import com.jvillada.movi.shared.model.TransactionDay
import com.jvillada.movi.shared.model.Wallet
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class WalletRepositoryImpl(
    private val client: HttpClient,
    private val baseUrl: String,
) : WalletRepository {

    override suspend fun getWallets(): List<Wallet> =
        client.get("$baseUrl/api/wallets").body()

    override suspend fun getWallet(id: String): Wallet =
        client.get("$baseUrl/api/wallets/$id").body()

    override suspend fun getTransactions(walletId: String): List<Transaction> =
        client.get("$baseUrl/api/wallets/$walletId/transactions").body()

    override suspend fun getTransactionsByDay(): List<TransactionDay> =
        client.get("$baseUrl/api/transactions/by-day").body()

    override suspend fun addTransaction(transaction: Transaction): Transaction =
        client.post("$baseUrl/api/wallets/${transaction.walletId}/transactions") {
            contentType(ContentType.Application.Json)
            setBody(transaction)
        }.body()

    override suspend fun getHoldings(): List<Holding> =
        client.get("$baseUrl/api/holdings").body()

    override suspend fun getCredits(): List<Credit> =
        client.get("$baseUrl/api/credits").body()

    override suspend fun getGoals(): List<Goal> =
        client.get("$baseUrl/api/goals").body()

    override suspend fun getSmsMessages(): List<SmsMessage> =
        client.get("$baseUrl/api/sms").body()

    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
        client.get("$baseUrl/api/finance-summary?scope=${scope.name}").body()

    override suspend fun getBudgets(): List<Budget> =
        client.get("$baseUrl/api/budgets").body()
}
