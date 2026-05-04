package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.*
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.StatementParseResult

class NoOpRepository : WalletRepository {
    override suspend fun getWallets() = emptyList<Wallet>()
    override suspend fun getWallet(id: String) = error("stub")
    override suspend fun getTransactions(walletId: String) = emptyList<Transaction>()
    override suspend fun getTransactionsByDay() = emptyList<TransactionDay>()
    override suspend fun addTransaction(transaction: Transaction) = error("stub")
    override suspend fun getHoldings() = emptyList<Holding>()
    override suspend fun getCredits() = emptyList<Credit>()
    override suspend fun getGoals() = emptyList<Goal>()
    override suspend fun getSmsMessages() = emptyList<SmsMessage>()
    override suspend fun getSms(id: String) = error("stub")
    override suspend fun parseSms(id: String) = error("stub")
    override suspend fun confirmSms(id: String) {}
    override suspend fun ignoreSms(id: String) {}
    override suspend fun getFinanceSummary(scope: Scope) = error("stub")
    override suspend fun getBudgets() = emptyList<Budget>()
    override suspend fun createBudget(budget: Budget) = budget
    override suspend fun updateBudget(category: String, budget: Budget) = budget
    override suspend fun deleteBudget(category: String) {}
    override suspend fun getRecurringRules() = emptyList<RecurringRule>()
    override suspend fun chatAi(request: AiChatRequest) = error("stub")
    override suspend fun getAccounts() = emptyList<Account>()
    override suspend fun getAccount(id: String) = error("stub")
    override suspend fun createAccount(account: Account) = account
    override suspend fun postEvent(event: FinancialEvent) = event
    override suspend fun getEvents(accountId: String?) = emptyList<FinancialEvent>()
    override suspend fun getEventsByDay() = emptyList<EventDay>()
    override suspend fun voidEvent(id: String, reason: String?) = error("stub")
    override suspend fun register(request: RegisterRequest) = error("stub")
    override suspend fun login(request: LoginRequest) = error("stub")
    override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String) =
        StatementParseResult("", "", "", emptyList(), emptyList())
    override suspend fun importStatement(decision: ImportDecision) {}
}
