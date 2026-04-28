package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Transaction
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

    override suspend fun addTransaction(transaction: Transaction): Transaction =
        client.post("$baseUrl/api/wallets/${transaction.walletId}/transactions") {
            contentType(ContentType.Application.Json)
            setBody(transaction)
        }.body()
}
